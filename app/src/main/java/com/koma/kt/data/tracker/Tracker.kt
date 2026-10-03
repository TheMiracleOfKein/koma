package com.koma.kt.data.tracker

import android.content.Context
import com.koma.kt.R
import com.koma.kt.data.db.ChapterReadEntity
import com.koma.kt.data.db.LibraryDao
import com.koma.kt.data.db.ProgressEntity
import com.koma.kt.data.db.TrackerLinkEntity
import com.koma.kt.data.network.AppHttp
import com.koma.kt.data.prefs.AppPreferences
import com.koma.kt.domain.Chapter
import com.koma.kt.util.urlEncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI

data class TrackerSearchResult(
    val remoteId: String,
    val title: String,
    val url: String? = null,
    val coverUrl: String? = null,
)

data class TrackerProgress(
    val chaptersRead: Int,
    val status: String? = null,
    val score: Float? = null,
    /** Shikimori user_rate id / AniList mediaList entry id when known. */
    val libraryId: String? = null,
)

data class TitleSyncResult(
    val summary: String,
    val details: List<String> = emptyList(),
) {
    fun displayText(): String = buildString {
        append(summary)
        if (details.isNotEmpty()) {
            append('\n')
            append(details.joinToString("\n"))
        }
    }
}

interface Tracker {
    val id: String
    val displayName: String
    suspend fun isLoggedIn(): Boolean
    suspend fun search(query: String): List<TrackerSearchResult>
    suspend fun link(
        sourceId: String,
        titleId: String,
        remoteId: String,
        remoteUrl: String? = null,
    )
    suspend fun unlink(sourceId: String, titleId: String)
    suspend fun fetchProgress(remoteId: String): TrackerProgress?
    suspend fun updateProgress(remoteId: String, chaptersRead: Int): Boolean
    fun authorizeUrl(): String?
    suspend fun exchangeCode(code: String): Boolean
    suspend fun logout()
}

fun chapterProgressNumber(chapters: List<Chapter>, chapterId: String): Int {
    val ch = chapters.firstOrNull { it.id == chapterId }
    ch?.number?.replace(',', '.')?.toFloatOrNull()?.toInt()?.takeIf { it > 0 }?.let { return it }
    val match = Regex("""(\d+(?:[.,]\d+)?)""").find(ch?.title.orEmpty())
    match?.groupValues?.getOrNull(1)?.replace(',', '.')?.toFloatOrNull()?.toInt()?.takeIf { it > 0 }
        ?.let { return it }
    val idx = chapters.indexOfFirst { it.id == chapterId }
    return if (idx >= 0) idx + 1 else 0
}

fun findChapterForProgress(chapters: List<Chapter>, chaptersRead: Int): Chapter? {
    if (chaptersRead <= 0 || chapters.isEmpty()) return null
    chapters.firstOrNull { chapterProgressNumber(chapters, it.id) == chaptersRead }?.let { return it }
    return chapters
        .map { it to chapterProgressNumber(chapters, it.id) }
        .filter { it.second in 1..chaptersRead }
        .maxByOrNull { it.second }
        ?.first
}

fun resolveLocalChapterProgress(
    chapters: List<Chapter>,
    progressChapterId: String?,
    readChapterIds: Set<String>,
): Pair<Int, Chapter?> {
    var best = 0
    var bestChapter: Chapter? = null
    fun consider(chapterId: String?) {
        if (chapterId.isNullOrBlank()) return
        val ch = chapters.firstOrNull { it.id == chapterId } ?: return
        val n = chapterProgressNumber(chapters, ch.id)
        if (n > best) {
            best = n
            bestChapter = ch
        }
    }
    consider(progressChapterId)
    for (id in readChapterIds) consider(id)
    if (bestChapter == null && best > 0) {
        bestChapter = findChapterForProgress(chapters, best)
    }
    return best to bestChapter
}

