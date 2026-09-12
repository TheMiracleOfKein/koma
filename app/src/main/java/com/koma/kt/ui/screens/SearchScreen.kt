package com.koma.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.koma.kt.domain.CloudflareException
import com.koma.kt.domain.TitleSummary
import com.koma.kt.ui.components.ErrorRetryBox
import com.koma.kt.ui.components.LoadingBox
import com.koma.kt.ui.components.QuickSearchBar
import com.koma.kt.ui.components.TitleListRow
import com.koma.kt.ui.nav.Routes
import com.koma.kt.ui.theme.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: NavHostController) {
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<TitleSummary>>(emptyList()) }
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
            runCatching {
                val source = KomaApp.instance.sources.activeSource() ?: error("Нет источников")
                sourceId = source.manifest.id
                source.search(q)
            }.onSuccess {
                items = it.items
                loading = false
            }.onFailure { e ->
                loading = false
                if (e is CloudflareException) {
                    cloudflareUri = e.uri
                    KomaApp.instance.http.requestChallenge(e.uri)
                    error = "Нужна проверка Cloudflare"
                } else {
                    error = e.message ?: e.toString()
                }
            }
        }
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = { Text("Поиск", color = AppColors.textPrimary) },
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
            QuickSearchBar(
                value = query,
                onValueChange = { query = it },
                onSubmit = { doSearch() },
                hint = "Поиск тайтла",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                loading -> LoadingBox()
                error != null -> ErrorRetryBox(
                    message = error!!,
                    onRetry = { doSearch() },
                    onCloudflare = cloudflareUri?.let { uri ->
                        { scope.launch { KomaApp.instance.http.requestChallenge(uri) } }
                    },
                )
                searched && items.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Ничего не найдено", color = AppColors.textSecondary, fontSize = 15.sp)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
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
            }
        }
    }
}
