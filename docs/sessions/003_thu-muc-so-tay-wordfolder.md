# Phiên 003 — Nâng cấp Thư mục Phân loại Sổ tay (`WordFolder`), Migration Room v1→v2 & `SaveToFolderBottomSheet`

- **Ngày**: 2026-11-27
- **Mục tiêu phiên**: Nâng cấp tính năng lưu từ vựng vào Sổ tay cho phép chọn hoặc tạo nhanh thư mục phân loại ngay tại lúc bấm lưu (từ Chi tiết từ & Popup tra nhanh `ACTION_PROCESS_TEXT`), chuẩn hóa bảng `word_folders` + khóa ngoại `folder_id` trong Room v2 kèm migration an toàn, và bổ sung thanh thẻ thư mục lọc & ôn tập Flashcard SRS riêng theo chủ đề trên `FlashcardScreen`.

---

## 1. Các Quyết định Kỹ thuật & Hạng mục Đã Hoàn thành

1. **Tầng Database Room v2 & Migration An toàn (`com.enziidict.app.data.local`)**:
   - Tạo Entity `WordFolder` (`id`, `name`, `icon_name`, `color_hex`, `created_at` + unique index `idx_word_folders_name_unique`) và `WordFolderDao` (lấy danh sách thư mục kèm `word_count` & `due_count`, thêm/đổi tên/xóa thư mục; luôn giữ và bảo vệ thư mục `"Mặc định"`).
   - Cập nhật Entity `SavedWord` bổ sung `folder_id` (`ForeignKey` → `word_folders(id)` `ON DELETE CASCADE`) và giữ đồng bộ `folder_name` khi đổi tên thư mục; cập nhật các index `idx_saved_words_unique_entry_folder_id`, `idx_saved_words_due_queue`, `idx_saved_words_entry_id`.
   - Viết `EnziiDatabaseMigrations.MIGRATION_1_2` di trú an toàn từ schema v1 → v2: tự động tạo `word_folders`, nạp 3 thư mục chuẩn (*Mặc định*, *Oxford 3000*, *Từ lưu từ hội thoại*) + mọi thư mục tự tạo đã tồn tại trong `saved_words` v1, ánh xạ chính xác `folder_id` mà **không mất bất kỳ từ đã lưu hay tiến độ SM-2 nào**.
   - Cập nhật đồng bộ `scripts/build_dictionary_db.py` theo schema v2 và build lại `app/src/main/assets/dictionary.db` (1.046 từ, 3 thư mục chuẩn, 7 thẻ mẫu, 5 bài đọc).

2. **Giao diện Chọn / Tạo nhanh Thư mục (`SaveToFolderBottomSheet`)**:
   - Tạo component dùng chung `SaveToFolderBottomSheet` hiển thị:
     - Nút **"+ Tạo thư mục mới"** ngay trên đầu sheet: nhập tên thư mục (VD: *"Động vật"*), chọn màu thẻ và bấm **"Tạo & Lưu ngay"** chỉ với 1 chạm.
     - Danh sách các thư mục hiện có kèm số từ, số thẻ đến hạn và dấu tích xanh (`CheckCircle`) nếu từ đã nằm trong thư mục đó.
     - Nút **"Bỏ lưu khỏi sổ tay"** khi từ đang được lưu để gỡ nhanh.
   - Tích hợp vào cả **Chi tiết từ (`DictionaryScreen` / `DictionaryViewModel`)** và **Popup tra nhanh (`QuickLookupActivity` / `QuickLookupViewModel`)**.
   - Đổi trạng thái nút Lưu hiển thị rõ tên thư mục đang lưu (VD: **`Đã lưu • Động vật`** hoặc **`Đã lưu • Động vật (+1)`**), bấm lại để chuyển/thêm thư mục hoặc bỏ lưu.
   - Đồng bộ `ReadingViewModel` đọc danh sách thư mục trực tiếp từ `WordFolderDao`.

3. **Nâng cấp Màn hình Sổ tay (`FlashcardScreen` & `SrsViewModel`)**:
   - Hiển thị **dải thẻ thư mục nằm ngang (`LazyRow`)** ngay trên đầu màn hình (*Tất cả*, *Mặc định*, *Oxford 3000*, *Từ lưu từ hội thoại*, và các thư mục người dùng tự tạo) kèm màu nhấn, tổng số từ và huy hiệu số thẻ đến hạn.
   - Cho phép lọc danh sách từ vựng trực tiếp theo từng thư mục, đổi tên / xóa thư mục tự tạo (bảo vệ thư mục *"Mặc định"*) và bấm **"Ôn (X)"** để vào chế độ Flashcard 3D SM-2 riêng cho từng thư mục.

---

## 2. Danh sách File Chính Đã Tạo / Sửa

- **Database & Migration**:
  - `app/src/main/java/com/enziidict/app/data/local/entity/WordFolder.kt` *(mới)*
  - `app/src/main/java/com/enziidict/app/data/local/entity/SavedWord.kt`
  - `app/src/main/java/com/enziidict/app/data/local/dao/WordFolderDao.kt` *(mới)*
  - `app/src/main/java/com/enziidict/app/data/local/dao/SavedWordDao.kt`
  - `app/src/main/java/com/enziidict/app/data/local/EnziiDatabaseMigrations.kt` *(mới)*
  - `app/src/main/java/com/enziidict/app/data/local/EnziiDatabase.kt` (version = 2)
  - `app/src/main/java/com/enziidict/app/data/local/DatabaseModule.kt`
  - `app/schemas/com.enziidict.app.data.local.EnziiDatabase/2.json` *(xuất tự động)*
  - `scripts/build_dictionary_db.py` & `app/src/main/assets/dictionary.db`
- **Repository & UI**:
  - `app/src/main/java/com/enziidict/app/core/database/DictionaryRepository.kt`
  - `app/src/main/java/com/enziidict/app/ui/components/SaveToFolderBottomSheet.kt` *(mới)*
  - `app/src/main/java/com/enziidict/app/feature/dictionary/{DictionaryViewModel,DictionaryScreen}.kt`
  - `app/src/main/java/com/enziidict/app/feature/quicklookup/QuickLookupActivity.kt`
  - `app/src/main/java/com/enziidict/app/feature/reading/ReadingViewModel.kt`
  - `app/src/main/java/com/enziidict/app/feature/srs/{SrsViewModel,FlashcardScreen}.kt`
- **Kiểm thử**:
  - `app/src/test/java/com/enziidict/app/data/local/WordFolderMigrationTest.kt` *(mới)*
  - `app/src/test/java/com/enziidict/app/feature/srs/SrsAndReadingFlowTest.kt`
  - `app/src/test/java/com/enziidict/app/feature/shadowing/ShadowingChunkerTest.kt`
  - `app/src/test/java/com/enziidict/app/feature/speaking/SpeakingEvaluationAndHistoryTest.kt`

---

## 3. Kết quả Kiểm chứng

- `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain`: **BUILD SUCCESSFUL**, 0 lỗi biên dịch.
- **26/26** unit & Robolectric integration tests đạt (9 test suites), bao gồm `WordFolderMigrationTest` kiểm chứng trực tiếp di trú SQLite thực tế từ schema v1 sang Room v2 bảo toàn toàn bộ từ đã lưu, tiến độ SM-2 và thư mục tự tạo.
- File APK Debug mới nhất sẵn sàng tại `%TEMP%\EnziiDictBuild\app\outputs\apk\debug\app-debug.apk`.
