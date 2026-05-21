package com.financeauto.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeauto.data.local.AppDatabase
import com.financeauto.data.local.entity.BookEntity
import com.financeauto.data.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream

private val BOOK_CATEGORIES = listOf("全部", "收藏", "投资理财", "经济学", "商业管理", "心理学", "人物传记", "小说文学", "历史哲学", "其他")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BooksScreen(onReaderState: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val repo = remember { BookRepository(db.bookDao()) }
    val allBooks by repo.allBooks.collectAsState(initial = emptyList())
    val favBooks by repo.getFavorites().collectAsState(initial = emptyList())
    val favIds = remember(favBooks) { favBooks.map { it.id }.toSet() }
    var selCategory by remember { mutableStateOf("全部") }
    var showReader by remember { mutableStateOf<BookEntity?>(null) }
    var showNews by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    var newsContent by remember { mutableStateOf(loadNewsCache(context)) }
    var newsLoading by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importBook(it, context, repo, scope) }
    }

    // Filtered books
    val filtered = remember(allBooks, selCategory, favIds) {
        when (selCategory) {
            "收藏" -> allBooks.filter { favIds.contains(it.id) }
            "全部" -> allBooks
            else -> allBooks.filter { it.category == selCategory }
        }
    }

    // Reader view — full screen
    showReader?.let { book ->
        LaunchedEffect(Unit) { onReaderState(true) }
        ReaderView(book = book, onBack = {
            showReader = null
            onReaderState(false)
        }, onPageChange = { page ->
            scope.launch { repo.updateProgress(book.id, page) }
        })
        return
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFFFF8E7)).navigationBarsPadding()) {
        // Header
        Surface(Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("图书馆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF252735))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { filePicker.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(Color(0xFF2979FF), Color.White),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("+ 导入", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Finance news
            item { FinanceNewsCard(newsContent, newsLoading, showNews, onRefresh = {
                newsLoading = true; scope.launch { newsContent = fetchFinanceNews(context, newsContent); newsLoading = false }
            }, onToggle = { showNews = !showNews }) }

            // Category filter
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BOOK_CATEGORIES.forEach { cat ->
                        val isFav = cat == "收藏"
                        Surface(
                            modifier = Modifier.clickable { selCategory = cat },
                            shape = RoundedCornerShape(20.dp),
                            color = if (selCategory == cat) Color(0xFF2979FF) else Color.White,
                            shadowElevation = if (selCategory == cat) 1.dp else 0.dp
                        ) {
                            Text(
                                if (isFav) "⭐ 收藏" else cat,
                                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                color = if (selCategory == cat) Color.White else Color(0xFF252735),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selCategory == cat) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Book grid or empty
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📚", fontSize = 40.sp)
                            Text(if (selCategory == "收藏") "暂无收藏书籍" else "书架空空，点击右上角「+ 导入」添加书籍",
                                color = Color(0xFF74768A), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                // Render grid in chunks of 3
                item {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        val rows = filtered.chunked(3)
                        rows.forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { book ->
                                    BookCard(
                                        book = book,
                                        isFav = favIds.contains(book.id),
                                        modifier = Modifier.weight(1f),
                                        onClick = { showReader = book },
                                        onToggleFav = { scope.launch { repo.toggleFavorite(book.id, !favIds.contains(book.id)) } },
                                        onDelete = {
                                            scope.launch {
                                                repo.delete(book.id)
                                                File(book.filePath).delete()
                                            }
                                        }
                                    )
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---- Finance News Card ----

@Composable
private fun FinanceNewsCard(
    content: String, loading: Boolean, expanded: Boolean,
    onRefresh: () -> Unit, onToggle: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color(0xFFFF9500).copy(alpha = 0.15f), modifier = Modifier.size(28.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("📰", fontSize = 14.sp) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("每日财经资讯", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF252735))
                }
                Row {
                    TextButton(onClick = onRefresh) { Text(if (loading) "刷新中..." else "刷新", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2979FF)) }
                    TextButton(onClick = onToggle) { Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelSmall, color = Color(0xFF74768A)) }
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp)); HorizontalDivider(color = Color(0xFFF0F0F0)); Spacer(Modifier.height(8.dp))
                Text(if (content.isBlank()) "暂无资讯，点击刷新获取今日财经要闻" else content,
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF252735), lineHeight = 20.sp)
            }
        }
    }
}

