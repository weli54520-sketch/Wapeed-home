package com.wm.iptv.player

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.wm.iptv.data.XtreamApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * يجيب صورة اللقطة عند وقت التقديم.
 * يفتح اتصال ثاني على السيرفر — عشان كذا ما نشغله إلا لو الاشتراك يسمح باتصالين.
 */
class ThumbFetcher(private val url: String, private val scope: CoroutineScope, private val onThumb: (Long, ImageBitmap) -> Unit) {
    private var retriever: MediaMetadataRetriever? = null
    private var failed = false
    private val requests = Channel<Long>(Channel.CONFLATED)
    private var job: Job? = null
    private val cache = LinkedHashMap<Long, ImageBitmap>()

    fun start() {
        job = scope.launch(Dispatchers.IO) {
            for (ms in requests) {
                if (failed) continue
                val bucket = (ms / 10_000) * 10_000 // صورة لكل ١٠ ثواني
                val cached = cache[bucket]
                if (cached != null) {
                    withContext(Dispatchers.Main) { onThumb(ms, cached) }
                    continue
                }
                try {
                    val r = retriever ?: MediaMetadataRetriever().also {
                        it.setDataSource(url, hashMapOf("User-Agent" to XtreamApi.USER_AGENT))
                        retriever = it
                    }
                    val bmp = r.getFrameAtTime(bucket * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                    val w = 320
                    val h = (bmp.height * w / bmp.width.coerceAtLeast(1)).coerceAtLeast(1)
                    val small = Bitmap.createScaledBitmap(bmp, w, h, true)
                    if (small !== bmp) bmp.recycle()
                    val img = small.asImageBitmap()
                    cache[bucket] = img
                    if (cache.size > 60) cache.remove(cache.keys.first())
                    withContext(Dispatchers.Main) { onThumb(ms, img) }
                } catch (_: Throwable) {
                    failed = true
                    release()
                }
            }
        }
    }

    fun request(ms: Long) { requests.trySend(ms) }

    fun release() {
        try { retriever?.release() } catch (_: Throwable) {}
        retriever = null
    }

    fun stop() {
        job?.cancel()
        requests.close()
        scope.launch(Dispatchers.IO) { release() }
    }
}
