# Phiên 013 — Triển khai Focus App Lock & Mở khẩn cấp có giới hạn

> **Ngày**: 2026-11-28  
> **Mục tiêu**: Xây dựng tính năng khóa ứng dụng gây xao nhãng theo mục tiêu học tập hằng ngày, kèm cơ chế mở khẩn cấp có giới hạn lượt/ngày và màn hình chặn tích hợp linh vật chú Dúi.

---

## 1. Những gì đã hoàn thành

### 1.1. Model dữ liệu & Room Schema v4
- Tạo mới `FocusLockConfig` và `LockedAppEntity` trong [FocusLockEntities.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/data/local/entity/FocusLockEntities.kt):
  - `FocusLockConfig`: `isEnabled`, `requiredStudyMinutes` (mặc định 20), `requiredKnowledgeCount` (mặc định 10), `requireBoth`, `emergencyUnlockDurationMinutes` (mặc định 5), `maxEmergencyUnlocksPerDay` (mặc định 2), `emergencyUnlockedUntilMillis`, `emergencyUsedCountToday`, `emergencyUsageDate`.
  - `LockedAppEntity`: lưu danh sách `packageName` bị khóa kèm `appLabel` và `addedAtMillis`.
- Tạo [FocusLockDao.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/data/local/dao/FocusLockDao.kt) và nâng cấp [EnziiDatabase.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/data/local/EnziiDatabase.kt) lên `version = 4`.
- Thêm `MIGRATION_3_4` trong [EnziiDatabaseMigrations.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/data/local/EnziiDatabaseMigrations.kt), đăng ký trong [DatabaseModule.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/data/local/DatabaseModule.kt) và các file test migration hiện có.
- Đồng bộ DDL + dòng seed mặc định vào [build_dictionary_db.py](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/scripts/build_dictionary_db.py) (không dùng `DEFAULT`), build lại `dictionary.db` (`33,619,968` bytes = `32.06 MB`) và xuất schema `4.json` khớp Room.

### 1.2. Policy đánh giá mở khóa & Repository
- Tạo [FocusLockRepository.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/core/database/FocusLockRepository.kt) với `FocusLockPolicyEvaluator` và `@Singleton FocusLockRepository`:
  - Hỗ trợ điều kiện chỉ theo phút học, chỉ theo số thẻ/lượt học, phải đạt cả hai (`requireBoth = true`), hoặc đạt một trong hai (`requireBoth = false`).
  - Hỗ trợ reset tự động bộ đếm mở khẩn cấp khi sang ngày mới (`resetEmergencyCounterIfNeeded`) và cấp quyền mở khẩn cấp giới hạn số lần/ngày (`requestEmergencyUnlock`).

### 1.3. Service giám sát & Màn hình chặn
- Khai báo quyền `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `<queries>` launcher apps, `AppBlockerService` và `BlockedAppActivity` trong [AndroidManifest.xml](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/AndroidManifest.xml).
- Thêm kênh thông báo mức ưu tiên thấp `CHANNEL_FOCUS_LOCK` (`NOTIF_ID_FOCUS_LOCK = 1003`) trong [EnziiNotificationHelper.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/core/notification/EnziiNotificationHelper.kt).
- Tạo [FocusLockPermissions.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/feature/focuslock/FocusLockPermissions.kt) kiểm tra `AppOpsManager.OPSTR_GET_USAGE_STATS`, `Settings.canDrawOverlays`, mở thẳng Settings hệ thống và tải danh sách app launcher cài đặt trên máy.
- Tạo [AppBlockerService.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/feature/focuslock/AppBlockerService.kt) dùng `UsageStatsManager.queryEvents` kiểm tra app đang ở foreground, tự dừng (`stopSelf`) khi tắt khóa hoặc danh sách app trống, và giải phóng coroutine scope trong `onDestroy`.
- Tạo [BlockedAppActivity.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/feature/focuslock/BlockedAppActivity.kt) chặn nút Back, hiển thị `DuiMascotView(FOCUSED)`, tên app đang bị chặn, thanh tiến độ học hôm nay, nút **"Học ngay để mở khóa"** (mở thẳng tab SRS) và nút **"Mở khẩn cấp"** kèm hộp thoại xác nhận.

### 1.4. Màn hình quản lý & Điều hướng
- Tạo [FocusLockViewModel.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/feature/focuslock/FocusLockViewModel.kt) và [FocusLockScreen.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/feature/focuslock/FocusLockScreen.kt) (công tắc bật/tắt, banner trạng thái theo thời gian thực, kiểm tra 2 quyền hệ thống, cấu hình phút/số từ & `requireBoth`, cấu hình mở khẩn cấp, tìm kiếm và bật/tắt nhanh ứng dụng cài đặt trên máy).
- Gắn lối vào từ [HomeScreen.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/feature/home/HomeScreen.kt), [SettingsScreen.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/feature/settings/SettingsScreen.kt), và đồng bộ service khi mở app trong [MainActivity.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/main/java/com/enziidict/app/MainActivity.kt).

---

## 2. Kết quả Kiểm chứng Cục bộ *(chưa commit/push theo quy tắc test trước)*

- **Room Schema**: Đã sinh [4.json](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/schemas/com.enziidict.app.data.local.EnziiDatabase/4.json) và rebuild `dictionary.db` (`33,619,968` bytes).
- **Test mới**: [FocusLockMigrationAndPolicyTest.kt](file:///c:/O_D/app%20h%E1%BB%8Dc%20ti%E1%BA%BFng%20anh/app/src/test/java/com/enziidict/app/data/local/FocusLockMigrationAndPolicyTest.kt) (migration v3 → v4, evaluator mở khóa, giới hạn & reset mở khẩn cấp qua ngày mới).
- **`.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain`**: `BUILD SUCCESSFUL` (`47/47` test đạt trên `16` suites, `0` lỗi, `0` skipped).
- **APK Debug**: `app-debug.apk` = `99,025,631` bytes (`94.44 MB`).
- **Tồn đọng**: Người dùng cần test thực tế trên `emulator-5554` (cấp 2 quyền Usage Access & Display over other apps, khóa 1 app mẫu, kiểm tra `BlockedAppActivity`, mở khẩn cấp và học đủ mục tiêu để mở khóa tự động) trước khi xác nhận commit/push/release.
