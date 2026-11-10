package com.qiell.doomscrollblocker.engine

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** Implementasi [Clock] di perangkat Android. */
class AndroidClock(private val context: Context) : Clock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun wallClockMs(): Long = System.currentTimeMillis()

    override fun bootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}
