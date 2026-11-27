# Phiên 007 — Tự động Cập nhật Trong App (GitHub Releases) & GitHub Actions Signed Release CI/CD

- **Ngày**: 2026-11-28
- **Mục tiêu phiên**: Triển khai hoàn chỉnh cơ chế cập nhật trực tiếp trên điện thoại qua **GitHub Releases** (Cách 1), đồng thời đảm bảo 2 điều kiện bắt buộc để cập nhật đè giữ nguyên 100% dữ liệu học tập: **ký đồng bộ bằng `enzii-release.jks`** qua GitHub Secrets và **tăng `versionCode` tự động theo SemVer**.

---

## 1. Những gì đã hoàn thành

1. **Dynamic Versioning & Ký số CI (`app/build.gradle.kts`)**:
   - Hỗ trợ truyền `-PenziiVersionCode` và `-PenziiVersionName` khi build để tự tăng `versionCode` trong CI (`MAJOR * 10000 + MINOR * 100 + PATCH`).
   - Thêm `BuildConfig.GITHUB_OWNER` (`"vudaivlogs-hue"`) và `BuildConfig.GITHUB_REPO` (`"app-h-c-ti-ng-anh-"`).
   - Cho phép đọc cấu hình ký Release từ **môi trường CI** (`ENZII_KEYSTORE_PATH`, `ENZII_KEYSTORE_PASSWORD`, `ENZII_KEY_ALIAS`, `ENZII_KEY_PASSWORD`) hoặc từ `keystore.properties` cục bộ (vẫn bỏ qua an toàn nếu đang để mật khẩu placeholder).

2. **Quyền Cài đặt & `FileProvider` (`AndroidManifest.xml`, `res/xml/file_paths.xml`)**:
   - Khai báo `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`.
   - Cấu hình `androidx.core.content.FileProvider` (`${applicationId}.fileprovider`) trỏ tới `cache/updates/` để mở trình cài đặt hệ thống Android an toàn từ Android 8+.

3. **Engine Kiểm tra & Tải APK Cập nhật (`AppUpdateManager.kt`)**:
   - Kiểm tra API `https://api.github.com/repos/{owner}/{repo}/releases/latest`, ưu tiên file `.apk` đã ký (`arm64` / `release`), bỏ qua file `-unsigned.apk` và so sánh phiên bản SemVer chính xác (`isNewerVersion`).
   - Stream tải `.apk` về `cacheDir/updates/` kèm tiến độ `%` và MB thực tế; kiểm tra quyền `canRequestPackageInstalls()` (Android 8+) và mở màn hình cấp quyền hoặc intent cài đặt đè trực tiếp.

4. **Giao diện Cập nhật trong `SettingsScreen` & Badge `MainActivity`**:
   - Thêm thẻ **"Cập nhật ứng dụng (GitHub Releases)"** trong `SettingsScreen`: hiển thị phiên bản hiện tại, nút **Kiểm tra cập nhật**, thông tin bản mới + release notes, thanh tiến độ tải và nút **Cài đặt ngay**.
   - `MainActivity` kiểm tra nhẹ khi mở app và hiển thị chấm `Badge` trên biểu tượng Cài đặt ⚙️ nếu có phiên bản mới trên GitHub Releases.

5. **GitHub Actions CI/CD (`.github/workflows/release-apk.yml`) & Helper Xuất Keystore**:
   - Workflow tự động kích hoạt khi push tag `v*` (VD: `git tag v0.2.0 && git push origin v0.2.0`) hoặc chạy thủ công qua **Actions → Run workflow**: giải mã `ENZII_KEYSTORE_BASE64`, chạy `testDebugUnitTest assembleRelease`, đổi tên thành `EnziiDict-vX.Y.Z-arm64-v8a.apk` và phát hành lên GitHub Releases.
   - Tạo `scripts/export_keystore_base64.ps1` copy chuỗi Base64 của `enzii-release.jks` thẳng vào Clipboard để dán vào GitHub Secrets an toàn.

---

## 2. Kết quả Kiểm chứng

- `.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease --no-daemon --console=plain`: **`BUILD SUCCESSFUL`**.
- **35/35** unit & Robolectric tests đạt (11 test suites), gồm 5 test mới trong `AppUpdateManagerTest`.
- R8 (`minifyReleaseWithR8`), `shrinkReleaseRes`, `lintVitalRelease` pass sạch.
- Release APK (`arm64-v8a`): `app-release-unsigned.apk` — **`20,063,830` bytes (~19.13 MiB / ~20.1 MB)**; Debug APK: `85,594,940` bytes.
