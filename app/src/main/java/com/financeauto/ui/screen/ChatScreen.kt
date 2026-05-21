package com.financeauto.ui.screen

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeauto.data.local.AppDatabase
import com.financeauto.data.local.entity.CardEntity
import com.financeauto.data.model.Transaction
import com.financeauto.data.repository.CardRepository
import com.financeauto.nlp.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ---- Data Models ----

data class ChatMessage(val role: String, val content: String)

data class ParsedCard(
    val type: String,
    val title: String,
    val description: String = "",
    val decision: String = "",
    val targetAmount: Double = 0.0,
    val targetDate: String = "",
    val trackCategory: String = "",
    val pattern: String = "",
    val actionSuggestion: String = "",
    val habitName: String = "",
    val frequency: String = "DAILY",
    val target: Int = 0
)

data class JsonTxCommand(
    val action: String,
    val id: Long = 0L,
    val amount: Double = 0.0,
    val type: String = "expense",
    val category: String = "",
    val note: String = "",
    val time: Long = System.currentTimeMillis()
)

sealed class ChatItem {
    data class Message(val role: String, val content: String) : ChatItem()
    data class CardSuggestion(
        val id: String = java.util.UUID.randomUUID().toString(),
        val cards: List<ParsedCard>,
        var confirmed: Boolean = false,
        var ignored: Boolean = false
    ) : ChatItem()
    data class ExpenseSuggestion(
        val id: String = java.util.UUID.randomUUID().toString(),
        val expense: ExpenseResult,
        var confirmed: Boolean = false,
        var ignored: Boolean = false
    ) : ChatItem()
    data class CrudNote(val text: String, val ok: Boolean) : ChatItem()
}

// ---- Main ----

