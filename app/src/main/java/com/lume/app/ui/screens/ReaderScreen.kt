package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material.icons.outlined.SwipeRight
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.db.BookmarkEntity
import com.lume.app.data.db.ChapterReadEntity
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.prefs.ReaderSettings
import com.lume.app.domain.Chapter
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.ComicPage
import com.lume.app.domain.ReaderMode
import com.lume.app.domain.ReaderTheme
import com.lume.app.domain.inReadingOrder
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    nav: NavHostController,
    sourceId: String,
    titleId: String,
    chapterId: String,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var loadedChapters by remember { mutableStateOf<List<LoadedChapter>>(emptyList()) }
    var chapter by remember { mutableStateOf<Chapter?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloudflareUri by remember { mutableStateOf<java.net.URI?>(null) }
    var titleName by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf<String?>(null) }
    var typeLabel by remember { mutableStateOf<String?>(null) }
    var chromeVisible by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    var bookmarked by remember { mutableStateOf(false) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var refreshEpoch by remember { mutableIntStateOf(0) }
    val prefetchMutex = remember { Mutex() }
    val scope = rememberCoroutineScope()
    val dao = LumeApp.instance.dao
    val networkEpoch by LumeApp.instance.http.networkEpoch.collectAsState()
    val readerSettingsGlobal by LumeApp.instance.prefs.readerSettings.collectAsState(initial = ReaderSettings())
    var readerSettings by remember { mutableStateOf(ReaderSettings()) }
    val systemDark = isSystemInDarkTheme()
    val stripItems = remember(loadedChapters) { buildReaderStrip(loadedChapters) }

    fun chapterIdsMatch(a: String, b: String): Boolean =
        a == b || a.trimEnd('/') == b.trimEnd('/')

    fun warmPageImages(pages: List<ComicPage>) {
        val loader = context.imageLoader
        pages.forEach { page ->
            val data: Any = if (page.imageUrl.startsWith("http")) page.imageUrl else File(page.imageUrl)
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(data)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)
                    .build(),
            )
        }
    }

    LaunchedEffect(readerSettingsGlobal, sourceId, titleId) {
        val titlePrefs = dao.getTitlePrefs(sourceId, titleId)
        readerSettings = readerSettingsGlobal.copy(
            mode = titlePrefs?.readerMode?.let { ReaderMode.fromPrefs(it) } ?: readerSettingsGlobal.mode,
            theme = titlePrefs?.readerTheme?.let { ReaderTheme.entries.getOrElse(it) { readerSettingsGlobal.theme } }
                ?: readerSettingsGlobal.theme,
            gap = titlePrefs?.gap ?: readerSettingsGlobal.gap,
        )
    }

    val readerBg = when (readerSettings.theme) {
        ReaderTheme.Light -> Color(0xFFF2F2F2)
        ReaderTheme.Dark -> Color.Black
        ReaderTheme.System -> if (systemDark) Color.Black else Color(0xFFF2F2F2)
    }

    suspend fun loadPagesFor(ch: Chapter): List<ComicPage> = withContext(Dispatchers.IO) {
        val source = LumeApp.instance.sources.sourceById(sourceId)
        val local = LumeApp.instance.downloads.localPages(sourceId, titleId, ch.id)
        local ?: source?.getPages(ch)
            ?: error(resources.getString(R.string.error_no_pages_offline))
    }

    /** Keep at least [ahead] chapter(s) after the last loaded one; warm Coil cache. */
    suspend fun prefetchNextChapter(ahead: Int = 1) {
        if (!readerSettings.seamlessReading) return
        prefetchMutex.withLock {
            repeat(ahead) {
                val list = chapters
                val loaded = loadedChapters
                if (list.isEmpty() || loaded.isEmpty()) return@withLock
                val lastId = loaded.last().chapter.id
                val idx = list.indexOfFirst { chapterIdsMatch(it.id, lastId) }
                if (idx < 0) return@withLock
                val next = list.getOrNull(idx + 1) ?: return@withLock
                if (loaded.any { chapterIdsMatch(it.chapter.id, next.id) }) return@withLock
                val pages = runCatching { loadPagesFor(next) }.getOrNull() ?: return@withLock
                if (pages.isEmpty()) return@withLock
                if (loadedChapters.any { chapterIdsMatch(it.chapter.id, next.id) }) return@withLock
                loadedChapters = loadedChapters + LoadedChapter(next, pages)
                warmPageImages(pages)
            }
        }
    }

    fun maybePrefetchFromProgress(visibleStripIndex: Int, stripSize: Int) {
        if (!readerSettings.seamlessReading || stripSize <= 0) return
        val remaining = stripSize - 1 - visibleStripIndex
        // Start well before the end so network + decode finish while user still scrolls.
        if (remaining <= 12) {
            scope.launch { prefetchNextChapter(ahead = 1) }
        }
    }

    LaunchedEffect(sourceId, titleId, chapterId, networkEpoch, refreshEpoch) {
        loading = true
        error = null
        cloudflareUri = null
        loadedChapters = emptyList()
        runCatching {
            val source = LumeApp.instance.sources.sourceById(sourceId)
            val offline = LumeApp.instance.offlineCache
            val cached = offline.get(sourceId, titleId)
            val details = cached?.details
                ?: source?.let { runCatching { it.getTitle(titleId) }.getOrNull() }
                ?: offline.detailsFromDownloads(sourceId, titleId)
                ?: error(resources.getString(R.string.error_no_title_offline))
            titleName = details.title
            coverUrl = details.coverUrl
            typeLabel = details.typeLabel
            bookmarked = dao.getBookmark(sourceId, titleId) != null
            val rawList = cached?.chapters
                ?: source?.let { runCatching { it.getChapters(titleId) }.getOrNull() }
                ?: offline.chaptersFromDownloads(sourceId, titleId)
            if (rawList.isEmpty()) error(resources.getString(R.string.error_no_chapters))
            // Keep source order in cache; reader always uses oldest → newest.
            if (source != null && cached == null) {
                offline.save(sourceId, titleId, details, rawList)
            }
            val list = rawList.inReadingOrder()
            chapters = list
            val ch = list.firstOrNull { it.id == chapterId }
                ?: list.firstOrNull { it.id.trimEnd('/') == chapterId.trimEnd('/') }
                ?: error(resources.getString(R.string.error_chapter_not_found))
            chapter = ch
            val loaded = loadPagesFor(ch)
            val progress = dao.getProgress(sourceId, titleId)
            if (progress?.chapterId == ch.id) {
                pageIndex = progress.pageIndex.coerceIn(0, (loaded.size - 1).coerceAtLeast(0))
            } else {
                pageIndex = 0
            }
            LoadedChapter(ch, loaded)
        }.onSuccess { first ->
            loadedChapters = listOf(first)
            loading = false
            warmPageImages(first.pages)
            scope.launch {
                dao.markChapterRead(
                    ChapterReadEntity(sourceId = sourceId, titleId = titleId, chapterId = first.chapter.id),
                )
                runCatching {
                    LumeApp.instance.trackerSync.pushProgress(sourceId, titleId, chapters, first.chapter.id)
                }
                // Prefetch next chapter + its images while the user still reads this one.
                if (readerSettings.seamlessReading) {
                    prefetchNextChapter(ahead = 1)
                }
            }
        }.onFailure { e ->
            loading = false
            if (e is CloudflareException) {
                cloudflareUri = e.uri
                scope.launch { LumeApp.instance.http.requestChallenge(e.uri) }
                error = resources.getString(R.string.error_cloudflare_needed)
            } else {
                error = e.message ?: e.toString()
            }
        }
    }

    // If user turns seamless on mid-session, kick off prefetch once.
    LaunchedEffect(readerSettings.seamlessReading) {
        if (readerSettings.seamlessReading && loadedChapters.isNotEmpty()) {
            prefetchNextChapter(ahead = 1)
        }
    }

    fun onVisiblePage(ch: Chapter, indexInChapter: Int, chapterPageCount: Int) {
        val chapterChanged = chapter?.id != ch.id
        if (!chapterChanged && pageIndex == indexInChapter) return
        pageIndex = indexInChapter
        chapter = ch
        // When entering a chapter that is the last loaded one, pull the following chapter.
        if (readerSettings.seamlessReading &&
            loadedChapters.lastOrNull()?.let { chapterIdsMatch(it.chapter.id, ch.id) } == true
        ) {
            scope.launch { prefetchNextChapter(ahead = 1) }
        }
        scope.launch {
            dao.upsertProgress(
                ProgressEntity(
                    sourceId = sourceId,
                    titleId = titleId,
                    titleName = titleName,
                    coverUrl = coverUrl,
                    typeLabel = typeLabel,
                    chapterId = ch.id,
                    chapterName = ch.title,
                    chapterUrl = ch.url,
                    pageIndex = indexInChapter,
                    pageCount = chapterPageCount,
                ),
            )
            if (chapterChanged) {
                dao.markChapterRead(
                    ChapterReadEntity(sourceId = sourceId, titleId = titleId, chapterId = ch.id),
                )
                runCatching {
                    LumeApp.instance.trackerSync.pushProgress(sourceId, titleId, chapters, ch.id)
                }
            }
        }
    }

    fun goChapter(delta: Int) {
        val ch = chapter ?: return
        val idx = chapters.indexOfFirst { chapterIdsMatch(it.id, ch.id) }
        if (idx < 0) return
        val next = chapters.getOrNull(idx + delta) ?: return
        nav.navigate(Routes.reader(sourceId, titleId, next.id)) {
            popUpTo(Routes.reader(sourceId, titleId, chapterId)) { inclusive = true }
        }
    }

    fun openTitle() {
        nav.navigate(Routes.title(sourceId, titleId))
    }

    fun toggleBookmark() {
        scope.launch {
            if (bookmarked) {
                dao.deleteBookmark(sourceId, titleId)
                bookmarked = false
            } else {
                dao.upsertBookmark(
                    BookmarkEntity(
                        sourceId = sourceId,
                        titleId = titleId,
                        titleName = titleName,
                        coverUrl = coverUrl,
                        typeLabel = typeLabel,
                    ),
                )
                bookmarked = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerBg),
    ) {
        when {
            loading -> LoadingBox()
            error != null -> ErrorRetryBox(
                message = error!!,
                onRetry = { refreshEpoch++ },
                onCloudflare = cloudflareUri?.let { uri ->
                    { scope.launch { LumeApp.instance.http.requestChallenge(uri) } }
                },
            )
            else -> {
                val toggleChrome = { chromeVisible = !chromeVisible }
                val chapterPageCount = loadedChapters.firstOrNull { it.chapter.id == chapter?.id }?.pages?.size
                    ?: stripItems.filterIsInstance<ReaderStripItem.Page>()
                        .firstOrNull { it.chapter.id == chapter?.id }?.chapterPageCount
                    ?: 0
                when (readerSettings.mode) {
                    ReaderMode.Webtoon -> {
                        val listState = rememberLazyListState(
                            initialFirstVisibleItemIndex = pageIndex.coerceIn(0, (stripItems.size - 1).coerceAtLeast(0)),
                        )
                        LaunchedEffect(listState, stripItems) {
                            snapshotFlow {
                                val idx = listState.firstVisibleItemIndex
                                val item = stripItems.getOrNull(idx)
                                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: idx
                                Triple(item, lastVisible, stripItems.size)
                            }
                                .distinctUntilChanged()
                                .collect { (item, lastVisible, total) ->
                                    when (item) {
                                        is ReaderStripItem.Page -> onVisiblePage(
                                            item.chapter,
                                            item.indexInChapter,
                                            item.chapterPageCount,
                                        )
                                        is ReaderStripItem.Divider -> onVisiblePage(item.chapter, 0, 1)
                                        null -> Unit
                                    }
                                    maybePrefetchFromProgress(lastVisible, total)
                                }
                        }
                        ReaderZoomFrame(
                            zoomEnabled = readerSettings.doubleTapZoom,
                            resetKey = chapterId,
                            onSingleTap = toggleChrome,
                            modifier = Modifier.fillMaxSize(),
                        ) { zoomModifier, scale ->
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(zoomModifier),
                                verticalArrangement = Arrangement.spacedBy(readerSettings.gap.dp),
                                userScrollEnabled = scale <= 1.01f,
                            ) {
                                itemsIndexed(
                                    stripItems,
                                    key = { _, item ->
                                        when (item) {
                                            is ReaderStripItem.Divider -> "div-${item.chapter.id}"
                                            is ReaderStripItem.Page ->
                                                "p-${item.chapter.id}-${item.page.index}-${item.indexInChapter}"
                                        }
                                    },
                                ) { _, item ->
                                    when (item) {
                                        is ReaderStripItem.Divider -> ChapterDividerBand(item.chapter.title)
                                        is ReaderStripItem.Page -> ReaderPageImage(item.page, zoomEnabled = false)
                                    }
                                }
                            }
                        }
                    }
                    ReaderMode.Ltr, ReaderMode.Rtl -> {
                        val pagerState = rememberPagerState(
                            initialPage = pageIndex.coerceIn(0, (stripItems.size - 1).coerceAtLeast(0)),
                            pageCount = { stripItems.size.coerceAtLeast(1) },
                        )
                        LaunchedEffect(pagerState, stripItems) {
                            snapshotFlow { pagerState.currentPage to stripItems.size }
                                .distinctUntilChanged()
                                .collect { (page, total) ->
                                    when (val item = stripItems.getOrNull(page)) {
                                        is ReaderStripItem.Page -> onVisiblePage(
                                            item.chapter,
                                            item.indexInChapter,
                                            item.chapterPageCount,
                                        )
                                        is ReaderStripItem.Divider -> onVisiblePage(item.chapter, 0, 1)
                                        null -> Unit
                                    }
                                    if (readerSettings.seamlessReading) {
                                        maybePrefetchFromProgress(page, total)
                                    }
                                }
                        }
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = readerSettings.mode == ReaderMode.Rtl,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { toggleChrome() })
                                },
                            userScrollEnabled = true,
                        ) { page ->
                            when (val item = stripItems.getOrNull(page)) {
                                is ReaderStripItem.Page -> Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ReaderPageImage(
                                        item.page,
                                        ContentScale.Fit,
                                        zoomEnabled = readerSettings.doubleTapZoom,
                                        fillViewport = true,
                                    )
                                }
                                is ReaderStripItem.Divider -> ChapterDividerBand(
                                    title = item.chapter.title,
                                    fillViewport = true,
                                )
                                null -> Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }

                if (chromeVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.87f), Color.Transparent),
                                ),
                            )
                            .statusBarsPadding(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { nav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = Color.White)
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(onClick = { openTitle() }),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = titleName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = chapter?.title.orEmpty(),
                                    color = AppColors.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Outlined.Settings, null, tint = Color.White)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.87f)),
                                ),
                            )
                            .navigationBarsPadding()
                            .padding(bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!readerSettings.hidePageNumber && chapterPageCount > 0) {
                            Text(
                                text = "${pageIndex + 1} / $chapterPageCount",
                                color = AppColors.textSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { goChapter(-1) }) {
                                Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.cd_prev_chapter), tint = Color.White)
                            }
                            IconButton(onClick = { nav.navigate(Routes.chapters(sourceId, titleId)) }) {
                                Icon(Icons.Outlined.FormatListNumbered, stringResource(R.string.cd_chapters), tint = Color.White)
                            }
                            IconButton(onClick = { toggleBookmark() }) {
                                Icon(
                                    if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                    stringResource(R.string.cd_bookmark),
                                    tint = if (bookmarked) AppColors.accent else Color.White,
                                )
                            }
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Outlined.Tune, stringResource(R.string.cd_settings), tint = Color.White)
                            }
                            IconButton(onClick = { goChapter(1) }) {
                                Icon(Icons.Outlined.SkipNext, stringResource(R.string.cd_next_chapter), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (settingsOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            sheetState = sheetState,
            containerColor = AppColors.surface,
            dragHandle = null,
        ) {
            ReaderSettingsSheet(
                settings = readerSettings,
                onClose = { settingsOpen = false },
                onChange = { next ->
                    readerSettings = next
                    scope.launch { LumeApp.instance.prefs.setReaderSettings(next) }
                },
                onSaveForTitle = { next ->
                    readerSettings = next
                    scope.launch {
                        dao.upsertTitlePrefs(
                            com.lume.app.data.db.TitlePrefsEntity(
                                sourceId = sourceId,
                                titleId = titleId,
                                readerMode = next.mode.prefsIndex,
                                readerTheme = next.theme.ordinal,
                                gap = next.gap,
                            ),
                        )
                    }
                },
            )
        }
    }
}

