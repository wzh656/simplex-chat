package chat.simplex.common.views.helpers

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
fun SearchTextField(
  modifier: Modifier,
  alwaysVisible: Boolean,
  searchText: MutableState<TextFieldValue> = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) },
  placeholder: String = stringResource(MR.strings.search_verb),
  enabled: Boolean = true,
  trailingContent: @Composable (() -> Unit)? = null,
  reducedCloseButtonPadding: Dp = 8.dp,
  onValueChange: (String) -> Unit
) {
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current

  if (!alwaysVisible) {
    LaunchedEffect(Unit) {
      focusRequester.requestFocus()
      delay(200)
      keyboard?.show()
    }
  }
  if (appPlatform.isAndroid) {
    LaunchedEffect(Unit) {
      val modalCountOnOpen = ModalManager.start.modalCount.value
      launch {
        snapshotFlow { ModalManager.start.modalCount.value }
          .filter { it > modalCountOnOpen }
          .collect { keyboard?.hide() }
      }
    }
    KeyChangeEffect(chatModel.chatId.value) {
      if (chatModel.chatId.value != null) {
        delay(300)
        keyboard?.hide()
      }
    }
  }

  val colorScheme = MaterialTheme.colorScheme
  val interactionSource = remember { MutableInteractionSource() }
  val textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface)
  val shape = RoundedCornerShape(12.dp)

  BasicTextField(
    value = searchText.value,
    onValueChange = {
      searchText.value = it
      onValueChange(it.text)
    },
    modifier = modifier
      .heightIn(min = 44.dp)
      .background(colorScheme.surfaceVariant.copy(alpha = 0.58f), shape)
      .focusRequester(focusRequester),
    enabled = enabled,
    cursorBrush = SolidColor(colorScheme.primary),
    visualTransformation = VisualTransformation.None,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    singleLine = true,
    textStyle = textStyle,
    interactionSource = interactionSource,
    decorationBox = { innerTextField ->
      Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          painterResource(MR.images.ic_search),
          contentDescription = null,
          tint = colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
          if (searchText.value.text.isEmpty()) {
            Text(
              placeholder,
              style = textStyle,
              color = colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          innerTextField()
        }
        if (searchText.value.text.isNotEmpty()) {
          IconButton(
            onClick = {
              if (alwaysVisible) {
                keyboard?.hide()
                focusManager.clearFocus()
              }
              searchText.value = TextFieldValue("")
              onValueChange("")
            },
            modifier = Modifier.size(36.dp),
          ) {
            Icon(
              painterResource(MR.images.ic_close),
              stringResource(MR.strings.icon_descr_close_button),
              tint = colorScheme.onSurfaceVariant,
            )
          }
        }
        if (trailingContent != null) {
          Box(Modifier.padding(end = reducedCloseButtonPadding), contentAlignment = Alignment.Center) {
            trailingContent()
          }
        }
      }
    }
  )
}