class AniListTracker(
    private val http: AppHttp,
    private val dao: LibraryDao,
    private val prefs: AppPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Tracker {
    override val id: String = "anilist"
    override val displayName: String = "AniList"

    override suspend fun isLoggedIn(): Boolean =
        !prefs.anilistAccessToken.first().isNullOrBlank()

    override fun authorizeUrl(): String? {
        // Client ID is user-configurable; redirect must match app deep link.
        return null // built in Settings with client id
    }

    override suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val clientId = TrackerOAuthDefaults.ANILIST_CLIENT_ID
            .ifBlank { prefs.anilistClientId.first().orEmpty() }
        val clientSecret = TrackerOAuthDefaults.ANILIST_CLIENT_SECRET
            .ifBlank { prefs.anilistClientSecret.first().orEmpty() }
        if (clientId.isBlank()) return@withContext false
        val jsonBody =
            """{"grant_type":"authorization_code","client_id":"$clientId","client_secret":"$clientSecret","redirect_uri":"$REDIRECT_URI","code":"$code"}"""
        val jsonRequest = Request.Builder()
            .url("https://anilist.co/api/v2/oauth/token")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()
        http.okHttp.newCall(jsonRequest).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@withContext false
            val obj = json.parseToJsonElement(text).jsonObject
            val token = obj["access_token"]?.jsonPrimitive?.content ?: return@withContext false
            prefs.setAniListAccessToken(token)
            true
        }
    }

    override suspend fun logout() {
        prefs.setAniListAccessToken(null)
    }

    override suspend fun search(query: String): List<TrackerSearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val escaped = json.encodeToString(q)
        val body =
            """{"query":"query (${'$'}search:String){Page(perPage:10){media(search:${'$'}search,type:MANGA){id title{romaji english native} siteUrl coverImage{large}}}}","variables":{"search":$escaped}}"""
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()
        http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@withContext emptyList()
            val root = json.parseToJsonElement(text).jsonObject
            val media = root["data"]?.jsonObject
                ?.get("Page")?.jsonObject
                ?.get("media")?.jsonArray
                ?: return@withContext emptyList()
            media.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.longOrNull?.toString() ?: return@mapNotNull null
                val titles = obj["title"]?.jsonObject
                val name = titles?.get("english")?.jsonPrimitive?.content
                    ?: titles?.get("romaji")?.jsonPrimitive?.content
                    ?: titles?.get("native")?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                TrackerSearchResult(
                    remoteId = id,
                    title = name,
                    url = obj["siteUrl"]?.jsonPrimitive?.content,
                    coverUrl = obj["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content,
                )
            }
        }
    }

    override suspend fun link(
        sourceId: String,
        titleId: String,
        remoteId: String,
        remoteUrl: String?,
    ) {
        dao.upsertTrackerLink(
            TrackerLinkEntity(
                sourceId = sourceId,
                titleId = titleId,
                tracker = id,
                remoteId = remoteId,
                remoteUrl = remoteUrl ?: "https://anilist.co/manga/$remoteId",
            ),
        )
    }

    override suspend fun unlink(sourceId: String, titleId: String) {
        dao.deleteTrackerLink(sourceId, titleId, id)
    }

    override suspend fun fetchProgress(remoteId: String): TrackerProgress? = withContext(Dispatchers.IO) {
        val token = prefs.anilistAccessToken.first() ?: return@withContext null
        val mediaId = remoteId.toLongOrNull() ?: return@withContext null
        val body =
            """{"query":"query(${'$'}id:Int){Media(id:${'$'}id,type:MANGA){mediaListEntry{progress status score}}}","variables":{"id":$mediaId}}"""
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()
        http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@withContext null
            val entry = json.parseToJsonElement(text).jsonObject["data"]?.jsonObject
                ?.get("Media")?.jsonObject
                ?.get("mediaListEntry")?.jsonObject
                ?: return@withContext TrackerProgress(0)
            TrackerProgress(
                chaptersRead = entry["progress"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                status = entry["status"]?.jsonPrimitive?.content,
                score = entry["score"]?.jsonPrimitive?.content?.toFloatOrNull(),
            )
        }
    }

    override suspend fun updateProgress(remoteId: String, chaptersRead: Int): Boolean =
        withContext(Dispatchers.IO) {
            val token = prefs.anilistAccessToken.first() ?: return@withContext false
            val mediaId = remoteId.toLongOrNull() ?: return@withContext false
            val body =
                """{"query":"mutation(${'$'}id:Int,${'$'}progress:Int){SaveMediaListEntry(mediaId:${'$'}id,progress:${'$'}progress){id progress}}","variables":{"id":$mediaId,"progress":$chaptersRead}}"""
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()
            http.okHttp.newCall(request).execute().use { it.isSuccessful }
        }

    companion object {
        const val REDIRECT_URI = "koma://oauth/anilist"
    }
}

