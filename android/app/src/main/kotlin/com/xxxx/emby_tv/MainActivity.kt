package com.xxxx.emby_tv

import android.content.Context
import android.graphics.Point
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.view.WindowManager
import androidx.core.content.getSystemService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.text.any
import android.os.Build
import android.media.MediaCodecInfo.CodecProfileLevel



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
                    val caps = queryDeviceCapabilities(context)
                    result.success(caps)
                } catch (e: Exception) {
                    result.error("CAP_ERROR", e.message, null)
                }
            } else {
                result.notImplemented()
            }
        }
    }




    /**
     * 兼容 2015 (Android 6.0) - 2025 (Android 15+) 的设备能力探测
     */
    private fun queryDeviceCapabilities(context: Context): Map<String, Any> {
        val videoCodecs = mutableSetOf<String>()
        val audioCodecs = mutableSetOf<String>()
        val videoProfiles = mutableListOf<Map<String, Any>>()

        // 1. 获取编解码器列表 (使用 REGULAR_CODECS 过滤掉不稳定的软件插件)
        // 在 API 21+ 均可用
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue

            for (type in info.supportedTypes) {
                try {
                    val caps = info.getCapabilitiesForType(type)

                    when {
                        // --- H.264 / AVC ---
                        type.equals("video/avc", ignoreCase = true) -> {
                            videoCodecs.add("h264")
                            val maxLevel = caps.profileLevels?.maxOfOrNull { mapAvcLevel(it.level) } ?: 41
                            videoProfiles.add(mapOf("Codec" to "h264", "MaxLevel" to maxLevel))
                        }

                        // --- H.265 / HEVC ---
                        type.equals("video/hevc", ignoreCase = true) -> {
                            videoCodecs.add("hevc")
                            videoCodecs.add("h265")
                            // 探测 10bit 支持 (2017年后设备主流)
                            val isMain10 = caps.profileLevels?.any {
                                it.profile >= CodecProfileLevel.HEVCProfileMain10
                            } ?: false
                            videoProfiles.add(mapOf(
                                "Codec" to "hevc",
                                "Profile" to if (isMain10) "Main10" else "Main"
                            ))
                        }

                        // --- AV1 (针对 2021-2025 年新设备) ---
                        type.equals("video/av01", ignoreCase = true) -> videoCodecs.add("av1")

                        // --- VP9 ---
                        type.equals("video/x-vnd.on2.vp9", ignoreCase = true) -> videoCodecs.add("vp9")

                        // --- 音频兼容性 ---
                        type.equals("audio/mp4a-latm", ignoreCase = true) -> audioCodecs.add("aac")
                        type.equals("audio/ac3", ignoreCase = true) -> audioCodecs.add("ac3")
                        type.equals("audio/eac3", ignoreCase = true) -> audioCodecs.add("eac3")
                        type.equals("audio/mpeg", ignoreCase = true) -> audioCodecs.add("mp3")
                        type.equals("audio/flac", ignoreCase = true) -> audioCodecs.add("flac")
                        type.equals("audio/opus", ignoreCase = true) -> audioCodecs.add("opus")
                        type.equals("audio/vnd.dts", true) -> audioCodecs.add("dts")
                        type.equals("audio/vnd.dts.hd", true) -> {
                            audioCodecs.add("dts")
                            audioCodecs.add("dtshd") // 蓝光原盘常见
                        }
                        type.equals("audio/true-hd", true) -> audioCodecs.add("truehd") // 杜比全景声原盘核心
                        type.equals("audio/eac3-joc", true) -> audioCodecs.add("eac3") // 杜比数字+ (带全景声)
                        type.equals("audio/ac4", true) -> audioCodecs.add("ac4")
                    }
                } catch (e: Exception) {
                    // 预防部分老旧系统在探测特定 Codec 时崩溃
                    continue
                }
            }
        }

        // 2. 屏幕分辨率探测 (处理 API 30+ 废弃方法)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val (screenWidth, screenHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val rect = metrics.bounds
            rect.width() to rect.height()
        } else {
            val display = wm.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            size.x to size.y
        }

        // 策略：即使屏幕是1080p，只要支持HEVC，上报4K解码能力以避免不必要的转码
        val canHandle4K = videoCodecs.contains("hevc") || videoCodecs.contains("av1")
        val maxDecodeWidth = if (canHandle4K) maxOf(screenWidth, 3840) else screenWidth
        val maxDecodeHeight = if (canHandle4K) maxOf(screenHeight, 2160) else screenHeight

        // 3. 构造 Emby 标准 DeviceProfile Map
        return mapOf(
            "MaxCanvasWidth" to maxDecodeWidth,
            "MaxCanvasHeight" to maxDecodeHeight,
            "VideoCodecs" to videoCodecs.toList(),
            "AudioCodecs" to audioCodecs.toList(),
            "VideoProfiles" to videoProfiles,
            "Containers" to listOf("mkv", "mp4", "mov", "ts", "m3u8", "webm", "avi"),
            "SupportsDirectPlay" to true,
            "SupportsDirectStream" to true,
            "DeviceName" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Platform" to "Android",
            "PlatformVersion" to Build.VERSION.RELEASE
        )
    }

    /**
     * 级别映射：将 Android 系统常量转为 H.264 标准 Level 数值
     */
    private fun mapAvcLevel(androidLevel: Int): Int {
        return when (androidLevel) {
            // AVC/H264 levels
            CodecProfileLevel.AVCLevel1 -> 10
            CodecProfileLevel.AVCLevel11 -> 11
            CodecProfileLevel.AVCLevel12 -> 12
            CodecProfileLevel.AVCLevel13 -> 13
            CodecProfileLevel.AVCLevel2 -> 20
            CodecProfileLevel.AVCLevel21 -> 21
            CodecProfileLevel.AVCLevel22 -> 22
            CodecProfileLevel.AVCLevel3 -> 30
            CodecProfileLevel.AVCLevel31 -> 31
            CodecProfileLevel.AVCLevel32 -> 32
            CodecProfileLevel.AVCLevel4 -> 40
            CodecProfileLevel.AVCLevel41 -> 41
            CodecProfileLevel.AVCLevel42 -> 42
            CodecProfileLevel.AVCLevel5 -> 50
            CodecProfileLevel.AVCLevel51 -> 51
            CodecProfileLevel.AVCLevel52 -> 52
            CodecProfileLevel.AVCLevel6 -> 60
            CodecProfileLevel.AVCLevel61 -> 61
            CodecProfileLevel.AVCLevel62 -> 62
            else -> 41
        }
    }
}