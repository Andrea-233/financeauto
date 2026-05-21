package com.financeauto.nlp

data class InsightResult(
    val title: String,
    val pattern: String,
    val actionSuggestion: String
)

class InsightExtractor {

    fun extract(text: String, expenseData: ExpenseSummary? = null): InsightResult? {
        val hasTrigger = text.anyContains("我发现", "注意到", "太多", "总是", "一直", "为什么", "怎么", "分析")
        if (!hasTrigger) {
            // Check if data itself reveals patterns
            return dataDrivenInsight(expenseData)
        }

        val title = buildInsightTitle(text, expenseData)
        val actionSuggestion = buildSuggestion(text, expenseData)

        return InsightResult(
            title = title,
            pattern = text.trim().take(80),
            actionSuggestion = actionSuggestion
        )
    }

    private fun dataDrivenInsight(expense: ExpenseSummary?): InsightResult? {
        if (expense == null || expense.totalExpense <= 0) return null
        val topCategory = expense.topCategory ?: return null
        val pct = expense.topCategoryPct
        if (pct > 50) {
            return InsightResult(
                title = "你的${topCategory}支出占比${pct}%",
                pattern = "${topCategory}是最大的支出类别",
                actionSuggestion = "建议控制${topCategory}方面的开销，可以尝试制定每周预算"
            )
        }
        if (expense.otherPct > 60) {
            return InsightResult(
                title = "你的其他支出占比${expense.otherPct}%",
                pattern = "大量消费未细化分类",
                actionSuggestion = "建议细化记账类别，更清楚地了解消费去向"
            )
        }
        return null
    }

    private fun buildInsightTitle(text: String, expense: ExpenseSummary?): String {
        if (text.contains("我发现") && text.contains("多")) return "支出可能有点高"
        if (text.contains("总是")) return "消费习惯提醒"
        if (text.contains("注意到")) return "值得关注的消费"
        return "消费洞察"
    }

    private fun buildSuggestion(text: String, expense: ExpenseSummary?): String {
        if (text.contains("咖啡") || text.contains("奶茶")) return "建议设定每周咖啡/奶茶预算，尝试自己泡"
        if (text.contains("外卖")) return "减少外卖频率，尝试自己做饭可以省下不少"
        if (text.contains("购物")) return "购物前先加入购物车冷静24小时，避免冲动消费"
        return "关注消费类别占比，针对性制定省钱计划"
    }
}

data class ExpenseSummary(
    val totalExpense: Double,
    val topCategory: String?,
    val topCategoryPct: Int,
    val otherPct: Int
)

private fun String.anyContains(vararg keywords: String): Boolean =
    keywords.any { contains(it, ignoreCase = true) }
