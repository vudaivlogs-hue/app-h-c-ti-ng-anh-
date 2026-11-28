# Phiên 008 — Hệ thống Thông báo Học tập Thông minh (`WorkManager` + `POST_NOTIFICATIONS` + Deep Links)

- **Ngày**: 2026-11-28
- **Mục tiêu phiên**: Triển khai hệ thống nhắc học hằng ngày & gợi ý Từ vựng / Mẫu câu mỗi ngày chạy nền bằng `WorkManager`, xin quyền `POST_NOTIFICATIONS` thân thiện trên Android 13+, hỗ trợ nút hành động & Deep Link mở thẳng màn hình Chi tiết từ / Flashcard / Shadowing, và đóng gói Debug APK cục bộ để kiểm thử trên máy ảo.

---

## 1. Những gì đã hoàn thành

1. **Cấu hình `HiltWorkerFactory`, `WorkManager` & Manifest**:
   - Cập nhật `EnziiApplication` triển khai `Configuration.Provider` dùng `HiltWorkerFactory` và khởi tạo 2 kênh thông báo ngay khi app mở (`EnziiNotificationHelper.ensureChannels`).
   - Cập nhật `AndroidManifest.xml`: tắt `WorkManagerInitializer` mặc định để Hilt inject DAO/Repository vào Worker, đặt `android:launchMode="singleTop"` cho `MainActivity` để nhận Deep Link mượt khi app đang chạy.

2. **Lưu cấu hình Thông báo (`SettingsDataStore`) & UI Cài đặt (`SettingsScreen` / `SettingsViewModel`)**:
   - Bổ sung `DailyWordSource` (`SRS_DUE`, `COMMON_VOCAB`, `MIXED`) cùng các trường lưu giờ nhắc học, tần suất gửi từ mỗi ngày và `studiedToday` (tính từ `lastStudyEpochDay`).
   - Thêm thẻ **"Nhắc nhở & Từ vựng mỗi ngày"** trong `SettingsScreen`:
     - Xin quyền `POST_NOTIFICATIONS` thân thiện trên Android 13+ (API 33+) bằng `ActivityResultContracts.RequestPermission` kèm lối tắt mở Cài đặt Thông báo hệ thống nếu người dùng lỡ từ chối.
     - Công tắc bật/tắt **Nhắc học hằng ngày**, nút chọn giờ (`TimePickerDialog`) + chip nhanh (`19:00`, `20:00`, `21:00`, `22:00`).
     - Công tắc bật/tắt **Từ vựng / Mẫu câu trong ngày**, chọn tần suất (`1 lần/ngày 08:30` hoặc `2 lần/ngày 08:30 & 13:00`) và nguồn từ.
     - Hai nút **Thử nhắc học** / **Thử từ hôm nay** để kiểm tra ngay thông báo & Deep Link trên máy ảo.

3. **Worker & Deep Links (`core/notification/`)**:
   - `EnziiNotificationHelper`: tạo 2 NotificationChannel, kiểm tra quyền trên API 33+, dựng thông báo mở rộng `NotificationCompat.BigTextStyle`, thêm 2 nút hành động (`NotificationCompat.Action`) và `PendingIntent` mang `EXTRA_DEST` / `EXTRA_WORD` / `EXTRA_SHADOWING_ID`.
   - `DailyStudyReminderWorker`: kiểm tra nếu hôm nay chưa đạt mục tiêu (hoặc sắp đứt Streak / còn thẻ SRS đến hạn) mới đẩy thông báo nhắc kèm số thẻ đang chờ ôn.
   - `DailyWordNotificationWorker`: xoay vòng từ vựng (IPA + nghĩa Việt + ví dụ) từ SRS / bộ từ thông dụng hoặc mẫu câu giao tiếp ngắn để đẩy thông báo `BigTextStyle`.
   - `StudyNotificationScheduler`: lên lịch `PeriodicWorkRequest` 24h (`UPDATE`) theo đúng giờ đã chọn và hỗ trợ `triggerPreviewNow`.
   - Nối Deep Link tại `MainActivity` (`onCreate` & `onNewIntent`) và `DictionaryScreen` (`initialQuery`) để mở thẳng Sổ tay Flashcard, Chi tiết từ hoặc Shadowing.

---

## 2. Kết quả Kiểm chứng Cục bộ *(chưa commit/push theo quy tắc test máy ảo trước)*

- `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain`:
  - **`BUILD SUCCESSFUL`**, 0 lỗi, 0 cảnh báo.
  - **39/39** unit & Robolectric tests đạt (12 test suites), gồm 4 test mới trong `StudyNotificationTest` (kiểm tra tính `initialDelay`, logic bảo vệ Streak, nội dung `BigTextStyle` + Deep Link, tạo NotificationChannel trên API 34 và kiểm tra quyền `POST_NOTIFICATIONS`).
- Debug APK cục bộ: `C:\Users\ADMIN\AppData\Local\Temp\EnziiDictBuild\app\outputs\apk\debug\app-debug.apk` (`85,594,999` bytes).
