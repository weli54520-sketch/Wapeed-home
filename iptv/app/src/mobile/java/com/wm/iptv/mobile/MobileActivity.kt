package com.wm.iptv.mobile

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wm.iptv.data.LoadState
import com.wm.iptv.data.Repo
import com.wm.iptv.player.PlayerLauncher
import com.wm.iptv.ui.AppTheme
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Navigator
import com.wm.iptv.ui.Route

class MobileActivity : ComponentActivity() {
    private val nav = Navigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        PlayerLauncher.target = MobilePlayerActivity::class.java
        Repo.start()
        setContent { AppTheme(scaleToTv = false) { MRoot(nav) { finish() } } }
    }

    override fun onResume() {
        super.onResume()
        Repo.bump()
    }
}

@Composable
private fun MRoot(nav: Navigator, onExit: () -> Unit) {
    val account by Repo.account.collectAsState()
    val state by Repo.state.collectAsState()
    val catalog by Repo.catalog.collectAsState()
    val ctx = LocalContext.current

    Box(Modifier.fillMaxSize().background(C.Bg).systemBarsPadding().imePadding()) {
        when {
            account == null -> MLoginScreen(onDone = { nav.stack.clear(); nav.stack.add(Route.Home) })
            state is LoadState.Loading && (state as LoadState.Loading).firstTime -> MLoading((state as LoadState.Loading).step)
            state is LoadState.Error && catalog.isEmpty && nav.top != Route.Accounts && nav.top != Route.AddAccount -> {
                Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("ما قدرنا نحمّل القوائم", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text((state as LoadState.Error).message, color = C.Text3, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(22.dp))
                    MBigButton("إعادة المحاولة", Ico.Refresh) { Repo.refresh() }
                    Spacer(Modifier.height(10.dp))
                    MBigButton("الحسابات", Ico.User, primary = false) { nav.go(Route.Accounts) }
                }
            }
            else -> MMain(nav)
        }
    }

    var lastBack by remember { mutableLongStateOf(0L) }
    BackHandler {
        if (!nav.back()) {
            val now = System.currentTimeMillis()
            if (now - lastBack < 2200) onExit()
            else { lastBack = now; Toast.makeText(ctx, "اسحب للرجوع مرة ثانية للخروج", Toast.LENGTH_SHORT).show() }
        }
    }
}

@Composable
private fun MLoading(step: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("WM TV", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = C.Text, strokeWidth = 3.dp)
        Spacer(Modifier.height(18.dp))
        Text(step, color = C.Text3, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text("أول مرة ياخذ شوي، بعدها يفتح على طول", color = C.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun MMain(nav: Navigator) {
    val holder = rememberSaveableStateHolder()
    val route = nav.top
    val state by Repo.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = route,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                contentKey = { it.key },
                label = "mnav",
            ) { r ->
                holder.SaveableStateProvider(r.key) {
                    Box(Modifier.fillMaxSize()) {
                        when (r) {
                            Route.Home -> MHomeScreen(nav)
                            Route.Live -> MLiveScreen(nav)
                            Route.Movies -> MBrowseScreen(nav, com.wm.iptv.data.Kind.MOVIE)
                            Route.SeriesList -> MBrowseScreen(nav, com.wm.iptv.data.Kind.SERIES)
                            Route.Search -> MSearchScreen(nav)
                            Route.Settings -> MSettingsScreen(nav)
                            is Route.MovieDetail -> MMovieDetail(nav, r.id)
                            is Route.SeriesDetail -> MSeriesDetail(nav, r.id)
                            is Route.HiddenCats -> MHiddenScreen(nav, r.kind)
                            Route.Accounts -> MAccountsScreen(nav)
                            Route.AddAccount -> MLoginScreen(onDone = { nav.go(Route.Home) }, onCancel = { nav.back() })
                        }
                    }
                }
            }
            if (state is LoadState.Loading) {
                Text((state as LoadState.Loading).step, fontSize = 11.sp, color = C.Text3,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)
                        .background(Color(0xE61C1C1E), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
        if (route.isTab || route == Route.Settings) BottomBar(nav, route)
    }
}

@Composable
private fun BottomBar(nav: Navigator, current: Route) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF0F0F12)).padding(top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Tab("الرئيسية", Ico.Home, current == Route.Home || current == Route.Settings) { nav.go(Route.Home) }
        Tab("المباشر", Ico.Tv, current == Route.Live) { nav.go(Route.Live) }
        Tab("الأفلام", Ico.Film, current == Route.Movies) { nav.go(Route.Movies) }
        Tab("المسلسلات", Ico.Stack, current == Route.SeriesList) { nav.go(Route.SeriesList) }
        Tab("بحث", Ico.Search, current == Route.Search) { nav.go(Route.Search) }
    }
}

@Composable
private fun Tab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) C.Text else C.Dim
    Column(
        Modifier.plainClick(onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 11.sp, color = color, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
