package cn.nanoturtle.rootmys9280.manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
                    backStack = navigator.backStack,
                    onBack = { navigator.back() },
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
    entry<TopLevelRoute.Logs> {
        LogsScreen()
    }
    entry<TopLevelRoute.Settings> {
        SettingsScreen(
            onOpenUrl = { url -> navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Web(url)) },
        )
    }
    entry<TopLevelRoute.About> {
        AboutScreen(
            onOpenUrl = { url -> navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Web(url)) },
            onOpenDonate = { navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Donate) },
            onOpenWiki = { navigator.go(cn.nanoturtle.rootmys9280.manager.ui.navigation.Wiki) },
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
