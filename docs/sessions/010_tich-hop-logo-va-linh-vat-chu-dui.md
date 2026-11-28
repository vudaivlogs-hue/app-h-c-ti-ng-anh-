# Phiên 010 — Tích hợp Logo Banner, Adaptive Icon & Linh vật Chú Dúi (`DuiMascotView`)

- **Ngày**: 2026-11-28
- **Mục tiêu phiên**: Nén tối ưu trọn bộ logo banner và 5 sắc thái linh vật chú Dúi sang WebP siêu nhẹ, cấu hình Android Adaptive Icon không bị cắt viền, xây dựng `MascotState` + `DuiMascotView` + `EnziiSplashOverlay`, và gắn linh vật xuyên suốt các màn hình chính của Enzii Dict.

---

## 1. Những gì đã hoàn thành

1. **Tối ưu Tài nguyên WebP & Adaptive Icon (`scripts/optimize_mascot_assets.py`, `res/drawable-nodpi/`)**:
   - Chuyển đổi trọn bộ 5 biểu cảm chú Dúi (`384×384` WebP ~10 KB/ảnh: `mascot_dui_inquisitive.webp`, `mascot_dui_focused.webp`, `mascot_dui_happy.webp`, `mascot_dui_sleepy.webp`, `mascot_dui_smart.webp`), banner logo (`logo_enzii_banner.webp`, `768×432`, `18,614` bytes) và `ic_launcher_dui_foreground.webp` (`432×432` RGBA nằm gọn trong vùng an toàn 64% với viền `#F59E0B`). Tổng dung lượng cả 7 tệp chỉ **`85.6 KB`**.
   - Cập nhật `ic_launcher_background.xml` (gradient xanh mòng két `#14B8A6 → #0D9488 → #0F766E`), `mipmap-anydpi-v26/ic_launcher.xml` và `ic_launcher_round.xml`.

2. **Component Tái sử dụng & Splash (`DuiMascotView.kt`, `SettingsDataStore.kt`)**:
   - Tạo `MascotMood` (`INQUISITIVE`, `FOCUSED`, `HAPPY`, `SLEEPY`, `SMART`), `MascotLevel` (`BASIC`, `INTERMEDIATE`, `ADVANCED` kèm helper `fromSpeakingLevel` & `fromMasteredCount`), `MascotState`.
   - Tạo `DuiMascotView` hỗ trợ `AnimatedContent` (fade + spring scale), hiệu ứng nhấn, viền đồng bộ màu cấp độ/tâm trạng, huy hiệu CEFR và bong bóng thoại tùy chọn có nút hành động & nút đóng tooltip.
   - Tạo `EnziiSplashOverlay` hiển thị logo banner chú Dúi (~1.15s, chạm bỏ qua ngay) và thêm cờ `dismissedMascotTip` vào `SettingsDataStore`.

3. **Tích hợp Xuyên suốt Các Màn hình Chính**:
   - **Splash (`MainActivity.kt`)**: Khởi động mượt với `EnziiSplashOverlay` (tự bỏ qua nếu mở từ Deep Link thông báo).
   - **Home (`HomeViewModel.kt`, `HomeScreen.kt`)**: Thẻ Streak Hero dùng `HAPPY` (khi đã học hôm nay) hoặc `SLEEPY` (khi chờ giữ chuỗi) kèm `MascotLevel`; bổ sung tooltip hướng dẫn cho người dùng mới (`INQUISITIVE`) có nút đóng vĩnh viễn.
   - **Dictionary (`DictionaryScreen.kt`)**: Hiển thị `INQUISITIVE` kèm bong bóng gợi ý tra Online khi chưa có từ offline, và gợi ý nghe phát âm/luyện Shadowing ngay trong `WordDetailCard`.
   - **AI Speaking & Shadowing (`SpeakingScreen.kt`, `ShadowingScreen.kt`)**: Phản ứng trực tiếp theo cấp độ (`BASIC` / `INTERMEDIATE` / `ADVANCED`), chuyển sang `FOCUSED` khi gọi thoại hoặc đang phát mẫu/thu âm/chấm điểm Shadowing, và `HAPPY` khi đạt `>= 80%`.
   - **Recap & Tổng kết (`SessionRecapComponents.kt`, `LearningSummaryScreen.kt`)**: Thay biểu tượng tĩnh bằng chú Dúi `HAPPY` / `SMART` / `SLEEPY` theo tiến độ thực tế.

---

## 2. Kết quả Kiểm chứng Cục bộ *(chưa commit/push theo quy tắc test trước)*

- Tạo test mới `app/src/test/java/com/enziidict/app/ui/components/MascotStateTest.kt`.
- Chạy `$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"; .\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain`:
  - **`BUILD SUCCESSFUL`**, **43/43** unit & Robolectric tests đạt across **14 suites** (`FAIL=0`).
  - Debug APK (`85,214,967` bytes) đã cài thành công lên máy ảo `emulator-5554` (`Performing Streamed Install: Success`).
