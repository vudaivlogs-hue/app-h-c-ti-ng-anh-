# Phiên 002 — Triển khai Toàn bộ Module MVP, AI Shadowing/Speaking & Hệ thống Lưu Ngữ cảnh

- **Ngày**: 2026-11-26
- **Trạng thái cuối phiên**: `BUILD SUCCESSFUL` — **25/25** unit & Robolectric integration tests đạt, APK Debug sẵn sàng cài kiểm thử (`%TEMP%\EnziiDictBuild\app\outputs\apk\debug\app-debug.apk`, ~84.9 MB).

---

## 1. Những gì đã hoàn thành

1. **Tầng Dữ liệu Cốt lõi & Từ điển Offline (`com.enziidict.app.data.local` + `scripts/`)**:
   - Triển khai Room Database (`EnziiDatabase`) với các bảng `dictionary_entries`, bảng ảo FTS4 `dictionary_fts` (4 trigger đồng bộ), `saved_words` (các trường SM-2 + `folder_name`), `speaking_sessions`, `speaking_messages`, và `bilingual_articles`.
   - Viết script Python `scripts/build_dictionary_db.py` hỗ trợ nạp nguồn **FVDP (`.dict`)**, **Wiktionary JSONL** và dữ liệu mẫu tích hợp, xuất `app/src/main/assets/dictionary.db` chứa **1.046 từ**, **5 bài đọc song ngữ (A2–C1)** và **7 thẻ mẫu**.
   - Xác minh bằng `EXPLAIN QUERY PLAN` dùng index B-Tree `idx_dict_word_prefix`, tìm kiếm 3 tầng (Prefix → FTS4 → tiếng Việt `searchByMeaning`) và lemmatization cơ bản.

2. **Sổ tay Từ vựng (SRS SM-2) & Đọc Song ngữ (`feature/srs`, `feature/reading`)**:
   - Quản lý 3 thư mục mặc định (*Mặc định*, *Oxford 3000*, *Từ lưu từ hội thoại*) + thư mục tự tạo.
   - Màn hình `FlashcardScreen` lật thẻ 3D mượt theo trục Y, 4 nút đánh giá SM-2 (`Again`, `Hard`, `Good`, `Easy`) kèm dự báo khoảng thời gian ôn tiếp theo (`next_review_at`).
   - Màn hình `ReadingScreen` với 5 bài đọc A2–C1, bộ lọc CEFR, ẩn/hiện bản dịch toàn bài & từng đoạn, chạm bất kỳ từ nào để mở `ModalBottomSheet` tra từ tại chỗ từ Room, phát âm UK/US và lưu ngay vào Sổ tay.

3. **Dịch thuật Hybrid & Cài đặt BYOK (`feature/translate`, `feature/settings`)**:
   - **Tầng 1 (Offline)**: Google ML Kit Translation hai chiều Anh ↔ Việt + tải/xóa gói ngôn ngữ offline trong `SettingsScreen`.
   - **Tầng 2 (AI Deep Analysis)**: Gọi Gemini API phân tích cấu trúc câu, điểm ngữ pháp, idioms/sắc thái từ và gợi ý diễn đạt tự nhiên hơn.
   - Mã hóa Gemini API Key cá nhân (BYOK) bằng `EncryptedSharedPreferences` (`SecureApiKeyStore`), chọn model Gemini mặc định, xử lý lỗi rõ ràng khi chưa có API Key hoặc mất mạng.

