package com.financeauto.nlp

import com.financeauto.util.ChineseNumberParser

data class ExpenseResult(
    val amount: Double,          // negative for expense
    val category: String,
    val note: String,
    val merchant: String
)

class ExpenseExtractor {

    fun extract(text: String): ExpenseResult? {
        if (isNonExpenseContext(text)) return null

        val amount = extractAmount(text) ?: return null
        if (isAmbiguousAmount(text)) return null

        val merchant = extractMerchant(text)
        val category = classifyCategory(text, merchant)
        val note = text.trim().take(50)

        return ExpenseResult(
            amount = -kotlin.math.abs(amount),
            category = category,
            note = note,
            merchant = merchant
        )
    }

    private fun extractAmount(text: String): Double? {
        // Arabic digits + currency unit
        val patterns = listOf(
            Regex("""(\d+\.?\d*)\s*[元块]?"""),
            Regex("""(\d+\.?\d*)\s*块钱"""),
            Regex("""花了\s*(\d+\.?\d*)"""),
            Regex("""买了\s*(\d+\.?\d*)"""),
            Regex("""付了.*?(\d+\.?\d*)"""),
            Regex("""消费了?\s*(\d+\.?\d*)"""),
            Regex("""支付了?\s*(\d+\.?\d*)"""),
            Regex("""用[了]?\s*(\d+\.?\d*)"""),
            Regex("""[¥￥]\s*(\d+\.?\d*)"""),
            Regex("""(\d+\.?\d*)\s*[元块]?"""),
        )
        for (p in patterns) {
            p.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                if (it > 0 && it < 100000) return it
            }
        }
        // Chinese numbers
        ChineseNumberParser.parse(text)?.let {
            if (it in 0.01..99999.0) return it
        }
        return null
    }

    private fun extractMerchant(text: String): String {
        val knownMerchants = listOf(
            "瑞幸", "星巴克", "库迪", "幸运咖", "蜜雪冰城", "喜茶", "奈雪的茶", "茶百道", "霸王茶姬",
            "沪上阿姨", "一点点", "CoCo", "茶颜悦色", "Manner", "M Stand",
            "肯德基", "麦当劳", "汉堡王", "必胜客", "达美乐", "海底捞", "呷哺呷哺", "太二酸菜鱼",
            "老乡鸡", "杨国福麻辣烫", "张亮麻辣烫", "华莱士", "塔斯汀", "萨莉亚", "南城香",
            "滴滴出行", "高德打车", "花小猪", "曹操出行", "T3出行", "哈啰出行", "嘀嗒出行",
            "美团单车", "青桔单车", "首汽约车", "喵走出行",
            "淘宝", "京东", "拼多多", "抖音电商", "小红书", "唯品会", "得物", "闲鱼",
            "盒马鲜生", "永辉超市", "大润发", "胖东来", "好特卖", "名创优品", "无印良品",
            "屈臣氏", "丝芙兰", "迪卡侬", "悦来悦喜", "赵一鸣", "折扣牛",
            "食堂", "校园", "教材", "学费",
            "U净", "丰巢", "菜鸟驿站", "顺丰速运", "中通快递", "圆通速递",
            "韵达快递", "京东快递", "德邦物流", "闪送",
            "Ujing", "12306", "Keep", "话费充值"
        )
        for (m in knownMerchants) {
            if (text.contains(m)) return m
        }
        // Try to extract name after "在" or before "花了"
        Regex("""在[的]?\s*(\S{1,8})""").find(text)?.let {
            return it.groupValues[1]
        }
        return ""
    }

    private fun classifyCategory(text: String, merchant: String): String {
        val combined = "$text $merchant"

        if (combined.anyContains("食堂", "学费", "教材", "校园")) return "校园生活"
        if (combined.anyContains("滴滴", "高德", "花小猪", "曹操", "T3", "哈啰", "嘀嗒", "美团单车",
                "青桔", "首汽", "喵走", "12306", "ETC", "停车", "加油", "公交", "地铁", "火车", "机票"))
            return "交通出行"

        return when {
            combined.anyContains("瑞幸", "星巴克", "库迪", "幸运咖", "蜜雪冰城", "喜茶", "奈雪", "茶百道",
                "霸王茶姬", "沪上阿姨", "一点点", "CoCo", "coco", "茶颜悦色", "Manner", "M Stand",
                "肯德基", "麦当劳", "汉堡王", "必胜客", "达美乐", "海底捞", "呷哺", "太二",
                "老乡鸡", "杨国福", "张亮", "华莱士", "塔斯汀", "萨莉亚", "南城香",
                "外卖", "餐厅", "饭店", "小吃", "面馆", "麻辣烫", "火锅", "烧烤",
                "奶茶", "咖啡", "甜品", "烘焙") -> "餐饮美食"

            combined.anyContains("淘宝", "京东", "拼多多", "抖音电商", "抖音", "小红书", "唯品会", "得物",
                "闲鱼", "天猫", "苏宁", "当当", "山姆", "Costco", "网上", "网购") -> "网购购物"

            combined.anyContains("盒马", "永辉", "大润发", "胖东来", "好特卖", "名创优品", "无印良品",
                "屈臣氏", "丝芙兰", "迪卡侬", "悦来悦喜", "赵一鸣", "折扣牛",
                "超市", "便利店", "百货", "日用") -> "日用百货"

            combined.anyContains("U净", "丰巢", "菜鸟", "顺丰", "中通", "圆通", "韵达", "京东快递",
                "德邦", "闪送", "快递", "洗衣", "维修", "打印", "理发", "美发",
                "医疗", "医院", "药房", "健身房", "KTV", "电影院", "娱乐", "宠物",
                "Keep", "运动", "健身") -> "生活服务"

            combined.anyContains("话费", "充值", "会员", "流量", "电费", "水费", "网费") -> "充值缴费"

            else -> "其他"
        }
    }

    private fun isNonExpenseContext(text: String): Boolean {
        val nonExpense = listOf("收入", "退款", "转账", "红包", "提现", "工资", "报销", "余额")
        val hasAmount = Regex("""\d+\.?\d*""").containsMatchIn(text)
        val hasExpenseAction = text.anyContains("花了", "买了", "付了", "消费", "支付", "用了", "¥", "￥")
        // If no expense action verb, check if it's a non-expense context
        if (!hasExpenseAction && hasAmount) {
            return nonExpense.any { text.contains(it) }
        }
        return false
    }

    private fun isAmbiguousAmount(text: String): Boolean {
        return text.anyContains("大概", "左右", "差不多", "可能有", "应该是", "好像是", "估计", "也许")
    }
}

private fun String.anyContains(vararg keywords: String): Boolean =
    keywords.any { contains(it, ignoreCase = true) }
