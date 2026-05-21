package com.financeauto.ui.screen

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeauto.data.model.Transaction
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class OcrResult(
    val amount: Double,
    val merchant: String,
    val date: Long,
    val type: String,
    val rawText: String
)

@Composable
fun OcrScanner(
    onSave: (Transaction) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf("pick") } // pick | scanning | ai_analyze | confirm
    var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
    var rawText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var scannedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        step = "scanning"
        error = null
        processImage(uri, context, scope,
            onResult = { bitmap, text ->
                scannedBitmap = bitmap
                rawText = text

                // Check if AI is configured
                val prefs = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
                val apiKey = prefs.getString("chat_api_key", "") ?: ""

                if (apiKey.isNotBlank()) {
                    // Go to AI analysis step
                    step = "ai_analyze"
                    scope.launch {
                        val aiResult = analyzeWithAI(text, prefs)
                        if (aiResult != null) {
                            ocrResult = aiResult
                        } else {
                            // AI failed, fall back to regex
                            ocrResult = parseOcrText(text)
                        }
                        step = "confirm"
                    }
                } else {
                    // No AI, use regex directly
                    ocrResult = parseOcrText(text)
                    step = "confirm"
                }
            },
            onError = { err ->
                error = err
                step = "pick"
            }
        )
    }

    when (step) {
        "pick" -> PickDialog(onDismiss, error, imagePicker)

        "scanning" -> LoadingOverlay("正在识别图片文字...")

        "ai_analyze" -> LoadingOverlay("AI 正在分析账单...")

        "confirm" -> {
            ocrResult?.let { result ->
                ConfirmDialog(
                    result = result,
                    onSave = { tx -> onSave(tx) },
                    onRetry = { step = "pick"; ocrResult = null; rawText = ""; error = null },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

// ---- Pick Dialog ----

@Composable
private fun PickDialog(
    onDismiss: () -> Unit,
    error: String?,
    imagePicker: androidx.activity.result.ActivityResultLauncher<String>
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扫描账单", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("拍照或选择支付截图，AI 会自动识别金额、类别、商家、日期。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                if (error != null) Text(error, color = Pink, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(Blue, Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("选择图片", color = Color.White) }
                Text("提示：使用清晰的支付确认页面截图效果最佳。", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Muted) } }
    )
}

// ---- Loading Overlay ----

@Composable
private fun LoadingOverlay(message: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(48.dp), color = Blue, strokeWidth = 4.dp)
                Spacer(Modifier.height(16.dp))
                Text(message, color = Muted)
            }
        }
    }
}

// ---- Confirm Dialog ----

@Composable
private fun ConfirmDialog(
    result: OcrResult,
    onSave: (Transaction) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf(if (result.amount > 0) String.format(Locale.CHINA, "%.2f", result.amount) else "") }
    var category by remember { mutableStateOf(if (result.merchant.isNotBlank()) inferCategorySimple(result.merchant, result.type) else "其他") }
    var note by remember { mutableStateOf(result.merchant) }
    var type by remember { mutableStateOf(result.type) }
    var dateText by remember {
        mutableStateOf(
            if (result.date > 0) SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(result.date))
            else SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        )
    }
    var showRaw by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认交易", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("金额") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, prefix = { Text("¥") }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeButton("支出", type == "expense") { type = "expense" }
                    TypeButton("收入", type == "income") { type = "income" }
                    // Neutral removed - rare for scanned bills
                }
                OutlinedTextField(value = category, onValueChange = { category = it },
                    label = { Text("类别") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = dateText, onValueChange = { dateText = it },
                    label = { Text("日期 (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it },
                    label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "隐藏原始识别文本" else "显示原始识别文本", color = Blue, style = MaterialTheme.typography.labelSmall)
                }
                if (showRaw) {
                    Surface(Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(8.dp), color = AppBg) {
                        Text(result.rawText, Modifier.padding(8.dp).verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (amt <= 0.0) return@Button
                val date = try {
                    SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(dateText)?.time ?: System.currentTimeMillis()
                } catch (_: Exception) { System.currentTimeMillis() }
                onSave(Transaction(amount = amt, type = type, category = category.ifBlank { "其他" }, note = note.trim(), time = date))
            }, enabled = amount.toDoubleOrNull()?.let { it > 0 } == true,
                colors = ButtonDefaults.buttonColors(Blue, Color.White)) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRetry) { Text("重试", color = Muted) }
                TextButton(onClick = onDismiss) { Text("取消", color = Muted) }
            }
        }
    )
}

