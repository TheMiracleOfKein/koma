package com.koma.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.koma.kt.KomaApp
import com.koma.kt.ui.components.TitleListRow
import com.koma.kt.ui.nav.Routes
import com.koma.kt.ui.theme.AppColors

@Composable
fun LibraryScreen(nav: NavHostController) {
    val bookmarks by KomaApp.instance.dao.observeBookmarks().collectAsState(initial = emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background),
        contentAlignment = Alignment.Center,
    ) {
        if (bookmarks.isEmpty()) {
            Text(
                text = "Нет закладок",
                color = AppColors.textSecondary,
                fontSize = 15.sp,
            )
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
