package com.financeauto.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.financeauto.FinanceApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class UserProfile(
    val nickname: String,
    val phone: String,
    val avatarUrl: String = "",
    val expiryDate: Long = 0L,
    val planType: String = "free"
) {
    val isExpired: Boolean
        get() = expiryDate > 0L && expiryDate < System.currentTimeMillis()

    val daysRemaining: Int
        get() {
            if (expiryDate <= 0L) return 0
            val diff = expiryDate - System.currentTimeMillis()
            return ((diff / (24 * 60 * 60 * 1000)) + 1).toInt().coerceAtLeast(0)
        }

    val planLabel: String
        get() = when (planType) {
            "trial" -> "试用"
            "monthly" -> "月付会员"
            "yearly" -> "年付会员"
            "permanent" -> "永久会员"
            "free" -> "免费版"
            else -> planType
        }

    companion object {
        fun empty() = UserProfile("", "", "", 0L, "free")
    }
}

class SupabaseManager(context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("supabase_prefs", Context.MODE_PRIVATE)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val baseUrl: String get() = FinanceApplication.SUPABASE_URL
    private val anonKey: String get() = FinanceApplication.SUPABASE_ANON_KEY

    // ---- Session ----

    val isLoggedIn: Boolean
        get() = prefs.getString("access_token", null) != null

    val isAdmin: Boolean
        get() = currentPhone == FinanceApplication.ADMIN_PHONE

    val currentUserId: String
        get() = prefs.getString("user_id", "") ?: ""

    val currentNickname: String
        get() = prefs.getString("nickname", "财务管家")?.ifBlank { "财务管家" } ?: "财务管家"

    val currentPhone: String
        get() = prefs.getString("phone", "") ?: ""

    val currentExpiryDate: Long
        get() = prefs.getLong("expiry_date", 0L)

    val currentPlanType: String
        get() = prefs.getString("plan_type", "free") ?: "free"

    private fun phoneToEmail(phone: String): String = "u${phone.trim()}@fin.user"

    private fun saveSession(phone: String, nickname: String, accessToken: String, refreshToken: String, userId: String) {
        prefs.edit()
            .putString("phone", phone.trim())
            .putString("nickname", nickname.trim())
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("user_id", userId)
            .apply()
    }

    private fun saveSubscription(expiryDate: Long, planType: String) {
        prefs.edit()
            .putLong("expiry_date", expiryDate)
            .putString("plan_type", planType)
            .apply()
    }

    private fun clearSession() {
        prefs.edit().clear().apply()
    }

    private fun authHeaders(token: String? = null): Map<String, String> {
        val map = mutableMapOf("apikey" to anonKey, "Content-Type" to "application/json")
        token?.let { map["Authorization"] = "Bearer $it" }
        return map
    }

    // ---- 注册 ----

    suspend fun register(phone: String, password: String, nickname: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("email", phoneToEmail(phone))
                    put("password", password)
                    put("data", JSONObject().apply {
                        put("nickname", nickname.trim())
                    })
                }

                val request = Request.Builder()
                    .url("$baseUrl/auth/v1/signup")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .apply { authHeaders().forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val response = client.newCall(request).execute()
                val respBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val msg = parseError(respBody, "注册失败")
                    return@withContext Result.failure(RuntimeException(msg))
                }

                val json = JSONObject(respBody)
                val accessToken = json.optString("access_token", "")
                val refreshToken = json.optString("refresh_token", "")
                val user = json.optJSONObject("user")
                val savedNickname = user?.optJSONObject("user_metadata")?.optString("nickname", nickname.trim())
                    ?: nickname.trim()
                val userId = user?.optString("id", "") ?: ""

                if (accessToken.isNotEmpty()) {
                    saveSession(phone, savedNickname, accessToken, refreshToken, userId)
                }
                // 调用 RPC 创建试用订阅
                createTrialSubscription()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ---- 登录 ----

    suspend fun login(phone: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("email", phoneToEmail(phone))
                    put("password", password)
                }

                val request = Request.Builder()
                    .url("$baseUrl/auth/v1/token?grant_type=password")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .apply { authHeaders().forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val response = client.newCall(request).execute()
                val respBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val msg = parseError(respBody, "登录失败，手机号或密码错误")
                    return@withContext Result.failure(RuntimeException(msg))
                }

                val json = JSONObject(respBody)
                val accessToken = json.optString("access_token", "")
                val refreshToken = json.optString("refresh_token", "")
                val user = json.optJSONObject("user")
                val nickname = user?.optJSONObject("user_metadata")?.optString("nickname", "财务管家")
                    ?: "财务管家"
                val userId = user?.optString("id", "") ?: ""

                if (accessToken.isEmpty()) {
                    return@withContext Result.failure(RuntimeException("登录失败"))
                }

                saveSession(phone, nickname, accessToken, refreshToken, userId)
                // 拉取订阅信息，如果不存在则创建试用
                val subResult = fetchSubscription()
                if (subResult.isFailure || subResult.getOrNull()?.planType == "free") {
                    createTrialSubscription()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ---- 订阅：获取当前用户订阅 ----

    suspend fun fetchSubscription(): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("access_token", null)
                    ?: return@withContext Result.failure(RuntimeException("未登录"))
                val userId = prefs.getString("user_id", "") ?: ""

                if (userId.isEmpty()) {
                    return@withContext Result.failure(RuntimeException("用户ID缺失"))
                }

                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/subscriptions?user_id=eq.$userId&select=expiry_date,plan_type")
                    .get()
                    .apply { authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val response = client.newCall(request).execute()
                val respBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("获取订阅信息失败"))
                }

                // 解析 JSON 数组
                val jsonArray = org.json.JSONArray(respBody)
                if (jsonArray.length() > 0) {
                    val sub = jsonArray.getJSONObject(0)
                    val expiryStr = sub.optString("expiry_date", "")
                    val planType = sub.optString("plan_type", "free")
                    val expiryDate = parseIsoTimestamp(expiryStr)

                    saveSubscription(expiryDate, planType)
                    Result.success(getProfile())
                } else {
                    // 没有订阅记录，使用默认值
                    saveSubscription(0L, "free")
                    Result.success(getProfile())
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ---- 创建试用订阅（RPC） ----

    private suspend fun createTrialSubscription(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("access_token", null)
                    ?: return@withContext Result.failure(RuntimeException("未登录"))

                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/create_trial_subscription")
                    .post("{}".toRequestBody(jsonMediaType))
                    .apply { authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("创建试用订阅失败"))
                }
                // 拉取刚创建的订阅
                fetchSubscription()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ---- 管理员：列出所有订阅 ----

    suspend fun adminListSubscriptions(): Result<List<SubscriptionInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("access_token", null)
                    ?: return@withContext Result.failure(RuntimeException("未登录"))

                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/admin_list_subscriptions")
                    .post("{}".toRequestBody(jsonMediaType))
                    .apply { authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val response = client.newCall(request).execute()
                val respBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val msg = parseError(respBody, "查询失败（需要管理员权限）")
                    return@withContext Result.failure(RuntimeException(msg))
                }

                val jsonArray = org.json.JSONArray(respBody)
                val list = mutableListOf<SubscriptionInfo>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val email = obj.optString("user_email", "")
                    val phone = email.removePrefix("u").removeSuffix("@fin.user")
                    list.add(
                        SubscriptionInfo(
                            phone = phone,
                            nickname = obj.optString("user_nickname", ""),
                            expiryDate = parseIsoTimestamp(obj.optString("expiry_date", "")),
                            planType = obj.optString("plan_type", "free")
                        )
                    )
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ---- 管理员：设置用户订阅 ----

    suspend fun adminSetSubscription(targetPhone: String, expiryDate: Long, planType: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("access_token", null)
                    ?: return@withContext Result.failure(RuntimeException("未登录"))

                val isoDate = formatIsoTimestamp(expiryDate)
                val body = JSONObject().apply {
                    put("target_user_email", phoneToEmail(targetPhone))
                    put("new_expiry_date", isoDate)
                    put("new_plan_type", planType)
                }

                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/admin_set_subscription")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .apply { authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val msg = parseError(response.body?.string() ?: "", "设置失败（需要管理员权限）")
                    return@withContext Result.failure(RuntimeException(msg))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ---- 管理员：删除用户订阅 ----

    suspend fun adminDeleteSubscription(targetPhone: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("access_token", null)
                    ?: return@withContext Result.failure(RuntimeException("未登录"))

                val body = JSONObject().apply {
                    put("target_user_email", phoneToEmail(targetPhone))
                }

                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/admin_delete_subscription")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .apply { authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val msg = parseError(response.body?.string() ?: "", "删除失败")
                    return@withContext Result.failure(RuntimeException(msg))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ---- 修改昵称 ----

    suspend fun updateNickname(newNickname: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("access_token", null)
                    ?: return@withContext Result.failure(RuntimeException("未登录"))

                val body = JSONObject().apply {
                    put("data", JSONObject().apply {
                        put("nickname", newNickname.trim())
                    })
                }

                val request = Request.Builder()
                    .url("$baseUrl/auth/v1/user")
                    .put(body.toString().toRequestBody(jsonMediaType))
                    .apply { authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val msg = parseError(response.body?.string() ?: "", "修改失败")
                    return@withContext Result.failure(RuntimeException(msg))
                }

                prefs.edit().putString("nickname", newNickname.trim()).apply()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ---- 修改手机号 ----

    suspend fun updatePhone(newPhone: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("access_token", null)
                    ?: return@withContext Result.failure(RuntimeException("未登录"))

                val oldPhone = prefs.getString("phone", "") ?: ""
                val verifyBody = JSONObject().apply {
                    put("email", phoneToEmail(oldPhone))
                    put("password", password)
                }
                val verifyReq = Request.Builder()
                    .url("$baseUrl/auth/v1/token?grant_type=password")
                    .post(verifyBody.toString().toRequestBody(jsonMediaType))
                    .apply { authHeaders().forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val verifyResp = client.newCall(verifyReq).execute()
                if (!verifyResp.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("密码验证失败"))
                }

                val body = JSONObject().apply {
                    put("email", phoneToEmail(newPhone))
                }

                val request = Request.Builder()
                    .url("$baseUrl/auth/v1/user")
                    .put(body.toString().toRequestBody(jsonMediaType))
                    .apply { authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val msg = parseError(response.body?.string() ?: "", "修改失败")
                    return@withContext Result.failure(RuntimeException(msg))
                }

                prefs.edit().putString("phone", newPhone.trim()).apply()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ---- 修改密码 ----

    suspend fun changePassword(newPassword: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("access_token", null)
                    ?: return@withContext Result.failure(RuntimeException("未登录"))

                val body = JSONObject().apply {
                    put("password", newPassword)
                }

                val request = Request.Builder()
                    .url("$baseUrl/auth/v1/user")
                    .put(body.toString().toRequestBody(jsonMediaType))
                    .apply { authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val msg = parseError(response.body?.string() ?: "",
                        "密码修改失败，请重新登录后再试")
                    return@withContext Result.failure(RuntimeException(msg))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ---- 退出登录 ----

    fun logout() {
        try {
            val token = prefs.getString("access_token", null)
            if (token != null) {
                val request = Request.Builder()
                    .url("$baseUrl/auth/v1/logout")
                    .post("{}".toRequestBody(jsonMediaType))
                    .apply { authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                    .build()
                client.newCall(request).execute().close()
            }
        } catch (_: Exception) {}
        clearSession()
    }

    // ---- 获取用户信息 ----

    fun getProfile(): UserProfile {
        return UserProfile(
            nickname = currentNickname,
            phone = currentPhone,
            avatarUrl = "",
            expiryDate = currentExpiryDate,
            planType = currentPlanType
        )
    }

    // ---- 解析错误 ----

    private fun parseError(body: String, default: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("msg", json.optString("message", default))
        } catch (_: Exception) {
            default
        }
    }

    // ---- 时间工具 ----

    private fun parseIsoTimestamp(iso: String): Long {
        if (iso.isBlank()) return 0L
        return try {
            // 兼容 Supabase 返回的 ISO 8601 格式
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(iso.substringBefore('.').substringBefore('+').substringBefore('Z'))?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatIsoTimestamp(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    companion object {
        val PLAN_OPTIONS = listOf(
            "trial" to "试用 (7天)",
            "monthly" to "月付会员",
            "yearly" to "年付会员",
            "permanent" to "永久会员"
        )

        fun planLabel(type: String): String = when (type) {
            "trial" -> "试用"
            "monthly" -> "月付会员"
            "yearly" -> "年付会员"
            "permanent" -> "永久会员"
            "free" -> "免费版"
            else -> type
        }
    }
}

data class SubscriptionInfo(
    val phone: String,
    val nickname: String,
    val expiryDate: Long,
    val planType: String
) {
    val planLabel: String
        get() = SupabaseManager.planLabel(planType)

    val isExpired: Boolean
        get() = expiryDate > 0L && expiryDate < System.currentTimeMillis()

    val daysRemaining: Int
        get() {
            if (expiryDate <= 0L) return 0
            val diff = expiryDate - System.currentTimeMillis()
            return ((diff / (24 * 60 * 60 * 1000)) + 1).toInt().coerceAtLeast(0)
        }
}
