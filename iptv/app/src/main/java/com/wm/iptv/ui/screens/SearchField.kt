package com.wm.iptv.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Pill
import com.wm.iptv.ui.Plex

/**
 * خانة بحث مناسبة للريموت: تظهر كزر، وما يطلع الكيبورد إلا لما تضغط OK
 * (عشان الكيبورد ما ينفتح كل ما مريت عليها بالأسهم).
 */
@Composable
fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    width: Dp = 320.dp,
    startEditing: Boolean = false,
    onDone: () -> Unit = {},
) {
    var editing by remember { mutableStateOf(startEditing) }
    var returnFocus by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val pillFr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current

    if (!editing) {
        LaunchedEffect(returnFocus) {
            if (returnFocus) { returnFocus = false; try { pillFr.requestFocus() } catch (_: Exception) {} ; onDone() }
        }
        Pill(if (query.isEmpty()) placeholder else "بحث: $query", icon = Ico.Search, modifier = modifier.focusRequester(pillFr), selected = query.isNotEmpty(),
            onClick = { editing = true })
    } else {
        LaunchedEffect(Unit) {
            try { fr.requestFocus() } catch (_: Exception) {}
            kb?.show()
        }
        Row(
            modifier.width(width).background(C.Text, RoundedCornerShape(999.dp)).border(2.dp, C.Accent, RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ico.Search, null, tint = C.Bg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(placeholder, color = C.Dim, fontSize = 15.sp)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Plex, fontSize = 15.sp, color = C.Bg),
                    cursorBrush = SolidColor(C.Bg),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { kb?.hide(); editing = false; returnFocus = true }, onDone = { kb?.hide(); editing = false; returnFocus = true }),
                    modifier = Modifier.focusRequester(fr).onPreviewKeyEvent { e ->
                        val c = e.nativeKeyEvent.keyCode
                        if (e.type == KeyEventType.KeyUp && (c == KeyEvent.KEYCODE_BACK || c == KeyEvent.KEYCODE_DPAD_DOWN)) {
                            kb?.hide(); editing = false; returnFocus = true; true
                        } else c == KeyEvent.KEYCODE_BACK
                    },
                )
            }
        }
    }
}
