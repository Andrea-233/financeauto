package com.financeauto.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financeauto.data.model.Transaction
import com.financeauto.viewmodel.TransactionViewModel
import java.text.SimpleDateFormat
import java.util.*

data class AmountRange(val label: String, val min: Double?, val max: Double?)

private val amountRanges = listOf(
    AmountRange("全部金额", null, null),
    AmountRange("少于 ¥100", null, 99.99),
    AmountRange("¥100 - ¥500", 100.0, 500.0),
    AmountRange("¥500 - ¥1,000", 500.0, 1000.0),
    AmountRange("¥1,000 - ¥5,000", 1000.0, 5000.0),
    AmountRange("超过 ¥5,000", 5000.0, null)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(vm: TransactionViewModel = viewModel()) {
    val records by vm.transactions.collectAsState(initial = emptyList())
    val sorted = remember(records) { records.sortedByDescending { it.time } }
    var filterType by remember { mutableStateOf<String?>(null) }
    var filterCategory by remember { mutableStateOf<String?>(null) }
    var filterAmount by remember { mutableStateOf<AmountRange?>(null) }
    var editing by remember { mutableStateOf<Transaction?>(null) }

    // Dropdown expanded states
    var catExpanded by remember { mutableStateOf(false) }
    var amtExpanded by remember { mutableStateOf(false) }

    val categories = remember(sorted) {
        sorted.map { it.category }.distinct().sorted()
    }

    val amtRange = filterAmount
    val filtered = remember(sorted, filterType, filterCategory, filterAmount) {
        sorted.filter { tx ->
            (filterType == null || tx.type == filterType) &&
            (filterCategory == null || tx.category == filterCategory) &&
            (amtRange == null || amtRange.min == null || tx.amount >= amtRange.min) &&
            (amtRange == null || amtRange.max == null || tx.amount <= amtRange.max)
        }
    }

    val totalExpense = filtered.filter { it.type == "expense" }.sumOf { it.amount }
    val totalIncome = filtered.filter { it.type == "income" }.sumOf { it.amount }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFFFF8E7)).navigationBarsPadding()
    ) {
        // Header
        Surface(Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("交易记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF252735))
                Text("${filtered.size} 条", color = Color(0xFF74768A), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Type filter row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipSmall("全部类型", filterType == null) { filterType = null }
            FilterChipSmall("支出", filterType == "expense") { filterType = if (filterType == "expense") null else "expense" }
            FilterChipSmall("收入", filterType == "income") { filterType = if (filterType == "income") null else "income" }
        }

        // Category + Amount dropdown filters
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category dropdown
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { catExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (filterCategory != null) Color(0xFF2979FF) else Color(0xFF252735)
                    )
                ) {
                    Text(
                        filterCategory ?: "全部类别",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("全部类别", fontWeight = if (filterCategory == null) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { filterCategory = null; catExpanded = false }
                    )
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat, fontWeight = if (filterCategory == cat) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { filterCategory = if (filterCategory == cat) null else cat; catExpanded = false }
                        )
                    }
                }
            }

            // Amount range dropdown
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { amtExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (filterAmount != null) Color(0xFF2979FF) else Color(0xFF252735)
                    )
                ) {
                    Text(
                        filterAmount?.label ?: "全部金额",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                DropdownMenu(expanded = amtExpanded, onDismissRequest = { amtExpanded = false }) {
                    amountRanges.forEach { range ->
                        DropdownMenuItem(
                            text = { Text(range.label, fontWeight = if (filterAmount == range) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { filterAmount = range; amtExpanded = false }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Stats row
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterStat("支出", totalExpense, Color(0xFFFF3D8B))
                FilterStat("收入", totalIncome, Color(0xFF00C853))
                FilterStat("结余", totalIncome - totalExpense, Color(0xFF2979FF))
            }
        }

        Spacer(Modifier.height(10.dp))

        // Transaction list
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无符合条件的交易记录", color = Color(0xFF74768A))
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { if (it.id != 0L) it.id else it.hashCode().toLong() }) { tx ->
                    TransactionRow(tx) { editing = tx }
                }
            }
        }

        // Edit dialog
        editing?.let { tx ->
            EditTransactionDialog(
                tx = tx,
                onDismiss = { editing = null },
                onSave = { vm.update(it); editing = null },
                onDelete = { vm.deleteById(tx.id); editing = null }
            )
        }
    }
}

@Composable
private fun FilterStat(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF74768A))
        Text("¥${String.format(Locale.CHINA, "%.2f", amount)}",
            fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FilterChipSmall(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFF2979FF) else Color.White,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            color = if (selected) Color.White else Color(0xFF252735),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun TransactionRow(tx: Transaction, onEdit: () -> Unit) {
    val sign = when (tx.type) {
        "income" -> "+"; "neutral" -> ""; else -> "-"
    }
    val color = when (tx.type) {
        "income" -> Color(0xFF00C853); "neutral" -> Color(0xFF74768A); else -> Color(0xFFFF3D8B)
    }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF00D5FF).copy(alpha = 0.15f)) {
                        Text(tx.category, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium, color = Color(0xFF252735))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (tx.type) { "income" -> "收入"; "neutral" -> "中性"; else -> "支出" },
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF74768A)
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(tx.note.ifBlank { "无备注" }, color = Color(0xFF252735), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(tx.time)),
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF74768A)
                )
            }
            Text("$sign¥${f(tx.amount)}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTransactionDialog(
    tx: Transaction, onDismiss: () -> Unit, onSave: (Transaction) -> Unit, onDelete: () -> Unit
) {
    var amount by remember(tx) { mutableStateOf(tx.amount.toString()) }
    var category by remember(tx) { mutableStateOf(tx.category) }
    var note by remember(tx) { mutableStateOf(tx.note) }
    var type by remember(tx) { mutableStateOf(tx.type) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑交易记录", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("类别") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeButtonSmall("支出", type == "expense") { type = "expense" }
                    TypeButtonSmall("收入", type == "income") { type = "income" }
                    TypeButtonSmall("中性", type == "neutral") { type = "neutral" }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.toDoubleOrNull() ?: return@TextButton
                if (amt > 0 && category.isNotBlank()) {
                    onSave(tx.copy(amount = amt, category = category.trim(), note = note.trim(), type = type))
                }
            }) { Text("保存", color = Color(0xFF2979FF)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除", color = Color(0xFFFF3D8B)) }
                TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF74768A)) }
            }
        }
    )
}

@Composable
private fun TypeButtonSmall(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF2979FF) else Color(0xFFF0F0F0)
    ) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (selected) Color.White else Color(0xFF252735), style = MaterialTheme.typography.labelMedium)
    }
}

private fun f(amount: Double): String = String.format(Locale.CHINA, "%.2f", amount)
