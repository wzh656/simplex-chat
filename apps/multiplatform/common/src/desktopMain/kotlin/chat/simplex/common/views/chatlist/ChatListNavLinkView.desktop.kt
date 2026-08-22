package chat.simplex.common.views.chatlist

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.onRightClick
import chat.simplex.common.views.helpers.*

object NoIndication : IndicationNodeFactory {
  // Should be as a class, not an object. Otherwise, crash
  private class NoIndicationInstance : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() { drawContent() }
  }
  override fun create(interactionSource: InteractionSource): DelegatableNode = NoIndicationInstance()
  override fun hashCode(): Int = -1
  override fun equals(other: Any?) = other === this
}

@Composable
actual fun ChatListNavLinkLayout(
  chatLinkPreview: @Composable () -> Unit,
  click: () -> Unit,
  dropdownMenuItems: (@Composable () -> Unit)?,
  showMenu: MutableState<Boolean>,
  disabled: Boolean,
  selectedChat: State<Boolean>,
  nextChatSelected: State<Boolean>,
) {
  val selected = selectedChat.value
  var modifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 8.dp, vertical = 2.dp)
    .clip(RoundedCornerShape(12.dp))
    .background(if (selected) Material3Theme.colorScheme.primaryContainer else Material3Theme.colorScheme.surface)
  if (!disabled) modifier = modifier
    .combinedClickable(onClick = click, onLongClick = { showMenu.value = true })
    .onRightClick { showMenu.value = true }
  CompositionLocalProvider(
    LocalIndication provides if (selected && !disabled) NoIndication else LocalIndication.current
  ) {
    Box(modifier) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 7.dp, end = 10.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        chatLinkPreview()
      }
      if (selected) {
        Box(
          Modifier
            .align(Alignment.CenterStart)
            .padding(vertical = 10.dp)
            .width(3.dp)
            .fillMaxHeight()
            .background(Material3Theme.colorScheme.primary, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
        )
      }
      if (dropdownMenuItems != null) {
        DefaultDropdownMenu(showMenu, dropdownMenuItems = dropdownMenuItems)
      }
    }
  }
}
