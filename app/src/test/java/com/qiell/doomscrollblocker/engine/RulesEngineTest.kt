package com.qiell.doomscrollblocker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengujian aturan §4 spesifikasi dengan jam palsu.
 * Setiap test memetakan ke setidaknya satu kriteria penerimaan §8.
 */
class RulesEngineTest {

    private val appA = "com.example.scrolla"
    private val appB = "com.example.scrollb"
    private val dialer = "com.example.dialer"

    private fun settings(
        allowanceMs: Long = 60_000L,
        cooldownMs: Long = 120_000L,
    ) = BlockerSettings(
        blockedPackages = setOf(appA, appB),
        allowanceMs = allowanceMs,
        cooldownMs = cooldownMs,
        whitelistedPackages = setOf(dialer),
    )

    private fun engine(clock: FakeClock, s: BlockerSettings = settings()) =
        RulesEngine(clock, EngineState(), s)

    @Test
    fun `mencapai allowance memicu blokir dengan sisa sama dengan cooldown`() {
        val clock = FakeClock()
        val e = engine(clock)
        assertTrue(e.onForegroundChanged(appA) is Decision.LimitIn)
        clock.advance(60_000L)
        val d = e.onTick()
        assertTrue(d is Decision.Block)
        assertEquals(120_000L, (d as Decision.Block).remainingMs)
    }

    @Test
    fun `waktu dua aplikasi terdaftar dijumlahkan bersama`() {
        val clock = FakeClock()
        val e = engine(clock)
        e.onForegroundChanged(appA)
        clock.advance(30_000L)
        e.onForegroundChanged(appB)
        clock.advance(30_000L)
        assertTrue(e.onTick() is Decision.Block)
    }

    @Test
    fun `meninggalkan aplikasi hanya menjeda, tidak me-reset`() {
        val clock = FakeClock()
        val e = engine(clock)
        e.onForegroundChanged(appA)
        clock.advance(40_000L)
        e.onForegroundChanged(null) // ke launcher selama 30 detik
        clock.advance(30_000L)
        e.onForegroundChanged(appA)
        clock.advance(20_000L)
        // Total terpakai 60 detik = allowance, walau ada jeda di tengah.
        assertTrue(e.onTick() is Decision.Block)
    }

    @Test
    fun `selama cooldown aplikasi terdaftar mana pun diblokir, whitelist tidak`() {
        val clock = FakeClock()
        val e = engine(clock)
        e.onForegroundChanged(appA)
        clock.advance(60_000L)
        e.onTick()
        assertTrue(e.onForegroundChanged(appB) is Decision.Block)
        assertEquals(Decision.Allow, e.onForegroundChanged(dialer))
    }

    @Test
    fun `setelah cooldown berakhir allowance segar kembali`() {
        val clock = FakeClock()
        val e = engine(clock)
        e.onForegroundChanged(appA)
        clock.advance(60_000L)
        e.onTick()
        clock.advance(120_000L)
        val d = e.onForegroundChanged(appA)
        assertTrue(d is Decision.LimitIn)
        assertEquals(0L, e.state.usedMs)
    }

    @Test
    fun `idle reset - menjauh selama cooldown menghapus waktu terpakai`() {
        val clock = FakeClock()
        val e = engine(clock)
        e.onForegroundChanged(appA)
        clock.advance(50_000L)
        e.onForegroundChanged(null)
        clock.advance(120_000L) // jauh >= cooldown
        e.onForegroundChanged(appA)
        // usedMs harus sudah 0 sebelum sesi baru dihitung.
        assertEquals(0L, e.state.usedMs)
    }

    @Test
    fun `idle sebentar tidak me-reset`() {
        val clock = FakeClock()
        val e = engine(clock)
        e.onForegroundChanged(appA)
        clock.advance(50_000L)
        e.onForegroundChanged(null)
        clock.advance(30_000L)
        e.onForegroundChanged(appA)
        assertEquals(50_000L, e.state.usedMs)
    }

