package com.wm.iptv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C as MC
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.wm.iptv.data.Channel
import com.wm.iptv.data.EpgEntry
import com.wm.iptv.data.Episode
import com.wm.iptv.data.Kind
import com.wm.iptv.data.Movie
import com.wm.iptv.data.Progress
import com.wm.iptv.data.Repo
import com.wm.iptv.ui.AppTheme
import com.wm.iptv.ui.screens.LiveKeys
import com.wm.iptv.ui.screens.channelsFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object PlayerLauncher {
    /** نسخة الجوال تغيّرها لمشغلها الخاص */
    var target: Class<out android.app.Activity> = PlayerActivity::class.java

    fun live(ctx: Context, channelId: Int, listKey: String) {
        ctx.startActivity(Intent(ctx, target).putExtra("type", "live").putExtra("id", channelId).putExtra("list", listKey))
    }

    fun movie(ctx: Context, m: Movie, startMs: Long) {
        ctx.startActivity(Intent(ctx, target).putExtra("type", "movie").putExtra("id", m.id).putExtra("ext", m.ext)
            .putExtra("title", m.name).putExtra("image", m.icon).putExtra("start", startMs))
    }

    fun episode(ctx: Context, seriesId: Int, e: Episode, seriesName: String, cover: String, startMs: Long) {
        ctx.startActivity(Intent(ctx, target).putExtra("type", "episode").putExtra("id", e.id).putExtra("ext", e.ext)
            .putExtra("sid", seriesId).putExtra("season", e.season).putExtra("num", e.number)
            .putExtra("title", seriesName).putExtra("image", e.image.ifBlank { cover }).putExtra("cover", cover).putExtra("start", startMs))
    }

    /** من "أكمل المشاهدة" */
    fun resume(ctx: Context, p: Progress) {
        val id = p.key.substringAfter(':').toIntOrNull() ?: return
        val i = Intent(ctx, target).putExtra("id", id).putExtra("ext", p.ext).putExtra("title", p.title)
            .putExtra("image", p.image).putExtra("start", if (p.finished) 0L else p.position)
        if (p.key.startsWith("e:")) i.putExtra("type", "episode").putExtra("sid", p.seriesId).putExtra("cover", p.image)
        else i.putExtra("type", "movie")
        ctx.startActivity(i)
    }
}

enum class Panel { TRACKS, CHANNELS }

/** حالة الواجهة فوق الفيديو */
class PlayerVm {
    var isLive by mutableStateOf(false)
    var title by mutableStateOf("")
    var subtitle by mutableStateOf("")
    var overlay by mutableStateOf(true)
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
    var seekTarget by mutableStateOf<Long?>(null)
    var seekOrigin by mutableLongStateOf(0L)
    var speedLabel by mutableStateOf("")
    var thumb by mutableStateOf<ImageBitmap?>(null)
    var thumbsOn by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var panel by mutableStateOf<Panel?>(null)
    var tracks by mutableStateOf(Tracks.EMPTY)
    var resizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    var subtitleSize by mutableIntStateOf(1)
    // مباشر
    var channel by mutableStateOf<Channel?>(null)
    var zapPreview by mutableStateOf<Channel?>(null)
    var channels by mutableStateOf<List<Channel>>(emptyList())
    var listTitle by mutableStateOf("")
    var banner by mutableStateOf(false)
    var epg by mutableStateOf<List<EpgEntry>>(emptyList())
    var digits by mutableStateOf("")
    var favorite by mutableStateOf(false)
    // مسلسلات
    var nextEp by mutableStateOf<Episode?>(null)
    var countdown by mutableIntStateOf(-1)
}

@OptIn(UnstableApi::class)
open class PlayerActivity : ComponentActivity() {
    internal lateinit var player: ExoPlayer
    internal val vm = PlayerVm()

    private var type = "movie"
    private var contentId = 0
    private var ext = ""
    private var seriesId = 0
    private var season = 0
    private var epNum = 0
    private var image = ""
    private var cover = ""
    private var listKey = LiveKeys.ALL
    private var url = ""

