package chat.simplex.common.views.helpers

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

@Composable
fun DefaultDropdownMenu(
  showMenu: MutableState<Boolean>,
  modifier: Modifier = Modifier,
  offset: DpOffset = DpOffset(0.dp, 0.dp),
  onClosed: State<() -> Unit> = remember { mutableStateOf({}) },
  shape: Shape = RoundedCornerShape(16.dp),
  containerColor: Color = MaterialTheme.colorScheme.surface,
  tonalElevation: Dp = 3.dp,
  shadowElevation: Dp = 6.dp,
  dropdownMenuItems: (@Composable () -> Unit)?
) {
  DropdownMenu(
    expanded = showMenu.value,
    onDismissRequest = { showMenu.value = false },
    modifier = modifier.widthIn(max = 280.dp),
    offset = offset,
    shape = shape,
    containerColor = containerColor,
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
  ) {
    dropdownMenuItems?.invoke()
    DisposableEffect(Unit) {
      onDispose { onClosed.value() }
    }
  }
}
@Composable
expect fun MessageDropdownMenu(
  showMenu: MutableState<Boolean>,
  anchor: MutableState<Offset?>,
  alignEnd: Boolean,
  reactions: @Composable (() -> Unit)?,
  dropdownMenuItems: @Composable () -> Unit,
)