@Composable
fun ChatPage(
    records: List<Transaction>,
    hasBalanceAnchor: Boolean,
    balanceAnchorDate: Long,
    balanceAnchorAmount: Double
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE) }
    var showSettings by remember { mutableStateOf(false) }

    val apiUrl = remember { mutableStateOf(prefs.getString("chat_api_url", "https://api.deepseek.com/v1/chat/completions") ?: "https://api.deepseek.com/v1/chat/completions") }
    val apiKey = remember { mutableStateOf(prefs.getString("chat_api_key", "") ?: "") }
    val model = remember { mutableStateOf(prefs.getString("chat_model", "deepseek-chat") ?: "deepseek-chat") }

    val savedMessages = remember { loadChatMessages(prefs) }
    val sysPrompt = remember(records, hasBalanceAnchor, balanceAnchorAmount) {
        buildSysPrompt(records, hasBalanceAnchor, balanceAnchorDate, balanceAnchorAmount)
    }
    var messages by remember { mutableStateOf(listOf(ChatMessage("system", sysPrompt)) + savedMessages) }

    LaunchedEffect(sysPrompt) {
        messages = listOf(ChatMessage("system", sysPrompt)) + messages.filter { it.role != "system" }
    }

    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Local NLP — primary mechanism for expense/card extraction
    val nlpAnalyzer = remember { ChatAnalyzer() }
    val db = remember { AppDatabase.getDatabase(context) }
    val cardRepo = remember { CardRepository(db.cardDao()) }
    var chatItems by remember { mutableStateOf(listOf<ChatItem>()) }


    // Initialize chatItems from saved messages on first load only
    LaunchedEffect(Unit) { chatItems = buildChatItemsFromMessages(messages) }

    if (showSettings) {
        SettingsDialog(apiUrl.value, apiKey.value, model.value,
            onSave = { url, key, m ->
                prefs.edit().putString("chat_api_url", url).putString("chat_api_key", key).putString("chat_model", m).apply()
                apiUrl.value = url; apiKey.value = key; model.value = m; showSettings = false
            },
            onDismiss = { showSettings = false })
    }

    LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty()) listState.animateScrollToItem(chatItems.size - 1)
    }

    Column(Modifier.fillMaxSize().background(AppBg).imePadding().navigationBarsPadding()) {
        // Top bar
        Surface(Modifier.fillMaxWidth(), color = CardBg, shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("AI 对话", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        messages = listOf(ChatMessage("system", sysPrompt))
                        saveChatMessages(prefs, messages); chatItems = emptyList(); error = null
                    }) { Text("清空", color = Pink) }
                    TextButton(onClick = { showSettings = true }) { Text("设置", color = Blue) }
                }
            }
        }

        // Chat area
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFFFF8E7))) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (chatItems.isEmpty() && !loading) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("开始和 AI 聊天吧\n记账、查账、分析消费都可问我", color = Muted)
                        }
                    }
                }
                itemsIndexed(chatItems, key = { i, item -> "$i-${item.hashCode()}" }) { _, item ->
                    when (item) {
                        is ChatItem.Message -> {
                            if (item.role == "user") UserBubble(item.content) else AiBubble(item.content)
                        }
                        is ChatItem.CardSuggestion -> {
                            if (item.confirmed) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF34C759).copy(alpha = 0.12f)) {
                                        Text("已生成 ${item.cards.size} 张卡片 ✓", Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium, color = Color(0xFF34C759))
                                    }
                                }
                            } else if (!item.ignored) {
                                // Pink AI-style bubble — same rendering path as AiBubble
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                    Surface(
                                        modifier = Modifier.widthIn(max = 300.dp),
                                        shape = RoundedCornerShape(12.dp, 12.dp, 12.dp, 4.dp),
                                        color = Color(0xFFFFE0EC),  // light pink
                                        shadowElevation = 0.dp
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("检测到 ${item.cards.size} 张卡片，需要帮你添加吗？",
                                                style = MaterialTheme.typography.bodyMedium, color = Ink)
                                            Spacer(Modifier.height(6.dp))
                                            item.cards.forEach { c ->
                                                val tn = when(c.type){ "DECISION"->"决策"; "BUDGET"->"预算"; "INSIGHT"->"洞察"; "CHECKIN"->"打卡"; "SAVINGS"->"存钱"; else->c.type }
                                                Text("• [$tn] ${c.title}", style = MaterialTheme.typography.bodySmall, color = Ink)
                                                if (c.decision.isNotBlank()) Text("  ${c.decision.take(50)}", style = MaterialTheme.typography.labelSmall, color = Muted)
                                                if (c.targetAmount > 0) Text("  目标: ¥${f(c.targetAmount)}", style = MaterialTheme.typography.labelSmall, color = Muted)
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                TextButton(onClick = { item.ignored = true; chatItems = chatItems.toList() }) { Text("忽略", color = Muted, style = MaterialTheme.typography.labelSmall) }
                                                Button(onClick = {
                                                    scope.launch {
                                                        for (c in item.cards) createCardEntity(c, getLastUserMsg(messages))?.let { cardRepo.insert(it) }
                                                    }
                                                    item.confirmed = true
                                                    chatItems = chatItems.toList()
                                                }, colors = ButtonDefaults.buttonColors(Blue, Color.White),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                                    Text("生成", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is ChatItem.ExpenseSuggestion -> ExpenseSuggestionView(item, onConfirm = {
                            scope.launch {
                                db.transactionDao().insert(com.financeauto.data.local.entity.TransactionEntity(
                                    amount = item.expense.amount, type = "expense", category = item.expense.category,
                                    note = item.expense.note, fingerprint = "", time = System.currentTimeMillis()))
                            }
                            item.confirmed = true
                            chatItems = chatItems.toList()
                        }, onIgnore = { item.ignored = true; chatItems = chatItems.toList() })
                        is ChatItem.CrudNote -> CrudNoteView(item)
                    }
                }
                if (error != null) {
                    item { Text("错误: ${error}", color = Pink, style = MaterialTheme.typography.bodySmall) }
                }
            }

            // Loading overlay
            if (loading) {
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start) {
                    Surface(shape = RoundedCornerShape(12.dp, 12.dp, 12.dp, 4.dp), color = CardBg, shadowElevation = 2.dp) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp, color = Blue)
                            Spacer(Modifier.width(8.dp))
                            Text("思考中...", color = Muted)
                        }
                    }
                }
            }
        }

        // Input
        Surface(Modifier.fillMaxWidth().padding(bottom = 68.dp), color = CardBg, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("询问财务问题或记账...") }, singleLine = true, enabled = !loading)
                Button(onClick = {
                    val query = input.trim()
                    if (query.isEmpty() || loading) return@Button
                    val userMsg = ChatMessage("user", query)
                    messages = messages + userMsg
                    saveChatMessages(prefs, messages)
                    chatItems = chatItems + ChatItem.Message("user", query)
                    input = ""; error = null; loading = true

                    // AI-first: let AI parse the message, fall back to local NLP
                    scope.launch {
                        var aiHandled = false
                        try {
                            val reply = callChatApi(apiUrl.value, apiKey.value, model.value, messages)
                            val parsed = parseAiResponse(reply)
                            if (parsed.textContent.isNotBlank()) {
                                messages = messages + ChatMessage("assistant", parsed.textContent)
                                chatItems = chatItems + ChatItem.Message("assistant", parsed.textContent)
                            }
                            // AI cards
                            if (parsed.cards.isNotEmpty()) {
                                chatItems = chatItems + ChatItem.CardSuggestion(cards = parsed.cards)
                            }
                            // AI CRUD - auto execute
                            for (cmd in parsed.txCommands) {
                                val result = executeCrudCommand(cmd, db)
                                if (result != null) {
                                    chatItems = chatItems + ChatItem.CrudNote(result, ok = true)
                                    aiHandled = true
                                }
                            }
                            // AI text → cards
                            val textCards = extractCardsFromAiText(parsed.textContent)
                            if (textCards.isNotEmpty()) {
                                chatItems = chatItems + ChatItem.CardSuggestion(cards = textCards)
                                aiHandled = true
                            }
                            if (parsed.aiClaimedOperation && !aiHandled) {
                                chatItems = chatItems + ChatItem.CrudNote("⚠️ AI 未提供具体指令，尝试本地识别...", ok = false)
                            }
                            saveChatMessages(prefs, messages)
                        } catch (e: Exception) {
                            error = e.message ?: "未知错误"
                        }

                        // Fallback: local NLP if AI didn't handle it
                        if (!aiHandled) {
                            val expenseExt = ExpenseExtractor()
                            expenseExt.extract(query)?.let { exp ->
                                chatItems = chatItems + ChatItem.ExpenseSuggestion(expense = exp)
                            }
                            val immCard = detectCardIntent(query)
                            if (immCard != null) {
                                chatItems = chatItems + ChatItem.CardSuggestion(cards = listOf(immCard))
                            }
                            try {
                                val allTxs = db.transactionDao().getAllOnce().map {
                                    Transaction(it.id, it.amount, it.type, it.category, it.note, it.fingerprint, it.time)
                                }
                                val nlpResult = nlpAnalyzer.analyze(query, allTxs)
                                if (immCard == null) {
                                    nlpResult.expenses.firstOrNull()?.let { exp ->
                                        chatItems = chatItems + ChatItem.ExpenseSuggestion(expense = exp)
                                    }
                                }
                                if (nlpResult.ambiguousAmount) {
                                    chatItems = chatItems + ChatItem.CrudNote("具体金额是多少？我需要准确数字才能记账。", ok = false)
                                }
                            } catch (_: Exception) {}
                        }

                        loading = false
                    }
                }, enabled = !loading && input.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(Blue, Color.White)) { Text("发送") }
            }
        }
    }
}