class ShikimoriTracker(
    private val http: AppHttp,
    private val dao: LibraryDao,
    private val prefs: AppPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Tracker {
    override val id: String = "shikimori"
    override val displayName: String = "Shikimori"

    override suspend fun isLoggedIn(): Boolean =
        !prefs.shikimoriAccessToken.first().isNullOrBlank()

    override fun authorizeUrl(): String? = null

    fun buildAuthorizeUrl(clientId: String): String {
        val redirect = urlEncode(REDIRECT_URI)
        return "$BASE_URL/oauth/authorize?client_id=$clientId&redirect_uri=$redirect&response_type=code&scope=user_rates"
    }

    override suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val clientId = TrackerOAuthDefaults.SHIKIMORI_CLIENT_ID
            .ifBlank { prefs.shikimoriClientId.first().orEmpty() }
        val clientSecret = TrackerOAuthDefaults.SHIKIMORI_CLIENT_SECRET
            .ifBlank { prefs.shikimoriClientSecret.first().orEmpty() }
        if (clientId.isBlank()) return@withContext false
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("redirect_uri", REDIRECT_URI)
            .add("code", code)
            .build()
        val request = Request.Builder()
            .url("$BASE_URL/oauth/token")
            .post(body)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@withContext false
            val obj = json.parseToJsonElement(text).jsonObject
            val token = obj["access_token"]?.jsonPrimitive?.content ?: return@withContext false
            val refresh = obj["refresh_token"]?.jsonPrimitive?.content
            prefs.setShikimoriTokens(token, refresh)
            runCatching { fetchAndStoreUserId(token) }
            true
        }
    }

    override suspend fun logout() {
        prefs.setShikimoriTokens(null, null)
        prefs.setShikimoriUserId(null)
    }

    override suspend fun search(query: String): List<TrackerSearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val encoded = urlEncode(q)
        // shikimori.one/api… often 404; Mihon uses shikimori.io
        val result = http.get(
            URI("$API_URL/mangas?search=$encoded&limit=10"),
            checkCloudflare = false,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json",
            ),
        )
        val arr = json.parseToJsonElement(result.body).jsonArray
        arr.mapNotNull { el ->
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["russian"]?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
                ?: obj["name"]?.jsonPrimitive?.content
                ?: return@mapNotNull null
            val image = obj["image"]?.jsonObject?.get("original")?.jsonPrimitive?.content
            TrackerSearchResult(
                remoteId = id,
                title = name,
                url = "$BASE_URL/mangas/$id",
                coverUrl = image?.let { if (it.startsWith("http")) it else "$BASE_URL$it" },
            )
        }
    }

    override suspend fun link(
        sourceId: String,
        titleId: String,
        remoteId: String,
        remoteUrl: String?,
    ) {
        dao.upsertTrackerLink(
            TrackerLinkEntity(
                sourceId = sourceId,
                titleId = titleId,
                tracker = id,
                remoteId = remoteId,
                remoteUrl = remoteUrl ?: "$BASE_URL/mangas/$remoteId",
            ),
        )
    }

    override suspend fun unlink(sourceId: String, titleId: String) {
        dao.deleteTrackerLink(sourceId, titleId, id)
    }

    override suspend fun fetchProgress(remoteId: String): TrackerProgress? = withContext(Dispatchers.IO) {
        val token = prefs.shikimoriAccessToken.first() ?: return@withContext null
        val userId = ensureUserId(token) ?: return@withContext null
        val request = Request.Builder()
            .url("$API_URL/v2/user_rates?user_id=$userId&target_type=Manga&target_id=$remoteId")
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@withContext null
            val arr = json.parseToJsonElement(text).jsonArray
            val obj = arr.firstOrNull()?.jsonObject ?: return@withContext TrackerProgress(0)
            TrackerProgress(
                chaptersRead = obj["chapters"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                status = obj["status"]?.jsonPrimitive?.content,
                score = obj["score"]?.jsonPrimitive?.content?.toFloatOrNull(),
                libraryId = obj["id"]?.jsonPrimitive?.content,
            )
        }
    }

    override suspend fun updateProgress(remoteId: String, chaptersRead: Int): Boolean =
        withContext(Dispatchers.IO) {
            val token = prefs.shikimoriAccessToken.first() ?: return@withContext false
            val userId = ensureUserId(token) ?: return@withContext false
            val listReq = Request.Builder()
                .url("$API_URL/v2/user_rates?user_id=$userId&target_type=Manga&target_id=$remoteId")
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
            val existing = http.okHttp.newCall(listReq).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) return@use null
                json.parseToJsonElement(text).jsonArray.firstOrNull()?.jsonObject
            }
            val rateId = existing?.get("id")?.jsonPrimitive?.content
            val remoteStatus = existing?.get("status")?.jsonPrimitive?.content
            val targetId = remoteId.toLongOrNull() ?: return@withContext false
            val payload = buildJsonObject {
                put("user_rate", buildJsonObject {
                    put("chapters", JsonPrimitive(chaptersRead))
                    if (rateId == null) {
                        put("user_id", JsonPrimitive(userId.toLongOrNull() ?: return@withContext false))
                        put("target_id", JsonPrimitive(targetId))
                        put("target_type", JsonPrimitive("Manga"))
                        put("status", JsonPrimitive("watching"))
                    } else if (remoteStatus.isNullOrBlank() || remoteStatus == "planned") {
                        // Only bump planned → watching; never clobber completed/on_hold/etc.
                        put("status", JsonPrimitive("watching"))
                    }
                })
            }.toString()
            val url = if (rateId != null) {
                "$API_URL/v2/user_rates/$rateId"
            } else {
                "$API_URL/v2/user_rates"
            }
            val request = Request.Builder()
                .url(url)
                .method(if (rateId != null) "PATCH" else "POST", payload.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()
            http.okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w(
                        "Shikimori",
                        "updateProgress failed ${response.code}: ${response.body.string()}",
                    )
                }
                response.isSuccessful
            }
        }

    private suspend fun ensureUserId(token: String): String? {
        prefs.shikimoriUserId.first()?.takeIf { it.isNotBlank() }?.let { return it }
        return fetchAndStoreUserId(token)
    }

    private suspend fun fetchAndStoreUserId(token: String): String? {
        val request = Request.Builder()
            .url("$API_URL/users/whoami")
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        return http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@use null
            val id = json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.content
                ?: return@use null
            prefs.setShikimoriUserId(id)
            id
        }
    }

    companion object {
        const val REDIRECT_URI = "koma://oauth/shikimori"
        /** Canonical host used by Mihon; .one redirects break POSTs with 301. */
        const val BASE_URL = "https://shikimori.io"
        const val API_URL = "$BASE_URL/api"
        const val USER_AGENT = "Koma"
    }
}