4. **AI Speaking Partner & Lịch sử Hội thoại (`feature/speaking`)**:
   - Xây dựng `LiveAudioManager` thu âm PCM 16kHz (`AudioRecord` có bật `AcousticEchoCanceler` & `NoiseSuppressor`) và phát streaming PCM 24kHz (`AudioTrack`) hỗ trợ ngắt lời (barge-in).
   - Kết nối Gemini Live API qua WebSocket (`GeminiLiveClient`), hỗ trợ 5 kịch bản (Giao tiếp hằng ngày, Quán cà phê, Phỏng vấn xin việc, Du lịch, Tự do), 3 trình độ, sóng âm theo giọng nói, Tắt/Mở mic, Ẩn/Hiện phụ đề song ngữ.
   - Lưu lịch sử vào `speaking_sessions` / `speaking_messages`; khi kết thúc phiên, gọi `SpeakingEvaluationService` để nhận xét tổng quan, gợi ý sửa câu tự nhiên hơn và lưu từ mới vào thư mục *Từ lưu từ hội thoại*.

5. **Nâng cấp UI/UX Bento Grid & Hệ thống Thiết kế (`feature/home`, `ui/theme`, `ui/components`)**:
   - Thiết kế lại `HomeScreen` theo bố cục **Bento Grid** bo góc lớn (20dp–24dp): hiển thị nhanh Streak ngày học, số thẻ SRS đến hạn hôm nay, lối tắt "Nói với AI" theo chủ đề, Từ vựng nổi bật trong ngày và lối tắt Đọc/Dịch/Shadowing.
   - Chuẩn hóa Light/Dark Mode dịu mắt, phân cấp typography, chip `PosPill`/`CefrPill`/`IpaAudioPill`, animation chuyển tab và Haptic Feedback.

6. **Luyện Shadowing (Offline + AI Coach) (`feature/shadowing`)**:
   - Mở luyện từ câu ví dụ/collocation ở màn hình Từ điển, từng đoạn ở màn hình Đọc song ngữ, hoặc 16 câu theo 4 chủ đề (Daily, Idioms, Phrasal Verbs, Interview).
   - Tách cụm nhịp (`ShadowingChunker`), phát giọng mẫu UK/US với tốc độ `0.75x / 1.0x / 1.15x`, Loop, đếm ngược 3-2-1 tự động thu âm.
   - Ghi âm `.m4a` AAC bằng `MediaRecorder` và phát đối chiếu `Mẫu ↔ Giọng của bạn`; chấm khớp phát âm offline bằng `SpeechRecognizer` + `PronunciationScorer` (highlight 3 màu: đúng / lệch / nuốt từ).
   - Nâng cấp AI với `GeminiShadowingCoachService`: phân tích điểm trôi chảy (0–100), trọng âm (stress), nối âm/giảm âm (linking/reduction) và lưu tiến bộ (`ShadowingHistoryStore`: số lần thử, điểm gần nhất/cao nhất, từ hay vấp).

7. **Máy ảo Android 14 & Hệ thống Lưu Ngữ cảnh Dự án**:
   - Cài Android Emulator (tăng tốc phần cứng WHPX), tạo AVD `Enzii_Pixel_API34` và file `start-emulator.bat`; kiểm chứng chạy app trên máy ảo không lỗi runtime.
   - Thiết lập bộ tài liệu ngữ cảnh dài hạn: `docs/PROJECT_STATE.md`, `docs/sessions/001_khoi-tao-va-ke-hoach.md`, `AGENTS.md` (rule tự động nạp bởi Antigravity) và `README.md`.

---

## 2. Các File Chính Đã Tạo / Sửa

- **Build, Scripts & Tài liệu**:
  - `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew.bat`, `gradle/libs.versions.toml`, `app/build.gradle.kts`
  - `scripts/build_dictionary_db.py`, `tools/dict-builder/seed_dictionary.json`, `tools/dict-builder/seed_articles.json`, `app/src/main/assets/dictionary.db`
  - `start-emulator.bat`, `AGENTS.md`, `README.md`, `docs/PROJECT_STATE.md`, `docs/sessions/001_khoi-tao-va-ke-hoach.md`
