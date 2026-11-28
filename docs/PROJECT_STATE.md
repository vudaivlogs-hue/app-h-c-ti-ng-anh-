# Trạng thái Dự án — Enzii Dict (`com.enziidict.app`)

> **Cập nhật lần cuối**: 2026-11-28 (09:27)  
> **Phiên gần nhất**: [012_nap-tu-dien-fvdp-109k.md](./sessions/012_nap-tu-dien-fvdp-109k.md)  
> **Trạng thái build**: `BUILD SUCCESSFUL` — **45/45** unit & Robolectric tests đạt (`testDebugUnitTest`, 15 suites), kho **113.933 mục từ** FVDP (`dictionary.db` = `32.05 MB`), `assembleDebug` (`98,801,029` bytes = `94.22 MB`) (chưa push lên Git theo quy tắc test trước).

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
│       ├── 007_tu-dong-cap-nhat-github-releases.md     # Nhật ký In-App Update (GitHub Releases) & CI signed release
│       ├── 008_thong-bao-nhac-hoc-workmanager.md       # Nhật ký thông báo WorkManager, POST_NOTIFICATIONS & Deep Link
│       ├── 009_theo-doi-tien-do-duolingo-va-tong-ket.md # Nhật ký Room v3, Streak Freeze, Daily Quests & Tổng kết học tập
│       ├── 010_tich-hop-logo-va-linh-vat-chu-dui.md     # Nhật ký WebP chú Dúi (85.6 KB), Adaptive Icon & DuiMascotView
│       ├── 011_tong-ket-phien-thong-bao-tien-do-va-linh-vat-dui.md # Tổng hợp toàn bộ đợt nâng cấp 008 -> 010 & lỗi đã sửa
│       └── 012_nap-tu-dien-fvdp-109k.md                 # Nhật ký nạp bộ FVDP 113.933 từ, DictionaryPackUpgrader & benchmark
├── scripts/
│   ├── build_dictionary_db.py           # Parse FVDP 109K / Wiktionary JSONL / seed -> assets/dictionary.db
│   ├── export_keystore_base64.ps1       # Xuất enzii-release.jks sang Base64 vào Clipboard cho GitHub Secrets
│   └── optimize_mascot_assets.py        # Nén logo/5 sắc thái chú Dúi sang WebP + Adaptive Icon
├── tools/dict-builder/
│   ├── seed_dictionary.json             # 40 mục từ chi tiết (IPA UK/US, ví dụ, đồng nghĩa, collocations)
│   └── seed_articles.json               # 5 bài đọc song ngữ A2 – C1
├── start-emulator.bat                   # Mở nhanh máy ảo Android 14 (Enzii_Pixel_API34)
└── app/src/
    ├── main/
    │   ├── AndroidManifest.xml          # MainActivity + QuickLookupActivity (ACTION_PROCESS_TEXT)
    │   ├── assets/dictionary.db         # Pre-packaged SQLite DB (113.933 từ + 5 bài đọc + 7 thẻ mẫu, 32.05 MB)
    │   ├── res/                         # drawable-nodpi (7 WebP chú Dúi 85.6 KB) & Adaptive Icon (#0D9488 / #F59E0B)
    │   └── java/com/enziidict/app/
    │       ├── MainActivity.kt          # EnziiSplashOverlay + DictionaryPackUpgrader + 5 tab đáy + Summary/Reading/Shadowing/Settings
    │       ├── ui/
    │       │   ├── theme/               # Color.kt, Type.kt, Theme.kt (Light & Dark Mode)
    │       │   └── components/          # BentoCard, DuiMascotView (MascotMood/MascotLevel), PosPill, CefrPill, IpaAudioPill, EnziiHaptics
    │       ├── data/local/              # EnziiDatabase (v3), EnziiDatabaseMigrations (1->2->3), DatabaseModule, entity/ & dao/
    │       ├── core/
    │       │   ├── model/               # Dictionary, SRS, Speaking, Grammar, Reading models
    │       │   ├── database/            # DictionaryRepository, DictionaryPackUpgrader & LearningProgressRepository
    │       │   ├── audio/               # TtsManager, OnlineAudioPlayer, LiveAudioManager, ShadowingAudioController, ShadowingSpeechRecognizer, PronunciationScorer
    │       │   ├── network/             # SecureApiKeyStore, SettingsDataStore, OfflineMlKitTranslator, OnlineDictionaryRepository, AppUpdateManager, GeminiGrammarService, GeminiLiveClient, SpeakingEvaluationService, GeminiShadowingCoachService
    │       │   └── notification/        # EnziiNotificationHelper, StudyNotificationScheduler, DailyStudyReminderWorker, DailyWordNotificationWorker
    │       └── feature/
    │           ├── home/                # HomeScreen (Tooltip chú Dúi, Streak Hero + Dúi HAPPY/SLEEPY, 3 Daily Quests, SRS due)
    │           ├── progress/            # LearningSummaryScreen, LearningSummaryViewModel & SessionRecapComponents (Recap Flashcard/Speaking)
    │           ├── dictionary/          # DictionaryScreen (113.933 từ offline + Online, gợi ý chú Dúi INQUISITIVE)
    │           ├── quicklookup/         # QuickLookupActivity (popup nổi ACTION_PROCESS_TEXT)
    │           ├── srs/                 # FlashcardScreen (lật 3D SM-2, quản lý thư mục, Recap sau phiên)
    │           ├── reading/             # ReadingScreen (5 bài A2–C1, chạm từ tra BottomSheet, Shadowing từng đoạn)
    │           ├── translate/           # TranslateScreen (Tầng 1 ML Kit Offline + Tầng 2 Gemini Grammar Breakdown)
    │           ├── speaking/            # SpeakingScreen (Dúi theo cấp độ BEGINNER/INTERMEDIATE/ADVANCED + FOCUSED khi gọi, Live PCM + AEC) & SpeakingHistoryScreen
    │           ├── shadowing/           # ShadowingScreen (Dúi FOCUSED/HAPPY/INQUISITIVE, chunks, 0.75x–1.15x, loop, ghi âm đối chiếu, AI stress/linking)
    │           └── settings/            # SettingsScreen (BYOK API Key AES-256, model Gemini, Theme, gói offline ML Kit, thông báo WorkManager)
    └── test/java/com/enziidict/app/     # 15 bộ test (45 test cases) — 100% PASS
