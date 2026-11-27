# Phiên 001 — Khởi tạo, Kiến trúc & Triển khai Các Module Cốt lõi của Enzii Dict

- **Ngày**: 2026-11-25 → 2026-11-26
- **Mục tiêu phiên**: Phân tích kiến trúc ứng dụng học tiếng Anh Android (tương tự Hanzii Dict), khởi tạo khung dự án, xây dựng máy ảo test, và triển khai đầy đủ các phân hệ cốt lõi từ tầng dữ liệu đến AI Speaking & Shadowing.

---

## 1. Các Quyết định Kiến trúc Chính

1. **Nền tảng**: Chọn **Kotlin + Jetpack Compose (Material 3) + Room (SQLite + FTS4)** thay vì Web/Capacitor để đạt tra từ offline < 15ms, popup tra nhanh `ACTION_PROCESS_TEXT` khởi động tức thì và dùng trực tiếp Android `TextToSpeech` / `AudioRecord` / `AudioTrack` / `SpeechRecognizer`.
2. **Cấu trúc module**: Chọn **Single-module (`:app`)** chia package sạch theo `core/`, `data/local/`, `feature/`, `ui/` nhằm giảm thời gian sync & build trong giai đoạn đầu.
3. **Định danh ứng dụng**: Tên **Enzii Dict**, package ID `com.enziidict.app`, hỗ trợ `minSdk = 26`, `compileSdk = 34`, `targetSdk = 34`.
4. **Chiến lược AI & Offline Hybrid**:
   - Dịch nhanh offline bằng **Google ML Kit Translate** (`EN ↔ VI`, cho phép tải/xóa gói ngôn ngữ).
   - Giải thích ngữ pháp sâu, đánh giá buổi nói, và chấm trọng âm/nối âm Shadowing dùng **Gemini API (BYOK)** với khóa mã hóa AES-256 bằng `EncryptedSharedPreferences`.
   - Giao tiếp giọng nói thời gian thực dùng **Gemini Live API** (WebSocket `BidiGenerateContent` PCM 16kHz/24kHz) kèm fallback khi chưa có key hoặc mất mạng.

---

## 2. Những Hạng mục Đã Thực hiện trong Phiên

### 2.1 Khởi tạo Dự án & Khắc phục Môi trường Windows
- Thiết lập Gradle Wrapper `8.9`, AGP `8.6.1`, Kotlin `2.0.21`, KSP `2.0.21-1.0.28`, Hilt `2.52`, Version Catalog `gradle/libs.versions.toml`.
- Xử lý trọn vẹn 3 vấn đề đặc thù trên Windows khi thư mục chứa dấu tiếng Việt (`c:\O_D\app học tiếng anh`):
  - Lỗi handshake Maven Central → thêm `-Djdk.tls.client.protocols=TLSv1.2 -Dhttps.protocols=TLSv1.2 -Djava.net.preferIPv4Stack=true` vào `gradle.properties`.
  - Lỗi `gradlew.bat` `ClassNotFoundException` dưới CP437 → ưu tiên classpath tương đối `gradle\wrapper\gradle-wrapper.jar`.
  - Lỗi AGP path check & JVM test worker `@argfile` CP1252 → bật `android.overridePathCheck=true` và chuyển điều kiện `layout.buildDirectory` sang `%TEMP%\EnziiDictBuild` khi đường dẫn gốc chứa non-ASCII.

### 2.2 Tầng Dữ liệu Offline & Từ điển (`com.enziidict.app.data.local` + `scripts/`)
- Tạo các Room Entity & DAO: `DictionaryEntry` + `DictionaryFts` (FTS4), `SavedWord` (SM-2), `SpeakingSession`, `SpeakingMessage`, `BilingualArticle`.
- Viết `scripts/build_dictionary_db.py` hỗ trợ parse nguồn **FVDP** (`.dict`), **Wiktionary JSONL** và dữ liệu mẫu tích hợp, tạo `app/src/main/assets/dictionary.db` với **1.046 mục từ**, 5 bài đọc song ngữ A2–C1 và 7 thẻ mẫu thuộc 3 thư mục chuẩn.
- Xác minh bằng `sqlite3.exe` `EXPLAIN QUERY PLAN`: dùng B-Tree index `idx_dict_word_prefix`, tốc độ truy vấn prefix thực tế < 15ms qua Robolectric test; bổ sung `searchByMeaning` cho tra ngược tiếng Việt có dấu mở rộng.