- **Tầng Dữ liệu Room (`app/src/main/java/com/enziidict/app/data/local/`)**:
  - `EnziiDatabase.kt`, `DatabaseModule.kt`, `app/schemas/com.enziidict.app.data.local.EnziiDatabase/1.json`
  - `entity/DictionaryEntry.kt`, `entity/SavedWord.kt`, `entity/SpeakingEntities.kt`, `entity/BilingualArticle.kt`
  - `dao/DictionaryDao.kt`, `dao/SavedWordDao.kt`, `dao/SpeakingAndReadingDaos.kt`
- **Core Audio, Database & Network (`app/src/main/java/com/enziidict/app/core/`)**:
  - `database/DictionaryRepository.kt`
  - `audio/TtsManager.kt`, `audio/LiveAudioManager.kt`, `audio/ShadowingAudioController.kt`, `audio/ShadowingSpeechRecognizer.kt`, `audio/PronunciationScorer.kt`
  - `network/SecureApiKeyStore.kt`, `network/SettingsDataStore.kt`, `network/OfflineMlKitTranslator.kt`, `network/GeminiGrammarService.kt`, `network/GeminiLiveClient.kt`, `network/SpeakingEvaluationService.kt`, `network/GeminiShadowingCoachService.kt`
- **UI & Các Feature (`app/src/main/java/com/enziidict/app/`)**:
  - `MainActivity.kt`, `EnziiApplication.kt`, `ui/theme/{Color,Type,Theme}.kt`, `ui/components/EnziiDesignComponents.kt`
  - `feature/home/{HomeScreen,HomeViewModel}.kt`
  - `feature/dictionary/{DictionaryScreen,DictionaryViewModel}.kt`, `feature/quicklookup/QuickLookupActivity.kt`
  - `feature/srs/{FlashcardScreen,SrsScreen,SrsViewModel,Sm2Scheduler}.kt`
  - `feature/reading/{ReadingScreen,ReadingViewModel}.kt`
  - `feature/translate/{TranslateScreen,TranslateViewModel}.kt`
  - `feature/settings/{SettingsScreen,SettingsViewModel}.kt`
  - `feature/speaking/{SpeakingScreen,SpeakingHistoryScreen,SpeakingViewModel}.kt`
  - `feature/shadowing/{ShadowingScreen,ShadowingViewModel,ShadowingModels,ShadowingHistoryStore}.kt`
- **Kiểm thử (`app/src/test/java/com/enziidict/app/`)**:
  - `data/local/DictionaryDatabaseTest.kt`, `feature/srs/{Sm2SchedulerTest,SrsAndReadingFlowTest}.kt`, `core/audio/PronunciationScorerTest.kt`, `core/network/GeminiGrammarServiceTest.kt`, `feature/speaking/SpeakingEvaluationAndHistoryTest.kt`, `feature/shadowing/{ShadowingChunkerTest,ShadowingAiCoachTest}.kt`

---

## 3. Các Lỗi Đã Gặp & Cách Khắc Phục

