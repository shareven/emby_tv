package com.xxxx.emby_tv

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.math.max
import kotlin.math.min

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        flutterEngine
            .platformViewsController
            .registry
            .registerViewFactory(
                "emby_tv/exoplayer_view",
                ExoPlayerViewFactory(messenger, this)
            )
        MethodChannel(messenger, "emby_tv/device_capabilities").setMethodCallHandler { call, result ->
            if (call.method == "getCapabilities") {
                try {
                    val caps = queryDeviceCapabilities()
                    result.success(caps)
                } catch (e: Exception) {
                    result.error("CAP_ERROR", e.message, null)
                }
            } else {
                result.notImplemented()
            }
        }
    }

    private fun queryDeviceCapabilities(): Map<String, Any> {
        val videoCodecs = mutableSetOf<String>()
        val audioCodecs = mutableSetOf<String>()
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in codecList.codecInfos) {
            if (info.isEncoder) {
                continue
            }
            for (type in info.supportedTypes) {
                when {
                    type.equals("video/avc", true) -> videoCodecs.add("h264")
                    type.equals("video/hevc", true) -> {
                        videoCodecs.add("hevc")
                        videoCodecs.add("h265")
                    }
                    type.equals("video/vp9", true) ||
                        type.equals("video/x-vnd.on2.vp9", true) -> videoCodecs.add("vp9")
                    type.equals("video/vp8", true) ||
                        type.equals("video/x-vnd.on2.vp8", true) -> videoCodecs.add("vp8")
                    type.equals("video/av01", true) -> videoCodecs.add("av1")
                    type.equals("audio/mp4a-latm", true) -> audioCodecs.add("aac")
                    type.equals("audio/opus", true) -> audioCodecs.add("opus")
                    type.equals("audio/ac3", true) -> audioCodecs.add("ac3")
                    type.equals("audio/eac3", true) -> audioCodecs.add("eac3")
                    type.equals("audio/vorbis", true) -> audioCodecs.add("vorbis")
                    type.equals("audio/mpeg", true) -> audioCodecs.add("mp3")
                    type.equals("audio/flac", true) -> audioCodecs.add("flac")
                    type.equals("audio/true-hd", true) ||
                        type.equals("audio/vnd.dts", true) ||
                        type.equals("audio/vnd.dts.hd", true) -> audioCodecs.add("dts")
                }
            }
        }
        if (videoCodecs.isEmpty()) {
            videoCodecs.add("h264")
        }
        if (audioCodecs.isEmpty()) {
            audioCodecs.add("aac")
            audioCodecs.add("mp3")
        }
        val metrics = resources.displayMetrics
        val maxWidth = max(metrics.widthPixels, metrics.heightPixels)
        val maxHeight = min(metrics.widthPixels, metrics.heightPixels)
        val containers = listOf("mp4", "m4v", "mov", "3gp", "mkv", "webm", "ts", "flv")
        return mapOf(
            "maxWidth" to maxWidth,
            "maxHeight" to maxHeight,
            "videoCodecs" to videoCodecs.toList(),
            "audioCodecs" to audioCodecs.toList(),
            "containers" to containers
        )
    }
}
