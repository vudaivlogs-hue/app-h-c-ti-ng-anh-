# Phiên 014 — Sửa lỗi Gemini HTTP 404, Đồng bộ Model theo Key & Khắc phục Luyện nói AI

> **Ngày**: 2026-11-28  
> **Mục tiêu**: Khắc phục triệt để lỗi `HTTP 404` khi gọi Gemini REST API (Dịch thuật, Từ điển Online, Shadowing Coach, Đánh giá Speaking) và sửa toàn bộ lỗi WebSocket / fallback trong tính năng Luyện nói AI (`Nói AI`).

---

## 1. Nguyên nhân gốc rễ đã xác định
1. **Model hardcode đã ngừng hỗ trợ / hạn chế với API Key mới**: `gemini-2.0-flash`, `gemini-2.5-flash`, `models/gemini-2.5-flash-native-audio-preview-09-2025` trả `HTTP 404 NOT_FOUND` hoặc ngắt WebSocket trên các key không được cấp model đó.
2. **`GeminiLiveClient` bỏ qua binary frame (`ByteString`)**: OkHttp `WebSocketListener` chỉ override `onMessage(ws, text: String)` nên bỏ sót dữ liệu JSON UTF-8 (`setupComplete`, âm thanh 24kHz, phụ đề) gửi qua binary frame.
3. **Đẩy audio trước khi `setupComplete` & ẩn mã lỗi đóng socket**: `SpeakingViewModel` mở mic và stream PCM ngay sau `connect(...)` mà chưa đợi `setupComplete`, đồng thời `onClosing`/`onClosed` không báo `code + reason` khi `code != 1000`.
4. **Chế độ dự phòng Speaking chưa nối với Gemini REST**: khi không có Live Audio WebSocket, `sendFallbackUserMessage` gửi vào WebSocket đóng thay vì gọi Gemini sinh hội thoại thực.

---

## 2. Những gì đã hoàn thành

### 2.1. Khám phá & Tự động đồng bộ Model Gemini (`GeminiModelCatalogService`, `SettingsDataStore`)
- Tạo [GeminiModelCatalogService.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/core/network/GeminiModelCatalogService.kt): gọi `GET /v1beta/models?pageSize=200&key=...` (hỗ trợ `nextPageToken`, cache theo key), phân loại & xếp hạng model văn bản (`generateContent`) và Live Audio (`bidiGenerateContent`), đồng thời trích xuất `error.message` từ phản hồi lỗi Google.
- Cập nhật [SettingsDataStore.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/core/network/SettingsDataStore.kt): lưu danh sách `discoveredTextModels` / `discoveredLiveModels`, thêm preset `gemini-flash-latest` & `gemini-2.5-flash-lite`, cập nhật `DEFAULT_LIVE_MODEL` và chuẩn hóa tên model.

### 2.2. Tự động phục hồi HTTP 404 trên toàn bộ dịch vụ Gemini REST
- Cập nhật [GeminiGrammarService.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/core/network/GeminiGrammarService.kt), [OnlineDictionaryRepository.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/core/network/OnlineDictionaryRepository.kt), [SpeakingEvaluationService.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/core/network/SpeakingEvaluationService.kt), và [GeminiShadowingCoachService.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/core/network/GeminiShadowingCoachService.kt):
  - Khi gặp `HTTP 404`, tự động dò model khả dụng từ `GeminiModelCatalogService`, thử lại 1 lần với model hợp lệ và tự lưu vào `SettingsDataStore` để các lần gọi sau chạy mượt luôn.
  - Kèm chi tiết `error.message` của Google trong thông báo lỗi tiếng Việt.

### 2.3. Sửa `GeminiLiveClient` & Nâng cấp `SpeakingViewModel` / `SpeakingScreen`
- Cập nhật [GeminiLiveClient.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/core/network/GeminiLiveClient.kt):
  - Bổ sung `override fun onMessage(ws: WebSocket, bytes: ByteString)` giải mã UTF-8.
  - Bổ sung `setupCompleted` gate cho `sendPcm16kAudioChunk` & `sendTextTurn`.
  - Đổi payload `realtimeInput` sang trường `audio` chuẩn mới, parse `error` JSON từ server và phát `GeminiLiveEvent.Error` kèm `code + reason` trong `onClosing`/`onClosed` khi `code != 1000`.
- Tạo [GeminiSpeakingTurnService.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/core/network/GeminiSpeakingTurnService.kt) cho hội thoại đa lượt qua Gemini REST (có phục hồi 404).
- Cập nhật [SpeakingViewModel.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/feature/speaking/SpeakingViewModel.kt) & [SpeakingScreen.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/feature/speaking/SpeakingScreen.kt):
  - Kiểm tra model Live trước khi kết nối, chỉ bật `LiveAudioManager` sau khi nhận `SetupComplete` (timeout 12s), và tự chuyển sang chế độ **AI Giọng nói (`ShadowingSpeechRecognizer` + `GeminiSpeakingTurnService` + `TtsManager`)** nếu Live WebSocket không khả dụng trên Key hiện tại.
  - Dọn `shadowingSpeechRecognizer.stopAndDestroy()` trong `stopHardwareResources()`.

### 2.4. Cài đặt Kiểm tra Key & Chọn Model động (`SettingsViewModel`, `SettingsScreen`)
- Cập nhật [SettingsViewModel.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/feature/settings/SettingsViewModel.kt) & [SettingsScreen.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/feature/settings/SettingsScreen.kt): thêm nút **"Kiểm tra & Tải model"**, tự đồng bộ model ngay khi bấm **"Lưu Key"**, hiển thị danh sách model văn bản & Live Audio thực tế của API Key.

---

## 3. Kết quả Kiểm chứng Cục bộ *(chưa commit/push theo quy tắc test trước)*
- **Test mới**: [GeminiModelDiscoveryAndLiveProtocolTest.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/test/java/com/enziidict/app/core/network/GeminiModelDiscoveryAndLiveProtocolTest.kt) (5 unit/Robolectric tests cho model catalog, parse `error.message`, phục hồi 404, `GeminiLiveClient`, `GeminiSpeakingTurnService`).
- **`.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain`**: `BUILD SUCCESSFUL` — **52/52** tests đạt trên **17** suites (`0` lỗi, `0` skipped).
- **APK Debug**: `app-debug.apk` = `99,361,251` bytes (`94.76 MB`), cài đặt lên `emulator-5554`.
- **Tồn đọng**: Người dùng test lại trên máy ảo `emulator-5554` (vào Cài đặt bấm **Kiểm tra & Tải model**, thử **Dịch → Phân tích ngữ pháp** và **Nói AI**) rồi xác nhận trước khi commit/push/release.
