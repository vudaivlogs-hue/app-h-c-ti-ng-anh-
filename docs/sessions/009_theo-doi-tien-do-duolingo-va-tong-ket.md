# Phiên 009 — Theo dõi Tiến độ Duolingo-inspired, Streak Freeze, Daily Quests & Tổng kết Học tập

- **Ngày**: 2026-11-28
- **Mục tiêu phiên**: Xây dựng hệ thống theo dõi tiến độ học tập hằng ngày, Streak kèm Streak Freeze, 3 Nhiệm vụ hằng ngày trên `HomeScreen`, Post-Session Recap sau Flashcard/Speaking AI, và màn hình `LearningSummaryScreen` thống kê theo tuần/tháng.

---

## 1. Những gì đã hoàn thành

1. **Mở rộng Room Database v2 → v3 & Đồng bộ Pre-packaged `dictionary.db`**:
   - Tạo `StudyProgressEntities.kt`:
     - `DailyStudyLog` (`daily_study_logs`: `date_yyyy_mm_dd`, `words_reviewed`, `words_learned`, `speaking_minutes`, `shadowing_count`, `xp_earned`, `goal_completed`).
     - `UserStreakState` (`user_streak_state`: `id`, `current_streak`, `longest_streak`, `freeze_count`, `last_active_date`, `daily_xp_goal`).
   - Tạo `StudyProgressDao.kt` và bổ sung `observeMasteredCount()` + `observeRecentlyStudiedWords()` vào `SavedWordDao.kt`.
   - Nâng `EnziiDatabase` lên `version = 3` (xuất `app/schemas/com.enziidict.app.data.local.EnziiDatabase/3.json`), viết `MIGRATION_2_3` trong `EnziiDatabaseMigrations.kt` (tặng sẵn 1 Streak Freeze & mục tiêu `50 XP/ngày`), đăng ký trong `DatabaseModule.kt`.
   - Cập nhật đồng bộ `scripts/build_dictionary_db.py` (không dùng SQL `DEFAULT`) và chạy tái tạo `app/src/main/assets/dictionary.db` (1.046 từ + 5 bài đọc).

2. **Engine Tiến độ, Streak/Freeze & Daily Quests (`LearningProgressRepository.kt`)**:
   - Triển khai `StreakCalculator` (tính cộng dồn Streak qua ngày liên tiếp, tự động tiêu thụ `freeze_count` nếu nghỉ ngắn ngày, thưởng thêm +1 Freeze mỗi mốc 7 ngày liên tiếp tối đa 3, tạo 3 Daily Quests và dải lịch 7 ngày T2–CN).
   - Triển khai `LearningProgressRepository` với `Mutex` đồng bộ ghi DB và đồng bộ `SettingsDataStore.recordStudyActivity()`, tích hợp tự động vào `SrsViewModel`, `ShadowingViewModel`, `ReadingViewModel`, `SpeakingViewModel` và `HomeViewModel`.

3. **Nâng cấp `HomeScreen` & Màn hình Tổng kết (`LearningSummaryScreen`) + Post-Session Recap**:
   - **`HomeScreen`**: Nâng cấp thẻ Streak Hero (số ngày Streak, pill `❄️ Freeze`, thanh mục tiêu XP hôm nay, lịch 7 ngày T2–CN) cùng thẻ **Nhiệm vụ hằng ngày (3 Daily Quests)** có thanh tiến độ trực quan và chạm điều hướng thẳng tới SRS / Shadowing / AI Speaking.
   - **Post-Session Recap**: Thêm `FlashcardRecapCard` trong `FlashcardScreen` (số thẻ, % chính xác, `+XP`, Streak) và `SpeakingRecapDialog` trong `SpeakingScreen` (thời lượng, lượt nói, `+XP`, nút mở đánh giá AI).
   - **`LearningSummaryScreen`**: Chuyển đổi Tuần (7 ngày) / Tháng (30 ngày), KPI Streak/Freeze/Từ thành thạo (Mastered)/XP/Điểm mạnh nhất, biểu đồ cột trực quan theo ngày (**Số từ** & **Phút luyện tập**) và danh sách từ vựng tiêu biểu trong tuần kèm nút phát âm TTS; mở từ `HomeScreen`, `FlashcardRecapCard` hoặc icon trên `TopAppBar` của `MainActivity`.

---

## 2. Kết quả Kiểm chứng Cục bộ *(chưa commit/push theo quy tắc test trước)*

- `python scripts/build_dictionary_db.py`: Tạo lại thành công `app/src/main/assets/dictionary.db`.
- `$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"; .\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain`:
  - **`BUILD SUCCESSFUL`**, 0 lỗi biên dịch.
  - **41/41** unit & Robolectric tests đạt across **13 suites** (gồm `StudyProgressMigrationAndLogicTest` kiểm tra `MIGRATION_2_3` trên DB v2 thật, XP/mục tiêu ngày, Streak Freeze, reset khi hết Freeze, thưởng Freeze mốc 7 ngày, Daily Quests & dải lịch tuần, và cập nhật `WordFolderMigrationTest` chạy `MIGRATION_1_2 -> MIGRATION_2_3`).
- Đã cài đặt thành công `app-debug.apk` (`85,594,999` bytes) lên máy ảo `emulator-5554` (`Performing Streamed Install: Success`).
