package com.wm.iptv.mobile

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import com.wm.iptv.player.PlayerActivity
import com.wm.iptv.ui.AppTheme

/** مشغل الجوال: نفس المحرك (إعادة الاتصال، الحفظ، الحلقة التالية) مع واجهة لمس */
@OptIn(UnstableApi::class)
class MobilePlayerActivity : PlayerActivity() {
    var inPip by mutableStateOf(false)
        private set

    @Composable
    override fun Content() {
        AppTheme(scaleToTv = false) { MobilePlayerScreen(vm, player, this) }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (_: Exception) {}
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // لو طلعت من التطبيق والفيديو شغال: يكمل في نافذة صغيرة
        if (vm.playing && vm.panel == null) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) { vm.overlay = false; vm.panel = null }
    }

    override fun onStop() {
        super.onStop()
        // سكّر النافذة الصغيرة = نوقف
        if (inPip) finish()
    }

    /** على الجوال: الرجوع يطلع على طول (بدل ما يخفي الأزرار) */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if ((event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) && vm.panel == null) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (vm.seekTarget != null) cancelSeekNow() else finish()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
