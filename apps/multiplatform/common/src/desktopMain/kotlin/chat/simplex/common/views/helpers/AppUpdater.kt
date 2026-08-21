package chat.simplex.common.views.helpers

import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.platform.AppUpdatesChannel

fun showAppUpdateNotice() = Unit

fun setupUpdateChecker() {
  appPrefs.appUpdateChannel.set(AppUpdatesChannel.DISABLED)
}
