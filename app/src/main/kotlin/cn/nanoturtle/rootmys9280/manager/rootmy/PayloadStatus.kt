package cn.nanoturtle.rootmys9280.manager.rootmy

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 载荷在线状态（机型选择页的「已测试」标记与 KSU 版本清单）。
 *
 * 数据来自 `rms24_api/payloads.php` —— 服务端读 `payloads.json`，并合并真实运行
 * 日志（`run_logs`）统计出来的成功/失败次数。这么做的收益很直接：
 *
 * 1. **「已测试」不再是烧死在 APK 里的常量**。某个固件实测通过了，改服务端一个
 *    JSON 就能让所有已装 App 立刻显示「已实测」，不必发版；实测出问题也能立刻摘掉。
 * 2. **KSU 版本清单由服务端下发**。哪台机器有哪个版本的驱动（内核 vermagic 必须
 *    对得上），只有服务端知道；App 只负责列出与选择。
 * 3. 拉取失败**绝不影响主流程**：拿不到就沿用 APK 内置的默认值与默认驱动。
 *
 * 与公告（[Announcer]）同一条设计红线：服务端只能"建议"。这里下发的一切都只用于
 * 显示与选择，`asset` 必须命中 APK 内真实存在的资产名（[validate] 校验），
 * 否则丢弃——服务端被注入也不能让 App 去加载一个不存在/外来的驱动。
 */
object PayloadStatus {

    private const val TAG = "PayloadStatus"
    private const val ENDPOINT = "https://blog.nanoturtle.cn/rms24_api/payloads.php"
    private const val PREFS = "payload_status"
    private const val KEY_JSON = "json"
    private const val KEY_AT = "fetched_at"
    private const val KEY_SCHEMA = "schema"

    /**
     * 缓存契约版本。**改服务端字段时把它 +1**：否则已装 App 会继续用旧缓存，
     * 新字段（例如 `version` / `devices`）要等 TTL 到期才生效——上线当天就是这种坑。
     */
    private const val SCHEMA = 3

    /** 缓存有效期：6 小时。过期只影响"是否重新拉取"，不影响可用性。 */
    const val TTL_MS = 6 * 60 * 60 * 1000L

    /** KSU 驱动的一个可选版本。 */
    data class KsuOption(
        /** APK assets 里的资产名（如 `ksud-dzh3-32601`）。 */
        val asset: String,
        /** 展示名（如 `3.3.0`）；选项行会拼成 `KSU: 3.3.0`。 */
        val label: String,
        /** `stable` / `candidate` / `legacy`，仅用于展示与排序。 */
        val state: String,
        /**
         * 版本号（`3.2.5` / `3.3.0`）。选择是**按版本**记的，不是按资产名——
         * 同一个版本在不同固件上对应不同资产（国行是 ksud-selected，CZB2 是另一个）。
         */
        val version: String = "",
        /**
         * 该版本适用的设备构建码（如 `["*DZH3"]`）；空 = 不限。
         *
         * 内核模块的 vermagic 必须与设备内核逐字节一致，3.3.0 目前只有 DZH3 一份，
         * 所以这个限制由服务端下发而不是写死在 App 里。
         */
        val devices: List<String> = emptyList(),
    ) {
        val isStable: Boolean get() = state.equals("stable", ignoreCase = true)

        /** 本机是否适用。构建码未知时一律视为适用（判据不足就不打扰用户）。 */
        fun appliesTo(deviceBuildTag: String): Boolean {
            if (devices.isEmpty()) return true
            val tag = deviceBuildTag.trim().uppercase()
            if (tag.isEmpty() || tag == "UNKNOWN") return true
            return devices.any { raw ->
                val pat = raw.trim().uppercase()
                if (pat.isEmpty()) return@any false
                if (pat.startsWith("*")) {
                    tag.takeLast(pat.removePrefix("*").length) == pat.removePrefix("*")
                } else {
                    tag == pat
                }
            }
        }
    }

    /** 单个固化条目（固件载荷）的在线状态。 */
    data class Entry(
        /** 与 [RootViewModel.FirmwareVersion.name] 对应。 */
        val key: String,
        /** 服务端的「已实测」结论；null = 服务端没表态，沿用 APK 内置值。 */
        val tested: Boolean?,
        /** 补充说明（例如"静态审计通过、真机验证中"）。 */
        val note: String,
        /** 最近窗口内的成功次数（来自 run_logs）。 */
        val ok: Int,
        /** 最近窗口内的失败次数。 */
        val fail: Int,
        /** 最近一次成功时间（`yyyy-MM-dd`），空串表示没有。 */
        val lastOk: String,
        /** 该固件可用的 KSU 驱动版本，**首个为推荐**。 */
        val ksu: List<KsuOption>,
    ) {
        val reports: Int get() = ok + fail
        val rateText: String
            get() = if (reports <= 0) "" else "$ok/$reports"
    }

