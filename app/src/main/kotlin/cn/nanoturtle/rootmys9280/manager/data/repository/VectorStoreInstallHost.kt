package cn.nanoturtle.rootmys9280.manager.data.repository

import android.Manifest
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import cn.nanoturtle.rootmys9280.manager.data.model.versionCodeCompat
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import cn.nanoturtle.rootmys9280.ui.store.InstallStep
import cn.nanoturtle.rootmys9280.ui.store.ReleaseAsset
import cn.nanoturtle.rootmys9280.ui.store.RepoVersion
import cn.nanoturtle.rootmys9280.ui.store.StoreInstall
import cn.nanoturtle.rootmys9280.ui.store.StoreInstallHost

/**
 * Vector's implementation of the shared Details screen's install capability: the module installer,
 * the post-install bookkeeping, and the device APK inspection that used to live in the details view
 * model. Created per opened module (it needs the package name). LSPatch has no installer and passes
 * null instead, collapsing the shared screen to open-in-browser links.
 */
class VectorStoreInstallHost(private val packageName: String) : StoreInstallHost {

    private val installer = ServiceLocator.installer

    override val installState: StateFlow<InstallStep> = installer.state

    override val silentInstall: Boolean
        get() =
            ServiceLocator.context.checkSelfPermission(Manifest.permission.INSTALL_PACKAGES) ==
                PackageManager.PERMISSION_GRANTED

    private val _installedScope = MutableStateFlow<List<String>>(emptyList())
    override val installedScope: StateFlow<List<String>> = _installedScope.asStateFlow()

    private val _installedIsLegacy = MutableStateFlow(false)
    override val installedIsLegacy: StateFlow<Boolean> = _installedIsLegacy.asStateFlow()

    init {
        refreshScope()
    }

    private fun refreshScope() {
        ServiceLocator.appScope.launch {
            runCatching {
                val pm = ServiceLocator.context.packageManager
                val info = pm.getPackageInfo(packageName, PackageManager.GET_META_DATA)
                val appInfo = info.applicationInfo ?: return@runCatching
                val manifest =
                    ServiceLocator.moduleDetection.inspect(
                        appInfo,
                        pm,
                        info.versionCodeCompat,
                        info.lastUpdateTime,
                    )
                _installedScope.value = manifest.scope
                _installedIsLegacy.value = manifest.isLegacy
            }
        }
    }

    override fun install(asset: ReleaseAsset, releaseVersion: RepoVersion?) {
        ServiceLocator.appScope.launch {
            installer.install(packageName, asset)
            // Record what was installed so a satisfied update stops being offered.
            val installed = ServiceLocator.store.readInstalled()[packageName]
            if (releaseVersion != null && installed != null) {
                ServiceLocator.settings.noteStoreInstall(
                    packageName,
                    StoreInstall(releaseVersion, installed),
                )
            }
            refreshScope()
        }
    }

    override fun acknowledge() = installer.acknowledge()
}
