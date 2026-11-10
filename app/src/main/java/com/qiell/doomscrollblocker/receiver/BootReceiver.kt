package com.qiell.doomscrollblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qiell.doomscrollblocker.DoomscrollApplication
import com.qiell.doomscrollblocker.engine.AndroidClock
import com.qiell.doomscrollblocker.engine.RulesEngine
import com.qiell.doomscrollblocker.whitelist.WhitelistResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Setelah reboot, jam elapsed sudah kembali ke nol sehingga sisa cooldown
 * harus dievaluasi ulang dari pasangan jam dinding. Receiver ini memuat state
 * tersimpan, membiarkan engine menyelarasikannya (sync terjadi pada panggilan
 * pertama), lalu menyimpannya kembali. Layanan aksesibilitas sendiri akan
 * dimulai ulang oleh sistem apabila masih diaktifkan pengguna.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as DoomscrollApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = app.repository
                val settings = repo.loadSettings().copy(
                    whitelistedPackages = WhitelistResolver.resolve(context)
                )
                val engine = RulesEngine(AndroidClock(context), repo.loadState(), settings)
                // onTick memicu sync: cooldown lewat -> dibersihkan, belum -> dipertahankan.
                engine.onTick()
                repo.saveState(engine.state)
            } finally {
                pending.finish()
            }
        }
    }
}
