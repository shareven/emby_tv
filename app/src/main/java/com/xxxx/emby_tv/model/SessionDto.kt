package com.xxxx.emby_tv.model

import com.google.gson.annotations.SerializedName

/**
 * Emby Session 数据模型
 * 对应 Emby API 返回的会话信息
 */
data class SessionDto(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("UserId") val userId: String? = null,
    @SerializedName("UserName") val userName: String? = null,
    @SerializedName("Client") val client: String? = null,
    @SerializedName("LastActivityDate") val lastActivityDate: String? = null,
    @SerializedName("LastPlaybackCheckIn") val lastPlaybackCheckIn: String? = null,
    @SerializedName("DeviceName") val deviceName: String? = null,
    @SerializedName("DeviceId") val deviceId: String? = null,
    @SerializedName("ApplicationVersion") val applicationVersion: String? = null,
    @SerializedName("IsActive") val isActive: Boolean? = null,
    @SerializedName("HasCustomDeviceName") val hasCustomDeviceName: Boolean? = null,
    @SerializedName("NowPlayingQueue") val nowPlayingQueue: List<NowPlayingQueueItem>? = null,
    @SerializedName("NowPlayingItem") val nowPlayingItem: BaseItemDto? = null,
    @SerializedName("NowViewingItem") val nowViewingItem: BaseItemDto? = null,
    @SerializedName("DeviceType") val deviceType: String? = null,
    @SerializedName("SupportedCommands") val supportedCommands: List<String>? = null,
    @SerializedName("PlayState") val playState: PlayStateInfo? = null,
    @SerializedName("AdditionalUsers") val additionalUsers: List<AdditionalUserInfo>? = null,
    @SerializedName("PlaySessionId") val playSessionId: String? = null,
    @SerializedName("Capabilities") val capabilities: SessionCapabilities? = null,
    @SerializedName("RemoteEndPoint") val remoteEndPoint: String? = null,
    @SerializedName("Protocol") val protocol: String? = null,
    @SerializedName("PlayableMediaTypes") val playableMediaTypes: List<String>? = null,
    @SerializedName("PlaylistIndex") val playlistIndex: Int? = null,
    @SerializedName("PlaylistLength") val playlistLength: Int? = null,
    @SerializedName("ServerId") val serverId: String? = null,
    @SerializedName("InternalDeviceId") val internalDeviceId: Int? = null,
    @SerializedName("AppIconUrl") val appIconUrl: String? = null,
    @SerializedName("SupportsRemoteControl") val supportsRemoteControl: Boolean? = null,
    @SerializedName("TranscodingInfo") val transcodingInfo: TranscodingInfo? = null
)

/**
 * 播放状态信息
 */
data class PlayStateInfo(
    @SerializedName("CanSeek") val canSeek: Boolean? = null,
    @SerializedName("IsPaused") val isPaused: Boolean? = null,
    @SerializedName("IsMuted") val isMuted: Boolean? = null,
    @SerializedName("VolumeLevel") val volumeLevel: Int? = null,
    @SerializedName("PlaybackStartTimeTicks") val playbackStartTimeTicks: Long? = null,
    @SerializedName("PlaybackRate") val playbackRate: Double? = null,
    @SerializedName("PositionTicks") val positionTicks: Long? = null,
    @SerializedName("PlaylistIndex") val playlistIndex: Int? = null,
    @SerializedName("PlaylistLength") val playlistLength: Int? = null,
    @SerializedName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerializedName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerializedName("MediaSourceId") val mediaSourceId: String? = null,
    @SerializedName("PlayMethod") val playMethod: String? = null,
    @SerializedName("RepeatMode") val repeatMode: String? = null,
    @SerializedName("SleepTimerMode") val sleepTimerMode: String? = null,
    @SerializedName("SubtitleOffset") val subtitleOffset: Int? = null,
    @SerializedName("Shuffle") val shuffle: Boolean? = null
)

/**
 * 附加用户信息
 */
data class AdditionalUserInfo(
    @SerializedName("UserId") val userId: String? = null,
    @SerializedName("UserName") val userName: String? = null
)

/**
 * 正在播放队列项
 */
data class NowPlayingQueueItem(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("PlaylistItemId") val playlistItemId: String? = null
)

/**
 * 会话能力
 */
