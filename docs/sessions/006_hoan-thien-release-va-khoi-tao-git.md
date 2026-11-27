# Phiên 006 — Hoàn thiện Cấu hình Release (`keystore.properties`, IDE Gradle Wrapper) & Khởi tạo Git An toàn

- **Ngày**: 2026-11-27
- **Mục tiêu phiên**: Khởi tạo `keystore.properties` (giữ placeholder mật khẩu), bảo vệ Gradle không lỗi khi mật khẩu chưa điền, cấu hình `.vscode/settings.json` ép IDE dùng Gradle Wrapper 8.9, xác minh `assembleRelease` R8 (`arm64-v8a`), khởi tạo Git repository sạch không lộ secret và ghi nhận tài liệu phiên.

---

## 1. Những gì đã hoàn thành

1. **Khởi tạo `keystore.properties` & Bảo vệ Ký số trong `app/build.gradle.kts`**:
   - Tạo `keystore.properties` từ `keystore.properties.example` tại thư mục gốc dự án (giữ nguyên placeholder `your_keystore_password_here` / `your_key_password_here` để người dùng tự điền mật khẩu thật cho `enzii-release.jks`).
   - Cập nhật logic kiểm tra `hasReleaseKeystore` trong `app/build.gradle.kts`: chỉ kích hoạt `signingConfigs.release` khi file `.jks` tồn tại thực sự và mật khẩu không còn ở dạng placeholder — giúp `.\gradlew.bat assembleRelease` chạy sạch ngay cả khi chưa điền mật khẩu thật, và tự động ký `app-release.apk` ngay sau khi người dùng cập nhật mật khẩu.
   - Kiểm tra `.gitignore` xác nhận loại trừ hoàn toàn `*.jks`, `*.keystore`, `keystore.properties`, `local.properties`, `.gradle/`, `build/`, `/app/build`, `*.apk`, `*.aab`.

2. **Cấu hình IDE ưu tiên Gradle Wrapper (`.vscode/settings.json`)**:
   - Bổ sung `"java.import.gradle.wrapper.enabled": true` và `"gradle.nestedProjects": true` để IDE / Java extension dùng đúng Gradle Wrapper `8.9` (`gradlew.bat`) thay vì Gradle 9.x cài sẵn trên hệ thống (nguyên nhân gây cảnh báo trong `build/reports/problems/problems-report.html`).

3. **Kiểm chứng Build Release (`arm64-v8a` + R8/ProGuard)**:
   - Chạy `.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease --no-daemon --console=plain`:
     - **`BUILD SUCCESSFUL`**, **30/30** unit & Robolectric tests đạt, R8 minification (`minifyReleaseWithR8`), shrink resources (`shrinkReleaseRes`) và `lintVitalRelease` đều pass không cần sửa thêm keep rule.
     - File Release APK (`arm64-v8a`, chưa ký do placeholder mật khẩu):  
       `C:\Users\ADMIN\AppData\Local\Temp\EnziiDictBuild\app\outputs\apk\release\app-release-unsigned.apk` — dung lượng **`20,046,986` bytes (~19.12 MiB / ~20.0 MB)**.
     - File Debug APK:  
       `C:\Users\ADMIN\AppData\Local\Temp\EnziiDictBuild\app\outputs\apk\debug\app-debug.apk` — dung lượng **`85,044,173` bytes (~81.10 MiB / ~85.0 MB)**.

4. **Khởi tạo Git Repository (`main`) & Kiểm tra Rò rỉ Secret**:
   - Khởi tạo Git local tại nhánh `main`, cấu hình `user.name` / `user.email` cục bộ (`--local`).
   - Xác minh bằng `git check-ignore` và `git status`: `enzii-release.jks`, `keystore.properties`, `local.properties`, `build/` hoàn toàn bị bỏ qua; commit sạch toàn bộ mã nguồn dự án và tài liệu.

---

## 2. Bước Tiếp Theo Khi Cần Ký Bản Chính Thức & Đẩy Lên GitHub

1. Điền mật khẩu thật của `enzii-release.jks` vào `keystore.properties` và chạy lại `.\gradlew.bat assembleRelease` → thu được `app-release.apk` đã ký (~20 MB) để cài thẳng lên điện thoại.
2. Gắn remote GitHub và đẩy nhánh `main`:
   ```powershell
   git remote add origin <LINK_REPO_GITHUB>
   git push -u origin main
   ```
