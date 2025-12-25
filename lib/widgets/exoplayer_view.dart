import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class ExoPlayerView extends StatefulWidget {
  final String videoUrl;
  final String? subtitleUrl;
  final int startPositionMs;
  final VoidCallback? onInitialized;
  final void Function(String code, String? message)? onError;

  const ExoPlayerView({
    required this.videoUrl,
    this.subtitleUrl,
    this.startPositionMs = 0,
    this.onInitialized,
    this.onError,
    super.key,
  });

  @override
  ExoPlayerViewState createState() => ExoPlayerViewState();
}

class ExoPlayerViewState extends State<ExoPlayerView> {
  MethodChannel? _channel;
  bool _initialized = false;

  Future<void> _initialize() async {
    if (_initialized || _channel == null) return;
    try {
      await _channel!.invokeMethod<Map<dynamic, dynamic>>("initialize", {
        "videoUrl": widget.videoUrl,
        "subtitleUrl": widget.subtitleUrl,
        "startPositionMs": widget.startPositionMs,
      });
      _initialized = true;
      widget.onInitialized?.call();
    } on PlatformException catch (e) {
      widget.onError?.call(e.code, e.message);
    } catch (_) {
      widget.onError?.call("PLAYER_ERROR", null);
    }
  }

  Future<void> _onPlatformViewCreated(int id) async {
    _channel = MethodChannel("emby_tv/exoplayer_$id");
    _channel!.setMethodCallHandler((call) async {
      if (call.method == "onPlayerError") {
        final args = call.arguments;
        if (args is Map) {
          final code = (args["code"] ?? "").toString();
          final message = args["message"] as String?;
          widget.onError?.call(code, message);
        } else {
          widget.onError?.call("PLAYER_ERROR", null);
        }
      }
    });
    await _initialize();
  }

  Future<void> play() async {
    await _channel?.invokeMethod("play");
  }

  Future<void> pause() async {
    await _channel?.invokeMethod("pause");
  }

  Future<void> seekTo(Duration position) async {
    await _channel?.invokeMethod("seekTo", {
      "positionMs": position.inMilliseconds,
    });
  }

  Future<int> getPositionMs() async {
    final result = await _channel?.invokeMethod<int>("getPosition");
    return result ?? 0;
  }

  Future<int> getDurationMs() async {
    final result = await _channel?.invokeMethod<int>("getDuration");
    return result ?? 0;
  }

  Future<int> getBufferedPositionMs() async {
    final result = await _channel?.invokeMethod<int>("getBufferedPosition");
    return result ?? 0;
  }

  Future<bool> isPlaying() async {
    final result = await _channel?.invokeMethod<bool>("isPlaying");
    return result ?? false;
  }

  Future<void> updateSource({
    required String videoUrl,
    String? subtitleUrl,
    int positionMs = 0,
    bool autoPlay = true,
  }) async {
    await _channel?.invokeMethod("updateSource", {
      "videoUrl": videoUrl,
      "subtitleUrl": subtitleUrl,
      "positionMs": positionMs,
      "autoPlay": autoPlay,
    });
  }

  @override
  Widget build(BuildContext context) {
    if (defaultTargetPlatform == TargetPlatform.android) {
      return AndroidView(
        viewType: "emby_tv/exoplayer_view",
        onPlatformViewCreated: _onPlatformViewCreated,
        creationParams: null,
        creationParamsCodec: const StandardMessageCodec(),
      );
    }
    return const SizedBox.shrink();
  }

  @override
  void dispose() {
    _channel?.invokeMethod("dispose");
    super.dispose();
  }
}
