package chat.simplex.common.views.helpers

/*
  * This was adapted from google example of drag and drop for Jetpack Compose
  * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/integration-tests/foundation-demos/src/main/java/androidx/compose/foundation/demos/LazyColumnDragAndDropDemo.kt
 */

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@Composable
fun rememberDragDropState(
  lazyListState: LazyListState,
  canDrag: (index: Int) -> Boolean = { true },
  onDragFinished: (cancelled: Boolean) -> Unit = {},
  onMove: (Int, Int) -> Unit
): DragDropState {
  val scope = rememberCoroutineScope()
  val currentCanDrag = rememberUpdatedState(canDrag)
  val currentOnMove = rememberUpdatedState(onMove)
  val currentOnDragFinished = rememberUpdatedState(onDragFinished)
  val state = remember(lazyListState) {
    DragDropState(
      state = lazyListState,
      canDrag = { index -> currentCanDrag.value(index) },
      onMove = { from, to -> currentOnMove.value(from, to) },
      onDragFinished = { cancelled -> currentOnDragFinished.value(cancelled) },
      scope = scope
    )
  }
  LaunchedEffect(state) {
    while (true) {
      val diff = state.scrollChannel.receive()
      lazyListState.scrollBy(diff)
    }
  }
  return state
}

class DragDropState
internal constructor(
  private val state: LazyListState,
  private val scope: CoroutineScope,
  private val canDrag: (index: Int) -> Boolean,
  private val onDragFinished: (cancelled: Boolean) -> Unit,
  private val onMove: (Int, Int) -> Unit
) {
  var draggingItemIndex by mutableStateOf<Int?>(null)
    private set

  internal val scrollChannel = Channel<Float>()

  private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
  private var draggingItemInitialOffset by mutableIntStateOf(0)
  internal val draggingItemOffset: Float
    get() =
      draggingItemLayoutInfo?.let { item ->
        draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
      } ?: 0f

  private val draggingItemLayoutInfo: LazyListItemInfo?
    get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

  internal var previousIndexOfDraggedItem by mutableStateOf<Int?>(null)
    private set

  internal var previousItemOffset = Animatable(0f)
    private set

  internal fun onDragStart(offset: Offset) {
    val touchY = offset.y.toInt()
    state.layoutInfo.visibleItemsInfo
      .firstOrNull { canDrag(it.index) && touchY in it.offset until it.offsetEnd }
      ?.let(::startDragging)
  }

  internal fun onDragStart(index: Int) {
    if (canDrag(index)) state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let(::startDragging)
  }

  private fun startDragging(item: LazyListItemInfo) {
    draggingItemIndex = item.index
    draggingItemInitialOffset = item.offset
  }

  internal fun onDragInterrupted(cancelled: Boolean) {
    val wasDragging = draggingItemIndex != null
    if (wasDragging) {
      previousIndexOfDraggedItem = draggingItemIndex
      val startOffset = draggingItemOffset
      scope.launch {
        previousItemOffset.snapTo(startOffset)
        previousItemOffset.animateTo(
          0f,
          spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 1f)
        )
        previousIndexOfDraggedItem = null
      }
    }
    draggingItemDraggedDelta = 0f
    draggingItemIndex = null
    draggingItemInitialOffset = 0
    if (wasDragging) onDragFinished(cancelled)
  }

  internal fun onDrag(offset: Offset) {
    draggingItemDraggedDelta += offset.y

    val draggingItem = draggingItemLayoutInfo ?: return
    val startOffset = draggingItem.offset + draggingItemOffset
    val endOffset = startOffset + draggingItem.size
    val middleOffset = startOffset + (endOffset - startOffset) / 2f

    val targetItem =
      state.layoutInfo.visibleItemsInfo.find { item ->
        canDrag(item.index) && middleOffset.toInt() in item.offset..item.offsetEnd &&
            draggingItem.index != item.index
      }
    if (targetItem != null) {
      if (
        draggingItem.index == state.firstVisibleItemIndex ||
        targetItem.index == state.firstVisibleItemIndex
      ) {
        state.requestScrollToItem(
          state.firstVisibleItemIndex,
          state.firstVisibleItemScrollOffset
        )
      }
      onMove.invoke(draggingItem.index, targetItem.index)
      draggingItemIndex = targetItem.index
    } else {
      val overscroll =
        when {
          draggingItemDraggedDelta > 0 ->
            (endOffset - state.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
          draggingItemDraggedDelta < 0 ->
            (startOffset - state.layoutInfo.viewportStartOffset).coerceAtMost(0f)
          else -> 0f
        }
      if (overscroll != 0f) {
        scrollChannel.trySend(overscroll)
      }
    }
  }

  private val LazyListItemInfo.offsetEnd: Int
    get() = this.offset + this.size
}

fun Modifier.dragContainer(dragDropState: DragDropState): Modifier {
  return pointerInput(dragDropState) {
    detectDragGesturesAfterLongPress(
      onDrag = { change, offset ->
        change.consume()
        dragDropState.onDrag(offset = offset)
      },
      onDragStart = { offset -> dragDropState.onDragStart(offset) },
      onDragEnd = { dragDropState.onDragInterrupted(cancelled = false) },
      onDragCancel = { dragDropState.onDragInterrupted(cancelled = true) }
    )
  }
}

fun Modifier.dragHandle(dragDropState: DragDropState, itemIndex: Int): Modifier {
  return pointerInput(dragDropState, itemIndex) {
    detectDragGestures(
      onDrag = { change, offset ->
        change.consume()
        dragDropState.onDrag(offset = offset)
      },
      onDragStart = { dragDropState.onDragStart(itemIndex) },
      onDragEnd = { dragDropState.onDragInterrupted(cancelled = false) },
      onDragCancel = { dragDropState.onDragInterrupted(cancelled = true) }
    )
  }
}

@Composable
fun LazyItemScope.DraggableItem(
  dragDropState: DragDropState,
  index: Int,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.(isDragging: Boolean) -> Unit
) {
  val dragging = index == dragDropState.draggingItemIndex
  val draggingModifier =
    if (dragging) {
      Modifier.zIndex(1f).graphicsLayer { translationY = dragDropState.draggingItemOffset }
    } else if (index == dragDropState.previousIndexOfDraggedItem) {
      Modifier.zIndex(1f).graphicsLayer {
        translationY = dragDropState.previousItemOffset.value
      }
    } else {
      Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
    }
  Column(modifier = modifier.then(draggingModifier)) { content(dragging) }
}