    private var retries = 0
    private var lastInteraction = 0L
    private var seekHoldStart = 0L
    private var seekLastRepeat = 0L
    private var seekCommitJob: Job? = null
    private var zapJob: Job? = null
    private var digitsJob: Job? = null
    private var reconnectJob: Job? = null
    private var thumbs: ThumbFetcher? = null
    private var nextDismissed = false
    private var bufferingSince = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Repo.account.value == null) { finish(); return }

        readIntent(intent)
        player = Players.create(this, vm.isLive)
        player.addListener(listener)
        vm.subtitleSize = Repo.store.subtitleSize

        setContent { Content() }

        startCurrent(intent.getLongExtra("start", 0L))
        startTicker()
    }

    /** واجهة المشغل — نسخة الجوال تستبدلها بواجهة لمس */
    @androidx.compose.runtime.Composable
    protected open fun Content() { AppTheme { PlayerScreen(vm, player, this) } }

    // ───── أوامر عامة (للواجهات اللمس) ─────
    fun togglePlayPause() { if (player.isPlaying) player.pause() else player.play(); vm.overlay = true; touch() }
    fun seekBy(ms: Long) {
        val d = player.duration.takeIf { it != MC.TIME_UNSET && it > 0 } ?: return
        player.seekTo((player.currentPosition + ms).coerceIn(0L, (d - 1_000).coerceAtLeast(0L))); touch()
    }
    /** معاينة مكان التقديم (مع الصورة) بدون ما نقدم فعلياً */
    fun previewSeek(targetMs: Long) {
        val d = player.duration.takeIf { it != MC.TIME_UNSET && it > 0 } ?: return
        if (vm.seekTarget == null) vm.seekOrigin = player.currentPosition
        val t = targetMs.coerceIn(0L, (d - 1_000).coerceAtLeast(0L))
        vm.seekTarget = t; vm.overlay = true; touch()
        thumbs?.request(t)
    }
    fun commitSeekNow() = commitSeek()
    fun cancelSeekNow() = cancelSeek()
    fun nextEpisodeNow() = playNextEpisode()
    fun dismissNextEpisode() { nextDismissed = true; vm.countdown = -1 }
    fun zapBy(dir: Int) = zap(dir)
    fun retryNow() { retries = 0; reconnectJob?.cancel(); reconnect("إعادة المحاولة") }
    fun keepOverlay() = touch()
    fun toggleFavoriteChannel(): Boolean {
        val c = vm.channel ?: return false
        vm.favorite = Repo.toggleFavorite(Kind.LIVE, c.id); return vm.favorite
    }
    fun showLiveBanner() = showBanner()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        saveProgress()
        setIntent(intent)
        readIntent(intent)
        player.stop()
        startCurrent(intent.getLongExtra("start", 0L))
    }

    private fun readIntent(i: Intent) {
        type = i.getStringExtra("type") ?: "movie"
        contentId = i.getIntExtra("id", 0)
        ext = i.getStringExtra("ext") ?: ""
        seriesId = i.getIntExtra("sid", 0)
        season = i.getIntExtra("season", 0)
        epNum = i.getIntExtra("num", 0)
        image = i.getStringExtra("image") ?: ""
        cover = i.getStringExtra("cover") ?: image
        listKey = i.getStringExtra("list") ?: LiveKeys.ALL
        vm.isLive = type == "live"
        vm.title = i.getStringExtra("title") ?: ""
        vm.subtitle = if (type == "episode" && season > 0) "الموسم $season · الحلقة $epNum" else ""
        vm.nextEp = null; vm.countdown = -1; nextDismissed = false
    }

    // ───────────────────────── التشغيل ─────────────────────────

    private fun startCurrent(startMs: Long) {
        retries = 0
        vm.message = null
        when (type) {
            "live" -> {
                Repo.ensureLoadedBlocking()
                var list = channelsFor(listKey)
                if (list.isEmpty()) { list = channelsFor(LiveKeys.ALL); listKey = LiveKeys.ALL }
                vm.channels = list
                vm.listTitle = com.wm.iptv.ui.screens.liveKeyTitle(listKey)
                val ch = Repo.catalog.value.channelById[contentId] ?: list.firstOrNull()
                if (ch == null) { Toast.makeText(this, "القناة غير موجودة", Toast.LENGTH_SHORT).show(); finish(); return }
                playChannel(ch)
            }
            else -> {
                val api = Repo.api()
                url = if (type == "episode") api.episodeUrl(contentId, ext) else api.movieUrl(contentId, ext)
                player.setMediaItem(Players.item(url), startMs.coerceAtLeast(0L))
                player.playWhenReady = true
                player.prepare()
                vm.overlay = true
                touch()
                setupThumbs()
                if (type == "episode") loadNextEpisode()
            }
        }
    }

    private fun playChannel(ch: Channel) {
        vm.channel = ch
        vm.zapPreview = null
        vm.title = ch.name
        vm.favorite = Repo.isFavorite(Kind.LIVE, ch.id)
        contentId = ch.id
        Repo.store.pushRecentChannel(ch.id)
        url = Repo.liveUrl(ch.id)
        player.setMediaItem(Players.item(url))
        player.playWhenReady = true
        player.prepare()
        showBanner()
        lifecycleScope.launch { vm.epg = Repo.epg(ch.id) }
    }

    private fun setupThumbs() {
        thumbs?.stop(); thumbs = null; vm.thumb = null
        val mode = Repo.store.seekPreviewMode
        val allowed = mode == 1 || (mode == 0 && Repo.store.maxConnections >= 2)
        vm.thumbsOn = allowed
        if (!allowed) return
        thumbs = ThumbFetcher(url, lifecycleScope) { _, img -> vm.thumb = img }.also { it.start() }
    }

    private fun loadNextEpisode() {
        val sid = seriesId
        if (sid == 0) return
        lifecycleScope.launch {
            val info = Repo.seriesInfo(sid) ?: return@launch
            val all = info.episodes.values.flatten().sortedWith(compareBy({ it.season }, { it.number }))
            val i = all.indexOfFirst { it.id == contentId }
            if (i < 0) return@launch
            val cur = all[i]
            if (season == 0) { season = cur.season; epNum = cur.number; vm.subtitle = "الموسم $season · الحلقة $epNum" }
            if (vm.title.isBlank()) vm.title = info.name
            vm.nextEp = all.getOrNull(i + 1)
        }
    }

    private fun playNextEpisode() {
        val e = vm.nextEp ?: return
        saveProgress()
        val p = Repo.store.progress("e:${e.id}")
        contentId = e.id; ext = e.ext; season = e.season; epNum = e.number
        image = e.image.ifBlank { cover }
        vm.subtitle = "الموسم $season · الحلقة $epNum"
        vm.nextEp = null; vm.countdown = -1; nextDismissed = false
        player.stop()
        startCurrent(if (p != null && !p.finished) p.position else 0L)
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            vm.buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_BUFFERING) { if (bufferingSince == 0L) bufferingSince = SystemClock.uptimeMillis() } else bufferingSince = 0L
            if (state == Player.STATE_READY) { retries = 0; vm.message = null }
            if (state == Player.STATE_ENDED) {
                when {
                    vm.isLive -> reconnect("انقطع البث") // البث المباشر ما المفروض ينتهي
                    vm.nextEp != null && Repo.store.autoNextEpisode && !nextDismissed -> playNextEpisode()
                    else -> { saveProgress(); finish() }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) { vm.playing = isPlaying }

        override fun onTracksChanged(tracks: Tracks) { vm.tracks = tracks }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition(); player.prepare(); return
            }
            reconnect(
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "السيرفر رفض التشغيل (ممكن عدد الاتصالات ممتلئ)"
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "مشكلة في الاتصال"
                    PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "الجهاز ما قدر يشغل صيغة الفيديو"
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "ملف الفيديو فيه مشكلة"
                    else -> "تعذر التشغيل"
                }
            )
        }
    }

    /** إعادة اتصال تلقائية بدون ما تطلع من المشغل */
    private fun reconnect(reason: String) {
        if (reconnectJob?.isActive == true) return
        val max = if (vm.isLive) 30 else 6
        if (retries >= max) {
            vm.message = "$reason — اضغط OK للمحاولة مرة ثانية"
            return
        }
        retries++
        vm.message = "$reason · جارٍ إعادة الاتصال…"
        val pos = player.currentPosition
        reconnectJob = lifecycleScope.launch {
            delay(if (vm.isLive) minOf(1000L * retries, 5000L) else minOf(1500L * retries, 6000L))
            if (vm.isLive) player.setMediaItem(Players.item(url)) else player.setMediaItem(Players.item(url), pos)
            player.playWhenReady = true
            player.prepare()
        }
    }

    // ───────────────────────── المؤقت ─────────────────────────

    private fun startTicker() {
        lifecycleScope.launch {
            var tick = 0
            while (isActive) {
                delay(500)
                tick++
                if (!vm.isLive) {
                    vm.position = player.currentPosition.coerceAtLeast(0L)
                    vm.duration = player.duration.takeIf { it != MC.TIME_UNSET && it > 0 } ?: 0L
                    vm.buffered = player.bufferedPosition
                    if (tick % 20 == 0) saveProgress()
                    checkNextEpisode()
                }
                val now = SystemClock.uptimeMillis()
                if (vm.overlay && vm.playing && vm.seekTarget == null && vm.panel == null && now - lastInteraction > 4500) vm.overlay = false
                if (vm.banner && vm.panel == null && vm.zapPreview == null && now - lastInteraction > 5000) vm.banner = false
                // البث علّق في التحميل فترة طويلة
                if (vm.isLive && bufferingSince > 0 && now - bufferingSince > 15_000) { bufferingSince = 0L; reconnect("البث علّق") }
            }
        }
    }

    private fun checkNextEpisode() {
        val next = vm.nextEp ?: return
        if (!Repo.store.autoNextEpisode || nextDismissed) return
        val d = vm.duration
        if (d <= 0) return
        val left = d - vm.position
        if (left in 1..20_000 && vm.countdown < 0) {
            vm.countdown = 10
            lifecycleScope.launch {
                while (vm.countdown > 0 && vm.nextEp == next) { delay(1000); if (vm.countdown > 0) vm.countdown-- }
                if (vm.countdown == 0 && vm.nextEp == next) playNextEpisode()
            }
        }
    }

    private fun touch() { lastInteraction = SystemClock.uptimeMillis() }

    private fun showBanner() { vm.banner = true; touch() }

    // ───────────────────────── الحفظ ─────────────────────────

    private fun saveProgress() {
        if (vm.isLive || !::player.isInitialized) return
        val d = player.duration
        val p = player.currentPosition
        if (d == MC.TIME_UNSET || d <= 0 || p < 5_000) return
        val key = if (type == "episode") "e:$contentId" else "m:$contentId"
        val prog = Progress(key, p, d, System.currentTimeMillis(), vm.title,
            if (type == "episode") "م$season · ح$epNum" else "", image, ext, seriesId)
        Repo.store.saveProgress(prog)
        if (type == "episode" && prog.finished) Repo.store.setWatched(contentId, true)
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
        if (::player.isInitialized && !isInPictureInPictureMode) player.pause()
    }

    override fun onResume() {
        super.onResume()
        if (::player.isInitialized && vm.message == null) player.play()
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        // البث المباشر يوقف تماماً عشان ما يضل الاتصال مفتوح
        if (::player.isInitialized && vm.isLive) player.stop()
    }

    override fun onRestart() {
        super.onRestart()
        if (::player.isInitialized && vm.isLive) { player.prepare(); player.play() }
    }

    override fun onDestroy() {
        thumbs?.stop()
        if (::player.isInitialized) { saveProgress(); player.removeListener(listener); player.release() }
        Repo.bump()
        super.onDestroy()
    }

    // ───────────────────────── الريموت ─────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::player.isInitialized) return super.dispatchKeyEvent(event)
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP

        // لوحة مفتوحة (الصوت والترجمة / قائمة القنوات): الريموت يتحكم فيها
        if (vm.panel != null) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (up) { vm.panel = null; touch() }
                return true
            }
            touch()
            return super.dispatchKeyEvent(event)
        }

        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (!up) return true
            when {
                vm.seekTarget != null -> cancelSeek()
                vm.countdown >= 0 -> { nextDismissed = true; vm.countdown = -1 }
                vm.digits.isNotEmpty() -> { digitsJob?.cancel(); vm.digits = "" }
                vm.zapPreview != null -> { zapJob?.cancel(); vm.zapPreview = null }
                vm.isLive && vm.banner -> vm.banner = false
                !vm.isLive && vm.overlay && vm.playing -> vm.overlay = false
                else -> { saveProgress(); finish() }
            }
            return true
        }

        return if (vm.isLive) liveKey(code, event, down) else vodKey(code, event, down)
    }

    private fun vodKey(code: Int, ev: KeyEvent, down: Boolean): Boolean {
        val up = ev.action == KeyEvent.ACTION_UP
        when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (down) seekPress(+1, ev) else if (up) scheduleCommit(650); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { if (down) seekPress(-1, ev) else if (up) scheduleCommit(650); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!up) return true
                when {
                    vm.countdown >= 0 -> playNextEpisode()
                    vm.message != null && !vm.buffering -> { retries = 0; reconnectJob?.cancel(); reconnect("إعادة المحاولة") }
                    vm.seekTarget != null -> commitSeek()
                    else -> { if (player.isPlaying) player.pause() else player.play(); vm.overlay = true }
                }
                touch(); return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { if (up) { if (player.isPlaying) player.pause() else player.play(); vm.overlay = true; touch() }; return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (up) { player.play(); touch() }; return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (up) { player.pause(); vm.overlay = true; touch() }; return true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { if (up) playNextEpisode(); return true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_CAPTIONS -> { if (up) { vm.panel = Panel.TRACKS; vm.overlay = true; touch() }; return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_INFO -> { if (up) { vm.overlay = !vm.overlay || !vm.playing; touch() }; return true }
        }
        return super.dispatchKeyEvent(ev)
    }

    private fun liveKey(code: Int, ev: KeyEvent, down: Boolean): Boolean {
        val up = ev.action == KeyEvent.ACTION_UP
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (down) zap(-1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_UP -> { if (down) zap(+1); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!up) return true
                when {
                    vm.zapPreview != null -> { zapJob?.cancel(); playChannel(vm.zapPreview!!) }
                    vm.message != null -> { retries = 0; reconnectJob?.cancel(); reconnect("إعادة المحاولة") }
                    else -> { vm.panel = Panel.CHANNELS; touch() }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> {
                if (up) { if (vm.banner) vm.banner = false else showBanner() }
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (up) vm.channel?.let { c ->
                    vm.favorite = Repo.toggleFavorite(Kind.LIVE, c.id)
                    Toast.makeText(this, if (vm.favorite) "انضافت للمفضلة" else "انشالت من المفضلة", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            KeyEvent.KEYCODE_LAST_CHANNEL -> { if (up) previousChannel(); return true }
            KeyEvent.KEYCODE_CAPTIONS -> { if (up) { vm.panel = Panel.TRACKS; touch() }; return true }
        }
        if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            if (down && ev.repeatCount == 0) typeDigit(code - KeyEvent.KEYCODE_0)
            return true
        }
        return super.dispatchKeyEvent(ev)
    }

    // ───── تقديم ورجوع ─────

    private fun seekPress(dir: Int, ev: KeyEvent) {
        val dur = player.duration.takeIf { it != MC.TIME_UNSET && it > 0 } ?: return
        seekCommitJob?.cancel()
        val now = SystemClock.uptimeMillis()
        if (vm.seekTarget == null) { vm.seekOrigin = player.currentPosition; vm.seekTarget = vm.seekOrigin }
        var t = vm.seekTarget ?: 0L
        if (ev.repeatCount == 0) {
            t += dir * 5_000L
            seekHoldStart = now; seekLastRepeat = now
            vm.speedLabel = ""
        } else {
            // ضغط مستمر: يتسارع مع الوقت
            val held = now - seekHoldStart
            val mult = when { held < 1_500 -> 2; held < 3_500 -> 4; held < 6_000 -> 8; else -> 16 }
            val dt = (now - seekLastRepeat).coerceIn(10, 250)
            seekLastRepeat = now
            t += dir * mult * 10L * dt  // ×2 = ٢٠ ثانية لكل ثانية ضغط
            vm.speedLabel = "×$mult"
        }
        t = t.coerceIn(0L, (dur - 1_500).coerceAtLeast(0L))
        vm.seekTarget = t
        vm.overlay = true
        touch()
        thumbs?.request(t)
    }

    private fun scheduleCommit(ms: Long) {
        if (vm.seekTarget == null) return
        seekCommitJob?.cancel()
        seekCommitJob = lifecycleScope.launch { delay(ms); commitSeek() }
    }

    private fun commitSeek() {
        seekCommitJob?.cancel()
        val t = vm.seekTarget ?: return
        player.seekTo(t)
        vm.position = t
        vm.seekTarget = null
        vm.speedLabel = ""
        if (!player.playWhenReady) player.play()
        touch()
    }

    private fun cancelSeek() {
        seekCommitJob?.cancel()
        vm.seekTarget = null
        vm.speedLabel = ""
        touch()
    }

    // ───── تقليب القنوات ─────

    private fun zap(dir: Int) {
        val list = vm.channels
        if (list.isEmpty()) return
        val base = vm.zapPreview ?: vm.channel
        val i = list.indexOfFirst { it.id == base?.id }
        val n = if (i < 0) 0 else Math.floorMod(i + dir, list.size)
        val target = list[n]
        vm.zapPreview = target
        showBanner()
        lifecycleScope.launch { vm.epg = Repo.epg(target.id) }
        zapJob?.cancel()
        zapJob = lifecycleScope.launch {
            delay(450) // لو تقلّب بسرعة ما يحمّل كل قناة تعديها
            if (vm.zapPreview?.id == target.id) playChannel(target)
        }
    }

    fun selectChannel(ch: Channel) {
        vm.panel = null
        if (ch.id != vm.channel?.id) playChannel(ch) else showBanner()
    }

    fun previousChannel() {
        val prev = Repo.store.previousChannel
        val ch = Repo.catalog.value.channelById[prev] ?: return
        vm.panel = null
        playChannel(ch)
    }

    private fun typeDigit(d: Int) {
        vm.digits = (vm.digits + d).take(5)
        touch()
        digitsJob?.cancel()
        digitsJob = lifecycleScope.launch {
            delay(1400)
            val n = vm.digits.toIntOrNull()
            vm.digits = ""
            val ch = vm.channels.firstOrNull { it.num == n } ?: Repo.catalog.value.channels.firstOrNull { it.num == n }
            if (ch != null) playChannel(ch) else Toast.makeText(this@PlayerActivity, "ما فيه قناة رقم $n", Toast.LENGTH_SHORT).show()
        }
    }

    // ───── الصوت والترجمة ─────

    fun selectTrack(group: Tracks.Group, index: Int) {
        val tt = group.type
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(tt, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            .build()
        val lang = group.getTrackFormat(index).language
        if (!lang.isNullOrBlank()) {
            if (tt == MC.TRACK_TYPE_AUDIO) Repo.store.preferredAudio = lang
            if (tt == MC.TRACK_TYPE_TEXT) Repo.store.preferredSubtitle = lang
        }
        touch()
    }

    fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true).build()
        Repo.store.preferredSubtitle = ""
        touch()
    }

    fun cycleSubtitleSize() {
        vm.subtitleSize = (vm.subtitleSize + 1) % 4
        Repo.store.subtitleSize = vm.subtitleSize
        touch()
    }

    fun cycleResize() {
        vm.resizeMode = when (vm.resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        touch()
    }
}
