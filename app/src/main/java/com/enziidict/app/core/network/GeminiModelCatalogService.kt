package com.enziidict.app.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class GeminiModelInfo(
    val id: String,
    val fullName: String,
    val displayName: String,
    val supportedMethods: List<String>
)

data class GeminiModelCatalog(
    val textModels: List<GeminiModelInfo>,
    val liveAudioModels: List<GeminiModelInfo>,
    val recommendedTextModel: String?,
    val recommendedLiveModel: String?
)

sealed interface ModelDiscoveryOutcome {
    data class Success(val catalog: GeminiModelCatalog) : ModelDiscoveryOutcome
    data object MissingApiKey : ModelDiscoveryOutcome
    data class NetworkError(val messageVi: String) : ModelDiscoveryOutcome
    data class ApiError(val httpCode: Int, val messageVi: String) : ModelDiscoveryOutcome
}

@Singleton
class GeminiModelCatalogService internal constructor(
    private val json: Json,
    private val getExecutor: OnlineHttpGetExecutor
) {
    @Inject
    constructor(json: Json) : this(json, defaultGetExecutor())

    private val cacheLock = Any()
    private var cachedKeyHash: Int? = null
    private var cachedCatalog: GeminiModelCatalog? = null

    suspend fun discoverModels(
        apiKey: String,
        forceRefresh: Boolean = false
    ): ModelDiscoveryOutcome {
        val cleanedKey = apiKey.trim()
        if (cleanedKey.isEmpty()) {
            return ModelDiscoveryOutcome.MissingApiKey
        }

        val keyHash = cleanedKey.hashCode()
        if (!forceRefresh) {
            synchronized(cacheLock) {
                if (cachedKeyHash == keyHash && cachedCatalog != null) {
                    return ModelDiscoveryOutcome.Success(cachedCatalog!!)
                }
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val allModels = mutableListOf<GeminiModelInfo>()
                var nextPageToken: String? = null
                var pagesFetched = 0

                do {
                    val encodedKey = URLEncoder.encode(cleanedKey, "UTF-8")
                    val pageParam = nextPageToken?.let {
                        "&pageToken=${URLEncoder.encode(it, "UTF-8")}"
                    }.orEmpty()
                    val url = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=$encodedKey$pageParam"

                    val resp = getExecutor.get(url)
                    if (resp.statusCode !in 200..299) {
                        val googleMsg = extractApiErrorMessage(json, resp.body)
                        val detailSuffix = googleMsg?.let { " Chi tiết: ${truncateDetail(it)}" }.orEmpty()
                        val msgVi = when (resp.statusCode) {
                            400, 401, 403 ->
                                "API Key không hợp lệ hoặc chưa bật Gemini API (HTTP ${resp.statusCode}).$detailSuffix"
                            429 ->
                                "API Key tạm thời vượt hạn mức truy vấn (HTTP 429).$detailSuffix"
                            else ->
                                "Không thể tải danh sách model từ Gemini (HTTP ${resp.statusCode}).$detailSuffix"
                        }
                        return@withContext ModelDiscoveryOutcome.ApiError(resp.statusCode, msgVi)
                    }

                    val page = parseModelsPage(resp.body)
                    allModels += page.models
                    nextPageToken = page.nextPageToken
                    pagesFetched++
                } while (!nextPageToken.isNullOrBlank() && pagesFetched < MAX_PAGES)

                val catalog = buildCatalog(allModels.distinctBy { it.id })
                synchronized(cacheLock) {
                    cachedKeyHash = keyHash
                    cachedCatalog = catalog
                }
                ModelDiscoveryOutcome.Success(catalog)
            } catch (_: UnknownHostException) {
                ModelDiscoveryOutcome.NetworkError(
                    "Không có kết nối Internet để kiểm tra danh sách model Gemini."
                )
            } catch (_: SocketTimeoutException) {
                ModelDiscoveryOutcome.NetworkError(
                    "Kết nối tới máy chủ Gemini quá thời gian chờ. Vui lòng thử lại."
                )
            } catch (e: IOException) {
                ModelDiscoveryOutcome.NetworkError(
                    "Lỗi mạng khi tải danh sách model (${e.message ?: "IO error"})."
                )
            }
        }
    }

    internal fun clearCache() {
        synchronized(cacheLock) {
            cachedKeyHash = null
            cachedCatalog = null
        }
    }

    internal data class ParsedModelsPage(
        val models: List<GeminiModelInfo>,
        val nextPageToken: String?
    )

    internal fun parseModelsPage(rawJson: String): ParsedModelsPage {
        return runCatching {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val token = root["nextPageToken"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
            val items = root["models"]?.jsonArray.orEmpty().mapNotNull { el ->
                val obj = el.jsonObject
                val fullNameRaw = obj["name"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (fullNameRaw.isEmpty()) return@mapNotNull null
                val fullName = normalizeFullModelName(fullNameRaw)
                val id = normalizeModelId(fullName)
                val displayName = obj["displayName"]?.jsonPrimitive?.content?.trim().orEmpty().ifEmpty { id }
                val methods = obj["supportedGenerationMethods"]?.jsonArray.orEmpty().mapNotNull { m ->
                    runCatching { m.jsonPrimitive.content.trim() }.getOrNull()?.takeIf { it.isNotBlank() }
                }
                GeminiModelInfo(
                    id = id,
                    fullName = fullName,
                    displayName = displayName,
                    supportedMethods = methods
                )
            }
            ParsedModelsPage(items, token)
        }.getOrElse { ParsedModelsPage(emptyList(), null) }
    }

    internal fun buildCatalog(rawModels: List<GeminiModelInfo>): GeminiModelCatalog {
        val allGemini = rawModels.filter { it.id.lowercase().startsWith("gemini") }

        val strictText = allGemini.filter { model ->
            val id = model.id.lowercase()
            model.supportedMethods.contains("generateContent") &&
                TEXT_EXCLUDED_TOKENS.none { token -> id.contains(token) }
        }
        val textPool = if (strictText.isNotEmpty()) {
            strictText
        } else {
            allGemini.filter { it.supportedMethods.contains("generateContent") }
        }
        val rankedText = textPool.sortedWith(
            compareByDescending<GeminiModelInfo> { scoreTextModel(it.id) }
                .thenBy { it.id }
        )

        val livePool = allGemini.filter { model ->
            model.supportedMethods.contains("bidiGenerateContent")
        }
        val rankedLive = livePool.sortedWith(
            compareByDescending<GeminiModelInfo> { scoreLiveModel(it.id) }
                .thenBy { it.id }
        )

        return GeminiModelCatalog(
            textModels = rankedText,
            liveAudioModels = rankedLive,
            recommendedTextModel = rankedText.firstOrNull()?.id,
            recommendedLiveModel = rankedLive.firstOrNull()?.fullName
        )
    }

    private fun scoreTextModel(rawId: String): Long {
        val id = rawId.lowercase()
        var score = 0L

        val versionScore = parseVersionScore(id)
        score += versionScore * 1_000L

        if (id == "gemini-flash-latest") score += 2_600L
        if (id == "gemini-flash-lite-latest") score += 1_800L
        if (id == "gemini-pro-latest") score += 1_200L

        when {
            id.contains("flash") && !id.contains("lite") -> score += 2_000L
            id.contains("flash-lite") || id.contains("lite") -> score += 1_400L
            id.contains("pro") -> score += 900L
        }

        if (!id.contains("preview") && !id.contains("exp")) {
            score += 400L
        }
        if (id.contains("-exp")) {
            score -= 250L
        }
        // Retired 2.0 family should stay below newer generations when both appear in catalogs.
        if (id.startsWith("gemini-2.0")) {
            score -= 5_000L
        }
        // Experimental thinking / nano / custom variants stay below main Flash models.
        if (id.contains("thinking") || id.contains("customtools")) {
            score -= 300L
        }
        return score
    }

    private fun scoreLiveModel(rawId: String): Long {
        val id = rawId.lowercase()
        var score = 0L
        score += parseVersionScore(id) * 1_000L
        if (id.contains("native-audio") || id.contains("live")) score += 2_000L
        if (!id.contains("exp")) score += 300L
        score += parseDateSuffixScore(id)
        if (id.startsWith("gemini-2.0")) score -= 3_000L
        return score
    }

    private fun parseVersionScore(id: String): Long {
        val match = VERSION_REGEX.find(id) ?: return 10L
        val major = match.groupValues[1].toLongOrNull() ?: 1L
        val minor = match.groupValues[2].toLongOrNull() ?: 0L
        return major * 100L + minor
    }

    private fun parseDateSuffixScore(id: String): Long {
        val match = DATE_MM_YYYY_REGEX.find(id)
        if (match != null) {
            val month = match.groupValues[1].toLongOrNull() ?: 0L
            val year = match.groupValues[2].toLongOrNull() ?: 0L
            return year * 12L + month
        }
        return 0L
    }

    companion object {
        private const val MAX_PAGES = 3
        private val VERSION_REGEX = Regex("""gemini-(\d+)(?:\.(\d+))?""")
        private val DATE_MM_YYYY_REGEX = Regex("""-(\d{2})-(\d{4})$""")
        private val TEXT_EXCLUDED_TOKENS = listOf(
            "embedding",
            "imagen",
            "veo",
            "aqa",
            "lyria",
            "tts",
            "native-audio",
            "live",
            "robotics",
            "computer-use",
            "deep-research"
        )

        fun normalizeModelId(raw: String): String =
            raw.trim().removePrefix("models/").trim()

        fun normalizeFullModelName(raw: String): String {
            val id = normalizeModelId(raw)
            return if (id.isEmpty()) "" else "models/$id"
        }

        fun extractApiErrorMessage(json: Json, rawBody: String): String? {
            if (rawBody.isBlank()) return null
            return runCatching {
                val root = json.parseToJsonElement(rawBody).jsonObject
                root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        fun truncateDetail(detail: String, maxChars: Int = 180): String {
            val singleLine = detail.replace(Regex("""\s+"""), " ").trim()
            return if (singleLine.length <= maxChars) singleLine else singleLine.take(maxChars) + "..."
        }

        private fun defaultGetExecutor(): OnlineHttpGetExecutor {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            return OnlineHttpGetExecutor { url ->
                val req = Request.Builder()
                    .url(url)
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