// ---- Book Card with long-press menu ----

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: BookEntity, isFav: Boolean, modifier: Modifier,
    onClick: () -> Unit, onToggleFav: () -> Unit, onDelete: () -> Unit
) {
    val catColors = mapOf(
        "投资理财" to Color(0xFFFF9500), "经济学" to Color(0xFF5856D6), "商业管理" to Color(0xFF2979FF),
        "心理学" to Color(0xFFFF3D8B), "人物传记" to Color(0xFF34C759), "小说文学" to Color(0xFFAF52DE),
        "历史哲学" to Color(0xFF8E8E93)
    )
    val bgColor = catColors[book.category] ?: Color(0xFF8E8E93)
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                // Cover
                Box(
                    Modifier.fillMaxWidth().height(100.dp).background(bgColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isFav) {
                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                            Surface(shape = CircleShape, color = Color(0xFFFFD600).copy(alpha = 0.8f)) {
                                Text("⭐", fontSize = 14.sp, modifier = Modifier.padding(3.dp))
                            }
                        }
                    }
                    Text(
                        when (book.fileType) { "pdf" -> "PDF"; "epub" -> "EPUB"; else -> "TXT" },
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = bgColor.copy(alpha = 0.5f)
                    )
                }
                Column(Modifier.padding(8.dp)) {
                    Text(book.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF252735), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(book.author.ifBlank { book.category }, style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF74768A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (book.totalPages > 0 && book.currentPage > 0) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { book.currentPage.toFloat() / book.totalPages },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF2979FF), trackColor = Color(0xFFF0F0F0)
                        )
                    }
                }
            }
        }

        // Long-press dropdown menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = DpOffset(40.dp, 0.dp)
        ) {
            DropdownMenuItem(
                text = { Text(if (isFav) "⭐ 取消收藏" else "⭐ 收藏") },
                onClick = { onToggleFav(); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text("🗑 删除", color = Color(0xFFFF3D8B)) },
                onClick = { onDelete(); showMenu = false }
            )
        }
    }
}

// ---- PDF / Text Reader ----

@Composable
private fun ReaderView(book: BookEntity, onBack: () -> Unit, onPageChange: (Int) -> Unit) {
    var currentPage by remember { mutableStateOf(book.currentPage.coerceAtLeast(0)) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var totalPages by remember { mutableStateOf(book.totalPages) }
    var textContent by remember { mutableStateOf("") }
    var isText by remember { mutableStateOf(book.fileType == "txt") }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPage) {
        loadError = null
        withContext(Dispatchers.IO) {
            try {
                val file = File(book.filePath)
                if (!file.exists()) { loadError = "文件不存在"; return@withContext }
                when {
                    book.fileType == "pdf" -> {
                        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        totalPages = renderer.pageCount
                        if (currentPage in 0 until renderer.pageCount) {
                            val page = renderer.openPage(currentPage)
                            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            pageBitmap = bmp; page.close()
                        }
                        renderer.close(); pfd.close()
                    }
                    book.fileType == "txt" -> {
                        isText = true; textContent = file.readText().take(100000); totalPages = 1
                    }
                    book.fileType == "epub" -> {
                        isText = true
                        textContent = extractEpubText(file).take(100000)
                        if (textContent.isBlank()) textContent = "无法提取 EPUB 内容，请使用其他阅读器打开。"
                        totalPages = 1
                    }
                    else -> { isText = true; textContent = file.readText().take(100000); totalPages = 1 }
                }
            } catch (e: Exception) {
                loadError = "无法打开文件: ${e.message}"
                isText = true
                if (textContent.isBlank()) {
                    try {
                        if (book.fileType == "epub") textContent = extractEpubText(File(book.filePath)).take(50000)
                        else textContent = File(book.filePath).readText().take(50000)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF1A1A2E)).navigationBarsPadding()) {
        // Top bar
        Surface(Modifier.fillMaxWidth(), color = Color(0xFF16213E)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onPageChange(currentPage); onBack() }) { Text("← 返回", color = Color.White) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(book.title, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (totalPages > 0) Text("${currentPage + 1} / $totalPages", style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA))
                }
                Spacer(Modifier.width(48.dp))
            }
        }

        // Content
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (loadError != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚠️", fontSize = 32.sp)
                        Text(loadError!!, color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (isText) {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                    item { Text(textContent, color = Color(0xFFDDDDDD), style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp) }
                }
            } else {
                Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                    if (pageBitmap != null) {
                        Image(bitmap = pageBitmap!!.asImageBitmap(), contentDescription = "第${currentPage + 1}页",
                            modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }

        // Bottom page controls (PDF only)
        if (!isText && totalPages > 1) {
            Surface(Modifier.fillMaxWidth(), color = Color(0xFF16213E)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        if (currentPage > 0) { currentPage--; onPageChange(currentPage) }
                    }, enabled = currentPage > 0) { Text("◀ 上一页", color = if (currentPage > 0) Color.White else Color(0xFF666666)) }
                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = { currentPage = it.toInt(); onPageChange(currentPage) },
                        valueRange = 0f..(totalPages - 1).coerceAtLeast(0).toFloat(),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF2979FF))
                    )
                    TextButton(onClick = {
                        if (currentPage < totalPages - 1) { currentPage++; onPageChange(currentPage) }
                    }, enabled = currentPage < totalPages - 1) { Text("下一页 ▶", color = if (currentPage < totalPages - 1) Color.White else Color(0xFF666666)) }
                }
            }
        }
    }
}

