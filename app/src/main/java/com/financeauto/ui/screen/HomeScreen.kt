package com.financeauto.ui.screen

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financeauto.data.model.Transaction
import com.financeauto.ui.screen.OcrScanner
import com.financeauto.data.parser.AlipayExcelParser
import com.financeauto.data.parser.BankExcelParser
import com.financeauto.data.parser.ExcelBillParser
import com.financeauto.data.parser.WeChatExcelParser
import com.financeauto.viewmodel.TransactionViewModel
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
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun HomeScreen(
    vm: TransactionViewModel = viewModel(),
    nickname: String = "财务管家",
    onProfileClick: () -> Unit = {},
    expiryDate: Long = 0L,
    planType: String = "free"
) {
    val context = LocalContext.current
    val records by vm.transactions.collectAsState(initial = emptyList())
    val sorted = remember(records) { records.sortedByDescending { it.time } }
    var detailCategory by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var showBalanceDialog by remember { mutableStateOf(false) }
    var showOcrScanner by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("finance_prefs", Context.MODE_PRIVATE) }
    val chatPrefs = remember { context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var balanceAnchorDate by remember { mutableStateOf(prefs.getLong("balance_anchor_date", 0L)) }
    var balanceAnchorAmount by remember { mutableStateOf(prefs.getFloat("balance_anchor_amount", 0f).toDouble()) }
    val hasBalanceAnchor = balanceAnchorDate > 0L && balanceAnchorAmount > 0.0
    var aiLoading by remember { mutableStateOf(false) }

    val wechat = importLauncher(context, vm, "WeChat", WeChatExcelParser()) { status = it }
    val alipay = importLauncher(context, vm, "Alipay", AlipayExcelParser()) { status = it }
    val bank = importLauncher(context, vm, "Bank", BankExcelParser()) { status = it }

    if (showBalanceDialog) {
        BalanceSetupDialog(
            currentDate = balanceAnchorDate,
            currentAmount = balanceAnchorAmount,
            onSave = { date, amount ->
                prefs.edit().putLong("balance_anchor_date", date).putFloat("balance_anchor_amount", amount.toFloat()).apply()
                balanceAnchorDate = date
                balanceAnchorAmount = amount
                showBalanceDialog = false
            },
            onDismiss = { showBalanceDialog = false }
        )
    }

    editing?.let { tx ->
        EditDialog(
            tx = tx,
            onDismiss = { editing = null },
            onSave = {
                vm.update(it)
                status = "已更新 1 条记录"
                editing = null
            },
            onDelete = {
                vm.delete(tx)
                status = "已删除 1 条记录"
                editing = null
            }
        )
    }

    Box(Modifier.fillMaxSize().background(AppBg)) {
        when {
            detailCategory != null -> DetailPage(
                category = detailCategory.orEmpty(),
                records = sorted,
                onBack = { detailCategory = null },
                onCategory = { detailCategory = it },
                onEdit = { editing = it }
            )

            else -> AnalysisPage(
                records = sorted,
                status = status,
                hasBalanceAnchor = hasBalanceAnchor,
                balanceAnchorDate = balanceAnchorDate,
                balanceAnchorAmount = balanceAnchorAmount,
                nickname = nickname,
                onProfileClick = onProfileClick,
                onSetBalance = { showBalanceDialog = true },
                onScan = { showOcrScanner = true },
                onWechat = { wechat.launch(EXCEL_MIME_TYPE) },
                onAlipay = { alipay.launch(EXCEL_MIME_TYPE) },
                onBank = { bank.launch(EXCEL_MIME_TYPE) },
                onClear = {
                    vm.clear()
                    status = "已清空全部记录"
                },
                onAdd = {
                    vm.add(it)
                    status = "已添加 1 条记录"
                },
                onCategory = { detailCategory = it },
                onEdit = { editing = it },
                expiryDate = expiryDate,
                planType = planType,
                onAiCategorize = {
                    val apiUrl = chatPrefs.getString("chat_api_url", "https://api.deepseek.com/v1/chat/completions") ?: "https://api.deepseek.com/v1/chat/completions"
                    val apiKey = chatPrefs.getString("chat_api_key", "") ?: ""
                    val model = chatPrefs.getString("chat_model", "deepseek-chat") ?: "deepseek-chat"
                    if (apiKey.isBlank()) {
                        status = "请先在 AI 对话设置中配置 API 密钥"
                        return@AnalysisPage
                    }
                    aiLoading = true
                    scope.launch {
                        try {
                            val corrections = autoCategorizeWithAI(apiUrl, apiKey, model, sorted)
                            if (corrections.isEmpty()) {
                                status = "AI：所有类别已正确"
                            } else {
                                for ((id, newCat) in corrections) {
                                    val tx = sorted.find { it.id == id } ?: continue
                                    vm.update(tx.copy(category = newCat))
                                }
                                status = "AI：已更新 ${corrections.size} 个类别"
                            }
                        } catch (e: Exception) {
                            status = "AI 错误：${e.message}"
                        } finally {
                            aiLoading = false
                        }
                    }
                },
                aiLoading = aiLoading
            )
        }

        if (showOcrScanner) {
            OcrScanner(
                onSave = { tx ->
                    vm.add(tx)
                    status = "已扫描：${tx.category} ¥${String.format(Locale.CHINA, "%.2f", tx.amount)}"
                    showOcrScanner = false
                },
                onDismiss = { showOcrScanner = false }
            )
        }
    }
}

