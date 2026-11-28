# Phiên 011 — Tổng kết Đợt Nâng cấp Cục bộ (Thông báo `WorkManager` + Tiến độ Duolingo-inspired + Trọn bộ Linh vật Chú Dúi)

- **Ngày**: 2026-11-28
- **Mục tiêu phiên**: Lưu trữ đầy đủ ngữ cảnh kỹ thuật của cả đợt phát triển mới nhất *(bao gồm chuỗi mốc 008 → 010 hiện đang giữ ở local để kiểm thử trên máy ảo Android trước khi commit/push lên GitHub)*.

---

## 1. Tóm tắt Những gì Đã hoàn thành trong Đợt làm việc

1. **Hệ thống Thông báo Học tập Thông minh (`WorkManager` + `POST_NOTIFICATIONS` + Deep Link)** *(chi tiết tại `008_thong-bao-nhac-hoc-workmanager.md`)*:
   - Cấu hình `HiltWorkerFactory` trong `EnziiApplication` và tắt `WorkManagerInitializer` mặc định tại `AndroidManifest.xml`, bật `singleTop` cho `MainActivity`.
   - Bổ sung mục **"Nhắc nhở & Từ vựng mỗi ngày"** trong `SettingsScreen` / `SettingsViewModel` / `SettingsDataStore` (xin quyền `POST_NOTIFICATIONS` Android 13+, chọn giờ nhắc học mặc định `20:00`, chọn tần suất từ vựng `1` hoặc `2` lần/ngày, chọn nguồn từ và nút xem thử).
   - Tạo `DailyStudyReminderWorker`, `DailyWordNotificationWorker` (`BigTextStyle` + `NotificationCompat.Action`) và `StudyNotificationScheduler` cùng Deep Link mở thẳng Sổ tay Flashcard / Chi tiết từ / Shadowing.

2. **Theo dõi Tiến độ, Streak Freeze, Daily Quests & Tổng kết Học tập (`Room v3`)** *(chi tiết tại `009_theo-doi-tien-do-duolingo-va-tong-ket.md`)*:
   - Mở rộng Room lên `version = 3` (`DailyStudyLog`, `UserStreakState`, `StudyProgressDao`, bổ sung `observeMasteredCount` & `observeRecentlyStudiedWords` ở `SavedWordDao`, `MIGRATION_2_3`, xuất schema `3.json`) và đồng bộ tái tạo `app/src/main/assets/dictionary.db` bằng `scripts/build_dictionary_db.py`.
   - Xây dựng `LearningProgressRepository` & `StreakCalculator` tự động ghi XP, từ, phút nói, câu Shadowing, tự tiêu thụ `Streak Freeze` khi nghỉ ngắn ngày và thưởng thêm Freeze ở cột mốc 7 ngày liên tiếp; gắn vào `SrsViewModel`, `ShadowingViewModel`, `ReadingViewModel`, `SpeakingViewModel` và `HomeViewModel`.
   - Nâng cấp `HomeScreen` với thẻ Streak Hero (lịch 7 ngày T2–CN, Streak Freeze, mục tiêu XP) + **3 Nhiệm vụ hằng ngày**, bổ sung `FlashcardRecapCard` / `SpeakingRecapDialog` (`SessionRecapComponents.kt`) và màn hình `LearningSummaryScreen` (biểu đồ cột 7/30 ngày, từ thành thạo Mastered, điểm mạnh nhất, từ vựng tiêu biểu tuần).

3. **Tối ưu Tài nguyên Logo & Tích hợp Linh vật Chú Dúi (`DuiMascotView`)** *(chi tiết tại `010_tich-hop-logo-va-linh-vat-chu-dui.md`)*:
   - Dựng 5 sắc thái chú Dúi đồng bộ 1:1 với ảnh logo gốc, tạo `scripts/optimize_mascot_assets.py` chuyển đổi & nén toàn bộ 7 ảnh WebP vào `app/src/main/res/drawable-nodpi/` chỉ chiếm **`85.6 KB`**.
   - Cấu hình `Adaptive Icon` (`ic_launcher_background.xml` gradient `#0D9488`, `ic_launcher_dui_foreground.webp` nằm gọn trong vùng an toàn 64% viền `#F59E0B`, cập nhật `ic_launcher.xml` & `ic_launcher_round.xml`).
   - Xây dựng `DuiMascotView` (`MascotMood`, `MascotLevel`, `MascotState`, hiệu ứng spring + bong bóng thoại tooltip) & `EnziiSplashOverlay` (`MainActivity`), gắn xuyên suốt Splash, Home (Streak + tooltip người mới `dismissedMascotTip`), Từ điển (chưa thấy từ & gợi ý Shadowing), AI Speaking (đổi theo trình độ & khi gọi), Shadowing, Recap và Tổng kết.

---

## 2. Các File Chính Đã Tạo Mới & Chỉnh Sửa