```

---

## 3. Trạng thái Các Module

### Đã hoàn thành & Kiểm thử
- [x] **Bộ Từ điển Lớn FVDP 109K (`113.933` mục từ, `DictionaryPackUpgrader`)**: Tải & chuẩn hóa `anhviet109K.txt`, đóng gói `dictionary.db` (`32.05 MB`), ưu tiên rank từ CEFR phổ biến, tối ưu truy vấn 3 tầng (`11–29ms`), tự động nâng cấp cho máy cài bản cũ mà bảo toàn `saved_words`/`user_streak_state`.
- [x] **Bộ Logo, Adaptive Icon & Linh vật Chú Dúi (`res/drawable-nodpi`, `DuiMascotView`)**: Nén 7 tệp WebP tổng `85.6 KB`, Adaptive Icon an toàn không cắt viền (`#0D9488` + `#F59E0B`), `EnziiSplashOverlay`, `MascotMood` (`INQUISITIVE`, `FOCUSED`, `HAPPY`, `SLEEPY`, `SMART`), `MascotLevel` (`BASIC`, `INTERMEDIATE`, `ADVANCED`) và tooltip ngữ cảnh tích hợp tại Splash, Home, Dictionary, Speaking, Shadowing, Recap & Learning Summary.
- [x] **Tầng dữ liệu Room + Pre-packaged SQLite (`data/local`, `scripts/build_dictionary_db.py`)**: Schema v3 (`113.933` từ, `dictionary_fts` FTS4 + 4 trigger, `word_folders`, `daily_study_logs`, `user_streak_state`, `MIGRATION_1_2`, `MIGRATION_2_3`).
- [x] **Trang chủ Bento Grid & Hệ thống UI/UX (`feature/home`, `ui/theme`, `ui/components`)**: Light/Dark Mode, Typography rõ cấp bậc, `PosPill`/`CefrPill`/`IpaAudioPill`, Haptic Feedback, Streak Hero + lịch 7 ngày + Freeze + 3 Nhiệm vụ hằng ngày.
- [x] **Theo dõi Tiến độ, Post-Session Recap & Tổng kết (`LearningProgressRepository`, `feature/progress`)**: Tự động ghi XP/từ/phút từ SRS, Shadowing, Reading và AI Speaking; bảo vệ chuỗi bằng Streak Freeze; màn chúc mừng sau phiên Flashcard & Speaking; `LearningSummaryScreen` (biểu đồ cột 7/30 ngày, số từ thành thạo Mastered, điểm mạnh nhất, từ vựng tiêu biểu tuần).
- [x] **Tra cứu Từ điển Hybrid (Offline + Online) (`feature/dictionary`, `OnlineDictionaryRepository`) & Tra nhanh (`feature/quicklookup`)**: Tìm kiếm 3 tầng offline (113.933 từ, lemmatization) kết hợp tra cứu trực tuyến qua Free Dictionary API + ML Kit / Gemini AI, tự động lưu cache vào Room để tra lại offline, hỗ trợ nút **"Mở rộng Online"** (Anh–Anh, audio bản xứ `OnlineAudioPlayer`, từ trái nghĩa) và gắn nhãn nguồn `Offline`/`Online`.
- [x] **Sổ tay Từ vựng, Thư mục `WordFolder` & Flashcard SM-2 (`feature/srs`, `ui/components/SaveToFolderBottomSheet`)**: Room v2 (`word_folders` + `saved_words.folder_id` + `MIGRATION_1_2` an toàn), `SaveToFolderBottomSheet` chọn/tạo nhanh thư mục từ Chi tiết từ & `QuickLookupActivity`, hiển thị trạng thái `Đã lưu • <Thư mục>`, dải thẻ thư mục lọc trực tiếp và ôn Flashcard 3D SM-2 riêng từng chủ đề.
- [x] **Đọc Song ngữ (`feature/reading`)**: 5 bài đọc A2–C1, ẩn/hiện bản dịch toàn bài & từng đoạn, chạm bất kỳ từ nào mở `ModalBottomSheet` tra từ + nghe UK/US + lưu SRS.
- [x] **Dịch Hybrid & Cài đặt (`feature/translate`, `feature/settings`)**: ML Kit dịch offline hai chiều + quản lý tải/xóa gói ngôn ngữ, Gemini API phân tích ngữ pháp/idioms/sắc thái từ, mã hóa API Key AES-256, xử lý lỗi thiếu key/mất mạng.
- [x] **AI Speaking Partner & Lịch sử (`feature/speaking`)**: `LiveAudioManager` (PCM 16kHz + `AcousticEchoCanceler` + `AudioTrack` 24kHz), Gemini Live WebSocket, 5 kịch bản & 3 trình độ, sóng âm động, phụ đề song ngữ, lưu `speaking_sessions`/`speaking_messages`, đánh giá AI sau buổi nói & lưu từ mới vào SRS.
- [x] **Luyện Shadowing + AI Coach (`feature/shadowing`)**: Tách cụm nhịp (chunks), mở từ Từ điển/Đọc/4 chủ đề mẫu, UK/US `0.75x–1.15x`, Loop, tự động thu sau khi nghe (3-2-1), `MediaRecorder` `.m4a` + đối chiếu `Giọng mẫu ↔ Giọng của bạn`, `SpeechRecognizer` offline + highlight 3 màu từng từ, Gemini phân tích trọng âm/nối âm, lưu lịch sử tiến bộ & từ hay vấp.
- [x] **Máy ảo Android 14 (`Enzii_Pixel_API34`)**: Đã cài Emulator (WHPX), AVD Pixel 6 (2GB RAM) và tạo [start-emulator.bat](../start-emulator.bat).
- [x] **Cấu hình Release APK, Keystore, R8/ProGuard & ABI `arm64-v8a`**: `.gitignore` bảo mật secret, `keystore.properties.example`, `app/build.gradle.kts` (`isMinifyEnabled = true`, `isShrinkResources = true`, `abiFilters = ["arm64-v8a"]`), `app/proguard-rules.pro` cho Room/Hilt/ML Kit/Kotlinx Serialization/Tink; `assembleRelease` thành công.
- [x] **Tự động Cập nhật Trong App (GitHub Releases) & CI/CD Signed Release**: `AppUpdateManager` + `FileProvider` + thẻ cập nhật trong `SettingsScreen` + badge `MainActivity` + workflow `.github/workflows/release-apk.yml` tự tăng `versionCode` và ký bằng `enzii-release.jks` qua GitHub Secrets.
- [x] **Thông báo Học tập Thông minh (`core/notification`, `WorkManager`)**: Xin quyền `POST_NOTIFICATIONS` trên Android 13+, nhắc học giữ Streak (`DailyStudyReminderWorker` với `TimePickerDialog`), thông báo Từ vựng / Mẫu câu mỗi ngày (`DailyWordNotificationWorker` 1–2 lần/ngày, `BigTextStyle`) kèm nút hành động & Deep Link mở thẳng Từ điển/Flashcard/Shadowing.

