# Panduan Setup Pengembangan dan Pengujian Manual

Dokumen ini menggantikan pengujian di mesin assistant (sesuai keputusan
pemilik proyek: development saja di sisi assistant). Semua langkah di bawah
dijalankan di **mesin dan ponsel kamu sendiri**. Ikuti berurutan; setiap
bagian diakhiri tanda periksa supaya mudah melanjutkan di lain waktu.

---

## 1. Instalasi perkakas

- [ ] **JDK 17** — cek dengan `java -version` (harus 17.x). Unduh dari
  Temurin (Adoptium) apabila belum ada.
- [ ] **Android Studio** versi stabil terbaru (Ladybug atau yang lebih baru).
  Saat pertama dibuka, biarkan ia memasang komponen SDK yang ditawarkan.
- [ ] **Android SDK 35** — di Android Studio: *Settings → Languages &
  Frameworks → Android SDK → SDK Platforms* → centang **Android 15.0 (API 35)**,
  dan di tab *SDK Tools* pastikan **Android SDK Build-Tools** dan
  **Android SDK Platform-Tools** terpasang.
- [ ] **Git** — cek dengan `git --version`.

Versi yang dipatok proyek: Gradle 8.9, Android Gradle Plugin 8.7.3,
Kotlin 2.0.21, compileSdk/targetSdk 35, minSdk 26.

## 2. Membuka proyek dan membuat Gradle Wrapper

Repo ini menyimpan `gradle/wrapper/gradle-wrapper.properties`, tetapi
**`gradle-wrapper.jar` dan skrip `gradlew` sengaja tidak ikut tersimpan**
(file biner tidak bisa diunggah lewat alur yang dipakai untuk mendorong
proyek ini). Buat sekali saja di mesinmu:

- [ ] Kloning repo, masuk ke folder proyek:

  ```bash
  git clone https://github.com/qielll/doomscroll-blocker-group-7.git
  cd doomscroll-blocker-group-7
  ```

- [ ] Apabila kamu punya Gradle 8.9 terpasang di sistem:

  ```bash
  gradle wrapper --gradle-version 8.9
  ```

  Apabila tidak: buka folder ini di **Android Studio** dan pilih
  *File → New → ... * tidak perlu — cara paling gampang adalah memasang
  Gradle lewat SDKMAN/Homebrew/winget (`winget install Gradle.Gradle`),
  lalu jalankan perintah di atas. Setelah itu `gradlew`, `gradlew.bat`, dan
  `gradle/wrapper/gradle-wrapper.jar` akan muncul; **commit ketiganya** supaya
  clone berikutnya langsung bisa membangun.
- [ ] Buka proyek di Android Studio, tunggu *Gradle Sync* selesai tanpa error.
  Error unduhan dependensi hampir selalu berarti jaringan/proxy — coba lagi
  dengan jaringan lain sebelum mengubah versi apa pun.

## 3. Menjalankan unit test mesin aturan (tanpa ponsel)

- [ ] Jalankan:

  ```bash
  ./gradlew testDebugUnitTest
  ```

- [ ] Hasil yang diharapkan: semua test di
  `app/src/test/java/com/qiell/doomscrollblocker/engine/RulesEngineTest.kt`
  **hijau**. Test tersebut mencakup: allowance tercapai, penjumlahan lintas
  aplikasi, jeda (bukan reset) saat keluar, blokir selama cooldown, jatah
  segar setelah cooldown, idle reset, cooldown beku terhadap perubahan
  pengaturan, jeda saat layar mati, reboot, pemunduran jam, break-glass
  sekali per 24 jam, dan aplikasi di luar daftar.
- [ ] Apabila ada test merah, **jangan ubah test-nya dulu**: catat nama test
  dan pesan errornya ke PROGRESS.md — test ditulis dari aturan spesifikasi,
  jadi kegagalan biasanya berarti bug di engine.

## 4. Memasang ke ponsel

- [ ] Aktifkan **Developer options** + **USB debugging** di ponsel
  (atau pakai *Wireless debugging*), sambungkan, dan jalankan:

  ```bash
  ./gradlew installDebug
  ```

- [ ] Buka aplikasi → baca kartu pengungkapan → **Buka pengaturan
  aksesibilitas** → aktifkan *Doomscroll Blocker — deteksi aplikasi*.
- [ ] Kembali ke aplikasi → terima permintaan **pengecualian baterai**.
- [ ] Di pemilih aplikasi, centang **dua** aplikasi yang sering kamu pakai.
  Di pengaturan waktu, pilih preset jatah **1 menit** dan cooldown
  **15 menit** untuk seluruh pengujian di bawah.

## 5. Daftar periksa pengujian manual (memetakan kriteria penerimaan §8)

Tandai setiap baris hanya setelah benar-benar dicoba di ponsel.
"Terblokir" berarti: layar blokir muncul, Back tidak menghilangkannya,
dan ±2–3 detik kemudian kamu dikirim ke Home.

- [ ] **AC-1 Batas tercapai.** Pakai aplikasi terdaftar A selama 1 menit
  tanpa keluar → terblokir.
