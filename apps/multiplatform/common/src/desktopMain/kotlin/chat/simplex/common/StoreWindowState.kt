package chat.simplex.common

import chat.simplex.common.model.json
import chat.simplex.common.platform.appPreferences
import chat.simplex.common.platform.desktopPlatform
import chat.simplex.common.ui.theme.DEFAULT_WINDOW_WIDTH
import androidx.compose.ui.unit.dp
import kotlinx.serialization.*

val DESKTOP_MIN_WINDOW_WIDTH = 760.dp
val DESKTOP_MIN_WINDOW_HEIGHT = 560.dp

@Serializable
data class WindowPositionSize(
  val width: Int = DEFAULT_WINDOW_WIDTH.value.toInt(),
  val height: Int = 768,
  val x: Int = 0,
  val y: Int = 0,
)

fun getStoredWindowState(): WindowPositionSize =
  try {
    val str = appPreferences.desktopWindowState.get()
    var state = if (str == null) {
      WindowPositionSize()
    } else {
      json.decodeFromString(str)
    }

    // Linux applies a small native frame correction to the stored width.
    if (desktopPlatform.isLinux() && state.width == 1366) {
      state = state.copy(width = 1376)
    }
    state.copy(
      width = maxOf(state.width, DESKTOP_MIN_WINDOW_WIDTH.value.toInt()),
      height = maxOf(state.height, DESKTOP_MIN_WINDOW_HEIGHT.value.toInt())
    )
  } catch (e: Throwable) {
    WindowPositionSize()
  }

fun storeWindowState(state: WindowPositionSize) =
  appPreferences.desktopWindowState.set(json.encodeToString(state))
