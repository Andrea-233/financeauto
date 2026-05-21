package com.financeauto

import android.app.Application

class FinanceApplication : Application() {
    companion object {
        // Supabase 配置 — 去 https://supabase.com 创建项目后获取
        const val SUPABASE_URL = "https://jpgeycamdpsjkoegbmus.supabase.co"
        const val SUPABASE_ANON_KEY = "sb_publishable_B6NWKo4CzU0GaeQaq3JVlg_gcQq9CFT"

        // GitHub Pages 自更新地址
        const val UPDATE_URL = "https://andrea-233.github.io/financeauto/update.json"

        // 管理员手机号
        const val ADMIN_PHONE = "16637316698"
        val ADMIN_EMAIL: String get() = "u${ADMIN_PHONE}@fin.user"
    }

    override fun onCreate() {
        super.onCreate()
    }
}