- [ ] **AC-2 Dijumlahkan lintas aplikasi.** Reset dulu (tunggu cooldown
  selesai). Pakai A 40 detik → pindah ke aplikasi terdaftar B → setelah
  ±20 detik di B, terblokir.
- [ ] **AC-3 Keluar sebentar = jeda, bukan reset.** Pakai A 40 detik →
  ke Home/launcher 30 detik → kembali ke A → terblokir setelah ±20 detik,
  **bukan** setelah 1 menit penuh.
- [ ] **AC-4 Selama cooldown selalu terblokir.** Saat cooldown berjalan,
  buka A → terblokir; buka B → terblokir; ulangi 2–3 kali, hasil sama.
  Tombol Back dan Home tidak membebaskan lebih awal.
- [ ] **AC-5 Cooldown selesai = jatah segar.** Tunggu cooldown 15 menit
  berakhir (atau atur ulang skenario dengan cooldown preset terkecil yang
  kamu buat khusus: 1 menit lewat field khusus) → A bisa dipakai lagi
  selama jatah penuh.
- [ ] **AC-6 Idle reset.** Pakai A 50 detik → tutup dan jangan sentuh
  aplikasi terdaftar apa pun selama durasi cooldown penuh → buka A:
  indikator di layar utama menunjukkan terpakai mulai dari 0 lagi.
- [ ] **AC-7 Pengaturan tidak memendekkan cooldown.** Saat terblokir,
  ubah cooldown menjadi 120 menit dan jatah menjadi 30 menit di
  pengaturan → sisa waktu di layar blokir **tidak berubah**; setelah itu
  ubah cooldown menjadi 15 menit → sisa tetap tidak berubah.
- [ ] **AC-8 Bertahan dari kill / reboot.** Saat terblokir: (a) paksa
  berhenti tidak usah dicoba dari aplikasi ini sendiri — cukup matikan-
  nyalakan layanan aksesibilitas, lalu buka A → tetap terblokir.
  (b) **Reboot ponsel** saat cooldown → setelah menyala, buka A → tetap
  terblokir dengan sisa waktu yang masuk akal.
- [ ] **AC-9 Jam dimundurkan tidak melewati cooldown.** Saat terblokir,
  reboot ponsel, lalu di Pengaturan Android matikan tanggal otomatis dan
  mundurkan tanggal 1 hari → buka A → tetap terblokir. Kembalikan tanggal
  otomatis setelahnya.
- [ ] **AC-10 Whitelist darurat.** Saat terblokir, buka **Telepon/Dialer**
  dan **Pesan/SMS** bawaan → keduanya terbuka normal. Cek juga aplikasi
  tersebut **tidak muncul** di pemilih aplikasi.
- [ ] **AC-11 Break-glass.** Di layar blokir, tahan teks hitung mundur
  5 detik → muncul hitungan tunggu 60 detik (tidak bisa dipercepat) →
  ketik frasa yang ditampilkan dengan tepat → aplikasi terdaftar bisa
  dipakai 5 menit → setelah 5 menit, blokir berlanjut dengan sisa cooldown.
  Coba break-glass **kedua kali** di hari yang sama → ditolak, dan layar
  utama mencatat tepat **1** pemakaian di log break-glass.
- [ ] **AC-12 Unit test hijau.** Lihat bagian 3 — semua test lulus.
- [ ] **Layar mati menjeda.** Pakai A 30 detik → matikan layar 2 menit →
  nyalakan → indikator terpakai tetap ±30 detik, bukan 2,5 menit.
- [ ] **Aplikasi lain tidak terpengaruh.** Aplikasi yang tidak dicentang
  bisa dipakai tanpa batas selama pengujian di atas.

## 6. Pengujian khusus OEM (Milestone 6)

Ulangi AC-1, AC-4, dan AC-8 di setiap perangkat yang tersedia, catat hasilnya
di PROGRESS.md (merek, model, versi Android, lulus/gagal):

- [ ] Samsung (One UI) — baterai aplikasi diatur *Unrestricted*.
- [ ] Xiaomi/Redmi/POCO (MIUI/HyperOS) — *Autostart* aktif + baterai
  *No restrictions* + kunci aplikasi di Recent Apps.
- [ ] Oppo/Realme/Vivo — izinkan berjalan di latar belakang dari halaman
  info aplikasi.
- [ ] Biarkan ponsel diam 30 menit dalam keadaan terblokir, lalu buka
  aplikasi terdaftar → harus tetap terblokir (layanan tidak dibunuh diam-diam).

## 7. Apabila ada yang gagal

1. Catat: nomor AC, merek/model ponsel, versi Android, langkah persis,
   dan apa yang terjadi vs. yang diharapkan.
2. Tulis ke PROGRESS.md di bagian celah/risiko, lalu bawa catatan itu ke
   chat proyek — perbaikan dilakukan per milestone, bukan mengubah banyak
   file sekaligus.
3. Untuk masalah layanan mati: periksa dulu tiga pengaturan OEM di bagian 6
   sebelum menganggap ada bug kode.

## 8. Membangun APK rilis demo (opsional)

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk — cukup untuk demo sideload.
```

APK debug sudah memadai untuk demo. Penandatanganan rilis dan Play Store
bukan bagian dari v1.
