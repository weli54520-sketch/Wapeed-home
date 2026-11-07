package com.wm.iptv.mobile

import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
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

    androidx.compose.runtime.LaunchedEffect(Unit) { com.wm.iptv.update.Updater.check(quiet = true) }
    MUpdateDialog()

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
        Text("Waleed TV", fontSize = 40.sp, fontWeight = FontWeight.Bold)
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
    val mode = Repo.store.barMode
    // الشريط: يختفي لما تنزل بالقائمة ويرجع لما تطلع (أو دايماً ظاهر / مخفي حسب الإعدادات)
    var barVisible by remember { mutableStateOf(true) }
    val modeNow = rememberUpdatedState(mode)
    LaunchedEffect(route, mode) { barVisible = mode != 2 }
    val barShown = showBar && (mode == 1 || barVisible)
    val scrollConn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (modeNow.value != 1) {
                    if (available.y < -8f) barVisible = false else if (available.y > 8f) barVisible = true
                }
                return Offset.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize().nestedScroll(scrollConn)) {
        CompositionLocalProvider(LocalBarSpace provides if (showBar && mode != 2) 96.dp else 0.dp) {
            AnimatedContent(
                targetState = route,
                // انتقال سريع: الصفحة القديمة تختفي بسرعة عشان ما ترسم صفحتين ثقيلة مع بعض
                transitionSpec = { fadeIn(tween(170, delayMillis = 30)) togetherWith fadeOut(tween(80)) },
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
            barShown, modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.slideInVertically { it } + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + fadeOut(),
        ) { FloatingBar(nav, route) }
        // شريط مخفي: اسحب من أسفل الشاشة لفوق (أو اضغطه) يطلع
        if (showBar && !barShown) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp)
                .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -3f) barVisible = true } }
                .plainClick { barVisible = true })
        }
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

/**
 * شريط عائم بستايل زجاجي مثل الآيفون:
 * - الفقاعة تتحرك بنعومة بين الأقسام، وتقدر تمسكها وتسحبها بإصبعك على الكبسولة
 * - تنتقل للقسم لما ترفع إصبعك، والضغطة العادية على أي قسم تشتغل برضو
 */
@Composable
private fun FloatingBar(nav: Navigator, current: Route) {
    val index = tabs.indexOfFirst { it.route == current || (current == Route.Settings && it.route == Route.Home) }.coerceAtLeast(0)
    val shape = RoundedCornerShape(999.dp)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    // محرّك واحد فقط للفقاعة (مصدر الحقيقة الوحيد): للسحب بزنبرك سريع، وللاستقرار بزنبرك ناعم
    val bubbleX = remember { Animatable(0f) }
    // أول ما يظهر الشريط (حتى بعد ما يختفي ويرجع مع التمرير) الفقاعة تبدأ مباشرة مكان القسم الحالي بدون انزلاق
    var placed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "press")
    val dragSpec = remember { spring<Float>(dampingRatio = 0.9f, stiffness = 1500f) }

    Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(66.dp)
                .graphicsLayer { scaleX = 1f + 0.02f * press; scaleY = 1f + 0.05f * press }
                .shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                // زجاج: طبقة غامقة شفافة + لمعة بيضاء من فوق + إطار رفيع لامع
                .background(Color(0x700A0A0E))
                .background(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x00FFFFFF))))
                .border(0.8.dp, Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x0DFFFFFF))), shape)
                .padding(5.dp)
        ) {
            val itemW = maxWidth / tabs.size
            val wpx = with(density) { itemW.toPx() }
            val maxPx = wpx * (tabs.size - 1)
            val bubblePx = if (!placed && !dragging) index * wpx else bubbleX.value
            val shownIndex = if (dragging && wpx > 0f) (dragPx / wpx).roundToInt().coerceIn(0, tabs.size - 1) else index

            // الفقاعة تثبت على القسم الحالي (أول مرة بدون حركة، وبعدها بنعومة من مكانها الحالي)
            LaunchedEffect(index, wpx, dragging) {
                if (dragging || wpx <= 0f) return@LaunchedEffect
                if (!placed) { bubbleX.snapTo(index * wpx); placed = true }
                else bubbleX.animateTo(index * wpx, spring(dampingRatio = 0.78f, stiffness = 420f))
            }
            // اهتزاز خفيف كل ما تعدي على قسم أثناء السحب
            LaunchedEffect(shownIndex) { if (dragging) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

            fun finishDrag() {
                if (!dragging) return
                val target = (dragPx / wpx).roundToInt().coerceIn(0, tabs.size - 1)
                dragging = false
                val r = tabs[target].route
                // نقارن بالقسم الحالي الفعلي (مو القيمة القديمة المحفوظة داخل الإيماءة)
                if (r != nav.top) nav.go(r)
            }

            Box(
                Modifier.fillMaxSize().pointerInput(wpx, rtl) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // أول ما تلمس: الفقاعة تروح تحت إصبعك مباشرة (مركزها = مكان إصبعك)
                        fun target(x: Float) = ((if (rtl) size.width - x else x) - wpx / 2f).coerceIn(0f, maxPx)
                        dragPx = target(down.position.x)
                        dragging = true
                        placed = true
                        scope.launch { bubbleX.animateTo(dragPx, dragSpec) }
                        drag(down.id) { change ->
                            change.consume()
                            dragPx = target(change.position.x)
                            scope.launch { bubbleX.animateTo(dragPx, dragSpec) }
                        }
                        // رفعت إصبعك: تثبت على أقرب قسم وتنتقل له (والضغطة العادية تنتقل لنفس القسم اللي ضغطته)
                        finishDrag()
                    }
                }
            ) {
                // فقاعة التحديد (زجاجية، وتكبر شوي وأنت ماسكها)
                Box(
                    Modifier.offset { IntOffset(bubblePx.roundToInt(), 0) }.width(itemW).fillMaxHeight()
                        .graphicsLayer { scaleX = 1f + 0.12f * press; scaleY = 1f + 0.1f * press }
                        .clip(shape)
                        .background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x14FFFFFF))))
                        .border(0.8.dp, Brush.verticalGradient(listOf(Color(0x73FFFFFF), Color(0x1AFFFFFF))), shape)
                )
                Row(Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { i, t ->
                        val sel = i == shownIndex
                        val color by animateColorAsState(if (sel) Color.White else Color(0xFFB8B8BE), label = "c")
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
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
}
