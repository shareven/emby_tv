package com.xxxx.emby_tv.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences 封装
 * 管理应用的本地偏好设置
 */
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("emby_tv", Context.MODE_PRIVATE)

    // === 主题设置 ===

    var themeId: String
        get() = prefs.getString(KEY_THEME_ID, DEFAULT_THEME_ID) ?: DEFAULT_THEME_ID
        set(value) = prefs.edit().putString(KEY_THEME_ID, value).apply()

    // === 播放器设置 ===

    var preferDirectPlay: Boolean
        get() = prefs.getBoolean(KEY_PREFER_DIRECT_PLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_PREFER_DIRECT_PLAY, value).apply()

    var disableHevc: Boolean
        get() = prefs.getBoolean(KEY_DISABLE_HEVC, false)
        set(value) = prefs.edit().putBoolean(KEY_DISABLE_HEVC, value).apply()

    // === 片头跳过设置 ===

    var autoSkipIntro: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SKIP_INTRO, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SKIP_INTRO, value).apply()

    // === 通用方法 ===

    fun getString(key: String, defaultValue: String? = null): String? {
        return prefs.getString(key, defaultValue)
    }

    fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return prefs.getLong(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_THEME_ID = "selected_theme_id"
        private const val KEY_PREFER_DIRECT_PLAY = "prefer_direct_play"
        private const val KEY_DISABLE_HEVC = "disable_hevc"
        private const val KEY_AUTO_SKIP_INTRO = "auto_skip_intro"

        private const val DEFAULT_THEME_ID = "purple"
    }
}
