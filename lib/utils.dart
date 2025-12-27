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
  int maxStreamingBitrate = 200000000,
}) async {
  try {
    // 1. 获取硬件探测能力
    final result = await _deviceCapabilitiesChannel
        .invokeMethod<Map<dynamic, dynamic>>('getCapabilities');

    if (result == null) throw Exception("无法获取设备硬件信息");

    // 2. 提取数据并转为可变列表 (处理 Unsupported operation 报错)
    List<String> videoCodecs = List<String>.from(result['VideoCodecs'] ?? []);
    List<String> audioCodecs = List<String>.from(result['AudioCodecs'] ?? []);
    final List<dynamic> videoProfiles = List<dynamic>.from(
      result['VideoProfiles'] ?? [],
    );

    // 这样即使用户“关闭服务器转码”，但硬件不支持时，App 会强制回退到 H264
    bool hardwareSupportsHevc = videoCodecs.any(
      (c) => ['hevc', 'h265', 'hevc10'].contains(c.toLowerCase()),
    );

    if (!hardwareSupportsHevc) {
      disableHevc = true;
    }

    // --- 动态 Level 处理 ---
    int rawLevel = _findMaxLevel(videoProfiles, "h264", 51);
    int finalLevel = disableHevc ? 51 : (rawLevel > 62 ? 62 : rawLevel);

    // 动态构建支持字符串
    String supportedVideo = videoCodecs.join(',');
    String supportedAudio = audioCodecs.join(',');
    return {
      "DeviceProfile": {
        "MaxStaticBitrate": 200000000,
        "MaxStreamingBitrate": 200000000,
        "MusicStreamingTranscodingBitrate": 192000,
        "MaxCanvasWidth": result['MaxCanvasWidth'] ?? 3840,
        "MaxCanvasHeight": result['MaxCanvasHeight'] ?? 2160,
        "DirectPlayProfiles": [
          {
            "Type": "Video",
            "VideoCodec": disableHevc ? "h264" : supportedVideo,
            "Container": "mp4,m4v,mkv,mov",
            "AudioCodec": supportedAudio,
          },
          {"Type": "Audio", "Container": null, "AudioCodec": null},
        ],
        "TranscodingProfiles": [
          {
            "Container": "aac",
            "Type": "Audio",
            "AudioCodec": "aac",
            "Context": "Streaming",
            "Protocol": "hls",
            "MaxAudioChannels": "8",
            "MinSegments": "1",
            "BreakOnNonKeyFrames": false,
          },
          {
            "Container": "aac",
            "Type": "Audio",
            "AudioCodec": "aac",
            "Context": "Streaming",
            "Protocol": "http",
            "MaxAudioChannels": "8",
          },
          {
            "Container": "mp3",
            "Type": "Audio",
            "AudioCodec": "mp3",
            "Context": "Streaming",
            "Protocol": "http",
            "MaxAudioChannels": "8",
          },
          {
            "Container": "opus",
            "Type": "Audio",
            "AudioCodec": "opus",
            "Context": "Streaming",
            "Protocol": "http",
            "MaxAudioChannels": "8",
          },
          {
            "Container": "wav",
            "Type": "Audio",
            "AudioCodec": "wav",
            "Context": "Streaming",
            "Protocol": "http",
            "MaxAudioChannels": "8",
          },
          {
            "Container": "opus",
            "Type": "Audio",
            "AudioCodec": "opus",
            "Context": "Static",
            "Protocol": "http",
            "MaxAudioChannels": "8",
          },
          {
            "Container": "mp3",
            "Type": "Audio",
            "AudioCodec": "mp3",
            "Context": "Static",
            "Protocol": "http",
            "MaxAudioChannels": "8",
          },
          {
            "Container": "aac",
            "Type": "Audio",
            "AudioCodec": "aac",
            "Context": "Static",
            "Protocol": "http",
            "MaxAudioChannels": "8",
          },
          {
            "Container": "wav",
            "Type": "Audio",
            "AudioCodec": "wav",
            "Context": "Static",
            "Protocol": "http",
            "MaxAudioChannels": "8",
          },
          {
            "Container": "mkv",
            "Type": "Video",
            "AudioCodec": supportedAudio,
            "VideoCodec": disableHevc ? "h264" : supportedVideo,
            "Context": "Static",
            "MaxAudioChannels": "8",
            "CopyTimestamps": true,
          },
          {
            "Container": "ts",
            "Type": "Video",
            "AudioCodec": supportedAudio,
            "VideoCodec": disableHevc ? "h264" : supportedVideo,
            "Context": "Streaming",
            "Protocol": "hls",
            "MaxAudioChannels": "8",
            "MinSegments": "1",
            "BreakOnNonKeyFrames": false,
            "ManifestSubtitles": "vtt",
          },
          {
            "Container": "webm",
            "Type": "Video",
            "AudioCodec": "vorbis",
            "VideoCodec": "vpx",
            "Context": "Streaming",
            "Protocol": "http",
            "MaxAudioChannels": "8",
          },
          {
            "Container": "mp4",
            "Type": "Video",
            "AudioCodec": supportedAudio,
            "VideoCodec": "h264",
            "Context": "Static",
            "Protocol": "http",
          },
        ],
        "ContainerProfiles": [],
        "CodecProfiles": [
          {
            "Type": "VideoAudio",
            "Codec": "aac",
            "Conditions": [
              {
                "Condition": "Equals",
                "Property": "IsSecondaryAudio",
                "Value": "false",
                "IsRequired": "false",
              },
            ],
          },
          {
            "Type": "VideoAudio",
            "Codec": "flac",
            "Conditions": [
              {
                "Condition": "Equals",
                "Property": "IsSecondaryAudio",
                "Value": "false",
                "IsRequired": "false",
              },
            ],
          },
          {
            "Type": "VideoAudio",
            "Codec": "vorbis",
            "Conditions": [
              {
                "Condition": "Equals",
                "Property": "IsSecondaryAudio",
                "Value": "false",
                "IsRequired": "false",
              },
            ],
          },
          {
            "Type": "VideoAudio",
            "Conditions": [
              {
                "Condition": "Equals",
                "Property": "IsSecondaryAudio",
                "Value": "false",
                "IsRequired": "false",
              },
            ],
          },
          {
            "Type": "Video",
            "Codec": "h264",
            "Conditions": [
              {
                "Condition": "EqualsAny",
                "Property": "VideoProfile",
                "Value": "high|main|baseline|constrained baseline|high 10",
                "IsRequired": false,
              },
              {
                "Condition": "LessThanEqual",
                "Property": "VideoLevel",
                "Value": finalLevel,
                "IsRequired": false,
              },
            ],
          },
          // HEVC 动态配置
          if (!disableHevc &&
              (videoCodecs.contains("hevc") || videoCodecs.contains("h265")))
            {
              "Type": "Video",
              "Codec": "hevc",
              "Conditions": [
                {
                  "Condition": "EqualsAny",
                  "Property": "VideoCodecTag",
                  "Value": "hvc1|hev1|hevc|hdmv",
                  "IsRequired": false,
                },
              ],
            },
        ],
        "SubtitleProfiles": [
          {"Format": "vtt", "Method": "Hls"},
          {"Format": "eia_608", "Method": "VideoSideData", "Protocol": "hls"},
          {"Format": "eia_708", "Method": "VideoSideData", "Protocol": "hls"},
          {"Format": "vtt", "Method": "External"},
          {"Format": "ass", "Method": "External"},
          {"Format": "ssa", "Method": "External"},
        ],
        "ResponseProfiles": [
          {"Type": "Video", "Container": "m4v", "MimeType": "video/mp4"},
        ],
      },
    };
  } catch (e) {
    print("Build PlaybackInfo Error: $e");
    return {};
  }
}

// 辅助方法保持不变
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