private data class LoadedChapter(
    val chapter: Chapter,
    val pages: List<ComicPage>,
)

private sealed class ReaderStripItem {
    data class Divider(val chapter: Chapter) : ReaderStripItem()
    data class Page(
        val chapter: Chapter,
        val page: ComicPage,
        val indexInChapter: Int,
        val chapterPageCount: Int,
    ) : ReaderStripItem()
}

private fun buildReaderStrip(loaded: List<LoadedChapter>): List<ReaderStripItem> {
    val out = ArrayList<ReaderStripItem>(loaded.sumOf { it.pages.size + 1 })
    loaded.forEachIndexed { index, lc ->
        if (index > 0) out += ReaderStripItem.Divider(lc.chapter)
        lc.pages.forEachIndexed { pi, page ->
            out += ReaderStripItem.Page(lc.chapter, page, pi, lc.pages.size)
        }
    }
    return out
}

@Composable
private fun ChapterDividerBand(
    title: String,
    fillViewport: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fillViewport) Modifier.fillMaxSize() else Modifier)
            .padding(vertical = if (fillViewport) 0.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.surface.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(AppColors.accent.copy(alpha = 0.55f)),
            )
            Text(
                text = stringResource(R.string.reader_chapter_separator, title),
                color = AppColors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .weight(2f, fill = false),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(AppColors.accent.copy(alpha = 0.55f)),
            )
        }
    }
}

