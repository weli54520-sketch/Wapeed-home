package com.wm.iptv.update

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** بدء التحديث: يطلب الإذن لو ناقص، وإلا ينزّل ويثبّت */
fun startUpdate(ctx: Context, scope: CoroutineScope, a: UpdateState.Available) {
    if (!Updater.canInstall(ctx)) {
        val opened = Updater.openInstallPermission(ctx)
        Toast.makeText(ctx,
            if (opened) "فعّل \"السماح من هذا المصدر\" لـ Waleed TV، وارجع اضغط تحديث"
            else "فعّل تثبيت التطبيقات غير المعروفة لـ Waleed TV من إعدادات الجهاز (الأمان)",
            Toast.LENGTH_LONG).show()
        return
    }
    scope.launch { Updater.downloadAndInstall(ctx, a) }
}

/** فحص يدوي من الإعدادات */
fun manualCheck(ctx: Context, scope: CoroutineScope) {
    scope.launch {
        Updater.dismissedVersion = 0
        Updater.check(quiet = false)
        if (Updater.state.value is UpdateState.UpToDate) {
            Toast.makeText(ctx, "عندك آخر إصدار (1.${Updater.currentVersion})", Toast.LENGTH_SHORT).show()
            Updater.reset()
        }
    }
}
