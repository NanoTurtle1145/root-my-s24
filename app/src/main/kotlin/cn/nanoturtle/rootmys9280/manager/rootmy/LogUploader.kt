package cn.nanoturtle.rootmys9280.manager.rootmy

import android.content.Context
import android.os.Build
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 运行日志上报：把一次 Root 运行的日志与机型/固件元数据 POST 到自建收集服务。
 *
 * 设计上分两层，收集端换地址不用改代码：
 * - [DEFAULT_ENDPOINT] 是编译进包的默认地址（留空表示尚未配置，此时上传直接跳过并提示）。
 * - 运行时可经 [setEndpoint] 覆盖（写在 SharedPreferences 里），方便自建/迁移。
 *
 * 什么时候传由 [LogSharing] 决定：ALWAYS 自动传，MANUAL 由调用方先问用户，NEVER 一律不传。
 * 这里不读偏好、不做确认——只负责「给定内容和地址就发出去」，
 * 判断交给调用方，避免上报逻辑散落在两个地方。
 */
object LogUploader {

    /** 自建日志收集服务的地址。待收集端就绪后填入（形如 `https://host/api/logs`）。 */
    const val DEFAULT_ENDPOINT = ""

    private const val PREFS = "settings"
    private const val KEY_ENDPOINT = "log_upload_endpoint"

    /** 单次运行日志通常几十 KB，10 秒足够；失败不重试，避免在弱网下拖住 UI。 */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun endpoint(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENDPOINT, null)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_ENDPOINT

    fun setEndpoint(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENDPOINT, url.trim())
            .apply()
    }

    fun isConfigured(context: Context): Boolean = endpoint(context).isNotBlank()

    /**
     * 上报一次运行的日志。
     *
     * @param log 完整日志文本（含头部版本信息，便于收集端直接归档）。
     * @param buildTag 机型/固件标识，如 `S9280ZCS6DZF2`，用于按固件聚合分析。
     * @param outcome 结果简述（成功/失败阶段），便于收集端先做粗筛。
     * @return 成功返回 null，失败返回可直接展示给用户的原因。
     */
    suspend fun upload(
        context: Context,
        log: String,
        buildTag: String,
        outcome: String,
    ): String? {
        val url = endpoint(context)
        if (url.isBlank()) return "not-configured"

        val payload =
            JSONObject()
                .put("buildTag", buildTag)
                .put("outcome", outcome)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("android", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("appVersion", appVersion(context))
                .put("log", log)
                .toString()

        return runCatching {
                val request =
                    Request.Builder()
                        .url(url)
                        .post(payload.toRequestBody(JSON))
                        .header("User-Agent", "RootMyS24-log-uploader")
                        .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) null else "HTTP ${response.code}"
                }
            }
            .getOrElse { it.message ?: it.javaClass.simpleName }
    }

    private fun appVersion(context: Context): String =
        runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                val code =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
                    else @Suppress("DEPRECATION") info.versionCode.toLong()
                "v${info.versionName} (build $code)"
            }
            .getOrDefault("unknown")
}