private class ReaderZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var viewport by mutableStateOf(IntSize.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun clamp(raw: Offset, s: Float = scale): Offset {
        if (s <= 1.01f || viewport.width == 0 || viewport.height == 0) return Offset.Zero
        val maxX = viewport.width * (s - 1f) / 2f
        val maxY = viewport.height * (s - 1f) / 2f
        return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
    }

    fun zoomBy(zoomChange: Float, focal: Offset) {
        // Slight amplification so pinch feels responsive without jumping.
        val amplified = 1f + (zoomChange - 1f) * 1.35f
        val old = scale
        val next = (old * amplified).coerceIn(1f, 5f)
        if (next <= 1.01f) {
            reset()
            return
        }
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        offset = if (old <= 1.01f) {
            (center - focal) * (next - 1f)
        } else {
            val ratio = next / old
            offset * ratio + (focal - center) * (1f - ratio)
        }
        scale = next
        offset = clamp(offset, next)
    }

    fun zoomTo(target: Float, focal: Offset) {
        val old = scale
        val next = target.coerceIn(1f, 5f)
        if (next <= 1.01f) {
            reset()
            return
        }
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        offset = if (old <= 1.01f) {
            (center - focal) * (next - 1f)
        } else {
            val ratio = next / old
            offset * ratio + (focal - center) * (1f - ratio)
        }
        scale = next
        offset = clamp(offset, next)
    }
}

