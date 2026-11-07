package com.wm.iptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.wm.iptv.data.Repo
import com.wm.iptv.data.XtreamApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true) // صور كثيرة تتحول من http إلى https
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", XtreamApi.USER_AGENT).build())
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(250L * 1024 * 1024).build() }
            .crossfade(180)
            .respectCacheHeaders(false) // سيرفرات IPTV ترسل هيدرات تمنع الكاش
            .build()
    }
}
