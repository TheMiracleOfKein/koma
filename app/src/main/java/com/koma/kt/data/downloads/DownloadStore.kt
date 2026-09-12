package com.koma.kt.data.downloads

import android.content.Context
import com.koma.kt.data.db.DownloadEntity
import com.koma.kt.data.db.LibraryDao
import com.koma.kt.data.network.AppHttp
import com.koma.kt.domain.CatalogSource
import com.koma.kt.domain.Chapter
import com.koma.kt.domain.ComicPage
import com.koma.kt.domain.TitleDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class DownloadStore(
    private val context: Context,
    private val dao: LibraryDao,
    private val http: AppHttp,
) {
    private val progressMap = ConcurrentHashMap<String, Float>()
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    fun keyOf(sourceId: String, titleId: String, chapterId: String) =
        "$sourceId|$titleId|$chapterId"

    private fun safe(value: String) =
        value.replace(Regex("""[<>:"/\\|?*]"""), "_").replace(' ', '_')

    private fun chapterDir(sourceId: String, titleId: String, chapterId: String): File {
        val dir = File(
            context.filesDir,
            "downloads/${safe(sourceId)}/${safe(titleId)}/${safe(chapterId)}",
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun downloadedChapterIds(sourceId: String, titleId: String): Set<String> =
        dao.downloadedChapterIds(sourceId, titleId).toSet()

    suspend fun localPages(sourceId: String, titleId: String, chapterId: String): List<ComicPage>? {
        val meta = dao.getDownload(sourceId, titleId, chapterId) ?: return null
        val paths = Json.decodeFromString<List<String>>(meta.pagePathsJson)
        val pages = mutableListOf<ComicPage>()
        for ((i, path) in paths.withIndex()) {
            val file = File(path)
            if (!file.exists()) return null
            pages += ComicPage(index = i, imageUrl = file.absolutePath)
        }
        return pages
    }

    suspend fun downloadChapter(
        source: CatalogSource,
        titleId: String,
        title: TitleDetails?,
        chapter: Chapter,
        onProgress: ((Float) -> Unit)? = null,
    ) {
        val sourceId = source.manifest.id
        val key = keyOf(sourceId, titleId, chapter.id)
        setProgress(key, 0f)
        onProgress?.invoke(0f)

        val pages = source.getPages(chapter)
        if (pages.isEmpty()) error("Нет страниц для скачивания")

        val dir = chapterDir(sourceId, titleId, chapter.id)
        val paths = mutableListOf<String>()
        val referer = source.manifest.headers["Referer"] ?: "${source.manifest.baseUrl.trimEnd('/')}/"

        for ((i, page) in pages.withIndex()) {
            val url = page.imageUrl
            val bytes = if (url.startsWith("/") || (!url.startsWith("http") && File(url).exists())) {
                File(url).readBytes()
            } else {
                http.getBytes(
                    URI(url),
                    source = source.manifest,
                    headers = mapOf(
                        "Referer" to referer,
                        "Accept" to "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
                    ),
                )
            }
            val ext = when {
                url.contains(".png", ignoreCase = true) -> "png"
                url.contains(".webp", ignoreCase = true) -> "webp"
                else -> "jpg"
            }
            val file = File(dir, "%04d.%s".format(i, ext))
            file.writeBytes(bytes)
            paths += file.absolutePath
            val p = (i + 1).toFloat() / pages.size
            setProgress(key, p)
            onProgress?.invoke(p)
        }

        dao.upsertDownload(
            DownloadEntity(
                sourceId = sourceId,
                titleId = titleId,
                chapterId = chapter.id,
                titleName = title?.title ?: titleId,
                coverUrl = title?.coverUrl,
                chapterName = chapter.title,
                pageCount = paths.size,
                pagePathsJson = Json.encodeToString(paths),
            ),
        )
        progressMap.remove(key)
        _progress.value = progressMap.toMap()
    }

    suspend fun delete(sourceId: String, titleId: String, chapterId: String) {
        val meta = dao.getDownload(sourceId, titleId, chapterId)
        if (meta != null) {
            runCatching {
                Json.decodeFromString<List<String>>(meta.pagePathsJson).forEach { File(it).delete() }
            }
            chapterDir(sourceId, titleId, chapterId).deleteRecursively()
        }
        dao.deleteDownload(sourceId, titleId, chapterId)
    }

    private fun setProgress(key: String, value: Float) {
        progressMap[key] = value
        _progress.value = progressMap.toMap()
    }
}
