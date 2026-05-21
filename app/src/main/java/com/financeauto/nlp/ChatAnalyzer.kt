package com.financeauto.nlp

import com.financeauto.data.model.Transaction

data class AnalysisResult(
    val expenses: List<ExpenseResult> = emptyList(),
    val decisions: List<DecisionResult> = emptyList(),
    val insights: List<InsightResult> = emptyList(),
    val checkins: List<CheckinResult> = emptyList(),
    val ambiguousAmount: Boolean = false
) {
    val hasCards: Boolean get() = decisions.isNotEmpty() || insights.isNotEmpty() || checkins.isNotEmpty()
    val cardCount: Int get() = decisions.size + insights.size + checkins.size
}

class ChatAnalyzer(
    private val expenseExtractor: ExpenseExtractor = ExpenseExtractor(),
    private val decisionExtractor: DecisionExtractor = DecisionExtractor(),
    private val insightExtractor: InsightExtractor = InsightExtractor(),
    private val checkinExtractor: CheckinExtractor = CheckinExtractor()
) {
    fun analyze(text: String, transactions: List<Transaction> = emptyList()): AnalysisResult {
        val expenses = mutableListOf<ExpenseResult>()
        var ambiguousAmount = false

        // Run expense extraction
        if (isExpenseRelated(text)) {
            val result = expenseExtractor.extract(text)
            if (result != null) {
                expenses.add(result)
            } else if (hasAmbiguousAmount(text)) {
                ambiguousAmount = true
            }
        }

        // Run decision extraction
        val decisions = mutableListOf<DecisionResult>()
        decisionExtractor.extract(text)?.let { decisions.add(it) }

        // Run insight extraction
        val expenseSummary = buildExpenseSummary(transactions)
        val insights = mutableListOf<InsightResult>()
        insightExtractor.extract(text, expenseSummary)?.let { insights.add(it) }

        // Run checkin extraction
        val checkins = mutableListOf<CheckinResult>()
        checkinExtractor.extract(text)?.let { checkins.add(it) }

        return AnalysisResult(
            expenses = expenses,
            decisions = decisions,
            insights = insights,
            checkins = checkins,
            ambiguousAmount = ambiguousAmount
        )
    }

    private fun isExpenseRelated(text: String): Boolean {
        return text.hasAny("花了", "买了", "付了", "消费", "支付", "用了", "¥", "￥", "元") ||
                Regex("""\d+\.?\d*\s*[元块]?""").containsMatchIn(text)
    }

    private fun hasAmbiguousAmount(text: String): Boolean {
        return text.hasAny("大概", "左右", "差不多", "可能有", "应该是", "好像是", "也许")
    }

    private fun buildExpenseSummary(transactions: List<Transaction>): ExpenseSummary? {
        val expenses = transactions.filter { it.type == "expense" }
        if (expenses.isEmpty()) return null
        val total = expenses.sumOf { it.amount }
        val topCategory = expenses.groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .maxByOrNull { it.value }
        val otherAmt = expenses.filter { it.category == "其他" }.sumOf { it.amount }
        return ExpenseSummary(
            totalExpense = total,
            topCategory = topCategory?.key,
            topCategoryPct = if (total > 0) ((topCategory?.value ?: 0.0) / total * 100).toInt() else 0,
            otherPct = if (total > 0) (otherAmt / total * 100).toInt() else 0
        )
    }
}

private fun String.hasAny(vararg keywords: String): Boolean =
    keywords.any { contains(it, ignoreCase = true) }
