package com.madtitan94.transactionsparser.core.designsystem.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex

/**
 * A short list whose rows can be dragged into a different order.
 *
 * A plain [Column] rather than a `LazyColumn`: the lists this exists for — the dashboards on Home,
 * the widgets on one dashboard — are single digits long, and measuring every row up front is what
 * makes the drag arithmetic below exact. A lazy list would only have measured the rows currently on
 * screen, so dragging toward an unmeasured neighbour would have to guess its height.
 *
 * Rows are reordered *during* the drag rather than on release: [onMove] fires the moment the
 * dragged row passes the midpoint of its neighbour, so the list under the finger always shows what
 * dropping now would produce. That means the caller's list is the single source of truth for order
 * and this composable holds no shadow copy of it.
 *
 * Dragging is not the only way through. Each handle also carries "move up" and "move down"
 * accessibility actions, because a drag gesture is unreachable with a screen reader and reordering
 * would otherwise be a feature only some users have.
 *
 * [content] receives the modifier that turns something into the drag handle — usually a grip icon.
 * Making the handle explicit rather than the whole row keeps a row's own taps and switches working.
 *
 * Rows are deliberately not keyed. Compose identifies these children by position, which is what lets
 * a drag survive the reordering it is causing — a keyed child would be moved out from under the
 * gesture that is still running on it.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    onMove: (from: Int, to: Int) -> Unit,
    moveUpLabel: String,
    moveDownLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable (item: T, dragHandle: Modifier) -> Unit
) {
    // Heights in pixels, by position. Swapped alongside the items on every move so the arithmetic
    // keeps working through a long drag across rows of different heights — a custom dashboard name
    // can wrap to two lines while the one above it does not.
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // The gesture below runs for the whole duration of a drag and must not be restarted while it
    // does, so it reads the list and the callback through these rather than capturing them. Keying
    // the `pointerInput` on the list instead looks tidier and breaks the feature: reordering
    // changes the list, which would cancel the very drag that caused the reorder, and every drag
    // would move exactly one row however far the finger travelled.
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)

    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            val isDragging = index == draggingIndex

            Column(
                modifier = Modifier
                    .onSizeChanged { heights[index] = it.height }
                    // The dragged row is lifted out of the flow visually only: its slot stays where
                    // it is, and the rows it passes have already moved to make room.
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
            ) {
                val handle = Modifier
                    .semantics {
                        customActions = buildList {
                            if (index > 0) {
                                add(CustomAccessibilityAction(moveUpLabel) { onMove(index, index - 1); true })
                            }
                            if (index < items.lastIndex) {
                                add(CustomAccessibilityAction(moveDownLabel) { onMove(index, index + 1); true })
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                draggingIndex?.let { from ->
                                    dragOffset += amount.y
                                    val settled = settle(
                                        from = from,
                                        offset = dragOffset,
                                        lastIndex = currentItems.lastIndex,
                                        heights = heights,
                                        onMove = currentOnMove
                                    )
                                    draggingIndex = settled.index
                                    dragOffset = settled.offset
                                }
                            }
                        )
                    }

                content(item, handle)
            }
        }
    }
}

private data class DragPosition(val index: Int, val offset: Float)

/**
 * Consumes as much of the accumulated drag as has crossed a neighbour's midpoint.
 *
 * The offset is reduced by each row stepped over rather than reset, so a fast drag that crosses
 * three rows in one frame lands three rows down instead of one — and the leftover keeps the row
 * under the finger where the finger actually is.
 */
private fun settle(
    from: Int,
    offset: Float,
    lastIndex: Int,
    heights: MutableMap<Int, Int>,
    onMove: (Int, Int) -> Unit
): DragPosition {
    var index = from
    var remaining = offset

    while (remaining > 0 && index < lastIndex) {
        val next = heights[index + 1] ?: break
        if (remaining <= next / 2f) break
        onMove(index, index + 1)
        heights.swap(index, index + 1)
        remaining -= next
        index += 1
    }
    while (remaining < 0 && index > 0) {
        val previous = heights[index - 1] ?: break
        if (-remaining <= previous / 2f) break
        onMove(index, index - 1)
        heights.swap(index, index - 1)
        remaining += previous
        index -= 1
    }
    return DragPosition(index, remaining)
}

/**
 * Keeps the measured heights lined up with the rows they belong to.
 *
 * `onSizeChanged` will correct this on the next frame anyway, but a fast drag crosses several rows
 * within one frame and every step after the first reads these values — so without the swap a long
 * drag over rows of unequal height drifts away from the finger.
 */
private fun MutableMap<Int, Int>.swap(a: Int, b: Int) {
    val first = this[a]
    val second = this[b]
    if (second == null) remove(a) else this[a] = second
    if (first == null) remove(b) else this[b] = first
}
