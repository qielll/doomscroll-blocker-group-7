package com.qiell.doomscrollblocker.engine

/** Jam palsu untuk pengujian JVM: waktu hanya bergerak saat test menggerakkannya. */
class FakeClock(
    var elapsedMs: Long = 0L,
    var wallMs: Long = 1_700_000_000_000L,
    var boot: Int = 1,
) : Clock {
    override fun elapsedRealtimeMs(): Long = elapsedMs
    override fun wallClockMs(): Long = wallMs
    override fun bootCount(): Int = boot

    /** Majukan kedua jam bersamaan (perilaku normal tanpa reboot / manipulasi). */
    fun advance(ms: Long) {
        elapsedMs += ms
        wallMs += ms
    }

    /** Simulasikan reboot: elapsed kembali kecil, BOOT_COUNT bertambah, wall berlanjut. */
    fun reboot(elapsedAfterBootMs: Long = 60_000L) {
        boot += 1
        elapsedMs = elapsedAfterBootMs
    }
}
