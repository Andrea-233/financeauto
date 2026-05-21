package com.financeauto.nlp

data class DecisionResult(
    val title: String,
    val decision: String,
    val targetAmount: Double?,
    val targetDate: String?
)

class DecisionExtractor {

    fun extract(text: String): DecisionResult? {
        val hasCommitment = text.anyContains("我决定", "我计划", "要存钱", "我要省", "目标", "省钱计划", "预算")
        if (!hasCommitment) return null

        val amount = extractTargetAmount(text)
        val date = extractTargetDate(text)
        val title = buildTitle(text, amount, date)
        val decision = text.trim().take(80)

        return DecisionResult(
            title = title,
            decision = decision,
            targetAmount = amount,
            targetDate = date
        )
    }

    private fun extractTargetAmount(text: String): Double? {
        val patterns = listOf(
            Regex("""这个月\s*[要省]?\s*(\d+\.?\d*)"""),
            Regex("""今天\s*[要省]?\s*(\d+\.?\d*)"""),
            Regex("""预算\s*[是]?\s*(\d+\.?\d*)"""),
            Regex("""目标\s*[是]?\s*(\d+\.?\d*)"""),
            Regex("""[要省]\s*(\d+\.?\d*)\s*(块钱|元|块)"""),
            Regex("""(\d+\.?\d*)\s*块?\s*(块钱|元|块|预算|目标)""")
        )
        for (p in patterns) {
            p.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                if (it > 0) return it
            }
        }
        return null
    }

    private fun extractTargetDate(text: String): String? {
        if (text.contains("这个月")) return "这个月"
        if (text.contains("今天")) return "今天"
        if (text.contains("本周")) return "本周"
        if (text.contains("下个月")) return "下个月"
        if (text.contains("下周")) return "下周"
        return null
    }

    private fun buildTitle(text: String, amount: Double?, date: String?): String {
        val sb = StringBuilder()
        if (date != null) sb.append("${date} ")
        when {
            text.contains("咖啡") -> sb.append("咖啡消费目标")
            text.contains("外卖") -> sb.append("外卖消费目标")
            text.contains("奶茶") -> sb.append("奶茶消费目标")
            text.contains("购物") -> sb.append("购物消费目标")
            text.contains("省钱") -> sb.append("省钱目标")
            text.contains("预算") -> sb.append("消费预算")
            else -> sb.append("消费目标")
        }
        if (amount != null) sb.append(" ¥$amount")
        if (date != null && amount != null) {
            if (date == "这个月") sb.append("/月")
            if (date == "今天") sb.append("/日")
            if (date == "本周") sb.append("/周")
        }
        return sb.toString()
    }
}

private fun String.anyContains(vararg keywords: String): Boolean =
    keywords.any { contains(it, ignoreCase = true) }
