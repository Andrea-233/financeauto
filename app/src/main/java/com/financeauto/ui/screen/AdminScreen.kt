package com.financeauto.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeauto.data.remote.SubscriptionInfo
import com.financeauto.data.remote.SupabaseManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    supabaseManager: SupabaseManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<SubscriptionInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingUser by remember { mutableStateOf<SubscriptionInfo?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            loadUsers(supabaseManager) { users = it; loading = false; error = null }
        }
    }

    // Edit dialog
    editingUser?.let { sub ->
        EditSubscriptionDialog(
            sub = sub,
            onSave = { targetPhone, expiryDate, planType ->
                scope.launch {
                    supabaseManager.adminSetSubscription(targetPhone, expiryDate, planType).fold(
                        onSuccess = {
                            Toast.makeText(context, "已更新 ${sub.nickname} 的订阅", Toast.LENGTH_SHORT).show()
                            editingUser = null
                            loadUsers(supabaseManager) { users = it }
                        },
                        onFailure = {
                            Toast.makeText(context, "更新失败：${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
            onDelete = { targetPhone ->
                scope.launch {
                    supabaseManager.adminDeleteSubscription(targetPhone).fold(
                        onSuccess = {
                            Toast.makeText(context, "已删除 ${sub.nickname} 的订阅", Toast.LENGTH_SHORT).show()
                            editingUser = null
                            loadUsers(supabaseManager) { users = it }
                        },
                        onFailure = {
                            Toast.makeText(context, "删除失败：${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
            onDismiss = { editingUser = null }
        )
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFFFF8E7))) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Surface(Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text("返回", color = Color(0xFF74768A)) }
                    Spacer(Modifier.weight(1f))
                    Text("会员管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF252735))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        loading = true
                        scope.launch { loadUsers(supabaseManager) { users = it; loading = false } }
                    }) { Text("刷新", color = Color(0xFF2979FF)) }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF2979FF))
                        Spacer(Modifier.height(12.dp))
                        Text("加载中...", color = Color(0xFF74768A))
                    }
                }
            } else if (error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = Color(0xFFFF3D8B), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            loading = true
                            scope.launch { loadUsers(supabaseManager) { users = it; loading = false } }
                        }) { Text("重试") }
                    }
                }
            } else if (users.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无用户数据", color = Color(0xFF74768A))
                }
            } else {
                // Summary
                val activeCount = users.count { !it.isExpired && it.planType != "free" }
                val expiredCount = users.count { it.isExpired }
                Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), color = Color.White) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatChip("总用户", "${users.size}", Color(0xFF2979FF))
                        StatChip("有效会员", "$activeCount", Color(0xFF00C853))
                        StatChip("已过期", "$expiredCount", Color(0xFFFF3D8B))
                    }
                }

                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(users, key = { it.phone }) { sub ->
                        SubscriptionCard(sub) { editingUser = sub }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(sub: SubscriptionInfo, onClick: () -> Unit) {
    val isExpired = sub.isExpired
    val accColor = when {
        sub.planType == "permanent" -> Color(0xFFC8A600)
        isExpired -> Color(0xFFFF3D8B)
        sub.daysRemaining <= 7 && sub.daysRemaining > 0 -> Color(0xFFFF8A00)
        else -> Color(0xFF00C853)
    }
    val statusText = when {
        sub.planType == "permanent" -> "永久"
        isExpired -> "已过期"
        sub.daysRemaining <= 7 && sub.daysRemaining > 0 -> "${sub.daysRemaining}天"
        else -> "${sub.daysRemaining}天"
    }

    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sub.nickname.ifBlank { "未设置昵称" }, fontWeight = FontWeight.Bold, color = Color(0xFF252735),
                        style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = accColor.copy(alpha = 0.12f)) {
                        Text(sub.planLabel, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = accColor, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(sub.phone, color = Color(0xFF74768A), style = MaterialTheme.typography.bodySmall)
                if (sub.expiryDate > 0L && sub.planType != "permanent") {
                    Text("到期：${formatExpiry(sub.expiryDate)}",
                        color = if (isExpired) Color(0xFFFF3D8B) else Color(0xFF74768A),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Surface(shape = RoundedCornerShape(8.dp), color = accColor.copy(alpha = 0.12f)) {
                Text(statusText, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = accColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(4.dp))
            Text(">", color = Color(0xFFB0B0B8))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSubscriptionDialog(
    sub: SubscriptionInfo,
    onSave: (String, Long, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(
        if (sub.expiryDate > 0L) sub.expiryDate else System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
    ) }
    var showDatePicker by remember { mutableStateOf(false) }
    var planType by remember { mutableStateOf(sub.planType) }
    var planExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selectedDate = normalizeDate(it) }
                    showDatePicker = false
                }) { Text("确定", color = Color(0xFF2979FF)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消", color = Color(0xFF74768A)) }
            }
        ) { DatePicker(state = state) }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除", fontWeight = FontWeight.Bold, color = Color(0xFF252735)) },
            text = { Text("确定要删除 ${sub.nickname} 的订阅吗？\n删除后将无法恢复。", color = Color(0xFF74768A)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(sub.phone)
                }) { Text("删除", color = Color(0xFFFF3D8B)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = Color(0xFF74768A)) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("修改订阅", fontWeight = FontWeight.Bold, color = Color(0xFF252735))
                Text("${sub.nickname} (${sub.phone})", color = Color(0xFF74768A),
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Plan type dropdown
                ExposedDropdownMenuBox(
                    expanded = planExpanded,
                    onExpandedChange = { planExpanded = it }
                ) {
                    OutlinedTextField(
                        value = SupabaseManager.planLabel(planType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("套餐类型") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = planExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = planExpanded,
                        onDismissRequest = { planExpanded = false }
                    ) {
                        SupabaseManager.PLAN_OPTIONS.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { planType = value; planExpanded = false }
                            )
                        }
                    }
                }

                // Date button
                OutlinedTextField(
                    value = formatExpiry(selectedDate),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("到期日期") },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                )
                TextButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("选择日期", color = Color(0xFF2979FF))
                }

                // Quick buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickDateButton("+7天", Modifier.weight(1f)) {
                        selectedDate = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
                    }
                    QuickDateButton("+30天", Modifier.weight(1f)) {
                        selectedDate = System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000L
                    }
                    QuickDateButton("+1年", Modifier.weight(1f)) {
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.YEAR, 1)
                        selectedDate = cal.timeInMillis
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("删除订阅", color = Color(0xFFFF3D8B))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF74768A)) }
                TextButton(onClick = {
                    onSave(sub.phone, selectedDate, planType)
                }) { Text("保存", color = Color(0xFF2979FF)) }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun QuickDateButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(Color(0xFFF0F0F5), Color(0xFF252735)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, color = Color(0xFF74768A), style = MaterialTheme.typography.labelSmall)
    }
}

private suspend fun loadUsers(
    supabaseManager: SupabaseManager,
    onResult: (List<SubscriptionInfo>) -> Unit
) {
    supabaseManager.adminListSubscriptions().fold(
        onSuccess = { onResult(it) },
        onFailure = { onResult(emptyList()) }
    )
}

private fun formatExpiry(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    return sdf.format(Date(millis))
}

private fun normalizeDate(millis: Long): Long {
    val cal = Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
    return cal.timeInMillis
}