@Composable
private fun CardSuggestionView(item: ChatItem.CardSuggestion, records: List<Transaction>,
                               onConfirm: () -> Unit, onIgnore: () -> Unit) {
    if (item.confirmed) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF34C759).copy(alpha = 0.12f)) {
                Text("已生成 ${item.cards.size} 张卡片 ✓", Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = Color(0xFF34C759))
            }
        }
        return
    }
    if (item.ignored) return

    Surface(Modifier.fillMaxWidth().padding(start = 32.dp), shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF0F4FF), shadowElevation = 1.dp) {
        Column(Modifier.padding(14.dp)) {
            Text("检测到 ${item.cards.size} 张卡片，需要帮你添加吗？",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(10.dp))
            item.cards.forEachIndexed { i, card ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFE0E4F0))
                CardPreview(card, records)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onIgnore) { Text("忽略", color = Muted) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(Blue, Color.White),
                    shape = RoundedCornerShape(10.dp)) { Text("一键生成") }
            }
        }
    }
}

@Composable
private fun CardPreview(card: ParsedCard, records: List<Transaction>) {
    val typeColor = when (card.type) {
        "DECISION", "BUDGET", "SAVINGS" -> Color(0xFFFF9500)
        "INSIGHT" -> Color(0xFF5856D6)
        "CHECKIN" -> Color(0xFF34C759)
        else -> Color(0xFF8E8E93)
    }
    val typeLabel = when (card.type) {
        "DECISION" -> "决策"; "BUDGET" -> "预算"; "SAVINGS" -> "存钱"
        "INSIGHT" -> "洞察"; "CHECKIN" -> "打卡"
        else -> card.type
    }
    val freqLabel = when (card.frequency) { "DAILY" -> "每天"; "WEEKLY" -> "每周"; "MONTHLY" -> "每月"; else -> card.frequency }

    val budgetInfo = remember(card, records) {
        if ((card.type == "BUDGET" || card.type == "DECISION" || card.type == "SAVINGS") &&
            card.targetAmount > 0 && card.trackCategory.isNotBlank()) {
            calcBudgetStatus(records, card.targetAmount, card.trackCategory, card.targetDate)
        } else null
    }

    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(8.dp).padding(top = 5.dp).background(typeColor, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = typeColor.copy(alpha = 0.12f)) {
                    Text(typeLabel, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = typeColor, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text(card.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            when {
                budgetInfo != null -> {
                    Spacer(Modifier.height(8.dp))
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color(0xFFF5F5F7)) {
                        Column(Modifier.padding(10.dp)) {
                            BudgetRow("预算", card.targetAmount, Ink)
                            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFFE8E8EC))
                            BudgetRow("已花", budgetInfo.spent, Ink)
                            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFFE8E8EC))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (budgetInfo.remaining >= 0) "剩余" else "超出", style = MaterialTheme.typography.labelSmall, color = Muted)
                                Text("${if (budgetInfo.remaining >= 0) "+" else "-"}¥${f(abs(budgetInfo.remaining))}",
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                                    color = if (budgetInfo.remaining >= 0) Color(0xFF34C759) else Color(0xFFFF3D8B))
                            }
                            Spacer(Modifier.height(6.dp))
                            val p = (budgetInfo.spent / card.targetAmount).coerceIn(0.0, 1.0).toFloat()
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                color = if (p <= 1f) Color(0xFF34C759) else Color(0xFFFF3D8B), trackColor = Color(0xFFE0E0E0))
                            Spacer(Modifier.height(4.dp))
                            Text("${budgetInfo.periodLabel} · ${card.trackCategory} · ${(p * 100).roundToInt()}% 已用",
                                style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                }
                card.type == "DECISION" -> {
                    if (card.decision.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("决定：${card.decision}", style = MaterialTheme.typography.bodySmall, color = Ink) }
                    if (card.targetAmount > 0) { Spacer(Modifier.height(2.dp)); Text("目标金额：¥${f(card.targetAmount)}", style = MaterialTheme.typography.bodySmall, color = Muted) }
                    if (card.targetDate.isNotBlank()) { Text("目标期限：${card.targetDate}", style = MaterialTheme.typography.bodySmall, color = Muted) }
                }
                card.type == "INSIGHT" -> {
                    if (card.pattern.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("模式：${card.pattern}", style = MaterialTheme.typography.bodySmall, color = Ink) }
                    if (card.actionSuggestion.isNotBlank()) { Spacer(Modifier.height(2.dp)); Text("建议：${card.actionSuggestion}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5856D6)) }
                }
                card.type == "CHECKIN" -> {
                    if (card.habitName.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("习惯：${card.habitName}", style = MaterialTheme.typography.bodySmall, color = Ink) }
                    Spacer(Modifier.height(2.dp)); Text("频率：$freqLabel", style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (card.target > 0) Text("目标：${card.target} 次", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                else -> {
                    if (card.decision.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(card.decision, style = MaterialTheme.typography.bodySmall, color = Ink) }
                    if (card.targetAmount > 0) { Spacer(Modifier.height(2.dp)); Text("目标金额：¥${f(card.targetAmount)}", style = MaterialTheme.typography.bodySmall, color = Muted) }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(label: String, amount: Double, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        Text("¥${f(amount)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

// ---- Expense Suggestion View ----

@Composable
private fun ExpenseSuggestionView(item: ChatItem.ExpenseSuggestion, onConfirm: () -> Unit, onIgnore: () -> Unit) {
    if (item.confirmed) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF34C759).copy(alpha = 0.12f)) {
                Text("已记账 ✓", Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = Color(0xFF34C759))
            }
        }
        return
    }
    if (item.ignored) return

    val catColor = when (item.expense.category) {
        "餐饮美食" -> Color(0xFFFF6B6B); "交通出行" -> Color(0xFF4ECDC4); "网购购物" -> Color(0xFFFFD93D)
        "日用百货" -> Color(0xFFA78BFA); "校园生活" -> Color(0xFF64B5F6); "生活服务" -> Color(0xFFFF8A65)
        "充值缴费" -> Color(0xFF9575CD); else -> Color(0xFF9CA3AF)
    }

    Surface(Modifier.fillMaxWidth().padding(start = 32.dp), shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF3E0), shadowElevation = 1.dp) {
        Column(Modifier.padding(14.dp)) {
            Text("检测到消费支出，需要帮你记账吗？", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = catColor.copy(alpha = 0.15f)) {
                    Text(item.expense.category, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = catColor, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(12.dp))
                Text("¥${f(abs(item.expense.amount))}", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = Color(0xFFFF3D8B))
            }
            if (item.expense.merchant.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("商家：${item.expense.merchant}", style = MaterialTheme.typography.bodySmall, color = Ink) }
            if (item.expense.note.isNotBlank()) { Spacer(Modifier.height(2.dp)); Text("备注：${item.expense.note.take(60)}", style = MaterialTheme.typography.bodySmall, color = Muted) }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onIgnore) { Text("忽略", color = Muted) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(Color(0xFF34C759), Color.White),
                    shape = RoundedCornerShape(10.dp)) { Text("记账") }
            }
        }
    }
}

@Composable
private fun CrudNoteView(item: ChatItem.CrudNote) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(10.dp),
            color = if (item.ok) Color(0xFF00C853).copy(alpha = 0.12f) else Color(0xFFFF3D8B).copy(alpha = 0.12f)) {
            Text(item.text, Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (item.ok) Color(0xFF00C853) else Color(0xFFFF3D8B))
        }
    }
}

