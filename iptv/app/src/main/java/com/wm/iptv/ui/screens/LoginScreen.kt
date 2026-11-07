package com.wm.iptv.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.wm.iptv.data.Repo
import com.wm.iptv.ui.C
import com.wm.iptv.ui.FocusBox
import com.wm.iptv.ui.Pill
import com.wm.iptv.ui.Plex
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    var name by rememberSaveable { mutableStateOf("أروما") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var ua by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firstFr = remember { FocusRequester() }

    LaunchedEffect(Unit) { try { firstFr.requestFocus() } catch (_: Exception) {} }

    fun submit() {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                Repo.login(name, server, user, pass, ua)
                onDone()
            } catch (e: Exception) {
                error = e.message ?: "تعذر الدخول"
            } finally { busy = false }
        }
    }

    Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.TopCenter) {
        // الصفحة تنزل مع التركيز لو ما كفت الشاشة
        Column(Modifier.width(620.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Waleed TV", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
            Text("أدخل بيانات اشتراك Xtream (اللي عطاك إياها المزود)", fontSize = 16.sp, color = C.Text3)
            Spacer(Modifier.height(6.dp))
            Input("اسم الحساب", name, { name = it }, Modifier.focusRequester(firstFr))
            Input("رابط السيرفر", server, { server = it }, hint = "مثال: http://example.com:8080", ltr = true, type = KeyboardType.Uri)
            Input("اسم المستخدم", user, { user = it }, ltr = true)
            Input("كلمة المرور", pass, { pass = it }, ltr = true, password = true)
            Input("User-Agent (اختياري)", ua, { ua = it }, hint = "اتركه فاضي إلا إذا طلبه المزود", ltr = true)
            error?.let { Text(it, color = Color(0xFFFF6961), fontSize = 15.sp) }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(if (busy) "جارٍ التحقق…" else "دخول", big = true, onClick = { submit() })
                if (onCancel != null) Pill("إلغاء", big = true, onClick = onCancel)
                if (busy) CircularProgressIndicator(color = C.Text3, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            Text("البيانات تنحفظ في الجهاز فقط.", fontSize = 12.sp, color = C.Dim)
        }
    }
}

/** خانة إدخال للريموت: تتحدد بالأسهم، والكيبورد يطلع بضغطة OK */
@Composable
private fun Input(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    ltr: Boolean = false,
    password: Boolean = false,
    type: KeyboardType = KeyboardType.Text,
) {
    var editing by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val boxFr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    var back by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, color = C.Muted)
        if (!editing) {
            LaunchedEffect(back) { if (back) { back = false; try { boxFr.requestFocus() } catch (_: Exception) {} } }
            var focused by remember { mutableStateOf(false) }
            FocusBox(modifier.focusRequester(boxFr).fillMaxWidth(), onClick = { editing = true }, shape = RoundedCornerShape(12.dp), focusScale = 1.02f,
                onFocus = { focused = it }) {
                Box(Modifier.fillMaxWidth().background(if (focused) C.Text else C.Card).padding(horizontal = 18.dp, vertical = 14.dp)) {
                    val shown = if (password && value.isNotEmpty()) "•".repeat(value.length) else value
                    Text(shown.ifEmpty { hint.ifEmpty { "اضغط OK للكتابة" } }, fontSize = 17.sp,
                        color = if (focused) C.Bg else if (value.isEmpty()) C.Dim else C.Text)
                }
            }
        } else {
            LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {}; kb?.show() }
            val dir = if (ltr) LayoutDirection.Ltr else LayoutDirection.Rtl
            CompositionLocalProvider(LocalLayoutDirection provides dir) {
                BasicTextField(
                    value = value, onValueChange = onValue, singleLine = true,
                    textStyle = TextStyle(fontFamily = Plex, fontSize = 17.sp, color = C.Bg),
                    cursorBrush = SolidColor(C.Bg),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else type, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { kb?.hide(); editing = false; back = true }),
                    modifier = Modifier.fillMaxWidth().focusRequester(fr)
                        .background(C.Text, RoundedCornerShape(12.dp)).border(2.dp, C.Accent, RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .onPreviewKeyEvent { e ->
                            val c = e.nativeKeyEvent.keyCode
                            if (c == KeyEvent.KEYCODE_BACK || c == KeyEvent.KEYCODE_DPAD_DOWN || c == KeyEvent.KEYCODE_DPAD_UP) {
                                if (e.type == KeyEventType.KeyUp) { kb?.hide(); editing = false; back = true }
                                true
                            } else false
                        },
                )
            }
        }
    }
}
