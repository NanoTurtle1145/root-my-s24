package cn.nanoturtle.rootmys9280.manager.ui.screens.splash

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import android.app.Application
import android.os.Build
import android.os.SystemClock
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import kotlinx.coroutines.flow.first
import cn.nanoturtle.rootmys9280.manager.logI
import cn.nanoturtle.rootmys9280.manager.logW

/** 图案淡入/缩放时长。放行闸门时图案必须已经画完，否则会出现"半张图"闪一下。 */
private const val ART_MS = 320L

/** 最短停留：与 daemon 握手**并行**计时，不再串行相加。 */
private const val MIN_SPLASH_MS = 320L

/**
 * 寄生模式（被注入到 com.android.shell）下等 daemon binder 的上限。
 *
 * 那种模式确实有 daemon 会来握手，值得等一段；但仍然从原来的 2500ms 压到 1200ms。
 */
private const val DAEMON_TIMEOUT_MS = 1_200L

/**
 * 独立进程（用户点图标启动，免解锁流程走 Shizuku/无线调试）下的等待窗口。
 *
 * 实测：这种启动**根本不会有 daemon binder 到来**，闸门却每次都等满上限——
 * 真机日志 `splash: handoff after 1202ms` 就是撞在 1200ms 上限上的结果，
 * 这才是"开 App 慢"的主因。binder 是 StateFlow，真晚到了顶栏也会自己更新，
 * 所以这里只留一个很短的窗口，够 KernelSU 挂钩子用即可。
 */
private const val BINDER_WINDOW_MS = 400L

/**
 * 当前是否运行在寄生进程里（被注入 com.android.shell）而不是独立进程。
 *
 * 两种模式的握手预期完全不同，所以不能用同一个等待窗口。
 */
private fun isParasiticProcess(): Boolean = runCatching {
    val pkg = ServiceLocator.context.packageName
    val name =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n')
        }
    name != pkg
}.getOrDefault(false)

/** 闸门放行时的交叉淡出时长。 */
private const val CROSSFADE_MS = 220

/**
 * The Winged Victory, fading and scaling in — Vector, from *Victoria*.
 *
 * 交棒条件 = 「图案至少完整显示一次」**且**「binder 已就绪（或等满上限）」。
 *
 * 两者**并行**计时：以前是握手完再 delay(800)，每次开 App 都固定多付 800ms，
 * 叠加 2.5s 的上限后最坏要 3.3 秒才见到界面（用户反馈"splash 太慢"就是这个）。
 * 图案时长压到 [ART_MS] 后，放行那一刻图案已经画完，不会露出半张图。
 *
 * binder 晚到不会导致状态错乱：顶栏订阅的是 StateFlow，binder 到达后自己会更新。
 */
@Composable
fun SplashGate(content: @Composable () -> Unit) {
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val startAt = SystemClock.elapsedRealtime()
        coroutineScope {
            // 图案至少完整显示一次
            launch { delay(MIN_SPLASH_MS) }
            // 与图案并行地等 binder，等不到就先放行。
            // 窗口按进程模式取：寄生模式有 daemon 会来；独立进程基本等不到，短窗即可。
            val window = if (isParasiticProcess()) DAEMON_TIMEOUT_MS else BINDER_WINDOW_MS
            launch {
                val bound = withTimeoutOrNull(window) { ServiceLocator.service.first { it != null } }
                if (bound == null) {
                    logI("splash: no daemon binder within ${window}ms, continuing unactivated")
                }
            }
        }
        ready = true
        logI("splash: handoff after ${SystemClock.elapsedRealtime() - startAt}ms")
    }

    Crossfade(targetState = ready, animationSpec = tween(CROSSFADE_MS), label = "splashHandoff") { done ->
        if (done) content() else WingedVictory()
    }
}

/** The splash artwork, also summoned by the header's easter egg. */
@Composable
fun WingedVictory() {
    var started by remember { mutableStateOf(false) }
    val alpha by
        animateFloatAsState(
            targetValue = if (started) 1f else 0f,
            animationSpec = tween(durationMillis = ART_MS.toInt()),
            label = "splashAlpha",
        )
    val scale by
        animateFloatAsState(
            targetValue = if (started) 1f else 0.8f,
            animationSpec = tween(durationMillis = ART_MS.toInt()),
            label = "splashScale",
        )

    val appName = stringResource(R.string.app_name)

    LaunchedEffect(Unit) { started = true }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .semantics { contentDescription = appName },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = Color.Unspecified,
            // The drawable is a 512px square whose subject runs in the safe middle, so it is given
            // 92% of both dimensions and fitted inside — which keeps its proportions in portrait
            // and landscape without stretching or clipping.
            modifier = Modifier.fillMaxSize(0.92f).scale(scale).alpha(alpha),
        )
    }
}
