# IDLIX: Android TV & Android Mobile 🎬📺📱

<p align="center">
  <img src="https://cdn.abdilah.id/uploads/com.idlix.tv_and_com.idlix.mobile-preview.png">
</p>

<p align="center">
  <a href="https://saweria.co/everydaysammmy" target="_blank">
    <img src="https://img.shields.io/badge/☕_Buy_Me_a_Coffee-Saweria-faad14?style=for-the-badge" alt="Buy Me a Coffee (Saweria)" />
  </a>
</p>

> ☕ **Dukungan & Kontribusi:** Suka dengan proyek ini? Anda dapat mendukung pengembangan aplikasi ini melalui [**Saweria: Buy me a coffee**](https://saweria.co/everydaysammmy).

---

### 📑 Index
* [Fitur Utama (Key Features)](#fitur-utama)
  * [Android Mobile Client (`app-mobile`)](#fitur-mobile)
  * [Android TV Client (`app-tv`)](#fitur-tv)
  * [Backend API Service](#fitur-backend)
* [Struktur Direktori Multi-Module (Project Structure)](#struktur-direktori)
* [Panduan Memulai & Kompilasi APK (Build Guide)](#panduan-build)
  * [1. Menjalankan Backend API](#build-backend)
  * [2. Membangun APK Android TV (`app-tv`)](#build-tv)
  * [3. Membangun APK Android Mobile (`app-mobile`)](#build-mobile)
* [Download Release APK](#download-release)
* [Frequently Asked Questions (FAQ)](#faq)
  * [Mengalami Error HTTP 403 (Forbidden)?](#faq-403)
* [Keamanan & Lisensi](#keamanan-lisensi)

---

A modern, high-performance, **100% native Android streaming platform** for movies and TV series from IDLIX with multi-platform support (Android TV & Android Mobile) built using a unified **Multi-Module Architecture**.

Project ini terbagi menjadi modul-modul independen yang saling melengkapi:
1. **Shared Core Data (`core/data/`)**: Modul library Kotlin murni tanpa dependensi UI yang mengelola Retrofit client, model data, deserializer toleran, repository, dan penyimpanan riwayat tontonan lokal (`WatchHistoryStore`).
2. **Android TV Client (`app-tv/`)**: Aplikasi khusus Android TV/Google TV berbasis Jetpack Compose for TV (`androidx.tv.material3`) dan Media3 ExoPlayer dengan antarmuka Leanback (remote D-Pad, 10-foot UI, fixed landscape).
3. **Android Mobile Client (`app-mobile/`)**: Aplikasi khusus smartphone & tablet berbasis Jetpack Compose **Material 3** modern (Bottom Navigation Bar, Swipeable Banner Pager, Inline-to-Fullscreen Player, Touch Gestures, dan **Picture-in-Picture**).
4. **Backend API Scraper & Proxy (`Backend API/`)**: Service Fastify TypeScript dengan headless stealth browser untuk bypass Cloudflare Turnstile, scraping katalog, dan resolusi one-shot token HLS stream.

---

<a id="fitur-utama"></a>
## 🌟 Fitur Utama (Key Features)

<a id="fitur-mobile"></a>
### 📱 Android Mobile Client (`app-mobile`)
* **Material 3 & Edge-to-Edge Display**:
  * Tampilan immersive modern dengan handling `WindowInsets` bebas dari benturan notch / kamera punch-hole.
  * Navigasi intuitif **Bottom Navigation Bar** 5 tab: *Home*, *Movies*, *Series*, *Search*, dan *Library*.
* **Hero Carousel & Infinite Scrolling**:
  * Pager banner horizontal otomatis (`HorizontalPager`) dengan indikator titik dinamis dan tombol aksi cepat.
  * Barisan *Lanjutkan Menonton* (Continue Watching) dengan thumbnail 16:9 dan progress bar tontonan.
  * Grid vertikal 3 kolom (smartphone) dan 5 kolom (tablet) dengan infinite scroll pagination dan filter chips genre/sorting.
* **Dual-Mode Player (Inline ke Fullscreen)**:
  * **Mode Inline (Portrait 16:9)**: Video langsung berputar di atas layar sambil pengguna tetap dapat membaca sinopsis dan memilih episode serial di bawahnya.
  * **Mode Fullscreen (Landscape)**: Transisi mulus ke layar penuh immersive melalui tombol expand atau sensor rotasi.
  * **BackHandler Cerdas**: Menekan tombol Back saat fullscreen akan mengecilkan player kembali ke mode inline (tidak langsung keluar layar).
* **Touch Gestures & Media Controls**:
  * **Double-Tap**: Sisi kiri mundur 10 detik, sisi kanan maju 10 detik disertai animasi ripple.
  * **Vertical Drag**: Sisi kiri untuk Brightness (kecerahan), sisi kanan untuk Volume suara.
  * **Timeline Scrubber**: Scrub horizontal presisi untuk loncat ke menit tertentu.
* **Picture-in-Picture (PiP)**:
  * Otomatis masuk ke mode floating PiP saat pengguna menekan Home atau beralih ke aplikasi lain (`onUserLeaveHint`).
* **Integrasi Subtitle SubDL**:
  * Pemilihan trek subtitle bawaan atau pencarian subtitle Bahasa Indonesia/Inggris via SubDL API.

<a id="fitur-tv"></a>
### 📺 Android TV Client (`app-tv`)
* **100% Native Architecture**: Bebas Chromium dan WebView di dalam APK. Menggunakan hardware decoder bawaan TV (`MediaCodec`) via AndroidX Media3 ExoPlayer.
* **Netflix-Style Leanback UI**:
  * Floating navigation sidebar dengan smooth overlay (tanpa re-layout konten).
  * Billboard Hero dinamis dengan preview sinopsis dan tombol Putar Cepat.
  * Baris Swimlane horizontal untuk Tren, Populer, Serial TV, dan Kategori Genre.
  * Fitur *Lanjutkan Menonton* (Continue Watching) dengan bar progress visual.
* **Token Query Preserving HLS Player**:
  * Mengatasi proteksi CDN Majorplay (Cloudflare token authorization `?t=...`) secara otomatis pada sub-playlist dan chunk segmen video (`.webp`, `.jpg`, `.js`, dll.).
  * Buffer kontrol TV adaptif untuk pemutaran instan tanpa stuttering.
* **Manajemen Subtitle & Integrasi SubDL**:
  * Subtitle internal IDLIX otomatis (Indonesian & English).
  * Integrasi third-party SubDL.com untuk pencarian dan pengunduhan subtitle alternatif langsung dari player remote TV.
* **Menu Pengaturan Terintegrasi (In-App Settings)**:
  * Pengaturan alamat Backend API langsung dari layar TV menggunakan remote D-pad.
  * Pengaturan custom API Key SubDL & tombol Uji Koneksi *real-time*.
* **Bilingual Localization**:
  * Mendukung penuh Bahasa Indonesia (`id`/`in`) dan English (`en`). Otomatis mengikuti bahasa sistem TV.

<a id="fitur-backend"></a>
### 🚀 Backend API Service
* **Cloudflare Turnstile & Challenge Bypass**: Stealth browser otomatis menyelesaikan proteksi Cloudflare di sisi server.
* **In-Memory LRU Caching**: Respons data instan (<15ms) untuk katalog film, serial, dan kategori.
* **All-in-One Playback Resolver**: Endpoint `/api/playback/resolve` menyelesaikan siklus `gateToken`, countdown timer provider (15 detik), session claim, dan HLS URL redemption dalam satu panggilan.
* **Ringan & Hemat Resource**: Mendukung Docker dengan batas memori <1GB, ideal dijalankan pada VPS murah (1–2GB RAM) atau komputer lokal.

---

<a id="struktur-direktori"></a>
## 📁 Struktur Direktori Multi-Module (Project Structure)

```
Idlix for TV/
├── core/
│   └── data/                            # [Shared Library] Model, API Retrofit, Repository & DB
│       ├── src/main/java/com/idlix/data/
│       │   ├── api/                     # ApiClient (Universal), IdlixApiService, MajorplayApiService
│       │   ├── local/                   # WatchHistoryStore (penyimpanan riwayat tontonan)
│       │   ├── model/                   # MovieItem, SeriesItem, Subdl, Serializers
│       │   └── repository/              # IdlixRepository & SubdlRepository
│       └── build.gradle.kts
│
├── app-tv/                              # [Application] Modul Khusus Android TV / Google TV
│   ├── src/main/java/com/idlix/tv/
│   │   ├── ui/
│   │   │   ├── components/              # TvNavDrawer, TvMovieCard, TvHeroBillboard, dll.
│   │   │   ├── navigation/              # TV NavHost & routes
│   │   │   ├── screens/                 # Home, Movies, Series, Detail, Player, Settings
│   │   │   └── theme/                   # Leanback Dark Theme
│   │   └── res/                         # Asset TV banner & strings localization
│   └── build.gradle.kts
│
├── app-mobile/                          # [Application] Modul Khusus Smartphone & Tablet
│   ├── src/main/java/com/idlix/mobile/
│   │   ├── ui/
│   │   │   ├── components/              # MobileMovieCard, HeroBannerPager, FilterChips, ContinueWatching
│   │   │   ├── navigation/              # Bottom Navigation Bar, NavHost, MainScaffold
│   │   │   ├── screens/                 # Home, Movies, Series, Search, Library, Detail, Settings
│   │   │   └── theme/                   # Material 3 Dark Theme & Edge-to-Edge styling
│   │   └── res/                         # Asset mobile & strings
│   └── build.gradle.kts
│
├── Backend API/                         # Service Backend Scraper & Cache (Node.js/Docker)
│   ├── src/
│   │   ├── engine/                      # Playwright Stealth Scraper & Token Resolver
│   │   ├── routes/                      # REST endpoints Fastify (/api/...)
│   │   └── services/                    # CacheService (LRU memory cache)
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── package.json
│
├── gradle/                              # Gradle wrapper & version catalogs (libs.versions.toml)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties                     # Konfigurasi lokal Android SDK & API URL (git-ignored)
└── PORTING_TO_MOBILE_PLAN.md            # Rencana arsitektur & roadmap implementasi teknis
```

---

<a id="panduan-build"></a>
## 🛠️ Panduan Memulai & Kompilasi APK (Build Guide)

<a id="build-backend"></a>
### 1. Menjalankan Backend API

Backend API wajib berjalan agar aplikasi Android TV dan Mobile dapat mengambil katalog film dan streaming video.

#### Menggunakan Docker / Podman (Direkomendasikan)
```bash
cd "Backend API"
docker compose up -d --build
```
Cek apakah server sudah aktif:
```bash
curl http://localhost:5376/health
```

---

<a id="build-tv"></a>
### 2. Membangun APK Android TV (`app-tv`)

#### Build APK Debug / Release:
```bash
# Debug APK
./gradlew :app-tv:assembleDebug
# Output: app-tv/build/outputs/apk/debug/app-tv-debug.apk

# Release APK
./gradlew :app-tv:assembleRelease
# Output: app-tv/build/outputs/apk/release/app-tv-release.apk
```

#### Install ke Android TV via ADB:
```bash
adb connect 192.168.x.tv:5555
adb -s 192.168.x.tv:5555 install -r app-tv/build/outputs/apk/debug/app-tv-debug.apk
```

---

<a id="build-mobile"></a>
### 3. Membangun APK Android Mobile (`app-mobile`)

#### Build APK Debug / Release:
```bash
# Debug APK
./gradlew :app-mobile:assembleDebug
# Output: app-mobile/build/outputs/apk/debug/app-mobile-debug.apk

# Release APK
./gradlew :app-mobile:assembleRelease
# Output: app-mobile/build/outputs/apk/release/app-mobile-release.apk
```

#### Install ke Perangkat Smartphone / Tablet via ADB:
```bash
# Cek ID perangkat mobile
adb devices

# Install ke perangkat target (contoh: Wi-Fi TLS / USB device)
adb -s "adb-DELFOVPBBQPFDIA6-W6Rq2R._adb-tls-connect._tcp" install -r app-mobile/build/outputs/apk/debug/app-mobile-debug.apk

# Jalankan aplikasi mobile
adb -s "adb-DELFOVPBBQPFDIA6-W6Rq2R._adb-tls-connect._tcp" shell am start -n com.idlix.mobile.debug/com.idlix.mobile.MainActivity
```

> **Catatan untuk Perangkat Xiaomi / MIUI / HyperOS**:
> Pastikan opsi **Install via USB** telah diaktifkan di *Developer Options*, dan tekan **Install / Allow** saat jendela konfirmasi muncul di layar ponsel.

---

<a id="download-release"></a>
## Download Release Apk

Look at this [page](https://github.com/samdgea/idlix-androidtv/releases).

---

<a id="faq"></a>
## ❓ Frequently Asked Questions (FAQ)

<a id="faq-403"></a>
### ⚠️ Mengalami Error HTTP 403 (Forbidden)?
Jika Anda mengalami error **403 Forbidden** saat memuat katalog film/serial atau memutar video untuk sementara:
1. Pastikan **Backend API Server** Anda telah diatur ke domain default IDLIX:
   👉 **`https://z3.idlixku.com/`**
2. **Cara Mengubah Alamat Backend API Server:**
   * **Di Aplikasi (Android TV & Mobile):** Buka menu **Pengaturan (Settings)** ⚙️ > masukkan atau ubah URL pada bagian **Backend API Server** ke `https://z3.idlixku.com/`. (Pastikan forward slash diakhir alamat juga terinput)
   * Test menekan tombol **Test Backend Connection** [Pastikan hasilnya Backend connected! (HTTP 200, xxxms)]
3. Setelah itu, Klik tombol **Save All Settings**

---

<a id="keamanan-lisensi"></a>
## 🔒 Keamanan & Lisensi

* File sensitif seperti `local.properties`, sertifikat signing keystore (`*.jks`), dan credential environment dilindungi di [`.gitignore`](file:///Users/abdi/Documents/Personal%20Workspace/Idlix%20for%20TV/.gitignore).
* Proyek ini dibuat untuk keperluan edukasi dan riset arsitektur multi-module Android Native (TV & Mobile) serta streaming HLS.
