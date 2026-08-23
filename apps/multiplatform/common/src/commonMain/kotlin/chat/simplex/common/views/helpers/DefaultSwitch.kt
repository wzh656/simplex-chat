package chat.simplex.common.views.helpers

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.*
import androidx.compose.material3.Switch as Material3Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun DefaultSwitch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  colors: SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colors.primary,
    uncheckedThumbColor = MaterialTheme.colors.secondary,
    checkedTrackAlpha = 0.0f,
    uncheckedTrackAlpha = 0.0f,
  )
) {
  if (LocalAlertDialogSectionStyle.current) {
    Material3Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = modifier,
      enabled = enabled,
    )
  } else {
    val color = if (checked) MaterialTheme.colors.primary.copy(alpha = 0.3f) else MaterialTheme.colors.secondary.copy(alpha = 0.3f)
    val size = with(LocalDensity.current) { Size(40.dp.toPx(), 26.dp.toPx()) }
    val offset = with(LocalDensity.current) { Offset(4.dp.toPx(), 11.dp.toPx()) }
    val radius = with(LocalDensity.current) { 13.dp.toPx() }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier.drawBehind { drawRoundRect(color, size = size, topLeft = offset, cornerRadius = CornerRadius(radius, radius)) },
      colors = colors,
      enabled = enabled,
      interactionSource = interactionSource,
    )
  }
}