import 'dart:math';

import 'package:flutter/foundation.dart';
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

Future<Map<String, dynamic>> getDeviceCapabilities() async {
  final fallback = <String, dynamic>{
    "maxWidth": 1920,
    "maxHeight": 1080,
    "videoCodecs": ["h264"],
    "audioCodecs": ["aac", "mp3"],
    "containers": ["mp4", "m4v", "mov", "3gp", "mkv", "webm", "ts", "flv"],
  };
  if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
    return fallback;
  }
  try {
    final result = await _deviceCapabilitiesChannel
        .invokeMethod<Map<dynamic, dynamic>>('getCapabilities');
    if (result == null) {
      return fallback;
    }
    final videoCodecs =
        (result["videoCodecs"] as List?)
            ?.map((e) => e.toString())
            .where((e) => e.isNotEmpty)
            .toList() ??
        <String>[];
    final audioCodecs =
        (result["audioCodecs"] as List?)
            ?.map((e) => e.toString())
            .where((e) => e.isNotEmpty)
            .toList() ??
        <String>[];
    final containers =
        (result["containers"] as List?)
            ?.map((e) => e.toString())
            .where((e) => e.isNotEmpty)
            .toList() ??
        <String>[];
    final maxWidth = result["maxWidth"];
    final maxHeight = result["maxHeight"];
    return <String, dynamic>{
      "maxWidth": maxWidth is int ? maxWidth : fallback["maxWidth"],
      "maxHeight": maxHeight is int ? maxHeight : fallback["maxHeight"],
      "videoCodecs": videoCodecs.isNotEmpty
          ? videoCodecs
          : fallback["videoCodecs"],
      "audioCodecs": audioCodecs.isNotEmpty
          ? audioCodecs
          : fallback["audioCodecs"],
      "containers": containers.isNotEmpty ? containers : fallback["containers"],
    };
  } on PlatformException {
    return fallback;
  } catch (_) {
    return fallback;
  }
}

