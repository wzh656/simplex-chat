package chat.simplex.common.views.helpers

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.*
import LocalCardScreen
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.chatlist.StatusBarBackground
import chat.simplex.common.views.onboarding.OnboardingStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
internal val LocalModalViewBackground = compositionLocalOf { Color.Unspecified }

@Composable
fun ModalSurface(
  modifier: Modifier = Modifier,
  background: Color = Color.Unspecified,
  content: @Composable () -> Unit,
) {
  val surfaceColor = if (background == Color.Unspecified) MaterialTheme.colorScheme.surface else background
  Surface(
    modifier.fillMaxSize(),
    color = surfaceColor,
    contentColor = if (surfaceColor.luminance() < 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,
    content = content,
  )
}


@Composable
fun ModalView(
  close: () -> Unit,
  showClose: Boolean = true,
  showAppBar: Boolean = true,
  enableClose: Boolean = true,
  background: Color = Color.Unspecified,
  cardScreen: Boolean = false,
  modifier: Modifier = Modifier,
  showSearch: Boolean = false,
  searchAlwaysVisible: Boolean = false,
  onSearchValueChanged: (String) -> Unit = {},
  endButtons: @Composable RowScope.() -> Unit = {},
  appBar: @Composable (BoxScope.() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  if (showClose && showAppBar) {
    BackHandler(enabled = enableClose, onBack = close)
  }
  val oneHandUI = remember { derivedStateOf { if (appPrefs.onboardingStage.state.value == OnboardingStage.OnboardingComplete) appPrefs.oneHandUI.state.value else false } }
  val inheritedBackground = LocalModalViewBackground.current
  val bgOverride = when {
    cardScreen -> canvasColorForCurrentTheme()
    background != Color.Unspecified -> background
    inheritedBackground != Color.Unspecified -> inheritedBackground
    else -> null
  }
  val surfaceColor = bgOverride ?: MaterialTheme.colorScheme.surface
  Surface(
    Modifier.fillMaxSize(),
    color = surfaceColor,
    contentColor = if (surfaceColor.luminance() < 0.5f) Color.White else MaterialTheme.colorScheme.onSurface
  ) {
    CompositionLocalProvider(LocalCardScreen provides cardScreen) {
    Box(Modifier.themedBackground(overrideColor = bgOverride)) {
      Box(modifier = modifier) {
        content()
      }
      if (showAppBar) {
        if (oneHandUI.value) {
          StatusBarBackground()
        }
        Box(Modifier.align(if (oneHandUI.value) Alignment.BottomStart else Alignment.TopStart)) {
          if (appBar != null) {
            appBar()
          } else {
            DefaultAppBar(
              navigationButton = if (showClose) {
                { NavigationButtonBack(onButtonClicked = if (enableClose) close else null) }
              } else null,
              onTop = !oneHandUI.value,
              showSearch = showSearch,
              searchAlwaysVisible = searchAlwaysVisible,
              onSearchValueChanged = onSearchValueChanged,
              buttons = endButtons
            )
          }
        }
      }
    }
    }
  }
}

enum class ModalPlacement {
  START, CENTER, END, FULLSCREEN
}

class ModalData(val keyboardCoversBar: Boolean = true) {
  private val state = mutableMapOf<String, MutableState<Any?>>()
  fun <T> stateGetOrPut (key: String, default: () -> T): MutableState<T> =
    state.getOrPut(key) { mutableStateOf(default() as Any) } as MutableState<T>

  fun <T> stateGetOrPutNullable (key: String, default: () -> T?): MutableState<T?> =
    state.getOrPut(key) { mutableStateOf(default() as Any?) } as MutableState<T?>

  val appBarHandler = AppBarHandler(keyboardCoversBar = keyboardCoversBar)
}

enum class ModalViewId {
  SECONDARY_CHAT,
  CONTEXT_USER_PICKER_INCOGNITO
}

class ModalManager(private val placement: ModalPlacement? = null) {
  data class ModalViewHolder(
    val id: ModalViewId?,
    val animated: Boolean,
    val data: ModalData,
    val modal: @Composable ModalData.(close: () -> Unit) -> Unit
  )

  private val modalViews = arrayListOf<ModalViewHolder>()
  private val _modalCount = mutableStateOf(0)
  val modalCount: State<Int> = _modalCount
  private val toRemove = mutableSetOf<Int>()
  private val endVisible = mutableStateOf(false)
  private val drawerClosing = mutableStateOf(false)
  private var oldViewChanging = AtomicBoolean(false)
  // Don't use mutableStateOf() here, because it produces this if showing from SimpleXAPI.startChat():
  // java.lang.IllegalStateException: Reading a state that was created after the snapshot was taken or in a snapshot that has not yet been applied
  private var passcodeView: MutableStateFlow<(@Composable (close: () -> Unit) -> Unit)?> = MutableStateFlow(null)
  private var onTimePasscodeView: MutableStateFlow<(@Composable (close: () -> Unit) -> Unit)?> = MutableStateFlow(null)

  fun hasModalOpen(id: ModalViewId): Boolean = modalViews.any { it.id == id }

  fun isLastModalOpen(id: ModalViewId): Boolean = modalViews.lastOrNull()?.id == id

  fun showModal(settings: Boolean = false, showClose: Boolean = true, id: ModalViewId? = null, forceAnimated: Boolean = false, cardScreen: Boolean = false, endButtons: @Composable RowScope.() -> Unit = {}, content: @Composable ModalData.() -> Unit) {
    showCustomModal(id = id, forceAnimated = forceAnimated) { close ->
      ModalView(close, showClose = showClose, cardScreen = cardScreen, endButtons = endButtons, content = { content() })
    }
  }

  fun showModalCloseable(settings: Boolean = false, showClose: Boolean = true, id: ModalViewId? = null, cardScreen: Boolean = false, endButtons: @Composable RowScope.() -> Unit = {}, content: @Composable ModalData.(close: () -> Unit) -> Unit) {
    showCustomModal(id = id) { close ->
      ModalView(close, showClose = showClose, cardScreen = cardScreen, endButtons = endButtons, content = { content(close) })
    }
  }

  fun showCustomModal(animated: Boolean = true, keyboardCoversBar: Boolean = true, id: ModalViewId? = null, forceAnimated: Boolean = false, modal: @Composable ModalData.(close: () -> Unit) -> Unit) {
    Log.d(TAG, "ModalManager.showCustomModal")
    val data = ModalData(keyboardCoversBar = keyboardCoversBar)
    // Remove an interrupted exit before adding the next page.
    if (toRemove.isNotEmpty()) {
      runAtomically {
        toRemove.sortedDescending().forEach { index ->
          if (index in modalViews.indices) modalViews.removeAt(index)
        }
        toRemove.clear()
      }
    }
    drawerClosing.value = false
    val anim = if (appPlatform.isAndroid) animated else
      (animated && modalCount.value > 0) || forceAnimated
    modalViews.add(ModalViewHolder(id, anim, data, modal))
    _modalCount.value = modalViews.size - toRemove.size
    if (placement == ModalPlacement.END) endVisible.value = true

    if (placement == ModalPlacement.CENTER) {
      ChatModel.chatId.value = null
    }
  }

  fun showPasscodeCustomModal(oneTime: Boolean, modal: @Composable (close: () -> Unit) -> Unit) {
    Log.d(TAG, "ModalManager.showPasscodeCustomModal, oneTime: $oneTime")
    if (oneTime) {
      onTimePasscodeView.value = modal
    } else {
      passcodeView.value = modal
    }
  }

  fun hasModalsOpen() = if (placement == ModalPlacement.END) endVisible.value else modalCount.value > 0

  val hasModalsOpen: Boolean
  @Composable get () = if (placement == ModalPlacement.END) endVisible.value else remember { modalCount }.value > 0
  fun openModalCount() = modalCount.value

  fun closeModal() {
    if (modalViews.isNotEmpty()) {
      val lastModal = modalViews.lastOrNull()
      if (lastModal != null) {
        if (lastModal.id == ModalViewId.SECONDARY_CHAT) chatModel.secondaryChatsContext.value = null
        if (placement == ModalPlacement.END || lastModal.animated) {
          runAtomically { toRemove.add(modalViews.lastIndex - min(toRemove.size, modalViews.lastIndex)) }
        } else {
          modalViews.removeAt(modalViews.lastIndex)
        }
      }
    }
    _modalCount.value = modalViews.size - toRemove.size
    if (placement == ModalPlacement.END && _modalCount.value == 0) {
      drawerClosing.value = true
      endVisible.value = false
    }
  }

  fun closeModals() {
    chatModel.secondaryChatsContext.value = null
    if (placement == ModalPlacement.END && modalViews.isNotEmpty()) {
      drawerClosing.value = true
      endVisible.value = false
      toRemove.addAll(modalViews.indices)
    } else {
      modalViews.clear()
      toRemove.clear()
    }
    _modalCount.value = 0
  }

  fun closeModalsExceptFirst() {
    while (modalCount.value > 1) {
      closeModal()
    }
  }

  @OptIn(ExperimentalAnimationApi::class)
  @Composable
  private fun showEndInView() {
    Box(Modifier.fillMaxSize()) {
      modalViews.forEachIndexed { index, holder ->
        val visible = index < modalCount.value || drawerClosing.value
        if (holder.animated) {
          val visibilityState = remember(holder) {
            MutableTransitionState(false).apply { targetState = visible }
          }
          LaunchedEffect(visible) {
            visibilityState.targetState = visible
          }
          AnimatedVisibility(
            visibleState = visibilityState,
            modifier = Modifier.fillMaxSize(),
            enter = slideInHorizontally(
              initialOffsetX = { fullWidth -> fullWidth },
              animationSpec = animationSpec()
            ),
            exit = slideOutHorizontally(
              targetOffsetX = { fullWidth -> fullWidth },
              animationSpec = animationSpec()
            )
          ) {
            CompositionLocalProvider(LocalAppBarHandler provides adjustAppBarHandler(holder.data.appBarHandler)) {
              holder.modal(holder.data, ::closeModal)
            }
          }
        } else if (visible) {
          CompositionLocalProvider(LocalAppBarHandler provides adjustAppBarHandler(holder.data.appBarHandler)) {
            holder.modal(holder.data, ::closeModal)
          }
        }
      }
      if (toRemove.isNotEmpty()) {
        LaunchedEffect(modalCount.value, toRemove.size) {
          delay(275)
          if (toRemove.isNotEmpty()) {
            runAtomically {
              toRemove.sortedDescending().forEach { index ->
                if (index in modalViews.indices) modalViews.removeAt(index)
              }
              toRemove.clear()
              drawerClosing.value = false
            }
            _modalCount.value = modalViews.size
          }
        }
      }
    }
  }

  @OptIn(ExperimentalAnimationApi::class)
  @Composable
  fun showInView() {
    if (placement == ModalPlacement.END) {
      showEndInView()
      return
    }
    // Without animation
    if (modalCount.value > 0 && modalViews.lastOrNull()?.animated == false) {
      modalViews.lastOrNull()?.let {
        CompositionLocalProvider(LocalAppBarHandler provides adjustAppBarHandler(it.data.appBarHandler)) {
          it.modal(it.data, ::closeModal)
        }
      }
      return
    }
    AnimatedContent(targetState = modalCount.value,
      transitionSpec = {
        if (targetState > initialState) {
          fromEndToStartTransition()
        } else {
          fromStartToEndTransition()
        }.using(SizeTransform(clip = false))
      }
    ) {
      modalViews.getOrNull(it - 1)?.let {
        CompositionLocalProvider(LocalAppBarHandler provides adjustAppBarHandler(it.data.appBarHandler)) {
          it.modal(it.data, ::closeModal)
        }
      }
      if (toRemove.isNotEmpty() && it == modalCount.value && transition.currentState == EnterExitState.Visible && !transition.isRunning) {
        runAtomically { toRemove.removeIf { elem -> modalViews.removeAt(elem); true } }
      }
    }
  }

  @Composable
  fun showPasscodeInView() {
    passcodeView.collectAsState().value?.invoke { passcodeView.value = null }
  }

  @Composable
  fun showOneTimePasscodeInView() {
    onTimePasscodeView.collectAsState().value?.invoke { onTimePasscodeView.value = null }
  }

  /**
  * Allows to modify a list without getting [ConcurrentModificationException]
  * */
  private fun runAtomically(atomicBoolean: AtomicBoolean = oldViewChanging, block: () -> Unit) {
    while (!atomicBoolean.compareAndSet(false, true)) {
      Thread.sleep(10)
    }
    block()
    atomicBoolean.set(false)
  }
//  private fun <T> animationSpecFromStart() = tween<T>(durationMillis = 150, easing = FastOutLinearInEasing)
//  private fun <T> animationSpecFromEnd() = tween<T>(durationMillis = 100, easing = FastOutSlowInEasing)

  companion object {
    private val shared = ModalManager()
    val start = if (appPlatform.isAndroid) shared else ModalManager(ModalPlacement.START)
    val center = if (appPlatform.isAndroid) shared else ModalManager(ModalPlacement.CENTER)
    val end = if (appPlatform.isAndroid) shared else ModalManager(ModalPlacement.END)
    val fullscreen = if (appPlatform.isAndroid) shared else ModalManager(ModalPlacement.FULLSCREEN)

    val floatingTerminal = if (appPlatform.isAndroid) shared else ModalManager(ModalPlacement.START)

    fun closeAllModalsEverywhere() {
      start.closeModals()
      center.closeModals()
      end.closeModals()
      fullscreen.closeModals()
      floatingTerminal.closeModals()
    }

    @OptIn(ExperimentalAnimationApi::class)
    fun fromStartToEndTransition() =
      slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth },
        animationSpec = animationSpec()
      ) with slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = animationSpec()
      )

    @OptIn(ExperimentalAnimationApi::class)
    fun fromEndToStartTransition() =
      slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = animationSpec()
      ) with slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth },
        animationSpec = animationSpec()
      )

    private fun <T> animationSpec() = tween<T>(durationMillis = 250, easing = FastOutSlowInEasing)
  }
}