    @Test
    fun `mengubah pengaturan saat cooldown tidak memendekkan dan tidak memanjangkan cooldown`() {
        val clock = FakeClock()
        val e = engine(clock)
        e.onForegroundChanged(appA)
        clock.advance(60_000L)
        e.onTick()
        val before = e.blockedRemainingMs()
        e.updateSettings(settings(allowanceMs = 10_000L, cooldownMs = 1_000L))
        assertEquals(before, e.blockedRemainingMs())
        e.updateSettings(settings(allowanceMs = 999_000L, cooldownMs = 999_000L))
        assertEquals(before, e.blockedRemainingMs())
    }

    @Test
    fun `layar mati menjeda timer`() {
        val clock = FakeClock()
        val e = engine(clock)
        e.onForegroundChanged(appA)
        clock.advance(30_000L)
        e.onScreenOff()
        clock.advance(10 * 60_000L)
        e.onScreenOn()
        assertEquals(30_000L, e.currentUsedMs())
        assertFalse(e.onTick() is Decision.Block)
    }

    @Test
    fun `reboot selama cooldown tidak menghapus blokir`() {
        val clock = FakeClock()
        val e = engine(clock)
        e.onForegroundChanged(appA)
        clock.advance(60_000L)
        e.onTick()
        clock.advance(30_000L) // 30 detik cooldown terlewati
        val stateBeforeReboot = e.state
        clock.reboot()
        // Engine dibuat ulang dari state tersimpan, seperti setelah BOOT_COMPLETED.
        val restored = RulesEngine(clock, stateBeforeReboot, settings())
        assertEquals(90_000L, restored.blockedRemainingMs())
        assertTrue(restored.onForegroundChanged(appA) is Decision.Block)
    }

    @Test
    fun `memundurkan jam setelah reboot tidak melewati cooldown`() {
        val clock = FakeClock()
        val e = engine(clock)
        e.onForegroundChanged(appA)
        clock.advance(60_000L)
        e.onTick()
        val stateBeforeReboot = e.state
        clock.reboot()
        clock.wallMs -= 6 * 60 * 60_000L // jam dimundurkan 6 jam
        val restored = RulesEngine(clock, stateBeforeReboot, settings())
        // Sisa tetap dihitung dari lastSeenWall, bukan dari jam yang dimundurkan.
        assertEquals(120_000L, restored.blockedRemainingMs())
    }

    @Test
    fun `break-glass memberi 5 menit sekali per 24 jam dan tidak menggeser cooldown`() {
        val clock = FakeClock()
        // Cooldown 10 menit (> kelonggaran 5 menit) supaya blokir terbukti berlanjut sesudahnya.
        val e = engine(clock, settings(cooldownMs = 600_000L))
        e.onForegroundChanged(appA)
        clock.advance(60_000L)
        e.onTick()
        val remainingBefore = e.blockedRemainingMs()

        // Frasa salah ditolak.
        assertFalse(e.requestBreakGlass("saya menyerah", 5_000L))
        // Tahan kurang dari 5 detik ditolak.
        assertFalse(e.requestBreakGlass(RulesEngine.BREAK_GLASS_PHRASE, 2_000L))
        // Frasa benar dengan normalisasi huruf besar dan spasi ganda diterima.
        assertTrue(
            e.requestBreakGlass(
                "  SAYA  sadar ini keputusan saya dan saya siap menerima akibatnya sekarang juga ",
                5_000L,
            )
        )
        assertTrue(e.isGraceActive())
        assertFalse(e.isBlocked())
        assertEquals(remainingBefore, e.blockedRemainingMs())

        // Kelonggaran berakhir -> blokir berlanjut dengan sisa cooldown yang sama alurnya.
        clock.advance(RulesEngine.BREAK_GLASS_GRACE_MS)
        assertTrue(e.onForegroundChanged(appA) is Decision.Block)
        // Pemakaian kedua dalam 24 jam ditolak.
        assertFalse(e.requestBreakGlass(RulesEngine.BREAK_GLASS_PHRASE, 5_000L))
        assertEquals(1, e.state.breakGlassLog.size)
    }

    @Test
    fun `aplikasi di luar daftar tidak pernah dihitung`() {
        val clock = FakeClock()
        val e = engine(clock)
        e.onForegroundChanged("com.example.maps")
        clock.advance(10 * 60_000L)
        assertEquals(Decision.Allow, e.onTick())
        assertEquals(0L, e.state.usedMs)
    }
}
