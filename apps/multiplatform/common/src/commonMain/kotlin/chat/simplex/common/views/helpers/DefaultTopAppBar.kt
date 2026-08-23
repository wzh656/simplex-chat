package chat.simplex.common.views.helpers

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.material3.HorizontalDivider as Material3HorizontalDivider
import androidx.compose.material3.IconButton as Material3IconButton
import androidx.compose.material3.Text as Material3Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.chat.item.CenteredRowLayout
import chat.simplex.res.MR
import kotlin.math.absoluteValue

@Composable
fun DefaultAppBar(
  navigationButton: (@Composable RowScope.() -> Unit)? = null,
  title: (@Composable () -> Unit)? = null,
  fixedTitleText: String? = null,
  onTitleClick: (() -> Unit)? = null,
  onTop: Boolean,
  showSearch: Boolean = false,
  searchAlwaysVisible: Boolean = false,
  searchPlaceholder: String? = null,
  onSearchValueChanged: (String) -> Unit = {},
  searchTrailingContent: @Composable (() -> Unit)? = null,
  buttons: @Composable RowScope.() -> Unit = {},
) {
  // If I just disable clickable modifier when don't need it, it will stop passing clicks to search. Replacing the whole modifier
  val modifier = if (!showSearch) {
    Modifier.clickable(enabled = onTitleClick != null, onClick = onTitleClick ?: { })
  } else if (!onTop) Modifier.imePadding()
  else Modifier

  val handler = LocalAppBarHandler.current
  val connection = handler?.connection
  val titleText = remember(handler?.title?.value, fixedTitleText) {
    if (fixedTitleText != null) mutableStateOf(fixedTitleText)
    else handler?.title ?: mutableStateOf("")
  }
  Box(modifier.background(Material3Theme.colorScheme.surface)) {
    Box(
      Modifier
        .fillMaxWidth()
        .then(if (!onTop) Modifier.navigationBarsPadding() else Modifier)
        .heightIn(min = AppBarHeight * fontSizeSqrtMultiplier)
    ) {
      AppBar(
        title = {
          if (showSearch) {
            val placeholder = searchPlaceholder ?: stringResource(MR.strings.search_verb)
            SearchTextField(Modifier.fillMaxWidth(), alwaysVisible = searchAlwaysVisible, placeholder = placeholder, trailingContent = searchTrailingContent, reducedCloseButtonPadding = 12.dp, onValueChange = onSearchValueChanged)
          } else if (title != null) {
            title()
          } else if (titleText.value.isNotEmpty() && connection != null) {
            Row(
              Modifier.graphicsLayer {
                alpha = if (fixedTitleText != null) 1f else topTitleAlpha(true, connection)
              }
            ) {
              Material3Text(
                titleText.value,
                style = Material3Theme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        },
        navigationIcon = navigationButton,
        buttons = if (!showSearch) buttons else {{}},
        centered = false,
        onTop = onTop,
      )
      AppBarDivider(onTop, title != null || fixedTitleText != null, connection)
    }
  }
}


@Composable
fun CallAppBar(
  title: @Composable () -> Unit,
  onBack: () -> Unit
) {
  AppBar(
    title,
    navigationIcon = { NavigationButtonBack(tintColor = Color(0xFFFFFFD8), onButtonClicked = onBack) },
    centered = false,
    onTop = true
  )
}

@Composable
fun NavigationButtonBack(onButtonClicked: (() -> Unit)?, tintColor: Color = if (onButtonClicked != null) Material3Theme.colorScheme.onSurface else Material3Theme.colorScheme.onSurfaceVariant, height: Dp = 24.dp) {
  Material3IconButton(onButtonClicked ?: {}, enabled = onButtonClicked != null) {
    Icon(
      Icons.AutoMirrored.Filled.ArrowBack, stringResource(MR.strings.back), Modifier.size(height), tint = tintColor
    )
  }
}

@Composable
fun NavigationButtonClose(onButtonClicked: (() -> Unit)?, tintColor: Color = if (onButtonClicked != null) Material3Theme.colorScheme.primary else Material3Theme.colorScheme.secondary, height: Dp = 24.dp) {
  Material3IconButton(onButtonClicked ?: {}, enabled = onButtonClicked != null) {
    Icon(
      painterResource(MR.images.ic_close), stringResource(MR.strings.back), Modifier.height(height), tint = tintColor
    )
  }
}

@Composable
fun ShareButton(onButtonClicked: () -> Unit) {
  Material3IconButton(onButtonClicked) {
    Icon(
      painterResource(MR.images.ic_share), stringResource(MR.strings.share_verb), tint = Material3Theme.colorScheme.primary
    )
  }
}

@Composable
fun NavigationButtonMenu(onButtonClicked: () -> Unit) {
  Material3IconButton(onClick = onButtonClicked) {
    Icon(
      painterResource(MR.images.ic_menu),
      stringResource(MR.strings.icon_descr_settings),
      tint = Material3Theme.colorScheme.primary,
    )
  }
}

@Composable
private fun BoxScope.AppBarDivider(onTop: Boolean, fixedAlpha: Boolean, connection: CollapsingAppBarNestedScrollConnection?) {
  val color = Material3Theme.colorScheme.outlineVariant.copy(alpha = 0.65f)
  if (connection != null) {
    Material3HorizontalDivider(
      Modifier
        .align(if (onTop) Alignment.BottomStart else Alignment.TopStart)
        .graphicsLayer {
          alpha = if (!onTop || fixedAlpha) 1f else topTitleAlpha(false, connection, 1f)
        },
      color = color,
    )
  } else {
    Material3HorizontalDivider(Modifier.align(if (onTop) Alignment.BottomStart else Alignment.TopStart), color = color)
  }
}

@Composable
private fun AppBar(
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  navigationIcon: @Composable (RowScope.() -> Unit)? = null,
  buttons: @Composable RowScope.() -> Unit = {},
  centered: Boolean,
  onTop: Boolean,
) {
  val adjustedModifier = modifier
    .then(if (onTop) Modifier.statusBarsPadding() else Modifier)
    .height(AppBarHeight * fontSizeSqrtMultiplier)
    .fillMaxWidth()
    .padding(horizontal = AppBarHorizontalPadding)
  if (centered) {
    AppBarCenterAligned(adjustedModifier, title, navigationIcon, buttons)
  } else {
    AppBarStartAligned(adjustedModifier, title, navigationIcon, buttons)
  }
}

@Composable
private fun AppBarStartAligned(
  modifier: Modifier,
  title: @Composable () -> Unit,
  navigationIcon: @Composable (RowScope.() -> Unit)? = null,
  buttons: @Composable RowScope.() -> Unit
) {
  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (navigationIcon != null) {
      navigationIcon()
      Spacer(Modifier.width(AppBarHorizontalPadding))
    } else {
      Spacer(Modifier.width(DEFAULT_PADDING))
    }
    Row(Modifier
      .weight(1f)
      .padding(end = DEFAULT_PADDING_HALF)
    ) {
      title()
    }
    Row(
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      buttons()
    }
  }
}

@Composable
private fun AppBarCenterAligned(
  modifier: Modifier,
  title: @Composable () -> Unit,
  navigationIcon: @Composable (RowScope.() -> Unit)? = null,
  buttons: @Composable RowScope.() -> Unit,
) {
  CenteredRowLayout(modifier) {
    if (navigationIcon != null) {
      Row(
        Modifier.padding(end = AppBarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = navigationIcon
      )
    } else {
      Spacer(Modifier)
    }
    Row(
      Modifier.padding(end = DEFAULT_PADDING_HALF)
    ) {
      title()
    }
    Row(
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      buttons()
    }
  }
}

private fun topTitleAlpha(text: Boolean, connection: CollapsingAppBarNestedScrollConnection, alpha: Float = appPrefs.inAppBarsAlpha.get()) =
  if (!connection.scrollTrackingEnabled) 0f
  else if (connection.appBarOffset.absoluteValue < AppBarHandler.appBarMaxHeightPx / 3) 0f
  else ((-connection.appBarOffset * 1.5f) / (AppBarHandler.appBarMaxHeightPx)).coerceIn(0f, if (text) 1f else alpha)

val AppBarHeight = 56.dp
val AppBarHorizontalPadding = 2.dp
