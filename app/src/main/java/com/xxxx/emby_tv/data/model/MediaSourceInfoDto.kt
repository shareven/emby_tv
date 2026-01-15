package com.xxxx.emby_tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Emby MediaSourceInfo 数据模型
 * 对应 Emby API 返回的媒体源信息
 */
data class MediaSourceInfoDto(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Path") val path: String? = null,
    @SerializedName("Type") val type: String? = null,
    @SerializedName("Container") val container: String? = null,
    @SerializedName("Size") val size: Long? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("IsRemote") val isRemote: Boolean? = null,
    @SerializedName("ETag") val eTag: String? = null,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerializedName("ReadAtNativeFramerate") val readAtNativeFramerate: Boolean? = null,
    @SerializedName("IgnoreDts") val ignoreDts: Boolean? = null,
    @SerializedName("IgnoreIndex") val ignoreIndex: Boolean? = null,
    @SerializedName("GenPtsInput") val genPtsInput: Boolean? = null,
    @SerializedName("SupportsTranscoding") val supportsTranscoding: Boolean? = null,
    @SerializedName("SupportsDirectStream") val supportsDirectStream: Boolean? = null,
    @SerializedName("SupportsDirectPlay") val supportsDirectPlay: Boolean? = null,
    @SerializedName("IsInfiniteStream") val isInfiniteStream: Boolean? = null,
    @SerializedName("RequiresOpening") val requiresOpening: Boolean? = null,
    @SerializedName("OpenToken") val openToken: String? = null,
    @SerializedName("RequiresClosing") val requiresClosing: Boolean? = null,
    @SerializedName("LiveStreamId") val liveStreamId: String? = null,
    @SerializedName("BufferMs") val bufferMs: Int? = null,
    @SerializedName("RequiresLooping") val requiresLooping: Boolean? = null,
    @SerializedName("SupportsProbing") val supportsProbing: Boolean? = null,
    @SerializedName("VideoType") val videoType: String? = null,
    @SerializedName("IsoType") val isoType: String? = null,
    @SerializedName("Video3DFormat") val video3DFormat: String? = null,
    @SerializedName("MediaAttachments") val mediaAttachments: List<Any>? = null, // 类型不确定
    @SerializedName("Formats") val formats: List<String>? = null,
    @SerializedName("Bitrate") val bitrate: Int? = null,
    @SerializedName("Timestamp") val timestamp: String? = null,
    @SerializedName("RequiredHttpHeaders") val requiredHttpHeaders: Map<String, String>? = null,
    @SerializedName("DefaultAudioStreamIndex") val defaultAudioStreamIndex: Int? = null,
    @SerializedName("DefaultSubtitleStreamIndex") val defaultSubtitleStreamIndex: Int? = null,
    @SerializedName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerializedName("DirectStreamUrl") val directStreamUrl: String? = null,
    @SerializedName("Protocol") val protocol: String? = null,
    @SerializedName("HasMixedProtocols") val hasMixedProtocols: Boolean? = null,
    @SerializedName("AddApiKeyToDirectStreamUrl") val addApiKeyToDirectStreamUrl: Boolean? = null,
    @SerializedName("TranscodingSubProtocol") val transcodingSubProtocol: String? = null,
    @SerializedName("TranscodingContainer") val transcodingContainer: String? = null,
    @SerializedName("TranscodingBitrate") val transcodingBitrate: Int? = null,
    @SerializedName("ItemId") val itemId: String? = null,
    @SerializedName("Chapters") val chapters: List<ChapterInfo>? = null,
    @SerializedName("MediaStreams") val mediaStreams: List<MediaStreamDto>? = null
)

/**
 * 章节信息数据模型
 */
data class ChapterInfo(
    @SerializedName("StartPositionTicks") val startPositionTicks: Long? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("MarkerType") val markerType: String? = null,
    @SerializedName("ChapterIndex") val chapterIndex: Int? = null
)