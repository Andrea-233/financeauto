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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeauto.data.remote.SupabaseManager
import com.financeauto.data.remote.UpdateManager
import com.financeauto.data.remote.UserProfile
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    profile: UserProfile,
    supabaseManager: SupabaseManager,
    onLogout: () -> Unit,
    onProfileUpdated: () -> Unit,
    onDismiss: () -> Unit,
    onAdminPanel: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentProfile by remember(profile) { mutableStateOf(profile) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var showChangeNickname by remember { mutableStateOf(false) }
    var showChangePhone by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }

    // 每次打开个人中心时从服务器刷新订阅
    LaunchedEffect(Unit) {
        supabaseManager.fetchSubscription().fold(
            onSuccess = {
                currentProfile = it
                onProfileUpdated()
            },
            onFailure = { /* 网络错误静默忽略，用缓存值 */ }
        )
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFFFF8E7))) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("返回", color = Color(0xFF74768A)) }
                Text("个人中心", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF252735))
                TextButton(onClick = {
                    scope.launch {
                        checkingUpdate = true
                        val updateManager = UpdateManager(context)
                        val result = updateManager.checkForUpdate()
                        checkingUpdate = false
                        result.fold(
                            onSuccess = { info ->
                                if (info != null) {
                                    showUpdateDialog(context, info, updateManager, scope)
                                } else {
                                    Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onFailure = {
                                Toast.makeText(context, "检查更新失败：${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }) { Text("检查更新", color = Color(0xFF2979FF)) }
            }

            Spacer(Modifier.height(24.dp))

            // Avatar placeholder
            Box(Modifier. size(80.dp).clip(CircleShape).background(Color(0xFF2979FF)), contentAlignment = Alignment.Center) {
                Text(currentProfile.nickname.take(1), color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium)
            }

            Spacer(Modifier.height(12.dp))
            Text(currentProfile.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF252735))
            Text(currentProfile.phone, color = Color(0xFF74768A), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            // Membership card
            MembershipCard(profile = currentProfile)

            Spacer(Modifier.height(8.dp))

            // Admin entry
            if (supabaseManager.isAdmin) {
                Surface(
                    Modifier.fillMaxWidth().clickable { onAdminPanel() },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF5856D6).copy(alpha = 0.1f)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("管理员面板", Modifier.weight(1f), color = Color(0xFF5856D6),
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(">", color = Color(0xFF5856D6))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(24.dp))

            // Status message
            statusMessage?.let {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    color = if (it.contains("成功")) Color(0xFF00C853).copy(alpha = 0.12f) else Color(0xFFFF3D8B).copy(alpha = 0.12f)) {
                    Text(it, Modifier.padding(12.dp), color = Color(0xFF252735), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
            }

            // Profile options
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 1.dp) {
                Column {
                    ProfileItem("修改昵称", "更改显示名称") { showChangeNickname = true }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color(0xFFF0F0F3))
                    ProfileItem("修改手机号", "更换登录手机号") { showChangePhone = true }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color(0xFFF0F0F3))
                    ProfileItem("修改密码", "更新账户密码") { showChangePassword = true }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Check update button
            Surface(
                Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        checkingUpdate = true
                        val updateManager = UpdateManager(context)
                        val result = updateManager.checkForUpdate()
                        checkingUpdate = false
                        result.fold(
                            onSuccess = { info ->
                                if (info != null) {
                                    showUpdateDialog(context, info, updateManager, scope)
                                } else {
                                    Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onFailure = {
                                Toast.makeText(context, "检查更新失败", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("检查更新", Modifier.weight(1f), color = Color(0xFF252735), style = MaterialTheme.typography.bodyLarge)
                    if (checkingUpdate) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF2979FF))
                    else Text(">", color = Color(0xFFB0B0B8))
                }
            }

            Spacer(Modifier.height(24.dp))

            // Logout
            Button(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(Color(0xFFFF3D8B), Color.White),
                shape = RoundedCornerShape(10.dp)
            ) { Text("退出登录", fontWeight = FontWeight.Bold) }
        }
    }

    // Change Nickname Dialog
    if (showChangeNickname) {
        ChangeNicknameDialog(
            current = currentProfile.nickname,
            onSave = { newName ->
                scope.launch {
                    supabaseManager.updateNickname(newName).fold(
                        onSuccess = {
                            currentProfile = currentProfile.copy(nickname = newName)
                            statusMessage = "昵称修改成功"
                            onProfileUpdated()
                        },
                        onFailure = { statusMessage = "修改失败：${it.message}" }
                    )
                }
            },
            onDismiss = { showChangeNickname = false }
        )
    }

    // Change Phone Dialog
    if (showChangePhone) {
        ChangePhoneDialog(
            current = currentProfile.phone,
            onSave = { newPhone, pwd ->
                scope.launch {
                    supabaseManager.updatePhone(newPhone, pwd).fold(
                        onSuccess = {
                            currentProfile = currentProfile.copy(phone = newPhone)
                            statusMessage = "手机号修改成功"
                            onProfileUpdated()
                        },
                        onFailure = { statusMessage = "修改失败：${it.message}" }
                    )
                }
            },
            onDismiss = { showChangePhone = false }
        )
    }

    // Change Password Dialog
    if (showChangePassword) {
        ChangePasswordDialog(
            onSave = { newPwd ->
                scope.launch {
                    supabaseManager.changePassword(newPwd).fold(
                        onSuccess = { statusMessage = "密码修改成功，下次登录请使用新密码" },
                        onFailure = { statusMessage = "修改失败：${it.message}" }
                    )
                }
            },
            onDismiss = { showChangePassword = false }
        )
    }

    // Logout Confirm
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录", fontWeight = FontWeight.Bold, color = Color(0xFF252735)) },
            text = { Text("确定要退出登录吗？退出后交易数据仍保留在本地。", color = Color(0xFF74768A)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    supabaseManager.logout()
                    onLogout()
                }) { Text("退出", color = Color(0xFFFF3D8B)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消", color = Color(0xFF74768A)) }
            }
        )
    }
}

@Composable
private fun MembershipCard(profile: UserProfile) {
    val isExpired = profile.isExpired
    val daysRemaining = profile.daysRemaining
    val isPermanent = profile.planType == "permanent"

    val bgColor = when {
        isPermanent -> Color(0xFFFFD600).copy(alpha = 0.12f)
        isExpired -> Color(0xFFFF3D8B).copy(alpha = 0.08f)
        daysRemaining <= 7 -> Color(0xFFFF8A00).copy(alpha = 0.1f)
        else -> Color(0xFF00C853).copy(alpha = 0.08f)
    }
    val accentColor = when {
        isPermanent -> Color(0xFFC8A600)
        isExpired -> Color(0xFFFF3D8B)
        daysRemaining <= 7 -> Color(0xFFFF8A00)
        else -> Color(0xFF00C853)
    }
    val statusText = when {
        isPermanent -> "永久有效"
        isExpired -> "已过期"
        daysRemaining <= 7 -> "即将到期"
        else -> "有效"
    }

    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bgColor
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("会员状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF252735))
                Surface(shape = RoundedCornerShape(8.dp), color = accentColor.copy(alpha = 0.15f)) {
                    Text(statusText, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = accentColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("套餐类型", color = Color(0xFF74768A), style = MaterialTheme.typography.bodySmall)
                Text(profile.planLabel, color = Color(0xFF252735), fontWeight = FontWeight.Medium)
            }

            if (!isPermanent) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("到期时间", color = Color(0xFF74768A), style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (profile.expiryDate > 0L) formatExpiryDate(profile.expiryDate) else "未知",
                        color = Color(0xFF252735), fontWeight = FontWeight.Medium
                    )
                }
                if (daysRemaining > 0 && !isExpired) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("剩余天数", color = Color(0xFF74768A), style = MaterialTheme.typography.bodySmall)
                        Text("${daysRemaining} 天", color = accentColor, fontWeight = FontWeight.Bold)
                    }
                }
                if (isExpired && profile.expiryDate > 0L) {
                    Text("会员已过期，部分功能可能受限。请联系管理员续费。",
                        color = Color(0xFFFF3D8B), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun formatExpiryDate(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
    return sdf.format(java.util.Date(millis))
}

@Composable
private fun ProfileItem(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(Modifier.clickable(onClick = onClick), color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color(0xFF252735), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Color(0xFF74768A), style = MaterialTheme.typography.bodySmall)
            }
            Text(">", color = Color(0xFFB0B0B8))
        }
    }
}

@Composable
private fun ChangeNicknameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改昵称", fontWeight = FontWeight.Bold, color = Color(0xFF252735)) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it },
                label = { Text("新昵称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (value.trim().isNotBlank()) onSave(value.trim()) }) { Text("保存", color = Color(0xFF2979FF)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF74768A)) } }
    )
}

@Composable
private fun ChangePhoneDialog(current: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var phone by remember { mutableStateOf(current) }
    var pwd by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改手机号", fontWeight = FontWeight.Bold, color = Color(0xFF252735)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text("新手机号") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                OutlinedTextField(value = pwd, onValueChange = { pwd = it },
                    label = { Text("当前密码") }, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (phone.trim().matches(Regex("^1[3-9]\\d{9}$")) && pwd.length >= 6) onSave(phone.trim(), pwd)
            }) { Text("保存", color = Color(0xFF2979FF)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF74768A)) } }
    )
}

