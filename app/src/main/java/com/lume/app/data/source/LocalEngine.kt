package com.lume.app.data.source

import android.content.Context
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.FilterList
import com.lume.app.domain.HomeFeed
import com.lume.app.domain.PagedResult
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Local catalogue under filesDir/local/{Series}/{Chapter}/ with jpg, png, webp pages,
 * or a .cbz / .zip archive per chapter.
 */
class LocalEngine(
    private val context: Context,
) : CatalogSource {

    override val manifest: SourceManifest = SourceManifest(
        id = "local",
        name = "Local",
        version = "1.0.0",
        engine = "local",
        baseUrl = "file://local",
        mediaKind = "comics",
        language = "local",
    )

    override val supportsLatest: Boolean = true
    override val supportsPopular: Boolean = true

    private fun rootDir(): File {
        val dir = File(context.filesDir, "local")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun seriesDirs(): List<File> =
        rootDir().listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }.orEmpty()

    private fun chapterEntries(series: File): List<File> {
        val children = series.listFiles()?.sortedBy { it.name.lowercase() }.orEmpty()
        return children.filter { child ->
            when {
                child.isDirectory -> true
                child.isFile && isArchive(child) -> true
                else -> false
            }
        }
    }

    private fun isArchive(file: File): Boolean {
        val n = file.name.lowercase()
        return n.endsWith(".cbz") || n.endsWith(".zip")
    }

    private fun imageFiles(dir: File): List<File> =
        dir.listFiles()
            ?.filter { it.isFile && isImage(it.name) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    private fun isImage(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")
    }

    private fun coverFor(series: File): String? {
        val chapters = chapterEntries(series)
        for (ch in chapters) {
            if (ch.isDirectory) {
                val img = imageFiles(ch).firstOrNull()
                if (img != null) return img.absolutePath
            }
        }
        return null
    }

    private fun toSummary(series: File): TitleSummary {
        val chapters = chapterEntries(series)
        return TitleSummary(
            id = series.name,
            title = series.name,
            coverUrl = coverFor(series),
            typeLabel = "Local",
            latestChapter = chapters.lastOrNull()?.nameWithoutExtension,
        )
    }

    override suspend fun getHome(): HomeFeed = withContext(Dispatchers.IO) {
        val items = seriesDirs().map { toSummary(it) }
        HomeFeed(spotlight = items.take(6), latest = items)
    }

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> = pageSeries(page)

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> = pageSeries(page)

    private suspend fun pageSeries(page: Int): PagedResult<TitleSummary> = withContext(Dispatchers.IO) {
        val all = seriesDirs().map { toSummary(it) }
        val pageSize = 50
        val from = ((page - 1) * pageSize).coerceAtLeast(0)
        val slice = if (from >= all.size) emptyList() else all.drop(from).take(pageSize)
        PagedResult(items = slice, page = page, hasNext = from + pageSize < all.size)
    }

    override fun getFilters(): FilterList = FilterList()

    override suspend fun search(
        query: String,
        page: Int,
        filters: FilterList,
    ): PagedResult<TitleSummary> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        val all = seriesDirs().map { toSummary(it) }
            .filter { q.isEmpty() || it.title.lowercase().contains(q) }
        PagedResult(items = all, page = 1, hasNext = false)
    }

    override suspend fun getTitle(id: String): TitleDetails = withContext(Dispatchers.IO) {
        val series = File(rootDir(), id)
        if (!series.isDirectory) {
            error(com.lume.app.data.locale.appString(com.lume.app.R.string.error_local_title_missing, id))
        }
        TitleDetails(
            id = id,
            title = series.name,
            coverUrl = coverFor(series),
            typeLabel = "Local",
            description = com.lume.app.data.locale.appString(
                com.lume.app.R.string.local_files_description,
                series.absolutePath,
            ),
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> = withContext(Dispatchers.IO) {
        val series = File(rootDir(), titleId)
        if (!series.isDirectory) return@withContext emptyList()
        chapterEntries(series).map { file ->
            Chapter(
                id = file.name,
                title = file.nameWithoutExtension,
                url = file.absolutePath,
            )
        }.reversed()
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> = withContext(Dispatchers.IO) {
        val path = chapter.url
        val file = File(path)
        when {
            file.isDirectory -> imageFiles(file).mapIndexed { i, img ->
                ComicPage(index = i, imageUrl = img.absolutePath)
            }
            file.isFile && isArchive(file) -> pagesFromZip(file)
            else -> {
                // Fallback: resolve via title folder + chapter id
                emptyList()
            }
        }
    }

    private fun pagesFromZip(archive: File): List<ComicPage> {
        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && isImage(it.name.substringAfterLast('/')) }
                .sortedBy { it.name.lowercase() }
                .toList()
            val outDir = File(context.cacheDir, "local_zip/${archive.nameWithoutExtension}")
            if (!outDir.exists()) outDir.mkdirs()
            val pages = mutableListOf<ComicPage>()
            entries.forEachIndexed { i, entry ->
                val name = entry.name.substringAfterLast('/').ifBlank { "page_$i" }
                val dest = File(outDir, "%04d_%s".format(i, name))
                if (!dest.exists() || dest.length() == 0L) {
                    zip.getInputStream(entry).use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                pages += ComicPage(index = i, imageUrl = dest.absolutePath)
            }
            return pages
        }
    }
}
