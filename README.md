# Enzii Dict (`com.enziidict.app`)

Ứng dụng học tiếng Anh trên Android (lấy cảm hứng từ Hanzii Dict) xây dựng bằng **Kotlin + Jetpack Compose (Material 3) + Room SQLite (FTS4)** theo hướng **Offline-First kết hợp AI (BYOK Gemini API)**.

---

## Tính năng chính

- **Trang chủ Bento Grid**: Hiển thị chuỗi ngày học (Streak), thẻ SRS đến hạn, lối tắt gọi thoại AI theo chủ đề, từ vựng nổi bật trong ngày, Đọc song ngữ, Dịch Hybrid và Shadowing; hỗ trợ **Light / Dark Mode** dịu mắt và Haptic Feedback.
- **Tra cứu Từ điển Offline tức thì (< 15ms)**: Database SQLite đánh index B-Tree (`idx_dict_word_prefix`) + FTS4 + tra ngược tiếng Việt; phát âm Anh-Anh (UK) & Anh-Mỹ (US), ví dụ, collocations, từ đồng nghĩa và popup tra nhanh từ mọi ứng dụng (`ACTION_PROCESS_TEXT`).
- **Sổ tay Từ vựng & Flashcard SM-2**: Quản lý thư mục chuẩn (*Mặc định*, *Oxford 3000*, *Từ lưu từ hội thoại*) và thư mục tự tạo; lật thẻ 3D mượt mà với 4 mức đánh giá (`Again`, `Hard`, `Good`, `Easy`) kèm dự báo thời điểm ôn lại.
- **Đọc Song ngữ (A2 – C1)**: 5 bài đọc theo cấp độ, ẩn/hiện bản dịch toàn bài hoặc từng đoạn, chạm bất kỳ từ nào để tra tại chỗ qua BottomSheet và lưu thẳng vào SRS.
- **Dịch thuật Hybrid & Giải thích Ngữ pháp**: Dịch offline tức thì bằng Google ML Kit (kèm quản lý tải/xóa gói Anh–Việt) + phân tích cấu trúc câu, idioms, sắc thái từ bằng Gemini API (BYOK mã hóa AES-256).
- **AI Speaking Partner**: Gọi thoại trực tiếp qua Gemini Live API (PCM 16kHz + `AcousticEchoCanceler` + streaming 24kHz) hoặc fallback STT/TTS; 5 kịch bản & 3 trình độ, sóng âm động, phụ đề song ngữ, lưu lịch sử Room, đánh giá AI sau buổi nói và lưu từ mới vào SRS.
- **Luyện Shadowing + AI Coach**: Chia cụm nhịp (chunks), đọc mẫu UK/US (`0.75x`/`1.0x`/`1.15x`), Loop, tự động thu sau đếm ngược 3-2-1, ghi âm `.m4a` đối chiếu `Giọng mẫu ↔ Giọng của bạn`, `SpeechRecognizer` highlight từng từ (đúng/lệch/bỏ sót), Gemini góp ý trọng âm & nối âm, lưu tiến bộ và từ hay vấp.

---

## Hệ thống Lưu trữ Ngữ cảnh & Lịch sử Phát triển

Dự án lưu toàn bộ trạng thái và nhật ký quyết định ngay trong kho mã nguồn:
- **Tổng hợp trạng thái hiện tại**: [docs/PROJECT_STATE.md](./docs/PROJECT_STATE.md)
- **Lịch sử từng phiên làm việc**: thư mục [docs/sessions/](./docs/sessions/) (đặt tên theo chuẩn `STT_ten-chu-de.md`, bắt đầu từ [001_khoi-tao-va-ke-hoach.md](./docs/sessions/001_khoi-tao-va-ke-hoach.md))
- **Quy tắc tự động cho AI Agent**: [AGENTS.md](./AGENTS.md) — quy định tự động cập nhật `docs/PROJECT_STATE.md` và xuất log phiên mới trong `docs/sessions/` sau mỗi tính năng lớn hoặc khi người dùng yêu cầu **"lưu phiên làm việc"**.

---

## Lệnh Build, Kiểm thử & Chạy Máy ảo (Windows PowerShell)

### 1. Biên dịch lại cơ sở dữ liệu từ điển offline (`dictionary.db`)
```powershell
python scripts/build_dictionary_db.py
# Tùy chọn nạp thêm dữ liệu nguồn lớn:
# python scripts/build_dictionary_db.py --fvdp data/anhviet109K.dict --wiktionary data/en.jsonl
```

### 2. Chạy toàn bộ Unit Tests & Build Debug APK
```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain
```
*(Khi dự án nằm trong đường dẫn Windows có dấu tiếng Việt, APK được xuất tại `%TEMP%\EnziiDictBuild\app\outputs\apk\debug\app-debug.apk`.)*

### 3. Khởi động máy ảo Android 14 & cài APK
```powershell
# Mở cửa sổ máy ảo Android 14 (Enzii_Pixel_API34)
.\start-emulator.bat

# Sau khi máy ảo vào màn hình chính, cài bản APK mới nhất:
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "$env:TEMP\EnziiDictBuild\app\outputs\apk\debug\app-debug.apk"
```
