package com.enziidict.app.core.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.enziidict.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val publishedAt: String,
    val htmlUrl: String
)

sealed interface UpdateCheckOutcome {
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateCheckOutcome
    data class UpToDate(val currentVersion: String) : UpdateCheckOutcome
    data class NoReleaseYet(val messageVi: String) : UpdateCheckOutcome
    data class Error(val messageVi: String) : UpdateCheckOutcome
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val progressPercent: Int,
        val downloadedMb: Double,
        val totalMb: Double
    ) : UpdateDownloadState

    data class ReadyToInstall(
        val apkFile: File,
        val versionName: String
    ) : UpdateDownloadState

    data class Failed(val messageVi: String) : UpdateDownloadState
}

@Singleton
class AppUpdateManager internal constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val getExecutor: OnlineHttpGetExecutor,
    private val okHttpClient: OkHttpClient
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        json: Json
    ) : this(
        context = context,
        json = json,
        getExecutor = defaultGithubGetExecutor(defaultClient()),
        okHttpClient = defaultClient()
    )

    suspend fun checkLatestRelease(
        currentVersionName: String = BuildConfig.VERSION_NAME,
        owner: String = BuildConfig.GITHUB_OWNER,
        repo: String = BuildConfig.GITHUB_REPO
    ): UpdateCheckOutcome {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val resp = try {
            withContext(Dispatchers.IO) { getExecutor.get(url) }
        } catch (_: UnknownHostException) {
            return UpdateCheckOutcome.Error(
                "Không có kết nối Internet để kiểm tra cập nhật."
            )
        } catch (_: SocketTimeoutException) {
            return UpdateCheckOutcome.Error(
                "Kết nối tới GitHub quá thời gian chờ. Vui lòng thử lại."
            )
        } catch (e: IOException) {
            return UpdateCheckOutcome.Error(
                "Lỗi mạng khi kiểm tra cập nhật (${e.message ?: "IO error"})."
            )
        }

        return when {
            resp.statusCode == 404 -> {
                UpdateCheckOutcome.NoReleaseYet(
                    "Chưa có bản phát hành (Release) nào trên GitHub. Hãy push tag v0.2.0 hoặc chạy workflow Release trên GitHub."
                )
            }
            resp.statusCode !in 200..299 -> {
                UpdateCheckOutcome.Error(
                    "GitHub API phản hồi mã lỗi ${resp.statusCode}. Vui lòng thử lại sau."
                )
            }
            else -> parseAndCompareRelease(resp.body, currentVersionName)
        }
    }

    internal fun parseAndCompareRelease(
        rawBody: String,
        currentVersionName: String
    ): UpdateCheckOutcome {
        val release = parseReleaseJson(rawBody)
            ?: return UpdateCheckOutcome.Error("Không tìm thấy file APK hợp lệ trong bản Release mới nhất trên GitHub.")

        return if (isNewerVersion(release.versionName, currentVersionName)) {
            UpdateCheckOutcome.UpdateAvailable(release)
        } else {
            UpdateCheckOutcome.UpToDate(currentVersionName)
        }
    }

    internal fun parseReleaseJson(rawBody: String): AppReleaseInfo? {
        return runCatching {
            val root = json.parseToJsonElement(rawBody).jsonObject
            val tagName = root.str("tag_name").orEmpty()
            val versionName = normalizeVersion(tagName)
            if (versionName.isBlank()) return null

            val assets = root["assets"]?.jsonArray.orEmpty()
                .mapNotNull { el -> runCatching { el.jsonObject }.getOrNull() }
                .filter { obj ->
                    val name = obj.str("name").orEmpty().lowercase()
                    name.endsWith(".apk") && !name.contains("unsigned")
                }

            val preferredAsset = assets.firstOrNull { obj ->
                val n = obj.str("name").orEmpty().lowercase()
                n.contains("arm64") || n.contains("release")
            } ?: assets.firstOrNull()
            ?: return null

            val downloadUrl = preferredAsset.str("browser_download_url").orEmpty()
            if (downloadUrl.isBlank()) return null

            val sizeBytes = preferredAsset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            AppReleaseInfo(
                tagName = tagName,
                versionName = versionName,
                releaseNotes = root.str("body").orEmpty().trim(),
                apkDownloadUrl = downloadUrl,
                apkSizeBytes = sizeBytes,
                publishedAt = root.str("published_at").orEmpty(),
                htmlUrl = root.str("html_url").orEmpty()
            )
        }.getOrNull()
    }

    suspend fun downloadApk(
        release: AppReleaseInfo,
        onProgress: (percent: Int, downloadedMb: Double, totalMb: Double) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { oldFile ->
                if (oldFile.isFile && oldFile.name.endsWith(".apk")) {
                    oldFile.delete()
                }
            }

            val safeVersion = release.versionName.replace(Regex("[^0-9A-Za-z._-]"), "_")
            val targetFile = File(updatesDir, "EnziiDict-$safeVersion.apk")

            val request = Request.Builder()
                .url(release.apkDownloadUrl)
                .header("User-Agent", "EnziiDict-Android")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Tải APK thất bại (HTTP ${response.code})")
                }
                val body = response.body ?: throw IOException("Phản hồi tải APK trống.")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes
                val totalMb = totalBytes / (1024.0 * 1024.0)

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead = input.read(buffer)
                        var downloaded = 0L
                        var lastReportedPct = -1

                        while (bytesRead >= 0) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            val pct = if (totalBytes > 0L) {
                                ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            if (pct != lastReportedPct || downloaded == totalBytes) {
                                lastReportedPct = pct
                                onProgress(pct, downloaded / (1024.0 * 1024.0), totalMb)
                            }
                            bytesRead = input.read(buffer)
                        }
                        output.flush()
                    }
                }
            }
            targetFile
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createUnknownSourcesSettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun createInstallApkIntent(apkFile: File): Intent {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun JsonObject.str(key: String): String? {
        val el = this[key] ?: return null
        return runCatching { el.jsonPrimitive.content }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    companion object {
        fun normalizeVersion(raw: String): String {
            return raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
                .trim()
        }

        fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
            val rParts = normalizeVersion(remoteVersion)
                .split('.')
                .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val cParts = normalizeVersion(currentVersion)
                .split('.')
                .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

            val maxLen = maxOf(rParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        private fun defaultGithubGetExecutor(client: OkHttpClient): OnlineHttpGetExecutor {
            return OnlineHttpGetExecutor { url ->
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "EnziiDict-Android")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    HttpPostResponse(
                        statusCode = resp.code,
                        body = resp.body?.string().orEmpty()
                    )
                }
            }
        }
    }
}
