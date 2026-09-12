package com.koma.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.koma.kt.KomaApp
import com.koma.kt.data.db.ProgressEntity
import com.koma.kt.domain.CloudflareException
import com.koma.kt.domain.HomeFeed
import com.koma.kt.ui.components.CoverImage
import com.koma.kt.ui.components.ErrorRetryBox
import com.koma.kt.ui.components.LoadingBox
import com.koma.kt.ui.components.QuickSearchBar
import com.koma.kt.ui.components.SectionHeader
import com.koma.kt.ui.components.TitleListRow
import com.koma.kt.ui.nav.Routes
import com.koma.kt.ui.theme.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    var feed by remember { mutableStateOf<HomeFeed?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloudflareUri by remember { mutableStateOf<java.net.URI?>(null) }
    var loading by remember { mutableStateOf(true) }
    var sourceId by remember { mutableStateOf("") }
    var refreshEpoch by remember { mutableIntStateOf(0) }
    val history by KomaApp.instance.dao.observeHistory().collectAsState(initial = emptyList())
    val networkEpoch by KomaApp.instance.http.networkEpoch.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(networkEpoch, refreshEpoch) {
        loading = true
        error = null
        cloudflareUri = null
        runCatching {
            val source = KomaApp.instance.sources.activeSource()
                ?: error("Нет активных источников")
            sourceId = source.manifest.id
            source.getHome()
        }.onSuccess {
            feed = it
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background),
    ) {
        QuickSearchBar(
            value = "",
            onValueChange = {},
            onSubmit = { nav.navigate(Routes.Search) },
            readOnly = true,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        when {
            loading && feed == null -> LoadingBox()
            error != null && feed == null -> ErrorRetryBox(
                message = error!!,
                onRetry = { refreshEpoch++ },
                onCloudflare = cloudflareUri?.let { uri ->
                    { scope.launch { KomaApp.instance.http.requestChallenge(uri) } }
                },
            )
            else -> {
                val ptrState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = loading,
                    onRefresh = { refreshEpoch++ },
                    state = ptrState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        val spotlight = feed?.spotlight.orEmpty()
                        if (spotlight.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.height(220.dp),
                            ) {
                                items(spotlight, key = { it.id }) { item ->
                                    Column(
                                        modifier = Modifier
                                            .width(128.dp)
                                            .clickable {
                                                nav.navigate(Routes.title(sourceId, item.id))
                                            },
                                    ) {
                                        Box {
                                            CoverImage(
                                                url = item.coverUrl,
                                                width = 128.dp,
                                                height = 180.dp,
                                                radius = 10.dp,
                                                sourceId = sourceId,
                                            )
                                            item.latestChapter?.let { ch ->
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(6.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color.Black.copy(alpha = 0.7f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                ) {
                                                    Text(ch, color = Color.White, fontSize = 10.sp, maxLines = 1)
                                                }
                                            }
                                        }
                                        Text(
                                            text = item.title,
                                            color = AppColors.textPrimary,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (history.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Продолжить читать",
                                    color = AppColors.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        scope.launch { KomaApp.instance.dao.clearAllProgress() }
                                    },
                                ) {
                                    Text("ОЧИСТИТЬ", color = AppColors.accent, fontSize = 12.sp)
                                }
                            }
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(history.take(20), key = { "${it.sourceId}:${it.titleId}" }) { item ->
                                    ContinueCard(item) {
                                        nav.navigate(
                                            Routes.reader(item.sourceId, item.titleId, item.chapterId),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SectionHeader(
                            title = "Сейчас читают",
                            onTap = { nav.navigate(Routes.Catalog) },
                        )
                        Text(
                            text = "Новинки",
                            color = AppColors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    items(feed?.latest.orEmpty(), key = { it.id }) { item ->
                        TitleListRow(
                            title = item.title,
                            coverUrl = item.coverUrl,
                            subtitle = item.latestChapter ?: item.updatedLabel,
                            typeLabel = item.typeLabel,
                            onClick = { nav.navigate(Routes.title(sourceId, item.id)) },
                        )
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(item: ProgressEntity, onClick: () -> Unit) {
    val progress = if (item.pageCount > 0) {
        (item.pageIndex + 1).toFloat() / item.pageCount
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick),
    ) {
        CoverImage(url = item.coverUrl, width = 110.dp, height = 150.dp, radius = 8.dp)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = AppColors.accent,
            trackColor = AppColors.card,
        )
        Text(
            text = item.titleName,
            color = AppColors.textPrimary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = item.chapterName,
            color = AppColors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
