# Trạng thái Dự án — Enzii Dict (`com.enziidict.app`)

> **Cập nhật lần cuối**: 2026-11-28 (00:07)  
> **Phiên gần nhất**: [007_tu-dong-cap-nhat-github-releases.md](./sessions/007_tu-dong-cap-nhat-github-releases.md)  
> **Trạng thái build**: `BUILD SUCCESSFUL` — **35/35** unit & Robolectric tests đạt (`testDebugUnitTest`), `assembleDebug` (`85,594,940` bytes) & `assembleRelease` R8 `arm64-v8a` (`20,063,830` bytes); đã cấu hình In-App Update (GitHub Releases) + CI workflow ký APK tự động.

---

## 1. Kiến trúc & Tech Stack đã chốt

| Thành phần | Lựa chọn đã chốt | Ghi chú |
| :--- | :--- | :--- |
| **Tên ứng dụng / Package ID** | **Enzii Dict** / `com.enziidict.app` | `minSdk = 26`, `compileSdk = 34`, `targetSdk = 34` |
| **Ngôn ngữ & UI** | Kotlin `2.0.21` + Jetpack Compose (Material 3 BOM `2024.10.01`) | Light/Dark Mode chuẩn hóa, Bento Grid Home, Haptic Feedback |
| **Tổ chức mã nguồn** | Single-module (`:app`) chia package rõ theo `core/`, `data/local/`, `feature/`, `ui/` | Build nhẹ, nhanh, dễ quản lý ở giai đoạn đầu |
| **Database Offline** | Room `2.6.1` + SQLite Pre-packaged (`createFromAsset("dictionary.db")`) + **FTS4** (`dictionary_fts`) | Index B-Tree `idx_dict_word_prefix` (< 15ms) + 4 trigger đồng bộ FTS4 |
| **Dependency Injection** | Hilt `2.52` + KSP `2.0.21-1.0.28` | Export Room schema tự động tại `app/schemas/` |
| **Dịch thuật & Ngữ pháp** | **Hybrid**: Google ML Kit Translate (`17.0.3`) offline EN ↔ VI + **Gemini API (BYOK)** | API Key mã hóa AES-256 bằng `EncryptedSharedPreferences` (`security-crypto`) |
| **AI Speaking Partner** | **Gemini Live API** (WebSocket `BidiGenerateContent` qua OkHttp `4.12.0`, PCM 16kHz/24kHz + `AcousticEchoCanceler`) + Fallback STT/TTS | Dùng chung API Key BYOK, tự động chấm điểm & gợi ý sửa câu sau buổi nói |
| **Shadowing & Âm thanh** | `TextToSpeech` (UK/US, 0.75x/1.0x/1.15x, Loop) + `MediaRecorder` (`.m4a` AAC) + `MediaPlayer` + `SpeechRecognizer` + Gemini Multimodal Audio | Hỗ trợ thuần offline và tăng cường AI phân tích trọng âm/nối âm |
| **Cấu hình & Lưu trữ nhẹ** | Preferences DataStore (`1.1.1`) + `EncryptedSharedPreferences` + `SharedPreferences` JSON | Lưu theme, streak, thư mục tự tạo, model Gemini và log luyện Shadowing |
| **Build Toolchain** | AGP `8.6.1`, Gradle Wrapper `8.9`, OpenJDK 17, Android SDK 34 | Kiểm thử JVM + Robolectric `4.13` (API 34) |

---

## 2. Cấu trúc Thư mục Hiện tại

