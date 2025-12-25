import 'dart:async';
import 'package:emby_tv/app_model.dart';
import 'package:emby_tv/utils.dart';
import 'package:emby_tv/widgets/exoplayer_view.dart';
import 'package:emby_tv/widgets/build_item.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:emby_tv/l10n/app_localizations.dart';

class _SubtitleCue {
  final int startMs;
  final int endMs;
  final String text;
  _SubtitleCue(this.startMs, this.endMs, this.text);
}

class PlayerScreen extends StatefulWidget {
  final String mediaId;
  final bool isSeries;
  final int playbackPositionTicks;
  final int? audioStreamIndexOverride;
  const PlayerScreen({
    required this.mediaId,
    required this.isSeries,
    required this.playbackPositionTicks,
    this.audioStreamIndexOverride,
    super.key,
  });

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  bool _isPlaying = false;
  Timer? _progressTimer;
  Timer? _seekTimer;
  DateTime? _keyDownTime;
  // final FocusNode _focusNode = FocusNode();
  // final FocusScopeNode _focusScopeNode = FocusScopeNode();
  bool _isShowInfo = false;
  bool _isLoading = false;
  Map _media = {};
  Map _mediaInfo = {};
  Map? _session;
  bool _hasSessionLoaded = false;

  List _subtitleTracks = [];
  int _selectedSubtitleIndex = -1;
  List _audioTracks = [];
  int _selectedAudioIndex = -1;
  String? _videoUrl;
  List<_SubtitleCue> _subtitleCues = [];
  String _currentSubtitle = '';
  Duration _position = Duration.zero;
  Duration _duration = Duration.zero;
  Duration _buffered = Duration.zero;
  int _progressTick = 0;
  bool _hasTriedTranscodeFallback = false;
  final GlobalKey<ExoPlayerViewState> _playerKey =
      GlobalKey<ExoPlayerViewState>();
  // 0: list loop, 1: single loop, 2: no loop
  int _playMode = 0;
  // 0: off, 1: server transcode
  int _playbackCorrection = 0;
  bool _endedHandled = false;
  // cache series items to avoid repeated requests
  final Map<String, List> _seriesCache = {};
  final Map<String, Future<List>> _seriesLoadingFutures = {};
  final Map<String, ScrollController> _seriesScrollControllers = {};
  final Map<String, List<FocusNode>> _seriesFocusNodes = {};

