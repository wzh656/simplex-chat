package chat.simplex.common.views.helpers

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import chat.simplex.common.simplexWindowState
import kotlin.math.max

private val MessageMenuWindowMargin = 8.dp
private val MessageMenuReactionHeight = 48.dp
private val MessageMenuReactionGap = 6.dp
private const val MessageMenuInTransitionDuration = 120
private const val MessageMenuOutTransitionDuration = 75
private const val MessageMenuClosedScale = 0.8f

@Composable
actual fun MessageDropdownMenu(
  showMenu: MutableState<Boolean>,
  anchor: MutableState<Offset?>,
  alignEnd: Boolean,
  reactions: @Composable (() -> Unit)?,
  dropdownMenuItems: @Composable () -> Unit,
) {
  val expandedState = remember { MutableTransitionState(false) }
  expandedState.targetState = showMenu.value
  val actionBounds = remember { mutableStateOf<IntRect?>(null) }
  val density = LocalDensity.current
  val windowSize = LocalWindowInfo.current.containerSize
  val windowMarginPx = with(density) { MessageMenuWindowMargin.roundToPx() }
  val reactionHeightPx = if (reactions == null) 0 else with(density) { MessageMenuReactionHeight.roundToPx() }
  val reactionGapPx = with(density) { MessageMenuReactionGap.roundToPx() }

  fun closeMenu() {
    showMenu.value = false
  }

  LaunchedEffect(expandedState.currentState, expandedState.targetState) {
    if (!expandedState.currentState && !expandedState.targetState) {
      actionBounds.value = null
      anchor.value = null
    }
  }
  LaunchedEffect(simplexWindowState.windowFocused.value) {
    if (!simplexWindowState.windowFocused.value && showMenu.value) closeMenu()
  }

  if (!expandedState.currentState && !expandedState.targetState) return

  val actionTransformOrigin = remember { mutableStateOf(TransformOrigin.Center) }
  val actionPositionProvider = remember(
    anchor.value,
    alignEnd,
    windowMarginPx,
    reactionHeightPx,
    reactionGapPx,
  ) {
    ActionMenuPositionProvider(
      anchor = anchor.value,
      alignEnd = alignEnd,
      windowMarginPx = windowMarginPx,
      reactionHeightPx = reactionHeightPx,
      reactionGapPx = reactionGapPx,
    ) { bounds, transformOrigin ->
      actionBounds.value = bounds
      actionTransformOrigin.value = transformOrigin
    }
  }

  var focusManager: FocusManager? by remember { mutableStateOf(null) }
  var inputModeManager: InputModeManager? by remember { mutableStateOf(null) }
  val maxActionHeight = with(density) {
    max(
      96.dp.roundToPx(),
      windowSize.height - 2 * windowMarginPx - reactionHeightPx -
          if (reactions == null) 0 else reactionGapPx,
    ).toDp()
  }

  Popup(
    popupPositionProvider = actionPositionProvider,
    onDismissRequest = ::closeMenu,
    properties = PopupProperties(
      focusable = true,
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
      clippingEnabled = true,
    ),
    onKeyEvent = { event ->
      if (event.type != KeyEventType.KeyDown) {
        false
      } else {
        when (event.key) {
          Key.DirectionDown -> {
            inputModeManager?.requestInputMode(InputMode.Keyboard)
            focusManager?.moveFocus(FocusDirection.Next) == true
          }
          Key.DirectionUp -> {
            inputModeManager?.requestInputMode(InputMode.Keyboard)
            focusManager?.moveFocus(FocusDirection.Previous) == true
          }
          else -> false
        }
      }
    },
  ) {
    focusManager = LocalFocusManager.current
    inputModeManager = LocalInputModeManager.current
    AnimatedMessageMenuSurface(
      expandedState = expandedState,
      transformOrigin = actionTransformOrigin.value,
      modifier = Modifier.widthIn(max = 280.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      shape = RoundedCornerShape(14.dp),
      tonalElevation = 3.dp,
      shadowElevation = 6.dp,
    ) {
      Column(
        Modifier
          .padding(vertical = 4.dp)
          .width(IntrinsicSize.Max)
          .heightIn(max = maxActionHeight)
          .verticalScroll(rememberScrollState())
      ) {
        dropdownMenuItems()
      }
    }
  }

  val positionedActionBounds = actionBounds.value
  if (reactions != null && positionedActionBounds != null) {
    val reactionTransformOrigin = remember { mutableStateOf(TransformOrigin.Center) }
    val reactionPositionProvider = remember(
      positionedActionBounds,
      alignEnd,
      windowMarginPx,
      reactionGapPx,
    ) {
      ReactionMenuPositionProvider(
        actionBounds = positionedActionBounds,
        alignEnd = alignEnd,
        windowMarginPx = windowMarginPx,
        reactionGapPx = reactionGapPx,
      ) { transformOrigin ->
        reactionTransformOrigin.value = transformOrigin
      }
    }
    Popup(
      popupPositionProvider = reactionPositionProvider,
      properties = PopupProperties(
        focusable = false,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        clippingEnabled = true,
      ),
    ) {
      AnimatedMessageMenuSurface(
        expandedState = expandedState,
        transformOrigin = reactionTransformOrigin.value,
        modifier = Modifier.width(IntrinsicSize.Max),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
      ) {
        reactions()
      }
    }
  }
}

private class ActionMenuPositionProvider(
  private val anchor: Offset?,
  private val alignEnd: Boolean,
  private val windowMarginPx: Int,
  private val reactionHeightPx: Int,
  private val reactionGapPx: Int,
  private val onPositioned: (IntRect, TransformOrigin) -> Unit,
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset {
    val anchorPoint = anchor?.round()?.let(anchorBounds.topLeft::plus)
      ?: IntOffset(if (alignEnd) anchorBounds.right else anchorBounds.left, anchorBounds.bottom)

    val minLeft = windowMarginPx
    val maxLeft = windowSize.width - windowMarginPx - popupContentSize.width
    val preferredLeft = if (alignEnd) anchorPoint.x - popupContentSize.width else anchorPoint.x
    val alternateLeft = if (alignEnd) anchorPoint.x else anchorPoint.x - popupContentSize.width
    val left = choosePosition(preferredLeft, alternateLeft, minLeft, maxLeft)

    val reactionReserve = if (reactionHeightPx == 0) 0 else reactionHeightPx + reactionGapPx
    val minTop = windowMarginPx + reactionReserve
    val maxTop = windowSize.height - windowMarginPx - popupContentSize.height
    val belowTop = anchorPoint.y
    val aboveTop = anchorPoint.y - popupContentSize.height
    val top = choosePosition(belowTop, aboveTop, minTop, maxTop)
    val bounds = IntRect(IntOffset(left, top), popupContentSize)
    onPositioned(bounds, menuTransformOrigin(anchorPoint, bounds))
    return bounds.topLeft
  }
}

private class ReactionMenuPositionProvider(
  private val actionBounds: IntRect,
  private val alignEnd: Boolean,
  private val windowMarginPx: Int,
  private val reactionGapPx: Int,
  private val onPositioned: (TransformOrigin) -> Unit,
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset {
    val preferredLeft = if (alignEnd) actionBounds.right - popupContentSize.width else actionBounds.left
    val minLeft = windowMarginPx
    val maxLeft = windowSize.width - windowMarginPx - popupContentSize.width
    val left = if (minLeft <= maxLeft) preferredLeft.coerceIn(minLeft, maxLeft) else minLeft
    val top = (actionBounds.top - reactionGapPx - popupContentSize.height).coerceAtLeast(windowMarginPx)
    onPositioned(menuTransformOrigin(actionBounds.topLeft, IntRect(IntOffset(left, top), popupContentSize)))
    return IntOffset(left, top)
  }
}

@Composable
private fun AnimatedMessageMenuSurface(
  expandedState: MutableTransitionState<Boolean>,
  transformOrigin: TransformOrigin,
  modifier: Modifier,
  shape: androidx.compose.ui.graphics.Shape,
  tonalElevation: androidx.compose.ui.unit.Dp,
  shadowElevation: androidx.compose.ui.unit.Dp,
  color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
  content: @Composable () -> Unit,
) {
  val transition = updateTransition(expandedState, "MessageMenu")
  val scale by transition.animateFloat(
    transitionSpec = {
      if (false isTransitioningTo true) tween(MessageMenuInTransitionDuration, easing = LinearOutSlowInEasing)
      else tween(1, delayMillis = MessageMenuOutTransitionDuration - 1)
    }
  ) { expanded -> if (expanded) 1f else MessageMenuClosedScale }
  val alpha by transition.animateFloat(
    transitionSpec = {
      if (false isTransitioningTo true) tween(30)
      else tween(MessageMenuOutTransitionDuration)
    }
  ) { expanded -> if (expanded) 1f else 0f }
  Surface(
    modifier = modifier.graphicsLayer {
      scaleX = scale
      scaleY = scale
      this.alpha = alpha
      this.transformOrigin = transformOrigin
    },
    color = color,
    shape = shape,
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
    content = content,
  )
}

private fun menuTransformOrigin(anchor: IntOffset, bounds: IntRect): TransformOrigin {
  val pivotX = if (bounds.width == 0) 0f else ((anchor.x - bounds.left).toFloat() / bounds.width).coerceIn(0f, 1f)
  val pivotY = if (bounds.height == 0) 0f else ((anchor.y - bounds.top).toFloat() / bounds.height).coerceIn(0f, 1f)
  return TransformOrigin(pivotX, pivotY)
}

private fun choosePosition(primary: Int, secondary: Int, min: Int, max: Int): Int = when {
  min > max -> min
  primary in min..max -> primary
  secondary in min..max -> secondary
  else -> primary.coerceIn(min, max)
}
