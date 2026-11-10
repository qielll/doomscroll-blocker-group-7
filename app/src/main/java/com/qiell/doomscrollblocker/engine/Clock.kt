package com.qiell.doomscrollblocker.engine

/**
 * Sumber waktu untuk [RulesEngine].
 *
 * Engine sengaja tidak memanggil `SystemClock` / `System.currentTimeMillis`
 * secara langsung supaya seluruh aturan bisa diuji di JVM dengan jam palsu.
 *
 * Dua jam dibutuhkan karena dua alasan berbeda:
 * - [elapsedRealtimeMs]: monoton, tidak terpengaruh perubahan tanggal ponsel,
 *   tetapi kembali ke nol setelah reboot.
 * - [wallClockMs]: bertahan melewati reboot, tetapi bisa dimundurkan pengguna.
 * Kombinasi keduanya + [bootCount] adalah dasar ketahanan terhadap manipulasi
 * jam (lihat ARCHITECTURE.md).
 */
interface Clock {
    /** Milidetik sejak boot, termasuk waktu tidur (SystemClock.elapsedRealtime). */
    fun elapsedRealtimeMs(): Long

    /** Milidetik epoch dinding (System.currentTimeMillis). */
    fun wallClockMs(): Long

    /** Jumlah boot perangkat (Settings.Global.BOOT_COUNT). Berubah = reboot terjadi. */
    fun bootCount(): Int
}
