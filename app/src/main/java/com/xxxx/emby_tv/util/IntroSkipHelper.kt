package com.xxxx.emby_tv.util

import com.xxxx.emby_tv.data.model.ChapterInfo

/**
 * 片头跳过辅助工具类
 * 用于检测视频中的片头标记
 */
object IntroSkipHelper {
    /**
     * 从章节列表中检测片头范围
     * 
     * @param chapters 章节列表
     * @return Pair<开始时间(ms), 结束时间(ms)>，如果未找到片头标记则返回 null
     */
    fun detectIntroRange(chapters: List<ChapterInfo>?): Pair<Long, Long>? {
        if (chapters.isNullOrEmpty()) return null
        
        var introStartTicks: Long? = null
        var introEndTicks: Long? = null
        
        // 遍历章节列表，查找 IntroStart 和 IntroEnd 标记
        for (chapter in chapters) {
            when (chapter.markerType) {
                "IntroStart" -> {
                    chapter.startPositionTicks?.let {
                        introStartTicks = it
                    }
                }
                "IntroEnd" -> {
                    chapter.startPositionTicks?.let {
                        introEndTicks = it
                    }
                }
            }
        }
        
        // 如果找到了开始和结束标记，转换为毫秒并返回
        if (introStartTicks != null && introEndTicks != null) {
            val startMs = introStartTicks / 10000
            val endMs = introEndTicks / 10000
            
            // 确保结束时间大于开始时间
            if (endMs > startMs) {
                return Pair(startMs, endMs)
            }
        }
        
        return null
    }
}
