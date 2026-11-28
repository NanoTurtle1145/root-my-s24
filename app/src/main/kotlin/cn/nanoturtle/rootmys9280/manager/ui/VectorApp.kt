package cn.nanoturtle.rootmys9280.manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import cn.nanoturtle.rootmys9280.manager.ui.navigation.LocalNavigator
import cn.nanoturtle.rootmys9280.manager.ui.navigation.Navigator
import cn.nanoturtle.rootmys9280.manager.ui.navigation.PanelBar
import cn.nanoturtle.rootmys9280.manager.ui.navigation.PanelEditDone
import cn.nanoturtle.rootmys9280.manager.ui.navigation.FloatingPanelNav
import cn.nanoturtle.rootmys9280.manager.ui.navigation.TopLevelRoute
import cn.nanoturtle.rootmys9280.manager.ui.navigation.rememberNavigator
import cn.nanoturtle.rootmys9280.manager.ui.screens.rootflow.RootFlowScreen
import cn.nanoturtle.rootmys9280.manager.ui.screens.logs.LogsScreen
import cn.nanoturtle.rootmys9280.manager.ui.screens.settings.SettingsScreen
import cn.nanoturtle.rootmys9280.manager.ui.screens.about.AboutScreen
import cn.nanoturtle.rootmys9280.manager.ui.screens.onboarding.OnboardingScreen
import cn.nanoturtle.rootmys9280.manager.rootmy.OnboardingPrefs

/**
 * The app shell.
 *
 * [NavigationSuiteScaffold] picks the navigation container from the window size — a bottom bar on a
 * phone, a rail when there is width to spare. Which panels that container holds, in which order, is
 * the reader's — see NavPanels — and there is a third arrangement it can take, a ball floating over
 * the content with no container at all.
 */
@Composable
fun VectorApp() {
    val navigator = rememberNavigator()

    // 首次启动先走引导（必读指南 / 通知权限 / 日志共享），做完写标记就不再出现。
    // 放在最外层整体替换，而不是塞进导航栈：引导期间不该有底部栏，也不该能被返回键绕过。
    val context = androidx.compose.ui.platform.LocalContext.current
    var onboardingDone by remember { mutableStateOf(OnboardingPrefs.isDone(context)) }
    if (!onboardingDone) {
        OnboardingScreen(onFinished = { onboardingDone = true })
        return
    }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        // Sheets and dialogs are their own windows, which reset the language override; this
        // re-applies it inside them, exactly as VectorAlertDialog does.
        cn.nanoturtle.rootmys9280.ui.LocalDialogLocalizer provides { content ->
            cn.nanoturtle.rootmys9280.manager.ui.theme.LocalizedOverlay { content() }
        },
    ) {
        val settings = ServiceLocator.settings
        val floating by settings.floatingNav.collectAsStateWithLifecycle()
        val editing = navigator.editingPanels
        val atRoot = !navigator.canGoBack

        // Navigation3 defaults to a 700 ms cross-fade with no direction; expressive page-level
        // motion is 150–500 ms, and going a level deeper is a slide, not a fade. Specs are read
        // here because a transitionSpec lambda is not a composable.
        val pageEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val settleEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val pageSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()

        val suiteState = rememberNavigationSuiteScaffoldState()
        LaunchedEffect(atRoot) { if (atRoot) suiteState.show() else suiteState.hide() }

        val suiteType =
            if (floating && !editing) NavigationSuiteType.None
            else NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())

        NavigationSuiteScaffold(
            navigationItems = {
                if (suiteType != NavigationSuiteType.None) {
                    PanelBar(
                        panels = navigator.panels,
                        current = navigator.currentTopLevel,
                        editing = editing,
                        suiteType = suiteType,
                        onSelect = { route -> navigator.switchTo(route) },
                        onEdit = { navigator.editingPanels = true },
                        onToggleHidden = { key, hidden -> navigator.setPanelHidden(key, hidden) },
                        onMove = { from, to -> navigator.movePanel(from, to) },
                    )
                }
            },
            navigationSuiteType = suiteType,
            state = suiteState,
            primaryActionContent = {
                if (editing) PanelEditDone(onDone = { navigator.editingPanels = false })
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                NavDisplay(
                    // 浮动导航模式（suiteType = None）下 NavigationSuiteScaffold 不消费任何 inset，
                    // 也不会为系统导航栏留出高度：内容会一直铺到窗口底边，于是贴底的可点元素
                    // （日志页的软重启按钮、列表最后一行、跳转 FAB…）被三键导航栏或手势条盖住。
                    // 导航栏/导航轨模式下脚手架已经消费了底部 inset，这里的 padding 为 0，
                    // 所以无条件加上是安全的，也是唯一不需要每个页面各写一遍的位置。
                    // 球本身不动：FloatingPanelNav 在同一个 Box 的兄弟节点里，仍按 safeDrawing 定位。
                    modifier = Modifier.navigationBarsPadding(),
                    backStack = navigator.backStack,
                    onBack = { navigator.back() },
                    // Peers and top-level panels: quick, no direction.
                    transitionSpec = {
                        (fadeIn(pageEffects) + scaleIn(pageEffects, initialScale = 0.98f))
                            .togetherWith(fadeOut(pageEffects))
                    },
                    // Going a level deeper: a small shared-axis slide, the "forward and backward"
                    // pattern, with a slower spring so the direction is legible.
                    popTransitionSpec = {
                        (fadeIn(settleEffects) + slideInHorizontally(pageSpatial) { it / 8 })
                            .togetherWith(fadeOut(settleEffects))
                    },
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    entryProvider = entryProvider { registerRoutes(navigator) },
                )
                if (floating && !editing && atRoot) {
                    FloatingPanelNav(
                        panels = navigator.panels,
                        current = navigator.currentTopLevel,
                        onSelect = { route -> navigator.switchTo(route) },
                    )
                }
            }
        }

        BackHandler(enabled = editing) { navigator.editingPanels = false }
    }
}