    data class Snapshot(
        val entries: Map<String, Entry> = emptyMap(),
        val fetchedAt: Long = 0L,
        /** true = 当前是本地缓存的结论（可能已过期或根本没拉到过）。 */
        val fromCache: Boolean = true,
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val _snapshot = MutableStateFlow(Snapshot())

    /** 当前状态；UI 直接 collect 这个（entries 为空 = 没有在线结论，走内置默认）。 */
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 解析服务端 JSON。
     *
     * 只接受 `entries.<key>.ksu[].asset` 里形如 `assets/ksud-*` 的资产名，
     * 其余字段缺失一律用中性默认值——服务端少给字段不等于客户端要崩。
     */
    fun parse(body: String): Map<String, Entry> {
        val json = JSONObject(body)
        if (!json.optBoolean("ok", true)) return emptyMap()
        val obj = json.optJSONObject("entries") ?: return emptyMap()
        val out = LinkedHashMap<String, Entry>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val o = obj.optJSONObject(key) ?: continue
            if (key.isBlank()) continue
            val ksu = ArrayList<KsuOption>()
            o.optJSONArray("ksu")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val asset = item.optString("asset").trim()
                    // 资产名白名单形状校验：必须是本 App 的 ksud 资产，避免服务端被注入后
                    // 让客户端去加载任意名字的东西
                    if (!asset.matches(Regex("^ksud-[A-Za-z0-9._-]{1,60}$"))) continue
                    val devices = ArrayList<String>()
                    item.optJSONArray("devices")?.let { arr ->
                        for (j in 0 until arr.length()) {
                            arr.optString(j).trim().takeIf { it.isNotEmpty() }?.let { devices += it }
                        }
                    }
                    ksu += KsuOption(
                        asset = asset,
                        label = item.optString("label").trim().ifBlank { asset },
                        state = item.optString("state").trim().ifBlank { "candidate" },
                        version = item.optString("version").trim(),
                        devices = devices,
                    )
                }
            }
            out[key] = Entry(
                key = key,
                tested = if (o.has("tested")) o.optBoolean("tested") else null,
                note = o.optString("note").trim(),
                ok = o.optInt("ok", 0),
                fail = o.optInt("fail", 0),
                lastOk = o.optString("lastOk").trim(),
                ksu = ksu.distinctBy { it.asset },
            )
        }
        return out
    }

    /** 读本地缓存（不联网）。 */
    fun cached(context: Context): Snapshot {
        val p = prefs(context)
        if (p.getInt(KEY_SCHEMA, 0) != SCHEMA) {
            // 契约已变：旧缓存里的字段不可信，直接当没有缓存
            android.util.Log.i(TAG, "cache schema outdated, will refetch")
            return Snapshot()
        }
        val body = p.getString(KEY_JSON, null) ?: return Snapshot()
        val at = p.getLong(KEY_AT, 0L)
        return runCatching { Snapshot(parse(body), at, fromCache = true) }
            .getOrElse {
                android.util.Log.w(TAG, "cached parse failed: $it")
                Snapshot()
            }
    }

    /** 阻塞拉取并写入缓存；失败抛异常，由 [load] 兜住。 */
    private fun fetchAndStore(context: Context): Snapshot {
        val request =
            Request.Builder()
                .url(ENDPOINT)
                .header("User-Agent", "RootMyS24-payloads")
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val parsed = parse(body)
            if (parsed.isEmpty()) error("empty_payloads")
            val now = System.currentTimeMillis()
            prefs(context)
                .edit()
                .putString(KEY_JSON, body)
                .putLong(KEY_AT, now)
                .putInt(KEY_SCHEMA, SCHEMA)
                .apply()
            android.util.Log.i(TAG, "refreshed: ${parsed.size} entries")
            return Snapshot(parsed, now, fromCache = false)
        }
    }

    /**
     * 先给缓存的结论，再按需刷新。
     *
     * 调用方负责放在 IO 线程；任何异常都吞掉——机型页照常用内置默认值工作。
     */
    fun load(context: Context, force: Boolean = false): Snapshot {
        val cache = cached(context)
        // 先发布缓存，UI 立刻有内容可显示
        if (cache.entries.isNotEmpty()) _snapshot.value = cache
        val stale = System.currentTimeMillis() - cache.fetchedAt > TTL_MS
        if (!force && cache.entries.isNotEmpty() && !stale) return cache
        return runCatching { fetchAndStore(context) }
            .onSuccess { _snapshot.value = it }
            .getOrElse {
                android.util.Log.w(TAG, "refresh failed: $it")
                cache
            }
    }

    /** 测试用：清掉缓存。 */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        _snapshot.value = Snapshot()
    }
}
