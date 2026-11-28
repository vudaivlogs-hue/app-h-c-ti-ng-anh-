# Phiên 012 — Nạp Bộ Từ điển FVDP Anh–Việt 109K+ (`113.933` mục từ) & Cơ chế Nâng cấp An toàn

- **Ngày**: 2026-11-28
- **Mục tiêu phiên**: Tải bộ dữ liệu mã nguồn mở FVDP `anhviet109K.txt`, nâng cấp parser và build `dictionary.db` (Phương án A — nhúng sẵn 100% offline), bổ sung `DictionaryPackUpgrader` tự động nâng cấp cho máy đã cài bản cũ mà giữ nguyên toàn bộ Sổ tay SRS / Streak, và tối ưu tốc độ truy vấn 3 tầng trên 100K+ từ.

---

## 1. Những gì đã hoàn thành

1. **Tải dữ liệu & Nâng cấp `scripts/build_dictionary_db.py`**:
   - Tải `anhviet109K.txt` (`14,828,109` bytes) từ `yenthanh132/avdict-database-sqlite-converter` về `tools/dict-builder/anhviet109K.txt` và loại trừ khỏi Git tại `.gitignore` (`tools/dict-builder/anhviet109K.*`).
   - Nâng cấp `parse_fvdp(...)`:
     - Chuẩn hóa từ loại tiếng Việt sang chuẩn ngắn (`noun`, `verb`, `adjective`, `adverb`, `preposition`, `conjunction`, `pronoun`, `interjection`, `determiner`, `idiom`, `phrasal verb`, `phrase`).
     - Tách riêng từng mục theo từ loại `* ...` trong cùng một `@word` thay vì ghi đè, bóc tách `!` idioms sang `collocations`, cắt gọn tối đa 4 nghĩa & 2 ví dụ/mục để giao diện gọn đẹp.
     - Ưu tiên `frequency_rank` (`1..2000` cho bộ 1.046 từ seed/CEFR, `5000+` cho từ đơn FVDP, `15000+` cho cụm từ) và khử trùng theo `(word_normalized, pos)`.
   - Tăng tốc build SQLite bằng `executemany`, tạm bỏ trigger `AFTER_INSERT` khi chèn hàng loạt, `rebuild` FTS4 1 lần rồi tạo lại trigger & `ANALYZE`/`VACUUM`.
   - Kết quả: Đã phân tích `121.272` mục từ FVDP → đóng gói thành **`app/src/main/assets/dictionary.db`** với **`113.933` mục từ** + `5` bài đọc song ngữ, tổng dung lượng chỉ **`33,607,680` bytes (`32.05 MB`)**.

2. **Nâng cấp An toàn cho Máy Đã Cài Bản Cũ (`DictionaryPackUpgrader.kt`)**:
   - Tạo `DictionaryPackUpgrader` (`@Singleton`) kiểm tra khi mở app (`MainActivity.LaunchedEffect`): nếu DB hiện tại có `< 50.000` từ, tự copy tạm `assets/dictionary.db` rồi chạy `ATTACH DATABASE` + `INSERT ... WHERE NOT EXISTS` + rebuild `dictionary_fts` + khôi phục trigger `AFTER_INSERT` trong 1 transaction.
   - Bảo toàn **100%** `saved_words`, `word_folders`, `daily_study_logs`, `user_streak_state`, `speaking_sessions` và từ đã cache online trước đó.
   - Gắn trạng thái `isUpgradingDictionaryPack` & `dictionaryEntryCount` vào `DictionaryViewModel` và banner tiến độ gọn nhẹ trên `DictionaryScreen` (tự làm mới danh sách gợi ý ngay khi nạp xong).

3. **Tối ưu Tốc độ Truy vấn 100K+ Từ (`DictionaryRepository.kt`)**:
   - Tầng 1 (`searchPrefix` qua B-Tree `idx_dict_word_prefix`) giữ nguyên tốc độ tức thì.
   - Tầng 2 (`searchFullText`) chạy khi query `>= 2` ký tự và chưa đủ `limit`.
   - Tầng 3 (`searchByMeaning` dùng `LIKE`) chỉ kích hoạt khi `cleaned.length >= 2`, `results.size < 5` và có ký tự Unicode tiếng Việt hoặc chưa có kết quả nào — tránh quét toàn bảng 113K dòng khi người dùng đang gõ tiếng Anh.

---

## 2. Kết quả Kiểm chứng & Benchmark *(chưa commit/push theo quy tắc test trước)*

- Tạo test mới `app/src/test/java/com/enziidict/app/data/local/LargeDictionaryPackTest.kt`:
  - Đo trực tiếp trên `dictionary.db` (`113.933` mục từ): `prefixMs = 29ms`, tìm từ chuyên ngành FVDP `"photosynthesis"` = `11ms`, tra ngược tiếng Việt `"kiên cường"` = `13ms`.
  - Xác minh `DictionaryPackUpgrader.upgradeFromFile` hợp nhất đủ từ mới, giữ bản curated cũ và không làm mất `SavedWord` hay `UserStreakState`.
- Cập nhật `OnlineDictionaryRepositoryTest.kt` đổi từ test mẫu sang `"doomscrolling"` (do `"serendipity"` đã có sẵn trong kho 113K offline!).
- `$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"; .\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain`:
  - **`BUILD SUCCESSFUL`**, **`45/45`** unit & Robolectric tests đạt across **`15` suites** (`FAIL=0`).
  - Debug APK: `98,801,029` bytes (`94.22 MB`).