@Composable
private fun importLauncher(
    context: Context,
    vm: TransactionViewModel,
    platform: String,
    parser: ExcelBillParser,
    onStatus: (String) -> Unit
) = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    uri?.let {
        importExcel(it, context, platform, parser) { txs, message ->
            if (txs.isNotEmpty()) vm.addAll(txs)
            onStatus(message)
        }
    }
}

@Composable
private fun AnalysisPage(
    records: List<Transaction>,
    status: String?,
    hasBalanceAnchor: Boolean,
    balanceAnchorDate: Long,
    balanceAnchorAmount: Double,
    nickname: String,
    onProfileClick: () -> Unit,
    onSetBalance: () -> Unit,
    onScan: () -> Unit,
    onWechat: () -> Unit,
    onAlipay: () -> Unit,
    onBank: () -> Unit,
    onClear: () -> Unit,
    onAdd: (Transaction) -> Unit,
    onCategory: (String) -> Unit,
    onEdit: (Transaction) -> Unit,
    expiryDate: Long = 0L,
    planType: String = "free",
    onAiCategorize: () -> Unit = {},
    aiLoading: Boolean = false
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Overview(records, hasBalanceAnchor, balanceAnchorDate, balanceAnchorAmount, nickname, onProfileClick, onSetBalance, onScan, onWechat, onAlipay, onBank, onClear, expiryDate, planType, onAiCategorize, aiLoading) }
        status?.let { item { StatusMessage(it) } }
        item { CardWallSection() }
        item { PiePanel(records.filter { it.type == "expense" }, selected = null, onCategory = onCategory) }
        item { QuickAdd(onAdd) }
        item { SectionTitle("最近记录", "${records.take(20).size} 条") }
        if (records.isEmpty()) item { EmptyState() } else {
            items(records.take(20), key = { stableKey(it) }) { tx ->
                TransactionItem(tx) { onEdit(tx) }
            }
        }
    }
}

@Composable
private fun DetailPage(
    category: String,
    records: List<Transaction>,
    onBack: () -> Unit,
    onCategory: (String) -> Unit,
    onEdit: (Transaction) -> Unit
) {
    val details = records.filter { it.category == category }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(category, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
                    Text("${details.size} 条", color = Muted)
                }
                OutlinedButton(onClick = onBack, colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)) { Text("返回") }
            }
        }
        item { PiePanel(records.filter { it.type == "expense" }, selected = category, onCategory = onCategory) }
        item { SectionTitle("$category 详情", "${details.size} 条") }
        if (details.isEmpty()) item { EmptyState("此类别暂无记录") } else {
            items(details, key = { stableKey(it) }) { tx -> TransactionItem(tx) { onEdit(tx) } }
        }
    }
}

