package com.xxxx.emby_tv

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.view.TextureView
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MediaSourceFactory
import android.graphics.Color
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.ui.SubtitleView
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.video.VideoSize
import android.util.TypedValue
import android.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import io.flutter.plugin.common.StandardMessageCodec

class ExoPlayerViewFactory(
    private val messenger: BinaryMessenger,
    private val appContext: Context
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        return ExoPlayerPlatformView(appContext, messenger, viewId)
    }
}

class ExoPlayerPlatformView(
    context: Context,
    messenger: BinaryMessenger,
    viewId: Int
) : PlatformView, MethodChannel.MethodCallHandler {

    private val container: FrameLayout = FrameLayout(context)
    private val textureView: TextureView = TextureView(context)
    private val subtitleView: SubtitleView = SubtitleView(context)
    private val aspectRatioFrameLayout: AspectRatioFrameLayout = AspectRatioFrameLayout(context)
    private var player: ExoPlayer? = null
    private val mediaSourceFactory: MediaSourceFactory =
        com.google.android.exoplayer2.source.DefaultMediaSourceFactory(context)
    private val methodChannel: MethodChannel =
        MethodChannel(messenger, "emby_tv/exoplayer_$viewId")

    init {
        aspectRatioFrameLayout.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        layoutParams.gravity = android.view.Gravity.CENTER
        aspectRatioFrameLayout.layoutParams = layoutParams
        aspectRatioFrameLayout.addView(
            textureView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        container.addView(aspectRatioFrameLayout)
        container.addView(
            subtitleView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        subtitleView.setUserDefaultTextSize()
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setApplyEmbeddedFontSizes(false)
        subtitleView.setFixedTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            22f
        )
        subtitleView.setStyle(
            CaptionStyleCompat(
                Color.WHITE,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                Color.BLACK,
                null
            )
        )
        methodChannel.setMethodCallHandler(this)
    }

    override fun getView(): View {
        return container
    }

    override fun dispose() {
        methodChannel.setMethodCallHandler(null)
        releasePlayer()
    }

    private fun ensurePlayer() {
        if (player != null) return
        val p = ExoPlayer.Builder(container.context)
            .build()
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        p.setVideoTextureView(textureView)
        p.addListener(
            object : Player.Listener {
                override fun onCues(cueGroup: CueGroup) {
                    subtitleView.setCues(cueGroup.cues)
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width == 0 || videoSize.height == 0) {
                        return
                    }
                    val pixelWidthHeightRatio = if (videoSize.pixelWidthHeightRatio > 0) videoSize.pixelWidthHeightRatio else 1f
                    aspectRatioFrameLayout.setAspectRatio(
                        videoSize.width * pixelWidthHeightRatio / videoSize.height
                    )
                }
            }
        )
        player = p
    }

    private fun buildMediaSource(videoUrl: String, subtitleUrl: String?): MediaSource {
        Log.d("ExoPlayerPlatformView", "buildMediaSource videoUrl=$videoUrl subtitleUrl=$subtitleUrl")
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(videoUrl))

        if (!subtitleUrl.isNullOrEmpty()) {
            val uri = Uri.parse(subtitleUrl)
            val path = uri.lastPathSegment ?: ""
            val mimeType = when {
                path.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                path.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                else -> MimeTypes.TEXT_VTT
            }
            Log.d("ExoPlayerPlatformView", "subtitle mimeType=$mimeType path=$path")
            val subtitleConfig =
                MediaItem.SubtitleConfiguration.Builder(uri)
                    .setMimeType(mimeType)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        val mediaItem = mediaItemBuilder.build()
        return mediaSourceFactory.createMediaSource(mediaItem)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "initialize" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<Any?, Any?>()
                val videoUrl = args["videoUrl"] as? String
                val subtitleUrl = args["subtitleUrl"] as? String
                val startPositionMs =
                    (args["startPositionMs"] as? Number)?.toLong() ?: 0L
                Log.d(
                    "ExoPlayerPlatformView",
                    "initialize videoUrl=$videoUrl subtitleUrl=$subtitleUrl startPositionMs=$startPositionMs"
                )
                if (videoUrl.isNullOrEmpty()) {
                    result.error("ARG_ERROR", "videoUrl is required", null)
                    return
                }
                ensurePlayer()
                val p = player ?: run {
                    result.error("PLAYER_ERROR", "Player not available", null)
                    return
                }
                val initBuilder = p.trackSelectionParameters.buildUpon()
                initBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleUrl == null)
                p.trackSelectionParameters = initBuilder.build()
                val mediaSource = buildMediaSource(videoUrl, subtitleUrl)
                var pendingResult: MethodChannel.Result? = result
                p.addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY && pendingResult != null) {
                                val info = hashMapOf<String, Any>(
                                    "durationMs" to if (p.duration != C.TIME_UNSET) p.duration else 0L,
                                    "isPlaying" to p.isPlaying
                                )
                                pendingResult?.success(info)
                                pendingResult = null
                                p.removeListener(this)
                            }
                        }

                        override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                            pendingResult?.error("PLAYER_ERROR", error.message, null)
                            pendingResult = null
                            p.removeListener(this)
                        }
                    }
                )
                p.setMediaSource(mediaSource)
                if (startPositionMs > 0) {
                    p.seekTo(startPositionMs)
                }
                p.prepare()
                p.playWhenReady = true
            }
            "play" -> {
                player?.play()
                result.success(null)
            }
            "pause" -> {
                player?.pause()
                result.success(null)
            }
            "seekTo" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<Any?, Any?>()
                val positionMs = (args["positionMs"] as? Number)?.toLong() ?: 0L
                player?.seekTo(positionMs)
                result.success(null)
            }
            "updateSource" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<Any?, Any?>()
                val videoUrl = args["videoUrl"] as? String
                val subtitleUrl = args["subtitleUrl"] as? String
                val positionMs = (args["positionMs"] as? Number)?.toLong() ?: 0L
                val autoPlay = args["autoPlay"] as? Boolean ?: true
                Log.d(
                    "ExoPlayerPlatformView",
                    "updateSource videoUrl=$videoUrl subtitleUrl=$subtitleUrl positionMs=$positionMs autoPlay=$autoPlay"
                )
                if (videoUrl.isNullOrEmpty()) {
                    result.error("ARG_ERROR", "videoUrl is required", null)
                    return
                }
                ensurePlayer()
                val p = player ?: run {
                    result.error("PLAYER_ERROR", "Player not available", null)
                    return
                }
                val builder = p.trackSelectionParameters.buildUpon()
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleUrl == null)
                p.trackSelectionParameters = builder.build()
                val mediaSource = buildMediaSource(videoUrl, subtitleUrl)
                p.setMediaSource(mediaSource)
                if (positionMs > 0) {
                    p.seekTo(positionMs)
                }
                p.prepare()
                p.playWhenReady = autoPlay
                result.success(null)
            }
            "getPosition" -> {
                val p = player
                if (p == null) {
                    result.success(0L)
                } else {
                    result.success(p.currentPosition)
                }
            }
            "getDuration" -> {
                val p = player
                if (p == null) {
                    result.success(0L)
                } else {
                    val duration = if (p.duration != C.TIME_UNSET) p.duration else 0L
                    result.success(duration)
                }
            }
            "getBufferedPosition" -> {
                val p = player
                if (p == null) {
                    result.success(0L)
                } else {
                    result.success(p.bufferedPosition)
                }
            }
            "isPlaying" -> {
                result.success(player?.isPlaying ?: false)
            }
            "dispose" -> {
                releasePlayer()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun releasePlayer() {
        player?.clearVideoSurface()
        player?.release()
        player = null
    }
}
