package com.koma.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.koma.kt.KomaApp
import com.koma.kt.data.db.BookmarkEntity
import com.koma.kt.domain.Chapter
import com.koma.kt.domain.CloudflareException
import com.koma.kt.domain.TitleDetails
import com.koma.kt.ui.components.CoverImage
import com.koma.kt.ui.components.ErrorRetryBox
import com.koma.kt.ui.components.LoadingBox
import com.koma.kt.ui.nav.Routes
import com.koma.kt.ui.theme.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleScreen(nav: NavHostController, sourceId: String, titleId: String) {
    var details by remember { mutableStateOf<TitleDetails?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloudflareUri by remember { mutableStateOf<java.net.URI?>(null) }
    var loading by remember { mutableStateOf(true) }
    var bookmarked by remember { mutableStateOf(false) }
    var refreshEpoch by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val dao = KomaApp.instance.dao
    val networkEpoch by KomaApp.instance.http.networkEpoch.collectAsState()
    var progressState by remember { mutableStateOf<com.koma.kt.data.db.ProgressEntity?>(null) }

    LaunchedEffect(sourceId, titleId, networkEpoch, refreshEpoch) {
        loading = true
        error = null
        cloudflareUri = null
        runCatching {
            val source = KomaApp.instance.sources.sourceById(sourceId)
                ?: error("Источник не найден")
            val d = source.getTitle(titleId)
            val c = source.getChapters(titleId)
            bookmarked = dao.getBookmark(sourceId, titleId) != null
            progressState = dao.getProgress(sourceId, titleId)
            d to c
        }.onSuccess { (d, c) ->
            details = d
            chapters = c
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

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = { Text(details?.title ?: "Тайтл", color = AppColors.textPrimary, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.textPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val d = details ?: return@IconButton
                            scope.launch {
                                if (bookmarked) {
                                    dao.deleteBookmark(sourceId, titleId)
                                    bookmarked = false
                                } else {
                                    dao.upsertBookmark(
                                        BookmarkEntity(
                                            sourceId = sourceId,
                                            titleId = titleId,
                                            titleName = d.title,
                                            coverUrl = d.coverUrl,
                                            typeLabel = d.typeLabel,
                                        ),
                                    )
                                    bookmarked = true
                                }
                            }
                        },
                    ) {
                        Icon(
                            if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Закладка",
                            tint = if (bookmarked) AppColors.accent else AppColors.textPrimary,
                        )
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
            else -> {
                val d = details!!
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Row {
                        CoverImage(
                            url = d.coverUrl,
                            width = 120.dp,
                            height = 170.dp,
                            radius = 8.dp,
                            sourceId = sourceId,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                d.title,
                                color = AppColors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            d.altTitle?.let {
                                Text(it, color = AppColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            d.status?.let {
                                Text("Статус: $it", color = AppColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                            d.typeLabel?.let {
                                Text(it, color = AppColors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val chId = progressState?.chapterId
                                        ?: chapters.firstOrNull()?.id
                                    if (chId != null) {
                                        nav.navigate(Routes.reader(sourceId, titleId, chId))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.accent,
                                    contentColor = Color.Black,
                                ),
                                enabled = chapters.isNotEmpty(),
                            ) {
                                Text(if (progressState != null) "Продолжить" else "Читать")
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { nav.navigate(Routes.chapters(sourceId, titleId)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Список глав (${chapters.size})", color = AppColors.textPrimary)
                    }
                    if (d.genres.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            d.genres.forEach { genre ->
                                Text(
                                    text = genre,
                                    color = AppColors.textSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(AppColors.card)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    d.description?.let {
                        Spacer(Modifier.height(16.dp))
                        Text("Описание", color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = AppColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}