| # | Lỗi gặp phải | Nguyên nhân | Cách khắc phục |
| :--- | :--- | :--- | :--- |
| 1 | `Remote host terminated the handshake` khi tải dependency Maven Central | Đàm phán TLS/IPv6 không ổn định trên môi trường Windows | Thêm `-Djdk.tls.client.protocols=TLSv1.2 -Dhttps.protocols=TLSv1.2 -Djava.net.preferIPv4Stack=true` vào `gradle.properties` |
| 2 | `ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain` khi chạy `gradlew.bat` | Đường dẫn thư mục chứa dấu tiếng Việt (`app học tiếng anh`) bị méo ký tự dưới CP437 | Cập nhật `gradlew.bat` ưu tiên classpath tương đối `gradle\wrapper\gradle-wrapper.jar` khi chạy từ thư mục gốc |
| 3 | AGP chặn build và JVM test worker (`GradleWorkerMain`) báo `ClassNotFoundException` | AGP path check + Windows CP1252 `@argfile` làm hỏng đường dẫn `app/build` có dấu | Bật `android.overridePathCheck=true` và tự động chuyển `layout.buildDirectory` sang `%TEMP%\EnziiDictBuild` trong root `build.gradle.kts` |
| 4 | Room báo lỗi trùng tên cột `entry_id` khi dùng `@Embedded` | Hai bảng `saved_words` và `dictionary_entries` dùng chung tên cột | Đặt `@Embedded(prefix = "dict_")` và dùng alias `dict_*` trong câu SQL |
| 5 | `DictionaryDatabaseTest` trượt với FTS4 `MATCH` cho từ tiếng Việt có dấu mở rộng | Tokenizer `unicode61` trên SQLite Android/Robolectric không tách tốt một số ký tự tiếng Việt mở rộng (`ế`, `ộ`, ...) | Bổ sung tầng 3 `DictionaryDao.searchByMeaning` (`LIKE`) cho tra ngược tiếng Việt |
| 6 | Lỗi tương thích thư viện AndroidX với `compileSdk = 34` | Bản `core-ktx` 1.15+ / `activity-compose` 1.10+ yêu cầu SDK 35 | Ghim `core-ktx = 1.13.1` và `activity-compose = 1.9.3` trong `libs.versions.toml` |
| 7 | `sdkmanager` tải ảnh máy ảo bị treo chậm qua IPv4 | Định tuyến tới `dl.google.com` bị nghẽn IPv4 | Dùng `$env:SDKMANAGER_OPTS = '-Djava.net.preferIPv6Addresses=true'` khi tải system image |
| 8 | Máy ảo khởi chạy từ shell nền không hiện cửa sổ desktop (`MainWindowHandle == 0`) & `adb` báo `no devices/emulators found` | Chạy emulator ở session nền hoặc chưa khởi động AVD trước khi gọi `adb install` | Dọn `.lock`, tạo `start-emulator.bat` để người dùng mở trực tiếp cửa sổ máy ảo trên desktop rồi mới cài APK |
| 9 | Cảnh báo deprecated Compose Icons & ký tự `>` trong tên test Kotlin | `Icons.Filled.VolumeUp`/`MenuBook` lỗi thời; tên hàm test backtick chứa `->` | Đổi sang `Icons.AutoMirrored.Filled.*` và đổi `->` thành `then` |

---

## 4. Điểm Cần Lưu Ý Khi Làm Tiếp

1. **PowerShell 5.1**: Không hỗ trợ toán tử `&&`; khi nối lệnh dùng `;` và kiểm tra `$LASTEXITCODE`. Luôn đặt `$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"` trước khi chạy `.\gradlew.bat`.
2. **Đường dẫn APK Debug**: Do build directory chuyển sang `%TEMP%`, file APK sau khi chạy `assembleDebug` nằm tại:  
   `%TEMP%\EnziiDictBuild\app\outputs\apk\debug\app-debug.apk`.
3. **Đồng bộ `dictionary.db` & Room Schema**: Khi sửa bất kỳ Entity/Index nào trong `data/local/`, bắt buộc cập nhật `scripts/build_dictionary_db.py`, không dùng `DEFAULT` lệch giữa SQLite và Room, chạy lại script Python và xác minh bằng `testDebugUnitTest`.
4. **Vòng đời Phần cứng & Bảo mật**:
   - Không lưu hoặc log Gemini API Key ở dạng plain text (luôn đi qua `SecureApiKeyStore`).
   - Giữ nguyên cơ chế dọn `AudioRecord`/`AudioTrack`/`MediaRecorder`/`MediaPlayer`/`SpeechRecognizer`/`WebSocket` ở cả `DisposableEffect` (`ON_STOP` & `onDispose`) và `ViewModel.onCleared()`.
5. **Quy ước Lưu Phiên**: File phiên tiếp theo trong `docs/sessions/` sẽ bắt đầu từ số **`003_...md`**.
