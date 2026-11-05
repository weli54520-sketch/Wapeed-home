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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
    val showBar = route.isTab
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBarSpace provides if (showBar) 96.dp else 0.dp) {
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
        }
        if (state is LoadState.Loading) {
            Text((state as LoadState.Loading).step, fontSize = 11.sp, color = C.Text3,
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                    .background(Color(0xE61C1C1E), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 5.dp))
        }
        // الشريط العائم
        androidx.compose.animation.AnimatedVisibility(
            showBar, modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.slideInVertically { it } + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + fadeOut(),
        ) { FloatingBar(nav, route) }
    }
}

private data class TabDef(val route: Route, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabDef(Route.Home, "الرئيسية", Ico.Home),
    TabDef(Route.Live, "المباشر", Ico.Tv),
    TabDef(Route.Movies, "الأفلام", Ico.Film),
    TabDef(Route.SeriesList, "المسلسلات", Ico.Stack),
    TabDef(Route.Search, "بحث", Ico.Search),
)

/** شريط عائم بستايل زجاجي: كبسولة فوق المحتوى، والتحديد فقاعة تتحرك بنعومة */
@Composable
private fun FloatingBar(nav: Navigator, current: Route) {
    val index = tabs.indexOfFirst { it.route == current || (current == Route.Settings && it.route == Route.Home) }.coerceAtLeast(0)
    val shape = RoundedCornerShape(999.dp)
    Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(66.dp)
                .shadow(28.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xE62C2C30), Color(0xF01A1A1D))))
                .border(0.8.dp, Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x0DFFFFFF))), shape)
                .padding(5.dp)
        ) {
            val itemW = maxWidth / tabs.size
            val x by animateDpAsState(itemW * index, spring(dampingRatio = 0.78f, stiffness = 420f), label = "tab")
            // فقاعة التحديد
            Box(
                Modifier.offset(x = x).width(itemW).fillMaxHeight().clip(shape)
                    .background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x1FFFFFFF))))
                    .border(0.6.dp, Color(0x33FFFFFF), shape)
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, t ->
                    val sel = i == index
                    val color by animateColorAsState(if (sel) Color.White else Color(0xFF9A9AA0), label = "c")
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(shape).plainClick { nav.go(t.route) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Icon(t.icon, t.label, tint = color, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(t.label, fontSize = 10.sp, color = color, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}
