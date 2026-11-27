# Phiên 005 — Cấu hình Release APK, Bảo mật Keystore, R8/ProGuard & Tối ưu ABI `arm64-v8a`

- **Ngày**: 2026-11-27
- **Mục tiêu phiên**: Thiết lập cấu hình đóng gói bản Release APK nhẹ, an toàn cho điện thoại Android thật với R8/ProGuard, ký số đọc từ `keystore.properties`, quy tắc `.gitignore` bảo mật keystore, và lọc native ABI chỉ giữ `arm64-v8a`.

---

## 1. Những gì đã hoàn thành

1. **Bảo mật Keystore & `.gitignore`**:
   - Cập nhật `.gitignore` loại trừ triệt để `*.jks`, `*.keystore`, `keystore.properties`, `local.properties`, `.gradle/`, `build/`, `/app/build`, `*.apk`, `*.aab`, `*.ap_`, `*.dex` và giữ `/.idea/` ngoài Git.
   - Tạo file mẫu `keystore.properties.example` (không chứa mật khẩu thật) kèm hướng dẫn tạo file `.jks` bằng `keytool` và điền `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

2. **Cấu hình Release trong `app/build.gradle.kts`**:
   - Tự động kiểm tra và nạp `keystore.properties` ở thư mục gốc dự án để gắn vào `signingConfigs.getByName("release")` khi tồn tại; vẫn cho phép build `app-release-unsigned.apk` sạch nếu chưa tạo keystore.
   - Bật `isMinifyEnabled = true` và `isShrinkResources = true` cho `buildTypes.release`.
   - Cấu hình `ndk { abiFilters += listOf("arm64-v8a") }` riêng cho `release`, loại bỏ `armeabi-v7a`, `x86` và `x86_64` trên bản phát hành (trong khi vẫn giữ đủ ABI ở bản `debug` để chạy trên máy ảo PC `x86_64`).

3. **Cấu hình R8/ProGuard An toàn (`app/proguard-rules.pro`)**:
   - Bảo vệ đầy đủ metadata stack trace (`SourceFile`, `LineNumberTable`) và JNI (`native <methods>`).
   - **Room Database**: `@Database`, `@Entity`, `@Fts4`, `@Dao`, `@TypeConverter`, `Migration`, `com.enziidict.app.data.local.**`, `android.database.CursorWindow`.
   - **Hilt / Dagger & WorkManager**: `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@HiltWorker`, `dagger.hilt.**`, `ViewModel`, `ListenableWorker`.
   - **Google ML Kit & Play Services**: `com.google.mlkit.**`, `@KeepForSdk` / `@KeepName`, `com.google.android.gms.**`.
   - **Kotlinx Serialization, Gemini DTOs, OkHttp, Retrofit, Gson/Moshi**: giữ `@Serializable` / `$$serializer` / `Companion.serializer()` cho `com.enziidict.app.core.model.**`, `com.enziidict.app.core.network.**`, `ShadowingModels**` và các `-dontwarn` chuẩn của OkHttp/Retrofit/Okio.
   - **AndroidX Security Crypto & Google Tink**: giữ `androidx.security.crypto.**` và `com.google.crypto.tink.**` (bao gồm `shaded.protobuf.GeneratedMessageLite`) để `EncryptedSharedPreferences` không bị R8 xóa class khi giải mã AES-256.

---

## 2. Kết quả Kiểm chứng

- Chạy `.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease --no-daemon --console=plain`:
  - **`BUILD SUCCESSFUL`**, 0 lỗi, `:app:minifyReleaseWithR8`, `:app:lintVitalRelease`, `:app:shrinkReleaseRes`, `:app:packageRelease` đều thông qua.
  - **Dung lượng giảm ~76%**:
    - `app-debug.apk`: ~85.0 MB (`85,044,173` bytes) với đủ 4 ABI.
    - `app-release-unsigned.apk`: **~20.0 MB (`20,046,986` bytes)**, kiểm tra cấu trúc ZIP `lib/` chỉ còn duy nhất `lib/arm64-v8a/` (`libtranslate_jni.so` 15.6 MB, `libandroidx.graphics.path.so` 0.01 MB, `libdatastore_shared_counter.so` 0.01 MB).