### Bước Tiếp Theo Cần Làm Khi Mở Lại Dự Án (Thứ tự Ưu tiên)
1. **Kiểm thử thủ công trên máy ảo `Enzii_Pixel_API34` (`emulator-5554`)**:
   - Đã cài bản Debug mới nhất (`98,801,029` bytes); kiểm tra Adaptive Icon, Splash chú Dúi, kho `113.933` từ (bao gồm tự động nâng cấp dữ liệu giữ nguyên Sổ tay), **Home / Daily Quests / Streak / Tổng kết học tập**, và **Cài đặt → Nhắc nhở & Từ vựng mỗi ngày**.
2. **Đẩy mã nguồn lên GitHub & phát hành bản cập nhật OTA cho điện thoại thật**:
   - Chỉ thực hiện sau khi bạn xác nhận đã test ổn trên máy ảo: `git commit` toàn bộ thay đổi local (từ phiên 008 → 012), `git push origin main` và tạo tag Release (`v*`) để GitHub Actions build + ký APK cho điện thoại thật tự tải về qua In-App Update.
3. **Mở rộng Lịch sử tra cứu & Lịch sử dịch**:
   - Lưu lịch sử tra từ và lịch sử dịch xuống Room để xem lại offline nhanh từ tab Từ điển / Dịch.

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
