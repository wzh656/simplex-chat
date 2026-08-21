package chat.simplex.common.views.usersettings

import SectionView
import androidx.compose.runtime.*
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource

@Composable
actual fun AdvancedSettingsAppSection(
  showSettingsModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  withAuth: (title: String, desc: String, block: () -> Unit) -> Unit,
) {
  SectionView {
    SettingsActionItem(painterResource(MR.images.ic_code), stringResource(MR.strings.settings_developer_tools), showSettingsModal { DeveloperView(withAuth) })
  }
}

@Composable
actual fun AppShutdownItem() {}
