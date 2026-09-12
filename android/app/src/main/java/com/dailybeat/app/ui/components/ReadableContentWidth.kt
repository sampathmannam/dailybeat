package com.dailybeat.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest a column of text or a stat row is allowed to get, matching the prototype's
 * `screen-inner { width: min(100%, 840px) }`.
 *
 * On a phone this never binds. On a tablet — where the navigation rail takes the left edge and
 * leaves a very wide content area — it is the difference between a readable measure and a line of
 * body text stretched across 900dp, or a three-up stat row whose numbers sit at opposite edges of
 * the screen because `SpaceBetween` had that much room to work with.
 */
val ReadableContentMaxWidth: Dp = 840.dp

/**
 * Caps this content at [maxWidth] and centres it in whatever space the parent offered.
 *
 * Applied to every top-level screen's scroll container, so the constant lives in exactly one place
 * and cannot drift screen by screen. Below the cap this is a no-op: the child still receives the
 * full incoming constraints, including any `fillMaxWidth` minimum.
 */
fun Modifier.readableContentWidth(maxWidth: Dp = ReadableContentMaxWidth): Modifier =
    layout { measurable, constraints ->
        val available = constraints.maxWidth
        val childMaxWidth = readableChildMaxWidth(available, maxWidth.roundToPx())
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = minOf(constraints.minWidth, childMaxWidth),
                maxWidth = childMaxWidth,
            ),
        )
        // Report the full width back to the parent so the screen background still fills the pane;
        // only the content inside it is inset.
        val width = if (available == Constraints.Infinity) placeable.width else available
        layout(width, placeable.height) {
            placeable.placeRelative(readableHorizontalOffset(width, placeable.width), 0)
        }
    }

/**
 * The width budget the content is measured against. Split out from the layout modifier so the
 * arithmetic is testable on the JVM without a device.
 */
internal fun readableChildMaxWidth(availableWidth: Int, capWidth: Int): Int =
    if (availableWidth == Constraints.Infinity) availableWidth else minOf(availableWidth, capWidth)

/** Left inset that centres content of [childWidth] inside [availableWidth]. */
internal fun readableHorizontalOffset(availableWidth: Int, childWidth: Int): Int =
    ((availableWidth - childWidth) / 2).coerceAtLeast(0)
