package cn.nanoturtle.rootmys9280.manager.rootmy

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import java.security.MessageDigest
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
 *
 * ## 这一版修掉的两个真 bug（2026-09-27）
 *
 * 1. **固定目标文件名 ⇒ 第二次下载必然失败**：`setDestinationInExternalPublicDir` 指向已存在的
 *    文件时，DownloadManager 会让任务以 `ERROR_FILE_ALREADY_EXISTS` 结束；而调用处把 enqueue
 *    的异常吞掉了，界面一直显示"下载中"，用户看到的就是"点了没反应、再点也没反应"。
 *    → 改为每次目标文件名带唯一后缀。
 * 2. **完成回调绑在界面生命周期上**：`ACTION_DOWNLOAD_COMPLETE` 的接收器只在该页面处于组合中
 *    时有效，离开设置页就收不到 ⇒ 下载成功也没有安装弹窗。
 *    → 下载 id 与期望 sha256 落盘，任何一次回到前台都能用 `consumeIfFinished()` 查真实状态收尾。
 *
 * 另外补上了清单里本来就给了、却一直没用的 **sha256 校验**：装之前先验，避免中间被替换。
 */
object UpdateChecker {

    private const val MANIFEST_URL = "https://blog.nanoturtle.cn/rms24_api/update.php"

    private const val PENDING_PREFS = "update_pending"
    private const val PENDING_ID = "download_id"
    private const val PENDING_SHA = "expected_sha256"

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

    /** 下载收尾的判定结果；由调用方决定怎么提示。 */
    sealed interface Completion {
        /** 还在下 / 没有待收尾的任务：什么都不用做。 */
        object None : Completion

        /** 下载完成且校验通过，可以拉起安装器（安装要在主线程调用 [install]）。 */
        data class Ready(val id: Long, val uri: Uri) : Completion

        /** 下载失败。[hint] 是一个短英文标记，供调用方套进本地化文案。 */
        data class Failed(val code: Int, val hint: String) : Completion

        /** 下载完成但 sha256 对不上——不要安装。 */
        object ShaMismatch : Completion
    }

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
     * 目标文件名唯一化。
     *
     * 固定文件名会在第二次下载时撞上 `ERROR_FILE_ALREADY_EXISTS`——而公共下载目录里的旧文件
     * 我们并没有权限直接删（那不是本 App 创建的文件），所以换个名字最省事也最可靠。
     */
    private fun uniqueName(fileName: String): String {
        val tag = System.currentTimeMillis().toString(36).takeLast(5)
        return if (fileName.endsWith(".apk", ignoreCase = true)) {
            fileName.dropLast(4) + "-" + tag + ".apk"
        } else {
            "$fileName-$tag"
        }
    }

    /**
     * 交给系统下载器下载 APK，并把这次下载登记成"待收尾"，供任何一次回到前台时结算。
     *
     * 用 DownloadManager 而不是自己写流：断点续传、通知栏进度、后台继续都由系统负责，
     * 完成后拿它的 content:// 直接拉起安装器，也不需要存储权限或 FileProvider。
     *
     * @return 下载任务 id，用于监听完成；入队失败会抛异常（调用方必须让用户看见）。
     */
    fun startDownload(context: Context, url: String, fileName: String, sha256: String): Long {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request =
            DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("RootMyS24")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setAllowedOverMetered(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, uniqueName(fileName))
        val id = manager.enqueue(request)
        context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(PENDING_ID, id)
            .putString(PENDING_SHA, sha256)
            .apply()
        return id
    }

    /** 是否还有一次下载等着收尾（界面可据此显示"下载中"）。 */
    fun hasPending(context: Context): Boolean =
        context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
            .getLong(PENDING_ID, -1L) > 0L

    private fun clearPending(context: Context) {
        context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** 把 DownloadManager 的失败原因翻译成一个短标记，供本地化文案使用。 */
    private fun hintOf(reason: Int): String = when (reason) {
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "file_exists"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "no_space"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "storage_missing"
        DownloadManager.ERROR_FILE_ERROR -> "file_error"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "http_data"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "redirect"
        DownloadManager.ERROR_CANNOT_RESUME -> "cannot_resume"
        DownloadManager.ERROR_UNKNOWN -> "unknown"
        else -> when (reason) {
            in 1000..1009 -> "network"
            in 1010..1099 -> "http_$reason"
            else -> "code_$reason"
        }
    }

    /** 下载文件的 sha256（读完即弃）。 */
    private fun sha256Of(context: Context, uri: Uri): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /**
     * 结算上一次下载：查真实状态（不依赖广播），必要时校验 sha256 并给出安装 Intent。
     *
     * **必须在处于前台时调用**（要拉起安装器 Activity）。调用方拿结果去提示即可；
     * 需要安装时本函数已经把安装器拉起来了。
     *
     * 这一函数只做 IO（查询 + 读文件算 sha256），**不要在主线程调用**；
     * 拿到 [Completion.Ready] 之后再回主线程调 [install]。
     */
    fun consumeIfFinished(context: Context): Completion {
        val prefs = context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(PENDING_ID, -1L)
        if (id <= 0L) return Completion.None

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // Kotlin 不允许在 lambda 里给 val 做确定赋值，所以用 var + 默认值
        var status = -1
        var reason = 0
        var found = false
        runCatching {
                manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        status = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        )
                        reason = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                        )
                        found = true
                    }
                }
            }
            .getOrElse { return Completion.None }
        if (!found) return Completion.None

        return when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val expect = prefs.getString(PENDING_SHA, "").orEmpty()
                val uri = manager.getUriForDownloadedFile(id)
                if (uri == null) {
                    clearPending(context)
                    return Completion.Failed(-1, "no_uri")
                }
                if (expect.isNotBlank()) {
                    val actual = sha256Of(context, uri)
                    if (actual == null || !actual.equals(expect, ignoreCase = true)) {
                        clearPending(context)
                        // 装之前先验：不匹配就绝不安装（可能是中间被替换或下载被截断）
                        runCatching { manager.remove(id) }
                        return Completion.ShaMismatch
                    }
                }
                clearPending(context)
                Completion.Ready(id, uri)
            }

            DownloadManager.STATUS_FAILED -> {
                clearPending(context)
                runCatching { manager.remove(id) }
                Completion.Failed(reason, hintOf(reason))
            }

            else -> Completion.None // 排队中 / 下载中
        }
    }

    /** 主线程调用：拉起系统安装器。 */
    fun install(context: Context, downloadId: Long): Boolean {
        val intent = installIntent(context, downloadId) ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 放弃当前待收尾的下载（用户点了"取消"之类）。 */
    fun cancelPending(context: Context) {
        val prefs = context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(PENDING_ID, -1L)
        if (id > 0L) {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            runCatching { manager.remove(id) }
        }
        clearPending(context)
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
