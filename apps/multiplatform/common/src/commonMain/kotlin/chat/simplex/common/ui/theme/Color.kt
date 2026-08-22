package chat.simplex.common.ui.theme

import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.cos
import kotlin.math.sin

fun oklch(L: Float, C: Float, H: Float, alpha: Float = 1f): Color {
  val hRad = H * (Math.PI.toFloat() / 180f)
  return Color(L, C * cos(hRad), C * sin(hRad), alpha, ColorSpaces.Oklab)
}

val Indigo = Color(0xFF9966FF)
val SimplexBlue = Color(0, 136, 255, 255)  // If this value changes also need to update #0088ff in string resource files
val SimplexGreen = Color(77, 218, 103, 255)
val SecretColor = Color(0x40808080)
val LightGray = Fog
val DarkGray = Graphite
val HighOrLowlight = SlateDark
val MessagePreviewDark = Color(0xFFB7BDC8)
val MessagePreviewLight = Color(0xFF5F6672)
val ToolbarLight = Color(0x0F1A1D23)
val ToolbarDark = Color(0x1FFFFFFF)
val SettingsSecondaryLight = Color(0x185F6672)
val GroupDark = Color(0x3342444C)
val IncomingCallLight = Fog
val WarningOrange = Color(255, 127, 0, 255)
val WarningYellow = Color(255, 192, 0, 255)
val FileLight = Color(191, 194, 199, 255)
val FileDark = Color(94, 94, 98, 255)

val MenuTextColor: Color @Composable get () = if (isInDarkTheme()) LocalContentColor.current.copy(alpha = 0.8f) else Color.Black
val NoteFolderIconColor: Color @Composable get() = MaterialTheme.appColors.primaryVariant2