@Composable
private fun TypeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = if (selected) ButtonDefaults.buttonColors(Blue, Color.White) else ButtonDefaults.buttonColors(AppBg, Ink)
    Button(onClick = onClick, colors = colors, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text(text) }
}

// ---- Image Processing ----

private fun processImage(
    uri: Uri, context: Context, scope: kotlinx.coroutines.CoroutineScope,
    onResult: (Bitmap, String) -> Unit, onError: (String) -> Unit
) {
    scope.launch {
        try {
            val bitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: throw RuntimeException("无法打开图片")
            }
            val text = recognizeText(bitmap)
            if (text.isBlank()) onError("图片中未找到文字，请尝试更清晰的照片。")
            else onResult(bitmap, text)
        } catch (e: Exception) {
            onError(e.message ?: "图片处理失败")
        }
    }
}

private suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    suspendCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { result -> cont.resume(result.text) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}

// ---- AI Analysis ----

private suspend fun analyzeWithAI(rawText: String, prefs: SharedPreferences): OcrResult? = withContext(Dispatchers.IO) {
    try {
        val apiUrl = prefs.getString("chat_api_url", "https://api.deepseek.com/v1/chat/completions") ?: return@withContext null
        val apiKey = prefs.getString("chat_api_key", "") ?: return@withContext null
        val model = prefs.getString("chat_model", "deepseek-chat") ?: "deepseek-chat"

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val body = JSONObject().apply {
            put("model", model)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "你是账单解析助手。从OCR识别的支付文本中提取信息，返回纯JSON。\n" +
                        "类别必须是：餐饮美食/网购购物/日用百货/校园生活/交通出行/生活服务/充值缴费/其他\n" +
                        "type 必须是：expense 或 income\n" +
                        "如果无法确定某字段，用合理默认值填充。金额必须提取实际数字。")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "从以下支付截图OCR文本中提取信息，返回JSON格式：\n" +
                        "{\"amount\":25.50,\"category\":\"餐饮美食\",\"merchant\":\"商家名\",\"type\":\"expense\",\"date\":\"$today\",\"note\":\"备注\"}\n\n" +
                        "OCR文本：\n$rawText")
                })
            })
            put("temperature", 0.1)
            put("max_tokens", 500)
        }

        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey"); doOutput = true
            connectTimeout = 15000; readTimeout = 30000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        if (conn.responseCode !in 200..299) { conn.disconnect(); return@withContext null }
        val respText = conn.inputStream.bufferedReader().readText(); conn.disconnect()

        val content = JSONObject(respText)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()

        // Extract JSON from response (may have markdown fences or extra text)
        val jsonStr = Regex("""\{[\s\S]*"amount"[\s\S]*\}""").find(content)?.value ?: return@withContext null
        val obj = JSONObject(jsonStr)
        val amount = obj.optDouble("amount", 0.0)
        if (amount <= 0) return@withContext null

        val dateStr = obj.optString("date", today)
        val date = try { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(dateStr)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }

        OcrResult(
            amount = amount,
            merchant = obj.optString("merchant", ""),
            date = date,
            type = obj.optString("type", "expense"),
            rawText = rawText
        )
    } catch (_: Exception) { null }
}

// ---- Regex Fallback Parsing ----

