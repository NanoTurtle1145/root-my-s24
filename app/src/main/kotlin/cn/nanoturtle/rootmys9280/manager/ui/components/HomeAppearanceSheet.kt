package cn.nanoturtle.rootmys9280.manager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import cn.nanoturtle.rootmys9280.manager.ui.navigation.LocalNavigator
import cn.nanoturtle.rootmys9280.ui.SheetHeading
import cn.nanoturtle.rootmys9280.ui.SheetAction
import cn.nanoturtle.rootmys9280.ui.ToggleRow
import cn.nanoturtle.rootmys9280.ui.appearance.AppearanceSheet

/**
 * 外观设置弹层（Vector HomeAppearanceSheet 同款）：
 * 共享 AppearanceSheet（主题/配色/页头动效/OLED）+ 导航 section（悬浮导航、重新排列面板）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAppearanceSheet(onDismiss: () -> Unit) {
    val settings = ServiceLocator.settings
    val navigator = LocalNavigator.current
    val floating by settings.floatingNav.collectAsStateWithLifecycle()

    AppearanceSheet(
        settings = settings,
        onDismiss = onDismiss,
        extra = {
            SheetHeading(stringResource(R.string.settings_navigation), Icons.Rounded.Dashboard)
            ToggleRow(
                title = stringResource(R.string.settings_floating_nav),
                icon = Icons.Rounded.BubbleChart,
                subtitle = stringResource(R.string.settings_floating_nav_summary),
                checked = floating,
                onCheckedChange = settings::setFloatingNav,
            )
            SheetAction(
                title = stringResource(R.string.settings_rearrange_panels),
                icon = Icons.Rounded.Reorder,
                onClick = {
                    navigator.editingPanels = true
                    onDismiss()
                },
                subtitle = stringResource(R.string.settings_rearrange_panels_summary),
            )
        },
    )
}
