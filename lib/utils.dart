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

/// 构建用于发送给 /Items/{Id}/PlaybackInfo 的请求体
/// [disableHevc]: 如果为 true，则从直接播放列表中移除 HEVC 编码，强制服务器进行 H264 转码
Future<Map<String, dynamic>> buildPlaybackInfoBody({
  bool disableHevc = false,
  int maxStreamingBitrate = 140000000, // 默认 140Mbps (2025年 4K 蓝光标准)
}) async {
  try {
    // 1. 获取 Kotlin 探测的硬件能力
    final result = await _deviceCapabilitiesChannel
        .invokeMethod<Map<dynamic, dynamic>>('getCapabilities');

    if (result == null) throw Exception("无法获取设备硬件信息");

    // 2. 提取基础数据
    List<String> videoCodecs = List<String>.from(result['VideoCodecs'] ?? []);
    List<String> audioCodecs = List<String>.from(result['AudioCodecs'] ?? []);
    final List<dynamic> videoProfiles = result['VideoProfiles'] ?? [];

    // --- 音频优化：增加蓝光原盘常见高清音轨支持 ---
    // 即使设备不支持自解，也要申明，以便在 DirectStream 模式下由客户端处理或 Downmix
    final hdAudio = [
      'ac3',
      'eac3',
      'dts',
      'dtshd',
      'truehd',
      'aac',
      'mp3',
      'flac',
    ];
    for (var codec in hdAudio) {
      if (!audioCodecs.contains(codec)) audioCodecs.add(codec);
    }

    // --- 关键逻辑：根据参数禁用 HEVC ---
    if (disableHevc) {
      videoCodecs.removeWhere((codec) => codec == 'hevc' || codec == 'h265');
      videoProfiles.removeWhere(
        (p) => p['Codec'] == 'hevc' || p['Codec'] == 'h265',
      );
    }

    // 在 buildPlaybackInfoBody 中处理 h264 level
    int rawLevel = _findMaxLevel(videoProfiles, "h264", 51);

    // 将 Level 限制在 52 (4K 60fps 级别)，确保服务器逻辑稳定
    int safeLevel = rawLevel > 52 ? 52 : rawLevel;

    // 3. 构建 Emby DeviceProfile
    return {
      "DeviceProfile": {
        "Name": result['DeviceName'] ?? "Android TV Player",
        "Id": result['DeviceId'] ?? "unique_id",
        "MaxStreamingBitrate": maxStreamingBitrate,
        "MaxStaticBitrate": maxStreamingBitrate,
        "MusicStreamingBitrate": 32000000,

        // 直接播放配置：列出所有原生支持的格式
        "DirectPlayProfiles": [
          {
            "Container": "mkv,mp4,mov,ts,m3u8,webm,avi,m2ts",
            "Type": "Video",
            "VideoCodec": videoCodecs.join(','),
            "AudioCodec": audioCodecs.join(','),
          },
          {"Container": "mp3,flac,aac,m4a,opus,wav,dsf,dff", "Type": "Audio"},
        ],

        // 核心优化：转码配置文件
        // 关键点：VideoCodec 包含 h264 和 hevc。
        // 当音频不兼容时，服务器会匹配此条目，发现视频是 hevc 且被允许，则仅转码音频，视频保持 Copy。
        "TranscodingProfiles": [
          {
            "Container": "ts",
            "Type": "Video",
            "AudioCodec": "aac,ac3", // 如果音频不兼容，优先转为兼容性最强的 AAC/AC3
            "VideoCodec": disableHevc ? "h264" : "h264,hevc",
            "Context": "Streaming",
            "Protocol": "hls",
          },
          {
            "Container": "mp3",
            "Type": "Audio",
            "AudioCodec": "mp3",
            "Context": "Streaming",
            "Protocol": "http",
          },
        ],

        // 编码细化限制
        "CodecProfiles": [
          // H.264 限制配置
          {
            "Type": "Video",
            "Codec": "h264",
            "Conditions": [
              {
                "Condition": "LessThanEqual",
                "Property": "VideoLevel",
                "Value": safeLevel.toString(),
                "IsRequired": true,
              },
            ],
          },
          // HEVC 不限制配置：上报 Profile=Main10 会造成服务器判断错误 TranscodeReasons=VideoProfileNotSupported
          if (!disableHevc &&
              (videoCodecs.contains("hevc") || videoCodecs.contains("h265")))
            {"Type": "Video", "Codec": "hevc", "Conditions": []},
          // AV1 限制 (2025年趋势)
          if (videoCodecs.contains("av1"))
            {
              "Type": "Video",
              "Codec": "av1",
              "Conditions": [
                {
                  "Condition": "LessThanEqual",
                  "Property": "Width",
                  "Value": "3840", // 限制在 4K，防止 8K 视频压垮低端芯片
                  "IsRequired": false,
                },
              ],
            },
          // 音频限制：防止因声道数过多触发视频转码
          {
            "Type": "Audio",
            "Conditions": [
              {
                "Condition": "LessThanEqual",
                "Property": "AudioChannels",
                "Value": "8", // 允许 7.1 声道直接串流
                "IsRequired": false,
              },
            ],
          },
        ],

        "SubtitleProfiles": [
          {"Format": "srt", "Method": "External"},
          {"Format": "srt", "Method": "Embed"},
          {"Format": "ass", "Method": "Embed"},
          {"Format": "ssa", "Method": "Embed"},
          {"Format": "pgs", "Method": "Embed"}, // 必须申明，否则蓝光原盘内置字幕会触发视频重编码
          {"Format": "sub", "Method": "Embed"},
          {"Format": "dvdsub", "Method": "Embed"},
        ],

        "MaxCanvasWidth": result['MaxCanvasWidth'] ?? 3840,
        "MaxCanvasHeight": result['MaxCanvasHeight'] ?? 2160,
      },

      "IsPlayback": true,
      "AutoOpenLiveStream": true,
      "MaxStreamingBitrate": maxStreamingBitrate,
      "EnableDirectPlay": true,
      "EnableDirectStream": true, // 必须开启，这是实现“只转音频”的关键
    };
  } catch (e) {
    return {};
  }
}

// 找到 H264 的最高级别 (如 51, 52)
int _findMaxLevel(List<dynamic> profiles, String codec, int defaultValue) {
  try {
    final p = profiles.firstWhere((item) => item['Codec'] == codec);
    return p['MaxLevel'];
  } catch (_) {
    return defaultValue;
  }
}

// 确定 HEVC 是否支持 Main10 (HDR)
String _findHevcProfile(List<dynamic> profiles) {
  try {
    final p = profiles.firstWhere(
      (item) => item['Codec'] == "hevc" || item['Codec'] == "h265",
    );
    return p['Profile'] ?? "Main";
  } catch (_) {
    return "Main";
  }
}
