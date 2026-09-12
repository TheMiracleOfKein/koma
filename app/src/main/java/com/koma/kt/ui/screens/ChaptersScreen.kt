package com.koma.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.koma.kt.KomaApp
import com.koma.kt.domain.Chapter
import com.koma.kt.domain.CloudflareException
import com.koma.kt.domain.TitleDetails
import com.koma.kt.ui.components.ErrorRetryBox
import com.koma.kt.ui.components.LoadingBox
import com.koma.kt.ui.nav.Routes
import com.koma.kt.ui.theme.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersScreen(nav: NavHostController, sourceId: String, titleId: String) {
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var details by remember { mutableStateOf<TitleDetails?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloudflareUri by remember { mutableStateOf<java.net.URI?>(null) }
    var ascending by remember { mutableStateOf(false) }
    var readIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentChapterId by remember { mutableStateOf<String?>(null) }
    var refreshEpoch by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val networkEpoch by KomaApp.instance.http.networkEpoch.collectAsState()
    val downloadProgress by KomaApp.instance.downloads.progress.collectAsState()

    LaunchedEffect(sourceId, titleId, networkEpoch, refreshEpoch) {
        loading = true
        error = null
        cloudflareUri = null
        runCatching {
            val source = KomaApp.instance.sources.sourceById(sourceId) ?: error("Нет источника")
            details = source.getTitle(titleId)
            val list = source.getChapters(titleId)
            readIds = KomaApp.instance.dao.readChapterIds(sourceId, titleId).toSet()
            downloadedIds = KomaApp.instance.downloads.downloadedChapterIds(sourceId, titleId)
            currentChapterId = KomaApp.instance.dao.getProgress(sourceId, titleId)?.chapterId
            list
        }.onSuccess {
            chapters = it
            loading = false
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

    val displayed = remember(chapters, ascending) {
        if (ascending) chapters else chapters.asReversed()
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = { Text("Главы", color = AppColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { ascending = !ascending }) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = "Сортировка", tint = AppColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        when {
            loading -> LoadingBox(Modifier.padding(padding))
            error != null -> ErrorRetryBox(
                message = error!!,
                onRetry = { refreshEpoch++ },
                onCloudflare = cloudflareUri?.let { uri ->
                    { scope.launch { KomaApp.instance.http.requestChallenge(uri) } }
                },
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(AppColors.background),
            ) {
                items(displayed, key = { it.id }) { chapter ->
                    val key = KomaApp.instance.downloads.keyOf(sourceId, titleId, chapter.id)
                    val progress = downloadProgress[key]
                    val isDownloaded = chapter.id in downloadedIds
                    val isRead = chapter.id in readIds
                    val isCurrent = chapter.id == currentChapterId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                nav.navigate(Routes.reader(sourceId, titleId, chapter.id))
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(chapter.title, color = AppColors.textPrimary, fontSize = 14.sp)
                            chapter.dateLabel?.let {
                                Text(it, color = AppColors.textTertiary, fontSize = 11.sp)
                            }
                        }
                        when {
                            isCurrent -> Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = "Текущая",
                                tint = AppColors.accent,
                                modifier = Modifier.size(20.dp).padding(end = 4.dp),
                            )
                            isRead -> Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Прочитано",
                                tint = AppColors.textSecondary,
                                modifier = Modifier.size(18.dp).padding(end = 4.dp),
                            )
                        }
                        when {
                            progress != null -> Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    color = AppColors.accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            isDownloaded -> Icon(
                                Icons.Outlined.DownloadDone,
                                contentDescription = "Скачано",
                                tint = AppColors.accent,
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(8.dp),
                            )
                            else -> IconButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            val source = KomaApp.instance.sources.sourceById(sourceId)
                                                ?: return@launch
                                            KomaApp.instance.downloads.downloadChapter(
                                                source = source,
                                                titleId = titleId,
                                                title = details,
                                                chapter = chapter,
                                            )
                                            downloadedIds = downloadedIds + chapter.id
                                        }.onFailure { e ->
                                            if (e is CloudflareException) {
                                                KomaApp.instance.http.requestChallenge(e.uri)
                                            }
                                        }
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.Download, null, tint = AppColors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
