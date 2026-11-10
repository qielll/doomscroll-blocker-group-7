# Kemajuan — Doomscroll Blocker

Dokumen ini adalah sumber kebenaran status proyek, supaya sesi baru bisa
melanjutkan hanya dari file. Terakhir diperbarui: 2026-11-10.

## Status: kode v1 selesai ditulis, BELUM dikompilasi / diuji di perangkat

Sesuai instruksi pemilik proyek, pembangunan di sisi assistant bersifat
*development saja*: tidak ada build environment yang dipasang di mesin
assistant, tidak ada kompilasi dan tidak ada pengujian otomatis yang dijalankan
di sana. Verifikasi dilakukan pemilik proyek mengikuti
[PANDUAN_SETUP_DAN_PENGUJIAN.md](PANDUAN_SETUP_DAN_PENGUJIAN.md).
Karena itu status di bawah adalah "ditulis", bukan "terverifikasi", sampai
daftar periksa di panduan itu dijalankan dan hasilnya dicatat di sini.

## Yang sudah ditulis (semua milestone 1–5)

- [x] **Milestone 1 — Mesin aturan.** `RulesEngine` Kotlin murni + model +
  `Clock`, beserta unit test JVM dengan jam palsu yang memetakan ke seluruh
  aturan §4: allowance bersama, jeda saat keluar, idle reset, cooldown beku,
  jeda saat layar mati, reboot, pemunduran jam, break-glass, whitelist.
- [x] **Milestone 2 — Deteksi.** `AppBlockerAccessibilityService`
  (`TYPE_WINDOW_STATE_CHANGED`, saring SystemUI/IME/dialog izin/paket sendiri,
  receiver layar mati/nyala, timer batas), kartu pengungkapan aksesibilitas,
  prompt pengecualian baterai, dan pemilih aplikasi launchable lewat
  deklarasi `<queries>` (tanpa `QUERY_ALL_PACKAGES`).
- [x] **Milestone 3 — Layar blokir.** `BlockActivity` dengan hitung mundur
  langsung, Back tanpa efek, dan pengiriman ke Home oleh service ±2,5 detik
  setelah tampil. (Overlay ditunda ke v2 — deviasi tercatat di ARCHITECTURE.md.)
- [x] **Milestone 4 — Pengkabelan + persistensi.** Service ↔ engine ↔
  DataStore; state bertahan melewati service mati / mulai ulang aplikasi;
  `BootReceiver` mengevaluasi ulang setelah reboot; UI pengaturan preset
  (allowance 1/5/10/15/30 mnt, cooldown 15/30/60/120 mnt) + menit khusus.
- [x] **Milestone 5 — Jalan keluar + OEM.** Whitelist dinamis
  (dialer/SMS bawaan perangkat + Settings + aplikasi sendiri + cadangan
  hardcoded), alur break-glass penuh (tahan 5 dtk → tunggu 60 dtk → frasa
  persis → 5 mnt, sekali/24 jam, tercatat), panduan OEM di README.
- [ ] **Milestone 6 — Pengujian perangkat.** Menunggu pemilik proyek:
  2–3 perangkat nyata termasuk minimal satu Xiaomi/Oppo/Samsung, mengikuti
  daftar periksa di PANDUAN_SETUP_DAN_PENGUJIAN.md.

## Keputusan yang mengikat (2026-11-10, semua default diterima pemilik proyek)

| # | Keputusan |
|---|---|
| P0-1 | Idle reset resmi, persis seperti §4 butir 7 |
| P0-2 | Cooldown berjalan dibekukan penuh; nilai baru berlaku siklus berikutnya |
| P1-3 | `BlockActivity` utama di v1; overlay v2 |
| P1-4 | Frasa break-glass: "saya sadar ini keputusan saya dan saya siap menerima akibatnya sekarang juga" |
| P1-5 | Whitelist dinamis + cadangan hardcoded |
| P2-6 | Repo `qielll/doomscroll-blocker-group-7`, publik |
| P2-7 | Dokumentasi & komentar kode Bahasa Indonesia; identifier Bahasa Inggris |
| P2-8 | applicationId `com.qiell.doomscrollblocker`, SDK 35, JDK 17 |
| P2-9 | Pengaturan: chip preset + field menit khusus, tanpa slider |

## Celah yang diketahui dan diterima di v1 (dari §3 spesifikasi)

- Split-screen, picture-in-picture, deep-link notifikasi, dan versi browser
  dari layanan yang sama dapat melewati blokir. Ditunda secara eksplisit.
- Tidak ada perlindungan anti-uninstall / Device Admin: mencopot aplikasi
  selalu bisa dilakukan di v1.
- Overlay aksesibilitas belum dibuat (v2).

## Risiko yang belum terverifikasi (harus dibuktikan di perangkat)

- Layanan dibunuh OEM (Xiaomi/Oppo/Samsung) — hanya terlihat di perangkat nyata.
- Ketepatan timer batas saat aplikasi tidak memancarkan event sama sekali.
- Perilaku `BlockActivity` + `GLOBAL_ACTION_HOME` di launcher pihak ketiga.
- `gradle-wrapper.jar` tidak ada di repo (file biner tidak disimpan oleh alur
  unggah proyek ini) — langkah membuatnya ada di PANDUAN, bagian 2.
