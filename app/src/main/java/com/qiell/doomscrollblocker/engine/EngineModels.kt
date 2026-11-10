package com.qiell.doomscrollblocker.engine

/**
 * Pengaturan yang dikontrol pengguna.
 *
 * [blockedPackages] memakai SATU anggaran bersama (bukan per aplikasi):
 * waktu di aplikasi mana pun dalam daftar ini ditambahkan ke penghitung yang sama.
 *
 * [whitelistedPackages] tidak pernah diblokir dan tidak pernah dihitung,
 * walaupun pengguna memasukkannya ke daftar blokir secara tidak sengaja.
 * Daftar ini diisi runtime oleh WhitelistResolver (dialer/SMS bawaan perangkat,
 * Settings, dan aplikasi ini sendiri).
 */
data class BlockerSettings(
    val blockedPackages: Set<String> = emptySet(),
    val allowanceMs: Long = DEFAULT_ALLOWANCE_MS,
    val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    val whitelistedPackages: Set<String> = emptySet(),
) {
    companion object {
        const val DEFAULT_ALLOWANCE_MS: Long = 15 * 60_000L
        const val DEFAULT_COOLDOWN_MS: Long = 60 * 60_000L
    }
}

/**
 * Keadaan engine yang harus bertahan melewati service mati, aplikasi ditutup,
 * dan reboot. Inilah yang disimpan ke DataStore (lihat DataStoreRepository).
 *
 * Semua cap waktu elapsed hanya bermakna selama [blockBootCount] / boot saat ini
 * sama; setelah reboot, pasangan versi wall-clock yang dipakai.
 */
data class EngineState(
    /** Waktu terpakai pada jendela saat ini, sudah diakumulasikan (tidak termasuk sesi foreground berjalan). */
    val usedMs: Long = 0L,
    /** Kapan aplikasi terdaftar saat ini masuk foreground (elapsed). Null = tidak sedang di aplikasi terdaftar. */
    val foregroundSinceElapsed: Long? = null,
    /** Paket yang sedang di foreground menurut event terakhir. */
    val foregroundPackage: String? = null,
    /** Layar sedang menyala. Timer hanya berjalan saat true. */
    val screenOn: Boolean = true,
    /** Kapan terakhir meninggalkan seluruh aplikasi terdaftar (elapsed), untuk idle reset. */
    val lastLeftListedElapsed: Long? = null,
    /** Akhir cooldown menurut jam elapsed. Null = status ALLOWED. Diisi SEKALI saat blokir terpicu dan tidak pernah dihitung ulang. */
    val blockedUntilElapsed: Long? = null,
    /** Akhir cooldown menurut jam dinding, pasangan fallback setelah reboot. */
    val blockedUntilWall: Long? = null,
    /** BOOT_COUNT saat blokir terpicu. */
    val blockBootCount: Int? = null,
    /** Jam dinding terbesar yang pernah dilihat. Melindungi dari pemunduran jam. */
    val lastSeenWall: Long = 0L,
    /** Kapan break-glass terakhir dipakai (wall), untuk batas sekali per 24 jam. */
    val breakGlassLastUsedWall: Long? = null,
    /** Akhir jendela kelonggaran break-glass (elapsed). Tidak menggeser blockedUntil*. */
    val graceUntilElapsed: Long? = null,
    /** BOOT_COUNT saat kelonggaran break-glass diberikan. */
    val graceBootCount: Int? = null,
    /** Log waktu (wall) setiap pemakaian break-glass, ditampilkan di layar utama. */
    val breakGlassLog: List<Long> = emptyList(),
)

/** Keputusan engine untuk satu event. */
sealed interface Decision {
    /** Paket ini boleh dipakai. */
    data object Allow : Decision

    /** Paket ini harus diblokir sekarang; [remainingMs] adalah sisa cooldown. */
    data class Block(val remainingMs: Long) : Decision

    /**
     * Paket terdaftar sedang dipakai dan masih diizinkan.
     * [limitInMs] adalah waktu sampai batas tercapai apabila pengguna tetap di
     * aplikasi terdaftar — pemanggil wajib menjadwalkan timer selama ini,
     * karena scroll di dalam aplikasi tidak memicu event jendela baru.
     */
    data class LimitIn(val limitInMs: Long) : Decision
}
