# Quy tắc Dự án Enzii Dict (`AGENTS.md`)

Tài liệu này định nghĩa các quy tắc làm việc bắt buộc cho AI Agent khi thao tác trên dự án **Enzii Dict** (`com.enziidict.app`).

---

## 1. Quy tắc Lưu trữ Ngữ cảnh & Lịch sử Phiên làm việc (Bắt buộc)

1. **Đọc ngữ cảnh khi bắt đầu phiên mới**:
   - Trước khi thiết kế hoặc sửa đổi lớn, ưu tiên đọc `docs/PROJECT_STATE.md` (và file mới nhất trong `docs/sessions/` nếu cần chi tiết lịch sử) để nắm chính xác kiến trúc, module hiện có và các lưu ý kỹ thuật.
2. **Tự động cập nhật khi kết thúc tính năng lớn hoặc khi người dùng yêu cầu "lưu phiên làm việc"**:
   - **Cập nhật `docs/PROJECT_STATE.md`**:
     - Cập nhật ngày chỉnh sửa, link tới phiên mới nhất, danh sách module đã hoàn thành / đang làm dở / backlog, cấu trúc thư mục mới và số lượng test/build thực tế vừa xác minh.
   - **Tạo file log phiên mới trong `docs/sessions/`**:
     - Đặt tên đúng chuẩn `STT_ten-chu-de.md` (số thứ tự 3 chữ số tăng dần, không dấu, gạch nối kebab-case).  
       Ví dụ: `002_nap-tu-dien-fvdp-100k.md`, `003_nhac-hoc-workmanager.md`.
     - Nội dung tối thiểu gồm: Ngày & mục tiêu phiên, các quyết định kỹ thuật, danh sách file/module đã thêm hoặc sửa, kết quả kiểm thử (`testDebugUnitTest` / `assembleDebug`) và đầu việc còn tồn đọng cho phiên sau.

---

## 2. Quy ước Kỹ thuật Cốt lõi của Enzii Dict

- **Kiến trúc**: Single-module `:app` (Kotlin + Jetpack Compose Material 3 + Room + Hilt), chia package rõ theo `core/`, `data/local/`, `feature/`, `ui/`.
- **Môi trường Windows & Đường dẫn có dấu**:
  - Giữ `android.overridePathCheck=true` và các cờ TLS/IPv4 trong `gradle.properties`.
  - Giữ logic chuyển `layout.buildDirectory` sang `%TEMP%\EnziiDictBuild` trong root `build.gradle.kts` khi đường dẫn dự án chứa ký tự non-ASCII.
  - Luôn đặt `$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"` trước khi chạy `.\gradlew.bat`.
- **Database & Pre-packaged `dictionary.db`**:
  - Mọi thay đổi Entity/Index trong `com.enziidict.app.data.local` phải cập nhật đồng bộ trong `scripts/build_dictionary_db.py`, chạy lại script và kiểm tra khớp Room schema (`app/schemas/`).
  - Không dùng `DEFAULT` không đồng bộ giữa SQLite và Room `@ColumnInfo`.
- **Bảo mật & Offline-first**:
  - Gemini API Key (BYOK) chỉ được lưu qua `SecureApiKeyStore` (`EncryptedSharedPreferences`), tuyệt đối không log hoặc commit API Key.
  - Mọi tính năng gọi AI phải có xử lý lỗi rõ ràng và fallback offline an toàn khi thiếu API Key hoặc mất mạng.
- **Vòng đời Phần cứng**:
  - Mọi màn hình dùng micro, loa hoặc WebSocket (`SpeakingScreen`, `ShadowingScreen`) phải giải phóng tài nguyên ở cả `DisposableEffect` (`ON_STOP` & `onDispose`) và `ViewModel.onCleared()`.
- **Kiểm chứng trước khi báo hoàn tất**:
  - Sau mỗi đợt thay đổi mã nguồn, chạy `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` và báo đúng kết quả kiểm thử thực tế.
- **Quy trình Git & Phát hành Bản cập nhật (Bắt buộc)**:
  - **Không tự động commit, push lên GitHub hoặc tạo/đẩy tag Release** sau khi chỉnh sửa code.
  - Chỉ xây bản Debug local để người dùng test trên máy ảo Android trước; chỉ thực hiện `git commit`, `git push origin main` và tạo tag Release (`v*`) cập nhật cho điện thoại thật **sau khi người dùng xác nhận đã test ổn và yêu cầu đẩy lên**.