// ---- EPUB Text Extraction ----

private fun extractEpubText(file: File): String {
    return try {
        val sb = StringBuilder()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if ((name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm")) &&
                    !name.contains("nav") && !name.contains("toc") && !name.contains("cover")) {
                    val raw = zis.readBytes().toString(Charsets.UTF_8)
                    // Strip HTML tags
                    val text = raw
                        .replace(Regex("""<style[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("""<script[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("""<[^>]+>"""), " ")
                        .replace(Regex("""&nbsp;|&lt;|&gt;|&amp;|&quot;|&#?\w+;"""), " ")
                        .replace(Regex("""\s{2,}"""), "\n")
                        .trim()
                    if (text.isNotBlank()) sb.appendLine(text)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        sb.toString().trim()
    } catch (_: Exception) { "" }
}

// ---- Book Import ----

private fun importBook(uri: Uri, context: Context, repo: BookRepository, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        try {
            val cr = context.contentResolver
            val mime = cr.getType(uri) ?: "application/octet-stream"
            val fileName = cr.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow("_display_name")) else "未命名"
            } ?: "未命名"

            val fileType = when {
                mime.contains("pdf") || fileName.endsWith(".pdf") -> "pdf"
                mime.contains("epub") || fileName.endsWith(".epub") -> "epub"
                mime.contains("text") || fileName.endsWith(".txt") -> "txt"
                else -> return@launch
            }

            val destDir = File(context.filesDir, "books"); destDir.mkdirs()
            val destFile = File(destDir, "${UUID.randomUUID()}.$fileType")
            cr.openInputStream(uri)?.use { input -> FileOutputStream(destFile).use { output -> input.copyTo(output) } }

            val title = fileName.substringBeforeLast(".").take(50)
            var pages = 0
            if (fileType == "pdf") {
                try {
                    val pfd = ParcelFileDescriptor.open(destFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd); pages = renderer.pageCount; renderer.close(); pfd.close()
                } catch (_: Exception) {}
            }

            repo.insert(BookEntity(
                title = title, category = guessCategory(title),
                filePath = destFile.absolutePath, fileName = fileName, fileType = fileType,
                fileSize = destFile.length(), totalPages = pages
            ))
        } catch (_: Exception) {}
    }
}

private fun guessCategory(title: String): String {
    val kw = title.lowercase()
    return when {
        kw.anyContains("投资", "股票", "基金", "理财", "巴菲特", "格雷厄姆", "彼得林奇") -> "投资理财"
        kw.anyContains("经济", "宏观", "微观", "国富论", "资本论", "货币") -> "经济学"
        kw.anyContains("管理", "商业", "创业", "领导力", "营销") -> "商业管理"
        kw.anyContains("心理", "行为", "思考", "思维", "认知", "习惯") -> "心理学"
        kw.anyContains("传", "乔布斯", "马斯克", "自传", "回忆录") -> "人物传记"
        kw.anyContains("科幻", "悬疑", "推理", "小说", "文学", "故事") -> "小说文学"
        kw.anyContains("历史", "哲学", "文明", "战争") -> "历史哲学"
        else -> "其他"
    }
}

// ---- Finance News ----

private suspend fun fetchFinanceNews(context: Context, existing: String): String = withContext(Dispatchers.IO) {
    val prefs = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
    val apiUrl = prefs.getString("chat_api_url", "https://api.deepseek.com/v1/chat/completions") ?: return@withContext existing
    val apiKey = prefs.getString("chat_api_key", "") ?: return@withContext existing
    val model = prefs.getString("chat_model", "deepseek-chat") ?: "deepseek-chat"
    if (apiKey.isBlank()) return@withContext "请在 AI Chat 设置中配置 API Key"

    try {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", "你是财经资讯编辑。生成今日财经要闻摘要（500字以内），包括：1.今日重要财经事件 2.市场行情概览 3.投资提示。用中文，简洁专业。") })
                put(JSONObject().apply { put("role", "user"); put("content", "请提供今日（${SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA).format(Date())}）财经资讯摘要") })
            })
            put("stream", false)
        }
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey"); doOutput = true; connectTimeout = 15000; readTimeout = 30000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        val text = if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().readText() else throw Exception("HTTP ${conn.responseCode}")
        conn.disconnect()
        val content = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        prefs.edit().putString("news_cache", content).putLong("news_time", System.currentTimeMillis()).apply()
        content
    } catch (e: Exception) { existing.ifBlank { "获取资讯失败: ${e.message}" } }
}

private fun loadNewsCache(context: Context): String {
    val prefs = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
    val time = prefs.getLong("news_time", 0)
    return if (System.currentTimeMillis() - time < 6 * 3600 * 1000) prefs.getString("news_cache", "") ?: "" else ""
}

private fun String.anyContains(vararg keywords: String): Boolean = keywords.any { contains(it, ignoreCase = true) }
