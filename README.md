# Doomscroll Blocker

Aplikasi Android untuk membatasi doomscrolling dengan aturan sederhana:
pilih daftar aplikasi, beri satu jatah waktu bersama (bawaan 15 menit), dan
apabila jatah itu habis, seluruh daftar terkunci selama cooldown (bawaan 60 menit).

- Repo: `qielll/doomscroll-blocker-group-7`
- applicationId: `com.qiell.doomscrollblocker`
- Kotlin + Jetpack Compose, minSdk 26, compileSdk/targetSdk 35, JDK 17

## Cara kerja singkat

1. Pengguna memilih aplikasi yang dibatasi dan mengatur jatah + cooldown.
2. `AccessibilityService` memberi tahu aplikasi mana yang sedang di depan
   (hanya nama paketnya — isi layar tidak pernah dibaca).
3. Mesin aturan murni Kotlin (`RulesEngine`) menghitung waktu terpakai bersama.
4. Jatah habis → `BlockActivity` tampil tanpa tombol keluar, lalu pengguna
   dikirim ke Home. Membuka aplikasi terdaftar mana pun selama cooldown
   mengulangi hal yang sama.
5. Cooldown selesai (atau menjauh dari aplikasi terdaftar selama cooldown —
   *idle reset*) → jatah kembali penuh.

Aturan lengkap ada di [ARCHITECTURE.md](ARCHITECTURE.md). Status pengerjaan
dan celah yang diketahui ada di [PROGRESS.md](PROGRESS.md).

## Membangun proyek

Ikuti [PANDUAN_SETUP_DAN_PENGUJIAN.md](PANDUAN_SETUP_DAN_PENGUJIAN.md) —
berisi instalasi Android Studio / JDK 17 / SDK 35, cara membuat
`gradle-wrapper.jar` (file biner itu sengaja tidak disimpan di repo ini),
menjalankan unit test, memasang APK ke ponsel, dan daftar periksa pengujian
manual untuk semua kriteria penerimaan.

Ringkasnya, setelah wrapper tersedia:

```bash
./gradlew testDebugUnitTest   # unit test mesin aturan (JVM, tanpa emulator)
./gradlew assembleDebug       # hasil: app/build/outputs/apk/debug/app-debug.apk
```

## Mengaktifkan di ponsel

1. Pasang APK, buka aplikasi.
2. Baca kartu pengungkapan di layar utama, lalu ketuk
   **Buka pengaturan aksesibilitas** dan aktifkan
   *Doomscroll Blocker — deteksi aplikasi*.
   Pengungkapan itu wajib dan jujur: aplikasi mendeteksi aplikasi mana yang
   sedang dibuka untuk menegakkan batas yang kamu atur sendiri; tidak ada isi
   layar yang dibaca atau disimpan.
3. Kembali ke aplikasi dan berikan **pengecualian baterai** apabila diminta.
4. Pilih aplikasi yang dibatasi, atur waktu (untuk pengujian pertama pakai
   preset jatah **1 menit**), lalu pakai aplikasi tersebut selama 1 menit.

## Catatan OEM (Xiaomi / Oppo / Vivo / Samsung)

Produsen tersebut agresif mematikan layanan latar belakang:

- Izinkan *Autostart* untuk aplikasi ini (Xiaomi: Pengaturan → Aplikasi →
  Doomscroll Blocker → Autostart).
- Atur baterai ke *Tidak dibatasi / Unrestricted*.
- Kunci aplikasi di layar *Recent apps* apabila merek ponselmu mendukungnya.
- Apabila layanan berhenti, buka lagi pengaturan aksesibilitas dan
  matikan-nyalakan layanan Doomscroll Blocker.

Perilaku ini tidak terlihat di emulator — pengujian wajib di perangkat nyata.

## Skrip demo (2 menit)

1. Atur jatah 1 menit, cooldown 15 menit, pilih 2 aplikasi (mis. TikTok + Instagram).
2. Pakai aplikasi pertama 40 detik, pindah ke aplikasi kedua.
   Tunjukkan bahwa waktu **dijumlahkan**: setelah 20 detik di aplikasi kedua,
   layar blokir muncul.
3. Tunjukkan layar blokir tidak punya tombol keluar, tombol Back tidak bekerja,
   dan setelah beberapa detik pengguna dikirim ke Home.
4. Buka lagi aplikasi mana pun dari daftar → langsung terblokir lagi.
5. Buka Telepon / Pesan → normal (whitelist darurat).
6. (Opsional, jangan didemokan penuh) jelaskan break-glass: tahan hitung mundur
   5 detik → tunggu 60 detik → ketik frasa → kelonggaran 5 menit, sekali per
   24 jam, cooldown tidak bergeser.

## Di luar cakupan v1

iOS, batas per aplikasi, jadwal, grafik riwayat, akun/sinkronisasi awan,
pemblokiran situs, perlindungan anti-uninstall (mencopot aplikasi selalu bisa),
serta jalur bypass seperti split-screen / PiP / deep-link notifikasi
(tercatat sebagai celah yang diterima di PROGRESS.md).