  @override
  void initState() {
    super.initState();
    if (widget.playbackPositionTicks > 0 &&
        widget.audioStreamIndexOverride == null) {
      _isLoading = true;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _loadPlaybackCorrection();
      await _loadPlayMode();
      await _getData();
      _startProgressTracking();
    });
  }

  Future<void> _loadPlaybackCorrection() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final v = prefs.getInt('playback_correction');
      if (v != null) {
        setState(() {
          _playbackCorrection = v;
        });
      }
    } catch (_) {}
  }

  Future<void> _savePlaybackCorrection() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt('playback_correction', _playbackCorrection);
    } catch (_) {}
  }

  Future<void> _loadPlayMode() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final v = prefs.getInt('play_mode');
      if (v != null) {
        setState(() {
          _playMode = v;
        });
      }
    } catch (_) {}
  }

  Future<void> _savePlayMode() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt('play_mode', _playMode);
    } catch (_) {}
  }

  Future _getData() async {
    final model = context.read<AppModel>();
    if (!mounted) return;
    final failedGetPlaybackInfoLabel = AppLocalizations.of(
      context,
    )!.failedGetPlaybackInfo;
    final media = await model.getPlaybackInfo(
      widget.mediaId,
      widget.playbackPositionTicks,
      widget.isSeries,
      // if user selected server transcode, ask server for a transcode-capable response
      disableHevc: _playbackCorrection == 1,
    );
    if (media.isEmpty || media["MediaSources"] == null) {
      showErrorMsg(failedGetPlaybackInfoLabel);
      if (mounted) {
        Navigator.of(context).pop();
      }
      return;
    }
    final mediaInfo = await model.getMediaInfo(widget.mediaId);
    final serverUrl = model.serverUrl!;
    final streams = media["MediaSources"][0]["MediaStreams"] as List;
    _subtitleTracks = streams
        .where((stream) => stream["Type"] == "Subtitle")
        .toList();
    _audioTracks = streams
        .where((stream) => stream["Type"] == "Audio")
        .toList();

    _selectedSubtitleIndex =
        media["MediaSources"][0]["DefaultSubtitleStreamIndex"] ?? -1;
    _selectedAudioIndex =
        media["MediaSources"][0]["DefaultAudioStreamIndex"] ?? -1;
    if (widget.audioStreamIndexOverride != null) {
      _selectedAudioIndex = widget.audioStreamIndexOverride!;
    }

    String? path = media['MediaSources'][0]['DirectStreamUrl'] as String?;
    if (_playbackCorrection == 1) {
      // prefer TranscodingUrl when server transcode requested
      path = media['MediaSources'][0]['TranscodingUrl'] as String? ?? path;
    }
    final videoUrl = path != null ? "$serverUrl/emby$path" : null;

    List<_SubtitleCue> cues = [];
    if (_selectedSubtitleIndex != -1) {
      cues = await _fetchSubtitleCues(media, _selectedSubtitleIndex);
    }

    setState(() {
      _media = media;
      _mediaInfo = mediaInfo;
      _videoUrl = videoUrl;
      _subtitleCues = cues;
      _currentSubtitle = '';
    });
    // load session info matching this playback
  }

  Future<void> _loadSessionForCurrent() async {
    if (_hasSessionLoaded) return;
    _hasSessionLoaded = true;
    try {
      final model = context.read<AppModel>();
      final sessions = await model.getPlayingSessions();
      if (sessions.isEmpty) return;
      Map? found;
      final playSessionId = _media['PlaySessionId']?.toString();
      for (final s in sessions) {
        if (s is Map) {
          if (playSessionId != null &&
              s['PlaySessionId']?.toString() == playSessionId) {
            found = s;
            break;
          }
          final now = s['NowPlayingItem'];
          if (now is Map &&
              (now['Id']?.toString() == widget.mediaId ||
                  now['Id']?.toString() == _media['Id']?.toString())) {
            found = s;
            break;
          }
        }
      }
      if (mounted) {
        setState(() {
          _session = found;
        });
      }
    } catch (_) {}
  }

  Future<List<_SubtitleCue>> _fetchSubtitleCues(
    Map media,
    int subtitleIndex,
  ) async {
    final streams = media["MediaSources"][0]["MediaStreams"] as List;
    Map? stream;
    for (final s in streams) {
      if (s is Map && s["Type"] == "Subtitle" && s["Index"] == subtitleIndex) {
        stream = s;
        break;
      }
    }
    if (stream == null) {
      return [];
    }
    final codec = (stream["Codec"] ?? "").toString().toLowerCase();
    final isAss = codec.contains('ass');
    final mediaSourceId = media["MediaSources"][0]["Id"];
    final model = context.read<AppModel>();
    final text = await model.getSubtitle(
      widget.mediaId,
      mediaSourceId,
      subtitleIndex,
      codec,
    );
    final isEnglish = _isEnglishTrack(stream);
    if (isAss) {
      return _parseAss(text, isEnglish: isEnglish);
    }
    return _parseSrt(text, isEnglish: isEnglish);
  }

  bool _isEnglishTrack(Map track) {
    final lang = (track["Language"] ?? "").toString().toLowerCase();
    final title = (track["DisplayTitle"] ?? "").toString().toLowerCase();
    if (lang.contains('en')) {
      return true;
    }
    if (title.contains('english')) {
      return true;
    }
    return false;
  }

  List<_SubtitleCue> _parseAss(String content, {required bool isEnglish}) {
    final normalized = content.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
    final lines = normalized.split('\n');
    final result = <_SubtitleCue>[];
    for (final rawLine in lines) {
      final line = rawLine.trimLeft();
      if (!line.startsWith('Dialogue:')) {
        continue;
      }
      final rest = line.substring('Dialogue:'.length).trim();
      final parts = rest.split(',');
      if (parts.length < 10) {
        continue;
      }
      final startStr = parts[1].trim();
      final endStr = parts[2].trim();
      final textPart = parts.sublist(9).join(',').replaceAll(r'\N', '\n');
      int parseAssTime(String s) {
        final m = RegExp(r'(\d+):(\d+):(\d+)[.](\d+)').firstMatch(s);
        if (m == null) {
          return 0;
        }
        final hh = int.parse(m.group(1)!);
        final mm = int.parse(m.group(2)!);
        final ss = int.parse(m.group(3)!);
        final frac = m.group(4)!;
        final ms = int.parse(
          frac.length >= 3 ? frac.substring(0, 3) : frac.padRight(3, '0'),
        );
        return (((hh * 60 + mm) * 60) + ss) * 1000 + ms;
      }

      final startMs = parseAssTime(startStr);
      final endMs = parseAssTime(endStr);
      if (endMs <= startMs) {
        continue;
      }
      final textLines = textPart.split('\n');
      String? selected;
      for (final raw in textLines) {
        final t = _cleanSubtitleText(raw);
        if (t.isEmpty) {
          continue;
        }
        final hasCjk = _hasCjk(t);
        final hasLatin = _hasLatin(t);
        if (isEnglish) {
          if (hasLatin && !hasCjk) {
            selected = t;
            break;
          }
        } else {
          if (hasCjk) {
            selected = t;
            break;
          }
        }
      }
      selected ??= _cleanSubtitleText(textPart);
      if (selected.isEmpty) {
        continue;
      }
      result.add(_SubtitleCue(startMs, endMs, selected));
    }
    return result;
  }

  List<_SubtitleCue> _parseSrt(String content, {required bool isEnglish}) {
    final normalized = content.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
    final blocks = normalized.split(RegExp(r'\n\s*\n'));
    final result = <_SubtitleCue>[];
    for (final block in blocks) {
      final trimmed = block.trim();
      if (trimmed.isEmpty) {
        continue;
      }
      final lines = trimmed.split('\n');
      if (lines.length < 2) {
        continue;
      }
      final timeLine = lines.firstWhere(
        (l) => l.contains('-->'),
        orElse: () => '',
      );
      if (timeLine.isEmpty) {
        continue;
      }
      final timeLineIndex = lines.indexOf(timeLine);
      final matchHms = RegExp(
        r'(\d+):(\d+):(\d+)[,.](\d+)\s*-->\s*(\d+):(\d+):(\d+)[,.](\d+)',
      ).firstMatch(timeLine);
      Match? match = matchHms;
      bool hasHour = true;
      if (match == null) {
        hasHour = false;
        match = RegExp(
          r'(\d+):(\d+)[,.](\d+)\s*-->\s*(\d+):(\d+)[,.](\d+)',
        ).firstMatch(timeLine);
      }
      if (match == null) {
        continue;
      }
      int parseTime(String h, String m, String s, String ms) {
        final hh = int.parse(h);
        final mm = int.parse(m);
        final ss = int.parse(s);
        final msec = int.parse(ms);
        return (((hh * 60 + mm) * 60) + ss) * 1000 + msec;
      }

      late int startMs;
      late int endMs;
      if (hasHour) {
        startMs = parseTime(
          match.group(1)!,
          match.group(2)!,
          match.group(3)!,
          match.group(4)!,
        );
        endMs = parseTime(
          match.group(5)!,
          match.group(6)!,
          match.group(7)!,
          match.group(8)!,
        );
      } else {
        startMs = parseTime(
          '0',
          match.group(1)!,
          match.group(2)!,
          match.group(3)!,
        );
        endMs = parseTime(
          '0',
          match.group(4)!,
          match.group(5)!,
          match.group(6)!,
        );
      }
      final textLines = lines.sublist(timeLineIndex + 1);
      if (textLines.isEmpty) {
        continue;
      }
      String? selected;
      for (final raw in textLines) {
        final line = _cleanSubtitleText(raw);
        if (line.isEmpty) {
          continue;
        }
        final hasCjk = _hasCjk(line);
        final hasLatin = _hasLatin(line);
        if (isEnglish) {
          if (hasLatin && !hasCjk) {
            selected = line;
            break;
          }
        } else {
          if (hasCjk) {
            selected = line;
            break;
          }
        }
      }
      selected ??= _cleanSubtitleText(textLines.join(' '));
      if (selected.isEmpty) {
        continue;
      }
      result.add(_SubtitleCue(startMs, endMs, selected));
    }
    return result;
  }

  String _cleanSubtitleText(String text) {
    var t = text;
    t = t.replaceAll(RegExp(r'\{[^}]*\}'), '');
    t = t.replaceAll(RegExp(r'<[^>]+>'), '');
    t = t.replaceAll('\n', ' ').trim();
    return t;
  }

  bool _hasCjk(String text) {
    return RegExp(r'[\u4E00-\u9FFF\u3040-\u30FF]').hasMatch(text);
  }

  bool _hasLatin(String text) {
    return RegExp(r'[A-Za-z]').hasMatch(text);
  }

  void _updateCurrentSubtitleLocked() {
    if (_subtitleCues.isEmpty) {
      if (_currentSubtitle.isNotEmpty) {
        _currentSubtitle = '';
      }
      return;
    }
    final ms = _position.inMilliseconds;
    String newText = '';
    for (final cue in _subtitleCues) {
      if (cue.startMs <= ms && ms < cue.endMs) {
        newText = cue.text;
        break;
      }
    }
    if (newText != _currentSubtitle) {
      _currentSubtitle = newText;
    }
  }

  Map? get _currentMediaSource {
    final sources = _media["MediaSources"];
    if (sources is List && sources.isNotEmpty && sources[0] is Map) {
      return sources[0] as Map;
    }
    return null;
  }

  Map? get _currentVideoStream {
    final source = _currentMediaSource;
    if (source == null) return null;
    final streams = source["MediaStreams"];
    if (streams is List) {
      for (final s in streams) {
        if (s is Map && s["Type"] == "Video") {
          return s;
        }
      }
    }
    return null;
  }

  Map? get _currentAudioStream {
    final source = _currentMediaSource;
    if (source == null) return null;
    final streams = source["MediaStreams"];
    Map? first;
    final defaultIndex = _asInt(source["DefaultAudioStreamIndex"]);
    final targetIndex = _selectedAudioIndex != -1
        ? _selectedAudioIndex
        : defaultIndex;
    Map? selected;
    if (streams is List) {
      for (final s in streams) {
        if (s is Map && s["Type"] == "Audio") {
          first ??= s;
          if (targetIndex != null && s["Index"] == targetIndex) {
            selected ??= s;
          }
        }
      }
    }
    return selected ?? first;
  }

  int? _asInt(dynamic value) {
    if (value is int) return value;
    if (value is double) return value.toInt();
    if (value is String) {
      return int.tryParse(value);
    }
    return null;
  }

  String _formatMbpsFromBps(int? bps) {
    if (bps == null || bps <= 0) return '';
    final mbps = bps / 1000000;
    if (mbps >= 1) {
      return '${mbps.toStringAsFixed(mbps >= 10 ? 0 : 1)} mbps';
    }
    final kbps = bps / 1000;
    return '${kbps.toStringAsFixed(0)} kbps';
  }

  String _formatKbps(int? bps) {
    if (bps == null || bps <= 0) return '';
    final kbps = (bps / 1000).round();
    return '$kbps kbps';
  }

  String _formatHz(int? hz) {
    if (hz == null || hz <= 0) return '';
    return '$hz Hz';
  }

  String _formatFps(dynamic value) {
    if (value == null) return '';
    final s = value.toString();
    final d = double.tryParse(s);
    if (d == null || d <= 0) return '';
    if (d == d.roundToDouble()) {
      return d.toStringAsFixed(0);
    }
    return d.toStringAsFixed(3);
  }

  String _streamContainerLine() {
    final source = _currentMediaSource;
    if (source == null) return '';
    final container = (source["Container"] ?? '').toString().toUpperCase();
    final bitrate =
        _asInt(source["Bitrate"]) ?? _asInt(source["TranscodingBitrate"]);
    final bitrateStr = _formatMbpsFromBps(bitrate);
    if (container.isEmpty && bitrateStr.isEmpty) return '';
    if (bitrateStr.isEmpty) return container;
    if (container.isEmpty) return bitrateStr;
    return '$container ($bitrateStr)';
  }

  String _streamModeLine() {
    final source = _currentMediaSource;
    if (source == null) return '';
    // If session info available, use it to determine direct play vs transcode
    final sess = _session;
    bool supportsDirectPlay = false;
    bool supportsDirectStream = false;
    bool supportsProbing = false;
    bool supportsTranscoding = false;
    if (sess != null) {
      supportsDirectPlay =
          sess['SupportsDirectPlay'] == true ||
          sess['SupportsDirectPlay'] == 'true';
      supportsDirectStream =
          sess['SupportsDirectStream'] == true ||
          sess['SupportsDirectStream'] == 'true';
      supportsProbing =
          sess['SupportsProbing'] == true || sess['SupportsProbing'] == 'true';
      supportsTranscoding =
          sess['SupportsTranscoding'] == true ||
          sess['SupportsTranscoding'] == 'true';
    }

    // fallback to source flags if session flags missing
    supportsDirectPlay =
        supportsDirectPlay ||
        (source['SupportsDirectPlay'] == true ||
            source['SupportsDirectPlay'] == 'true');
    supportsDirectStream =
        supportsDirectStream ||
        (source['SupportsDirectStream'] == true ||
            source['SupportsDirectStream'] == 'true');
    supportsProbing =
        supportsProbing ||
        (source['SupportsProbing'] == true ||
            source['SupportsProbing'] == 'true');
    supportsTranscoding =
        supportsTranscoding ||
        (source['SupportsTranscoding'] == true ||
            source['SupportsTranscoding'] == 'true');

    bool shouldTranscode;
    if (supportsDirectPlay || supportsDirectStream) {
      shouldTranscode = false;
    } else if (supportsProbing && !supportsTranscoding) {
      shouldTranscode = false;
    } else {
      shouldTranscode = supportsTranscoding;
    }

    if (!shouldTranscode) {
      return AppLocalizations.of(context)!.directPlay;
    }

    // Transcoding: prefer session-provided reasons if available
    List reasons = [];
    try {
      if (sess != null) {
        final ti = sess['TranscodingInfo'] ?? sess['Transcoding'];
        if (ti is Map) {
          final r =
              ti['TranscodeReasons'] ??
              ti['TranscodeReason'] ??
              ti['TranscodeReasons'];
          if (r is List) reasons = r;
        }
        if (reasons.isEmpty && sess['TranscodeReasons'] is List) {
          reasons = sess['TranscodeReasons'];
        }
      }
    } catch (_) {}

    if (reasons.isEmpty) {
      // fallback to source-level transcoding info
      final r = source['TranscodeReasons'];
      if (r is List) reasons = r;
    }

    if (reasons.isEmpty) {
      return AppLocalizations.of(context)!.transcode;
    }

    final localized = reasons
        .map((e) {
          final key = e?.toString() ?? '';
          return AppLocalizations.of(context)!.transcodeReason(key);
        })
        .where((s) => s.isNotEmpty)
        .toList();
    if (localized.isEmpty) return AppLocalizations.of(context)!.transcode;
    return '${AppLocalizations.of(context)!.transcode} (${localized.join(', ')})';
  }

  String _videoMainLine() {
    final video = _currentVideoStream;
    final source = _currentMediaSource;
    if (video == null && source == null) return '';
    if (video?["DisplayTitle"] != null) return video?["DisplayTitle"];
    // prefer session-provided info when available
    final sess = _session;
    bool isVideoDirect = true;
    if (sess != null && sess['TranscodingInfo']?['IsVideoDirect'] != null) {
      isVideoDirect =
          sess['TranscodingInfo']?['IsVideoDirect'] == true ||
          sess['TranscodingInfo']?['IsVideoDirect'] == 'true';
    }

    final height = _asInt(video?["Height"]) ?? _asInt(source?["Height"]);
    String res = '';
    if (height != null && height > 0) {
      res = '${height}p';
    }

    if (!isVideoDirect) {
      String codec = '';
      int? bitrate;
      try {
        final ti = (sess != null)
            ? (sess['TranscodingInfo'] ?? sess['Transcoding'])
            : null;
        if (ti is Map) {
          codec = (ti['VideoCodec'] ?? ti['Codec'] ?? ti['Video'] ?? '')
              .toString()
              .toUpperCase();
          bitrate = _asInt(
            ti['VideoBitrate'] ?? ti['Bitrate'] ?? ti['TranscodingBitrate'],
          );
        }
      } catch (_) {}
      codec = codec.isEmpty
          ? (video?["Codec"] ?? '').toString().toUpperCase()
          : codec;
      final bitrateStr = _formatMbpsFromBps(
        bitrate ?? _asInt(source?["TranscodingBitrate"]),
      );
      if (res.isEmpty && codec.isEmpty && bitrateStr.isEmpty) return '';
      final parts = <String>[];
      if (res.isNotEmpty) parts.add(res);
      if (codec.isNotEmpty) parts.add(codec);
      if (bitrateStr.isNotEmpty) parts.add(bitrateStr);
      return parts.join(' ');
    }

    final codec = (video?["Codec"] ?? '').toString().toUpperCase();
    if (res.isEmpty && codec.isEmpty) return '';
    if (codec.isEmpty) return res;
    if (res.isEmpty) return codec;
    return '$res $codec';
  }

  String _videoDetailLine() {
    final video = _currentVideoStream;
    final source = _currentMediaSource;
    if (video == null && source == null) return '';
    final profile = (video?["Profile"] ?? '').toString();
    final levelVal = _asInt(video?["Level"]);
    final level = levelVal != null && levelVal > 0 ? levelVal.toString() : '';
    

    String fps = _formatFps(
      video?["AverageFrameRate"] ?? video?["RealFrameRate"],
    );
    
    final bitrateVal =
        _asInt(video?["BitRate"]) ??
        _asInt(source?["Bitrate"]) ??
        _asInt(source?["TranscodingBitrate"]);
    final bitrateStr = _formatMbpsFromBps(bitrateVal);
    final parts = <String>[];
    if (profile.isNotEmpty) {
      parts.add(profile);
    }
    if (level.isNotEmpty) {
      parts.add(level);
    }
    if (bitrateStr.isNotEmpty) {
      parts.add(bitrateStr);
    }
    if (fps.isNotEmpty) {
      parts.add('$fps ${AppLocalizations.of(context)!.fpsSuffix}');
    }
    return parts.join(' ');
  }

  String _videoModeLine() {
    final source = _currentMediaSource;
    if (source == null) return '';
    final transcodingUrl = source["TranscodingUrl"];
    if (transcodingUrl == null || transcodingUrl.toString().isEmpty) {
      return AppLocalizations.of(context)!.directPlay;
    }
    final sess = _session;
    bool isVideoDirect = true;
    if (sess != null && sess['TranscodingInfo']?['IsVideoDirect'] != null) {
      isVideoDirect =
          sess['TranscodingInfo']?['IsVideoDirect'] == true ||
          sess['TranscodingInfo']?['IsVideoDirect'] == 'true';
    }

    if (isVideoDirect) {
      return AppLocalizations.of(context)!.directPlay;
    }
    String vcodec = "";
    int? bitrate;
    final ti = (sess != null)
        ? (sess['TranscodingInfo'] ?? sess['Transcoding'])
        : null;
    if (ti is Map) {
      vcodec = (ti['VideoCodec'] ?? ti['Codec'] ?? '').toString();
      bitrate = _asInt(
        ti['VideoBitrate'] ?? ti['VideoBitrate'] ?? ti['TranscodingBitrate'],
      );
    }

    final mbps = _formatMbpsFromBps(bitrate);
    if (mbps.isEmpty) {
      return '${AppLocalizations.of(context)!.transcode} ($vcodec)';
    }
    return '${AppLocalizations.of(context)!.transcode} ($vcodec $mbps)';
  }

  String _currentPlayMethod() {
    final source = _currentMediaSource;
    if (_playbackCorrection == 1) {
      return 'Transcode';
    }
    if (source == null) return 'DirectPlay';
    if (source["SupportsDirectPlay"] == true) {
      return 'DirectStream';
    }
    if (source["SupportsDirectStream"] == true) {
      return 'DirectStream';
    }
    return 'Transcode';
  }

  String _audioMainLine() {
    final audio = _currentAudioStream;
    if (audio == null) return '';
    final lang = (audio["Language"] ?? '').toString();
    final title = (audio["DisplayTitle"] ?? '').toString();
    String languageLabel;
    if (title.isNotEmpty) {
      return title;
    } else if (lang.isNotEmpty) {
      languageLabel = lang;
    } else {
      languageLabel = AppLocalizations.of(context)!.unknown;
    }
    // determine if audio is direct from session
    final source = _currentMediaSource;
    final sess = _session;
    bool isAudioDirect = true;
    if (sess != null && sess['TranscodingInfo']?['IsAudioDirect'] != null) {
      isAudioDirect =
          sess['TranscodingInfo']?['IsAudioDirect'] == true ||
          sess['TranscodingInfo']?['IsAudioDirect'] == 'true';
    }

    String codec = (audio["Codec"] ?? '').toString().toUpperCase();
    final channels = _asInt(audio["Channels"]);
    if (!isAudioDirect) {
      try {
        final ti = (sess != null)
            ? (sess['TranscodingInfo'] ?? sess['Transcoding'])
            : null;
        if (ti is Map) {
          codec = (ti['AudioCodec'] ?? ti['Audio'] ?? ti['Codec'] ?? codec)
              .toString()
              .toUpperCase();
        }
      } catch (_) {}
    }
    String channelLabel = '';
    if (channels != null) {
      if (channels == 2) {
        channelLabel = AppLocalizations.of(context)!.stereo;
      } else {
        channelLabel = '$channels ch';
      }
    }
    final parts = <String>[languageLabel];
    if (codec.isNotEmpty) {
      parts.add(codec);
    }
    if (channelLabel.isNotEmpty) {
      parts.add(channelLabel);
    }
    final defaultIndex = _asInt(source?["DefaultAudioStreamIndex"]);
    if (defaultIndex != null && audio["Index"] == defaultIndex) {
      parts.add(AppLocalizations.of(context)!.defaultMarker);
    }
    return parts.join(' ');
  }

  String _audioDetailLine() {
    final audio = _currentAudioStream;
    if (audio == null) return '';
    // prefer session transcoding audio bitrate when available
   
    int? bitrate = _asInt(audio["BitRate"]);
    final sampleRate = _asInt(audio["SampleRate"]);
    
    final kbps = _formatKbps(bitrate);
    final hz = _formatHz(sampleRate);
    if (kbps.isEmpty && hz.isEmpty) return '';
    if (hz.isEmpty) return kbps;
    if (kbps.isEmpty) return hz;
    return '$kbps $hz';
  }

  String _audioModeLine() {
    final source = _currentMediaSource;
    if (source == null) return '';
    final transcodingUrl = source["TranscodingUrl"];
    if (transcodingUrl == null || transcodingUrl.toString().isEmpty) {
      return AppLocalizations.of(context)!.directPlay;
    }
    final sess = _session;
    bool isAudioDirect = true;
    if (sess != null && sess['TranscodingInfo']?['IsAudioDirect'] != null) {
      isAudioDirect =
          sess['TranscodingInfo']?['IsAudioDirect'] == true ||
          sess['TranscodingInfo']?['IsAudioDirect'] == 'true';
    }
    if (isAudioDirect) {
      return AppLocalizations.of(context)!.directPlay;
    }
    int? bitrate;
    String codec = '';
    if (sess != null) {
      final ti = sess['TranscodingInfo'] ?? sess['Transcoding'];
      if (ti is Map) {
        bitrate = _asInt(
          ti['AudioBitrate'] ?? ti['AudioBitrate'] ?? ti['TranscodingBitrate'],
        );
      }
       
        if (ti is Map) {
          codec = (ti['AudioCodec'] ?? ti['Audio'] ?? ti['Codec'] ?? codec)
              .toString()
              .toUpperCase();
        }
    }
    final kbps = _formatKbps(bitrate);
    if (codec.isEmpty && kbps.isEmpty) {
      return AppLocalizations.of(context)!.transcode;
    }
    if (kbps.isEmpty) {
      return '${AppLocalizations.of(context)!.transcode} ($codec)';
    }
    if (codec.isEmpty) {
      return '${AppLocalizations.of(context)!.transcode} ($kbps)';
    }
    return '${AppLocalizations.of(context)!.transcode} ($codec $kbps)';
  }

  void _startProgressTracking() {
    _progressTimer = Timer.periodic(const Duration(milliseconds: 200), (
      _,
    ) async {
      final playerState = _playerKey.currentState;
      if (playerState == null || !mounted) return;
      int positionMs;
      int durationMs;
      int bufferedMs;
      bool isPlaying;
      try {
        positionMs = await playerState.getPositionMs();
        durationMs = await playerState.getDurationMs();
        bufferedMs = await playerState.getBufferedPositionMs();
        isPlaying = await playerState.isPlaying();
      } catch (_) {
        return;
      }
      if (!mounted) return;
      if (isPlaying) {
        _loadSessionForCurrent();
      }
      setState(() {
        _position = Duration(milliseconds: positionMs);
        _duration = Duration(milliseconds: durationMs);
        _buffered = Duration(milliseconds: bufferedMs);
        _isPlaying = isPlaying;
        _updateCurrentSubtitleLocked();
      });
      // detect ended
      if (!_endedHandled && durationMs > 0) {
        final nearEnd = positionMs >= (durationMs - 1000);
        if (nearEnd && !_isPlaying) {
          _onPlaybackEnded();
          _endedHandled = true;
        }
      }
      if (_isLoading && _isPlaying && widget.playbackPositionTicks > 0) {
        final targetMs = widget.playbackPositionTicks ~/ 10000;
        if (positionMs >= targetMs && positionMs > 0) {
          setState(() {
            _isLoading = false;
          });
        }
      }
      _progressTick++;
      if (_progressTick >= 25) {
        _progressTick = 0;
        await _reportProgress(_position.inMilliseconds * 10000);
      }
    });
  }

  void _onPlaybackEnded() async {
    if (!mounted) return;

    if (_playMode == 1) {
      // single loop: seek to start and play
      await _playerKey.currentState?.seekTo(Duration.zero);
      await _playerKey.currentState?.play();
      setState(() {
        _endedHandled = false;
      });
      return;
    } else if (_playMode == 0) {
      // list loop: try to get next episode from series listing
      final seriesId = _mediaInfo['SeriesId']?.toString();

      if (seriesId == null) return;

      final model = context.read<AppModel>();
      List nextList = await model.getSeriesList(seriesId);

      final firstSource = _media['MediaSources']?[0];
      String currentItemId = firstSource?['ItemId']?.toString() ?? '';
      int idx = nextList.indexWhere(
        (it) => it is Map && it['Id']?.toString() == currentItemId,
      );
      int nextIdx = nextList.isEmpty
          ? -1
          : (idx >= 0 ? (idx + 1) % nextList.length : 0);

      final nextItem = nextList.isNotEmpty ? nextList[nextIdx] : null;
      if (nextItem is Map) {
        final nextId = nextItem['Id']?.toString() ?? '';
        if (nextId.isNotEmpty && mounted) {
          Navigator.of(context).pushReplacement(
            MaterialPageRoute(
              builder: (context) => PlayerScreen(
                mediaId: nextId,
                isSeries: widget.isSeries,
                playbackPositionTicks: 0,
              ),
            ),
          );
          return;
        }
      }
    }

    // default: no loop or fallback — pause and report stopped
    await _playerKey.currentState?.pause();
    final ticks = _position.inMilliseconds * 10000;
    await _reportProgress(ticks, eventName: 'Stopped');
  }

  Future<void> _reportProgress(int ticks, {String? eventName}) async {
    Map body = {
      "VolumeLevel": 100,
      "IsMuted": false,
      "IsPaused": true,
      "RepeatMode": "RepeatNone",
      "Shuffle": false,
      "SubtitleOffset": 0,
      "PlaybackRate": 1,
      "MaxStreamingBitrate": 60000000,
      "PositionTicks": ticks,
      "PlaybackStartTimeTicks": DateTime.now().millisecondsSinceEpoch * 10000,
      "SubtitleStreamIndex": _selectedSubtitleIndex,
      "AudioStreamIndex": _selectedAudioIndex,
      "SeekableRanges": [
        {"start": 0, "end": _media["MediaSources"][0]["RunTimeTicks"]},
      ],
      "PlayMethod": _currentPlayMethod(),
      "PlaySessionId": _media["PlaySessionId"],
      "MediaSourceId": _media["MediaSources"][0]["Id"],
      "CanSeek": true,
      "ItemId": _media["MediaSources"][0]["ItemId"],
    };
    if (eventName != null && eventName.isNotEmpty) {
      body["EventName"] = eventName;
    }
    await context.read<AppModel>().reportPlaybackProgress(body);
  }

  Future<void> _playingReport() async {
    int ticks = _position.inMilliseconds * 10000;
    Map body = {
      "VolumeLevel": 100,
      "IsMuted": false,
      "IsPaused": false,
      "RepeatMode": "RepeatNone",
      "Shuffle": false,
      "SubtitleOffset": 0,
      "PlaybackRate": 1,
      "MaxStreamingBitrate": 60000000,
      "PositionTicks": ticks,
      "PlaybackStartTimeTicks": DateTime.now().millisecondsSinceEpoch * 10000,
      "SubtitleStreamIndex": _selectedSubtitleIndex,
      "AudioStreamIndex": _selectedAudioIndex,
      "BufferedRanges": [],
      "SeekableRanges": [
        {"start": 0, "end": _media["MediaSources"][0]["RunTimeTicks"]},
      ],
      "PlayMethod": _currentPlayMethod(),
      "PlaySessionId": _media["PlaySessionId"],
      "MediaSourceId": _media["MediaSources"][0]["Id"],
      "CanSeek": true,
      "ItemId": _media["MediaSources"][0]["ItemId"],
    };

    await context.read<AppModel>().playing(body);
  }

  void _handleKeyDown(LogicalKeyboardKey key) {
    _keyDownTime = DateTime.now();
    _startSeekTimer(key);
    setState(() {
      _isShowInfo = true;
    });
  }

  void _handleKeyUp() async {
    _keyDownTime = null;
    _stopSeekTimer();
    setState(() {
      _isShowInfo = false;
    });
  }

  void _startSeekTimer(LogicalKeyboardKey key) {
    const seekInterval = Duration(milliseconds: 200);
    Duration offset = const Duration(seconds: 30);

    _seekTimer = Timer.periodic(seekInterval, (timer) {
      final isLongPress =
          DateTime.now().difference(_keyDownTime!) >
          const Duration(milliseconds: 500);

      if (isLongPress) {
        setState(() {
          if (key == LogicalKeyboardKey.arrowLeft) {
            _seekRelative(-offset);
          } else if (key == LogicalKeyboardKey.arrowRight) {
            _seekRelative(offset);
          }
          if (!_isPlaying) {
            _play();
          }
        });
      }
    });
  }

  void _stopSeekTimer() {
    _seekTimer?.cancel();
    _seekTimer = null;
  }

  Future<void> _seekRelative(Duration offset) async {
    if (_playerKey.currentState == null) return;
    final target = _position + offset;
    final safeTarget = target < Duration.zero ? Duration.zero : target;
    setState(() {
      _position = safeTarget;
      _updateCurrentSubtitleLocked();
    });
    await _playerKey.currentState!.seekTo(safeTarget);
  }

  Future<void> _play() async {
    if (_playerKey.currentState == null) return;
    _endedHandled = false;
    await _playerKey.currentState!.play();
    setState(() {
      _isPlaying = true;
    });
  }

  Future<void> _pause() async {
    if (_playerKey.currentState == null) return;
    await _playerKey.currentState!.pause();
    setState(() {
      _isPlaying = false;
    });
  }

  void _onPlayerError(String code, String? message) {
    if (_hasTriedTranscodeFallback) {
      return;
    }
    _hasTriedTranscodeFallback = true;
    _fallbackToServerTranscode();
  }

  Future<void> _fallbackToServerTranscode() async {
    if (!mounted) return;
    final model = context.read<AppModel>();
    final currentTicks = _position.inMilliseconds * 10000;
    Map media;
    try {
      media = await model.getPlaybackInfo(
        widget.mediaId,
        currentTicks,
        widget.isSeries,
        disableHevc: true,
      );
    } catch (_) {
      return;
    }
    final serverUrl = model.serverUrl;
    if (serverUrl == null) {
      return;
    }
    final sources = media["MediaSources"];
    if (sources is! List || sources.isEmpty || sources[0] is! Map) {
      return;
    }
    final source = sources[0] as Map;
    String? path = source["DirectStreamUrl"] as String?;
    if (path == null || path.isEmpty) {
      path = source["TranscodingUrl"] as String?;
    }
    if (path == null || path.isEmpty) {
      return;
    }
    final videoUrl = "$serverUrl/emby$path";
    List<_SubtitleCue> cues = _subtitleCues;
    if (_selectedSubtitleIndex != -1) {
      cues = await _fetchSubtitleCues(media, _selectedSubtitleIndex);
    }
    if (!mounted) return;
    setState(() {
      _media = media;
      _videoUrl = videoUrl;
      _subtitleCues = cues;
      _currentSubtitle = '';
    });
    if (_playerKey.currentState != null) {
      await _playerKey.currentState!.updateSource(
        videoUrl: videoUrl,
        subtitleUrl: null,
        positionMs: _position.inMilliseconds,
        autoPlay: true,
      );
    }
  }

  Widget _buildEpisodesListForSidWidget(String sid, List items) {
    final currentItemId = _media['MediaSources']?[0]?['ItemId']?.toString();
    final currentIndex = items.indexWhere(
      (e) => e is Map && e['Id']?.toString() == currentItemId,
    );
    final focusIndex = currentIndex >= 0 ? currentIndex : 0;

    // ensure scroll controller exists
    _seriesScrollControllers[sid] ??= ScrollController();

    // ensure focus nodes exist
    final nodes = _seriesFocusNodes[sid] ??= List<FocusNode>.generate(
      items.length,
      (_) => FocusNode(),
    );
    if (nodes.length < items.length) {
      nodes.addAll(
        List.generate(items.length - nodes.length, (_) => FocusNode()),
      );
    }

    // after frame, request focus and scroll to item (horizontal)
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      try {
        final controller = _seriesScrollControllers[sid]!;
        // approximate item width
        final itemExtent = 180.0;
        final max = controller.hasClients
            ? controller.position.maxScrollExtent
            : 0.0;
        final offset = (focusIndex * itemExtent).clamp(0.0, max);
        if (controller.hasClients) controller.jumpTo(offset);
        nodes[focusIndex].requestFocus();
      } catch (_) {}
    });
    double height = 290;
    List aspectRatios = items
        .map((e) => (e["PrimaryImageAspectRatio"] ?? 1).toDouble())
        .toList();
    double maxAspectRatio = aspectRatios.isEmpty
        ? 1
        : aspectRatios.reduce(
            (currMax, ratio) => ratio > currMax ? ratio : currMax,
          );
    double width = maxAspectRatio * height - (maxAspectRatio > 1 ? 220 : 90);

    return SingleChildScrollView(
      child: SizedBox(
        height: height,
        child: ListView.separated(
          controller: _seriesScrollControllers[sid],
          scrollDirection: Axis.horizontal,
          itemCount: items.length,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          separatorBuilder: (_, _) => const SizedBox(width: 12),
          itemBuilder: (context, index) {
            final it = items[index];
            if (it is! Map) return const SizedBox.shrink();
            final node = _seriesFocusNodes[sid]![index];
            final ticks = it['UserData']?['PlaybackPositionTicks'] ?? 0;
            final isCurrent =
                it['Id']?.toString() ==
                _media['MediaSources']?[0]?['ItemId']?.toString();
            return Focus(
              focusNode: node,
              onKeyEvent: (node, event) {
                if (!isCurrent && event is KeyDownEvent) {
                  final key = event.logicalKey;
                  if (key == LogicalKeyboardKey.accept ||
                      key == LogicalKeyboardKey.select ||
                      key == LogicalKeyboardKey.enter) {
                    Navigator.of(context).pop();
                    Navigator.of(context).pushReplacement(
                      MaterialPageRoute(
                        builder: (context) => PlayerScreen(
                          mediaId: it['Id']?.toString() ?? '',
                          isSeries: (it['Type'] ?? '') == 'Series',
                          playbackPositionTicks: ticks is int ? ticks : 0,
                        ),
                      ),
                    );
                    return KeyEventResult.handled;
                  }
                }
                return KeyEventResult.ignored;
              },
              child: Builder(
                builder: (context) {
                  final isFocused = Focus.of(context).hasFocus;

                  return SizedBox(
                    width: width,
                    child: Stack(
                      children: [
                        if (isCurrent)
                          Positioned.fill(
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(12),
                              child: Container(color: Colors.white24),
                            ),
                          ),
                        BuildItem(
                          item: it,
                          width: width,
                          isContinueWatching: false,
                          isMyLibrary: false,
                          isFocused: isFocused,
                          isShowOverview: true,
                          imageBoxFit: BoxFit.fitHeight,
                        ),
                      ],
                    ),
                  );
                },
              ),
            );
          },
        ),
      ),
    );
  }

  void _showAlert(BuildContext context) {
    int selectedTab = 0;
    final i10n = AppLocalizations.of(context)!;
    showDialog(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setStateDialog) {
            final seriesId = _mediaInfo['SeriesId']?.toString();

            final tabs = <String>[];
            if (seriesId != null && seriesId.isNotEmpty) {
              tabs.addAll([i10n.episodes, i10n.playback]);
            }
            tabs.addAll([
              i10n.info,
              i10n.subtitles,
              i10n.audio,
              i10n.playbackCorrection,
            ]);

            Widget buildTab(String label, int index) {
              final bool isSelected = selectedTab == index;
              return Focus(
                autofocus: isSelected,
                onFocusChange: (hasFocus) {
                  if (hasFocus) {
                    setStateDialog(() {
                      selectedTab = index;
                    });
                  }
                },
                child: GestureDetector(
                  onTap: () {
                    setStateDialog(() {
                      selectedTab = index;
                    });
                  },
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 8,
                    ),
                    decoration: BoxDecoration(
                      color: isSelected
                          ? Theme.of(context).colorScheme.primary
                          : Colors.transparent,
                    ),
                    child: Text(
                      label,
                      style: const TextStyle(color: Colors.white, fontSize: 20),
                    ),
                  ),
                ),
              );
            }

            Widget buildInfoTab() {
              String yearText = '';
              String overview = '';

              final year = _mediaInfo["ProductionYear"];
              if (year != null) {
                yearText = year.toString();
              } else {
                final date = _mediaInfo["PremiereDate"]?.toString();
                if (date != null && date.length >= 4) {
                  yearText = date.substring(0, 4);
                }
              }
              overview = (_mediaInfo["Overview"] ?? '').toString();
              if (overview.trim().isEmpty) {
                overview =
                    (_mediaInfo["SeriesOverview"] ??
                            _mediaInfo["ShortOverview"] ??
                            '')
                        .toString();
              }

              final video = _currentVideoStream;
              String resolution = video?["DisplayTitle"] ?? '';

              final metaParts = <String>[];
              if (yearText.isNotEmpty) {
                metaParts.add(yearText);
              }
              if (resolution.isNotEmpty) {
                metaParts.add(resolution);
              }
              return SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      (_mediaInfo['SeriesName'] ??
                          _mediaInfo['Name'] ??
                          'Unknown title'),
                      style: TextStyle(
                        fontSize: 20,
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                      ),
                      maxLines: 1,
                      textAlign: TextAlign.center,
                      overflow: TextOverflow.fade,
                    ),

                    Text(
                      _mediaInfo['ParentIndexNumber'] != null
                          ? "S${_mediaInfo['ParentIndexNumber']}:E${_mediaInfo['IndexNumber']} ${_mediaInfo['Name']}"
                          : "${_mediaInfo['ProductionYear'] ?? '--'}",
                      style: TextStyle(
                        fontSize: 16,
                        color: Colors.grey[300],
                        fontWeight: FontWeight.bold,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.fade,
                    ),
                    if (metaParts.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        metaParts.join(' · '),
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 16,
                        ),
                      ),
                    ],
                    const SizedBox(height: 8),
                    Text(
                      formatFileSize(_media['MediaSources']?[0]['Size'] ?? 0),
                      style: const TextStyle(color: Colors.white, fontSize: 16),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      overview,
                      style: const TextStyle(color: Colors.white, fontSize: 16),
                    ),
                  ],
                ),
              );
            }

            Widget buildEpisodesTab() {
              final sid = seriesId;
              if (sid == null || sid.isEmpty) {
                return const SizedBox.shrink();
              }
              final model = context.read<AppModel>();

              // If cached, show immediately
              final cached = _seriesCache[sid];
              if (cached != null) {
                return _buildEpisodesListForSidWidget(sid, cached);
              }

              // create or reuse a loading future so we don't request repeatedly
              _seriesLoadingFutures[sid] ??= model.getSeriesList(sid);
              return FutureBuilder<List>(
                future: _seriesLoadingFutures[sid],
                builder: (context, snap) {
                  if (snap.connectionState != ConnectionState.done) {
                    return const Center(child: CircularProgressIndicator());
                  }
                  final items = snap.data ?? [];
                  // cache the result
                  WidgetsBinding.instance.addPostFrameCallback((_) {
                    if (mounted) {
                      setState(() {
                        _seriesCache[sid] = items;
                      });
                    }
                  });
                  if (items.isEmpty) {
                    return Center(
                      child: Text(
                        i10n.noEpisodesFound,
                        style: TextStyle(color: Colors.white),
                      ),
                    );
                  }
                  return _buildEpisodesListForSidWidget(sid, items);
                },
              );
            }

            Widget buildSubtitleTab() {
              return ListView.builder(
                itemCount: _subtitleTracks.length + 1,
                itemBuilder: (context, index) {
                  final bool isCloseRow = index == 0;
                  final int rowIndex = isCloseRow
                      ? -1
                      : _subtitleTracks[index - 1]["Index"] as int;
                  return Focus(
                    autofocus: index == 0,
                    child: Builder(
                      builder: (context) {
                        final node = Focus.of(context);
                        final isFocused = node.hasFocus;
                        final bool isSelected =
                            _selectedSubtitleIndex == rowIndex;
                        Color? color;
                        if (isFocused) {
                          color = Theme.of(context).colorScheme.primary;
                        } else if (isSelected) {
                          color = Colors.white24;
                        }
                        if (isCloseRow) {
                          return ListTile(
                            title: Text(
                              i10n.disableSubtitles,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 18,
                              ),
                            ),
                            tileColor: color,
                            onTap: () {
                              _updateSubtitle(-1);
                              Navigator.of(context).pop();
                            },
                          );
                        }
                        final track = _subtitleTracks[index - 1];
                        return ListTile(
                          title: Text(
                            '${track["Language"] ?? i10n.unknown} - ${track["DisplayTitle"]}',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                            ),
                          ),
                          tileColor: color,
                          onTap: () {
                            _updateSubtitle(track["Index"]);
                            Navigator.of(context).pop();
                          },
                        );
                      },
                    ),
                  );
                },
              );
            }

            Widget buildAudioTab() {
              return ListView.builder(
                itemCount: _audioTracks.length,
                itemBuilder: (context, index) {
                  final track = _audioTracks[index];
                  final source = _currentMediaSource;
                  final defaultIndex = _asInt(
                    source?["DefaultAudioStreamIndex"],
                  );
                  final lang = (track["Language"] ?? '').toString();
                  final title = (track["DisplayTitle"] ?? '').toString();
                  String label;
                  if (title.isNotEmpty) {
                    label = title;
                  } else if (lang.isNotEmpty) {
                    label = lang;
                  } else {
                    label = i10n.unknown;
                  }
                  final codec = (track["Codec"] ?? '').toString().toUpperCase();
                  final channels = track["Channels"];
                  String channelLabel = '';
                  if (channels is int) {
                    if (channels == 2) {
                      channelLabel = i10n.stereo;
                    } else {
                      channelLabel = '$channels ch';
                    }
                  }
                  final parts = <String>[label];
                  if (codec.isNotEmpty) {
                    parts.add(codec);
                  }
                  if (channelLabel.isNotEmpty) {
                    parts.add(channelLabel);
                  }
                  if (defaultIndex != null && track["Index"] == defaultIndex) {
                    parts.add(i10n.defaultMarker);
                  }
                  final showName = title.isNotEmpty ? title : parts.join(' ');
                  return Focus(
                    autofocus: index == 0,
                    child: Builder(
                      builder: (context) {
                        final node = Focus.of(context);
                        final isFocused = node.hasFocus;
                        final bool isSelected =
                            _selectedAudioIndex == track["Index"];
                        Color? color;
                        if (isFocused) {
                          color = Theme.of(context).colorScheme.primary;
                        } else if (isSelected) {
                          color = Colors.white24;
                        }
                        return ListTile(
                          title: Text(
                            showName,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                            ),
                          ),
                          tileColor: color,
                          onTap: () {
                            _updateAudio(track["Index"]);
                            Navigator.of(context).pop();
                          },
                        );
                      },
                    ),
                  );
                },
              );
            }

            Widget buildPlaybackCorrectionTab() {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Focus(
                    autofocus: _playbackCorrection == 0,
                    child: Builder(
                      builder: (context) {
                        final node = Focus.of(context);
                        final isFocused = node.hasFocus;
                        final bool isSelected = _playbackCorrection == 0;
                        Color? color;
                        if (isFocused) {
                          color = Theme.of(context).colorScheme.primary;
                        } else if (isSelected) {
                          color = Colors.white24;
                        }
                        return ListTile(
                          title: Text(
                            i10n.playbackCorrectionOff,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                            ),
                          ),
                          tileColor: color,
                          enabled: _playbackCorrection != 0,
                          onTap: () async {
                            setState(() {
                              _playbackCorrection = 0;
                            });
                            await _savePlaybackCorrection();
                            // reload playback info/source
                            if (!mounted) return;
                            Navigator.of(context).pop();
                            Navigator.of(context).pushReplacement(
                              MaterialPageRoute(
                                builder: (context) => PlayerScreen(
                                  mediaId: widget.mediaId,
                                  isSeries: widget.isSeries,
                                  playbackPositionTicks:
                                      _position.inMilliseconds * 10000,
                                ),
                              ),
                            );
                          },
                        );
                      },
                    ),
                  ),

                  Focus(
                    autofocus: _playbackCorrection == 1,
                    child: Builder(
                      builder: (context) {
                        final node = Focus.of(context);
                        final isFocused = node.hasFocus;
                        final bool isSelected = _playbackCorrection == 1;
                        Color? color;
                        if (isFocused) {
                          color = Theme.of(context).colorScheme.primary;
                        } else if (isSelected) {
                          color = Colors.white24;
                        }
                        return ListTile(
                          title: Text(
                            i10n.playbackCorrectionServer,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                            ),
                          ),
                          tileColor: color,
                          enabled: _playbackCorrection != 1,
                          onTap: () async {
                            setState(() {
                              _playbackCorrection = 1;
                            });
                            await _savePlaybackCorrection();
                            // reload playback info/source and use server transcode
                            if (!mounted) return;
                            Navigator.of(context).pop();
                            Navigator.of(context).pushReplacement(
                              MaterialPageRoute(
                                builder: (context) => PlayerScreen(
                                  mediaId: widget.mediaId,
                                  isSeries: widget.isSeries,
                                  playbackPositionTicks:
                                      _position.inMilliseconds * 10000,
                                ),
                              ),
                            );
                          },
                        );
                      },
                    ),
                  ),
                ],
              );
            }

            Widget content;
            final currentTab = tabs[selectedTab];

            if (currentTab == i10n.info) {
              content = buildInfoTab();
            } else if (currentTab == i10n.episodes) {
              content = buildEpisodesTab();
            } else if (currentTab == i10n.subtitles) {
              content = buildSubtitleTab();
            } else if (currentTab == i10n.audio) {
              content = buildAudioTab();
            } else if (currentTab == i10n.playbackCorrection) {
              content = buildPlaybackCorrectionTab();
            } else if (currentTab == i10n.playback) {
              content = ListView.builder(
                itemCount: 3,
                itemBuilder: (context, index) {
                  final labels = [i10n.listLoop, i10n.singleLoop, i10n.noLoop];
                  final isSelected = _playMode == index;
                  return Focus(
                    autofocus: index == 0,
                    child: Builder(
                      builder: (context) {
                        final node = Focus.of(context);
                        final isFocused = node.hasFocus;
                        final color = isFocused
                            ? Theme.of(context).colorScheme.primary
                            : (isSelected ? Colors.white24 : null);
                        return ListTile(
                          title: Text(
                            labels[index],
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                            ),
                          ),
                          tileColor: color,
                          onTap: () {
                            setState(() {
                              _playMode = index;
                              _endedHandled = false;
                            });
                            _savePlayMode();
                            Navigator.of(context).pop();
                          },
                        );
                      },
                    ),
                  );
                },
              );
            } else {
              content = const SizedBox.shrink();
            }

            return AlertDialog(
              backgroundColor: Colors.black.withValues(alpha: 0.6),
              contentPadding: const EdgeInsets.all(16),
              content: SizedBox(
                width: 900,
                height: 600,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        for (int i = 0; i < tabs.length; i++) ...[
                          buildTab(tabs[i], i),
                          const SizedBox(width: 12),
                        ],
                      ],
                    ),
                    const SizedBox(height: 16),
                    Expanded(child: content),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  void _updateSubtitle(int index) async {
    if (_videoUrl == null) return;
    setState(() {
      _selectedSubtitleIndex = index;
      _subtitleCues = [];
      _currentSubtitle = '';
    });
    final model = context.read<AppModel>();
    final serverUrl = model.serverUrl!;
    final newVideoUrl =
        "$serverUrl/emby${_media['MediaSources'][0]['DirectStreamUrl']}";
    if (_selectedSubtitleIndex != -1) {
      final cues = await _fetchSubtitleCues(_media, _selectedSubtitleIndex);
      setState(() {
        _subtitleCues = cues;
        _currentSubtitle = '';
      });
    }
    if (_playerKey.currentState != null) {
      await _playerKey.currentState!.updateSource(
        videoUrl: newVideoUrl,
        subtitleUrl: null,
        positionMs: _position.inMilliseconds,
        autoPlay: true,
      );
    }
  }

  void _updateAudio(int index) async {
    if (_videoUrl == null) return;
    final ticks = _position.inMilliseconds * 10000;
    setState(() {
      _selectedAudioIndex = index;
      _isLoading = true;
    });
    await _reportProgress(ticks, eventName: "AudioTrackChange");
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(
        builder: (context) => PlayerScreen(
          mediaId: widget.mediaId,
          isSeries: widget.isSeries,
          playbackPositionTicks: ticks,
          audioStreamIndexOverride: index,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    double width = MediaQuery.of(context).size.width;
    double height = MediaQuery.of(context).size.height;
    final duration = _duration;
    final position = _position;
    final buffered = _buffered;
    final bufferedProgress = duration.inMilliseconds > 0
        ? buffered.inMilliseconds / duration.inMilliseconds
        : 0.0;
    final progress = duration.inMilliseconds > 0
        ? position.inMilliseconds / duration.inMilliseconds
        : 0.0;
    return Scaffold(
      backgroundColor: Colors.black,
      body: Center(
        child: _videoUrl != null
            ? Stack(
                children: [
                  Positioned.fill(
                    child: Focus(
                      autofocus: true,
                      onKeyEvent: (node, event) {
                        if (event is KeyDownEvent) {
                          final offset = const Duration(seconds: 10);
                          switch (event.logicalKey) {
                            case LogicalKeyboardKey.arrowLeft:
                              _handleKeyDown(event.logicalKey);
                              _seekRelative(-offset);
                              break;
                            case LogicalKeyboardKey.arrowRight:
                              _handleKeyDown(event.logicalKey);
                              _seekRelative(offset);
                              break;
                            case LogicalKeyboardKey.accept ||
                                LogicalKeyboardKey.select ||
                                LogicalKeyboardKey.enter:
                              if (_isPlaying) {
                                _pause();
                              } else {
                                _play();
                              }
                              return KeyEventResult.handled;
                            case LogicalKeyboardKey.arrowDown ||
                                LogicalKeyboardKey.contextMenu ||
                                LogicalKeyboardKey.browserFavorites:
                              _showAlert(context);
                              return KeyEventResult.handled;
                          }
                          return KeyEventResult.ignored;
                        } else if (event is KeyUpEvent) {
                          _handleKeyUp();
                        }
                        return KeyEventResult.ignored;
                      },
                      child: ExoPlayerView(
                        key: _playerKey,
                        videoUrl: _videoUrl!,
                        subtitleUrl: null,
                        startPositionMs:
                            (widget.playbackPositionTicks) ~/ 10000,
                        onError: _onPlayerError,
                        onInitialized: () {
                          setState(() {
                            _isPlaying = true;
                          });
                          _playingReport();
                        },
                      ),
                    ),
                  ),
                  if (_currentSubtitle.isNotEmpty)
                    Positioned(
                      left: 40,
                      right: 40,
                      bottom: 80,
                      child: Text(
                        _currentSubtitle,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 26,
                          shadows: [
                            Shadow(
                              color: Colors.black,
                              offset: Offset(1, 1),
                              blurRadius: 4,
                            ),
                            Shadow(
                              color: Colors.black,
                              offset: Offset(-1, -1),
                              blurRadius: 4,
                            ),
                          ],
                        ),
                      ),
                    ),
                  if (_isLoading)
                    Positioned.fill(
                      child: Container(
                        color: Colors.black,
                        child: const Center(child: CircularProgressIndicator()),
                      ),
                    ),
                  if (!_isLoading && (_isShowInfo || !_isPlaying))
                    Positioned(
                      top: 0,
                      left: 0,
                      child: Container(
                        width: width,
                        height: height,
                        padding: const EdgeInsets.all(15),
                        color: Colors.black54,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.start,
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    (_mediaInfo['SeriesName'] ??
                                        _mediaInfo['Name'] ??
                                        'Unknown title'),
                                    style: TextStyle(
                                      fontSize: 20,
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                    ),
                                    maxLines: 1,
                                    textAlign: TextAlign.center,
                                    overflow: TextOverflow.fade,
                                  ),

                                  Text(
                                    _mediaInfo['ParentIndexNumber'] != null
                                        ? "S${_mediaInfo['ParentIndexNumber']}:E${_mediaInfo['IndexNumber']} ${_mediaInfo['Name']}"
                                        : "${_mediaInfo['ProductionYear'] ?? '--'}",
                                    style: TextStyle(
                                      fontSize: 16,
                                      color: Colors.grey[300],
                                      fontWeight: FontWeight.bold,
                                    ),
                                    maxLines: 1,
                                    overflow: TextOverflow.fade,
                                  ),
                                  const SizedBox(height: 8),
                                  Text(
                                    formatFileSize(
                                      _media['MediaSources']?[0]['Size'] ?? 0,
                                    ),
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 16,
                                    ),
                                  ),
                                  const SizedBox(height: 12),
                                  Text(
                                    AppLocalizations.of(context)!.streaming,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 12,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    _streamContainerLine(),
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 12,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    _streamModeLine(),
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 12,
                                    ),
                                  ),
                                  const SizedBox(height: 12),
                                  Text(
                                    AppLocalizations.of(context)!.videoLabel,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 12,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    _videoMainLine(),
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 12,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    _videoDetailLine(),
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 12,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    _videoModeLine(),
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 12,
                                    ),
                                  ),
                                  const SizedBox(height: 12),
                                  Text(
                                    AppLocalizations.of(context)!.audioLabel,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 12,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    _audioMainLine(),
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 12,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    _audioDetailLine(),
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 12,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    _audioModeLine(),
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                              ),
                            ),

                            SizedBox(
                              width: width - 30,
                              child: Stack(
                                children: [
                                  LinearProgressIndicator(
                                    minHeight: 6,
                                    value: bufferedProgress,
                                    backgroundColor: Colors.grey[800],
                                    valueColor: AlwaysStoppedAnimation<Color>(
                                      Colors.grey,
                                    ),
                                  ),
                                  LinearProgressIndicator(
                                    minHeight: 6,
                                    value: progress,
                                    backgroundColor: Colors.transparent,
                                    valueColor: AlwaysStoppedAnimation<Color>(
                                      Colors.pink,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(height: 8),
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Text(
                                  formatDuration(position),
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 14,
                                  ),
                                ),
                                Text(
                                  formatDuration(duration),
                                  style: TextStyle(
                                    color: Colors.grey[300],
                                    fontSize: 14,
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                  if (!_isLoading && !_isPlaying && !_isShowInfo)
                    Positioned(
                      left: width / 2 - 50,
                      top: height / 2 - 40,
                      child: Icon(
                        Icons.play_arrow,
                        size: 100,
                        color: Colors.white,
                      ),
                    ),
                  if (!_isLoading && !_isPlaying && !_isShowInfo)
                    Positioned(
                      left: 0,
                      right: 0,
                      top: (height / 2) + 180,
                      child: Text.rich(
                        textAlign: TextAlign.center,
                        TextSpan(
                          children: [
                            WidgetSpan(
                              child: Icon(
                                Icons.menu,
                                size: 20,
                                color: Colors.grey[400],
                              ),
                            ),
                            TextSpan(
                              text:
                                  ' ${AppLocalizations.of(context)!.pressMenuDownToShowMenu}',
                              style: TextStyle(
                                color: Colors.grey[400],
                                fontSize: 16,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                ],
              )
            : const CircularProgressIndicator(),
      ),
    );
  }

  @override
  void dispose() {
    _progressTimer?.cancel();
    _seekTimer?.cancel();
    // dispose series focus nodes and scroll controllers
    try {
      for (final nodes in _seriesFocusNodes.values) {
        for (final n in nodes) {
          n.dispose();
        }
      }
    } catch (_) {}
    try {
      for (final c in _seriesScrollControllers.values) {
        c.dispose();
      }
    } catch (_) {}

    super.dispose();
  }
}
