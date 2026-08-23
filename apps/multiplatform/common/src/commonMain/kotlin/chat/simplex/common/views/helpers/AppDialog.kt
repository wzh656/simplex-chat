package chat.simplex.common.views.helpers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Colors as Material2Colors
import androidx.compose.material.MaterialTheme as Material2Theme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Shapes as Material2Shapes
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import chat.simplex.common.ui.theme.GrayShapes
import chat.simplex.common.ui.theme.GrayTypography

internal val LocalAlertDialogSectionStyle = staticCompositionLocalOf { false }

@Composable
fun AppDialogBody(content: @Composable ColumnScope.() -> Unit) {
  BoxWithConstraints {
    Column(
      Modifier
        .heightIn(max = maxHeight * 0.65f)
        .padding(start = 24.dp, top = 16.dp, end = 24.dp)
        .verticalScroll(rememberScrollState())
    ) {
      content()
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
fun AppDialogActions(content: @Composable RowScope.() -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

@Composable
private fun AppDialogContentTheme(content: @Composable () -> Unit) {
  val colorScheme = MaterialTheme.colorScheme
  Material2Theme(
    colors = dialogMaterial2Colors(colorScheme),
    shapes = Material2Shapes(
      small = GrayShapes.small,
      medium = GrayShapes.medium,
      large = GrayShapes.large,
    ),
  ) {
    ProvideTextStyle(GrayTypography.bodyLarge) {
      CompositionLocalProvider(LocalAlertDialogSectionStyle provides true) {
        content()
      }
    }
  }
}

@Composable
fun AppDialogSurface(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth().widthIn(min = 280.dp, max = 560.dp),
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = 0.dp,
  ) {
    Column(Modifier.padding(top = 24.dp, bottom = 16.dp)) {
      AppDialogContentTheme { content() }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDialog(
  onDismissRequest: () -> Unit,
  title: (@Composable () -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  BasicAlertDialog(onDismissRequest = onDismissRequest) {
    AppDialogSurface {
      title?.invoke()
      content()
    }
  }
}

private fun dialogMaterial2Colors(colorScheme: ColorScheme): Material2Colors {
  return if (colorScheme.background.luminance() >= 0.5f) {
    lightColors(
      primary = colorScheme.primary,
      primaryVariant = colorScheme.primaryContainer,
      secondary = colorScheme.secondary,
      secondaryVariant = colorScheme.secondaryContainer,
      background = colorScheme.background,
      surface = colorScheme.surface,
      error = colorScheme.error,
      onPrimary = colorScheme.onPrimary,
      onSecondary = colorScheme.onSecondary,
      onBackground = colorScheme.onBackground,
      onSurface = colorScheme.onSurface,
      onError = colorScheme.onError,
    )
  } else {
    darkColors(
      primary = colorScheme.primary,
      primaryVariant = colorScheme.primaryContainer,
      secondary = colorScheme.secondary,
      secondaryVariant = colorScheme.secondaryContainer,
      background = colorScheme.background,
      surface = colorScheme.surface,
      error = colorScheme.error,
      onPrimary = colorScheme.onPrimary,
      onSecondary = colorScheme.onSecondary,
      onBackground = colorScheme.onBackground,
      onSurface = colorScheme.onSurface,
      onError = colorScheme.onError,
    )
  }
}
