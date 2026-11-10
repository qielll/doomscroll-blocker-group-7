package com.qiell.doomscrollblocker.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.qiell.doomscrollblocker.DoomscrollApplication
import com.qiell.doomscrollblocker.engine.AndroidClock
import com.qiell.doomscrollblocker.engine.Decision
import com.qiell.doomscrollblocker.engine.RulesEngine
import com.qiell.doomscrollblocker.ui.BlockActivity
import com.qiell.doomscrollblocker.whitelist.WhitelistResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Deteksi aplikasi foreground lewat TYPE_WINDOW_STATE_CHANGED.
 *
 * Tanggung jawab service ini sengaja tipis — semua keputusan ada di
 * [RulesEngine] yang murni Kotlin:
 * 1. Saring paket derau (SystemUI, keyboard/IME, paket sendiri, dialog izin).
 * 2. Teruskan paket bersih ke engine, simpan state hasilnya ke DataStore.
 * 3. Jadwalkan timer untuk sisa allowance (scroll tidak memicu event baru).
 * 4. Apabila engine memutuskan Block: tampilkan BlockActivity, lalu setelah
 *    jeda singkat kirim pengguna ke Home lewat GLOBAL_ACTION_HOME.
 * 5. Jeda/lanjutkan timer saat layar mati/menyala.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())

    private var engine: RulesEngine? = null
    private var limitTimer: Runnable? = null
    private var currentForeground: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> runEngine { it.onScreenOff(); Decision.Allow }
                Intent.ACTION_SCREEN_ON -> runEngine {
                    val decision = it.onForegroundChanged(currentForeground)
                    decision
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        )
        scope.launch { ensureEngine() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (isNoisePackage(pkg)) return
        currentForeground = pkg
        runEngine { it.onForegroundChanged(pkg) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        cancelLimitTimer()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ engine

    private suspend fun ensureEngine(): RulesEngine {
        engine?.let { return it }
        val app = applicationContext as DoomscrollApplication
        val settings = app.repository.loadSettings().copy(
            whitelistedPackages = WhitelistResolver.resolve(this)
        )
        val created = RulesEngine(AndroidClock(this), app.repository.loadState(), settings)
        engine = created
        return created
    }

    private fun runEngine(block: (RulesEngine) -> Decision) {
        scope.launch {
            val e = ensureEngine()
            // Pengaturan bisa berubah dari UI saat service hidup: selalu muat ulang
            // (engine membekukan cooldown berjalan, jadi ini aman).
            val app = applicationContext as DoomscrollApplication
            e.updateSettings(
                app.repository.loadSettings().copy(
                    whitelistedPackages = WhitelistResolver.resolve(this@AppBlockerAccessibilityService)
                )
            )
            val decision = block(e)
            app.repository.saveState(e.state)
            handleDecision(decision)
        }
    }

    private fun handleDecision(decision: Decision) {
        when (decision) {
            is Decision.Block -> {
                cancelLimitTimer()
                showBlockScreen(decision.remainingMs)
            }
            is Decision.LimitIn -> scheduleLimitTimer(decision.limitInMs)
            Decision.Allow -> cancelLimitTimer()
        }
    }

    /** Timer batas: menembak onTick tepat saat allowance habis, tanpa event jendela baru. */
    private fun scheduleLimitTimer(delayMs: Long) {
        cancelLimitTimer()
        val runnable = Runnable { runEngine { it.onTick() } }
        limitTimer = runnable
        handler.postDelayed(runnable, delayMs.coerceAtLeast(0L))
    }

    private fun cancelLimitTimer() {
        limitTimer?.let { handler.removeCallbacks(it) }
        limitTimer = null
    }

    private fun showBlockScreen(remainingMs: Long) {
        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockActivity.EXTRA_REMAINING_MS, remainingMs)
        }
        startActivity(intent)
        // Setelah layar blokir terlihat sebentar, kirim pengguna ke Home.
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 2_500L)
    }

    // ------------------------------------------------------------------- noise

    /**
     * Paket yang event jendelanya adalah derau dan tidak boleh menjeda /
     * memulai ulang timer: SystemUI, keyboard yang sedang aktif, dialog izin,
     * dan aplikasi ini sendiri (termasuk BlockActivity).
     */
    private fun isNoisePackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg == "com.android.systemui") return true
        if (pkg == "com.android.permissioncontroller" ||
            pkg == "com.google.android.permissioncontroller"
        ) return true
        val currentIme = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )?.substringBefore('/') ?: return false
        return pkg == currentIme
    }
}