data class SessionCapabilities(
    @SerializedName("PlayableMediaTypes") val playableMediaTypes: List<String>? = null,
    @SerializedName("SupportsMediaControl") val supportsMediaControl: Boolean? = null,
    @SerializedName("SupportsRemoteControl") val supportsRemoteControl: Boolean? = null,
    @SerializedName("MaxStaticBitrate") val maxStaticBitrate: Int? = null,
    @SerializedName("MusicStreamingTranscodingBitrate") val musicStreamingTranscodingBitrate: Int? = null,
    @SerializedName("MovieStreamingTranscodingBitrate") val movieStreamingTranscodingBitrate: Int? = null,
    @SerializedName("AlbumArtPngUrl") val albumArtPngUrl: String? = null,
    @SerializedName("TrickplayOptions") val trickplayOptions: TrickplayOptions? = null
)

/**
 * Trickplay选项
 */
data class TrickplayOptions(
    @SerializedName("Entries") val entries: List<TrickplayEntry>? = null
)

/**
 * Trickplay条目
 */
data class TrickplayEntry(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Width") val width: Int? = null,
    @SerializedName("Height") val height: Int? = null,
    @SerializedName("TileWidth") val tileWidth: Int? = null,
    @SerializedName("TileHeight") val tileHeight: Int? = null,
    @SerializedName("Codectag") val codectag: String? = null,
    @SerializedName("Container") val container: String? = null,
    @SerializedName("MuxingMode") val muxingMode: String? = null
)

/**
 * 转码信息
 */
data class TranscodingInfo(
    @SerializedName("AudioCodec") val audioCodec: String? = null,
    @SerializedName("VideoCodec") val videoCodec: String? = null,
    @SerializedName("SubProtocol") val subProtocol: String? = null,
    @SerializedName("Container") val container: String? = null,
    @SerializedName("IsVideoDirect") val isVideoDirect: Boolean? = null,
    @SerializedName("IsAudioDirect") val isAudioDirect: Boolean? = null,
    @SerializedName("Bitrate") val bitrate: Int? = null,
    @SerializedName("AudioBitrate") val audioBitrate: Int? = null,
    @SerializedName("VideoBitrate") val videoBitrate: Int? = null,
    @SerializedName("CompletionPercentage") val completionPercentage: Double? = null,
    @SerializedName("TranscodingPositionTicks") val transcodingPositionTicks: Long? = null,
    @SerializedName("TranscodingStartPositionTicks") val transcodingStartPositionTicks: Long? = null,
    @SerializedName("Width") val width: Int? = null,
    @SerializedName("Height") val height: Int? = null,
    @SerializedName("AudioChannels") val audioChannels: Int? = null,
    @SerializedName("TranscodeReasons") val transcodeReasons: List<String>? = null,
    @SerializedName("VideoDecoderIsHardware") val videoDecoderIsHardware: Boolean? = null,
    @SerializedName("VideoEncoderIsHardware") val videoEncoderIsHardware: Boolean? = null,
    @SerializedName("VideoPipelineInfo") val videoPipelineInfo: List<VideoPipelineInfo>? = null,
    @SerializedName("SubtitlePipelineInfos") val subtitlePipelineInfos: List<SubtitlePipelineInfo>? = null
)

/**
 * 视频管道信息
 */
data class VideoPipelineInfo(
    @SerializedName("HardwareContextName") val hardwareContextName: String? = null,
    @SerializedName("IsHardwareContext") val isHardwareContext: Boolean? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Short") val short: String? = null,
    @SerializedName("StepType") val stepType: String? = null,
    @SerializedName("StepTypeName") val stepTypeName: String? = null,
    @SerializedName("FfmpegName") val ffmpegName: String? = null,
    @SerializedName("FfmpegDescription") val ffmpegDescription: String? = null,
    @SerializedName("FfmpegOptions") val ffmpegOptions: String? = null,
    @SerializedName("Param") val param: String? = null,
    @SerializedName("ParamShort") val paramShort: String? = null
)

/**
 * 字幕管道信息
 */
data class SubtitlePipelineInfo(
    @SerializedName("HardwareContextName") val hardwareContextName: String? = null,
    @SerializedName("IsHardwareContext") val isHardwareContext: Boolean? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Short") val short: String? = null,
    @SerializedName("StepType") val stepType: String? = null,
    @SerializedName("StepTypeName") val stepTypeName: String? = null,
    @SerializedName("FfmpegName") val ffmpegName: String? = null,
    @SerializedName("FfmpegDescription") val ffmpegDescription: String? = null,
    @SerializedName("FfmpegOptions") val ffmpegOptions: String? = null,
    @SerializedName("Param") val param: String? = null,
    @SerializedName("ParamShort") val paramShort: String? = null
)