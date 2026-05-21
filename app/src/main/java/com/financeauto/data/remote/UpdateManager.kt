package com.financeauto.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.financeauto.FinanceApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String
)

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // APK download may take a while
        .build()

    /** Check GitHub Pages for a newer version. Returns UpdateInfo if update available, null otherwise. */
    suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(FinanceApplication.UPDATE_URL).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.success(null)

            val json = JSONObject(body)
            val remoteVersionCode = json.getInt("versionCode")
            val currentVersionCode = context.packageManager
                .getPackageInfo(context.packageName, 0).versionCode

            if (remoteVersionCode > currentVersionCode) {
                Result.success(
                    UpdateInfo(
                        versionCode = remoteVersionCode,
                        versionName = json.optString("versionName", ""),
                        downloadUrl = json.getString("downloadUrl"),
                        changelog = json.optString("changelog", "")
                    )
                )
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Download APK via OkHttp and launch install intent. */
    suspend fun downloadAndInstall(downloadUrl: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.cacheDir
                val apkFile = File(dir, "financeauto_update.apk")
                apkFile.parentFile?.mkdirs()
                if (apkFile.exists()) apkFile.delete()

                val request = Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        RuntimeException("下载失败: HTTP ${response.code}")
                    )
                }

                val body = response.body ?: return@withContext Result.failure(
                    RuntimeException("响应为空")
                )

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                        }
                    }
                }

                if (totalBytes > 0 && apkFile.length() < totalBytes) {
                    apkFile.delete()
                    return@withContext Result.failure(
                        RuntimeException("下载不完整")
                    )
                }

                installApk(apkFile)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun installApk(file: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } else {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
