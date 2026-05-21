package com.financeauto.util

object ChineseNumberParser {
    private val digits = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        '两' to 2, '十' to 10
    )
    private val units = mapOf(
        '十' to 10, '百' to 100, '千' to 1000, '万' to 10000, '亿' to 100000000
    )

    fun parse(text: String): Double? {
        val trimmed = text.trim()
        trimmed.toDoubleOrNull()?.let { return it }

        val pureChinese = Regex("[零一二三四五六七八九十百千万亿两]+")
        val match = pureChinese.find(trimmed)
        if (match == null) return null

        var result = 0.0
        var section = 0.0
        val s = match.value

        for (i in s.indices) {
            val c = s[i]
            when {
                c in digits && c != '十' -> {
                    section = digits[c]!!.toDouble()
                }
                c == '十' -> {
                    section = if (section == 0.0) 10.0 else section * 10.0
                }
                c in units -> {
                    val unit = units[c]!!.toDouble()
                    if (unit >= 10000) {
                        result += (if (section == 0.0) 1.0 else section) * unit
                        section = 0.0
                    } else {
                        section = (if (section == 0.0) 1.0 else section) * unit
                    }
                }
            }
        }
        result += section
        return if (result > 0) result else null
    }
}