### Tạo mới
- **Database & Progress**: `StudyProgressEntities.kt`, `StudyProgressDao.kt`, `app/schemas/.../EnziiDatabase/3.json`, `LearningProgressRepository.kt`
- **Notifications (`core/notification/`)**: `EnziiNotificationHelper.kt`, `StudyNotificationScheduler.kt`, `DailyStudyReminderWorker.kt`, `DailyWordNotificationWorker.kt`
- **Progress & Recap UI (`feature/progress/`)**: `LearningSummaryScreen.kt`, `LearningSummaryViewModel.kt`, `SessionRecapComponents.kt`
- **Mascot & Assets**: `DuiMascotView.kt`, `scripts/optimize_mascot_assets.py`, `res/drawable-nodpi/` (7 file WebP: `logo_enzii_banner`, `ic_launcher_dui_foreground`, `mascot_dui_{inquisitive,focused,happy,sleepy,smart}`)
- **Unit & Robolectric Tests**: `StudyNotificationTest.kt`, `StudyProgressMigrationAndLogicTest.kt`, `MascotStateTest.kt`

### Chỉnh sửa chính
- `AGENTS.md` *(bổ sung quy tắc chỉ build Debug cục bộ test máy ảo, không tự commit/push khi chưa được xác nhận)*
- `EnziiApplication.kt`, `AndroidManifest.xml`, `MainActivity.kt`
- `EnziiDatabase.kt`, `EnziiDatabaseMigrations.kt`, `DatabaseModule.kt`, `SavedWordDao.kt`, `scripts/build_dictionary_db.py`, `app/src/main/assets/dictionary.db`
- `SettingsDataStore.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- `HomeViewModel.kt`, `HomeScreen.kt`, `DictionaryScreen.kt`, `FlashcardScreen.kt`, `SrsViewModel.kt`, `ReadingViewModel.kt`, `ShadowingScreen.kt`, `ShadowingViewModel.kt`, `SpeakingScreen.kt`, `SpeakingViewModel.kt`
- `ic_launcher_background.xml`, `ic_launcher.xml`, `ic_launcher_round.xml`
- `WordFolderMigrationTest.kt`

---

## 3. Các Lỗi Đã Khắc phục Trong Phiên

1. **Suy luận kiểu `kotlinx.serialization` trong `DailyWordNotificationWorker.kt`**:
   - Khắc phục lỗi compile bằng cách chỉ định rõ type argument cho `json.decodeFromString<List<ExampleItem>>(...)`, `<List<String>>`, `<List<CollocationItem>>`.
2. **Lỗi khởi tạo constructor trong `LearningProgressRepository.kt`** (`Cannot access '<this>' before the instance has been initialized`):
   - Thay `lambda` truyền qua `this(...)` constructor delegation bằng `private constructor(studyProgressDao, settingsDataStore?, onSyncActivity?)` tách sạch DI `@Inject constructor` và `internal constructor` phục vụ unit test.
3. **Lỗi nhánh `when` và enum trong `DuiMascotView.kt`, `SpeakingScreen.kt` & `MascotStateTest.kt`**:
   - Đổi `SpeakingLevel.BASIC` thành `SpeakingLevel.BEGINNER` khớp đúng với model thực tế của dự án.
4. **Cập nhật Migration Path cho `WordFolderMigrationTest.kt`**:
   - Bổ sung `EnziiDatabaseMigrations.MIGRATION_2_3` vào `.addMigrations(...)` để test nâng cấp DB v1 → v3 hợp lệ toàn vẹn schema.

---

## 4. Kết quả Kiểm chứng & Lưu ý Quan trọng

- **Kết quả kiểm thử cuối cùng đã xác minh**:
  - `$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"; .\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` → **`BUILD SUCCESSFUL`**, **`43/43`** unit & Robolectric tests đạt trên **`14` test suites** (`FAIL=0`).
  - Debug APK (`85,214,967` bytes) đã cài thành công lên máy ảo `emulator-5554` (`Performing Streamed Install: Success`).
- **Điểm cần lưu ý cho phiên tiếp theo**:
  - Toàn bộ thay đổi của chuỗi phiên **008 → 011** hiện mới lưu ở máy cục bộ (chưa `git commit`, chưa `git push origin main`, chưa đẩy tag Release `v*`) theo đúng quy tắc trong `AGENTS.md`.
  - Không tự ý thêm SQL `DEFAULT` vào `scripts/build_dictionary_db.py` hay `@ColumnInfo(defaultValue = ...)` vào Kotlin Entity khi chỉnh Room v3 (`dictionary.db`).
  - Các màn hình thu phát âm thanh (`SpeakingScreen`, `ShadowingScreen`, `DictionaryScreen`) đã được gắn `DisposableEffect` + `onCleared`; khi mở rộng thêm tính năng audio cần giữ nguyên cơ chế giải phóng này.
