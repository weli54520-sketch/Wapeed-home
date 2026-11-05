package com.wm.iptv.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

sealed class LoadState {
    object Idle : LoadState()
    data class Loading(val step: String, val firstTime: Boolean) : LoadState()
    data class Error(val message: String) : LoadState()
}

/** مصدر البيانات الوحيد للتطبيق */
object Repo {
    lateinit var store: Store
        private set
    private lateinit var appDir: File

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account

    private val _catalog = MutableStateFlow(Catalog())
    val catalog: StateFlow<Catalog> = _catalog

    private val _state = MutableStateFlow<LoadState>(LoadState.Idle)
    val state: StateFlow<LoadState> = _state

    /** يزيد كل ما تغيرت المفضلة أو أماكن التوقف عشان الواجهة تتحدث */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version
    fun bump() { _version.value = _version.value + 1 }

    private val refreshLock = Mutex()
    private var refreshJob: Job? = null

    private val epgCache = ConcurrentHashMap<Int, Pair<Long, List<EpgEntry>>>()
    private val vodCache = ConcurrentHashMap<Int, VodInfo>()
    private val seriesCache = ConcurrentHashMap<Int, SeriesInfo>()

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = Store(context.applicationContext)
        appDir = context.applicationContext.filesDir
        _account.value = store.currentAccount()
    }

    fun api(): XtreamApi = XtreamApi(_account.value ?: throw ApiException("لا يوجد حساب"))

    private fun dir(a: Account): File = File(appDir, "acc_${a.id.filter { it.isLetterOrDigit() }}").apply { mkdirs() }

    // ───────────── التشغيل الأول ─────────────
    /** يقرأ الكاش فوراً، وبعدين يحدث من السيرفر لو قديم */
    fun start() {
        val a = _account.value ?: return
        scope.launch {
            if (_catalog.value.isEmpty) {
                val c = withContext(Dispatchers.IO) { readCache(a) }
                if (_account.value?.id == a.id) _catalog.value = c
            }
            val old = System.currentTimeMillis() - store.lastRefresh > 12 * 3600 * 1000L
            if (_catalog.value.isEmpty || old) refresh()
            else refreshUserInfoQuietly()
        }
    }

    /** للمشغل لو انقتل التطبيق ورجع: يقرأ الكاش بشكل متزامن */
    fun ensureLoadedBlocking() {
        val a = _account.value ?: return
        if (_catalog.value.isEmpty) _catalog.value = readCache(a)
    }

    private fun readCache(a: Account): Catalog {
        val d = dir(a)
        return try {
            Catalog(
                liveCats = CatalogParser.categories(File(d, "live_cats.json")),
                movieCats = CatalogParser.categories(File(d, "vod_cats.json")),
                seriesCats = CatalogParser.categories(File(d, "series_cats.json")),
                channels = CatalogParser.channels(File(d, "live.json")),
                movies = CatalogParser.movies(File(d, "vod.json")),
                series = CatalogParser.series(File(d, "series.json")),
            )
        } catch (e: Exception) {
            Catalog()
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        val a = _account.value ?: return
        refreshJob = scope.launch {
            refreshLock.withLock {
                val first = _catalog.value.isEmpty
                try {
                    withContext(Dispatchers.IO) {
                        val api = XtreamApi(a)
                        val d = dir(a)
                        _state.value = LoadState.Loading("التحقق من الاشتراك…", first)
                        val info = api.authenticate()
                        store.saveUserInfo(info)
                        _state.value = LoadState.Loading("تحميل القنوات…", first)
                        api.download("get_live_categories", File(d, "live_cats.json"))
                        api.download("get_live_streams", File(d, "live.json"))
                        _state.value = LoadState.Loading("تحميل الأفلام…", first)
                        api.download("get_vod_categories", File(d, "vod_cats.json"))
                        api.download("get_vod_streams", File(d, "vod.json"))
                        _state.value = LoadState.Loading("تحميل المسلسلات…", first)
                        api.download("get_series_categories", File(d, "series_cats.json"))
                        api.download("get_series", File(d, "series.json"))
                        _state.value = LoadState.Loading("ترتيب القوائم…", first)
                        val c = readCache(a)
                        if (_account.value?.id == a.id) {
                            _catalog.value = c
                            store.lastRefresh = System.currentTimeMillis()
                        }
                    }
                    _state.value = LoadState.Idle
                    bump()
                } catch (e: Exception) {
                    _state.value = if (first) LoadState.Error(e.message ?: "خطأ غير معروف") else LoadState.Idle
                }
            }
        }
    }

    private fun refreshUserInfoQuietly() {
        val a = _account.value ?: return
        scope.launch(Dispatchers.IO) {
            try { store.saveUserInfo(XtreamApi(a).authenticate()); bump() } catch (_: Exception) {}
        }
    }

    // ───────────── الحسابات ─────────────
    /** يتحقق من البيانات ويضيف الحساب. يرمي ApiException لو فيه مشكلة */
    suspend fun login(name: String, server: String, user: String, pass: String): Account {
        val srv = XtreamApi.normalizeServer(server)
        if (srv.isEmpty() || user.isBlank() || pass.isBlank()) throw ApiException("عبّ كل الحقول")
        val existing = store.accounts().firstOrNull { it.server.equals(srv, true) && it.username == user.trim() }
        val acc = Account(existing?.id ?: System.currentTimeMillis().toString(), name.trim().ifEmpty { "اشتراكي" }, srv, user.trim(), pass.trim())
        val info = withContext(Dispatchers.IO) { XtreamApi(acc).authenticate() }
        store.saveAccount(acc)
        switchAccount(acc.id)
        store.saveUserInfo(info)
        return acc
    }

    fun switchAccount(id: String) {
        store.currentAccountId = id
        _account.value = store.currentAccount()
        _catalog.value = Catalog()
        epgCache.clear(); vodCache.clear(); seriesCache.clear()
        _state.value = LoadState.Idle
        bump()
        start()
    }

    fun deleteAccount(id: String) {
        store.accounts().firstOrNull { it.id == id }?.let { a -> dir(a).deleteRecursively() }
        store.deleteAccount(id)
        _account.value = store.currentAccount()
        _catalog.value = Catalog()
        bump()
        start()
    }

    // ───────────── التصنيفات الظاهرة ─────────────
    fun visibleCategories(kind: Kind): List<Category> {
        val c = _catalog.value
        val hidden = store.hiddenCategories(kind)
        val list = when (kind) { Kind.LIVE -> c.liveCats; Kind.MOVIE -> c.movieCats; Kind.SERIES -> c.seriesCats }
        return list.filter { it.id !in hidden }
    }

    fun allCategories(kind: Kind): List<Category> {
        val c = _catalog.value
        return when (kind) { Kind.LIVE -> c.liveCats; Kind.MOVIE -> c.movieCats; Kind.SERIES -> c.seriesCats }
    }

    // ───────────── التفاصيل (مع كاش) ─────────────
    suspend fun epg(id: Int): List<EpgEntry> {
        val now = System.currentTimeMillis()
        epgCache[id]?.let { (t, l) -> if (now - t < 10 * 60 * 1000 && (l.isEmpty() || l.first().end > now)) return l }
        return try {
            val l = withContext(Dispatchers.IO) { api().shortEpg(id) }
            epgCache[id] = now to l
            l
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun cachedEpg(id: Int): List<EpgEntry>? = epgCache[id]?.second

    suspend fun vodInfo(id: Int): VodInfo? {
        vodCache[id]?.let { return it }
        return try {
            withContext(Dispatchers.IO) { api().vodInfo(id) }.also { vodCache[id] = it }
        } catch (_: Exception) { null }
    }

    suspend fun seriesInfo(id: Int): SeriesInfo? {
        seriesCache[id]?.let { return it }
        return try {
            withContext(Dispatchers.IO) { api().seriesInfo(id) }.also { seriesCache[id] = it }
        } catch (_: Exception) { null }
    }

    fun cachedSeriesInfo(id: Int): SeriesInfo? = seriesCache[id]

    // ───────────── اختصارات ─────────────
    fun toggleFavorite(kind: Kind, id: Int): Boolean = store.toggleFavorite(kind, id).also { bump() }
    fun isFavorite(kind: Kind, id: Int): Boolean = id in store.favorites(kind)

    fun liveUrl(id: Int) = api().liveUrl(id, store.liveHls)
}
