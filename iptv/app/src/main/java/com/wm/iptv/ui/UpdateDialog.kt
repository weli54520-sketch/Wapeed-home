package com.wm.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wm.iptv.update.UpdateState
import com.wm.iptv.update.Updater
import com.wm.iptv.update.startUpdate
import kotlinx.coroutines.delay

/** نافذة التحديث (نسخة التلفزيون) */
@Composable
fun TvUpdateDialog() {
    val state by Updater.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = state
    val show = when (s) {
        is UpdateState.Available -> s.version != Updater.dismissedVersion
        is UpdateState.Downloading, UpdateState.Installing, is UpdateState.Error -> true
        else -> false
    }
    if (!show) return
    val fr = remember { FocusRequester() }
    LaunchedEffect(s::class) { delay(80); try { fr.requestFocus() } catch (_: Exception) {} }

    Dialog(onDismissRequest = {
        when (s) {
            is UpdateState.Available -> Updater.dismissedVersion = s.version
            is UpdateState.Error -> Updater.reset()
            else -> {}
        }
        if (s is UpdateState.Available) Updater.reset()
    }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AppTheme {
            Column(
                Modifier.width(560.dp).background(C.Panel, RoundedCornerShape(22.dp)).padding(30.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (s) {
                    is UpdateState.Available -> {
                        Text("تحديث جديد متوفر", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("الإصدار 1.${s.version} — أنت على 1.${Updater.currentVersion}", fontSize = 15.sp, color = C.Text3)
                        Text("التحديث ينزل فوق النسخة الحالية، ومفضلتك وأماكن التوقف تبقى مثل ما هي.", fontSize = 14.sp, color = C.Muted)
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Pill("تحديث الآن", icon = Ico.Refresh, big = true, modifier = Modifier.focusRequester(fr), onClick = { startUpdate(ctx, scope, s) })
                            Pill("لاحقاً", big = true, onClick = { Updater.dismissedVersion = s.version; Updater.reset() })
                        }
                    }
                    is UpdateState.Downloading -> {
                        Text("جارٍ تنزيل التحديث…", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        ProgressLine(s.percent / 100f, height = 6.dp)
                        Text("${s.percent}٪", fontSize = 15.sp, color = C.Text3)
                    }
                    UpdateState.Installing -> {
                        Text("جارٍ التثبيت…", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("إذا طلعت لك نافذة تأكيد، اختر \"تحديث\" أو \"تثبيت\".", fontSize = 15.sp, color = C.Text3)
                    }
                    is UpdateState.Error -> {
                        Text("ما تم التحديث", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(s.message, fontSize = 15.sp, color = C.Text3)
                        Pill("حسناً", big = true, modifier = Modifier.focusRequester(fr), onClick = { Updater.reset() })
                    }
                    else -> {}
                }
            }
        }
    }
}
