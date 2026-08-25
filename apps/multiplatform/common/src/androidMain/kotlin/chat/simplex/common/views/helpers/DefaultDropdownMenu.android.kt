package chat.simplex.common.views.helpers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun MessageDropdownMenu(
  showMenu: MutableState<Boolean>,
  anchor: MutableState<Offset?>,
  alignEnd: Boolean,
  reactions: @Composable (() -> Unit)?,
  dropdownMenuItems: @Composable () -> Unit,
) {
  val backdropInteractionSource = remember { MutableInteractionSource() }
  DropdownMenu(
    expanded = showMenu.value,
    onDismissRequest = {
      showMenu.value = false
      anchor.value = null
    },
    modifier = Modifier.padding(6.dp),
    shape = RectangleShape,
    containerColor = Color.Transparent,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Box {
      Box(
        Modifier.matchParentSize().clickable(
          interactionSource = backdropInteractionSource,
          indication = null,
          onClick = {
            showMenu.value = false
            anchor.value = null
          },
        )
      )
      Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
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
