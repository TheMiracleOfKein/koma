package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.lume.app.LumeApp
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.TitleSummary
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.TitleListRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.lume.app.R

@Composable
fun CatalogScreen(nav: NavHostController) {
    val resources = LocalResources.current
    var tab by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<TitleSummary>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasNext by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloudflareUri by remember { mutableStateOf<java.net.URI?>(null) }
    var sourceId by remember { mutableStateOf("") }
    var refreshEpoch by remember { mutableIntStateOf(0) }
    val bookmarks by LumeApp.instance.dao.observeBookmarks().collectAsState(initial = emptyList())
    val networkEpoch by LumeApp.instance.http.networkEpoch.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun load(reset: Boolean) {
        scope.launch {
            if (reset) {
                loading = true
                page = 1
                error = null
                cloudflareUri = null
            } else {
                loadingMore = true
            }
            val nextPage = if (reset) 1 else page + 1
            runCatching {
                val source = LumeApp.instance.sources.activeSource() ?: error(resources.getString(R.string.error_no_sources))
                sourceId = source.manifest.id
                when (tab) {
                    0 -> source.getPopular(nextPage)
                    else -> source.getLatest(nextPage)
                }
            }.onSuccess { result ->
                items = if (reset) result.items else items + result.items
                page = result.page
                hasNext = result.hasNext
                loading = false
                loadingMore = false
            }.onFailure { e ->
                loading = false
                loadingMore = false
                if (e is CloudflareException) {
                    cloudflareUri = e.uri
                    LumeApp.instance.http.requestChallenge(e.uri)
                    error = resources.getString(R.string.error_cloudflare_needed)
                } else {
                    error = e.message ?: e.toString()
                }
            }
        }
    }

    LaunchedEffect(networkEpoch, refreshEpoch, tab) {
        if (tab == 0 || tab == 1) load(reset = true)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasNext && !loadingMore && !loading && last >= items.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore, tab) {
        if ((tab == 0 || tab == 1) && shouldLoadMore) load(reset = false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .statusBarsPadding(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
            CatalogTab(stringResource(R.string.catalog_tab_popular), selected = tab == 0, modifier = Modifier.weight(1f)) { tab = 0 }
            CatalogTab(stringResource(R.string.catalog_tab_updates), selected = tab == 1, modifier = Modifier.weight(1f)) { tab = 1 }
            CatalogTab(stringResource(R.string.catalog_tab_library), selected = tab == 2, modifier = Modifier.weight(1f)) { tab = 2 }
        }

        when (tab) {
            0, 1 -> when {
                loading && items.isEmpty() -> LoadingBox()
                error != null && items.isEmpty() -> ErrorRetryBox(
                    message = error!!,
                    onRetry = { refreshEpoch++ },
                    onCloudflare = cloudflareUri?.let { uri ->
                        { scope.launch { LumeApp.instance.http.requestChallenge(uri) } }
                    },
                )
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { item ->
                        TitleListRow(
                            title = item.title,
                            coverUrl = item.coverUrl,
                            subtitle = item.latestChapter ?: item.updatedLabel,
                            typeLabel = item.typeLabel,
                            onClick = { nav.navigate(Routes.title(sourceId, item.id)) },
                        )
                    }
                    if (loadingMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = AppColors.accent)
                            }
                        }
                    }
                }
            }
            else -> {
                if (bookmarks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.library_empty), color = AppColors.textSecondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(bookmarks, key = { "${it.sourceId}:${it.titleId}" }) { item ->
                            TitleListRow(
                                title = item.titleName,
                                coverUrl = item.coverUrl,
                                typeLabel = item.typeLabel,
                                onClick = { nav.navigate(Routes.title(item.sourceId, item.titleId)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.accent else AppColors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}
