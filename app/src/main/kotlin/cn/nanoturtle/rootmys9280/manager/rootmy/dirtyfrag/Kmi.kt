package cn.nanoturtle.rootmys9280.manager.rootmy.dirtyfrag

/**
 * 内核 KMI（Kernel Module Interface）识别与利用引擎分流。
 *
 * 为什么需要这一层：**同一个漏洞在不同内核线上是否可达完全不同**。
 * 我们自己的实测结论（见 samsung_root_research 的 FEASIBILITY_VERDICT.md）：
 *
 * | 内核线 | DirtyFrag（就地解密写页缓存）| GhostLock（CVE-2026-43499）|
 * |---|---|---|
 * | 5.10 / 5.15 | ✅ 可达（老式 `ip_append_page` 直接挂页）| 需 per-device 定标 |
 * | 6.1.75+ | ❌ 不可达（`__ip_append_data` 缺 `MSG_SPLICE_PAGES`）| ✅ 主线 |
 * | 6.5 / 6.6+ | ✅ 可达（`splice_to_socket`）| — |
 *
 * 所以引擎选择必须以 KMI 为准，而不是"机型"或"固件前缀"：
 * S9280 全系落在 6.1 那条裂缝里，只能用 GhostLock；而 W23（5.10.236）
 * 虽然同属心系天下，却必须走 DirtyFrag。
 *
 * KMI 的字符串形态与 Android GKI 一致：`android{API}-{major}.{minor}`，
 * 例如 `android12-5.10`、`android14-6.1`。
 */
object Kmi {

    /** 支持的引擎。 */
    enum class Engine {
        /** CVE-2026-43499（rt_mutex waiter 伪造）+ KernelSU late-load。 */
        GHOSTLOCK,

        /** ESP-in-UDP 就地解密写页缓存 + permissive LKM + ksud late-load。 */
        DIRTYFRAG,
    }

    /**
     * 从内核 release 串解析 KMI。
     *
     * 输入形如：
     * - `5.10.236-android12-9-2755199-abW9023ZCSAIZF1` → `android12-5.10`
     * - `6.1.145-android14-11-3254743-abS9280ZCS6DZH3` → `android14-6.1`
     * - `6.12.76-android16-8-...`                      → `android16-6.12`
     *
     * 解析失败返回 null（判据不足时上层应保守处理，不要猜）。
     */
    fun fromKernelRelease(release: String): String? {
        val text = release.trim()
        if (text.isEmpty()) return null

        // 1) 内核主次版本：取开头连续的三段数字，只用前两段。
        val kernel = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)").find(text) ?: return null
        val major = kernel.groupValues[1]
        val minor = kernel.groupValues[2]

        // 2) Android API 代号：优先取串里的 "androidNN"（三星内核串一定带）。
        val apiFromKernel = Regex("android(\\d+)").find(text)?.groupValues?.get(1)

        // 3) 兜底：用 ro.build.version.release 的 SDK 映射（少数内核串不带 androidNN）。
        val api = apiFromKernel ?: return null

        return "android$api-$major.$minor"
    }

    /**
     * 该 KMI 上 DirtyFrag 原语是否可达。
     *
     * 依据是内核 UDP 发送路径的形态（详见 FEASIBILITY_VERDICT.md 的可达性矩阵），
     * 而不是"试出来的概率"——这条判据是从 vmlinux 反汇编 + 真机探针得出的。
     */
    fun dirtyFragReachable(kmi: String?): Boolean {
        if (kmi.isNullOrBlank()) return false
        // 老式 ip_append_page：直接挂页，原语成立。
        if (kmi.startsWith("android12-5.10")) return true
        if (kmi.startsWith("android13-5.10")) return true
        if (kmi.startsWith("android13-5.15")) return true
        if (kmi.startsWith("android14-5.15")) return true
        // 6.5+ 起 splice_to_socket，同样可达。
        if (kmi.startsWith("android15-6.6")) return true
        if (kmi.startsWith("android16-6.6")) return true
        if (kmi.startsWith("android16-6.12")) return true
        if (kmi.startsWith("android17-6.12")) return true
        // 6.1 系（android14-6.1）：半截回移把 MSG_SPLICE_PAGES 打断，页进不了 skb。
        // 明确不可达，避免用户白跑一次还污染页缓存。
        return false
    }

    /**
     * 为该 KMI 选择引擎。
     *
     * 注意 GhostLock 仍然是所有机型的可用路径（只要定标匹配），
     * 所以这里只在 **DirtyFrag 可达** 时才切过去；否则回落到 GhostLock。
     */
    fun engineFor(kmi: String?): Engine =
        if (dirtyFragReachable(kmi)) Engine.DIRTYFRAG else Engine.GHOSTLOCK

    /** DirtyFrag 的 permissive LKM 资产名（按 KMI 选取，与 GhostSam 的命名保持一致）。 */
    fun dirtyFragLkmAsset(kmi: String): String = "dfr_lkm-$kmi.ko"
}
