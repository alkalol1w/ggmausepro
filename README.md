# GGMausePro 🎯 - Android Klavye & Fare Tuş Eşleyici (PUBG Mobile & FPS)

**GGMausePro**, Android cihazınızda USB OTG veya Bluetooth ile bağladığınız fiziksel klavye ve fareyi kullanarak **PUBG Mobile, Call of Duty Mobile, Free Fire** gibi dokunmatik FPS oyunlarını sıfır gecikme (**Zero-Latency**) ile oynamanızı sağlayan profesyonel bir tuş eşleme (Key Mapper) uygulamasıdır.

Play Store'daki popüler *Panda Mouse Pro* ve *GG Mouse Pro* uygulamalarının tüm yeteneklerini içerir ve **Shizuku** entegrasyonu sayesinde **ROOT GEREKTİRMEZ**.

---

## 🚀 Öne Çıkan Özellikler

1. **Sıfır Gecikme (Zero-Latency) Shizuku Motoru**:
   - Standart Android Accessibility API gecikmesini aşmak için doğrudan `IInputManager` C++ Dispatcher düzeyinde eşleme yapar (`INJECT_INPUT_EVENT_MODE_ASYNC`).
   - Tuşa bastığınız anda dokunma sinyali 0 milisaniye gecikmeyle ekrana aktarılır.
   - 60 FPS / 90 FPS / 120 FPS ekranlarda akıcı fare kamera kontrolü.

2. **Görsel Ekran Düzenleyici (Floating HUD Overlay)**:
   - Oyun açıkken ekranda beliren şeffaf tuş baloncuklarını doğrudan PUBG butonlarının (ateş etme, zıplama, dürbün vb.) üzerine sürükleyip bırakabilirsiniz.
   - Tek tıkla kaydeder ve oyun moduna geçer.

3. **Özel PUBG Mobile Tuş Yerleşimi (Hazır Profil)**:
   - **W, A, S, D**: Sol analog joystick simülasyonu (çapraz koşu destekli).
   - **Fare Hareketi (Mouse Aim)**: 360° serbest kamera ve nişan alma (ayarlanabilir hassasiyet & ters Y ekseni seçeneği).
   - **Sol Tık (LMB)**: Anında ateş etme (tekli ve seri atış).
   - **Sağ Tık (RMB)**: Dürbün / Scope açma.
   - **Space**: Zıplama.
   - **C**: Eğilme / Çömelme.
   - **Z**: Yere yatma (Prone).
   - **R**: Şarjör değiştirme (Reload).
   - **Shift**: Otomatik depar / Koşma geçişi.
   - **F / G**: Eşya toplama, araç binme, kapı açma / Bomba fırlatma.
   - **1, 2, 3, 4, 5**: Silah ve teçhizat slotları.
   - **Tab**: Çanta / Envanter.
   - **M**: Harita açma / kapama.
   - **ESC**: Otomatik oyundan çıkış / Geri tuşu.

4. **Anti-Ban Dokunma Rastgeleliği (Touch Jittering)**:
   - Oyunların bot veya emülatör algılayıcılarını atlatmak için dokunma koordinatlarına milimetrik doğal rastgele sapmalar ekler.

5. **Çoklu Oyun Profili Yönetimi**:
   - PUBG Mobile, CoD Mobile, Free Fire için ayrı ayrı profiller.
   - Profilleri JSON olarak panoya kopyalama ve içe aktarma imkanı.

---

## 🛠️ Kurulum ve Kullanım Adımları

### 1. Donanım Bağlantısı
- Telefonunuza bir **USB OTG HUB** bağlayın.
- Hub'a klavyenizi ve farenizi takın (Bluetooth klavye/fare de desteklenir).

### 2. Shizuku'yu Başlatma (Sıfır Gecikme İçin)
1. Telefonunuza **Shizuku** uygulamasını yükleyin (Play Store veya GitHub).
2. Ayarlar -> Geliştirici Seçenekleri -> **Kablosuz Hata Ayıklama (Wireless Debugging)** özelliğini açın.
3. Shizuku'yu kablosuz hata ayıklama ile eşleştirip başlatın (`shizuku running` yazısını görün).
4. **GGMausePro** uygulamasını açtığınızda Shizuku iznini onaylayın.

### 3. GGMausePro'yu Çalıştırma
1. **Hizmeti Aç** butonuna basarak Erişilebilirlik iznini verin.
2. **Kaplama İzni Ver** butonuna basarak diğer uygulamaların üzerinde gösterme iznini verin.
3. **EŞLEMEYİ BAŞLAT** butonuna basın.
4. PUBG Mobile oyununu açın.
5. Ekrandaki **GGMausePro baloncuğuna** dokunup **Tuş Düzeni (Görsel Düzenleyici)** modunu açarak butonları oyunun HUD butonlarının tam üstüne sürükleyin.
6. Artık PC hissiyatında gecikmesiz PUBG oynamaya hazırsınız!

---

## 📂 Proje Yapısı

```
ggmausepro/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml             # İzinler ve servis tanımları
│   │   ├── java/com/ggmausepro/mapper/
│   │   │   ├── GGMauseProApp.kt             # Application sınıfı ve bildirim kanalları
│   │   │   ├── activity/
│   │   │   │   ├── MainActivity.kt          # Ana kontrol paneli ve göstergeler
│   │   │   │   ├── KeyMappingActivity.kt    # Tuş atama listesi ve tuş tespit diyaloğu
│   │   │   │   ├── ProfileActivity.kt       # Oyun profilleri ve JSON import/export
│   │   │   │   └── SettingsActivity.kt      # Hassasiyet, Shizuku, Anti-Ban ayarları
│   │   │   ├── adapter/
│   │   │   │   ├── KeyMappingAdapter.kt     # Tuş eşleme RecyclerView adaptörü
│   │   │   │   └── ProfileAdapter.kt        # Profil kartları RecyclerView adaptörü
│   │   │   ├── service/
│   │   │   │   ├── InputMapperService.kt    # Klavye tuş yakalama servisi
│   │   │   │   ├── OverlayService.kt        # Ekran üstü sürüklenebilir tuş baloncukları
│   │   │   │   └── TouchInjector.kt         # Çoklu dokunma & jest simülasyonu
│   │   │   ├── shizuku/
│   │   │   │   ├── ShizukuManager.kt        # Shizuku bağlantı ve yetki yöneticisi
│   │   │   │   └── ShizukuInputInjector.kt  # Sıfır gecikmeli IInputManager injektörü
│   │   │   ├── manager/
│   │   │   │   ├── KeyMappingManager.kt     # Hazır PUBG koordinatları ve profil deposu
│   │   │   │   └── MouseCaptureManager.kt   # Fare bağıl hareketlerini açıya çeviren nişan motoru
│   │   │   └── util/
│   │   │       └── EventBus.kt              # Bileşenler arası hızlı asenkron mesajlaşma
│   │   └── res/                             # Oyun temalı koyu neon XML layout ve stilleri
├── build.gradle.kts                         # AGP 8.2.0 & Kotlin 1.9.20
└── settings.gradle.kts
```

---

## 💻 Android Studio ile Derleme

1. Android Studio'yu açın.
2. `File -> Open` seçeneğiyle `c:\Users\pc\OneDrive\Desktop\ggmausepro` klasörünü seçin.
3. Gradle senkronizasyonunun tamamlanmasını bekleyin.
4. Cihazınızı bağlayıp **Run (Shift + F10)** butonuna basarak APK'yı telefonunuza yükleyin.
