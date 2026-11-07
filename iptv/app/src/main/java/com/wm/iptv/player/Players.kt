package com.wm.iptv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C as MC
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.wm.iptv.data.Repo
import com.wm.iptv.data.XtreamApi

@OptIn(UnstableApi::class)
object Players {

    fun create(context: Context, live: Boolean): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(XtreamApi.USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
            .setAllowCrossProtocolRedirects(true) // سيرفرات IPTV تحول http ↔ https كثير
            .setKeepPostFor302Redirects(true)
        val dataSource = DefaultDataSource.Factory(context, http)

        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)        // تقديم سريع في الملفات اللي ما فيها فهرس
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
            .setTsExtractorTimestampSearchBytes(600 * 188 * 3)

        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (live) 8_000 else 20_000,     // أقل بفر
                if (live) 40_000 else 90_000,    // أعلى بفر
                if (live) 1_200 else 1_500,      // يبدأ التشغيل بعد
                if (live) 3_000 else 4_000,      // بعد التقطيع
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(if (live) 0 else 30_000, true)
            .build()

        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON) // ffmpeg للصوت لو الجهاز ما يدعمه
            .setEnableDecoderFallback(true)

        val selector = DefaultTrackSelector(context)
        val st = Repo.store
        selector.setParameters(
            selector.buildUponParameters()
                .setPreferredAudioLanguages(*langs(st.preferredAudio))
                .setPreferredTextLanguages(*langs(st.preferredSubtitle))
                .setSelectUndeterminedTextLanguage(false)
        )

        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource, extractors))
            .setLoadControl(load)
            .setTrackSelector(selector)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(MC.USAGE_MEDIA).setContentType(MC.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(5_000)
            .setSeekForwardIncrementMs(5_000)
            .build()
    }

    private fun langs(pref: String): Array<String> = when (pref) {
        "ar" -> arrayOf("ar", "ara")
        "en" -> arrayOf("en", "eng")
        "" -> emptyArray()
        else -> arrayOf(pref)
    }

    /** نحدد نوع البث من الرابط عشان ExoPlayer ما يتلخبط */
    fun item(url: String): MediaItem {
        val b = MediaItem.Builder().setUri(url)
        when {
            url.endsWith(".m3u8", true) -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            url.endsWith(".ts", true) -> b.setMimeType(MimeTypes.VIDEO_MP2T)
        }
        return b.build()
    }
}
