package chat.simplex.common.platform

import androidx.compose.runtime.*
import chat.simplex.common.views.helpers.KeyboardState
import chat.simplex.common.views.helpers.ToastManager

fun showToast(text: String, timeout: Long = 2500L) {
  ToastManager.show(text, timeout)
}

@Composable
expect fun LockToCurrentOrientationUntilDispose()

@Composable
expect fun LocalMultiplatformView(): Any?

@Composable
expect fun getKeyboardState(): State<KeyboardState>
expect fun hideKeyboard(view: Any?, clearFocus: Boolean = false)

expect fun androidIsFinishingMainActivity(): Boolean

fun registerGlobalErrorHandler() {
  Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionsHandler())
}

expect class GlobalExceptionsHandler(): Thread.UncaughtExceptionHandler {
  override fun uncaughtException(thread: Thread, e: Throwable)
}
