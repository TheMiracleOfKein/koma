package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.lume.app.domain.GlobalSearchHit
import com.lume.app.domain.TitleSummary
import com.lume.app.ui.components.ErrorRetryBox
import com.lume.app.ui.components.LoadingBox
import com.lume.app.ui.components.QuickSearchBar
import com.lume.app.ui.components.SectionHeader
import com.lume.app.ui.components.TitleListRow
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.theme.AppColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.lume.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: NavHostController) {
    val resources = LocalResources.current
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) } // 0 current, 1 global
    var items by remember { mutableStateOf<List<TitleSummary>>(emptyList()) }
    var globalHits by remember { mutableStateOf<List<GlobalSearchHit>>(emptyList()) }
    var sourceId by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloudflareUri by remember { mutableStateOf<java.net.URI?>(null) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            loading = true
            error = null
            cloudflareUri = null
            searched = true
            items = emptyList()
            globalHits = emptyList()
            if (mode == 0) {
                runCatching {
                    val source = LumeApp.instance.sources.activeSource() ?: error(resources.getString(R.string.error_no_sources))
                    sourceId = source.manifest.id
                    source.search(q)
                }.onSuccess {
                    items = it.items
                    loading = false
                }.onFailure { e ->
                    loading = false
                    if (e is CloudflareException) {
                        cloudflareUri = e.uri
                        LumeApp.instance.http.requestChallenge(e.uri)
                        error = resources.getString(R.string.error_cloudflare_needed)
                    } else {
                        error = e.message ?: e.toString()
                    }
                }
            } else {
                runCatching {
                    coroutineScope {
                        val sources = LumeApp.instance.sources.sources.value
                        sources.map { source ->
                            async {
                                runCatching {
                                    source.search(q).items.map { title ->
                                        GlobalSearchHit(
                                            sourceId = source.manifest.id,
                                            sourceName = source.manifest.name,
                                            title = title,
                                        )
                                    }
                                }.getOrDefault(emptyList())
                            }
                        }.awaitAll().flatten()
                    }
                }.onSuccess {
                    globalHits = it
                    loading = false
                }.onFailure { e ->
                    loading = false
                    error = e.message ?: e.toString()
                }
            }
        }
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title), color = AppColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppColors.background),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                SearchModeTab(stringResource(R.string.search_mode_current), selected = mode == 0, modifier = Modifier.weight(1f)) {
                    mode = 0
                }
                SearchModeTab(stringResource(R.string.search_mode_all), selected = mode == 1, modifier = Modifier.weight(1f)) {
                    mode = 1
                }
            }
            QuickSearchBar(
                value = query,
                onValueChange = { query = it },
                onSubmit = { doSearch() },
                hint = stringResource(R.string.search_hint_title),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                loading -> LoadingBox()
                error != null -> ErrorRetryBox(
                    message = error!!,
                    onRetry = { doSearch() },
                    onCloudflare = cloudflareUri?.let { uri ->
                        { scope.launch { LumeApp.instance.http.requestChallenge(uri) } }
                    },
                )
                searched && mode == 0 && items.isEmpty() -> EmptySearch()
                searched && mode == 1 && globalHits.isEmpty() -> EmptySearch()
                mode == 0 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { item ->
                        TitleListRow(
                            title = item.title,
                            coverUrl = item.coverUrl,
                            subtitle = item.latestChapter,
                            typeLabel = item.typeLabel,
                            onClick = { nav.navigate(Routes.title(sourceId, item.id)) },
                        )
                    }
                }
                else -> {
                    val grouped = globalHits.groupBy { it.sourceName }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        grouped.forEach { (name, hits) ->
                            item(key = "header-$name") {
                                SectionHeader(title = name)
                            }
                            items(hits, key = { "${it.sourceId}:${it.title.id}" }) { hit ->
                                TitleListRow(
                                    title = hit.title.title,
                                    coverUrl = hit.title.coverUrl,
                                    subtitle = hit.title.latestChapter,
                                    typeLabel = hit.title.typeLabel,
                                    onClick = {
                                        nav.navigate(Routes.title(hit.sourceId, hit.title.id))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearch() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.search_empty), color = AppColors.textSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun SearchModeTab(
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
