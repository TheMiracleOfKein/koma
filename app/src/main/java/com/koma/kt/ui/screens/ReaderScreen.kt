package com.koma.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.koma.kt.KomaApp
import com.koma.kt.data.db.BookmarkEntity
import com.koma.kt.data.db.ChapterReadEntity
import com.koma.kt.data.db.ProgressEntity
import com.koma.kt.data.prefs.ReaderSettings
import com.koma.kt.domain.Chapter
import com.koma.kt.domain.CloudflareException
import com.koma.kt.domain.ComicPage
import com.koma.kt.domain.ReaderMode
import com.koma.kt.domain.ReaderTheme
import com.koma.kt.ui.components.ErrorRetryBox
import com.koma.kt.ui.components.LoadingBox
import com.koma.kt.ui.nav.Routes
import com.koma.kt.ui.theme.AppColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    nav: NavHostController,
    sourceId: String,
    titleId: String,
    chapterId: String,
) {
    var pages by remember { mutableStateOf<List<ComicPage>>(emptyList()) }
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
    val scope = rememberCoroutineScope()
    val dao = KomaApp.instance.dao
    val networkEpoch by KomaApp.instance.http.networkEpoch.collectAsState()
    val readerSettings by KomaApp.instance.prefs.readerSettings.collectAsState(initial = ReaderSettings())
    val systemDark = isSystemInDarkTheme()

    val readerBg = when (readerSettings.theme) {
        ReaderTheme.Light -> Color(0xFFF2F2F2)
        ReaderTheme.Dark -> Color.Black
        ReaderTheme.System -> if (systemDark) Color.Black else Color(0xFFF2F2F2)
    }

    LaunchedEffect(sourceId, titleId, chapterId, networkEpoch, refreshEpoch) {
        loading = true
        error = null
        cloudflareUri = null
        runCatching {
            val source = KomaApp.instance.sources.sourceById(sourceId) ?: error("Нет источника")
            val details = source.getTitle(titleId)
            titleName = details.title
            coverUrl = details.coverUrl
            typeLabel = details.typeLabel
            bookmarked = dao.getBookmark(sourceId, titleId) != null
            val list = source.getChapters(titleId)
            chapters = list
            val ch = list.firstOrNull { it.id == chapterId }
                ?: list.firstOrNull { it.id.trimEnd('/') == chapterId.trimEnd('/') }
                ?: error("Глава не найдена")
            chapter = ch
            val local = KomaApp.instance.downloads.localPages(sourceId, titleId, ch.id)
            val loaded = local ?: source.getPages(ch)
            val progress = dao.getProgress(sourceId, titleId)
            if (progress?.chapterId == ch.id) {
                pageIndex = progress.pageIndex.coerceIn(0, (loaded.size - 1).coerceAtLeast(0))
            }
            loaded
        }.onSuccess {
            pages = it
            loading = false
            chapter?.let { ch ->
                scope.launch {
                    dao.markChapterRead(
                        ChapterReadEntity(sourceId = sourceId, titleId = titleId, chapterId = ch.id),
                    )
                }
            }
        }.onFailure { e ->
            loading = false
            if (e is CloudflareException) {
                cloudflareUri = e.uri
                scope.launch { KomaApp.instance.http.requestChallenge(e.uri) }
                error = "Нужна проверка Cloudflare"
            } else {
                error = e.message ?: e.toString()
            }
        }
    }

    fun saveProgress(index: Int) {
        val ch = chapter ?: return
        if (pages.isEmpty()) return
        pageIndex = index
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
                    pageIndex = index,
                    pageCount = pages.size,
                ),
            )
        }
    }

    fun goChapter(delta: Int) {
        val ch = chapter ?: return
        val idx = chapters.indexOfFirst { it.id == ch.id }
        if (idx < 0) return
        val next = chapters.getOrNull(idx + delta) ?: return
        nav.navigate(Routes.reader(sourceId, titleId, next.id)) {
            popUpTo(Routes.reader(sourceId, titleId, chapterId)) { inclusive = true }
        }
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
                    { scope.launch { KomaApp.instance.http.requestChallenge(uri) } }
                },
            )
            else -> {
                val toggleChrome = { chromeVisible = !chromeVisible }
                when (readerSettings.mode) {
                    ReaderMode.Webtoon -> {
                        val listState = rememberLazyListState(
                            initialFirstVisibleItemIndex = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                        )
                        LaunchedEffect(listState) {
                            snapshotFlow { listState.firstVisibleItemIndex }
                                .distinctUntilChanged()
                                .collect { saveProgress(it) }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { toggleChrome() })
                                },
                            verticalArrangement = Arrangement.spacedBy(readerSettings.gap.dp),
                        ) {
                            itemsIndexed(pages, key = { i, p -> "${p.index}-$i" }) { _, page ->
                                ReaderPageImage(page, zoomEnabled = false)
                            }
                        }
                    }
                    ReaderMode.Ltr, ReaderMode.Rtl -> {
                        val pagerState = rememberPagerState(
                            initialPage = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                            pageCount = { pages.size },
                        )
                        LaunchedEffect(pagerState) {
                            snapshotFlow { pagerState.currentPage }
                                .distinctUntilChanged()
                                .collect { saveProgress(it) }
                        }
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = readerSettings.mode == ReaderMode.Rtl,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { toggleChrome() })
                                },
                        ) { page ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ReaderPageImage(
                                    pages[page],
                                    ContentScale.Fit,
                                    zoomEnabled = readerSettings.doubleTapZoom,
                                )
                            }
                        }
                    }
                }

                if (chromeVisible) {
                    // Top bar — title + chapter, gradient like Flutter
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
                                modifier = Modifier.weight(1f),
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

                    // Bottom bar — page + 5 icons like Flutter
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
                        if (!readerSettings.hidePageNumber && pages.isNotEmpty()) {
                            Text(
                                text = "${pageIndex + 1} / ${pages.size}",
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
                                Icon(Icons.Outlined.SkipPrevious, "Пред. глава", tint = Color.White)
                            }
                            IconButton(onClick = { nav.navigate(Routes.chapters(sourceId, titleId)) }) {
                                Icon(Icons.Outlined.FormatListNumbered, "Главы", tint = Color.White)
                            }
                            IconButton(onClick = { toggleBookmark() }) {
                                Icon(
                                    if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                    "Закладка",
                                    tint = if (bookmarked) AppColors.accent else Color.White,
                                )
                            }
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Outlined.Tune, "Настройки", tint = Color.White)
                            }
                            IconButton(onClick = { goChapter(1) }) {
                                Icon(Icons.Outlined.SkipNext, "След. глава", tint = Color.White)
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
                    scope.launch { KomaApp.instance.prefs.setReaderSettings(next) }
                },
            )
        }
    }
}