const Map _playbackInfoBodyTemplate = {
  "DeviceProfile": {
    "MaxStaticBitrate": 140000000,
    "MaxStreamingBitrate": 140000000,
    "MusicStreamingTranscodingBitrate": 192000,
    "DirectPlayProfiles": [
      {
        "Container": "mp4,m4v",
        "Type": "Video",
        "VideoCodec": "h264,h265,hevc,av1,vp8,vp9",
        "AudioCodec": "mp3,aac,opus,flac,vorbis",
      },
      {
        "Container": "mkv",
        "Type": "Video",
        "VideoCodec": "h264,h265,hevc,av1,vp8,vp9",
        "AudioCodec": "mp3,aac,opus,flac,vorbis",
      },
      {
        "Container": "flv",
        "Type": "Video",
        "VideoCodec": "h264",
        "AudioCodec": "aac,mp3",
      },
      {
        "Container": "3gp",
        "Type": "Video",
        "VideoCodec": "",
        "AudioCodec": "mp3,aac,opus,flac,vorbis",
      },
      {
        "Container": "mov",
        "Type": "Video",
        "VideoCodec": "h264",
        "AudioCodec": "mp3,aac,opus,flac,vorbis",
      },
      {"Container": "opus", "Type": "Audio"},
      {"Container": "mp3", "Type": "Audio", "AudioCodec": "mp3"},
      {"Container": "mp2,mp3", "Type": "Audio", "AudioCodec": "mp2"},
      {"Container": "m4a", "AudioCodec": "aac", "Type": "Audio"},
      {"Container": "mp4", "AudioCodec": "aac", "Type": "Audio"},
      {"Container": "flac", "Type": "Audio"},
      {"Container": "webma,webm", "Type": "Audio"},
      {
        "Container": "wav",
        "Type": "Audio",
        "AudioCodec": "PCM_S16LE,PCM_S24LE",
      },
      {"Container": "ogg", "Type": "Audio"},
      {
        "Container": "webm",
        "Type": "Video",
        "AudioCodec": "vorbis,opus",
        "VideoCodec": "av1,VP8,VP9",
      },
    ],
    "TranscodingProfiles": [
      {
        "Container": "aac",
        "Type": "Audio",
        "AudioCodec": "aac",
        "Context": "Streaming",
        "Protocol": "hls",
        "MaxAudioChannels": "2",
        "MinSegments": "1",
        "BreakOnNonKeyFrames": true,
      },
      {
        "Container": "aac",
        "Type": "Audio",
        "AudioCodec": "aac",
        "Context": "Streaming",
        "Protocol": "http",
        "MaxAudioChannels": "2",
      },
      {
        "Container": "mp3",
        "Type": "Audio",
        "AudioCodec": "mp3",
        "Context": "Streaming",
        "Protocol": "http",
        "MaxAudioChannels": "2",
      },
      {
        "Container": "opus",
        "Type": "Audio",
        "AudioCodec": "opus",
        "Context": "Streaming",
        "Protocol": "http",
        "MaxAudioChannels": "2",
      },
      {
        "Container": "wav",
        "Type": "Audio",
        "AudioCodec": "wav",
        "Context": "Streaming",
        "Protocol": "http",
        "MaxAudioChannels": "2",
      },
      {
        "Container": "opus",
        "Type": "Audio",
        "AudioCodec": "opus",
        "Context": "Static",
        "Protocol": "http",
        "MaxAudioChannels": "2",
      },
      {
        "Container": "mp3",
        "Type": "Audio",
        "AudioCodec": "mp3",
        "Context": "Static",
        "Protocol": "http",
        "MaxAudioChannels": "2",
      },
      {
        "Container": "aac",
        "Type": "Audio",
        "AudioCodec": "aac",
        "Context": "Static",
        "Protocol": "http",
        "MaxAudioChannels": "2",
      },
      {
        "Container": "wav",
        "Type": "Audio",
        "AudioCodec": "wav",
        "Context": "Static",
        "Protocol": "http",
        "MaxAudioChannels": "2",
      },
      {
        "Container": "mkv",
        "Type": "Video",
        "AudioCodec": "mp3,aac,opus,flac,vorbis",
        "VideoCodec": "h264,h265,hevc,av1,vp8,vp9",
        "Context": "Static",
        "MaxAudioChannels": "2",
        "CopyTimestamps": true,
      },
      {
        "Container": "ts",
        "Type": "Video",
        "AudioCodec": "mp3,aac",
        "VideoCodec": "h264,h265,hevc,av1",
        "Context": "Streaming",
        "Protocol": "hls",
        "MaxAudioChannels": "2",
        "MinSegments": "1",
        "BreakOnNonKeyFrames": true,
        "ManifestSubtitles": "vtt",
      },
      {
        "Container": "webm",
        "Type": "Video",
        "AudioCodec": "vorbis",
        "VideoCodec": "vpx",
        "Context": "Streaming",
        "Protocol": "http",
        "MaxAudioChannels": "2",
      },
      {
        "Container": "mp4",
        "Type": "Video",
        "AudioCodec": "mp3,aac,opus,flac,vorbis",
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
            "Value": "62",
            "IsRequired": false,
          },
          {
            "Condition": "LessThanEqual",
            "Property": "Width",
            "Value": "1920",
            "IsRequired": false,
          },
        ],
      },
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
          {
            "Condition": "LessThanEqual",
            "Property": "Width",
            "Value": "1920",
            "IsRequired": false,
          },
        ],
      },
      {
        "Type": "Video",
        "Conditions": [
          {
            "Condition": "LessThanEqual",
            "Property": "Width",
            "Value": "1920",
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

Future<Map<String, dynamic>> buildPlaybackInfoBody({
  bool disableHevc = false,
}) async {
  final capabilities = await getDeviceCapabilities();
  final base = Map<String, dynamic>.from(_playbackInfoBodyTemplate);
  final deviceProfile = Map<String, dynamic>.from(base["DeviceProfile"] as Map);
  final videoCodecs =
      (capabilities["videoCodecs"] as List?)
          ?.map((e) => e.toString().toLowerCase())
          .where((e) => e.isNotEmpty)
          .toSet()
          .toList() ??
      <String>["h264"];
  final audioCodecs =
      (capabilities["audioCodecs"] as List?)
          ?.map((e) => e.toString().toLowerCase())
          .where((e) => e.isNotEmpty)
          .toSet()
          .toList() ??
      <String>["aac", "mp3"];
  final containers =
      (capabilities["containers"] as List?)
          ?.map((e) => e.toString())
          .where((e) => e.isNotEmpty)
          .toSet()
          .toList() ??
      <String>["mp4", "m4v", "mov", "3gp", "mkv", "webm", "ts", "flv"];
  var filteredVideoCodecs = videoCodecs;
  if (disableHevc) {
    filteredVideoCodecs = filteredVideoCodecs
        .where((c) => !c.contains("hevc") && !c.contains("h265"))
        .toList();
    if (filteredVideoCodecs.isEmpty) {
      filteredVideoCodecs = ["h264"];
    }
  }
  final videoCodecCsv = filteredVideoCodecs.join(',');
  final audioCodecCsv = audioCodecs.join(',');
  final directProfiles = (deviceProfile["DirectPlayProfiles"] as List)
      .map((e) => Map<String, dynamic>.from(e as Map))
      .toList();
  for (final profile in directProfiles) {
    final type = profile["Type"];
    if (type == "Video") {
      profile["VideoCodec"] = videoCodecCsv;
      profile["AudioCodec"] = audioCodecCsv;
      final existingContainers = (profile["Container"] as String?) ?? "";
      if (existingContainers.isNotEmpty) {
        final list = existingContainers
            .split(',')
            .map((e) => e.trim())
            .where((e) => e.isNotEmpty)
            .toList();
        final supported = list.where(containers.contains).toList();
        if (supported.isNotEmpty) {
          profile["Container"] = supported.join(',');
        } else {
          profile["Container"] = containers.join(',');
        }
      } else {
        profile["Container"] = containers.join(',');
      }
    } else if (type == "Audio") {
      profile["AudioCodec"] = audioCodecCsv;
      final existingContainers = (profile["Container"] as String?) ?? "";
      if (existingContainers.isNotEmpty) {
        final list = existingContainers
            .split(',')
            .map((e) => e.trim())
            .where((e) => e.isNotEmpty)
            .toList();
        final supported = list.where(containers.contains).toList();
        if (supported.isNotEmpty) {
          profile["Container"] = supported.join(',');
        }
      }
    }
  }
  deviceProfile["DirectPlayProfiles"] = directProfiles;
  final rawCodecProfiles = deviceProfile["CodecProfiles"] as List?;
  final codecProfiles = <Map<String, dynamic>>[];
  if (rawCodecProfiles != null) {
    for (final item in rawCodecProfiles) {
      if (item is Map) {
        final profile = Map<String, dynamic>.from(item);
        final conditions = item["Conditions"];
        if (conditions is List) {
          profile["Conditions"] = conditions
              .map((c) => c is Map ? Map<String, dynamic>.from(c) : c)
              .toList();
        }
        codecProfiles.add(profile);
      }
    }
  }
  final maxWidth = capabilities["maxWidth"];
  final widthValue = maxWidth is int && maxWidth > 0
      ? maxWidth.toString()
      : null;
  if (widthValue != null) {
    for (final profile in codecProfiles) {
      final conditions = profile["Conditions"];
      if (conditions is List) {
        for (final condition in conditions) {
          if (condition is Map &&
              condition["Property"] == "Width" &&
              condition["Condition"] == "LessThanEqual") {
            condition["Value"] = widthValue;
          }
        }
      }
    }
  }
  deviceProfile["CodecProfiles"] = codecProfiles;
  base["DeviceProfile"] = deviceProfile;
  return base;
}
