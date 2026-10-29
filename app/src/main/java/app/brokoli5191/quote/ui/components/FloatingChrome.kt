package app.brokoli5191.quote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

// Height of the main bottom navigation bar above the system navigation inset.
val BottomNavigationBarHeight = 81.dp

// Fade above the bottom navigation bar; drawn over content, which keeps 32dp extra bottom padding.
val BottomNavigationBarFade = 24.dp

/** Distance from the screen bottom to the top edge of the floating bottom navigation bar. */
@Composable
fun floatingBottomBarHeight(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BottomNavigationBarHeight

/**
 * Bottom padding for content that scrolls under the floating bottom bar, so its last item can
 * still be scrolled fully above the bar.
 */
@Composable
fun bottomBarContentPadding(): Dp = floatingBottomBarHeight() + 32.dp

/**
 * Backdrop behind chrome that floats over scrolling content (headers, tab switchers, bottom
 * buttons): the content blurs (when [hazeState] has blur enabled and the content is its source) and
 * fades into the background instead of ending at a hard edge. Same look as the Library header.
 * Size and position come from [modifier]; [fadeDown] fades toward the bottom (top chrome), otherwise
 * toward the top (bottom chrome). Over plain background it is invisible.
 */
@Composable
fun FloatingChromeBackdrop(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    fadeDown: Boolean = true
) {
    val background = MaterialTheme.colorScheme.background
    val stops = arrayOf(
        0f to background.copy(alpha = 0.92f),
        0.55f to background.copy(alpha = 0.68f),
        0.82f to background.copy(alpha = 0.22f),
        1f to Color.Transparent
    )
    Box(
        modifier = modifier
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(hazeState) {
                        blurRadius = 32.dp
                        tints = emptyList()
                        noiseFactor = 0f
                        mask = if (fadeDown) {
                            Brush.verticalGradient(0f to Color.Black, 0.72f to Color.Black, 1f to Color.Transparent)
                        } else {
                            Brush.verticalGradient(0f to Color.Transparent, 0.28f to Color.Black, 1f to Color.Black)
                        }
                    }
                } else {
                    Modifier
                }
            )
            .background(
                if (fadeDown) {
                    Brush.verticalGradient(*stops)
                } else {
                    Brush.verticalGradient(*stops.map { (offset, color) -> 1f - offset to color }.reversed().toTypedArray())
                }
            )
    )
}

/**
 * One line of items: shows, in order, the items that fit completely and drops the others instead
 * of wrapping, clipping or overflowing. An item too long for the space left is skipped, so a
 * shorter one after it can still fill the line. Takes no height when nothing fits.
 */
@Composable
fun SingleLineFitRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val gap = spacing.roundToPx()
        // Measured at their natural width, so an item too wide for the line is dropped, not squeezed.
        val placeables = measurables.map {
            it.measure(Constraints(maxHeight = constraints.maxHeight))
        }
        val shown = ArrayList<Pair<Int, Placeable>>(placeables.size)
        var x = 0
        for (placeable in placeables) {
            val start = if (shown.isEmpty()) 0 else x + gap
            if (start + placeable.width > constraints.maxWidth) continue
            shown += start to placeable
            x = start + placeable.width
        }
        val height = (shown.maxOfOrNull { it.second.height } ?: 0)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else x
        layout(width.coerceAtLeast(constraints.minWidth), height) {
            shown.forEach { (placeX, placeable) ->
                placeable.placeRelative(placeX, (height - placeable.height) / 2)
            }
        }
    }
}

/**
 * Widens a horizontally scrolling row by [padding] on both sides, so it scrolls out to the edges
 * of its padded parent instead of being cut off at the padding. Follow it with
 * `.horizontalScroll(...).padding(horizontal = padding)` to keep the first and last item aligned.
 */
fun Modifier.bleedHorizontally(padding: Dp): Modifier = layout { measurable, constraints ->
    val bleed = padding.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + bleed * 2,
            maxWidth = constraints.maxWidth + bleed * 2
        )
    )
    layout(placeable.width - bleed * 2, placeable.height) {
        placeable.place(-bleed, 0)
    }
}