```text
c:\O_D\app học tiếng anh\
├── AGENTS.md                            # Quy tắc workspace tự động nạp cho Antigravity
├── README.md                            # Hướng dẫn tổng quan, build & chạy máy ảo
├── keystore.properties.example          # Mẫu cấu hình ký số Release (copy sang keystore.properties)
├── .github/workflows/release-apk.yml    # CI tự động build, ký & phát hành APK Release lên GitHub Releases
├── docs/
│   ├── PROJECT_STATE.md                           # Tài liệu trạng thái hiện tại (file này)
│   └── sessions/
│       ├── 001_khoi-tao-va-ke-hoach.md                 # Nhật ký kiến trúc & kế hoạch khởi tạo
│       ├── 002_trien-khai-mvp-va-luu-ngu-canh.md       # Nhật ký triển khai MVP, lỗi đã sửa & lưu ngữ cảnh
│       ├── 003_thu-muc-so-tay-wordfolder.md            # Nhật ký nâng cấp WordFolder, Migration v1->v2 & SaveToFolderBottomSheet
│       ├── 004_tra-tu-dien-online-va-cache-offline.md  # Nhật ký tra cứu Online (Free Dictionary + ML Kit/Gemini) & cache Room
│       ├── 005_cau-hinh-release-apk-r8-keystore.md     # Nhật ký cấu hình Release R8/ProGuard, Keystore & arm64-v8a
│       ├── 006_hoan-thien-release-va-khoi-tao-git.md   # Nhật ký keystore.properties, .vscode Wrapper & Git init sạch
│       └── 007_tu-dong-cap-nhat-github-releases.md     # Nhật ký In-App Update (GitHub Releases) & CI signed release
├── scripts/
│   ├── build_dictionary_db.py           # Parse FVDP / Wiktionary JSONL / seed -> assets/dictionary.db
│   └── export_keystore_base64.ps1       # Xuất enzii-release.jks sang Base64 vào Clipboard cho GitHub Secrets
├── tools/dict-builder/
│   ├── seed_dictionary.json             # 40 mục từ chi tiết (IPA UK/US, ví dụ, đồng nghĩa, collocations)
│   └── seed_articles.json               # 5 bài đọc song ngữ A2 – C1
├── start-emulator.bat                   # Mở nhanh máy ảo Android 14 (Enzii_Pixel_API34)
└── app/src/
    ├── main/
    │   ├── AndroidManifest.xml          # MainActivity + QuickLookupActivity (ACTION_PROCESS_TEXT)
    │   ├── assets/dictionary.db         # Pre-packaged SQLite DB (1.046 từ + 5 bài đọc + 7 thẻ mẫu)
    │   └── java/com/enziidict/app/
    │       ├── MainActivity.kt          # 5 tab đáy + AnimatedContent + Light/Dark toggle + Reading/Shadowing/Settings
    │       ├── ui/
    │       │   ├── theme/               # Color.kt, Type.kt, Theme.kt (Light & Dark Mode)
    │       │   └── components/          # BentoCard, PosPill, CefrPill, IpaAudioPill, EnziiHaptics, StateCards
    │       ├── data/local/              # EnziiDatabase, DatabaseModule, entity/ & dao/
    │       ├── core/
    │       │   ├── model/               # Dictionary, SRS, Speaking, Grammar, Reading models
    │       │   ├── database/            # DictionaryRepository (3-tier search, lemmatization, cache online, save SRS)
    │       │   ├── audio/               # TtsManager, OnlineAudioPlayer, LiveAudioManager, ShadowingAudioController, ShadowingSpeechRecognizer, PronunciationScorer
    │       │   └── network/             # SecureApiKeyStore, SettingsDataStore, OfflineMlKitTranslator, OnlineDictionaryRepository, AppUpdateManager, GeminiGrammarService, GeminiLiveClient, SpeakingEvaluationService, GeminiShadowingCoachService
    │       └── feature/
    │           ├── home/                # HomeScreen (Bento Grid: Streak, SRS due, AI Speaking shortcuts, Word of the Day)
    │           ├── dictionary/          # DictionaryScreen (tra offline <15ms + tra/mở rộng Online, audio bản xứ, nút Shadow)
    │           ├── quicklookup/         # QuickLookupActivity (popup nổi ACTION_PROCESS_TEXT)
    │           ├── srs/                 # FlashcardScreen (lật 3D SM-2, quản lý 3 thư mục chuẩn + thư mục tự tạo)
    │           ├── reading/             # ReadingScreen (5 bài A2–C1, chạm từ tra BottomSheet, Shadowing từng đoạn)
    │           ├── translate/           # TranslateScreen (Tầng 1 ML Kit Offline + Tầng 2 Gemini Grammar Breakdown)
    │           ├── speaking/            # SpeakingScreen (5 kịch bản, 3 trình độ, Live PCM + AEC) & SpeakingHistoryScreen
    │           ├── shadowing/           # ShadowingScreen (chunks, 0.75x–1.15x, loop, đếm ngược, ghi âm đối chiếu, AI stress/linking, lịch sử)
    │           └── settings/            # SettingsScreen (BYOK API Key AES-256, model Gemini, Theme, gói offline ML Kit)
    └── test/java/com/enziidict/app/     # 11 bộ test (35 test cases) — 100% PASS
```

---

## 3. Trạng thái Các Module

