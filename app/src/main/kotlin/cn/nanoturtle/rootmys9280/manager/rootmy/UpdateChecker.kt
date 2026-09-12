package cn.nanoturtle.rootmys9280.manager.rootmy

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 检查并获取应用更新。
 *
 * 走**项目自建服务器**而不是 GitHub：国内访问 GitHub Releases 经常失败或极慢，
 * 而服务器本来就在收集运行日志，顺手做分发最省事（`update.php` 返回版本清单，
 * APK 放在 `rms24_api/dl/`）。GitHub 作为镜像保留在清单里，云端不可用时还能兜底。
 */
object UpdateChecker {

    private const val MANIFEST_URL = "https://blog.nanoturtle.cn/rms24_api/update.php"

    /** 一次检查的结果。 */
    data class Release(
        val versionName: String,
        val versionCode: Int,
        val size: Long,
        val sha256: String,
        val notes: String,
        val url: String,
        val mirror: String,
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** 当前安装的 versionCode。 */
    fun currentVersionCode(context: Context): Int =
        runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    info.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION") info.versionCode
                }
            }
            .getOrDefault(0)

    /** 当前安装的 versionName。 */
    fun currentVersionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()

    /**
     * 查询最新版本。
     *
     * @return 最新版本信息；失败抛异常，由调用方决定提示方式。
     */
    fun fetchLatest(): Release {
        val request =
            Request.Builder()
                .url(MANIFEST_URL)
                .header("User-Agent", "RootMyS24-updater")
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            if (!json.optBoolean("ok")) error(json.optString("error").ifBlank { "bad_manifest" })
            return Release(
                versionName = json.optString("versionName"),
                versionCode = json.optInt("versionCode"),
                size = json.optLong("size"),
                sha256 = json.optString("sha256"),
                notes = json.optString("notes"),
                url = json.optString("url"),
                mirror = json.optString("mirror"),
            )
        }
    }

    /**
     * 交给系统下载器下载 APK。
     *
     * 用 DownloadManager 而不是自己写流：断点续传、通知栏进度、后台继续都由系统负责，
     * 完成后拿它的 content:// 直接拉起安装器，也不需要存储权限或 FileProvider。
     *
     * @return 下载任务 id，用于监听完成。
     */
    fun startDownload(context: Context, url: String, fileName: String): Long {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request =
            DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("RootMyS24")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        return manager.enqueue(request)
    }

    /**
     * 下载完成后的安装 Intent。
     *
     * @return 下载文件不可用时返回 null。
     */
    fun installIntent(context: Context, downloadId: Long): Intent? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(downloadId) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
