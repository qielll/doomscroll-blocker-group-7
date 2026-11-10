# Arsitektur — Doomscroll Blocker

## Aturan dalam bahasa sederhana (kontrak §4)

Satu anggaran bersama untuk seluruh daftar.

1. Aplikasi di luar daftar, dan aplikasi whitelist, tidak pernah disentuh.
   Timer berhenti selama pengguna berada di aplikasi tersebut.
2. Selama aplikasi terdaftar di foreground **dan layar menyala**, waktu
   terpakai bertambah. Waktu itu dibagi bersama seluruh daftar, jadi berpindah
   dari satu aplikasi terdaftar ke aplikasi lain tidak me-reset apa pun.
3. Meninggalkan aplikasi terdaftar **tidak** me-reset waktu terpakai. Hanya jeda.
4. Saat waktu terpakai mencapai **allowance** (bawaan 15 menit), pengguna
   dianggap doomscrolling: tampilkan layar blokir, kirim ke Home, dan tetapkan
   `blockedUntil = sekarang + cooldown`.
5. Selama cooldown, membuka aplikasi terdaftar mana pun menampilkan layar
   blokir dan mengirim ke Home lagi.
6. Saat cooldown berakhir, waktu terpakai reset ke 0 dan status kembali ALLOWED.
7. **Idle reset (terkonfirmasi):** apabila pengguna berstatus ALLOWED dan
   menjauh dari seluruh aplikasi terdaftar selama minimal durasi cooldown,
   waktu terpakai juga reset ke 0. Tanpa aturan ini, 10 menit yang terpakai
   kemarin akan tetap terhitung hari ini. Jadi cooldown bermakna ganda:
   "berapa lama sampai jatah reset", dalam dua-duanya.
8. **Cooldown yang berjalan dibekukan penuh (terkonfirmasi).** `blockedUntil`
   dihitung sekali saat blokir terpicu dan tidak pernah dihitung ulang.
   Perubahan allowance/cooldown tidak memendekkan dan tidak memanjangkan
   cooldown yang sedang berjalan; nilai baru berlaku mulai siklus berikutnya.
   Kelonggaran break-glass juga tidak pernah menggeser `blockedUntil`.

### Mesin status

```
        ┌───────────────────────────────────────────────┐
        │                                               │
        ▼                                               │
   [ALLOWED] ── usedMs ≥ allowance ──► [COOLDOWN] ── now ≥ blockedUntil ──┘
   timer runs while a                  any listed app → block screen → Home
   listed app is foreground            usedMs reset to 0 on exit
        ▲   │
        └───┘ away ≥ cooldown → usedMs = 0 (idle reset)
```

## Komponen

| Bagian | File | Tanggung jawab |
|---|---|---|
| Mesin aturan | `engine/RulesEngine.kt` | Semua keputusan. Kotlin murni, tanpa import Android, jam disuntikkan lewat `engine/Clock.kt`. |
| Model | `engine/EngineModels.kt` | `BlockerSettings`, `EngineState`, `Decision`. |
| Jam perangkat | `engine/AndroidClock.kt` | `elapsedRealtime`, `currentTimeMillis`, `BOOT_COUNT`. |
| Penyimpanan | `data/DataStoreRepository.kt` | DataStore Preferences; kunci mengikuti model §5 satu per satu. Tanpa Room di v1. |
| Deteksi | `service/AppBlockerAccessibilityService.kt` | Event `TYPE_WINDOW_STATE_CHANGED`, penyaringan derau, timer batas, kirim ke Home. |
| Layar blokir | `ui/BlockActivity.kt` | Hitung mundur langsung, tanpa keluar, alur break-glass. |
| Whitelist | `whitelist/WhitelistResolver.kt` | Dialer/SMS bawaan perangkat dibaca runtime + Settings + aplikasi sendiri + cadangan hardcoded. |
| Reboot | `receiver/BootReceiver.kt` | Evaluasi ulang state tersimpan setelah `BOOT_COMPLETED`. |
| UI utama | `ui/MainScreens.kt`, `ui/MainViewModel.kt` | Status, pemilih aplikasi, pengaturan preset + khusus, pengungkapan aksesibilitas. |

