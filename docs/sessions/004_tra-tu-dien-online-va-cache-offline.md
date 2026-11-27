# Phiên 004 — Tìm kiếm Từ điển Online (`OnlineDictionaryRepository`), Audio Bản xứ & Tự động Lưu Cache Offline

- **Ngày**: 2026-11-27
- **Mục tiêu phiên**: Bổ sung khả năng tra cứu từ điển trực tuyến thông minh vào `DictionaryScreen` & `WordDetailCard` theo cơ chế ưu tiên Offline tốc độ cao, tự động mở rộng Online khi thiếu từ hoặc khi người dùng bấm **"Mở rộng Online"**, hỗ trợ phát âm audio bản xứ và tự động cache xuống Room Database để dùng offline ở những lần sau.

---

## 1. Những gì đã hoàn thành

1. **Tầng Lưu Cache Room (`DictionaryDao`, `DictionaryRepository`)**:
   - Bổ sung `@Update updateEntry(...)` trong `DictionaryDao`.
   - Viết `DictionaryRepository.cacheOnlineEntry(...)`:
     - Nếu từ chưa có trong SQLite offline: chèn bản ghi mới vào `dictionary_entries` (`frequencyRank = 25_000`), trigger FTS4 tự động lập chỉ mục để tra lại tức thì offline và hỗ trợ lưu ngay vào `WordFolder`.
     - Nếu từ đã có trong SQLite offline: giữ nguyên `id` (bảo toàn mọi liên kết `SavedWord`), bổ sung IPA / ví dụ / từ đồng nghĩa / collocations còn thiếu rồi cập nhật tại chỗ.

2. **Trình Phát Audio Bản xứ Online (`OnlineAudioPlayer`)**:
   - Tạo `OnlineAudioPlayer` dùng `android.media.MediaPlayer` phát streaming file `.mp3` UK/US từ nguồn online (`AudioAttributes.USAGE_MEDIA` + `CONTENT_TYPE_SPEECH`).
   - Tự động fallback sang `TtsManager` nếu đường dẫn âm thanh lỗi hoặc mất mạng; giải phóng tài nguyên an toàn qua `DisposableEffect` (`ON_STOP` & `onDispose`) trên `DictionaryScreen` và `DictionaryViewModel.onCleared()`.

3. **Tầng Dữ liệu Online (`OnlineDictionaryRepository`)**:
   - Tích hợp **Free Dictionary API** (`https://api.dictionaryapi.dev/api/v2/entries/en/{word}` — miễn phí, không cần API key) lấy đầy đủ IPA UK/US, URL audio phát âm chuẩn, định nghĩa theo từng từ loại, ví dụ, từ đồng nghĩa và trái nghĩa.
   - Kết hợp dịch thuật thông minh:
     - **Nếu đã có Gemini API Key (BYOK)**: gọi Gemini dịch nghĩa tiếng Việt tự nhiên cho định nghĩa/ví dụ, sinh 2–3 collocations và cấp độ CEFR; khi Free Dictionary trả về `404` (thành ngữ, cụm từ, tiếng lóng), tự động fallback sang Gemini để tra cứu và lưu cache.
     - **Nếu chưa có API Key**: dùng `OfflineMlKitTranslator` dịch nghĩa & ví dụ chính sang tiếng Việt (giữ fallback an toàn nếu chưa tải gói ML Kit).
   - Xử lý rõ ràng trạng thái mất mạng (`OfflineError`) với thông báo tiếng Việt thân thiện, không gây crash.

4. **Nâng cấp Giao diện `DictionaryScreen` & `DictionaryViewModel`**:
   - Khi gõ từ chưa có trong kho offline (hoặc chưa có từ khớp chính xác): hiển thị thẻ nổi bật **"Tìm trực tuyến cho từ '[từ khóa]'"** (và hỗ trợ bấm phím **Search** trên bàn phím) kèm hiệu ứng loading mượt mà.
   - Trên `WordDetailCard`:
     - Gắn nhãn nguồn dữ liệu **`Offline`** / **`Online`** ngay dưới đầu từ.
     - Thêm nút **"Mở rộng Online"** cho mọi từ (kể cả từ đã có sẵn offline) để tải thêm định nghĩa Anh–Anh chuyên sâu, audio bản xứ UK/US, từ trái nghĩa (`Antonyms`) và nhiều câu ví dụ thực tế (mỗi ví dụ đều có nút **Shadow** để nhại câu ngay).
     - Hiển thị banner lỗi thân thiện kèm nút **Thử lại** khi thiết bị đang tắt mạng.

---

## 2. Các File Chính Đã Tạo / Sửa

- **Core Database, Audio & Network**:
  - `app/src/main/java/com/enziidict/app/data/local/dao/DictionaryDao.kt`
  - `app/src/main/java/com/enziidict/app/core/database/DictionaryRepository.kt`
  - `app/src/main/java/com/enziidict/app/core/audio/OnlineAudioPlayer.kt` *(mới)*
  - `app/src/main/java/com/enziidict/app/core/network/OnlineDictionaryRepository.kt` *(mới)*
- **UI & ViewModel**:
  - `app/src/main/java/com/enziidict/app/feature/dictionary/DictionaryViewModel.kt`
  - `app/src/main/java/com/enziidict/app/feature/dictionary/DictionaryScreen.kt`
- **Kiểm thử**:
  - `app/src/test/java/com/enziidict/app/core/network/OnlineDictionaryRepositoryTest.kt` *(mới)*

---

## 3. Kết quả Kiểm chứng

- `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain`: **`BUILD SUCCESSFUL`**, 0 lỗi biên dịch.
- **30/30** unit & Robolectric integration tests đạt (10 test suites), bao gồm 4 test mới trong `OnlineDictionaryRepositoryTest`:
  1. Tra từ chưa có trong kho offline (`serendipity`) qua Free Dictionary + ML Kit → lưu cache Room → tra lại offline & lưu sổ tay thành công.
  2. Fallback sang Gemini AI khi gặp thành ngữ (`break a leg`, HTTP 404 trên Free Dictionary) và lưu cache.
  3. Xử lý ngoại tuyến (`UnknownHostException`) trả về `OfflineError` tiếng Việt an toàn.
  4. Mở rộng online từ đã có sẵn offline (`decision`) bảo toàn nguyên `id` Room và dữ liệu SRS hiện hữu.
- APK Debug cập nhật mới nhất tại `%TEMP%\EnziiDictBuild\app\outputs\apk\debug\app-debug.apk` (~85.0 MB).