### Đã hoàn thành & Kiểm thử
- [x] **Tầng dữ liệu Room + Pre-packaged SQLite (`data/local`, `scripts/build_dictionary_db.py`)**: 1.046 từ, index `idx_dict_word_prefix` (< 15ms), FTS4 + 4 trigger đồng bộ, tra ngược tiếng Việt `searchByMeaning`.
- [x] **Trang chủ Bento Grid & Hệ thống UI/UX (`feature/home`, `ui/theme`, `ui/components`)**: Light/Dark Mode, Typography rõ cấp bậc, `PosPill`/`CefrPill`/`IpaAudioPill`, Haptic Feedback, theo dõi Streak học tập.
- [x] **Tra cứu Từ điển Hybrid (Offline + Online) (`feature/dictionary`, `OnlineDictionaryRepository`) & Tra nhanh (`feature/quicklookup`)**: Tìm kiếm 3 tầng offline (< 15ms, lemmatization) kết hợp tra cứu trực tuyến qua Free Dictionary API + ML Kit / Gemini AI, tự động lưu cache vào Room để tra lại offline, hỗ trợ nút **"Mở rộng Online"** (Anh–Anh, audio bản xứ `OnlineAudioPlayer`, từ trái nghĩa) và gắn nhãn nguồn `Offline`/`Online`.
- [x] **Sổ tay Từ vựng, Thư mục `WordFolder` & Flashcard SM-2 (`feature/srs`, `ui/components/SaveToFolderBottomSheet`)**: Room v2 (`word_folders` + `saved_words.folder_id` + `MIGRATION_1_2` an toàn), `SaveToFolderBottomSheet` chọn/tạo nhanh thư mục từ Chi tiết từ & `QuickLookupActivity`, hiển thị trạng thái `Đã lưu • <Thư mục>`, dải thẻ thư mục lọc trực tiếp và ôn Flashcard 3D SM-2 riêng từng chủ đề.
- [x] **Đọc Song ngữ (`feature/reading`)**: 5 bài đọc A2–C1, ẩn/hiện bản dịch toàn bài & từng đoạn, chạm bất kỳ từ nào mở `ModalBottomSheet` tra từ + nghe UK/US + lưu SRS.
- [x] **Dịch Hybrid & Cài đặt (`feature/translate`, `feature/settings`)**: ML Kit dịch offline hai chiều + quản lý tải/xóa gói ngôn ngữ, Gemini API phân tích ngữ pháp/idioms/sắc thái từ, mã hóa API Key AES-256, xử lý lỗi thiếu key/mất mạng.
- [x] **AI Speaking Partner & Lịch sử (`feature/speaking`)**: `LiveAudioManager` (PCM 16kHz + `AcousticEchoCanceler` + `AudioTrack` 24kHz), Gemini Live WebSocket, 5 kịch bản & 3 trình độ, sóng âm động, phụ đề song ngữ, lưu `speaking_sessions`/`speaking_messages`, đánh giá AI sau buổi nói & lưu từ mới vào SRS.
- [x] **Luyện Shadowing + AI Coach (`feature/shadowing`)**: Tách cụm nhịp (chunks), mở từ Từ điển/Đọc/4 chủ đề mẫu, UK/US `0.75x–1.15x`, Loop, tự động thu sau khi nghe (3-2-1), `MediaRecorder` `.m4a` + đối chiếu `Giọng mẫu ↔ Giọng của bạn`, `SpeechRecognizer` offline + highlight 3 màu từng từ, Gemini phân tích trọng âm/nối âm, lưu lịch sử tiến bộ & từ hay vấp.
- [x] **Máy ảo Android 14 (`Enzii_Pixel_API34`)**: Đã cài Emulator (WHPX), AVD Pixel 6 (2GB RAM) và tạo [start-emulator.bat](../start-emulator.bat).
- [x] **Cấu hình Release APK, Keystore, R8/ProGuard & ABI `arm64-v8a`**: `.gitignore` bảo mật secret, `keystore.properties.example`, `app/build.gradle.kts` (`isMinifyEnabled = true`, `isShrinkResources = true`, `abiFilters = ["arm64-v8a"]`), `app/proguard-rules.pro` cho Room/Hilt/ML Kit/Kotlinx Serialization/Tink; `assembleRelease` thành công (~20.1 MB).
- [x] **Tự động Cập nhật Trong App (GitHub Releases) & CI/CD Signed Release**: `AppUpdateManager` + `FileProvider` + thẻ cập nhật trong `SettingsScreen` + badge `MainActivity` + workflow `.github/workflows/release-apk.yml` tự tăng `versionCode` và ký bằng `enzii-release.jks` qua GitHub Secrets.

### Bước Tiếp Theo Cần Làm Khi Mở Lại Dự Án (Thứ tự Ưu tiên)
1. **Kiểm thử thủ công thực tế trên máy ảo & điện thoại thật**:
   - Chạy `start-emulator.bat` để mở cửa sổ máy ảo `Enzii_Pixel_API34` (đợi vào màn hình chính Android), sau đó cài APK Debug mới nhất bằng:  
     `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "$env:TEMP\EnziiDictBuild\app\outputs\apk\debug\app-debug.apk"`
   - Kiểm tra lần lượt: Tra từ offline + `ACTION_PROCESS_TEXT`, Flashcard SM-2 + thư mục tự tạo, Đọc song ngữ chạm từ, Cài đặt tải gói ML Kit + nhập Gemini API Key, AI Speaking (mic/loa/phụ đề/đánh giá sau phiên) và Shadowing (ghi âm/đối chiếu/AI phân tích trọng âm).