/**
 * Every destination, registered.
 */
private fun EntryProviderScope<NavKey>.registerRoutes(navigator: Navigator) {
    entry<TopLevelRoute.RootFlow> {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        RootFlowScreen(
            onGoAbout = { navigator.switchTo(TopLevelRoute.About) },
            onOpenAnnouncements = {
                navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Announcements)
            },
            onOpenFirmwareSelect = {
                // 启动独立版本选择 Activity
                ctx.startActivity(
                    android.content.Intent(
                        ctx,
                        cn.nanoturtle.rootmys9280.manager.ui.firmware.FirmwareSelectActivity::class.java,
                    ),
                )
            },
        )
    }
    entry<cn.nanoturtle.rootmys9280.manager.ui.navigation.Announcements> {
        cn.nanoturtle.rootmys9280.manager.ui.screens.announce.AnnouncementsScreen(
            onNavigateBack = { navigator.back() },
        )
    }
    entry<TopLevelRoute.Logs> {
        LogsScreen()
    }
    entry<TopLevelRoute.Settings> {
        SettingsScreen(
            onOpenUrl = { url -> navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Web(url)) },
            onOpenFeedback = { navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Feedback) },
            onOpenPrivacy = { navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Privacy) },
        )
    }
    entry<cn.nanoturtle.rootmys9280.manager.ui.navigation.Privacy> {
        cn.nanoturtle.rootmys9280.manager.ui.screens.privacy.PrivacyScreen(
            onNavigateBack = { navigator.back() },
            onOpenUrl = { url -> navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Web(url)) },
        )
    }
    entry<TopLevelRoute.About> {
        AboutScreen(
            onOpenPrivacy = { navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Privacy) },
            onOpenUrl = { url -> navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Web(url)) },
            onOpenForumUrl = { url ->
                // 带票据的讨论区地址；票据短期有效，过期后 App 会重新换一张
                navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Web(url))
            },
            onOpenDonate = { navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Donate) },
            onOpenWiki = { navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Wiki) },
        )
    }
    entry<cn.nanoturtle.rootmys9280.manager.ui.navigation.Feedback> {
        cn.nanoturtle.rootmys9280.manager.ui.screens.feedback.FeedbackScreen(
            onNavigateBack = { navigator.back() },
        )
    }
    entry<cn.nanoturtle.rootmys9280.manager.ui.navigation.Wiki> {
        cn.nanoturtle.rootmys9280.manager.ui.screens.wiki.WikiScreen(
            onNavigateBack = { navigator.back() },
        )
    }
    entry<cn.nanoturtle.rootmys9280.manager.ui.navigation.Donate> {
        cn.nanoturtle.rootmys9280.manager.ui.screens.donate.DonateScreen(
            onNavigateBack = { navigator.back() },
        )
    }
    entry<cn.nanoturtle.rootmys9280.manager.ui.navigation.Web> { route ->
        cn.nanoturtle.rootmys9280.manager.ui.screens.web.WebScreen(
            url = route.url,
            onNavigateBack = { navigator.back() },
        )
    }
}
