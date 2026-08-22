package chat.simplex.common.views.helpers

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

val LocalAppBarHandler: ProvidableCompositionLocal<AppBarHandler?> = staticCompositionLocalOf { null }

@Composable
fun rememberAppBarHandler(key1: Any? = null, key2: Any? = null, keyboardCoversBar: Boolean = true): AppBarHandler =
  remember(key1, key2, keyboardCoversBar) { AppBarHandler(keyboardCoversBar) }

fun adjustAppBarHandler(handler: AppBarHandler): AppBarHandler = handler

@Stable
class AppBarHandler(
  val keyboardCoversBar: Boolean = true,
  listState: LazyListState = LazyListState(0, 0),
  scrollState: ScrollState = ScrollState(initial = 0)
) {
  val title = mutableStateOf("")
  var listState by mutableStateOf(listState, structuralEqualityPolicy())
    internal set

  var scrollState by mutableStateOf(scrollState, structuralEqualityPolicy())
    internal set

  val connection = CollapsingAppBarNestedScrollConnection()


  companion object {
    var appBarMaxHeightPx: Int = 0
  }
}

class CollapsingAppBarNestedScrollConnection(): NestedScrollConnection {
  var scrollTrackingEnabled = true
  var appBarOffset: Float by mutableFloatStateOf(0f)

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    appBarOffset += available.y
    return Offset(0f, 0f)
  }

  override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
    appBarOffset -= available.y
    return Offset(x = 0f, 0f)
  }
}
