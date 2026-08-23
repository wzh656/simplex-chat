package chat.simplex.common.views.helpers

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private class ToastMessage(
  val text: String,
  val timeout: Long,
)

object ToastManager {
  private val messages = MutableStateFlow<List<ToastMessage>>(emptyList())

  fun show(text: String, timeout: Long = 2500L) {
    messages.update { it + ToastMessage(text, timeout.coerceAtLeast(1L)) }
  }

  private fun dismiss(message: ToastMessage) {
    messages.update { messages ->
      if (messages.firstOrNull() === message) messages.drop(1) else messages - message
    }
  }

  @Composable
  fun Host(modifier: Modifier = Modifier) {
    val toast = messages.collectAsState().value.firstOrNull() ?: return

    Box(
      modifier
        .fillMaxSize()
        .zIndex(10f)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 16.dp, vertical = 16.dp),
      contentAlignment = Alignment.TopCenter,
    ) {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 3.dp,
      ) {
        Text(
          escapedHtmlToAnnotatedString(toast.text, LocalDensity.current),
          Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    LaunchedEffect(toast) {
      delay(toast.timeout)
      dismiss(toast)
    }
  }
}
