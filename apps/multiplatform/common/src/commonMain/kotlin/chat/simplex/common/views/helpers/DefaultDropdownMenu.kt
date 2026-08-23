package chat.simplex.common.views.helpers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun MessageDropdownMenu(
  showMenu: MutableState<Boolean>,
  reactions: @Composable (() -> Unit)?,
  dropdownMenuItems: @Composable () -> Unit,
) {
  DropdownMenu(
    expanded = showMenu.value,
    onDismissRequest = { showMenu.value = false },
    // Keep room on every side inside the popup's clipping surface for the child card shadows.
    modifier = Modifier.padding(6.dp),
    shape = RectangleShape,
    containerColor = Color.Transparent,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Box {
      // The popup width is set by the reaction row; unused space must dismiss the menu.
      Box(
        Modifier.matchParentSize().clickable { showMenu.value = false }
      )
      Column(horizontalAlignment = Alignment.Start) {
        if (reactions != null) {
          Surface(
            modifier = Modifier.width(IntrinsicSize.Max),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
          ) {
            reactions()
          }
          Spacer(Modifier.height(6.dp))
        }
        Surface(
          modifier = Modifier.width(IntrinsicSize.Max),
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
}