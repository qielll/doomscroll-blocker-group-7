package com.qiell.doomscrollblocker.engine

/**
 * Mesin aturan Doomscroll Blocker — Kotlin murni, TANPA import Android,
 * sehingga seluruh aturan di §4 spesifikasi bisa diuji di JVM dengan jam palsu.
 *
 * Aturan ringkasnya (teks lengkap di ARCHITECTURE.md):
 * 1. Satu anggaran bersama untuk seluruh daftar blokir.
 * 2. Timer hanya berjalan saat aplikasi terdaftar di foreground DAN layar menyala.
 * 3. Meninggalkan aplikasi terdaftar hanya menjeda, tidak me-reset.
 * 4. usedMs >= allowanceMs -> COOLDOWN; blockedUntil dihitung sekali dan dibekukan.
 *    Perubahan pengaturan tidak memendekkan dan tidak memanjangkan cooldown berjalan.
 * 5. Cooldown berakhir -> usedMs = 0, kembali ALLOWED.
 * 6. Idle reset terkonfirmasi: menjauh dari seluruh aplikasi terdaftar selama
 *    cooldownMs (saat ALLOWED) juga me-reset usedMs ke 0.
 * 7. Break-glass: tahan 5 detik (diperiksa di sini) + frasa persis + tunggu 60 detik
 *    (ditegakkan UI) -> kelonggaran 5 menit, sekali per 24 jam, tanpa menggeser cooldown.
 * 8. Tahan manipulasi jam: boot sama -> percaya jam elapsed saja; boot berubah ->
 *    pakai jam dinding, dan apabila jam dinding mundur dari lastSeenWall, sisa
 *    waktu dihitung dari lastSeenWall (dianggap manipulasi).
 *
 * Pemanggil (service) bertanggung jawab memanggil [onTick] lewat timer terjadwal
 * selama aplikasi terdaftar di foreground, dan menyimpan [state] setelah setiap panggilan.
 */