2. **Nạp bộ từ điển lớn thực tế (100k+ mục từ)**:
   - Chuẩn bị dữ liệu nguồn FVDP (`.dict`) hoặc Wiktionary JSONL, chạy `scripts/build_dictionary_db.py`, tối ưu kích thước `app/src/main/assets/dictionary.db` và đo lại tốc độ truy vấn prefix/FTS4.
3. **Nhắc học SRS hằng ngày bằng `WorkManager`**:
   - Triển khai `DailyReminderWorker` kiểm tra số thẻ đến hạn (`next_review_at <= now`), xin quyền `POST_NOTIFICATIONS` (Android 13+) và thêm công tắc bật/tắt giờ nhắc học trong `SettingsScreen`.
4. **Mở rộng Lịch sử tra cứu & Lịch sử dịch**:
   - Lưu lịch sử tra từ và lịch sử dịch xuống Room để xem lại offline nhanh từ tab Từ điển / Dịch.
5. **Thêm 4 GitHub Secrets & phát hành bản `v0.2.0` đầu tiên cho In-App Update**:
   - Chạy `.\scripts\export_keystore_base64.ps1`, dán 4 secret (`ENZII_KEYSTORE_BASE64`, `ENZII_KEYSTORE_PASSWORD`, `ENZII_KEY_ALIAS`, `ENZII_KEY_PASSWORD`) lên GitHub Repository Settings → Secrets and variables → Actions.
   - Đẩy tag đầu tiên (`git tag v0.2.0; git push origin v0.2.0`) để GitHub Actions tự build & phát hành bản Release đầu tiên.
   - *(Tùy chọn build local)* Điền mật khẩu thật vào `keystore.properties` và chạy `.\gradlew.bat assembleRelease`.

---

## 4. Quy ước Code & Lưu ý Kỹ thuật Quan trọng

1. **Đường dẫn Windows có dấu tiếng Việt (`c:\O_D\app học tiếng anh`)**:
   - `gradle.properties` bắt buộc giữ `android.overridePathCheck=true` và các cờ TLS/IPv4: `-Djdk.tls.client.protocols=TLSv1.2 -Dhttps.protocols=TLSv1.2 -Djava.net.preferIPv4Stack=true`.
   - `gradlew.bat` dùng đường dẫn tương đối `gradle\wrapper\gradle-wrapper.jar` khi chạy tại thư mục gốc để tránh lỗi `ClassNotFoundException` do CP437/CP1252.
   - Root `build.gradle.kts` tự động chuyển `layout.buildDirectory` sang `%TEMP%\EnziiDictBuild\<project>` khi đường dẫn gốc chứa ký tự non-ASCII (nhằm tránh lỗi `@argfile` CP1252 của JVM test worker). Khi build từ đường dẫn hiện tại, APK nằm tại:  
     `C:\Users\ADMIN\AppData\Local\Temp\EnziiDictBuild\app\outputs\apk\debug\app-debug.apk`.
2. **Tương thích Android SDK 34**:
   - Giữ `compileSdk = 34`, `targetSdk = 34`, `core-ktx = 1.13.1`, `activity-compose = 1.9.3` (tránh nâng bản yêu cầu `compileSdk = 35` trừ khi cài thêm `platforms;android-35`).
3. **Đồng bộ Room Schema & Pre-packaged `dictionary.db`**:
   - Không đặt `@ColumnInfo(defaultValue = ...)` và không đặt SQL `DEFAULT` trong `build_dictionary_db.py` để tránh lỗi mismatch khi Room validate `createFromAsset("dictionary.db")`.
   - Mọi thay đổi schema bảng trong `EnziiDatabase` phải cập nhật đồng thời `scripts/build_dictionary_db.py`, chạy lại script và xác minh khớp với `app/schemas/.../1.json`.
   - Với FTS4 trên SQLite Android: luôn giữ tầng 3 `searchByMeaning` (`LIKE`) cho tra ngược tiếng Việt có dấu mở rộng (`ế`, `ộ`, `ư`, `đ`, ...).
4. **An toàn Vòng đời Phần cứng (Audio / Mic / WebSocket)**:
   - Mọi màn hình dùng micro/loa/WebSocket (`SpeakingScreen`, `ShadowingScreen`) phải có `DisposableEffect(lifecycleOwner)` giải phóng tài nguyên ở `ON_STOP` & `onDispose`, kết hợp dọn dẹp trong `ViewModel.onCleared()`.
5. **Offline-First & Xử lý lỗi BYOK**:
   - Mọi tính năng gọi Gemini API phải đọc key từ `SecureApiKeyStore` (không lưu plain text ở DataStore) và luôn có đường lui (fallback) rõ ràng khi chưa có API key hoặc mất mạng.
