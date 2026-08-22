package chat.simplex.common.views.chatlist

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.onRightClick
import chat.simplex.common.views.helpers.*

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
  val shape = RoundedCornerShape(12.dp)
  var modifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 8.dp, vertical = 2.dp)
    .clip(shape)
    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)

  if (!disabled) modifier = modifier
    .combinedClickable(onClick = click, onLongClick = { showMenu.value = true })
    .onRightClick { showMenu.value = true }
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
          .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
      )
    }
    if (dropdownMenuItems != null) {
      DefaultDropdownMenu(showMenu, dropdownMenuItems = dropdownMenuItems)
    }
  }
}