class RulesEngine(
    private val clock: Clock,
    initialState: EngineState = EngineState(),
    initialSettings: BlockerSettings = BlockerSettings(),
) {
    var state: EngineState = initialState
        private set

    var settings: BlockerSettings = initialSettings
        private set

    /**
     * Mengganti pengaturan. Aman dipanggil kapan pun: cooldown yang sedang
     * berjalan tidak dihitung ulang dari nilai baru — nilai baru hanya berlaku
     * mulai siklus berikutnya (keputusan terkonfirmasi 2026-11-10).
     */
    fun updateSettings(newSettings: BlockerSettings) {
        settings = newSettings
    }

    fun isListed(pkg: String?): Boolean =
        pkg != null && pkg in settings.blockedPackages && pkg !in settings.whitelistedPackages

    fun isWhitelisted(pkg: String?): Boolean =
        pkg != null && pkg in settings.whitelistedPackages

    /** Sisa cooldown dalam ms; 0 apabila tidak sedang diblokir. Tidak mengubah state. */
    fun blockedRemainingMs(): Long {
        if (state.blockedUntilElapsed == null && state.blockedUntilWall == null) return 0L
        return if (state.blockBootCount != null && state.blockBootCount == clock.bootCount()) {
            val until = state.blockedUntilElapsed ?: return blockedRemainingAfterReboot()
            (until - clock.elapsedRealtimeMs()).coerceAtLeast(0L)
        } else {
            blockedRemainingAfterReboot()
        }
    }

    private fun blockedRemainingAfterReboot(): Long {
        val untilWall = state.blockedUntilWall ?: return 0L
        val nowWall = clock.wallClockMs()
        // Jam dimundurkan (now < yang terakhir terlihat) = manipulasi:
        // sisa dihitung dari titik terakhir yang terpercaya, bukan dari jam baru.
        val reference = if (nowWall < state.lastSeenWall) state.lastSeenWall else nowWall
        return (untilWall - reference).coerceAtLeast(0L)
    }

    /** Apakah jendela kelonggaran break-glass sedang aktif pada boot ini. */
    fun isGraceActive(): Boolean {
        val until = state.graceUntilElapsed ?: return false
        if (state.graceBootCount != null && state.graceBootCount != clock.bootCount()) return false
        return clock.elapsedRealtimeMs() < until
    }

    fun isBlocked(): Boolean = blockedRemainingMs() > 0L && !isGraceActive()

    /**
     * Dipanggil setiap kali paket foreground berubah.
     * [pkg] null berarti tidak ada aplikasi pengguna di depan (launcher / layar mati dsb.).
     */
    fun onForegroundChanged(pkg: String?): Decision {
        // Paket sebelumnya menentukan apakah ini momen "meninggalkan daftar".
        // Diturunkan dari state (bukan variabel sementara) supaya tetap benar
        // setelah engine dibuat ulang dari state tersimpan.
        val wasListedBefore = isListed(state.foregroundPackage)
        sync()
        // Akumulasikan sesi sebelumnya sebelum berpindah paket.
        accumulateForeground()
        state = state.copy(foregroundPackage = pkg, foregroundSinceElapsed = null)

        if (pkg == null || isWhitelisted(pkg) || !isListed(pkg)) {
            // Meninggalkan aplikasi terdaftar: catat awal jeda untuk idle reset.
            if (wasListedBefore && state.lastLeftListedElapsed == null) {
                state = state.copy(lastLeftListedElapsed = clock.elapsedRealtimeMs())
            }
            return Decision.Allow
        }

        // Paket terdaftar masuk foreground.
        state = state.copy(lastLeftListedElapsed = null)

        if (blockedRemainingMs() > 0L) {
            return if (isGraceActive()) {
                state = state.copy(foregroundSinceElapsed = clock.elapsedRealtimeMs())
                Decision.Allow
            } else {
                state = state.copy(foregroundSinceElapsed = null)
                Decision.Block(blockedRemainingMs())
            }
        }

        if (state.usedMs >= settings.allowanceMs) {
            triggerBlock()
            return Decision.Block(blockedRemainingMs())
        }

        state = state.copy(
            foregroundSinceElapsed = if (state.screenOn) clock.elapsedRealtimeMs() else null
        )
        return Decision.LimitIn(settings.allowanceMs - currentUsedMs())
    }

    /** Layar mati: jeda timer. Waktu sampai momen ini tetap dihitung. */
    fun onScreenOff() {
        sync()
        accumulateForeground()
        state = state.copy(screenOn = false, foregroundSinceElapsed = null)
    }

    /** Layar menyala: lanjutkan timer apabila paket di depan adalah aplikasi terdaftar. */
    fun onScreenOn() {
        sync()
        state = state.copy(screenOn = true)
        if (isListed(state.foregroundPackage) && blockedRemainingMs() == 0L) {
            state = state.copy(foregroundSinceElapsed = clock.elapsedRealtimeMs())
        }
    }

    /**
     * Detak berkala dari timer terjadwal service. Inilah yang menegakkan batas
     * saat pengguna terus scroll tanpa memicu event jendela apa pun.
     */
    fun onTick(): Decision {
        sync()
        if (blockedRemainingMs() > 0L) {
            return if (isGraceActive()) Decision.Allow else Decision.Block(blockedRemainingMs())
        }
        if (isListed(state.foregroundPackage) && state.screenOn) {
            if (currentUsedMs() >= settings.allowanceMs) {
                accumulateForeground()
                triggerBlock()
                return Decision.Block(blockedRemainingMs())
            }
            return Decision.LimitIn(settings.allowanceMs - currentUsedMs())
        }
        return Decision.Allow
    }

    /**
     * Memeriksa syarat break-glass yang menjadi urusan engine: sedang diblokir,
     * tekanan tahan minimal 5 detik, frasa persis (setelah normalisasi), dan
     * belum pernah dipakai dalam 24 jam terakhir. Masa tunggu 60 detik
     * ditegakkan UI sebelum memanggil fungsi ini (lihat BlockActivity).
     *
     * Mengembalikan true dan membuka kelonggaran 5 menit apabila semua syarat lolos.
     * Kelonggaran tidak mengubah blockedUntil* sedikit pun.
     */
    fun requestBreakGlass(phrase: String, heldMs: Long): Boolean {
        sync()
        if (blockedRemainingMs() <= 0L) return false
        if (heldMs < BREAK_GLASS_HOLD_MS) return false
        if (normalizePhrase(phrase) != normalizePhrase(BREAK_GLASS_PHRASE)) return false

        val nowWall = clock.wallClockMs()
        val last = state.breakGlassLastUsedWall
        if (last != null) {
            // Jam mundur membuat selisih negatif -> tetap dianggap belum 24 jam.
            if (nowWall - last < BREAK_GLASS_INTERVAL_MS) return false
        }

        state = state.copy(
            breakGlassLastUsedWall = nowWall,
            graceUntilElapsed = clock.elapsedRealtimeMs() + BREAK_GLASS_GRACE_MS,
            graceBootCount = clock.bootCount(),
            breakGlassLog = state.breakGlassLog + nowWall,
            // Selama kelonggaran, timer sesi boleh berjalan lagi.
            foregroundSinceElapsed = null,
        )
        return true
    }

    /** Waktu terpakai saat ini, termasuk sesi foreground yang sedang berjalan. */
    fun currentUsedMs(): Long {
        val since = state.foregroundSinceElapsed ?: return state.usedMs
        if (!state.screenOn || !isListed(state.foregroundPackage)) return state.usedMs
        if (blockedRemainingMs() > 0L && !isGraceActive()) return state.usedMs
        return state.usedMs + (clock.elapsedRealtimeMs() - since).coerceAtLeast(0L)
    }

    // ---------------------------------------------------------------- internal

    /**
     * Penyelarasan yang berjalan di awal setiap event:
     * - perbarui lastSeenWall (hanya maju, tidak pernah mundur),
     * - akhiri cooldown yang sudah lewat waktunya (usedMs -> 0),
     * - akhiri kelonggaran yang sudah lewat,
     * - idle reset: jauh dari aplikasi terdaftar >= cooldownMs saat ALLOWED -> usedMs = 0.
     */
    private fun sync() {
        val nowWall = clock.wallClockMs()
        if (nowWall > state.lastSeenWall) {
            state = state.copy(lastSeenWall = nowWall)
        }

        if ((state.blockedUntilElapsed != null || state.blockedUntilWall != null) &&
            blockedRemainingMs() <= 0L
        ) {
            // Cooldown selesai.
            accumulateForeground()
            state = state.copy(
                usedMs = 0L,
                blockedUntilElapsed = null,
                blockedUntilWall = null,
                blockBootCount = null,
                graceUntilElapsed = null,
                graceBootCount = null,
                lastLeftListedElapsed = null,
            )
        }

        if (state.graceUntilElapsed != null && !isGraceActive()) {
            state = state.copy(graceUntilElapsed = null, graceBootCount = null)
        }

        val leftAt = state.lastLeftListedElapsed
        if (blockedRemainingMs() == 0L && leftAt != null && !isListed(state.foregroundPackage)) {
            if (clock.elapsedRealtimeMs() - leftAt >= settings.cooldownMs) {
                state = state.copy(usedMs = 0L, lastLeftListedElapsed = null)
            }
        }
    }

    /** Lipat sesi foreground berjalan ke usedMs dan mulai segmen baru apabila masih berjalan. */
    private fun accumulateForeground() {
        val since = state.foregroundSinceElapsed ?: return
        val delta = (clock.elapsedRealtimeMs() - since).coerceAtLeast(0L)
        val stillRunning = state.screenOn && isListed(state.foregroundPackage) &&
            blockedRemainingMs() == 0L
        state = state.copy(
            usedMs = state.usedMs + delta,
            foregroundSinceElapsed = if (stillRunning) clock.elapsedRealtimeMs() else null,
        )
    }

    /** Memulai cooldown. blockedUntil dihitung di sini, sekali, dan setelah itu dibekukan. */
    private fun triggerBlock() {
        val nowElapsed = clock.elapsedRealtimeMs()
        val nowWall = clock.wallClockMs()
        state = state.copy(
            foregroundSinceElapsed = null,
            blockedUntilElapsed = nowElapsed + settings.cooldownMs,
            blockedUntilWall = nowWall + settings.cooldownMs,
            blockBootCount = clock.bootCount(),
        )
    }

    companion object {
        /** Frasa break-glass resmi (keputusan 2026-11-10). */
        const val BREAK_GLASS_PHRASE: String =
            "saya sadar ini keputusan saya dan saya siap menerima akibatnya sekarang juga"
        const val BREAK_GLASS_HOLD_MS: Long = 5_000L
        const val BREAK_GLASS_WAIT_MS: Long = 60_000L
        const val BREAK_GLASS_GRACE_MS: Long = 5 * 60_000L
        const val BREAK_GLASS_INTERVAL_MS: Long = 24 * 60 * 60_000L

        /** Normalisasi frasa: huruf kecil, spasi dirapikan dan dipangkas. */
        fun normalizePhrase(raw: String): String =
            raw.lowercase().trim().replace(Regex("\\s+"), " ")
    }
}
