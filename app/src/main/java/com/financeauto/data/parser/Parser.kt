package com.financeauto.data.parser

import com.financeauto.data.model.Transaction
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

interface ExcelBillParser {
    fun parseExcel(inputStream: InputStream): List<Transaction>
}

class AlipayExcelParser : ExcelBillParser {
    override fun parseExcel(inputStream: InputStream): List<Transaction> =
        ExcelBillReader(BillSource.ALIPAY).parse(inputStream)
}

class WeChatExcelParser : ExcelBillParser {
    override fun parseExcel(inputStream: InputStream): List<Transaction> =
        ExcelBillReader(BillSource.WECHAT).parse(inputStream)
}

class BankExcelParser : ExcelBillParser {
    override fun parseExcel(inputStream: InputStream): List<Transaction> =
        ExcelBillReader(BillSource.BANK).parse(inputStream)
}

private enum class BillSource(val label: String) {
    ALIPAY("支付宝"),
    WECHAT("微信"),
    BANK("银行卡")
}

private data class HeaderColumns(
    val rowIndex: Int,
    val time: Int,
    val platformCategory: Int?,
    val party: Int?,
    val goods: Int?,
    val flow: Int?,
    val amount: Int?,
    val incomeAmount: Int?,
    val expenseAmount: Int?,
    val payment: Int?,
    val status: Int?,
    val orderId: Int?,
    val merchantOrderId: Int?
)

