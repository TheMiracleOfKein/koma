package com.koma.kt.ui.nav

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clips destination content with display-like rounded corners while the screen is
 * exiting (including Navigation Compose predictive back). At rest the radius is 0,
 * so full-screen layouts stay edge-to-edge.
 */
@Composable
fun AnimatedVisibilityScope.PredictiveBackClip(
    content: @Composable () -> Unit,
) {
    val maxCornerRadius = rememberDisplayCornerRadius()
    val cornerRadius by transition.animateDp(label = "predictiveBackCorners") { state ->
        when (state) {
            EnterExitState.Visible -> 0.dp
            EnterExitState.PreEnter -> 0.dp
            EnterExitState.PostExit -> maxCornerRadius
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val radiusPx = cornerRadius.toPx()
                if (radiusPx > 0f) {
                    shape = RoundedCornerShape(radiusPx)
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                }
            },
    ) {
        content()
    }
}

@Composable
private fun rememberDisplayCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val fallback = 28.dp
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@remember fallback
        val insets = view.rootWindowInsets ?: return@remember fallback
        val maxPx = listOf(
            RoundedCorner.POSITION_TOP_LEFT,
            RoundedCorner.POSITION_TOP_RIGHT,
            RoundedCorner.POSITION_BOTTOM_RIGHT,
            RoundedCorner.POSITION_BOTTOM_LEFT,
        ).mapNotNull { pos -> insets.getRoundedCorner(pos)?.radius }
            .maxOrNull()
        if (maxPx != null && maxPx > 0) {
            with(density) { maxPx.toDp() }
        } else {
            fallback
        }
    }
}
