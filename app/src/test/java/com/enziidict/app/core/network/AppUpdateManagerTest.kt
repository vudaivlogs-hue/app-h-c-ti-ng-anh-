package com.enziidict.app.core.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createManager(executor: OnlineHttpGetExecutor): AppUpdateManager {
        return AppUpdateManager(
            context = context,
            json = json,
            getExecutor = executor,
            okHttpClient = OkHttpClient()
        )
    }

    @Test
    fun `isNewerVersion accurately compares semantic versions including multi-digit segments`() {
        assertTrue(AppUpdateManager.isNewerVersion("v0.2.0", "0.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("0.10.0", "0.2.0"))
        assertTrue(AppUpdateManager.isNewerVersion("V1.0.0", "0.9.9"))
        assertFalse(AppUpdateManager.isNewerVersion("0.1.0", "0.1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("0.1.0", "0.2.0"))
    }

    @Test
    fun `checkLatestRelease picks signed arm64 apk asset over unsigned apk and returns UpdateAvailable`() = runTest {
        val sampleReleaseJson = """
            {
              "tag_name": "v0.2.0",
              "html_url": "https://github.com/vudaivlogs-hue/app-h-c-ti-ng-anh-/releases/tag/v0.2.0",
              "published_at": "2026-11-28T01:00:00Z",
              "body": "- Bổ sung tra từ điển Online & tự động lưu cache\n- Nâng cấp thư mục Sổ tay WordFolder",
              "assets": [
                {
                  "name": "app-release-unsigned.apk",
                  "size": 20000000,
                  "browser_download_url": "https://example.com/unsigned.apk"
                },
                {
                  "name": "EnziiDict-v0.2.0-arm64-v8a.apk",
                  "size": 20123456,
                  "browser_download_url": "https://example.com/EnziiDict-v0.2.0-arm64-v8a.apk"
                }
              ]
            }
        """.trimIndent()

        val manager = createManager { HttpPostResponse(200, sampleReleaseJson) }
        val outcome = manager.checkLatestRelease(currentVersionName = "0.1.0")

        assertTrue("Expected UpdateAvailable, got $outcome", outcome is UpdateCheckOutcome.UpdateAvailable)
        val release = (outcome as UpdateCheckOutcome.UpdateAvailable).release
        assertEquals("0.2.0", release.versionName)
        assertEquals("https://example.com/EnziiDict-v0.2.0-arm64-v8a.apk", release.apkDownloadUrl)
        assertEquals(20123456L, release.apkSizeBytes)
    }

    @Test
    fun `checkLatestRelease returns UpToDate when remote version matches current version`() = runTest {
        val sampleReleaseJson = """
            {
              "tag_name": "v0.1.0",
              "html_url": "https://example.com/rel",
              "published_at": "2026-11-27T00:00:00Z",
              "body": "Initial release",
              "assets": [
                {
                  "name": "EnziiDict-v0.1.0-arm64-v8a.apk",
                  "size": 20046986,
                  "browser_download_url": "https://example.com/EnziiDict-v0.1.0-arm64-v8a.apk"
                }
              ]
            }
        """.trimIndent()

        val manager = createManager { HttpPostResponse(200, sampleReleaseJson) }
        val outcome = manager.checkLatestRelease(currentVersionName = "0.1.0")
        assertTrue(outcome is UpdateCheckOutcome.UpToDate)
    }

    @Test
    fun `checkLatestRelease returns NoReleaseYet on HTTP 404`() = runTest {
        val manager = createManager { HttpPostResponse(404, """{"message":"Not Found"}""") }
        val outcome = manager.checkLatestRelease(currentVersionName = "0.1.0")
        assertTrue(outcome is UpdateCheckOutcome.NoReleaseYet)
    }

    @Test
    fun `checkLatestRelease returns friendly Vietnamese Error when offline`() = runTest {
        val manager = createManager { throw UnknownHostException("api.github.com") }
        val outcome = manager.checkLatestRelease(currentVersionName = "0.1.0")
        assertTrue(outcome is UpdateCheckOutcome.Error)
        assertTrue((outcome as UpdateCheckOutcome.Error).messageVi.contains("Internet"))
    }
}