private class ExcelBillReader(
    private val source: BillSource
) {
    private val formatter = DataFormatter(Locale.CHINA)

    fun parse(inputStream: InputStream): List<Transaction> {
        return try {
            XSSFWorkbook(inputStream).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val header = findHeader(sheet) ?: return emptyList()

                (header.rowIndex + 1..sheet.lastRowNum).mapNotNull { rowIndex ->
                    sheet.getRow(rowIndex)?.let { parseRow(it, header) }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            emptyList()
        }
    }

    private fun findHeader(sheet: Sheet): HeaderColumns? {
        val maxScanRow = minOf(sheet.lastRowNum, 100)
        for (rowIndex in 0..maxScanRow) {
            val row = sheet.getRow(rowIndex) ?: continue
            val headers = (0 until row.lastCellNum.coerceAtLeast(0)).associateBy { index ->
                normalizeHeader(readCell(row.getCell(index)))
            }

            val time = headers.firstIndexOf("交易时间", "交易日期", "记账日期", "入账日期", "交易日", "日期", "时间")
            val flow = headers.firstIndexOf("收支", "收支方向", "借贷标志", "借贷方向", "交易方向", "收入支出")
            val amount = headers.firstIndexOf("金额", "金额元", "交易金额", "发生额", "交易额", "人民币金额")
            val incomeAmount = headers.firstIndexOf("收入金额", "收入", "贷方金额", "贷方发生额", "存入", "转入金额")
            val expenseAmount = headers.firstIndexOf("支出金额", "支出", "借方金额", "借方发生额", "取出", "转出金额")
            if (time == null || (amount == null && incomeAmount == null && expenseAmount == null)) continue

            return HeaderColumns(
                rowIndex = rowIndex,
                time = time,
                platformCategory = headers.firstIndexOf("交易分类", "交易类型", "业务类型", "交易渠道", "类目", "类别", "分类", "摘要"),
                party = headers.firstIndexOf("交易对方", "对方户名", "对方名称", "对方账户", "户名", "商户名称"),
                goods = headers.firstIndexOf("商品说明", "产品说明", "商品", "商品名称", "交易说明", "摘要", "交易摘要", "用途", "备注", "附言", "交易备注"),
                flow = flow,
                amount = amount,
                incomeAmount = incomeAmount,
                expenseAmount = expenseAmount,
                payment = headers.firstIndexOf("收付款方式", "支付方式"),
                status = headers.firstIndexOf("交易状态", "当前状态", "状态"),
                orderId = headers.firstIndexOf("交易订单号", "交易单号", "流水号", "银行流水号", "交易流水号", "凭证号", "业务编号", "交易编号"),
                merchantOrderId = headers.firstIndexOf("商家订单号", "商户单号", "对方账号", "对方账户", "卡号")
            )
        }
        return null
    }

    private fun parseRow(row: Row, header: HeaderColumns): Transaction? {
        val flowText = header.flow?.let { readCell(row.getCell(it)) }.orEmpty()
        val income = header.incomeAmount?.let { parseAmount(readCell(row.getCell(it))) } ?: 0.0
        val expense = header.expenseAmount?.let { parseAmount(readCell(row.getCell(it))) } ?: 0.0
        val rawAmount = header.amount?.let { parseSignedAmount(readCell(row.getCell(it))) } ?: 0.0
        val type = normalizeType(flowText, income, expense, rawAmount)
        val amount = when {
            income > 0.0 -> income
            expense > 0.0 -> expense
            rawAmount != 0.0 -> kotlin.math.abs(rawAmount)
            else -> 0.0
        }
        if (amount <= 0.0) return null

        val status = header.status?.let { readCell(row.getCell(it)) }.orEmpty()
        if (isInvalidStatus(status)) return null

        val platformCategory = header.platformCategory?.let { readCell(row.getCell(it)) }.orEmpty()
        val party = header.party?.let { readCell(row.getCell(it)) }.orEmpty()
        val goods = header.goods?.let { readCell(row.getCell(it)) }.orEmpty()
        val payment = header.payment?.let { readCell(row.getCell(it)) }.orEmpty()
        val orderId = header.orderId?.let { readCell(row.getCell(it)) }.orEmpty()
        val merchantOrderId = header.merchantOrderId?.let { readCell(row.getCell(it)) }.orEmpty()
        val time = parseTime(row.getCell(header.time))

        return Transaction(
            id = 0,
            amount = amount,
            type = type,
            category = inferCategory(type, platformCategory, party, goods),
            note = buildNote(party, goods, payment, status),
            fingerprint = buildFingerprint(orderId, merchantOrderId, time, amount, type, platformCategory, party, goods),
            time = time
        )
    }

    private fun buildNote(party: String, goods: String, payment: String, status: String): String {
        return listOf(source.label, party, goods, payment, status)
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "/" }
            .distinct()
            .joinToString(" · ")
    }

    private fun buildFingerprint(
        orderId: String,
        merchantOrderId: String,
        time: Long,
        amount: Double,
        type: String,
        platformCategory: String,
        party: String,
        goods: String
    ): String {
        val strongId = listOf(orderId, merchantOrderId)
            .map { it.trim().replace("\t", "") }
            .firstOrNull { it.isNotBlank() && it != "/" }
        if (strongId != null) return "${source.name}|id|$strongId"

        val minuteTime = time / 60000L
        val amountKey = "%.2f".format(Locale.US, amount)
        val textKey = "$platformCategory|$party|$goods"
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), "")
        return "${source.name}|fallback|$type|$amountKey|$minuteTime|$textKey"
    }

    private fun normalizeType(flowText: String, income: Double, expense: Double, signedAmount: Double): String = when {
        income > 0.0 -> "income"
        expense > 0.0 -> "expense"
        flowText.hasAny("不计收支", "不计") -> "neutral"
        flowText.hasAny("收入", "收", "贷", "入账", "转入", "存入") -> "income"
        flowText.hasAny("支出", "支", "借", "出账", "转出", "取出", "消费") -> "expense"
        signedAmount > 0.0 -> "income"
        signedAmount < 0.0 -> "expense"
        else -> "neutral"
    }

    private fun inferCategory(type: String, platformCategory: String, party: String, goods: String): String {
        val text = "$platformCategory $party $goods"

        if (type == "neutral") return "不计收支"
        if (type == "income") {
            return when {
                text.hasAny("退款", "退回", "返现", "返利", "赔付", "报销") -> "退款"
                text.hasAny("转账", "收钱码", "二维码收款", "红包", "收款", "汇入") -> "转账"
                text.hasAny("工资", "薪资", "薪金", "奖金", "劳务", "报酬", "兼职") -> "工资收入"
                text.hasAny("助学金", "助研费", "奖学金") -> "收入"
                text.hasAny("理财", "收益", "分红", "利息", "基金", "余额宝", "零钱通") -> "投资收益"
                else -> "收入"
            }
        }

        val mapped = mapPlatformCategory(platformCategory, party, goods)
        if (mapped != null) return mapped

        // Special channel rules: platform-first classification
        if (text.hasAny("拼多多")) return "网购购物"
        if (text.hasAny("抖音")) {
            return if (text.hasAny("餐饮", "外卖", "美食", "奶茶", "咖啡", "饮品", "小吃", "烘焙", "蛋糕", "甜品", "正餐", "饭店", "食堂", "快餐", "烧烤", "火锅"))
                "餐饮美食" else "网购购物"
        }
        if (text.hasAny("美团")) {
            return if (text.hasAny("外卖", "餐饮", "美食", "小吃", "快餐", "奶茶", "咖啡", "烘焙", "蛋糕", "甜品", "烧烤", "火锅"))
                "餐饮美食" else "网购购物"
        }

        // 8-category keyword matching
        return when {
            // 1. 餐饮美食
            text.hasAny("餐饮", "外卖", "奶茶", "烘焙", "蛋糕", "小吃", "美食", "饭店", "饮品", "正餐", "甜品", "螺蛳粉", "臭豆腐",
                "咖啡", "瑞幸", "星巴克", "蜜雪冰城", "喜茶", "奈雪", "茶百道", "古茗", "霸王茶姬", "一点点", "coco", "茶颜悦色",
                "麻辣烫", "炸鸡", "汉堡", "披萨", "米线", "烧烤", "火锅", "串串", "自助", "餐厅", "快餐", "便当", "盒饭") -> "餐饮美食"
            // 2. 网购购物
            text.hasAny("淘宝", "京东", "电商", "格物致品", "服饰", "数码", "家居", "饰品", "文具", "3C", "零食", "平台商户",
                "唯品会", "得物", "闲鱼", "天猫", "苏宁", "当当", "小红书", "订单", "在线购物", "网购",
                "手机", "电脑", "电器", "家电", "电子", "耳机", "美妆", "护肤", "化妆品", "美容", "衣服", "鞋", "包") -> "网购购物"
            // 3. 日用百货
            text.hasAny("超市", "便利店", "悦来悦喜", "赵一鸣", "小卖部", "折扣牛", "日用品", "百货", "杂货",
                "好特卖", "屈臣氏", "名创优品", "商店", "五金") -> "日用百货"
            // 4. 校园生活
            text.hasAny("大学", "学校", "食堂", "餐费", "校园", "莘园", "清苑", "教材", "学费") -> "校园生活"
            // 5. 交通出行
            text.hasAny("滴滴", "打车", "骑行", "出行", "公交", "地铁", "威科姆", "喵走",
                "顺风车", "快车", "网约车", "专车", "花小猪", "T3", "曹操", "高德打车",
                "单车", "共享单车", "哈啰", "美团单车", "青桔", "火车", "高铁", "机票", "飞机", "12306",
                "加油", "充电桩", "停车", "ETC", "高速") -> "交通出行"
            // 6. 生活服务
            text.hasAny("U净", "洗衣", "共享", "洗烘", "维修", "打印", "理发", "便民服务",
                "干洗", "洗鞋", "清洁", "家政", "保洁", "电影", "KTV", "游戏", "音乐", "视频",
                "医疗", "医院", "药", "门诊", "体检", "快递", "物流", "顺丰", "中通", "圆通", "韵达", "邮政", "菜鸟",
                "健身", "运动", "瑜伽", "游泳", "健身房", "Keep", "房租", "住房", "房贷", "房产", "宠物", "猫粮", "狗粮") -> "生活服务"
            // 7. 充值缴费
            text.hasAny("话费", "充值", "会员", "流量", "电费", "水费", "网费",
                "燃气", "煤气", "宽带", "物业", "校园卡", "缴费", "有线电视", "暖气") -> "充值缴费"
            // 8. 其他
            else -> "其他"
        }
    }

    private fun mapPlatformCategory(platformCategory: String, party: String, goods: String): String? {
        val cat = platformCategory.trim()
        if (cat.isBlank()) return null
        val detail = "$party $goods"

        val contradiction = findContradiction(cat, detail)
        if (contradiction != null) return null

        return when {
            cat.hasAny("餐饮美食", "美食", "小吃", "快餐", "饮品", "咖啡", "奶茶", "茶饮") -> "餐饮美食"
            cat.hasAny("交通出行", "公共交通", "公交地铁", "打车", "网约车", "出租车") -> "交通出行"
            cat.hasAny("日用百货", "超市", "便利店", "杂货") -> "日用百货"
            cat.hasAny("生活缴费", "公共事业") -> "充值缴费"
            cat.hasAny("充值", "缴费") -> "充值缴费"
            cat.hasAny("生活服务") -> {
                when {
                    detail.hasAny("水电", "水费", "电费", "燃气", "话费", "宽带", "物业", "暖气") -> "充值缴费"
                    detail.hasAny("洗衣", "干洗", "清洁", "家政", "保洁", "快递", "物流", "运费") -> "生活服务"
                    else -> "生活服务"
                }
            }
            cat.hasAny("文化休闲", "娱乐", "电影", "游戏", "旅游") -> "生活服务"
            cat.hasAny("医疗健康", "医疗", "医药", "医院") -> "生活服务"
            cat.hasAny("运动健身", "运动", "健身", "体育") -> "生活服务"
            cat.hasAny("通讯物流", "通讯", "物流", "快递") -> "生活服务"
            cat.hasAny("住房物业", "住房", "物业", "房租", "房产") -> "生活服务"
            cat.hasAny("洗涤", "洗衣", "家政") -> "生活服务"
            cat.hasAny("教育培训", "教育", "考试", "培训") -> "校园生活"
            cat.hasAny("服饰美容", "服饰", "美妆", "美容", "护肤") -> "网购购物"
            cat.hasAny("数码家电", "数码", "家电", "电子", "手机", "电脑") -> "网购购物"
            cat.hasAny("汽车", "加油", "停车", "养车") -> "交通出行"
            cat.hasAny("投资理财", "理财", "基金", "保险", "证券") -> "其他"
            cat.hasAny("宠物", "宠物用品") -> "其他"
            cat.hasAny("转账", "红包") -> "其他"
            cat.hasAny("退款", "返现") -> "退款"
            else -> null
        }
    }

    private fun findContradiction(platformCategory: String, detail: String): String? {
        return when {
            platformCategory.hasAny("餐饮美食", "美食") && detail.hasAny("洗衣", "洗烘", "干洗", "U净", "Ujing", "洗鞋", "清洁", "家政", "保洁", "快递", "物流", "顺丰") -> "生活服务"
            platformCategory.hasAny("日用百货") && detail.hasAny("外卖", "餐", "饭", "面", "粉", "麻辣烫", "炸鸡", "汉堡", "披萨") -> "餐饮美食"
            platformCategory.hasAny("交通出行") && detail.hasAny("外卖", "餐", "饭", "咖啡", "奶茶") -> "餐饮美食"
            else -> null
        }
    }

    private fun parseTime(cell: Cell?): Long {
        if (cell == null) return System.currentTimeMillis()
        if (cell.cellType == CellType.NUMERIC) {
            val numeric = cell.numericCellValue
            val asLong = numeric.toLong()
            if (asLong in 19000101L..21001231L) {
                parseDateText(asLong.toString())?.let { return it }
            }
            if (numeric in 20000.0..60000.0) {
                return DateUtil.getJavaDate(numeric).time
            }
        }

        val raw = readCell(cell)
        parseDateText(raw)?.let { return it }
        raw.toDoubleOrNull()?.let { numeric ->
            val asLong = numeric.toLong()
            if (asLong in 19000101L..21001231L) parseDateText(asLong.toString())?.let { return it }
            if (numeric in 20000.0..60000.0) return DateUtil.getJavaDate(numeric).time
        }
        return System.currentTimeMillis()
    }

    private fun parseDateText(value: String): Long? {
        val raw = value.trim()
            .replace("年", "-")
            .replace("月", "-")
            .replace("日", "")
            .replace(".", "-")
        val compact = raw.replace(Regex("[^0-9]"), "")
        if (compact.length == 8) {
            parseByPatterns(compact, listOf("yyyyMMdd"))?.let { return it }
        }
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/M/d HH:mm:ss",
            "yyyy-M-d HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/M/d HH:mm",
            "yyyy-M-d HH:mm",
            "yyyy-MM-dd",
            "yyyy/M/d",
            "yyyy-M-d",
            "MM/dd/yyyy",
            "M/d/yyyy"
        )
        return parseByPatterns(raw, patterns)
    }

    private fun parseByPatterns(value: String, patterns: List<String>): Long? {
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.CHINA)
                format.isLenient = false
                val parsed = format.parse(value)?.time ?: continue
                val year = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = parsed }.get(Calendar.YEAR)
                if (year in 2000..2100) return parsed
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun parseAmount(value: String): Double {
        return parseSignedAmount(value).let { kotlin.math.abs(it) }
    }

    private fun parseSignedAmount(value: String): Double {
        val normalized = value
            .replace("¥", "")
            .replace("￥", "")
            .replace("元", "")
            .replace(",", "")
            .replace("+", "")
            .trim()
        val negative = normalized.startsWith("-") || normalized.startsWith("(") && normalized.endsWith(")")
        val cleaned = normalized.replace("(", "").replace(")", "")
        val amount = cleaned.toDoubleOrNull() ?: 0.0
        return if (negative) -kotlin.math.abs(amount) else amount
    }

    private fun isInvalidStatus(status: String): Boolean {
        return status.hasAny("失败", "关闭", "取消")
    }

    private fun readCell(cell: Cell?): String {
        if (cell == null) return ""
        return formatter.formatCellValue(cell)
            .replace('\u00A0', ' ')
            .trim()
    }

    private fun normalizeHeader(value: String): String {
        return value.replace(Regex("[\\s/()（）:：]+"), "")
    }

    private fun Map<String, Int>.firstIndexOf(vararg names: String): Int? {
        return names.firstNotNullOfOrNull { this[normalizeHeader(it)] }
    }

    private fun String.hasAny(vararg keywords: String): Boolean {
        return keywords.any { keyword -> contains(keyword, ignoreCase = true) }
    }
}
