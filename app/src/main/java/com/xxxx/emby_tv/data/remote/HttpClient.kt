package com.xxxx.emby_tv.data.remote

import android.content.Context
import com.google.net.cronet.okhttptransport.CronetInterceptor
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import java.util.concurrent.TimeUnit

/**
 * 全局 Cronet + OkHttp 网络客户端配置
 * 
 * 特性：
 * - 支持 HTTP/2 和 QUIC (HTTP/3)
 * - 支持 Brotli 压缩
 * - 连接池复用
 * - 10MB 磁盘缓存
 */
object HttpClient {
    @Volatile
    private var client: OkHttpClient? = null

    fun getClient(context: Context): OkHttpClient {
        return client ?: synchronized(this) {
            client ?: createClient(context).also { client = it }
        }
    }

    private fun createClient(context: Context): OkHttpClient {
        // 1. 初始化 Cronet 引擎
        val cronetEngine = CronetEngine.Builder(context)
            .enableQuic(true)
            .enableHttp2(true)
            .enableBrotli(true)
            .setStoragePath(context.cacheDir.absolutePath)
            .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 10 * 1024 * 1024) // 10MB 缓存
            .build()

        // 2. 构建 OkHttpClient 并植入 Cronet 拦截器
        return OkHttpClient.Builder()
            .addInterceptor(CronetInterceptor.newBuilder(cronetEngine).build())
            .dispatcher(Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 20
            })
            .connectionPool(ConnectionPool(10, 2, TimeUnit.MINUTES))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 重置客户端（用于测试或特殊场景）
     */
    fun reset() {
        synchronized(this) {
            client = null
        }
    }
}