@Composable
private fun CalendarPage(
    records: List<Transaction>,
    hasBalanceAnchor: Boolean,
    balanceAnchorDate: Long,
    balanceAnchorAmount: Double,
    onEdit: (Transaction) -> Unit
) {
    val initialMonth = remember(records) { monthStart(records.maxByOrNull { it.time }?.time ?: System.currentTimeMillis()) }
    var visibleMonth by remember(records) { mutableStateOf(initialMonth) }
    var selectedDay by remember(records, visibleMonth) { mutableStateOf(dayKey(visibleMonth.timeInMillis)) }
    val dayGroups = remember(records) { records.groupBy { dayKey(it.time) } }
    val dayRecords = dayGroups[selectedDay].orEmpty().sortedByDescending { it.time }

    var yearExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    val currentYear = visibleMonth.get(Calendar.YEAR)
    val currentMonth = visibleMonth.get(Calendar.MONTH) + 1
    val yearRange = remember { (Calendar.getInstance().get(Calendar.YEAR) - 10 .. Calendar.getInstance().get(Calendar.YEAR) + 10).toList() }
    val monthLabels = listOf("1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            TextButton(onClick = { yearExpanded = true }) {
                                Text("${currentYear}年", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
                            }
                            DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                                yearRange.forEach { year ->
                                    DropdownMenuItem(
                                        text = { Text("${year}年") },
                                        onClick = {
                                            visibleMonth = shiftYear(visibleMonth, year)
                                            yearExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Text(" ", style = MaterialTheme.typography.titleLarge)
                        Box {
                            TextButton(onClick = { monthExpanded = true }) {
                                Text(monthLabels[currentMonth - 1], style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
                            }
                            DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                                monthLabels.forEachIndexed { index, label ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            visibleMonth = shiftMonth(visibleMonth, index + 1 - currentMonth)
                                            monthExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    CalendarGrid(visibleMonth, dayGroups, selectedDay) { selectedDay = it }
                }
            }
        }
        item { DaySummary(selectedDay, dayRecords, hasBalanceAnchor, balanceAnchorDate, balanceAnchorAmount, sorted = records) }
        if (dayRecords.isEmpty()) item { EmptyState("该日无记录") } else {
            items(dayRecords, key = { stableKey(it) }) { tx -> TransactionItem(tx) { onEdit(tx) } }
        }
    }
}

@Composable
private fun Overview(
    records: List<Transaction>,
    hasBalanceAnchor: Boolean,
    balanceAnchorDate: Long,
    balanceAnchorAmount: Double,
    nickname: String,
    onProfileClick: () -> Unit,
    onSetBalance: () -> Unit,
    onScan: () -> Unit,
    onWechat: () -> Unit,
    onAlipay: () -> Unit,
    onBank: () -> Unit,
    onClear: () -> Unit,
    expiryDate: Long = 0L,
    planType: String = "free",
    onAiCategorize: () -> Unit = {},
    aiLoading: Boolean = false
) {
    val income = records.filter { it.type == "income" }.sumOf { it.amount }
    val expense = records.filter { it.type == "expense" }.sumOf { it.amount }
    val todayEnd = endOfDay(System.currentTimeMillis())
    val totalBalance = if (hasBalanceAnchor) calculateBalance(todayEnd, records, balanceAnchorDate, balanceAnchorAmount) else null

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Sun), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(nickname, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f).clickable(onClick = onProfileClick))
                Button(
                    onClick = onAiCategorize,
                    enabled = !aiLoading,
                    colors = ButtonDefaults.buttonColors(Purple, Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (aiLoading) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("...", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text("AI校对", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Membership warning
            val daysRemaining = if (expiryDate > 0L) ((expiryDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000) + 1).toInt().coerceAtLeast(0) else 0
            val isExpired = expiryDate > 0L && expiryDate < System.currentTimeMillis()
            if (planType != "permanent" && expiryDate > 0L) {
                if (isExpired) {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color(0xFFFF3D8B).copy(alpha = 0.1f)) {
                        Text("会员已过期，请续费", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color(0xFFFF3D8B), style = MaterialTheme.typography.labelMedium)
                    }
                } else if (daysRemaining <= 7) {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color(0xFFFF8A00).copy(alpha = 0.1f)) {
                        Text("会员将在 ${daysRemaining} 天后到期", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color(0xFFFF8A00), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Text("智能记账 · 消费分析 · 重复检测 · AI 对话", color = Ink.copy(alpha = 0.74f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBlock("支出", expense, Modifier.weight(1f), Pink)
                StatBlock("收入", income, Modifier.weight(1f), Green)
                StatBlock("结余", income - expense, Modifier.weight(1f), Blue)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (totalBalance != null) {
                    Column {
                        Text("总余额", style = MaterialTheme.typography.labelMedium, color = Muted)
                        Text(money(totalBalance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Blue)
                    }
                } else {
                    Text("设置当前总余额以追踪变化", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = onSetBalance,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
                ) { Text(if (hasBalanceAnchor) "更新余额" else "设置余额") }
            }
            if (totalBalance != null) {
                Text("上次设置：${formatDate(balanceAnchorDate)}", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onWechat, modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(Blue, Color.White),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)) {
                    Text("微信", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = onAlipay, modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(Orange, Color.White),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)) {
                    Text("支付宝", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = onBank, modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(Green, Color.White),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)) {
                    Text("银行", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = onScan, modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(Purple, Color.White),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)) {
                    Text("扫描", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (records.isNotEmpty()) OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)) { Text("清空全部") }
        }
    }
}

@Composable
private fun CardWallSection() {
    val context = LocalContext.current
    val db = remember { com.financeauto.data.local.AppDatabase.getDatabase(context) }
    val cards by db.cardDao().getAllActiveFlow().collectAsState(initial = emptyList())

    val activeCards = remember(cards) {
        cards.filter { it.status == "ACTIVE" }.take(3)
    }

    if (activeCards.isEmpty()) return

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(CardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "我的决策墙",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Text(
                    "${activeCards.size} 张活跃卡片",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted
                )
            }
            Spacer(Modifier.height(8.dp))
            activeCards.forEach { card ->
                val typeColor = when (card.cardType) {
                    "DECISION" -> Color(0xFFFF9500)
                    "INSIGHT" -> Color(0xFF5856D6)
                    "CHECKIN" -> Color(0xFF34C759)
                    else -> Color(0xFF8E8E93)
                }
                val typeLabel = when (card.cardType) {
                    "DECISION" -> "决策"
                    "INSIGHT" -> "洞察"
                    "CHECKIN" -> "打卡"
                    else -> card.cardType
                }
                Surface(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF5F5F7)
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(typeColor, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                card.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Ink,
                                maxLines = 1
                            )
                            if (card.description.isNotBlank()) {
                                Text(
                                    card.description.take(30),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted,
                                    maxLines = 1
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = typeColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                typeLabel,
                                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = typeColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, amount: Double, modifier: Modifier, color: Color) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = CardBg.copy(alpha = 0.76f)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
            Text(money(amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PiePanel(records: List<Transaction>, selected: String?, onCategory: (String) -> Unit) {
    val groups = remember(records) { categoryTotals(records) }
    val total = groups.sumOf { it.amount }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("支出类别", selected ?: "点击分类查看详情")
            if (groups.isEmpty() || total <= 0.0) {
                Text("导入或添加支出以查看图表", color = Muted)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Pie(groups, selected, total, onCategory, Modifier.size(156.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        groups.take(7).forEach { item -> Legend(item, total, item.category == selected) { onCategory(item.category) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pie(groups: List<CategoryTotal>, selected: String?, total: Double, onCategory: (String) -> Unit, modifier: Modifier) {
    Canvas(
        modifier.pointerInput(groups, total) {
            detectTapGestures { tap -> hitCategory(tap, size.width.toFloat(), size.height.toFloat(), groups, total)?.let(onCategory) }
        }
    ) {
        var start = -90f
        val side = size.minDimension * 0.82f
        val base = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        val chart = androidx.compose.ui.geometry.Size(side, side)
        groups.forEach { item ->
            val sweep = ((item.amount / total) * 360f).toFloat()
            val active = item.category == selected
            val mid = (start + sweep / 2f) * (PI.toFloat() / 180f)
            val lift = if (active) Offset(cos(mid) * 7f, sin(mid) * 7f - 7f) else Offset.Zero
            for (layer in 5 downTo 1) {
                drawArc(item.color.darken(0.68f).copy(alpha = 0.26f), start, sweep, true, base + lift + Offset(0f, layer * 2.1f), chart)
            }
            drawArc(item.color, start, sweep, true, base + lift, chart)
            start += sweep
        }
    }
}

@Composable
private fun Legend(item: CategoryTotal, total: Double, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) Yellow.copy(alpha = 0.34f) else Color.Transparent
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(11.dp).background(item.color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(item.category, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${((item.amount / total) * 100).roundToInt()}% | ${money(item.amount)}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAdd(onAdd: (Transaction) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("餐饮美食") }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selectedDate = it }
                    showDatePicker = false
                }) { Text("确定", color = Blue) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消", color = Muted) }
            }
        ) { DatePicker(state = state) }
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("手动添加", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("类别") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, modifier = Modifier.weight(1.25f), singleLine = true)
            }
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("日期：${dateFormat.format(Date(selectedDate))}", color = Ink) }
            Button(
                onClick = {
                    val value = amount.toDoubleOrNull()
                    if (value != null && value > 0 && category.isNotBlank()) {
                        onAdd(Transaction(amount = value, type = "expense", category = category.trim(), note = note.trim(), time = adjustTime(selectedDate)))
                        amount = ""
                        note = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(Green, Color.White)
            ) { Text("添加支出") }
        }
    }
}

@Composable
private fun TransactionItem(tx: Transaction, onEdit: () -> Unit) {
    val sign = if (tx.type == "income") "+" else if (tx.type == "neutral") "" else "-"
    val color = when (tx.type) {
        "income" -> Green
        "neutral" -> Muted
        else -> Pink
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(tx.category)
                    Spacer(Modifier.width(8.dp))
                    Text(typeLabel(tx.type), style = MaterialTheme.typography.labelMedium, color = Muted)
                }
                Spacer(Modifier.height(6.dp))
                Text(tx.note.ifBlank { "无备注" }, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDate(tx.time), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$sign${money(tx.amount)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                TextButton(onClick = onEdit) { Text("编辑", color = Blue) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDialog(tx: Transaction, onDismiss: () -> Unit, onSave: (Transaction) -> Unit, onDelete: () -> Unit) {
    var amount by remember(tx) { mutableStateOf(tx.amount.toString()) }
    var category by remember(tx) { mutableStateOf(tx.category) }
    var note by remember(tx) { mutableStateOf(tx.note) }
    var type by remember(tx) { mutableStateOf(tx.type) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember(tx) { mutableStateOf(tx.time) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selectedDate = it }
                    showDatePicker = false
                }) { Text("确定", color = Blue) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消", color = Muted) }
            }
        ) { DatePicker(state = state) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("类别") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeButton("支出", type == "expense") { type = "expense" }
                    TypeButton("收入", type == "income") { type = "income" }
                    TypeButton("Neutral", type == "neutral") { type = "neutral" }
                }
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text("日期：${dateFormat.format(Date(selectedDate))}", color = Ink)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = amount.toDoubleOrNull()
                if (value != null && value > 0 && category.isNotBlank()) {
                    onSave(tx.copy(amount = value, category = category.trim(), note = note.trim(), type = type, time = adjustTime(selectedDate)))
                }
            }) { Text("保存", color = Blue) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除", color = Pink) }
                TextButton(onClick = onDismiss) { Text("取消", color = Muted) }
            }
        }
    )
}

@Composable
private fun TypeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = if (selected) ButtonDefaults.buttonColors(Blue, Color.White) else ButtonDefaults.buttonColors(AppBg, Ink)
    Button(onClick = onClick, colors = colors) { Text(text) }
}

@Composable
private fun SectionTitle(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(value, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DaySummary(
    day: String,
    records: List<Transaction>,
    hasBalanceAnchor: Boolean,
    balanceAnchorDate: Long,
    balanceAnchorAmount: Double,
    sorted: List<Transaction>
) {
    val income = records.filter { it.type == "income" }.sumOf { it.amount }
    val expense = records.filter { it.type == "expense" }.sumOf { it.amount }
    val dayEnd = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(day)?.time?.plus(DAY_MS - 1) ?: 0L
    val dayBalance = if (hasBalanceAnchor && dayEnd > 0L) calculateBalance(dayEnd, sorted, balanceAnchorDate, balanceAnchorAmount) else null
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Sun), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
                Text("${records.size} records", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("支出 ${money(expense)}", color = Pink, fontWeight = FontWeight.SemiBold)
                Text("收入 ${money(income)}", color = Green, fontWeight = FontWeight.SemiBold)
            }
            if (dayBalance != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("余额", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Text(money(dayBalance), color = Blue, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CalendarGrid(month: Calendar, groups: Map<String, List<Transaction>>, selected: String, onSelect: (String) -> Unit) {
    val cells = remember(month.timeInMillis) { calendarCells(month) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { cell ->
                    DayCell(cell, cell?.let { groups[it.key] }.orEmpty(), cell?.key == selected, { cell?.let { onSelect(it.key) } }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(cell: CalendarCell?, records: List<Transaction>, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val expense = records.filter { it.type == "expense" }.sumOf { it.amount }
    val income = records.filter { it.type == "income" }.sumOf { it.amount }
    val bg = if (selected) Blue else if (records.isNotEmpty()) Yellow.copy(alpha = 0.28f) else AppBg
    val fg = if (selected) Color.White else Ink
    Surface(modifier.height(58.dp).clickable(enabled = cell != null, onClick = onClick), shape = MaterialTheme.shapes.medium, color = bg) {
        if (cell == null) Spacer(Modifier.height(1.dp)) else Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(cell.day.toString(), color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (expense > 0) Dot(Pink)
                if (income > 0) Dot(Green)
            }
            if (expense > 0) Text(expense.roundToInt().toString(), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.84f))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BalanceSetupDialog(
    currentDate: Long,
    currentAmount: Double,
    onSave: (Long, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    var selectedDate by remember(currentDate) { mutableStateOf(if (currentDate > 0L) currentDate else System.currentTimeMillis()) }
    var amountText by remember(currentAmount) { mutableStateOf(if (currentAmount > 0.0) String.format(Locale.CHINA, "%.2f", currentAmount) else "") }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selectedDate = it }
                    showDatePicker = false
                }) { Text("确定", color = Blue) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消", color = Muted) }
            }
        ) { DatePicker(state = state) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置总余额", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("输入当前总余额，系统将根据交易数据计算历史余额。", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("日期：${dateFormat.format(Date(selectedDate))}", color = Ink)
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("总余额") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    prefix = { Text("¥") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Normalize to start of day
                val cal = Calendar.getInstance(Locale.CHINA).apply {
                    timeInMillis = selectedDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val normalizedDate = cal.timeInMillis
                val amount = amountText.toDoubleOrNull() ?: 0.0
                if (normalizedDate > 0L && amount > 0.0) {
                    onSave(normalizedDate, amount)
                }
            }) { Text("保存", color = Blue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Muted) }
        }
    )
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(5.dp).background(color, CircleShape))

@Composable
private fun Chip(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = Cyan.copy(alpha = 0.24f)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusMessage(text: String) {
    val ok = text.contains("Added") || text.contains("Updated") || text.contains("Imported") || text.contains("read") || text.contains("cleared")
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = if (ok) Green.copy(alpha = 0.18f) else Pink.copy(alpha = 0.16f)) {
        Text(text, Modifier.padding(12.dp), color = if (ok) Green else Pink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyState(text: String = "暂无记录，请从微信、支付宝或银行导入 .xlsx 文件。") {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(0.dp)) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text(text, color = Muted, textAlign = TextAlign.Center)
        }
    }
}

private fun importExcel(uri: Uri, context: Context, platform: String, parser: ExcelBillParser, done: (List<Transaction>, String) -> Unit) {
    try {
        val txs = context.contentResolver.openInputStream(uri)?.use { parser.parseExcel(it) }.orEmpty()
        val message = if (txs.isEmpty()) "未找到有效的 $platform 记录" else "已导入 ${txs.size} 条 $platform 记录，重复项已跳过"
        done(txs, message)
    } catch (t: Throwable) {
        done(emptyList(), t.message ?: "导入失败")
    }
}

private fun categoryTotals(records: List<Transaction>): List<CategoryTotal> {
    return records.groupBy { it.category }
        .mapValues { item -> item.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
        .mapIndexed { index, item -> CategoryTotal(item.first, item.second, PieColors[index % PieColors.size]) }
}

private fun hitCategory(tap: Offset, width: Float, height: Float, groups: List<CategoryTotal>, total: Double): String? {
    if (total <= 0.0) return null
    val side = minOf(width, height) * 0.82f
    val center = Offset(width / 2f, height / 2f)
    val dx = tap.x - center.x
    val dy = tap.y - center.y
    if (sqrt(dx * dx + dy * dy) > side / 2f) return null
    val raw = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    val angle = (raw + 450f) % 360f
    var cursor = 0f
    groups.forEach { item ->
        val sweep = ((item.amount / total) * 360f).toFloat()
        if (angle >= cursor && angle <= cursor + sweep) return item.category
        cursor += sweep
    }
    return groups.lastOrNull()?.category
}

private fun monthStart(time: Long): Calendar = Calendar.getInstance(Locale.CHINA).apply {
    timeInMillis = time
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun shiftMonth(source: Calendar, amount: Int): Calendar = (source.clone() as Calendar).apply { add(Calendar.MONTH, amount) }

private fun shiftYear(source: Calendar, year: Int): Calendar = (source.clone() as Calendar).apply { set(Calendar.YEAR, year) }

private fun calendarCells(month: Calendar): List<CalendarCell?> {
    val cursor = month.clone() as Calendar
    cursor.set(Calendar.DAY_OF_MONTH, 1)
    val blanks = when (val first = cursor.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> 6
        else -> first - Calendar.MONDAY
    }
    val result = MutableList<CalendarCell?>(blanks) { null }
    val maxDay = cursor.getActualMaximum(Calendar.DAY_OF_MONTH)
    for (day in 1..maxDay) {
        cursor.set(Calendar.DAY_OF_MONTH, day)
        result += CalendarCell(day, dayKey(cursor.timeInMillis))
    }
    while (result.size % 7 != 0) result += null
    return result
}

private fun typeLabel(type: String) = when (type) {
    "income" -> "收入"
    "neutral" -> "中性"
    else -> "支出"
}

private fun dayKey(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(time))
private fun monthLabel(month: Calendar): String = SimpleDateFormat("yyyy-MM", Locale.CHINA).format(month.time)
private fun stableKey(tx: Transaction): Long = if (tx.id != 0L) tx.id else tx.hashCode().toLong()
private fun formatTime(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(time))
private fun formatDate(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(time))
private fun money(amount: Double): String = "¥${String.format(Locale.CHINA, "%.2f", amount)}"
private fun Color.darken(factor: Float): Color = Color(red * factor, green * factor, blue * factor, alpha)

private fun adjustTime(datePickerMillis: Long): Long {
    val cal = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = datePickerMillis }
    val today = Calendar.getInstance(Locale.CHINA)
    val isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    if (isToday) return System.currentTimeMillis()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun endOfDay(time: Long): Long {
    val cal = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = time }
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis
}

private val VALID_CATEGORIES = setOf("餐饮美食", "网购购物", "日用百货", "校园生活", "交通出行", "生活服务", "充值缴费", "其他")

data class AiCorrection(
    val id: Long,
    val category: String? = null,
    val amount: Double? = null,
    val type: String? = null,
    val note: String? = null
)

private suspend fun autoCategorizeWithAI(
    apiUrl: String,
    apiKey: String,
    model: String,
    transactions: List<Transaction>
): List<Pair<Long, String>> = withContext(Dispatchers.IO) {
    val expenseTxs = transactions.filter { it.type == "expense" }
    if (expenseTxs.isEmpty()) return@withContext emptyList()

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    val txList = expenseTxs.mapIndexed { i, tx ->
        val date = dateFormat.format(Date(tx.time))
        "Index $i: $date | ¥${String.format(Locale.CHINA, "%.2f", tx.amount)} | ${tx.category} | ${tx.note}"
    }.joinToString("\n")

    val systemPrompt = buildString {
        appendLine("You are a financial transaction auditor. Review each transaction comprehensively and flag issues.")
        appendLine()
        appendLine("## Categories (must be one of):")
        appendLine("1.餐饮美食: restaurant, coffee, milk tea, takeout, fast food, bakery, snacks (典型金额 ¥5-200)")
        appendLine("2.网购购物: Taobao, JD, Pinduoduo, Douyin shop, clothing, electronics (典型金额 ¥10-5000)")
        appendLine("3.日用百货: supermarket, convenience store, daily necessities (典型金额 ¥5-500)")
        appendLine("4.校园生活: school, campus, cafeteria, textbook, tuition (典型金额 ¥5-2000)")
        appendLine("5.交通出行: taxi, bus, metro, bike, gas, train, flight (典型金额 ¥1-2000)")
        appendLine("6.生活服务: laundry, repair, barber, medical, express, entertainment, gym (典型金额 ¥5-1000)")
        appendLine("7.充值缴费: phone bill, top-up, utilities, membership (典型金额 ¥10-500)")
        appendLine("8.其他: none of the above")
        appendLine()
        appendLine("## Audit rules:")
        appendLine("- Category mismatch: e.g., '瑞幸' under 网购购物 → should be 餐饮美食")
        appendLine("- Amount anomaly: e.g., ¥5000 for 餐饮美食 is suspicious (likely ¥50.00 mis-entered)")
        appendLine("- Time anomaly: future dates, unusual hours for given category")
        appendLine("- Missing or wrong note: note doesn't match the category/merchant")
        appendLine()
        appendLine("Reply ONLY with a JSON array of corrections needed:")
        appendLine("[{\"index\":0,\"category\":\"correctCat\",\"amount\":50.0,\"note\":\"fixed note\"}]")
        appendLine("Omit fields that don't need changes. If all correct, reply with [].")
    }

    val body = JSONObject().apply {
        put("model", model)
        put("messages", JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", "Audit these transactions:\n$txList") })
        })
        put("temperature", 0.1)
        put("max_tokens", 2000)
    }

    val url = URL(apiUrl)
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Authorization", "Bearer $apiKey")
    conn.doOutput = true
    conn.connectTimeout = 30000
    conn.readTimeout = 60000

    conn.outputStream.use { os ->
        OutputStreamWriter(os, Charsets.UTF_8).use { it.write(body.toString()) }
    }

    val responseText = if (conn.responseCode in 200..299) {
        conn.inputStream.bufferedReader().readText()
    } else {
        val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
        throw Exception(err ?: "API error ${conn.responseCode}")
    }

    val json = JSONObject(responseText)
    val content = json.getJSONArray("choices")
        .getJSONObject(0)
        .getJSONObject("message")
        .getString("content")

    parseAuditResponse(content, expenseTxs)
}

private fun parseAuditResponse(content: String, transactions: List<Transaction>): List<Pair<Long, String>> {
    val jsonMatch = Regex("""\[[\s\S]*?]""").find(content.trim()) ?: return emptyList()
    return try {
        val arr = JSONArray(jsonMatch.value)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            val idx = obj.optInt("index", -1)
            if (idx !in transactions.indices) return@mapNotNull null
            val newCat = obj.optString("category", "").trim()
            if (newCat in VALID_CATEGORIES && transactions[idx].category != newCat) {
                Pair(transactions[idx].id, newCat)
            } else null
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun calculateBalance(
    targetDate: Long,
    transactions: List<Transaction>,
    anchorDate: Long,
    anchorAmount: Double
): Double {
    val rangeStart = minOf(targetDate, anchorDate)
    val rangeEnd = maxOf(targetDate, anchorDate)
    val txsInRange = transactions.filter { it.time in rangeStart..rangeEnd }
    val netChange = txsInRange.sumOf { tx ->
        when (tx.type) {
            "income" -> tx.amount
            "expense" -> -tx.amount
            else -> 0.0
        }
    }
    return if (targetDate >= anchorDate) anchorAmount + netChange
    else anchorAmount - netChange
}

private const val DAY_MS = 24 * 60 * 60 * 1000L
private data class CategoryTotal(val category: String, val amount: Double, val color: Color)
internal data class CalendarCell(val day: Int, val key: String)

private const val EXCEL_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private val AppBg = Color(0xFFFFF8E7)
private val CardBg = Color(0xFFFFFFFF)
private val Ink = Color(0xFF252735)
private val Muted = Color(0xFF74768A)
private val Pink = Color(0xFFFF3D8B)
private val Orange = Color(0xFFFF8A00)
private val Yellow = Color(0xFFFFD600)
private val Green = Color(0xFF00C853)
private val Cyan = Color(0xFF00D5FF)
private val Blue = Color(0xFF2979FF)
private val Purple = Color(0xFF9C27FF)
private val Sun = Color(0xFFFFF176)
private val PieColors = listOf(Pink, Blue, Yellow, Green, Purple, Orange, Cyan, Color(0xFFFF5E3A), Color(0xFF64DD17))
