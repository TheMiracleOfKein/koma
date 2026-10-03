package com.koma.kt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koma.kt.R
import com.koma.kt.ui.theme.AppColors

@Composable
fun CoverImage(
    url: String?,
    width: Dp,
    height: Dp,
    radius: Dp = 8.dp,
    @Suppress("UNUSED_PARAMETER") sourceId: String? = null,
) {
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(radius))
            .background(AppColors.card),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = AppColors.textTertiary,
                modifier = Modifier.size(28.dp),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = null,
                error = null,
            )
        }
    }
}

@Composable
fun QuickSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    hint: String? = null,
) {
    val resolvedHint = hint ?: stringResource(R.string.search_hint_quick)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.search)
            .clickable(enabled = readOnly, onClick = onSubmit)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = AppColors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = resolvedHint,
                    color = AppColors.textTertiary,
                    fontSize = 14.sp,
                )
            }
            if (!readOnly) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = AppColors.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(AppColors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (value.isNotEmpty()) {
                Text(
                    text = value,
                    color = AppColors.textPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TitleListRow(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    typeLabel: String? = null,
    badge: String? = null,
    sourceId: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(url = coverUrl, width = 48.dp, height = 64.dp, radius = 6.dp, sourceId = sourceId)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = AppColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!badge.isNullOrBlank()) {
                    Text(
                        text = badge,
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppColors.accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            val line = listOfNotNull(typeLabel, subtitle).filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotEmpty()) {
                Text(
                    text = line,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun KomaLogo(
    selected: Boolean,
    size: Dp = 28.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(if (selected) AppColors.accent else AppColors.cardHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "K",
            color = if (selected) Color.Black else AppColors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.55f).sp,
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = AppColors.accent)
    }
}

@Composable
fun ErrorRetryBox(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onCloudflare: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = AppColors.textSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.accent,
                contentColor = Color.Black,
            ),
        ) {
            Text(stringResource(R.string.action_retry))
        }
        if (onCloudflare != null) {
            TextButton(onClick = onCloudflare) {
                Text("Cloudflare", color = AppColors.accent)
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onTap: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = AppColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
