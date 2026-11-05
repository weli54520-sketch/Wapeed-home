package com.wm.iptv

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wm.iptv.data.LoadState
import com.wm.iptv.data.Repo
import com.wm.iptv.ui.AppTheme
import com.wm.iptv.ui.C
import com.wm.iptv.ui.CenterMessage
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Navigator
import com.wm.iptv.ui.Pill
import com.wm.iptv.ui.Route
import com.wm.iptv.ui.clockText
import com.wm.iptv.ui.screens.AccountsScreen
import com.wm.iptv.ui.screens.HiddenCategoriesScreen
import com.wm.iptv.ui.screens.HomeScreen
import com.wm.iptv.ui.screens.LiveScreen
import com.wm.iptv.ui.screens.LoginScreen
import com.wm.iptv.ui.screens.MovieDetailScreen
import com.wm.iptv.ui.screens.MoviesScreen
import com.wm.iptv.ui.screens.SearchScreen
import com.wm.iptv.ui.screens.SeriesDetailScreen
import com.wm.iptv.ui.screens.SeriesListScreen
import com.wm.iptv.ui.screens.SettingsScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val nav = Navigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        Repo.start()
        setContent { AppTheme { Root(nav, onExit = { finish() }) } }
    }

    override fun onResume() {
        super.onResume()
        Repo.bump() // يحدث "أكمل المشاهدة" بعد الرجوع من المشغل
    }
}

@Composable
private fun Root(nav: Navigator, onExit: () -> Unit) {
    val account by Repo.account.collectAsState()
    val state by Repo.state.collectAsState()
    val catalog by Repo.catalog.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        when {
            account == null -> LoginScreen(onDone = { nav.stack.clear(); nav.stack.add(Route.Home) })
            state is LoadState.Loading && (state as LoadState.Loading).firstTime -> LoadingScreen((state as LoadState.Loading).step)
            // شاشة الحسابات تشتغل حتى لو صار خطأ في التحميل
            state is LoadState.Error && catalog.isEmpty && nav.top != Route.Accounts && nav.top != Route.AddAccount ->
                CenterMessage("ما قدرنا نحمّل القوائم", (state as LoadState.Error).message) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pill("إعادة المحاولة", icon = Ico.Refresh, big = true, onClick = { Repo.refresh() })
                        Pill("الحسابات", icon = Ico.User, big = true, onClick = { nav.go(Route.Accounts) })
                    }
                }
            else -> MainArea(nav)
        }
    }

    // يشيك على التحديثات بهدوء أول ما يفتح التطبيق
    LaunchedEffect(Unit) { com.wm.iptv.update.Updater.check(quiet = true) }
    com.wm.iptv.ui.TvUpdateDialog()

    var lastBack by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    BackHandler {
        if (!nav.back()) {
            val now = System.currentTimeMillis()
            if (now - lastBack < 2200) onExit()
            else { lastBack = now; Toast.makeText(ctx, "اضغط رجوع مرة ثانية للخروج", Toast.LENGTH_SHORT).show() }
        }
    }
}

@Composable
private fun LoadingScreen(step: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("WM TV", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(color = C.Text, strokeWidth = 3.dp)
        Spacer(Modifier.height(20.dp))
        Text(step, color = C.Text3, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text("أول مرة ياخذ شوي، بعدها يفتح على طول", color = C.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun MainArea(nav: Navigator) {
    val holder = rememberSaveableStateHolder()
    val route = nav.top
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            contentKey = { it.key },
            label = "nav",
        ) { r ->
            holder.SaveableStateProvider(r.key) {
                Box(Modifier.fillMaxSize()) {
                    when (r) {
                        Route.Home -> HomeScreen(nav)
                        Route.Live -> LiveScreen(nav)
                        Route.Movies -> MoviesScreen(nav)
                        Route.SeriesList -> SeriesListScreen(nav)
                        Route.Search -> SearchScreen(nav)
                        Route.Settings -> SettingsScreen(nav)
                        is Route.MovieDetail -> MovieDetailScreen(nav, r.id)
                        is Route.SeriesDetail -> SeriesDetailScreen(nav, r.id)
                        is Route.HiddenCats -> HiddenCategoriesScreen(nav, r.kind)
                        Route.Accounts -> AccountsScreen(nav)
                        Route.AddAccount -> LoginScreen(onDone = { nav.go(Route.Home) }, onCancel = { nav.back() })
                    }
                }
            }
        }
        val state by Repo.state.collectAsState()
        if (state is LoadState.Loading) {
            Text((state as LoadState.Loading).step, fontSize = 12.sp, color = C.Text3,
                modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)
                    .background(Color(0xCC1C1C1E), RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
}

/** شريط التنقل العلوي بستايل Apple TV */
@Composable
fun TopBar(nav: Navigator, current: Route, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    Row(modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("WM", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(" TV", fontSize = 20.sp, color = C.Text3)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.background(Color(0x14FFFFFF), RoundedCornerShape(999.dp)).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NavPill("الرئيسية", current == Route.Home) { nav.go(Route.Home) }
            NavPill("المباشر", current == Route.Live) { nav.go(Route.Live) }
            NavPill("الأفلام", current == Route.Movies) { nav.go(Route.Movies) }
            NavPill("المسلسلات", current == Route.SeriesList) { nav.go(Route.SeriesList) }
            NavPill("", current == Route.Search, Ico.Search) { nav.go(Route.Search) }
            NavPill("", current == Route.Settings, Ico.Gear) { nav.go(Route.Settings) }
        }
        Spacer(Modifier.weight(1f))
        Text(clockText(now), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = C.Text2, modifier = Modifier.width(90.dp))
    }
}

@Composable
private fun NavPill(text: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Pill(text, icon = icon, selected = selected, onClick = onClick)
}
