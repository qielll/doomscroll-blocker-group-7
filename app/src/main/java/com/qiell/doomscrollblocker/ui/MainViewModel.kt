package com.qiell.doomscrollblocker.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qiell.doomscrollblocker.DoomscrollApplication
import com.qiell.doomscrollblocker.engine.BlockerSettings
import com.qiell.doomscrollblocker.engine.EngineState
import com.qiell.doomscrollblocker.whitelist.WhitelistResolver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Satu aplikasi terpasang yang bisa diluncurkan, untuk pemilih aplikasi. */
data class InstalledApp(val label: String, val packageName: String)

/**
 * ViewModel layar utama. Tanpa framework DI: repository diambil dari
 * [DoomscrollApplication], sesuai keputusan "manual wiring" di spesifikasi.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as DoomscrollApplication).repository

    /** Daftar aplikasi yang tidak pernah boleh muncul di pemilih (whitelist dinamis). */
    val whitelistedPackages: Set<String> = WhitelistResolver.resolve(application)

    val settings: StateFlow<BlockerSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BlockerSettings())

    val engineState: StateFlow<EngineState> = repo.stateFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, EngineState())

    /**
     * Daftar aplikasi launchable. Memakai intent MAIN/LAUNCHER lewat deklarasi
     * `<queries>` di manifest — BUKAN QUERY_ALL_PACKAGES.
     * Aplikasi whitelist dan aplikasi ini sendiri disembunyikan.
     */
    fun loadInstalledApps(): List<InstalledApp> {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg in whitelistedPackages) return@mapNotNull null
                InstalledApp(
                    label = info.loadLabel(pm).toString(),
                    packageName = pkg
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun setPackageBlocked(packageName: String, blocked: Boolean) {
        val current = settings.value
        val updated = if (blocked) {
            current.blockedPackages + packageName
        } else {
            current.blockedPackages - packageName
        }
        // Whitelist tidak akan pernah disimpan sebagai aplikasi terblokir.
        saveSettings(current.copy(blockedPackages = updated - whitelistedPackages))
    }

    fun setAllowanceMs(ms: Long) {
        if (ms > 0) saveSettings(settings.value.copy(allowanceMs = ms))
    }

    fun setCooldownMs(ms: Long) {
        if (ms > 0) saveSettings(settings.value.copy(cooldownMs = ms))
    }

    private fun saveSettings(newSettings: BlockerSettings) {
        viewModelScope.launch { repo.saveSettings(newSettings) }
    }
}
