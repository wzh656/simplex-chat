package chat.simplex.common.ui.theme

import androidx.compose.material.Colors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.views.helpers.mixWith

/**
 * Gray Heterotopia's Material 3 design tokens.
 *
 * The app still exposes the upstream theme editor through Material 2 [Colors] while screens are
 * migrated. The Material 3 scheme is derived here so both component families share one active
 * palette during the cutover.
 */
val PortalViolet = Color(0xFF6F61D8)
val PortalVioletLight = Color(0xFFC9C2FF)
val PortalVioletDark = Color(0xFF5143B9)
val SecureTeal = Color(0xFF2F9B76)
val Fog = Color(0xFFF3F4F6)
val Paper = Color(0xFFFFFFFF)
val Night = Color(0xFF0F1115)
val Graphite = Color(0xFF1A1D23)
val OnGraphite = Color(0xFFF2F3F5)
val Slate = Color(0xFF5F6672)
val SlateDark = Color(0xFFB7BDC8)

val GrayTypography = Typography(
  displayLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
  displayMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
  displaySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
  headlineLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
  headlineMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
  headlineSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
  titleLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
  titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
  titleSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
  bodyLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
  bodyMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
  bodySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
  labelLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
  labelMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
  labelSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

val GrayShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(12.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

object GraySpacing {
  val xxs = 4.dp
  val xs = 8.dp
  val sm = 12.dp
  val md = 16.dp
  val lg = 20.dp
  val xl = 24.dp
}

internal fun grayColorScheme(colors: Colors): ColorScheme {
  val primary = colors.primary
  val background = colors.background
  val surface = colors.surface
  val onSurface = colors.onSurface
  val primaryContainer = primary.mixWith(background, if (colors.isLight) 0.14f else 0.32f)
  val secondaryContainer = onSurface.mixWith(background, if (colors.isLight) 0.08f else 0.12f)

  return if (colors.isLight) {
    lightColorScheme(
      primary = primary,
      onPrimary = Color.White,
      primaryContainer = primaryContainer,
      onPrimaryContainer = PortalVioletDark,
      secondary = Slate,
      onSecondary = Color.White,
      secondaryContainer = secondaryContainer,
      onSecondaryContainer = Color(0xFF1D2025),
      tertiary = SecureTeal,
      onTertiary = Color.White,
      background = background,
      onBackground = colors.onBackground,
      surface = surface,
      onSurface = onSurface,
      surfaceContainerHigh = surface,
      onSurfaceVariant = Slate,
      outline = Color(0xFF777984),
      outlineVariant = Color(0xFFC7C8D0),
      error = Color(0xFFBA1A1A),
      onError = Color.White,
    )
  } else {
    darkColorScheme(
      primary = primary,
      onPrimary = Color.White,
      primaryContainer = primaryContainer,
      onPrimaryContainer = PortalVioletLight,
      secondary = SlateDark,
      onSecondary = Color(0xFF29303A),
      secondaryContainer = secondaryContainer,
      onSecondaryContainer = OnGraphite,
      tertiary = Color(0xFF63D4AC),
      onTertiary = Color(0xFF003828),
      background = background,
      onBackground = colors.onBackground,
      surface = surface,
      onSurface = onSurface,
      surfaceContainerHigh = surface,
      onSurfaceVariant = Color(0xFFC7C8D0),
      outline = Color(0xFF91919A),
      outlineVariant = Color(0xFF47464F),
      error = Color(0xFFFFB4AB),
      onError = Color(0xFF690005),
    )
  }
}
