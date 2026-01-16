package com.xxxx.emby_tv.util

import android.content.Context
import android.util.Log
import com.xxxx.emby_tv.R

/**
 * 统一错误处理工具类
 * 
 * 提供统一的错误日志记录和处理方法
 */
object ErrorHandler {
    private const val DEFAULT_TAG = "EmbyTV"

    /**
     * 记录错误日志
     */
    fun logError(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    /**
     * 记录警告日志
     */
    fun logWarning(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    /**
     * 记录调试日志
     */
    fun logDebug(tag: String = DEFAULT_TAG, message: String) {
        Log.d(tag, message)
    }

    /**
     * 记录信息日志
     */
    fun logInfo(tag: String = DEFAULT_TAG, message: String) {
        Log.i(tag, message)
    }

    /**
     * 安全执行代码块，捕获异常并记录
     */
    fun <T> runSafely(
        tag: String = DEFAULT_TAG,
        errorMessage: String = "操作失败",
        defaultValue: T,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            logError(tag, errorMessage, e)
            defaultValue
        }
    }

    /**
     * 安全执行挂起函数，捕获异常并记录
     */
    suspend fun <T> runSafelySuspend(
        tag: String = DEFAULT_TAG,
        errorMessage: String = "操作失败",
        defaultValue: T,
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            logError(tag, errorMessage, e)
            defaultValue
        }
    }

    /**
     * 获取用户友好的错误消息（支持国际化）
     * 
     * @param context Android Context，用于获取本地化字符串
     * @param throwable 异常对象
     * @return 本地化的用户友好错误消息
     */
    fun getFriendlyMessage(context: Context, throwable: Throwable?): String {
        return when (throwable) {
            is java.net.UnknownHostException -> context.getString(R.string.error_network_unavailable)
            is java.net.SocketTimeoutException -> context.getString(R.string.error_connection_timeout)
            is java.net.ConnectException -> context.getString(R.string.error_server_unreachable)
            is javax.net.ssl.SSLException -> context.getString(R.string.error_ssl_failed)
            is java.io.IOException -> context.getString(R.string.error_network_error)
            else -> throwable?.message ?: context.getString(R.string.error_unknown)
        }
    }

    /**
     * 获取用户友好的错误消息（无 Context 版本，使用硬编码字符串）
     * 
     * @param throwable 异常对象
     * @return 用户友好错误消息（英文）
     */
    fun getFriendlyMessage(throwable: Throwable?): String {
        return when (throwable) {
            is java.net.UnknownHostException -> "Network unavailable, please check your connection"
            is java.net.SocketTimeoutException -> "Connection timeout, please try again"
            is java.net.ConnectException -> "Unable to connect to server"
            is javax.net.ssl.SSLException -> "Secure connection failed"
            is java.io.IOException -> "Network error, please try again"
            else -> throwable?.message ?: "Unknown error"
        }
    }
}
