package cn.nanoturtle.rootmys9280.manager.rootmy

import android.content.Context
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 服务端公告（announcer）。
 *
 * 数据来自 `rms24_api/announce.php`（人工编辑服务端的 `announcements.json`，
 * App 不需要发版就能改文案/上下线）。设计上有两条硬约束：
 *
 * 1. **服务端只能"建议"，执行权在客户端**：公告里的 `link` 只放行 http/https；
 *    `action` 必须命中 [Action] 白名单，未知动作一律不执行。
 *    绝不能做成"服务端下发什么就执行什么"——公告接口一旦被注入，
 *    就等于给攻击者一个跳转/触发跳板。
 * 2. **拉取失败不影响任何主流程**：网络错误、JSON 坏掉、服务端下线，
 *    一律当成"没有公告"，主页照常显示。
 *
 * 已关闭的公告按 id 记在本地 prefs 里；服务端换新 id 就会重新出现，
 * 所以"想让用户再看一次"只要改 id（或用 from/until 控制有效期）。
 */
object Announcer {

    private const val TAG = "Announcer"
    private const val ENDPOINT = "https://blog.nanoturtle.cn/rms24_api/announce.php"
    private const val PREFS = "announcer"
    private const val KEY_DISMISSED = "dismissed_ids"

    /** 客户端支持的应用内动作（白名单）。 */
    enum class Action {
        DISMISS,
        OPEN_ABOUT,
        NONE;

        companion object {
            /** 未知动作返回 [NONE]，宁可什么都不做也不误触发。 */
            fun of(raw: String): Action = when (raw.trim().lowercase()) {
                "dismiss" -> DISMISS
                "open_about" -> OPEN_ABOUT
                else -> NONE
            }
        }
    }

    /** 公告配色档位；服务端给未知值时按 INFO 处理。 */
    enum class Level {
        INFO, WARN, CRITICAL, UPDATE;

        companion object {
            fun of(raw: String): Level = when (raw.trim().lowercase()) {
                "warn", "warning" -> WARN
                "critical", "error" -> CRITICAL
                "update" -> UPDATE
                else -> INFO
            }
        }
    }

    data class Item(
        val id: String,
        val level: Level,
        val title: String,
        val body: String,
        val link: String?,
        val linkText: String,
        val action: Action,
        val actionText: String,
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** 只放行 http/https：任何其它 scheme（intent:、file:、market:…）都丢掉。 */
    private fun safeLink(raw: String): String? {
        val t = raw.trim()
        return t.takeIf {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        }
    }

    /** 拉取公告列表。失败抛异常，由 [visible] 兜住。 */
    private fun fetch(): List<Item> {
        val request =
            Request.Builder()
                .url(ENDPOINT)
                .header("User-Agent", "RootMyS24-announcer")
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            if (!json.optBoolean("ok")) error("bad_response")
            val array = json.optJSONArray("items") ?: return emptyList()
            val out = ArrayList<Item>(array.length())
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                val title = o.optString("title").trim()
                if (id.isEmpty() || title.isEmpty()) continue
                out += Item(
                    id = id,
                    level = Level.of(o.optString("level")),
                    title = title,
                    body = o.optString("body"),
                    link = safeLink(o.optString("link")),
                    linkText = o.optString("linkText").ifBlank { "查看详情" },
                    action = Action.of(o.optString("action")),
                    actionText = o.optString("actionText").ifBlank { "知道了" },
                )
            }
            return out
        }
    }

    /** 本地记录"已关闭"的公告 id。 */
    fun dismissedIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DISMISSED, "")
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /** 关闭一条公告（本地生效；服务端换 id 后会重新出现）。 */
    fun dismiss(context: Context, id: String) {
        val now = dismissedIds(context) + id
        // 只留最近 50 条，避免 prefs 无限增长
        val kept = now.toList().takeLast(50).joinToString(",")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED, kept)
            .apply()
    }

    /**
     * 拉取并在本地过滤掉已关闭的公告。
     *
     * 任何异常都吞掉并返回空列表——公告是锦上添花，绝不能因为它影响主页。
     * 调用方请放在 IO 线程。
     */
    /**
     * 一次拉取的两种视图。
     *
     * 之所以要分开：主页只显示"未关闭"的条目，但**入口本身要按"服务端是否还有公告"来决定**
     * —— 否则用户把公告逐条关掉之后，连"查看全部"都一起消失，公告就再也找不回来了
     * （2026-09-27 实测踩到：fetched=3 dismissed=3 → 主页整块空白）。
     */
    data class Feed(val all: List<Item>, val fresh: List<Item>)

    fun feed(context: Context): Feed = runCatching {
        val dismissed = dismissedIds(context)
        val all = fetch()
        android.util.Log.i(TAG, "fetched=${all.size} dismissed=${dismissed.size}")
        Feed(all = all, fresh = all.filterNot { it.id in dismissed })
    }.getOrElse {
        android.util.Log.w(TAG, "fetch failed: ${it}")
        Feed(emptyList(), emptyList())
    }

    /** 兼容旧调用：只要未关闭的那些。 */
    fun visible(context: Context): List<Item> = feed(context).fresh

    /** 清掉本地"已关闭"记录，让所有公告重新出现（列表页的「全部恢复」）。 */
    fun clearDismissed(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
