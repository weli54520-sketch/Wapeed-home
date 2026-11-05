@file:OptIn(ExperimentalFoundationApi::class)

package com.wm.iptv.mobile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.wm.iptv.ui.Art
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Plex
import com.wm.iptv.ui.ProgressLine

/** لمسة مع تصغير خفيف وقت الضغط (بدون موجة) */
@Composable
fun Modifier.tap(onLong: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "press")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .combinedClickable(interactionSource = src, indication = null, onClick = onClick, onLongClick = onLong)
}

@Composable
fun MPoster(
    title: String, image: String, seed: Int,
    modifier: Modifier = Modifier, width: Dp = Dp.Unspecified,
    badge: String? = null, progress: Float = 0f, favorite: Boolean = false,
    onLong: (() -> Unit)? = null, onClick: () -> Unit,
) {
    Column(if (width == Dp.Unspecified) modifier.fillMaxWidth() else modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).tap(onLong, onClick)) {
            Art(image, seed, title)
            if (badge != null) Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = C.Bg,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(C.Accent, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp))
            if (favorite) Icon(com.wm.iptv.ui.Ico.Star, null, tint = C.Star, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp))
            if (progress > 0.01f) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(6.dp), height = 3.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 12.sp, color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun MLandscape(
    title: String, sub: String, image: String, seed: Int,
    modifier: Modifier = Modifier, width: Dp = 220.dp, progress: Float = 0f, fit: Boolean = false,
    onLong: (() -> Unit)? = null, onClick: () -> Unit,
) {
    Column(modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).tap(onLong, onClick)) {
            Art(image, seed, title, if (fit) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x80000000)))))
            if (progress > 0.01f) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 8.dp), height = 3.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** زر حبة للمس */
@Composable
fun MPill(
    text: String, modifier: Modifier = Modifier, icon: ImageVector? = null,
    primary: Boolean = false, selected: Boolean = false, onClick: () -> Unit,
) {
    val bg = when { primary -> C.Text; selected -> C.Text; else -> C.Glass }
    val fg = if (primary || selected) C.Bg else C.Text
    Row(
        modifier.clip(RoundedCornerShape(999.dp)).tap(onClick = onClick).background(bg)
            .padding(horizontal = if (text.isEmpty()) 10.dp else 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp))
        if (text.isNotEmpty()) Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** زر كبير بعرض الشاشة (تشغيل / أكمل) */
@Composable
fun MBigButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, primary: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).tap(onClick = onClick).background(if (primary) C.Text else C.Glass)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        val fg = if (primary) C.Bg else C.Text
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MIconButton(icon: ImageVector, desc: String, modifier: Modifier = Modifier, tint: Color = C.Text, size: Dp = 22.dp, onClick: () -> Unit) {
    Box(modifier.size(42.dp).clip(CircleShape).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
fun MHeader(title: String, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun MSection(title: String, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, bottom = 10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** صف حبوب أفقي (تصنيفات / اختيارات) */
@Composable
fun MChips(items: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.first }) { (k, label) -> MPill(label, selected = k == selected, onClick = { onPick(k) }) }
    }
}

/** خانة كتابة للمس */
@Composable
fun MField(
    value: String, onValue: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, ltr: Boolean = false, password: Boolean = false,
    type: KeyboardType = KeyboardType.Text, autoFocus: Boolean = false, leading: ImageVector? = null,
    onDone: () -> Unit = {},
) {
    val fr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) { if (autoFocus) { try { fr.requestFocus() } catch (_: Exception) {}; kb?.show() } }
    Row(
        modifier.fillMaxWidth().background(C.Card, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) { Icon(leading, null, tint = C.Muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)) }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = C.Dim, fontSize = 16.sp, maxLines = 1)
            CompositionLocalProvider(LocalLayoutDirection provides if (ltr) LayoutDirection.Ltr else LayoutDirection.Rtl) {
                BasicTextField(
                    value = value, onValueChange = onValue, singleLine = true,
                    textStyle = TextStyle(fontFamily = Plex, fontSize = 16.sp, color = C.Text),
                    cursorBrush = SolidColor(C.Accent),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else type, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { kb?.hide(); onDone() }, onSearch = { kb?.hide(); onDone() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(fr),
                )
            }
        }
        if (value.isNotEmpty() && !password) {
            Spacer(Modifier.width(8.dp))
            Icon(com.wm.iptv.ui.Ico.Close, "مسح", tint = C.Muted, modifier = Modifier.size(18.dp).clip(CircleShape).tap { onValue("") })
        }
    }
}

/** قائمة اختيار تطلع من تحت (للتصنيفات الكثيرة) */
@Composable
fun MSheet(title: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).plainClick(onDismiss)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 560.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(C.Panel)
                .plainClick {}.navigationBarsPadding()
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(width = 40.dp, height = 4.dp).background(C.Dim, CircleShape))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(options, key = { it.first }) { (k, label) ->
                    val sel = k == selected
                    Row(
                        Modifier.fillMaxWidth().tap(onClick = { onPick(k) }).background(if (sel) Color(0x1FFFFFFF) else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 16.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        if (sel) Icon(com.wm.iptv.ui.Ico.Check, null, tint = C.Accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MEmpty(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 80.dp, horizontal = 30.dp), contentAlignment = Alignment.Center) {
        Text(text, color = C.Text3, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun MInfoChip(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = C.Text2,
        modifier = Modifier.border(1.dp, C.Muted, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp))
}

/** ضغطة بدون أي تأثير */
@Composable
fun Modifier.plainClick(onClick: () -> Unit): Modifier =
    this.combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
