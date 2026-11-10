package com.qiell.doomscrollblocker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qiell.doomscrollblocker.engine.BlockerSettings
import com.qiell.doomscrollblocker.engine.EngineState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "doomscroll_blocker")

/**
 * Penyimpanan v1: DataStore Preferences (tanpa Room, sesuai spesifikasi).
 *
 * Kunci-kunci di bawah mengikuti model data §5 spesifikasi satu per satu,
 * supaya state engine bisa diaudit langsung dari file preferensi saat debugging.
 */
class DataStoreRepository(private val context: Context) {

    private object Keys {
        val BlockedPackages = stringSetPreferencesKey("blocked_packages")
        val AllowanceMs = longPreferencesKey("allowance_ms")
        val CooldownMs = longPreferencesKey("cooldown_ms")

        val UsedMs = longPreferencesKey("used_ms")
        val ForegroundSinceElapsed = longPreferencesKey("foreground_since_elapsed")
        val ForegroundPackage = stringPreferencesKey("foreground_package")
        val ScreenOn = stringPreferencesKey("screen_on")
        val LastLeftListedElapsed = longPreferencesKey("last_left_listed_elapsed")
        val BlockedUntilElapsed = longPreferencesKey("blocked_until_elapsed")
        val BlockedUntilWall = longPreferencesKey("blocked_until_wall")
        val BlockBootCount = intPreferencesKey("block_boot_count")
        val LastSeenWall = longPreferencesKey("last_seen_wall")

        val BreakGlassLastUsedWall = longPreferencesKey("break_glass_last_used_wall")
        val GraceUntilElapsed = longPreferencesKey("grace_until_elapsed")
        val GraceBootCount = intPreferencesKey("grace_boot_count")
        val BreakGlassLog = stringPreferencesKey("break_glass_log")
    }

    val settingsFlow: Flow<BlockerSettings> = context.dataStore.data.map { prefs ->
        BlockerSettings(
            blockedPackages = prefs[Keys.BlockedPackages] ?: emptySet(),
            allowanceMs = prefs[Keys.AllowanceMs] ?: BlockerSettings.DEFAULT_ALLOWANCE_MS,
            cooldownMs = prefs[Keys.CooldownMs] ?: BlockerSettings.DEFAULT_COOLDOWN_MS,
            // Whitelist tidak disimpan: selalu dihitung ulang dari perangkat (dinamis).
            whitelistedPackages = emptySet(),
        )
    }

    val stateFlow: Flow<EngineState> = context.dataStore.data.map { prefs -> prefs.toEngineState() }

    suspend fun loadSettings(): BlockerSettings = settingsFlow.first()

    suspend fun loadState(): EngineState = stateFlow.first()

    suspend fun saveSettings(settings: BlockerSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BlockedPackages] = settings.blockedPackages
            prefs[Keys.AllowanceMs] = settings.allowanceMs
            prefs[Keys.CooldownMs] = settings.cooldownMs
        }
    }

    suspend fun saveState(state: EngineState) {
        context.dataStore.edit { prefs ->
            prefs[Keys.UsedMs] = state.usedMs
            state.foregroundSinceElapsed?.let { prefs[Keys.ForegroundSinceElapsed] = it }
                ?: prefs.remove(Keys.ForegroundSinceElapsed)
            state.foregroundPackage?.let { prefs[Keys.ForegroundPackage] = it }
                ?: prefs.remove(Keys.ForegroundPackage)
            prefs[Keys.ScreenOn] = state.screenOn.toString()
            state.lastLeftListedElapsed?.let { prefs[Keys.LastLeftListedElapsed] = it }
                ?: prefs.remove(Keys.LastLeftListedElapsed)
            state.blockedUntilElapsed?.let { prefs[Keys.BlockedUntilElapsed] = it }
                ?: prefs.remove(Keys.BlockedUntilElapsed)
            state.blockedUntilWall?.let { prefs[Keys.BlockedUntilWall] = it }
                ?: prefs.remove(Keys.BlockedUntilWall)
            state.blockBootCount?.let { prefs[Keys.BlockBootCount] = it }
                ?: prefs.remove(Keys.BlockBootCount)
            prefs[Keys.LastSeenWall] = state.lastSeenWall
            state.breakGlassLastUsedWall?.let { prefs[Keys.BreakGlassLastUsedWall] = it }
                ?: prefs.remove(Keys.BreakGlassLastUsedWall)
            state.graceUntilElapsed?.let { prefs[Keys.GraceUntilElapsed] = it }
                ?: prefs.remove(Keys.GraceUntilElapsed)
            state.graceBootCount?.let { prefs[Keys.GraceBootCount] = it }
                ?: prefs.remove(Keys.GraceBootCount)
            // Log disimpan sebagai daftar epoch yang dipisahkan koma (cukup untuk v1).
            prefs[Keys.BreakGlassLog] = state.breakGlassLog.joinToString(",")
        }
    }

    private fun Preferences.toEngineState(): EngineState = EngineState(
        usedMs = this[Keys.UsedMs] ?: 0L,
        foregroundSinceElapsed = this[Keys.ForegroundSinceElapsed],
        foregroundPackage = this[Keys.ForegroundPackage],
        screenOn = this[Keys.ScreenOn]?.toBooleanStrictOrNull() ?: true,
        lastLeftListedElapsed = this[Keys.LastLeftListedElapsed],
        blockedUntilElapsed = this[Keys.BlockedUntilElapsed],
        blockedUntilWall = this[Keys.BlockedUntilWall],
        blockBootCount = this[Keys.BlockBootCount],
        lastSeenWall = this[Keys.LastSeenWall] ?: 0L,
        breakGlassLastUsedWall = this[Keys.BreakGlassLastUsedWall],
        graceUntilElapsed = this[Keys.GraceUntilElapsed],
        graceBootCount = this[Keys.GraceBootCount],
        breakGlassLog = this[Keys.BreakGlassLog]
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?: emptyList(),
    )
}