@Composable
private fun ReaderPageImage(
    page: ComicPage,
    contentScale: ContentScale = ContentScale.FillWidth,
    zoomEnabled: Boolean = false,
) {
    val model: Any = remember(page.imageUrl) {
        val path = page.imageUrl
        if (path.startsWith("http")) path else File(path)
    }
    var scale by remember { mutableFloatStateOf(1f) }
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (zoomEnabled) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = if (scale > 1.1f) 1f else 2.5f
                                },
                            )
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                } else {
                    Modifier
                },
            ),
        contentScale = contentScale,
    )
}

@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onClose: () -> Unit,
    onChange: (ReaderSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Настройки",
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
        Text("Режим читалки", color = AppColors.textSecondary, fontSize = 13.sp)
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
        Text("Тема читалки", color = AppColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChoice(
                label = ReaderTheme.Light.label,
                selected = settings.theme == ReaderTheme.Light,
                onClick = { onChange(settings.copy(theme = ReaderTheme.Light)) },
                modifier = Modifier.weight(1f),
            )
            ThemeChoice(
                label = ReaderTheme.Dark.label,
                selected = settings.theme == ReaderTheme.Dark,
                onClick = { onChange(settings.copy(theme = ReaderTheme.Dark)) },
                modifier = Modifier.weight(1f),
            )
            ThemeChoice(
                label = ReaderTheme.System.label,
                selected = settings.theme == ReaderTheme.System,
                onClick = { onChange(settings.copy(theme = ReaderTheme.System)) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Отступ между изображениями ${settings.gap.toInt()}px",
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
            label = "Увеличение двойным нажатием",
            checked = settings.doubleTapZoom,
            onCheckedChange = { onChange(settings.copy(doubleTapZoom = it)) },
        )
        SettingSwitchRow(
            label = "Скрыть номер страниц",
            checked = settings.hidePageNumber,
            onCheckedChange = { onChange(settings.copy(hidePageNumber = it)) },
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