@Composable
private fun ReaderZoomFrame(
    zoomEnabled: Boolean,
    resetKey: Any?,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (zoomModifier: Modifier, scale: Float) -> Unit,
) {
    val state = remember { ReaderZoomState() }
    val targetZoom = 2.5f

    LaunchedEffect(resetKey) { state.reset() }

    Box(
        modifier = modifier
            .onSizeChanged { state.viewport = it }
            // Keys must NOT include scale — restarting mid-pinch makes zoom weak/jerky.
            .pointerInput(zoomEnabled) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { pos ->
                        if (!zoomEnabled) return@detectTapGestures
                        if (state.scale > 1.1f) state.reset()
                        else state.zoomTo(targetZoom, pos)
                    },
                )
            }
            .pointerInput(zoomEnabled) {
                if (!zoomEnabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            pressed.size >= 2 -> {
                                val zoomChange = event.calculateZoom()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (kotlin.math.abs(zoomChange - 1f) > 0.001f) {
                                    state.zoomBy(zoomChange, centroid)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            pressed.size == 1 && state.scale > 1.01f -> {
                                val change = pressed[0]
                                val pan = change.positionChange()
                                if (pan != Offset.Zero) {
                                    state.offset = state.clamp(state.offset + pan)
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        content(
            Modifier.graphicsLayer {
                scaleX = state.scale
                scaleY = state.scale
                translationX = state.offset.x
                translationY = state.offset.y
                transformOrigin = TransformOrigin.Center
            },
            state.scale,
        )
    }
}

@Composable
private fun ReaderPageImage(
    page: ComicPage,
    contentScale: ContentScale = ContentScale.FillWidth,
    zoomEnabled: Boolean = false,
    fillViewport: Boolean = false,
) {
    val context = LocalContext.current
    val model = remember(page.imageUrl) {
        ImageRequest.Builder(context)
            .data(if (page.imageUrl.startsWith("http")) page.imageUrl else File(page.imageUrl))
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val state = remember(page.imageUrl) { ReaderZoomState() }
    val targetZoom = 2.5f
    var imageReady by remember(page.imageUrl) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .then(if (fillViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            // Reserve height so LazyColumn doesn't jump when the bitmap arrives mid-fling.
            .then(
                if (!fillViewport && !imageReady) {
                    Modifier.heightIn(min = 420.dp)
                } else {
                    Modifier
                },
            )
            .background(Color.Black.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            onSuccess = { imageReady = true },
            onError = { imageReady = true },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillViewport) Modifier.fillMaxSize() else Modifier)
                .onSizeChanged { state.viewport = it }
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.offset.x
                    translationY = state.offset.y
                    transformOrigin = TransformOrigin.Center
                }
                .then(
                    if (zoomEnabled) {
                        Modifier
                            .pointerInput(page.imageUrl) {
                                detectTapGestures(
                                    onDoubleTap = { pos ->
                                        if (state.scale > 1.1f) state.reset()
                                        else state.zoomTo(targetZoom, pos)
                                    },
                                )
                            }
                            .pointerInput(page.imageUrl) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        when {
                                            pressed.size >= 2 -> {
                                                val zoomChange = event.calculateZoom()
                                                val centroid = event.calculateCentroid(useCurrent = true)
                                                if (kotlin.math.abs(zoomChange - 1f) > 0.001f) {
                                                    state.zoomBy(zoomChange, centroid)
                                                }
                                                event.changes.forEach {
                                                    if (it.positionChanged()) it.consume()
                                                }
                                            }
                                            pressed.size == 1 && state.scale > 1.01f -> {
                                                val change = pressed[0]
                                                val pan = change.positionChange()
                                                if (pan != Offset.Zero) {
                                                    state.offset = state.clamp(state.offset + pan)
                                                    change.consume()
                                                }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                    } else {
                        Modifier
                    },
                ),
            contentScale = contentScale,
        )
    }
}

@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onClose: () -> Unit,
    onChange: (ReaderSettings) -> Unit,
    onSaveForTitle: (ReaderSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_title),
                color = AppColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, null, tint = AppColors.textPrimary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.reader_mode), color = AppColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeCard(
                selected = settings.mode == ReaderMode.Rtl,
                icon = Icons.Outlined.SwipeLeft,
                label = ReaderMode.Rtl.label,
                onClick = { onChange(settings.copy(mode = ReaderMode.Rtl)) },
                modifier = Modifier.weight(1f),
            )
            ModeCard(
                selected = settings.mode == ReaderMode.Webtoon,
                icon = Icons.Outlined.SwipeVertical,
                label = ReaderMode.Webtoon.label,
                onClick = { onChange(settings.copy(mode = ReaderMode.Webtoon)) },
                modifier = Modifier.weight(1f),
            )
            ModeCard(
                selected = settings.mode == ReaderMode.Ltr,
                icon = Icons.Outlined.SwipeRight,
                label = ReaderMode.Ltr.label,
                onClick = { onChange(settings.copy(mode = ReaderMode.Ltr)) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.reader_theme), color = AppColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChoice(
                label = stringResource(ReaderTheme.Light.labelRes),
                selected = settings.theme == ReaderTheme.Light,
                onClick = { onChange(settings.copy(theme = ReaderTheme.Light)) },
                modifier = Modifier.weight(1f),
            )
            ThemeChoice(
                label = stringResource(ReaderTheme.Dark.labelRes),
                selected = settings.theme == ReaderTheme.Dark,
                onClick = { onChange(settings.copy(theme = ReaderTheme.Dark)) },
                modifier = Modifier.weight(1f),
            )
            ThemeChoice(
                label = stringResource(ReaderTheme.System.labelRes),
                selected = settings.theme == ReaderTheme.System,
                onClick = { onChange(settings.copy(theme = ReaderTheme.System)) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.reader_gap, settings.gap.toInt()),
            color = AppColors.textSecondary,
            fontSize = 13.sp,
        )
        Slider(
            value = settings.gap,
            onValueChange = { onChange(settings.copy(gap = it)) },
            valueRange = 0f..32f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = AppColors.cardHigh,
            ),
        )
        SettingSwitchRow(
            label = stringResource(R.string.reader_double_tap_zoom),
            checked = settings.doubleTapZoom,
            onCheckedChange = { onChange(settings.copy(doubleTapZoom = it)) },
        )
        SettingSwitchRow(
            label = stringResource(R.string.reader_hide_page_number),
            checked = settings.hidePageNumber,
            onCheckedChange = { onChange(settings.copy(hidePageNumber = it)) },
        )
        SettingSwitchRow(
            label = stringResource(R.string.reader_seamless),
            checked = settings.seamlessReading,
            onCheckedChange = { onChange(settings.copy(seamlessReading = it)) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.reader_save_for_title),
            color = AppColors.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSaveForTitle(settings) }
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun ModeCard(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AppColors.cardHigh else AppColors.card)
            .then(
                if (selected) Modifier.border(1.dp, AppColors.accent, RoundedCornerShape(12.dp))
                else Modifier,
            )
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = AppColors.textPrimary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = if (selected) AppColors.accent else AppColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AppColors.cardHigh else AppColors.card)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = AppColors.textPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppColors.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppColors.accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = AppColors.cardHigh,
            ),
        )
    }
}
