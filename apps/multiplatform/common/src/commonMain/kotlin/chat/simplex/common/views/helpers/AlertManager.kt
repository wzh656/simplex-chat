package chat.simplex.common.views.helpers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import chat.simplex.common.model.ChatModel
import chat.simplex.common.model.LocalBadge
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow


class AlertManager {
  // Don't use mutableStateOf() here, because it produces this if showing from SimpleXAPI.startChat():
  // java.lang.IllegalStateException: Reading a state that was created after the snapshot was taken or in a snapshot that has not yet been applied
  private var alertViews = MutableStateFlow(listOf<(@Composable () -> Unit)>())

  internal fun showAlert(alert: @Composable () -> Unit) {
    Log.d(TAG, "AlertManager.showAlert")
    alertViews.value += alert
  }

  fun hideAlert() {
    alertViews.value = ArrayList(alertViews.value).also { it.removeLastOrNull() }
  }

  fun hideAllAlerts() {
    alertViews.value = listOf()
  }

  fun hasAlertsShown() = alertViews.value.isNotEmpty()

  fun showAlertDialogContent(
    title: @Composable () -> Unit,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
  ) {
    showAlert {
      AppDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        content = content,
      )
    }
  }

  fun showAlertDialogButtons(
    title: String,
    text: String? = null,
    buttons: @Composable () -> Unit,
  ) {
    showAlert {
      AppDialog(
        onDismissRequest = this::hideAlert,
        title = alertTitle(title),
        content = {
          AlertContent(text, null, extraPadding = true) {
            buttons()
          }
        },
      )
    }
  }

  fun showAlertDialogButtonsColumn(
    title: String,
    text: String? = null,
    textAlign: TextAlign = TextAlign.Center,
    dismissible: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    hostDevice: Pair<Long?, String>? = null,
    belowTextContent: @Composable (() -> Unit) = {},
    // When false, [text] is rendered as literal text — use for user-controlled content.
    parseHtml: Boolean = true,
    buttons: @Composable () -> Unit,
  ) {
    showAlert {
      AppDialog(
        onDismissRequest = { onDismissRequest?.invoke(); if (dismissible) hideAlert() },
        title = alertTitle(title),
        content = {
          if (parseHtml) {
            AlertContent(text, hostDevice, extraPadding = true, textAlign = textAlign, belowTextContent = belowTextContent) {
              buttons()
            }
          } else {
            AlertContent(text?.let { AnnotatedString(it) }, hostDevice, extraPadding = true) {
              buttons()
            }
          }
        },
      )
    }
  }

  fun showAlertDialogButtonsColumn(
    title: String,
    text: AnnotatedString,
    onDismissRequest: (() -> Unit)? = null,
    hostDevice: Pair<Long?, String>? = null,
    buttons: @Composable () -> Unit,
  ) {
    showAlert {
      AppDialog(
        onDismissRequest = { onDismissRequest?.invoke(); hideAlert() },
        title = alertTitle(title),
        content = {
          AlertContent(text, hostDevice, extraPadding = true) {
            buttons()
          }
        },
      )
    }
  }

  fun showAlertDialog(
    title: String,
    text: String? = null,
    confirmText: String = generalGetString(MR.strings.ok),
    onConfirm: (() -> Unit)? = null,
    dismissText: String = generalGetString(MR.strings.cancel_verb),
    onDismiss: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    destructive: Boolean = false,
    hostDevice: Pair<Long?, String>? = null,
    // When false, [text] is rendered as literal text — use for user-controlled content.
    parseHtml: Boolean = true,
  ) {
    showAlert {
      AppDialog(
        onDismissRequest = { onDismissRequest?.invoke(); hideAlert() },
        title = alertTitle(title),
        content = {
          val buttonRow: @Composable () -> Unit = {
            Row(
              Modifier.fillMaxWidth().padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
              val focusRequester = remember { FocusRequester() }
              LaunchedEffect(Unit) {
                // Wait before focusing to prevent auto-confirming if a user used Enter key on hardware keyboard
                delay(200)
                focusRequester.requestFocus()
              }
              TextButton(onClick = {
                onDismiss?.invoke()
                hideAlert()
              }) { Text(dismissText) }
              TextButton(onClick = {
                onConfirm?.invoke()
                hideAlert()
              }, Modifier.focusRequester(focusRequester)) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
              }
            }
          }
          if (parseHtml) {
            AlertContent(text, hostDevice, true, content = buttonRow)
          } else {
            AlertContent(text?.let { AnnotatedString(it) }, hostDevice, true, content = buttonRow)
          }
        },
      )
    }
  }

  fun showAlertDialogStacked(
    title: String,
    text: String? = null,
    confirmText: String = generalGetString(MR.strings.ok),
    onConfirm: (() -> Unit)? = null,
    dismissText: String = generalGetString(MR.strings.cancel_verb),
    onDismiss: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    destructive: Boolean = false
  ) {
    showAlert {
      AppDialog(
        onDismissRequest = { onDismissRequest?.invoke(); hideAlert() },
        title = alertTitle(title),
        content = {
          AlertContent(text, null) {
            Column(
              Modifier.fillMaxWidth().padding(horizontal = 16.dp),
              horizontalAlignment = Alignment.End,
            ) {
              TextButton(onClick = {
                onDismiss?.invoke()
                hideAlert()
              }) { Text(dismissText) }
              TextButton(onClick = {
                onConfirm?.invoke()
                hideAlert()
              }) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
              }
            }
          }
        },
      )
    }
  }

  fun showAlertMsg(
    title: String, text: String? = null,
    confirmText: String = generalGetString(MR.strings.ok),
    onConfirm: (() -> Unit)? = null,
    hostDevice: Pair<Long?, String>? = null,
    shareText: Boolean? = null
  ) {
    showAlert {
      AppDialog(
        onDismissRequest = this::hideAlert,
        title = alertTitle(title),
        content = {
          AlertContent(text, hostDevice, extraPadding = true) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
              // Wait before focusing to prevent auto-confirming if a user used Enter key on hardware keyboard
              delay(200)
              focusRequester.requestFocus()
            }
            // Can pass shareText = false to prevent showing Share button if it's needed in a specific case
            val showShareButton = text != null && (shareText == true || (shareText == null && text.length > 500))
            Row(
              Modifier.fillMaxWidth().padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
              val clipboard = LocalClipboardManager.current
              if (showShareButton && text != null) {
                TextButton(onClick = {
                  clipboard.shareText(text)
                  hideAlert()
                }) { Text(stringResource(MR.strings.share_verb)) }
              }
              TextButton(
                onClick = {
                  onConfirm?.invoke()
                  hideAlert()
                },
                modifier = Modifier.focusRequester(focusRequester),
              ) {
                Text(confirmText)
              }
            }
          }
        },
      )
    }
  }

  fun showAlertMsgWithProgress(
    title: String,
    text: String? = null,
  ) {
    showAlert {
      AppDialog(
        onDismissRequest = this::hideAlert,
        title = alertTitle(title),
        content = {
          AlertContent(text, null) {
            Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
            }
          }
        },
      )
    }
  }

  fun showAlertMsg(
    title: StringResource,
    text: StringResource? = null,
    confirmText: StringResource = MR.strings.ok,
    onConfirm: (() -> Unit)? = null,
    hostDevice: Pair<Long?, String>? = null,
  ) = showAlertMsg(generalGetString(title), if (text != null) generalGetString(text) else null, generalGetString(confirmText), onConfirm, hostDevice)

  fun showOpenChatAlert(
    profileName: String,
    profileFullName: String,
    profileImage: @Composable () -> Unit,
    profileBadge: LocalBadge? = null,
    nameCaption: String? = null,
    subtitle: String? = null,
    information: String? = null,
    confirmText: String? = generalGetString(MR.strings.connect_plan_open_chat),
    onConfirm: (() -> Unit)? = null,
    connectOtherButton: String? = null,
    onConnectOther: (() -> Unit)? = null,
    dismissText: String = generalGetString(MR.strings.cancel_verb),
    onDismiss: (() -> Unit)? = null,
  ) {
    showAlert {
      AppDialog(
        onDismissRequest = {
          onDismiss?.invoke()
          hideAlert()
        },
        content = {
          AlertContent(text = null as String?, null) {
            Column(
              Modifier
                .padding(top = DEFAULT_PADDING_HALF)
                .width(360.dp),
              verticalArrangement = Arrangement.SpaceEvenly
            ) {
              Column(
                Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                profileImage()
                Spacer(Modifier.height(DEFAULT_PADDING_HALF))
                val nameFontSize = MaterialTheme.typography.titleSmall.fontSize
                Text(
                  buildAnnotatedString {
                    append(profileName)
                    if (profileBadge != null) {
                      append(" ")
                      appendInlineContent(id = "nameBadge")
                    }
                  },
                  inlineContent =
                    if (profileBadge != null) mapOf("nameBadge" to nameBadgeInline(profileBadge, nameFontSize)) else emptyMap(),
                  textAlign = TextAlign.Center,
                  style = MaterialTheme.typography.titleSmall,
                  lineHeight = 20.sp,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 2,
                  modifier = Modifier.fillMaxWidth()
                )

                if (nameCaption != null) {
                  Spacer(Modifier.height(DEFAULT_PADDING_HALF))
                  Text(
                    nameCaption,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                  )
                }
                if (profileFullName.isNotEmpty() && profileFullName != profileName) {
                  Spacer(Modifier.height(DEFAULT_PADDING_HALF))
                  Text(
                    profileFullName,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                  )
                }
                if (subtitle != null) {
                  Spacer(Modifier.height(DEFAULT_PADDING_HALF))
                  Text(
                    subtitle,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                  )
                }
                if (information != null) {
                  Spacer(Modifier.height(DEFAULT_PADDING_HALF))
                  Text(
                    information,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                  )
                }
              }

              Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End,
              ) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                  // Wait before focusing to prevent auto-confirming if a user used Enter key on hardware keyboard
                  delay(200)
                  focusRequester.requestFocus()
                }
                if (confirmText != null && onConfirm != null) {
                  TextButton(onClick = {
                    onConfirm.invoke()
                    hideAlert()
                  }, Modifier.focusRequester(focusRequester)) {
                    Text(confirmText)
                  }
                }
                if (connectOtherButton != null && onConnectOther != null) {
                  TextButton(onClick = {
                    onConnectOther.invoke()
                    hideAlert()
                  }) {
                    Text(connectOtherButton)
                  }
                }
                TextButton(onClick = {
                  onDismiss?.invoke()
                  hideAlert()
                }, if (confirmText == null) Modifier.focusRequester(focusRequester) else Modifier) {
                  Text(dismissText)
                }
              }
            }
          }
        },
      )
    }
  }

  @Composable
  fun showInView() {
    alertViews.collectAsState().value.lastOrNull()?.invoke()
  }

  companion object {
    val shared = AlertManager()
    val privacySensitive = AlertManager()
  }
}



