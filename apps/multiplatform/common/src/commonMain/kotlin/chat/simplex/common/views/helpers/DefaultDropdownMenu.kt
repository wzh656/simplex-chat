package chat.simplex.common.views.helpers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
  androidx.compose.material3.DropdownMenu(
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
@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun MessageDropdownMenu(
  showMenu: MutableState<Boolean>,
  reactions: @Composable (() -> Unit)?,
  dropdownMenuItems: @Composable () -> Unit,
) {
  androidx.compose.material3.DropdownMenu(
    expanded = showMenu.value,
    onDismissRequest = { showMenu.value = false },
    modifier = Modifier,
    shape = RectangleShape,
    containerColor = Color.Transparent,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Column(horizontalAlignment = Alignment.Start) {
      if (reactions != null) {
        Surface(
          modifier = Modifier.width(320.dp),
          shape = RoundedCornerShape(28.dp),
          tonalElevation = 3.dp,
          shadowElevation = 6.dp,
        ) {
          reactions()
        }
        Spacer(Modifier.height(6.dp))
      }
      Surface(
        modifier = Modifier.width(240.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
      ) {
        Column(Modifier.padding(vertical = 4.dp)) {
          dropdownMenuItems()
        }
      }
    }
  }
}