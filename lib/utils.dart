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

    // --- 关键逻辑：根据参数禁用 HEVC ---
    if (disableHevc) {
      videoCodecs.removeWhere((codec) => codec == 'hevc' || codec == 'h265');
      // 同时清理 Profile 列表中的 hevc 项
      videoProfiles.removeWhere(
        (p) => p['Codec'] == 'hevc' || p['Codec'] == 'h265',
      );
    }

    // 3. 构建 Emby DeviceProfile
    return {
      "DeviceProfile": {
        "MaxStreamingBitrate": maxStreamingBitrate,
        "MaxStaticBitrate": maxStreamingBitrate,

        // 直接播放配置
        "DirectPlayProfiles": [
          {
            "Container": "mkv,mp4,mov,ts,m3u8,webm,avi",
            "Type": "Video",
            // 这里的 videoCodecs 已根据 disableHevc 进行了过滤
            "VideoCodec": videoCodecs.join(','),
            "AudioCodec": audioCodecs.join(','),
          },
          {"Container": "mp3,flac,aac,m4a,opus", "Type": "Audio"},
        ],

        // 转码配置：明确告诉服务器，如果不直解，优先转码为 H.264 (兼容性最强)
        "TranscodingProfiles": [
          {
            "Container": "ts",
            "Type": "Video",
            "AudioCodec": "aac,mp3",
            "VideoCodec": "h264",
            "Context": "Streaming",
            "Protocol": "hls",
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
                "Value": _findMaxLevel(videoProfiles, "h264", 51).toString(),
                "IsRequired": true,
              },
            ],
          },
          // HEVC 限制配置 (仅在未禁用时添加)
          if (!disableHevc &&
              (videoCodecs.contains("hevc") || videoCodecs.contains("h265")))
            {
              "Type": "Video",
              "Codec": "hevc",
              "Conditions": [
                {
                  "Condition": "Equals",
                  "Property": "VideoProfile",
                  "Value": _findHevcProfile(videoProfiles),
                  "IsRequired": false,
                },
              ],
            },
          // AV1 限制 (2025年设备主流)
          if (videoCodecs.contains("av1"))
            {"Type": "Video", "Codec": "av1", "Conditions": []},
        ],

        "SubtitleProfiles": [
          {"Format": "srt", "Method": "External"},
          {"Format": "srt", "Method": "Embed"},
          {"Format": "ass", "Method": "Embed"},
          {"Format": "ssa", "Method": "Embed"},
          {"Format": "pgs", "Method": "Embed"}, // 2025年高端设备可尝试支持 PGS
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
