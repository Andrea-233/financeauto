package com.financeauto.ui.screen

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financeauto.data.model.Transaction
import com.financeauto.viewmodel.TransactionViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CalendarScreen(vm: TransactionViewModel = viewModel()) {
    val records by vm.transactions.collectAsState(initial = emptyList())
    val sorted = remember(records) { records.sortedByDescending { it.time } }

    val initialMonth = remember(sorted) {
        monthStart(sorted.maxByOrNull { it.time }?.time ?: System.currentTimeMillis())
    }
    var visibleMonth by remember(sorted) { mutableStateOf(initialMonth) }
    var selectedDay by remember(sorted, visibleMonth) { mutableStateOf(dayKey(visibleMonth.timeInMillis)) }
    val dayGroups = remember(sorted) { sorted.groupBy { dayKey(it.time) } }
    val dayRecords = dayGroups[selectedDay].orEmpty().sortedByDescending { it.time }

    var yearExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    val currentYear = visibleMonth.get(Calendar.YEAR)
    val currentMonth = visibleMonth.get(Calendar.MONTH) + 1
    val yearRange = remember {
        val now = Calendar.getInstance().get(Calendar.YEAR)
        (now - 10..now + 10).toList()
    }
    val monthLabels = listOf("1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月")

    Column(
        Modifier.fillMaxSize().background(Color(0xFFFFF8E7)).navigationBarsPadding()
    ) {
        // Header
        Surface(Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 2.dp) {
            Text(
                "日历视图",
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF252735)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(0.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Year + Month selector
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                TextButton(onClick = { yearExpanded = true }) {
                                    Text("${currentYear}年", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF252735))
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
                                    Text(monthLabels[currentMonth - 1], style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF252735))
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

            // Selected day summary
            item { DaySummaryView(selectedDay, dayRecords) }

            if (dayRecords.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(0.dp)) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text("该日无交易记录", color = Color(0xFF74768A), textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(dayRecords, key = { if (it.id != 0L) it.id else it.hashCode().toLong() }) { tx ->
                    TransactionRow(tx) { /* edit handled by RecordsScreen */ }
                }
            }
        }
    }
}

@Composable
private fun DaySummaryView(
    day: String,
    records: List<Transaction>
) {
    val income = records.filter { it.type == "income" }.sumOf { it.amount }
    val expense = records.filter { it.type == "expense" }.sumOf { it.amount }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF176)), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF252735))
                Text("${records.size} 条记录", style = MaterialTheme.typography.bodySmall, color = Color(0xFF74768A))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("支出 ¥${money(expense)}", color = Color(0xFFFF3D8B), fontWeight = FontWeight.SemiBold)
                Text("收入 ¥${money(income)}", color = Color(0xFF00C853), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    month: Calendar,
    groups: Map<String, List<Transaction>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val cells = remember(month.timeInMillis) { calendarCells(month) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Color(0xFF74768A), style = MaterialTheme.typography.labelSmall)
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
private fun DayCell(
    cell: CalendarCell?,
    records: List<Transaction>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val expense = records.filter { it.type == "expense" }.sumOf { it.amount }
    val income = records.filter { it.type == "income" }.sumOf { it.amount }
    val bg = if (selected) Color(0xFF2979FF) else if (records.isNotEmpty()) Color(0xFFFFD600).copy(alpha = 0.28f) else Color(0xFFFFF8E7)
    val fg = if (selected) Color.White else Color(0xFF252735)
    Surface(
        modifier.height(58.dp).clickable(enabled = cell != null, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = bg
    ) {
        if (cell == null) Spacer(Modifier.height(1.dp))
        else Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(cell.day.toString(), color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (expense > 0) Dot(Color(0xFFFF3D8B))
                if (income > 0) Dot(Color(0xFF00C853))
            }
            if (expense > 0) Text(expense.roundToInt().toString(), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.84f))
        }
    }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(5.dp).background(color, CircleShape))

@Composable
private fun TransactionRow(tx: Transaction, onEdit: () -> Unit) {
    val sign = if (tx.type == "income") "+" else if (tx.type == "neutral") "" else "-"
    val color = when (tx.type) {
        "income" -> Color(0xFF00C853)
        "neutral" -> Color(0xFF74768A)
        else -> Color(0xFFFF3D8B)
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
                Text(formatDateTime(tx.time), style = MaterialTheme.typography.bodySmall, color = Color(0xFF74768A))
            }
            Text("$sign${money(tx.amount)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// ---- Helper Functions ----

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

private fun monthStart(time: Long): Calendar = Calendar.getInstance(Locale.CHINA).apply {
    timeInMillis = time
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}

private fun shiftMonth(source: Calendar, amount: Int): Calendar =
    (source.clone() as Calendar).apply { add(Calendar.MONTH, amount) }

private fun shiftYear(source: Calendar, year: Int): Calendar =
    (source.clone() as Calendar).apply { set(Calendar.YEAR, year) }

private fun dayKey(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(time))

private fun formatDateTime(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(time))

private fun money(amount: Double): String = "¥${String.format(Locale.CHINA, "%.2f", amount)}"

