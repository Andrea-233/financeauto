package com.financeauto.ui.screen

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeauto.data.remote.SupabaseManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val supabaseManager = remember { SupabaseManager(context) }
    var tab by remember { mutableStateOf(0) } // 0 = login, 1 = register
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8E7))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            Text("财务管家", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color(0xFF252735))
            Spacer(Modifier.height(8.dp))
            Text("智能记账 · 消费分析 · 安全可靠", color = Color(0xFF74768A), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(40.dp))

            // Tab switcher
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TabButton("登录", tab == 0, Modifier.weight(1f)) { tab = 0 }
                    TabButton("注册", tab == 1, Modifier.weight(1f)) { tab = 1 }
                }
            }

            Spacer(Modifier.height(24.dp))

            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                    if (tab == 1) {
                        OutlinedTextField(
                            value = nickname, onValueChange = { nickname = it },
                            label = { Text("用户昵称") }, modifier = Modifier.fillMaxWidth(),
                            singleLine = true, enabled = !loading
                        )
                    }

                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text("手机号") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, enabled = !loading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("密码") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, enabled = !loading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    if (tab == 1) {
                        OutlinedTextField(
                            value = confirmPassword, onValueChange = { confirmPassword = it },
                            label = { Text("确认密码") }, modifier = Modifier.fillMaxWidth(),
                            singleLine = true, enabled = !loading,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = confirmPassword.isNotEmpty() && confirmPassword != password
                        )
                    }

                    if (error != null) {
                        Text(error!!, color = Color(0xFFFF3D8B), style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            val validationError = validate(tab, phone, password, confirmPassword, nickname)
                            if (validationError != null) {
                                error = validationError
                                return@Button
                            }
                            error = null
                            loading = true
                            scope.launch {
                                val result = if (tab == 0) {
                                    supabaseManager.login(phone, password)
                                } else {
                                    supabaseManager.register(phone, password, nickname)
                                }
                                loading = false
                                result.fold(
                                    onSuccess = { onLoginSuccess() },
                                    onFailure = { error = it.message ?: "操作失败" }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(Color(0xFF2979FF), Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text(if (tab == 0) "登 录" else "注 册", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (tab == 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Text("还没有账号？", color = Color(0xFF74768A), style = MaterialTheme.typography.bodySmall)
                            Text("立即注册", color = Color(0xFF2979FF), style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(enabled = !loading) { tab = 1; error = null })
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Text("已有账号？", color = Color(0xFF74768A), style = MaterialTheme.typography.bodySmall)
                            Text("立即登录", color = Color(0xFF2979FF), style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(enabled = !loading) { tab = 0; error = null })
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("首次使用需联网注册 · 数据安全存储于 Supabase 云端", color = Color(0xFF74768A), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }

        // Version text at bottom
        Text(
            "v1.0", Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            color = Color(0xFFB0B0B8), style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF2979FF) else Color.Transparent,
            contentColor = if (selected) Color.White else Color(0xFF74768A)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun validate(
    tab: Int,
    phone: String,
    password: String,
    confirmPassword: String,
    nickname: String
): String? {
    val phoneTrim = phone.trim()
    if (phoneTrim.isEmpty()) return "请输入手机号"
    if (!phoneTrim.matches(Regex("^1[3-9]\\d{9}$"))) return "请输入有效的11位手机号"
    if (password.length < 6) return "密码至少需要6位"
    if (tab == 1) {
        if (nickname.trim().isEmpty()) return "请输入昵称"
        if (confirmPassword != password) return "两次输入的密码不一致"
    }
    return null
}
