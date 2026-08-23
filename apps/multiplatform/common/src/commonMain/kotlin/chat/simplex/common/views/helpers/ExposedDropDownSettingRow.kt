package chat.simplex.common.views.helpers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import chat.simplex.res.MR
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.usersettings.SettingsActionItemWithContent
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.compose.painterResource

@Composable
fun <T> ExposedDropDownSetting(
  values: List<Pair<T, String>>,
  selection: State<T>,
  textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  fontSize: TextUnit = 16.sp,
  label: String? = null,
  enabled: State<Boolean> = mutableStateOf(true),
  minWidth: Dp = 200.dp,
  maxWidth: Dp = with(LocalDensity.current) { 180.sp.toDp() },
  onSelected: (T) -> Unit
) {
  val expanded = remember { mutableStateOf(false) }
  Box {
    Row(
      Modifier
        .clickable(enabled = enabled.value) { expanded.value = !expanded.value }
        .padding(start = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.End
    ) {
      Text(
        (values.firstOrNull { it.first == selection.value }?.second ?: "") + (if (label != null) " $label" else ""),
        Modifier.widthIn(max = maxWidth),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = textColor,
        fontSize = fontSize,
      )
      Spacer(Modifier.size(12.dp))
      Icon(
        if (!expanded.value) painterResource(MR.images.ic_arrow_drop_down) else painterResource(MR.images.ic_arrow_drop_up),
        generalGetString(MR.strings.icon_descr_more_button),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    DropdownMenu(
      expanded = expanded.value,
      onDismissRequest = { expanded.value = false },
      modifier = Modifier.widthIn(min = minWidth, max = 280.dp),
    ) {
      values.forEach { selectionOption ->
        DropdownMenuItem(
          text = {
            Text(
              selectionOption.second + (if (label != null) " $label" else ""),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = MenuTextColor,
              fontSize = fontSize,
            )
          },
          onClick = {
            onSelected(selectionOption.first)
            expanded.value = false
          },
          contentPadding = PaddingValues(horizontal = 12.dp),
        )
      }
    }
  }
}
@Composable
fun <T> ExposedDropDownSettingWithIcon(
  values: List<Triple<T, ImageResource, String>>,
  selection: State<T>,
  fontSize: TextUnit = 16.sp,
  iconPaddingPercent: Float = 0.2f,
  listIconSize: Dp = 30.dp,
  boxSize: Dp = 60.dp,
  iconColor: Color = MenuTextColor,
  enabled: State<Boolean> = mutableStateOf(true),
  background: Color,
  minWidth: Dp = 200.dp,
  onSelected: (T) -> Unit
) {
  val expanded = remember { mutableStateOf(false) }
  Box {
    Box(
      Modifier
        .clickable(enabled = enabled.value) { expanded.value = !expanded.value }
        .background(background, CircleShape)
        .size(boxSize),
      contentAlignment = Alignment.Center
    ) {
      values.firstOrNull { it.first == selection.value }?.let { choice ->
        Icon(painterResource(choice.second), choice.third, Modifier.padding(boxSize * iconPaddingPercent).fillMaxSize(), tint = iconColor)
      }
    }
    DropdownMenu(
      expanded = expanded.value,
      onDismissRequest = { expanded.value = false },
      modifier = Modifier.widthIn(min = minWidth, max = 280.dp),
    ) {
      values.forEach { selectionOption ->
        DropdownMenuItem(
          text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(painterResource(selectionOption.second), selectionOption.third, Modifier.size(listIconSize))
              Spacer(Modifier.width(12.dp))
              Text(selectionOption.third, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MenuTextColor, fontSize = fontSize)
            }
          },
          onClick = {
            onSelected(selectionOption.first)
            expanded.value = false
          },
          contentPadding = PaddingValues(horizontal = 12.dp),
        )
      }
    }
  }
}
@Composable
fun <T> ExposedDropDownSettingRow(
  title: String,
  values: List<Pair<T, String>>,
  selection: State<T>,
  textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  label: String? = null,
  icon: Painter? = null,
  iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  enabled: State<Boolean> = mutableStateOf(true),
  minWidth: Dp = 200.dp,
  maxWidth: Dp = with(LocalDensity.current) { 180.sp.toDp() },
  onSelected: (T) -> Unit
) {
  SettingsActionItemWithContent(icon, title, iconColor = iconTint, disabled = !enabled.value) {
    ExposedDropDownSetting(values, selection, textColor, label = label, enabled = enabled, minWidth = minWidth, maxWidth = maxWidth, onSelected = onSelected)
  }
}
