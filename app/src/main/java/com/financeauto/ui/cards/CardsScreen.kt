package com.financeauto.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financeauto.data.local.entity.CardEntity
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(vm: CardsViewModel = viewModel()) {
    val cards by vm.cards.collectAsState(initial = emptyList())
    var typeFilter by remember { mutableStateOf("ALL") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var expandedCard by remember { mutableStateOf<Long?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filtered = remember(cards, typeFilter, statusFilter) {
        cards.filter { card ->
            (typeFilter == "ALL" || card.cardType == typeFilter) &&
            (statusFilter == "ALL" || card.status == statusFilter)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8E7))
            .navigationBarsPadding()
    ) {
        // Header + add button
        Surface(Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("卡片中心", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = Color(0xFF252735))
                Button(onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(Color(0xFF2979FF), Color.White),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Text("+ 创建卡片", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Manual card creation dialog
        if (showCreateDialog) {
            CreateCardDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { card ->
                    vm.addCard(card)
                    showCreateDialog = false
                }
            )
        }

        // Filter chips - card type
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip("全部", typeFilter == "ALL") { typeFilter = "ALL" }
            FilterChip("决策", typeFilter == "DECISION") { typeFilter = "DECISION" }
            FilterChip("洞察", typeFilter == "INSIGHT") { typeFilter = "INSIGHT" }
            FilterChip("打卡", typeFilter == "CHECKIN") { typeFilter = "CHECKIN" }
        }

        // Status filter
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip("全部状态", statusFilter == "ALL") { statusFilter = "ALL" }
            FilterChip("活跃中", statusFilter == "ACTIVE") { statusFilter = "ACTIVE" }
            FilterChip("已完成", statusFilter == "COMPLETED") { statusFilter = "COMPLETED" }
            FilterChip("已忽略", statusFilter == "DISMISSED") { statusFilter = "DISMISSED" }
        }

        // Card count
        Text(
            "共 ${filtered.size} 张卡片",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF74768A)
        )

        // Card list
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无卡片", color = Color(0xFF74768A))
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { card ->
                    CardItem(
                        card = card,
                        expanded = expandedCard == card.id,
                        onToggle = {
                            expandedCard = if (expandedCard == card.id) null else card.id
                        },
                        onComplete = { vm.completeCard(card.id) },
                        onDismiss = { vm.dismissCard(card.id) },
                        onDelete = { vm.deleteCard(card.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFF2979FF) else Color.White,
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = if (selected) Color.White else Color(0xFF252735),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CardItem(
    card: CardEntity,
    expanded: Boolean,
    onToggle: () -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
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
    val statusLabel = when (card.status) {
        "ACTIVE" -> "活跃中"
        "COMPLETED" -> "已完成"
        "DISMISSED" -> "已忽略"
        else -> card.status
    }
    val statusColor = when (card.status) {
        "ACTIVE" -> Color(0xFFFF9500)
        "COMPLETED" -> Color(0xFF34C759)
        "DISMISSED" -> Color(0xFF8E8E93)
        else -> Color(0xFF74768A)
    }

    val jsonData = remember(card.jsonData) {
        try { JSONObject(card.jsonData) } catch (_: Exception) { JSONObject() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header row: type chip + status
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Colored dot
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(typeColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = typeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        statusLabel,
                        Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Title
            Text(
                card.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF252735),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (card.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    card.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF74768A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Expanded detail
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF0F0F0))
                Spacer(Modifier.height(12.dp))

                // Type-specific fields from jsonData
                when (card.cardType) {
                    "DECISION" -> {
                        val targetAmount = jsonData.optDouble("targetAmount", 0.0)
                        val targetDate = jsonData.optString("targetDate", "")
                        val decision = jsonData.optString("decision", "")
                        if (targetAmount > 0) {
                            Text("目标金额：¥$targetAmount", color = Color(0xFF252735), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (targetDate.isNotBlank()) {
                            Text("目标日期：$targetDate", color = Color(0xFF252735), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (decision.isNotBlank()) {
                            Text("决策：$decision", color = Color(0xFF74768A), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    "INSIGHT" -> {
                        val pattern = jsonData.optString("pattern", "")
                        val action = jsonData.optString("actionSuggestion", "")
                        if (pattern.isNotBlank()) {
                            Text("模式：$pattern", color = Color(0xFF252735), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (action.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("建议：$action", color = Color(0xFF5856D6), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                    "CHECKIN" -> {
                        val freq = jsonData.optString("frequency", "")
                        val target = jsonData.optInt("target", 0)
                        val progress = jsonData.optInt("currentProgress", 0)
                        val freqLabel = when (freq) {
                            "DAILY" -> "每天"
                            "WEEKLY" -> "每周"
                            "MONTHLY" -> "每月"
                            else -> ""
                        }
                        if (target > 0) {
                            Text("目标：$freqLabel $target 次", color = Color(0xFF252735), style = MaterialTheme.typography.bodyMedium)
                            Text("当前进度：$progress / $target", color = Color(0xFF34C759), fontWeight = FontWeight.Medium)
                            // Progress bar
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (target > 0) progress.toFloat() / target else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF34C759),
                                trackColor = Color(0xFFF0F0F0)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Action buttons
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (card.status == "ACTIVE") {
                        TextButton(onClick = onComplete) {
                            Text("已完成", color = Color(0xFF34C759), fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onDismiss) {
                            Text("忽略", color = Color(0xFF8E8E93))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDelete) {
                        Text("删除", color = Color(0xFFFF3D8B))
                    }
                }
            }

            // Source message preview
            if (card.sourceMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "来源：${card.sourceMessage.take(40)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8E8E93),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCardDialog(
    onDismiss: () -> Unit,
    onCreate: (CardEntity) -> Unit
) {
    val types = listOf("DECISION" to "决策", "BUDGET" to "预算", "INSIGHT" to "洞察", "CHECKIN" to "打卡", "SAVINGS" to "存钱")
    val freqs = listOf("DAILY" to "每天", "WEEKLY" to "每周", "MONTHLY" to "每月")
    val categories = listOf("餐饮美食", "网购购物", "日用百货", "校园生活", "交通出行", "生活服务", "充值缴费", "其他")

    var selType by remember { mutableStateOf("DECISION") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var decision by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var trackCategory by remember { mutableStateOf("其他") }
    var habitName by remember { mutableStateOf("") }
    var selFreq by remember { mutableStateOf("DAILY") }
    var targetNum by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var actionSug by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建卡片", fontWeight = FontWeight.Bold, color = Color(0xFF252735)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { (t, label) ->
                        Surface(
                            modifier = Modifier.clickable { selType = t },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selType == t) Color(0xFF2979FF) else Color(0xFFF0F0F0)
                        ) {
                            Text(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = if (selType == t) Color.White else Color(0xFF252735),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("描述") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                when (selType) {
                    "DECISION" -> {
                        OutlinedTextField(value = decision, onValueChange = { decision = it }, label = { Text("决定内容") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        OutlinedTextField(value = targetAmount, onValueChange = { targetAmount = it }, label = { Text("目标金额(可选)") }, singleLine = true)
                    }
                    "BUDGET", "SAVINGS" -> {
                        OutlinedTextField(value = decision, onValueChange = { decision = it }, label = { Text("决定/说明") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        OutlinedTextField(value = targetAmount, onValueChange = { targetAmount = it }, label = { Text("目标金额 ¥") }, singleLine = true)
                        Text("监控类别", style = MaterialTheme.typography.labelSmall, color = Color(0xFF74768A))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            categories.take(4).forEach { cat ->
                                Surface(modifier = Modifier.clickable { trackCategory = cat }, shape = RoundedCornerShape(6.dp),
                                    color = if (trackCategory == cat) Color(0xFF2979FF) else Color(0xFFF0F0F0)) {
                                    Text(cat, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = if (trackCategory == cat) Color.White else Color(0xFF252735),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    "INSIGHT" -> {
                        OutlinedTextField(value = pattern, onValueChange = { pattern = it }, label = { Text("发现的消费模式") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        OutlinedTextField(value = actionSug, onValueChange = { actionSug = it }, label = { Text("改进建议") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    }
                    "CHECKIN" -> {
                        OutlinedTextField(value = habitName, onValueChange = { habitName = it }, label = { Text("习惯名称") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            freqs.forEach { (f, label) ->
                                Surface(modifier = Modifier.clickable { selFreq = f }, shape = RoundedCornerShape(8.dp),
                                    color = if (selFreq == f) Color(0xFF2979FF) else Color(0xFFF0F0F0)) {
                                    Text(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = if (selFreq == f) Color.White else Color(0xFF252735),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        OutlinedTextField(value = targetNum, onValueChange = { targetNum = it }, label = { Text("目标次数(可选)") }, singleLine = true)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isBlank()) return@Button
                val json = JSONObject().apply {
                    when (selType) {
                        "DECISION" -> { put("decision", decision); targetAmount.toDoubleOrNull()?.let { put("targetAmount", it) } }
                        "BUDGET", "SAVINGS" -> { put("decision", decision); targetAmount.toDoubleOrNull()?.let { put("targetAmount", it) }; put("trackCategory", trackCategory) }
                        "INSIGHT" -> { put("pattern", pattern); put("actionSuggestion", actionSug) }
                        "CHECKIN" -> { put("frequency", selFreq); targetNum.toIntOrNull()?.let { put("target", it) }; put("currentProgress", 0) }
                    }
                }
                onCreate(CardEntity(cardType = selType, title = title, description = desc, jsonData = json.toString(), status = "ACTIVE"))
            }, colors = ButtonDefaults.buttonColors(Color(0xFF2979FF), Color.White), shape = RoundedCornerShape(10.dp)) {
                Text("创建")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF74768A)) } }
    )
}
