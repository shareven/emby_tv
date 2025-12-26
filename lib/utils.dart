import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:overlay_support/overlay_support.dart';

String getImageUrl(String serverUrl, Map item, bool isContinueWatching) {
  if (isContinueWatching) {
    if (item['ParentBackdropItemId'] != null) {
      return "$serverUrl/emby/Items/${item['ParentBackdropItemId']}/Images/Backdrop?maxWidth=500&tag=${item['ParentBackdropImageTags'][0]}&quality=80";
    } else if (item['ParentThumbItemId'] != null) {
      return "$serverUrl/emby/Items/${item['ParentThumbItemId']}/Images/Thumb?maxWidth=500&tag=${item['ParentThumbImageTag']}&quality=80";
    } else if (item['ImageTags']['Thumb'] != null) {
      return "$serverUrl/emby/Items/${item['Id']}/Images/Thumb?maxHeight=450&maxWidth=300&tag=${item['ImageTags']['Thumb']}&quality=80";
    }
    return "$serverUrl/emby/Items/${item['Id']}/Images/Primary?maxWidth=500&tag=${item['ImageTags']['Primary']}&quality=80";
  }
  return "$serverUrl/emby/Items/${item['Id']}/Images/Primary?maxHeight=450&maxWidth=300&tag=${item['ImageTags']['Primary']}&quality=80";
}

String formatDuration(Duration duration) {
  String twoDigits(int n) => n.toString().padLeft(2, '0');
  final hours = duration.inHours;
  final minutes = duration.inMinutes.remainder(60);
  final seconds = duration.inSeconds.remainder(60);
  return [
    if (hours > 0) twoDigits(hours),
    twoDigits(minutes),
    twoDigits(seconds),
  ].join(':');
}

String formatFileSize(int bytes) {
  if (bytes <= 0) return '0 B';
  const suffixes = ['B', 'KB', 'MB', 'GB', 'TB'];
  final i = (log(bytes) / log(1024)).floor();
  return '${(bytes / pow(1024, i)).toStringAsFixed(2)} ${suffixes[i]}';
}

/// [Show error msg]
void showErrorMsg(String msg) {
  showSimpleNotification(
    Text(msg, style: const TextStyle(color: Colors.white)),
    leading: const Icon(Icons.error, color: Colors.white),
    duration: const Duration(seconds: 10),
    position: NotificationPosition.bottom,
    background: Colors.red,
  );
}

/// [Show success msg]
void showSuccessMsg(String msg) {
  showSimpleNotification(
    Text(msg, style: const TextStyle(color: Colors.white)),
    leading: const Icon(Icons.check, color: Colors.white),
    position: NotificationPosition.bottom,
    background: Colors.green,
  );
}

void showDeleteDialog(
  BuildContext context,
  String text, {
  required Function deleteFn,
  Function? cancelFn,
}) {
  showDialog(
    context: context,
    builder: (BuildContext context) => AlertDialog(
      content: Text(text, maxLines: 5, overflow: TextOverflow.ellipsis),
      actions: <Widget>[
        TextButton(
          child: const Text("Cancel", style: TextStyle(color: Colors.grey)),
          onPressed: () {
            if (cancelFn != null) cancelFn();
            Navigator.pop(context);
          },
        ),
        TextButton(
          child: const Text("Delete", style: TextStyle(color: Colors.pink)),
          onPressed: () {
            deleteFn();
            Navigator.pop(context);
          },
        ),
      ],
    ),
  );
}

const MethodChannel _deviceCapabilitiesChannel = MethodChannel(
  'emby_tv/device_capabilities',
);