// ---- Message Bubbles ----

@Composable
private fun UserBubble(content: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(modifier = Modifier.widthIn(max = 300.dp), shape = RoundedCornerShape(12.dp, 12.dp, 4.dp, 12.dp),
            color = Blue, shadowElevation = 2.dp) {
            Text(content, Modifier.padding(12.dp), color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AiBubble(content: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(modifier = Modifier.widthIn(max = 300.dp), shape = RoundedCornerShape(12.dp, 12.dp, 12.dp, 4.dp),
            color = CardBg, shadowElevation = 0.dp) {
            Text(content, Modifier.padding(12.dp), color = Ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---- Settings ----

@Composable
private fun SettingsDialog(currentUrl: String, currentKey: String, currentModel: String,
                           onSave: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(currentUrl) }; var key by remember { mutableStateOf(currentKey) }; var m by remember { mutableStateOf(currentModel) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("API 设置", fontWeight = FontWeight.Bold, color = Ink) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("API 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API 密钥") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(value = m, onValueChange = { m = it }, label = { Text("模型") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("支持兼容 OpenAI 的 API", style = MaterialTheme.typography.bodySmall, color = Muted)
        } },
        confirmButton = { TextButton(onClick = { onSave(url.trim(), key.trim(), m.trim()) }) { Text("保存", color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Muted) } })
}

// ---- System Prompt (shorter, JSON instructions first) ----

private fun buildSysPrompt(records: List<Transaction>, hasBalanceAnchor: Boolean,
                            balanceAnchorDate: Long, balanceAnchorAmount: Double): String {
    val income = records.filter { it.type == "income" }.sumOf { it.amount }
    val expense = records.filter { it.type == "expense" }.sumOf { it.amount }
    val now = Calendar.getInstance(); val cm = "${now.get(Calendar.MONTH) + 1}月"; val cy = now.get(Calendar.YEAR)
    val monthStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
    val monthByCat = records.filter { it.type == "expense" && it.time >= monthStart }.groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amount } }
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    val recent = records.sortedByDescending { it.time }.take(15)

    return buildString {
        appendLine("你是小财，个人财务顾问。当前时间: ${cy}年${cm}。")
        appendLine()
        appendLine("## ⚠️ 操作规则（必须遵守！不用JSON的操作不会生效！）")
        appendLine("- 任何记账/修改/删除/生成卡片 → 回复末尾带 ```json 代码块")
        appendLine("- 用户说\"记一笔\"、\"花了\"、\"买了XX\" → 直接添加交易JSON")
        appendLine("- 添加: {\"transactions\":[{\"action\":\"add\",\"amount\":25.5,\"type\":\"expense\",\"category\":\"餐饮美食\",\"note\":\"瑞幸咖啡\",\"date\":\"2026-05-15\"}]}")
        appendLine("- 修改: {\"transactions\":[{\"action\":\"update\",\"id\":记录ID,\"category\":\"交通出行\",\"amount\":30.0,\"date\":\"2026-05-10\"}]}")
        appendLine("- 删除: {\"transactions\":[{\"action\":\"delete\",\"id\":记录ID}]}")
        appendLine("- 卡片: {\"cards\":[{\"type\":\"BUDGET\",\"title\":\"${cm}餐饮预算\",\"description\":\"...\",\"decision\":\"...\",\"targetAmount\":500,\"trackCategory\":\"餐饮美食\"}]}")
        appendLine("")
        appendLine("日期处理规则（重要！）：")
        appendLine("- 用户指定日期→用date字段(yyyy-MM-dd),如\"5月10号\"→\"2026-05-10\"")
        appendLine("- \"昨天\"→当前日期减1天,\"前天\"→减2天,\"上周三\"→上周的周三")
        appendLine("- 未指定日期→不填date字段(默认为今天)")
        appendLine("- 修改记录时可同时更新date字段来修改日期")
        appendLine("")
        appendLine("类别判定（重要！根据商家名和金额智能判断）：")
        appendLine("- 咖啡/奶茶/餐厅/外卖/快餐/火锅/麻辣烫/烧烤/甜品→餐饮美食")
        appendLine("- 滴滴/高德/曹操/地铁/公交/停车/加油/火车/机票→交通出行")
        appendLine("- 淘宝/京东/拼多多/抖音购物→网购购物")
        appendLine("- 超市/便利店/百货/屈臣氏→日用百货")
        appendLine("- U净/丰巢/快递/洗衣/维修/理发/医疗→生活服务")
        appendLine("- 话费/充值/会员/流量→充值缴费")
        appendLine("- 食堂/校园/教材/学费→校园生活")
        appendLine("- 金额异常大的(如餐饮¥5000)→可能是输入错误，请保留原样让用户确认")
        appendLine("- type: 消费=\"expense\" 收入=\"income\"")
        appendLine("- 有效类别: 餐饮美食/网购购物/日用百货/校园生活/交通出行/生活服务/充值缴费/其他")
        appendLine()
        appendLine("## 财务摘要")
        appendLine("${cy}年${cm} | 总收支: ¥${f(income)}入 ¥${f(expense)}出 | 净: ¥${f(income-expense)}")
        if (hasBalanceAnchor) appendLine("余额: ¥${f(calcBal(now.timeInMillis, records, balanceAnchorDate, balanceAnchorAmount))}")
        appendLine("本月分类: ${monthByCat.toList().sortedByDescending { it.second }.take(6).joinToString { "${it.first} ¥${f(it.second)}" }}")
        appendLine()
        appendLine("最近${recent.size}条 (ID|时间|金额|类别|备注):")
        recent.forEach { tx ->
            val s = if (tx.type == "income") "+" else if (tx.type == "expense") "-" else ""
            appendLine("[ID:${tx.id}] ${df.format(Date(tx.time))} $s¥${f(tx.amount)} ${tx.category} ${tx.note}")
        }
        appendLine()
        appendLine("回复: 中文、亲切。操作跟JSON。投资加风险提示。")
    }
}

// ---- Budget Calc ----

data class BudgetStatus(val spent: Double, val remaining: Double, val periodLabel: String)

private fun calcBudgetStatus(records: List<Transaction>, targetAmount: Double, trackCategory: String, targetDate: String): BudgetStatus {
    val label = targetDate.ifBlank { "本月" }
    val monthNames = listOf("", "1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月")
    val rangeStart = when {
        targetDate.contains("月") -> {
            val m = monthNames.indexOfFirst { targetDate.contains(it) }
            Calendar.getInstance().apply { set(if (m > 0) m - 1 else Calendar.getInstance().get(Calendar.MONTH), 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        }
        targetDate.contains("本周") -> Calendar.getInstance().apply { val d = get(Calendar.DAY_OF_WEEK); add(Calendar.DAY_OF_MONTH, if (d == Calendar.SUNDAY) -6 else Calendar.MONDAY - d); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        else -> Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
    }
    val spent = records.filter { it.time >= rangeStart && it.type == "expense" && it.category == trackCategory }.sumOf { it.amount }
    return BudgetStatus(spent = spent, remaining = targetAmount - spent, periodLabel = label)
}

// ---- JSON Parser ----

data class ParsedAiResponse(val textContent: String, val cards: List<ParsedCard> = emptyList(),
                            val txCommands: List<JsonTxCommand> = emptyList(), val aiClaimedOperation: Boolean = false)

private fun parseAiResponse(reply: String): ParsedAiResponse {
    val cards = mutableListOf<ParsedCard>(); val txs = mutableListOf<JsonTxCommand>(); var text = reply

    // Strategy 1: ```json ... ```
    Regex("""```json\s*([\s\S]*?)```""").findAll(reply).forEach { m ->
        parseBlock(m.groupValues[1].trim(), cards, txs); text = text.replace(m.value, "")
    }
    // Strategy 2: ``` ... ``` with JSON inside
    Regex("""```\s*(\{[\s\S]*?"cards"[\s\S]*?\})\s*```""").findAll(text).forEach { m ->
        parseBlock(m.groupValues[1].trim(), cards, txs); text = text.replace(m.value, "")
    }
    Regex("""```\s*(\{[\s\S]*?"transactions"[\s\S]*?\})\s*```""").findAll(text).forEach { m ->
        parseBlock(m.groupValues[1].trim(), cards, txs); text = text.replace(m.value, "")
    }
    // Strategy 3: Inline JSON
    Regex("""\{[^{}]*"cards"\s*:\s*\[[\s\S]*?\][^{}]*\}""").find(text)?.let {
        parseBlock(it.value, cards, txs); text = text.replace(it.value, "")
    }
    Regex("""\{[^{}]*"transactions"\s*:\s*\[[\s\S]*?\][^{}]*\}""").find(text)?.let {
        parseBlock(it.value, cards, txs); text = text.replace(it.value, "")
    }
    // Strategy 4: Single quote fix
    if (cards.isEmpty() && txs.isEmpty()) {
        Regex("""```(?:json)?\s*(\{[\s\S]*?'(?:cards|transactions)'[\s\S]*?\})\s*```""").find(reply)?.let { m ->
            parseBlock(m.groupValues[1].replace('\'', '"'), cards, txs); text = text.replace(m.value, "")
        }
    }
    // Detect claims without JSON
    val hasClaim = listOf("已添加", "已修改", "已更新", "已删除", "已记", "已生成", "添加了", "修改了", "更新了", "删除了", "记下了", "生成了").any { text.lowercase().contains(it) }
    return ParsedAiResponse(text.trim(), cards, txs, aiClaimedOperation = hasClaim && cards.isEmpty() && txs.isEmpty())
}

private fun parseBlock(jsonStr: String, cards: MutableList<ParsedCard>, txs: MutableList<JsonTxCommand>) {
    fun parseObj(obj: JSONObject) {
        obj.optJSONArray("cards")?.let { arr ->
            for (i in 0 until arr.length()) { val c = arr.getJSONObject(i); cards.add(ParsedCard(type = c.optString("type","DECISION"), title = c.optString("title",""), description = c.optString("description",""), decision = c.optString("decision",""), targetAmount = c.optDouble("targetAmount",0.0), targetDate = c.optString("targetDate",""), trackCategory = c.optString("trackCategory",""), pattern = c.optString("pattern",""), actionSuggestion = c.optString("actionSuggestion",""), habitName = c.optString("habitName",""), frequency = c.optString("frequency","DAILY"), target = c.optInt("target",0))) }
        }
        obj.optJSONArray("transactions")?.let { arr ->
            for (i in 0 until arr.length()) { val t = arr.getJSONObject(i)
                // Parse date from either "date" string (e.g. "2026-05-15") or "time" epoch millis
                var ts = t.optLong("time", 0L)
                if (ts <= 0L) {
                    val dateStr = t.optString("date", "")
                    if (dateStr.isNotBlank()) {
                        ts = try { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(dateStr)?.time ?: 0L } catch (_: Exception) { 0L }
                    }
                }
                if (ts <= 0L) ts = System.currentTimeMillis()
                txs.add(JsonTxCommand(action = t.optString("action","add"), id = t.optLong("id",0L), amount = t.optDouble("amount",0.0), type = t.optString("type","expense"), category = t.optString("category","其他"), note = t.optString("note",""), time = ts))
            }
        }
    }
    try { parseObj(JSONObject(jsonStr)) } catch (_: Exception) {
        try { val arr = JSONArray(jsonStr); for (i in 0 until arr.length()) parseObj(arr.getJSONObject(i)) } catch (_: Exception) {}
    }
}

// ---- CRUD Execution ----

private suspend fun executeCrudCommand(cmd: JsonTxCommand, db: AppDatabase): String? {
    return try {
        when (cmd.action) {
            "add" -> {
                db.transactionDao().insert(com.financeauto.data.local.entity.TransactionEntity(amount = abs(cmd.amount), type = cmd.type, category = cmd.category, note = cmd.note, fingerprint = "", time = if (cmd.time > 0L) cmd.time else System.currentTimeMillis()))
                "已添加：${cmd.category} ¥${f(cmd.amount)}"
            }
            "update" -> {
                if (cmd.id <= 0L) return null
                val ex = db.transactionDao().getById(cmd.id) ?: return "未找到 ID=${cmd.id}"
                db.transactionDao().update(ex.copy(
                    amount = if (cmd.amount > 0.0) cmd.amount else ex.amount,
                    type = if (cmd.type.isNotBlank()) cmd.type else ex.type,
                    category = if (cmd.category.isNotBlank()) cmd.category else ex.category,
                    note = if (cmd.note.isNotBlank()) cmd.note else ex.note
                ))
                "已更新 ID=${cmd.id}"
            }
            "delete" -> {
                if (cmd.id <= 0L) return null
                db.transactionDao().deleteById(cmd.id)
                "已删除 ID=${cmd.id}"
            }
            else -> null
        }
    } catch (e: Exception) { "操作失败: ${e.message}" }
}

// ---- Card Intent Detection (direct user input) ----

private fun detectCardIntent(text: String): ParsedCard? {
    val now = Calendar.getInstance()
    val cm = "${now.get(Calendar.MONTH) + 1}月"

    // Direct card creation requests
    if (text.contains("卡片") || text.contains("创建卡") || text.contains("生成卡") || text.contains("添加卡")) {
        // Try to extract budget info
        val amt = Regex("""(\d+\.?\d*)\s*元?""").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val cat = CATEGORY_MAP.entries.firstOrNull { text.contains(it.key) }
        if (amt != null && amt > 0 && cat != null) {
            return ParsedCard(type = "BUDGET", title = "${cm}${cat.key}预算", description = "控制${cat.key}开销",
                decision = "限制${cat.key}支出不超过${f(amt)}元", targetAmount = amt, targetDate = cm, trackCategory = cat.value)
        }
        if (text.contains("预算")) {
            return ParsedCard(type = "BUDGET", title = "${cm}消费预算", description = "控制当月支出",
                decision = "设定消费预算", targetAmount = 0.0, targetDate = cm, trackCategory = "其他")
        }
        // Generic card intent
        return ParsedCard(type = "DECISION", title = "${cm}消费目标", description = "控制当月支出",
            decision = text.take(80), targetAmount = 0.0, targetDate = cm)
    }

    // Budget-related intent (but NLP didn't catch specifics)
    if ((text.contains("预算") || text.contains("省钱") || text.contains("控制") && text.contains("花")) &&
        !text.contains("花了") && !text.contains("买了") && !text.contains("付了")) {
        val amt = Regex("""(\d+\.?\d*)\s*元?""").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val cat = CATEGORY_MAP.entries.firstOrNull { text.contains(it.key) }
        if (cat != null || amt != null) {
            return ParsedCard(type = "BUDGET", title = if (cat != null) "${cm}${cat.key}预算" else "${cm}消费预算",
                description = "控制开销", decision = text.take(80),
                targetAmount = amt ?: 0.0, targetDate = cm,
                trackCategory = cat?.value ?: "其他")
        }
        return ParsedCard(type = "DECISION", title = "${cm}省钱目标", description = "控制开销",
            decision = text.take(80), targetAmount = amt ?: 0.0, targetDate = cm)
    }

    // 存钱 intent
    if (text.contains("存钱") || text.contains("攒钱") || text.contains("储蓄")) {
        val amt = Regex("""(\d+\.?\d*)\s*元?""").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        return ParsedCard(type = "SAVINGS", title = "${cm}存钱目标", description = "努力存钱",
            decision = if (amt != null) "本月存${f(amt)}元" else "本月多存钱",
            targetAmount = amt ?: 0.0, targetDate = cm)
    }

    // 打卡 intent
    val ckMatch = Regex("""(每天|每周|每月)\s*(记账|运动|学习|阅读|省钱|存钱|减肥|健身|早睡)""").find(text)
    if (ckMatch != null) {
        val freq = when (ckMatch.groupValues[1]) { "每天" -> "DAILY"; "每周" -> "WEEKLY"; else -> "MONTHLY" }
        val habit = ckMatch.groupValues[2]
        return ParsedCard(type = "CHECKIN", title = habit, habitName = habit, frequency = freq,
            description = "${ckMatch.groupValues[1]}${habit}")
    }

    return null
}

// ---- AI Text → Card Fallback Extractor ----

private val CATEGORY_MAP = mapOf(
    "咖啡" to "餐饮美食", "奶茶" to "餐饮美食", "外卖" to "餐饮美食", "餐厅" to "餐饮美食",
    "午餐" to "餐饮美食", "晚餐" to "餐饮美食", "早饭" to "餐饮美食", "零食" to "餐饮美食",
    "打车" to "交通出行", "滴滴" to "交通出行", "公交" to "交通出行", "地铁" to "交通出行",
    "购物" to "网购购物", "淘宝" to "网购购物", "京东" to "网购购物", "拼多多" to "网购购物",
    "超市" to "日用百货", "便利店" to "日用百货",
    "话费" to "充值缴费", "充值" to "充值缴费"
)

private fun extractCardsFromAiText(text: String): List<ParsedCard> {
    if (text.isBlank()) return emptyList()
    val now = Calendar.getInstance()
    val cm = "${now.get(Calendar.MONTH) + 1}月"
    val result = mutableListOf<ParsedCard>()

    fun addBudget(keyword: String, amount: Double?) {
        val cat = CATEGORY_MAP.entries.firstOrNull { keyword.contains(it.key) }?.value ?: "其他"
        if (result.none { it.type == "BUDGET" && it.title.contains(keyword) }) {
            result.add(ParsedCard(
                type = "BUDGET", title = "${cm}${keyword}预算", description = "控制${keyword}开销",
                decision = if (amount != null && amount > 0) "限制${keyword}支出不超过${f(amount)}元" else "控制${keyword}预算",
                targetAmount = amount ?: 0.0, targetDate = cm, trackCategory = cat
            ))
        }
    }

    // Pattern 1: "200元的咖啡预算" / "咖啡预算200元" / "预算200元咖啡"
    Regex("""(\d+\.?\d*)\s*元?\s*(?:的)?\s*(\S{1,6})\s*(?:的)?\s*(?:预算|花费|开销)""").findAll(text).forEach { m ->
        addBudget(m.groupValues[2], m.groupValues[1].toDoubleOrNull())
    }
    Regex("""(\S{1,6})\s*(?:预算|花费|开销)(?:\S{0,4})?(\d+\.?\d*)\s*元?""").findAll(text).forEach { m ->
        addBudget(m.groupValues[1], m.groupValues[2].toDoubleOrNull())
    }
    Regex("""(?:预算|花费|开销)(?:\S{0,4})?(\d+\.?\d*)\s*元?\s*(?:\S{0,4})?(\S{1,6})""").findAll(text).forEach { m ->
        addBudget(m.groupValues[2], m.groupValues[1].toDoubleOrNull())
    }

    // Pattern 2: "建议设定预算" or "可以设置预算" with optional category
    Regex("""(?:建议|可以|应该|需要).{0,10}(?:设置|设定|做|搞).{0,6}(?:一个|个|一下)?(\S{1,6})?\s*(?:预算|省钱)""").findAll(text).forEach { m ->
        val kw = m.groupValues.getOrNull(1)?.trim()?.take(6) ?: return@forEach
        if (kw.isNotBlank() && kw.length in 1..6 && kw !in listOf("的", "了", "是", "个", "和", "为")) {
            addBudget(kw, null)
        }
    }

    // Pattern 3: "存钱XXX元" / "储蓄目标XXX"
    Regex("""(?:存钱|储蓄|攒钱|存).{0,6}(\d+\.?\d*)\s*元?""").find(text)?.let { m ->
        val amt = m.groupValues[1].toDoubleOrNull()
        if (amt != null && amt > 0 && result.none { it.type == "SAVINGS" }) {
            result.add(ParsedCard(type = "SAVINGS", title = "${cm}存钱目标", description = "努力存钱",
                decision = "本月存${f(amt)}元", targetAmount = amt, targetDate = cm))
        }
    }

    // Pattern 4: "每天/每周/每月" + habit -> CHECKIN
    Regex("""(每天|每周|每月)\s*(记账|运动|学习|阅读|省钱|存钱|减肥|健身|早睡)""").findAll(text).forEach { m ->
        val freq = when (m.groupValues[1]) { "每天" -> "DAILY"; "每周" -> "WEEKLY"; else -> "MONTHLY" }
        val habit = m.groupValues[2]
        if (result.none { it.type == "CHECKIN" && it.habitName == habit }) {
            result.add(ParsedCard(type = "CHECKIN", title = habit, habitName = habit, frequency = freq,
                description = "${m.groupValues[1]}${habit}"))
        }
    }

    // Pattern 5: Any budget-number pair in reasonable range
    Regex("""(\d{2,5}(?:\.\d{1,2})?)\s*元\s*(?:的|用于)?\s*(\S{1,6})\s*(?:预算|目标|计划)""").findAll(text).forEach { m ->
        val amt = m.groupValues[1].toDoubleOrNull()
        val kw = m.groupValues[2]
        if (amt != null && amt in 1.0..99999.0 && kw.length in 1..6) {
            addBudget(kw, amt)
        }
    }

    return result
}

// ---- Card Entity Factory ----

private fun createCardEntity(card: ParsedCard, src: String): CardEntity? {
    if (card.title.isBlank()) return null
    val jd = JSONObject().apply {
        when (card.type) {
            "DECISION", "BUDGET", "SAVINGS" -> { put("decision", card.decision); if (card.targetAmount > 0) put("targetAmount", card.targetAmount); if (card.targetDate.isNotBlank()) put("targetDate", card.targetDate); if (card.trackCategory.isNotBlank()) put("trackCategory", card.trackCategory) }
            "INSIGHT" -> { put("pattern", card.pattern); if (card.actionSuggestion.isNotBlank()) put("actionSuggestion", card.actionSuggestion) }
            "CHECKIN" -> { put("frequency", card.frequency); if (card.target > 0) put("target", card.target); put("currentProgress", 0) }
            else -> { put("decision", card.decision); if (card.targetAmount > 0) put("targetAmount", card.targetAmount) }
        }
    }
    return CardEntity(cardType = card.type, title = card.title, description = card.description, sourceMessage = src.take(100), jsonData = jd.toString(), status = "ACTIVE")
}

// ---- Helpers ----

private fun getLastUserMsg(msgs: List<ChatMessage>) = msgs.findLast { it.role == "user" }?.content ?: ""
private fun buildChatItemsFromMessages(msgs: List<ChatMessage>): List<ChatItem> = msgs.filter { it.role != "system" }.map { ChatItem.Message(it.role, it.content) }
private fun calcBal(targetDate: Long, txs: List<Transaction>, anchorDate: Long, anchorAmount: Double): Double { val r = txs.filter { it.time in minOf(targetDate, anchorDate)..maxOf(targetDate, anchorDate) }; val n = r.sumOf { when (it.type) { "income" -> it.amount; "expense" -> -it.amount; else -> 0.0 } }; return if (targetDate >= anchorDate) anchorAmount + n else anchorAmount - n }

private suspend fun callChatApi(apiUrl: String, apiKey: String, model: String, messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
    val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; setRequestProperty("Content-Type", "application/json"); setRequestProperty("Authorization", "Bearer $apiKey"); doOutput = true; connectTimeout = 30000; readTimeout = 60000 }
    val body = JSONObject().apply { put("model", model); put("messages", JSONArray().apply { messages.forEach { msg -> put(JSONObject().apply { put("role", msg.role); put("content", msg.content) }) } }); put("stream", false) }
    OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
    if (conn.responseCode !in 200..299) { val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"; conn.disconnect(); throw RuntimeException("API error: $err") }
    val text = conn.inputStream.bufferedReader().readText(); conn.disconnect()
    JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
}

private fun saveChatMessages(prefs: SharedPreferences, msgs: List<ChatMessage>) {
    val json = JSONArray(); msgs.filter { it.role != "system" }.forEach { msg -> json.put(JSONObject().apply { put("role", msg.role); put("content", msg.content) }) }; prefs.edit().putString("chat_history", json.toString()).apply()
}

private fun loadChatMessages(prefs: SharedPreferences): List<ChatMessage> {
    val s = prefs.getString("chat_history", null) ?: return emptyList()
    return try { val json = JSONArray(s); (0 until json.length()).map { i -> val o = json.getJSONObject(i); ChatMessage(o.getString("role"), o.getString("content")) } } catch (_: Exception) { emptyList() }
}


private val AppBg = Color(0xFFFFF8E7)
private val CardBg = Color(0xFFFFFFFF)
private val Ink = Color(0xFF252735)
private val Muted = Color(0xFF74768A)
private val Pink = Color(0xFFFF3D8B)
private val Blue = Color(0xFF2979FF)
private fun f(amount: Double): String = String.format(Locale.CHINA, "%.2f", amount)
