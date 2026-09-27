package cn.nanoturtle.rootmys9280.manager.rootmy

import android.content.Context
import java.util.concurrent.TimeUnit
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 讨论区入口（网页版）。
 *
 * ## 设计：客户端不留任何秘密
 *
 * App 是开源的，所以**口令不能进 APK**（也不能放"可离线校验的东西"，那等于把可爆破的
 * 校验物塞进客户端）。这里只做三件事：
 *
 *  1. 本地保存用户自己输入的口令（App 私有目录，只在自己设备上）；
 *  2. 拿口令去服务端换一张**短期票据**（HMAC 签名 + 过期时间）；
 *  3. 用带票据的 URL 打开网页版讨论区（走现有的 WebScreen）。
 *
 * 于是：口令藏在服务端、随时可换、旧口令立刻失效；客户端伪造不了票据。
 */
object Forum {

    private const val GATE = "https://blog.nanoturtle.cn/rms24_api/forum_gate.php"
    private const val PREFS = "forum"
    private const val KEY_TOKEN = "token"

    /** 服务端返回的错误码 → 给用户看的话。未知码原样带出来，方便排查。 */
    fun describe(code: String): String = when (code.trim()) {
        "bad_token" -> "口令不正确"
        "rate_limited" -> "尝试过于频繁，请稍后再试"
        "no_secret" -> "服务端尚未配置讨论区口令"
        "no_ticket", "bad_ticket" -> "票据无效，请重新输入口令"
        "expired" -> "票据已过期，请重新输入口令"
        else -> code
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun savedToken(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, "")
            .orEmpty()

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    fun clearToken(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TOKEN).apply()
    }

    /**
     * 用口令换票据 URL。失败抛异常，消息已可由 [describe] 解释。
     *
     * @param installId 安装标识，仅用于服务端限速与审计
     */
    fun requestTicket(context: Context, token: String, installId: String): String {
        val body =
            FormBody.Builder()
                .add("token", token.trim())
                .add("install", installId)
                .build()
        val request =
            Request.Builder()
                .url(GATE)
                .post(body)
                .header("User-Agent", "RootMyS24-forum")
                .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: error("HTTP ${response.code}")
            if (!json.optBoolean("ok")) {
                error(json.optString("error").ifBlank { "HTTP ${response.code}" })
            }
            return json.optString("url").ifBlank { error("bad_response") }
        }
    }
}