private fun alertTitle(title: String): (@Composable () -> Unit)? {
  return {
    Text(
      title,
      Modifier.fillMaxWidth().padding(horizontal = 24.dp),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Start,
    )
  }
}

@Composable
private fun AlertContent(
  text: String?,
  hostDevice: Pair<Long?, String>?,
  extraPadding: Boolean = false,
  textAlign: TextAlign = TextAlign.Start,
  belowTextContent: @Composable (() -> Unit) = {},
  content: @Composable (() -> Unit)
) {
  BoxWithConstraints {
    Column {
      if (appPlatform.isDesktop && hostDevice != null) {
        HostDeviceTitle(hostDevice, extraPadding = extraPadding)
      }
      if (text != null) {
        Column(
          Modifier
            .heightIn(max = this@BoxWithConstraints.maxHeight * 0.65f)
            .padding(start = 24.dp, top = 16.dp, end = 24.dp)
            .verticalScroll(rememberScrollState())
        ) {
          SelectionContainer {
            Text(
              escapedHtmlToAnnotatedString(text, LocalDensity.current),
              Modifier.fillMaxWidth(),
              style = MaterialTheme.typography.bodyLarge,
              textAlign = textAlign,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          belowTextContent()
          Spacer(Modifier.height(24.dp))
        }
      } else {
        Spacer(Modifier.height(8.dp))
      }
      content()
    }
  }
}

@Composable
private fun AlertContent(text: AnnotatedString?, hostDevice: Pair<Long?, String>?, extraPadding: Boolean = false, content: @Composable (() -> Unit)) {
  BoxWithConstraints {
    Column {
      if (appPlatform.isDesktop && hostDevice != null) {
        HostDeviceTitle(hostDevice, extraPadding = extraPadding)
      }
      if (text != null) {
        Column(
          Modifier
            .heightIn(max = this@BoxWithConstraints.maxHeight * 0.65f)
            .padding(start = 24.dp, top = 16.dp, end = 24.dp)
            .verticalScroll(rememberScrollState())
        ) {
          SelectionContainer {
            Text(
              text,
              Modifier.fillMaxWidth(),
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Start,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.height(24.dp))
        }
      } else {
        Spacer(Modifier.height(8.dp))
      }
      content()
    }
  }
}

fun hostDevice(rhId: Long?): Pair<Long?, String>? = if (rhId == null && chatModel.remoteHosts.isNotEmpty()) {
  null to ChatModel.controller.appPrefs.deviceNameForRemoteAccess.get()!!
} else if (rhId == null) {
  null
} else {
  rhId to (chatModel.remoteHosts.firstOrNull { it.remoteHostId == rhId }?.hostDeviceName?.ifEmpty { rhId.toString() } ?: rhId.toString())
}

@Composable
private fun HostDeviceTitle(hostDevice: Pair<Long?, String>?, extraPadding: Boolean = false) {
  if (hostDevice != null) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (extraPadding) 16.dp else 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Start,
    ) {
      Icon(
        painterResource(if (hostDevice.first == null) MR.images.ic_desktop else MR.images.ic_smartphone_300),
        null,
        Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.width(8.dp))
      Text(hostDevice.second, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
