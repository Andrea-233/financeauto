package com.financeauto.nlp

data class CheckinResult(
    val habitName: String,
    val frequency: String,   // DAILY / WEEKLY / MONTHLY
    val target: Int?,
    val description: String
)

class CheckinExtractor {

    fun extract(text: String): CheckinResult? {
        val frequency = when {
            text.contains("每天") -> "DAILY"
            text.contains("每周") -> "WEEKLY"
            text.contains("每月") -> "MONTHLY"
            else -> return null
        }

        val habitKeyword = text.anyContains("记账", "省钱", "运动", "学习", "阅读", "读书", "存钱", "减肥", "健身", "早睡")
        if (!habitKeyword) return null

        val habitName = extractHabitName(text)
        val target = extractTarget(text)
        val freqLabel = when (frequency) {
            "DAILY" -> "每天"
            "WEEKLY" -> "每周"
            "MONTHLY" -> "每月"
            else -> ""
        }

        return CheckinResult(
            habitName = habitName,
            frequency = frequency,
            target = target,
            description = "$freqLabel ${habitName}" + if (target != null) " $target 次" else ""
        )
    }

    private fun extractHabitName(text: String): String {
        return when {
            text.contains("记账") -> "记账"
            text.contains("省钱") -> "省钱"
            text.contains("运动") -> "运动打卡"
            text.contains("学习") -> "学习"
            text.contains("阅读") -> "阅读"
            text.contains("读书") -> "读书"
            text.contains("存钱") -> "存钱"
            text.contains("减肥") -> "减肥"
            text.contains("健身") -> "健身"
            text.contains("早睡") -> "早睡"
            else -> "习惯打卡"
        }
    }

    private fun extractTarget(text: String): Int? {
        val patterns = listOf(
            Regex("""(\d+)\s*次"""),
            Regex("""(\d+)\s*天"""),
            Regex("""(\d+)\s*小时"""),
            Regex("""目标\s*(\d+)""")
        )
        for (p in patterns) {
            p.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
                if (it in 1..100) return it
            }
        }
        return null
    }
}

private fun String.anyContains(vararg keywords: String): Boolean =
    keywords.any { contains(it, ignoreCase = true) }