Alur data: service menerima event → memuat settings + state → bertanya ke
engine → menyimpan state baru → menjalankan keputusan (jadwalkan timer /
tampilkan blokir / tidak melakukan apa-apa). UI utama hanya membaca dan
menulis settings + membaca state; UI tidak pernah mengambil keputusan blokir.

## Mengapa timer terjadwal, bukan hanya event

Scroll di dalam aplikasi tidak memicu event jendela baru. Maka saat aplikasi
terdaftar masuk foreground, engine mengembalikan `Decision.LimitIn(ms)` dan
service menjadwalkan satu timer selama itu. Timer memanggil `onTick()`, yang
menilai ulang dan memicu blokir tepat saat jatah habis. Timer dibatalkan saat
pengguna keluar dari aplikasi terdaftar atau layar mati.

## Penyaringan derau foreground

Paket berikut diabaikan saat menentukan foreground, karena mereka memancarkan
event jendela yang akan menjeda/memulai ulang timer secara keliru:
`com.android.systemui`, metode masukan (keyboard) yang sedang aktif
(dibaca dari `Settings.Secure.DEFAULT_INPUT_METHOD`), dialog izin
(`permissioncontroller`), dan paket aplikasi ini sendiri.

## Ketahanan terhadap manipulasi jam

State menyimpan dua penanda akhir cooldown sekaligus, plus `bootCount` saat
blokir terpicu dan `lastSeenWall` (jam dinding terbesar yang pernah dilihat,
diperbarui pada setiap event):

- `bootCount` sama → percaya jam elapsed saja (`blockedUntilElapsed`).
  Jam elapsed monoton dan tidak peduli perubahan tanggal.
- `bootCount` berubah (reboot) → pakai jam dinding (`blockedUntilWall`).
  Apabila `now < lastSeenWall` (jam dimundurkan), sisa dihitung sebagai
  `blockedUntilWall − lastSeenWall` — titik terakhir yang terpercaya —
  sehingga memundurkan tanggal tidak pernah melewati cooldown.

## Layar blokir: Activity, bukan overlay (deviasi tercatat)

Keputusan terkonfirmasi 2026-11-10 membalik urutan awal spesifikasi:
`BlockActivity` (`singleTask`, `excludeFromRecents`, `showWhenLocked`) adalah
implementasi utama v1. Overlay aksesibilitas (`TYPE_ACCESSIBILITY_OVERLAY`)
ditunda ke v2 karena Compose di dalam overlay membutuhkan penyiapan manual
`LifecycleOwner`/`SavedStateRegistryOwner` dan tidak bisa diverifikasi tanpa
perangkat nyata; keandalan demo lebih penting. Setelah layar blokir tampil,
service memanggil `performGlobalAction(GLOBAL_ACTION_HOME)` ±2,5 detik kemudian.

## Break-glass

Tersembunyi, berlapis, dan terbatas:

1. Tahan teks hitung mundur selama 5 detik (diperiksa juga oleh engine).
2. Tunggu wajib 60 detik (ditegakkan UI, tidak bisa dipercepat).
3. Ketik frasa persis: *"saya sadar ini keputusan saya dan saya siap menerima
   akibatnya sekarang juga"* — pencocokan dinormalisasi (huruf kecil, spasi
   dirapikan).
4. Hasilnya kelonggaran 5 menit, **sekali per 24 jam**, tercatat di log yang
   tampil di layar utama.
5. Kelonggaran tidak me-reset dan tidak memendekkan cooldown: sesudahnya
   blokir berlanjut dengan sisa cooldown yang sama alurnya.

## Whitelist darurat

Telepon/Dialer, Pesan, Kontak/Darurat, Settings sistem, dan aplikasi ini
sendiri tidak pernah diblokir dan disembunyikan dari pemilih aplikasi.
Dialer dan aplikasi SMS bawaan diselesaikan **secara dinamis** dari perangkat
(berbeda tiap OEM) lewat `TelecomManager.defaultDialerPackage` dan resolusi
intent DIAL/SENDTO; paket hardcoded hanya cadangan.

## Keputusan penyimpanan

DataStore Preferences dipilih sebagai opsi paling sederhana di v1 (tanpa Room).
Log break-glass disimpan sebagai daftar epoch yang dipisahkan koma — cukup
untuk daftar yang maksimal bertambah satu entri per hari.