Future<Map<String, dynamic>> buildPlaybackInfoBody({
  bool disableHevc = false,
  // 修正为 200Mbps，符合 2025 年 4K/8K 蓝光原盘标准
  int maxStreamingBitrate = 200000000, 
}) async {
  try {
    // 1. 获取硬件能力
    final result = await _deviceCapabilitiesChannel
        .invokeMethod<Map<dynamic, dynamic>>('getCapabilities');

    if (result == null) throw Exception("无法获取设备硬件信息");

    // 2. 提取数据并转为可变列表 (处理 Unsupported operation 报错)
    List<String> videoCodecs = List<String>.from(result['VideoCodecs'] ?? []);
    final List<dynamic> videoProfiles = List<dynamic>.from(result['VideoProfiles'] ?? []);

    // --- 动态 Level + 手动开关结合逻辑 ---
    int rawLevel = _findMaxLevel(videoProfiles, "h264", 51);
    int finalLevel;
    if (disableHevc) {
      // 开启兼容模式时，收紧 H264 Level 到 51 (1080P/4K 30fps)
      finalLevel = 51;
    } else {
      // 正常模式，信任动态探测，上限设为 62 (8K 标准上限)
      finalLevel = rawLevel > 62 ? 62 : rawLevel;
    }

    // 3. 构建 DeviceProfile (参考官方 2025 最新配置)
    return {
      "DeviceProfile": {
        "Name": result['DeviceName'] ?? "Android TV Player",
        "Id": result['DeviceId'] ?? "unique_id",
        "MaxStaticBitrate": maxStreamingBitrate,
        "MaxStreamingBitrate": maxStreamingBitrate,
        "MusicStreamingTranscodingBitrate": 192000,
        "MaxStaticMusicBitrate": 320000,

        // 核心修复：音轨切换的关键。仿照官方设为 null，禁用盲目的 Direct Play
        // 迫使服务器在切换音轨时提供支持 AudioStreamIndex 的串流链接
        "DirectPlayProfiles": [
          {
            "Type": "Video",
            "VideoCodec": null,
            "Container": null,
            "AudioCodec": null
          },
          {
            "Type": "Audio",
            "Container": null
          }
        ],

        // 串流与转码：允许所有硬件支持的编码
        "TranscodingProfiles": [
          {
            "Container": "ts",
            "Type": "Video",
            "AudioCodec": "ac3,aac,mp3,mp2,dts,dtshd,truehd",
            "VideoCodec": disableHevc ? "h264" : "h264,hevc,av1",
            "Context": "Streaming",
            "Protocol": "hls",
            "MaxAudioChannels": "8",
            "MinSegments": "1",
            "BreakOnNonKeyFrames": true,
          },
          {
            "Container": "mkv",
            "Type": "Video",
            "AudioCodec": "aac,mp3,ac3,dts,flac,truehd",
            "VideoCodec": disableHevc ? "h264" : "h264,hevc,av1",
            "Context": "Static",
            "MaxAudioChannels": "8"
          },
          {
            "Container": "mp3",
            "Type": "Audio",
            "AudioCodec": "mp3",
            "Context": "Streaming",
            "Protocol": "http"
          }
        ],

        "CodecProfiles": [
          // H.264 动态等级
          {
            "Type": "Video",
            "Codec": "h264",
            "Conditions": [
              {
                "Condition": "LessThanEqual",
                "Property": "VideoLevel",
                "Value": finalLevel.toString(),
                "IsRequired": false,
              },
            ],
          },
          // HEVC 动态配置
          if (!disableHevc)
            {
              "Type": "Video",
              "Codec": "hevc",
              "Conditions": [
                {
                  "Condition": "EqualsAny",
                  "Property": "VideoProfile",
                  "Value": "Main|Main 10|Rext",
                  "IsRequired": false
                }
              ]
            },
          // 音频多声道支持
          {
            "Type": "Audio",
            "Conditions": [
              {
                "Condition": "LessThanEqual",
                "Property": "AudioChannels",
                "Value": "8",
                "IsRequired": false,
              },
            ],
          },
        ],

        "SubtitleProfiles": [
          {"Format": "vtt", "Method": "Hls"},
          {"Format": "srt", "Method": "External"},
          {"Format": "ass", "Method": "External"},
          {"Format": "ssa", "Method": "External"},
          {"Format": "srt", "Method": "Embed"},
          {"Format": "ass", "Method": "Embed"},
          {"Format": "ssa", "Method": "Embed"},
          {"Format": "pgs", "Method": "Embed"},
          {"Format": "sub", "Method": "Embed"},
          {"Format": "dvdsub", "Method": "Embed"},
          {"Format": "vtt", "Method": "Embed"}
        ],
        "MaxCanvasWidth": result['MaxCanvasWidth'] ?? 3840,
        "MaxCanvasHeight": result['MaxCanvasHeight'] ?? 2160,
      },

      "IsPlayback": true,
      "AutoOpenLiveStream": true,
      "MaxStreamingBitrate": maxStreamingBitrate,
      "EnableDirectPlay": true,
      "EnableDirectStream": true,
    };
  } catch (e) {
    print("Build PlaybackInfo Error: $e");
    return {};
  }
}

// 辅助方法：动态获取硬件 Level
int _findMaxLevel(List<dynamic> profiles, String codec, int defaultValue) {
  try {
    final p = profiles.firstWhere(
      (item) => item['Codec']?.toString().toLowerCase() == codec.toLowerCase(),
      orElse: () => null,
    );
    if (p != null && p['MaxLevel'] != null) {
      if (p['MaxLevel'] is int) return p['MaxLevel'];
      return int.parse(p['MaxLevel'].toString());
    }
  } catch (_) {}
  return defaultValue;
}