private fun parseOcrText(raw: String): OcrResult {
    val lines = raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    var amount = 0.0; var merchant = ""; var date = 0L; var type = "expense"

    val amountPatterns = listOf(
        Regex("[¥￥]\\s*(\\d+\\.?\\d{0,2})"),
        Regex("(\\d+\\.\\d{2})\\s*元?"),
        Regex("付款金额[：:]\\s*(\\d+\\.?\\d{0,2})"),
        Regex("实付[：:]\\s*(\\d+\\.?\\d{0,2})"),
        Regex("金额[：:]\\s*(\\d+\\.?\\d{0,2})")
    )
    for (p in amountPatterns) {
        val m = p.find(raw)
        if (m != null) { amount = m.groupValues[1].toDoubleOrNull() ?: 0.0; if (amount > 0) break }
    }
    if (amount <= 0) {
        for (line in lines) {
            val m = Regex("^(\\d+\\.\\d{2})$").find(line)
            if (m != null) { val v = m.groupValues[1].toDoubleOrNull() ?: 0.0; if (v in 0.01..999999.0) { amount = v; break } }
        }
    }

    for (line in lines) {
        if (line.contains("支出") || line.contains("消费") || line.contains("付款") || line.contains("扣款")) { type = "expense"; break }
        if (line.contains("收入") || line.contains("收款") || line.contains("入账") || line.contains("转入")) { type = "income"; break }
    }

    // Merchant extraction
    val merchants = listOf("瑞幸", "星巴克", "库迪", "肯德基", "麦当劳", "海底捞", "茶百道", "蜜雪冰城",
        "喜茶", "奈雪的茶", "滴滴出行", "高德", "淘宝", "京东", "拼多多", "盒马", "永辉", "屈臣氏")
    for (m in merchants) {
        if (raw.contains(m)) { merchant = m; break }
    }
    if (merchant.isBlank()) {
        for (line in lines) {
            val m2 = Regex("""(?:商户|商家|收款方)[：:]?\s*(\S{2,12})""").find(line)
            if (m2 != null && m2.groupValues[1].length in 2..12) { merchant = m2.groupValues[1]; break }
        }
    }

    // Date extraction
    val datePatterns = listOf(
        Regex("""(\d{4}[-/年]\d{1,2}[-/月]\d{1,2})"""),
        Regex("""交易时间[：:]?\s*(\d{4}[-/]\d{1,2}[-/]\d{1,2})"""),
        Regex("""(\d{4}\.\d{1,2}\.\d{1,2})""")
    )
    for (p in datePatterns) {
        val m = p.find(raw)
        if (m != null) {
            val ds = m.groupValues[1].replace("年", "-").replace("月", "-").replace("日", "").replace(".", "-").replace("/", "-")
            date = try { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(ds)?.time ?: 0L } catch (_: Exception) { 0L }
            if (date > 0) break
        }
    }

    return OcrResult(amount = amount, merchant = merchant, date = date, type = type, rawText = raw)
}

private fun inferCategorySimple(merchant: String, type: String): String {
    if (type == "income") return "其他"
    val kw = merchant.lowercase()
    return when {
        kw.anyContains("瑞幸", "星巴克", "库迪", "肯德基", "麦当劳", "海底捞", "茶百道", "蜜雪冰城",
            "喜茶", "奈雪", "外卖", "咖啡", "奶茶", "餐厅", "麻辣烫", "火锅") -> "餐饮美食"
        kw.anyContains("滴滴", "高德", "曹操", "T3", "哈啰") -> "交通出行"
        kw.anyContains("淘宝", "京东", "拼多多") -> "网购购物"
        kw.anyContains("盒马", "永辉", "大润发", "超市") -> "日用百货"
        kw.anyContains("话费", "充值") -> "充值缴费"
        else -> "其他"
    }
}

private fun String.anyContains(vararg keywords: String): Boolean = keywords.any { contains(it, ignoreCase = true) }

// ---- Colors ----

private val AppBg = Color(0xFFFFF8E7)
private val Ink = Color(0xFF252735)
private val Muted = Color(0xFF74768A)
private val Pink = Color(0xFFFF3D8B)
private val Blue = Color(0xFF2979FF)