@Composable
private fun ChangePasswordDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码", fontWeight = FontWeight.Bold, color = Color(0xFF252735)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = newPwd, onValueChange = { newPwd = it; error = null },
                    label = { Text("新密码") }, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = confirmPwd, onValueChange = { confirmPwd = it; error = null },
                    label = { Text("确认新密码") }, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = confirmPwd.isNotEmpty() && confirmPwd != newPwd)
                if (error != null) Text(error!!, color = Color(0xFFFF3D8B), style = MaterialTheme.typography.bodySmall)
                Text("通过已登录状态验证身份，无需输入旧密码", color = Color(0xFFB0B0B8), style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    newPwd.length < 6 -> error = "新密码至少需要6位"
                    newPwd != confirmPwd -> error = "两次密码输入不一致"
                    else -> onSave(newPwd)
                }
            }) { Text("保存", color = Color(0xFF2979FF)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF74768A)) } }
    )
}

// Show update available dialog
private fun showUpdateDialog(
    context: android.content.Context,
    info: com.financeauto.data.remote.UpdateInfo,
    updateManager: UpdateManager,
    scope: kotlinx.coroutines.CoroutineScope
) {
    // Use a simple Toast + direct download since we can't launch a Compose dialog easily from here
    Toast.makeText(context, "发现新版本 ${info.versionName}\n${info.changelog}\n正在下载...", Toast.LENGTH_LONG).show()
    scope.launch {
        updateManager.downloadAndInstall(info.downloadUrl)
    }
}
