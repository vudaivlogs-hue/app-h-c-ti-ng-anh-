# =====================================================================
# Enzii Dict — R8 / ProGuard Configuration
# =====================================================================

# 1. General debugging & reflection metadata
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Preserve native JNI bindings
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2. Room Database (Entities, FTS4, DAOs, Migrations, CursorWindow)
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Fts4 class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * implements androidx.room.migration.Migration { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
-keep class com.enziidict.app.data.local.** { *; }
-keep class android.database.CursorWindow { *; }
-dontwarn androidx.room.paging.**

# 3. Hilt / Dagger & WorkManager
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn dagger.hilt.**

# 4. Google ML Kit (Translation / Play Services / Native reflection)
-keep class com.google.mlkit.** { *; }
-keepclassmembers class * {
    @com.google.android.gms.common.annotation.KeepForSdk *;
    @com.google.android.gms.common.annotation.KeepName *;
}
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**

# 5. Kotlinx Serialization, Gemini API DTOs, OkHttp & Retrofit
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.enziidict.app.**$$serializer { *; }
-keepclassmembers class com.enziidict.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.enziidict.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.enziidict.app.** { *; }
-keep class com.enziidict.app.core.model.** { *; }
-keep class com.enziidict.app.core.network.** { *; }
-keep class com.enziidict.app.feature.shadowing.ShadowingModels** { *; }

# Retrofit & OkHttp
-keepattributes Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Optional Gson / Moshi reflection safety (in case transitive deps invoke them)
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.squareup.moshi.Json <fields>;
}
-dontwarn com.google.gson.**
-dontwarn com.squareup.moshi.**

# 6. AndroidX Security Crypto (EncryptedSharedPreferences) & Google Tink
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
    <init>(...);
}
-dontwarn com.google.crypto.tink.**
-dontwarn androidx.security.crypto.**
