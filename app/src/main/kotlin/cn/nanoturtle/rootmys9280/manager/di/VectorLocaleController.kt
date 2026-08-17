package cn.nanoturtle.rootmys9280.manager.di

import cn.nanoturtle.rootmys9280.manager.ui.theme.availableLocales
import cn.nanoturtle.rootmys9280.ui.locale.LocaleController
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridges the shared [LocaleController] to Vector's persisted app-locale setting,
 * so the LanguageSheet reads/writes the same preference LocalizedContent applies.
 */
object VectorLocaleController : LocaleController {
    private val settings
        get() = ServiceLocator.settings

    override val appLocale: StateFlow<String> = settings.appLocale

    override val availableTags: List<String>
        get() = availableLocales().map { it.toLanguageTag() }

    override fun setAppLocale(tag: String) = settings.setAppLocale(tag)
}
