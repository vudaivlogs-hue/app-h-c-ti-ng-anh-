package com.enziidict.app.core.network

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class OfflineMlKitTranslator @Inject constructor() {

    private val modelManager: RemoteModelManager by lazy { RemoteModelManager.getInstance() }
    private val enModel by lazy { TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build() }
    private val viModel by lazy { TranslateRemoteModel.Builder(TranslateLanguage.VIETNAMESE).build() }

    suspend fun isEnViPackDownloaded(): Boolean {
        return runCatching {
            val enReady = modelManager.isModelDownloaded(enModel).awaitTask()
            val viReady = modelManager.isModelDownloaded(viModel).awaitTask()
            enReady && viReady
        }.getOrDefault(false)
    }

    suspend fun downloadEnViPack(): Result<Unit> {
        return runCatching {
            val conditions = DownloadConditions.Builder().build()
            modelManager.download(enModel, conditions).awaitTask()
            modelManager.download(viModel, conditions).awaitTask()
            Unit
        }
    }

    suspend fun deleteEnViPack(): Result<Unit> {
        return runCatching {
            modelManager.deleteDownloadedModel(enModel).awaitTask()
            modelManager.deleteDownloadedModel(viModel).awaitTask()
            Unit
        }
    }

    suspend fun translate(text: String, enToVi: Boolean): Result<String> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return Result.success("")

        val sourceLang = if (enToVi) TranslateLanguage.ENGLISH else TranslateLanguage.VIETNAMESE
        val targetLang = if (enToVi) TranslateLanguage.VIETNAMESE else TranslateLanguage.ENGLISH
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)

        return try {
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).awaitTask()
            val translated = translator.translate(cleaned).awaitTask()
            Result.success(translated)
        } catch (t: Throwable) {
            val downloaded = isEnViPackDownloaded()
            val friendly = if (!downloaded) {
                "Chưa có gói ngôn ngữ offline Anh – Việt và không thể tải do mất mạng. Hãy kết nối mạng rồi vào Cài đặt để tải gói offline trước."
            } else {
                "Lỗi dịch ML Kit Offline: ${t.localizedMessage ?: "Không xác định"}"
            }
            Result.failure(IllegalStateException(friendly, t))
        } finally {
            translator.close()
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exc ->
            if (cont.isActive) cont.resumeWithException(exc)
        }
        addOnCanceledListener {
            cont.cancel()
        }
    }
}