### 2.3 Các Module Tính năng Đã Hoàn thiện
- **Tra cứu Offline & Tra nhanh (`feature/dictionary`, `feature/quicklookup`)**: Tra cứu 3 tầng, lemmatization cơ bản, `QuickLookupActivity` trong suốt xử lý `ACTION_PROCESS_TEXT`.
- **Sổ tay & Flashcard SM-2 (`feature/srs`)**: Quản lý 3 thư mục chuẩn (*Mặc định*, *Oxford 3000*, *Từ lưu từ hội thoại*) + thư mục tự tạo, lật thẻ 3D trục Y, 4 nút `Again/Hard/Good/Easy` kèm dự báo thời gian ôn tiếp theo (`next_review_at`).
- **Đọc Song ngữ (`feature/reading`)**: 5 bài đọc A2–C1, bộ lọc CEFR, ẩn/hiện dịch toàn bài & từng đoạn, chạm bất kỳ từ nào bằng `LinkAnnotation.Clickable` để mở `ModalBottomSheet` tra từ, nghe UK/US và lưu SRS.
- **Dịch Hybrid & Cài đặt (`feature/translate`, `feature/settings`)**: Tầng 1 ML Kit Offline, Tầng 2 Gemini Deep Grammar Breakdown (cấu trúc câu, điểm ngữ pháp, idioms/sắc thái, gợi ý diễn đạt tự nhiên), màn hình Cài đặt quản lý API Key mã hóa AES-256, chọn model Gemini, chọn Theme và tải/xóa gói offline ML Kit.
- **AI Speaking Partner & Lịch sử (`feature/speaking`)**: `LiveAudioManager` (PCM 16kHz + `AcousticEchoCanceler` + `AudioTrack` 24kHz), Gemini Live WebSocket, 5 kịch bản & 3 trình độ, sóng âm động, phụ đề song ngữ, `SpeakingHistoryScreen` phân tích lỗi & lưu từ mới vào SRS.
- **Luyện Shadowing + AI Coach (`feature/shadowing`)**: Tách cụm nhịp (`ShadowingChunker`), 16 câu mẫu 4 chủ đề + mở trực tiếp từ Từ điển/Đọc, giọng UK/US (`0.75x`/`1.0x`/`1.15x`), Loop, tự động thu sau khi nghe (3-2-1), `MediaRecorder` `.m4a` + đối chiếu `Mẫu ↔ Bạn`, `SpeechRecognizer` offline + highlight 3 trạng thái từng từ, `GeminiShadowingCoachService` góp ý trọng âm/nối âm và `ShadowingHistoryStore` lưu tiến bộ.
- **UI/UX Bento Grid (`feature/home`, `ui/theme`, `ui/components`)**: Màn hình Trang chủ Bento Grid (Streak, SRS đến hạn, lối tắt Nói AI theo chủ đề, Từ vựng nổi bật hôm nay, Đọc, Dịch, Shadowing), hệ màu Light/Dark Mode dịu mắt, `PosPill`/`CefrPill`/`IpaAudioPill` và Haptic Feedback.

### 2.4 Môi trường Máy ảo Android 14
- Cài đặt `emulator` và `system-images;android-34;google_apis;x86_64` (xác nhận tăng tốc phần cứng `WHPX(10.0.26300)`), tạo AVD `Enzii_Pixel_API34` (Pixel 6, 2GB RAM, bật bàn phím PC) và tạo script `start-emulator.bat`.
- Đã cài và chạy kiểm chứng thực tế trên máy ảo không có lỗi runtime (`AndroidRuntime:E` / `SQLiteLog:E` trống).

---

## 3. Kết quả Kiểm chứng Cuối Phiên

- **Unit & Robolectric Integration Tests** (`.\gradlew.bat testDebugUnitTest`): **25/25 passed** (8 test suites: `DictionaryDatabaseTest`, `SrsAndReadingFlowTest`, `Sm2SchedulerTest`, `PronunciationScorerTest`, `GeminiGrammarServiceTest`, `SpeakingEvaluationAndHistoryTest`, `ShadowingChunkerTest`, `ShadowingAiCoachTest`).
- **Smoke Build APK** (`.\gradlew.bat assembleDebug`): **BUILD SUCCESSFUL**, 0 lỗi, 0 cảnh báo Kotlin (`app-debug.apk` ~84.9 MB).