class TrackerRegistry(
    http: AppHttp,
    dao: LibraryDao,
    prefs: AppPreferences,
) {
    val anilist = AniListTracker(http, dao, prefs)
    val shikimori = ShikimoriTracker(http, dao, prefs)
    val all: List<Tracker> = listOf(shikimori, anilist)

    fun byId(id: String): Tracker? = all.firstOrNull { it.id == id }
}

class TrackerSync(
    private val context: Context,
    private val dao: LibraryDao,
    private val trackers: TrackerRegistry,
) {
    suspend fun pushProgress(
        sourceId: String,
        titleId: String,
        chapters: List<Chapter>,
        chapterId: String,
    ) {
        val progressNum = chapterProgressNumber(chapters, chapterId)
        if (progressNum <= 0) return
        val links = dao.getTrackerLinks(sourceId, titleId)
        for (link in links) {
            val tracker = trackers.byId(link.tracker) ?: continue
            if (!tracker.isLoggedIn()) continue
            val remote = runCatching { tracker.fetchProgress(link.remoteId) }.getOrNull()
            val remoteCh = remote?.chaptersRead ?: 0
            if (progressNum <= remoteCh) {
                dao.upsertTrackerLink(link.copy(lastSync = System.currentTimeMillis()))
                continue
            }
            val ok = runCatching { tracker.updateProgress(link.remoteId, progressNum) }.getOrDefault(false)
            if (ok) {
                dao.upsertTrackerLink(
                    link.copy(
                        lastSync = System.currentTimeMillis(),
                        status = remote?.status ?: "watching",
                    ),
                )
            }
        }
    }

    suspend fun syncTitle(
        sourceId: String,
        titleId: String,
        chapters: List<Chapter>,
        titleName: String,
        coverUrl: String?,
        typeLabel: String?,
    ): TitleSyncResult {
        val links = dao.getTrackerLinks(sourceId, titleId)
        if (links.isEmpty()) {
            return TitleSyncResult(
                context.getString(R.string.sync_no_links),
                listOf(context.getString(R.string.sync_no_links_hint)),
            )
        }

        val localProgress = dao.getProgress(sourceId, titleId)
        val readIds = dao.readChapterIds(sourceId, titleId).toSet()
        val (localBest, localChapter) = resolveLocalChapterProgress(
            chapters = chapters,
            progressChapterId = localProgress?.chapterId,
            readChapterIds = readIds,
        )

        val details = mutableListOf<String>()
        details += context.getString(R.string.sync_local_progress, localBest)

        var target = localBest
        var targetChapter = localChapter
        val remoteByTracker = mutableMapOf<String, TrackerProgress?>()

        for (link in links) {
            val tracker = trackers.byId(link.tracker) ?: continue
            if (!tracker.isLoggedIn()) {
                details += context.getString(R.string.sync_not_logged_in, tracker.displayName)
                remoteByTracker[link.tracker] = null
                continue
            }
            val remote = runCatching { tracker.fetchProgress(link.remoteId) }
                .onFailure {
                    details += context.getString(
                        R.string.sync_read_error,
                        tracker.displayName,
                        it.message ?: "network",
                    )
                }
                .getOrNull()
            remoteByTracker[link.tracker] = remote
            if (remote == null) {
                details += context.getString(R.string.sync_progress_unavailable, tracker.displayName)
                continue
            }
            details += remote.status?.let {
                context.getString(
                    R.string.sync_remote_progress_status,
                    tracker.displayName,
                    remote.chaptersRead,
                    it,
                )
            } ?: context.getString(
                R.string.sync_remote_progress,
                tracker.displayName,
                remote.chaptersRead,
            )
            if (remote.chaptersRead > target) {
                target = remote.chaptersRead
                targetChapter = findChapterForProgress(chapters, target) ?: targetChapter
            }
        }

        if (target <= 0) {
            return TitleSyncResult(
                context.getString(R.string.sync_nothing),
                details + context.getString(R.string.sync_nothing_hint),
            )
        }

        val chapter = targetChapter
            ?: findChapterForProgress(chapters, target)
            ?: return TitleSyncResult(
                context.getString(R.string.sync_chapter_missing, target),
                details + context.getString(R.string.sync_chapter_missing_hint),
            )

        var localUpdated = false
        if (target > localBest) {
            dao.upsertProgress(
                ProgressEntity(
                    sourceId = sourceId,
                    titleId = titleId,
                    titleName = titleName,
                    coverUrl = coverUrl,
                    typeLabel = typeLabel,
                    chapterId = chapter.id,
                    chapterName = chapter.title,
                    chapterUrl = chapter.url,
                    pageIndex = localProgress?.takeIf { it.chapterId == chapter.id }?.pageIndex ?: 0,
                    pageCount = localProgress?.takeIf { it.chapterId == chapter.id }?.pageCount ?: 0,
                ),
            )
            var marked = 0
            for (ch in chapters) {
                val n = chapterProgressNumber(chapters, ch.id)
                if (n in 1..target && ch.id !in readIds) {
                    dao.markChapterRead(
                        ChapterReadEntity(
                            sourceId = sourceId,
                            titleId = titleId,
                            chapterId = ch.id,
                        ),
                    )
                    marked++
                }
            }
            localUpdated = true
            details += context.getString(R.string.sync_local_pulled, target) +
                if (marked > 0) context.getString(R.string.sync_marked_read, marked) else ""
        } else if (localProgress == null) {
            dao.upsertProgress(
                ProgressEntity(
                    sourceId = sourceId,
                    titleId = titleId,
                    titleName = titleName,
                    coverUrl = coverUrl,
                    typeLabel = typeLabel,
                    chapterId = chapter.id,
                    chapterName = chapter.title,
                    chapterUrl = chapter.url,
                ),
            )
            localUpdated = true
        }

        var pushed = 0
        var alreadyOk = 0
        var failed = 0
        for (link in links) {
            val tracker = trackers.byId(link.tracker) ?: continue
            if (!tracker.isLoggedIn()) continue
            val remote = remoteByTracker[link.tracker]
            val remoteCh = remote?.chaptersRead ?: -1
            when {
                remoteCh >= target -> {
                    alreadyOk++
                    dao.upsertTrackerLink(
                        link.copy(
                            lastSync = System.currentTimeMillis(),
                            status = remote?.status ?: link.status,
                            score = remote?.score ?: link.score,
                        ),
                    )
                    details += context.getString(R.string.sync_already_up_to_date, tracker.displayName)
                }
                else -> {
                    val ok = runCatching { tracker.updateProgress(link.remoteId, target) }
                        .onFailure {
                            failed++
                            details += context.getString(
                                R.string.sync_write_error,
                                tracker.displayName,
                                it.message ?: "fail",
                            )
                        }
                        .getOrDefault(false)
                    if (ok) {
                        pushed++
                        dao.upsertTrackerLink(
                            link.copy(
                                lastSync = System.currentTimeMillis(),
                                status = remote?.status ?: "watching",
                                score = remote?.score ?: link.score,
                            ),
                        )
                        details += if (remoteCh >= 0) {
                            context.getString(
                                R.string.sync_pushed_was,
                                tracker.displayName,
                                target,
                                remoteCh,
                            )
                        } else {
                            context.getString(R.string.sync_pushed, tracker.displayName, target)
                        }
                    } else {
                        failed++
                        details += context.getString(R.string.sync_update_failed, tracker.displayName)
                    }
                }
            }
        }

        val summary = buildString {
            append(context.getString(R.string.sync_summary, target))
            when {
                localUpdated && pushed > 0 -> append(context.getString(R.string.sync_summary_pull_and_push))
                localUpdated -> append(context.getString(R.string.sync_summary_pull_local))
                pushed > 0 -> append(context.getString(R.string.sync_summary_pushed))
                alreadyOk > 0 && failed == 0 -> append(context.getString(R.string.sync_summary_already))
                failed > 0 -> append(context.getString(R.string.sync_summary_errors))
                else -> append(context.getString(R.string.sync_summary_unchanged))
            }
        }
        return TitleSyncResult(summary, details)
    }
}
