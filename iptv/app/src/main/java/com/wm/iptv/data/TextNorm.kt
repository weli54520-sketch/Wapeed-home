package com.wm.iptv.data

import java.text.Normalizer

/** تطبيع النص للبحث: يتجاهل الهمزات والتشكيل والتاء المربوطة وحالة الأحرف */
object TextNorm {
    private val marks = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    private val latinMarks = Regex("\\p{M}+")
    private val spaces = Regex("\\s+")

    fun key(s: String): String {
        if (s.isEmpty()) return ""
        var t = marks.replace(s, "")
        val sb = StringBuilder(t.length)
        for (ch in t) {
            sb.append(
                when (ch) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ة' -> 'ه'
                    'ى' -> 'ي'
                    'ؤ' -> 'و'
                    'ئ' -> 'ي'
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> ch
                }
            )
        }
        t = Normalizer.normalize(sb.toString(), Normalizer.Form.NFD)
        t = latinMarks.replace(t, "")
        return spaces.replace(t.lowercase(), " ").trim()
    }

    /** يطابق إذا كل كلمة في البحث موجودة في الاسم */
    fun matches(nameKey: String, queryKey: String): Boolean {
        if (queryKey.isEmpty()) return true
        if (nameKey.contains(queryKey)) return true
        val parts = queryKey.split(' ')
        return parts.all { it.isEmpty() || nameKey.contains(it) }
    }
}
