package chat.simplex.app.model

import android.app.*
import android.app.TaskStackBuilder
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.app.*
import chat.simplex.app.*
import chat.simplex.app.TAG
import chat.simplex.common.views.helpers.*
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.views.call.RcvCallInvitation
import kotlinx.datetime.Clock
import chat.simplex.res.MR

object NtfManager {
  const val MessageChannel: String = "chat.simplex.app.MESSAGE_NOTIFICATION"
  const val MessageGroup: String = "chat.simplex.app.MESSAGE_NOTIFICATION"
  const val OpenChatAction: String = "chat.simplex.app.OPEN_CHAT"
  const val ShowChatsAction: String = "chat.simplex.app.SHOW_CHATS"

  const val CallNotificationId: Int = -1
  private const val UserIdKey: String = "userId"
  private const val ChatIdKey: String = "chatId"
  private val appPreferences: AppPreferences = ChatController.appPrefs
  private val context: Context
    get() = SimplexApp.context

  fun getUserIdFromIntent(intent: Intent?): Long? {
    val userId = intent?.getLongExtra(UserIdKey, -1L)
    return if (userId == -1L || userId == null) null else userId
  }

  private val manager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  // (UserId, ChatId) -> Time
  private var prevNtfTime = mutableMapOf<Pair<Long, ChatId>, Long>()
  private val msgNtfTimeoutMs = 30000L

  init {
    if (areNotificationsEnabledInSystem()) createNtfChannelsMaybeShowAlert()
  }

  fun cancelNotificationsForChat(chatId: String) {
    val key = prevNtfTime.keys.firstOrNull { it.second == chatId }
    prevNtfTime.remove(key)
    manager.cancel(chatId.hashCode())
    val msgNtfs = manager.activeNotifications.filter { ntf ->
      ntf.notification.channelId == MessageChannel
    }
    if (msgNtfs.size <= 1) {
      // Have a group notification with no children so cancel it
      manager.cancel(0)
    }
  }

  fun cancelNotificationsForUser(userId: Long) {
    prevNtfTime.keys.filter { it.first == userId }.forEach {
      prevNtfTime.remove(it)
      manager.cancel(it.second.hashCode())
    }
    val msgNtfs = manager.activeNotifications.filter { ntf ->
      ntf.notification.channelId == MessageChannel
    }
    if (msgNtfs.size <= 1) {
      // Have a group notification with no children so cancel it
      manager.cancel(0)
    }
  }

  fun displayNotification(user: UserLike, chatId: String, displayName: String, msgText: String, image: String? = null, actions: List<NotificationAction> = emptyList()) {
    if (!user.showNotifications) return
    Log.d(TAG, "notifyMessageReceived $chatId")
    val now = Clock.System.now().toEpochMilliseconds()
    val recentNotification = (now - prevNtfTime.getOrDefault(user.userId to chatId, 0) < msgNtfTimeoutMs)
    prevNtfTime[user.userId to chatId] = now
    val previewMode = appPreferences.notificationPreviewMode.get()
    val title = if (previewMode == NotificationPreviewMode.HIDDEN.name) generalGetString(MR.strings.notification_preview_somebody) else displayName
    val content = if (previewMode != NotificationPreviewMode.MESSAGE.name) generalGetString(MR.strings.notification_preview_new_message) else msgText
    val largeIcon = when {
      actions.isEmpty() -> null
      image == null || previewMode == NotificationPreviewMode.HIDDEN.name -> BitmapFactory.decodeResource(context.resources, R.drawable.icon)
      else -> base64ToBitmap(image).asAndroidBitmap()
    }
    val builder = NotificationCompat.Builder(context, MessageChannel)
      .setContentTitle(title)
      .setContentText(content)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setGroup(MessageGroup)
      .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
      .setSmallIcon(R.drawable.ntf_icon)
      .setLargeIcon(largeIcon)
      .setColor(0x88FFFF)
      .setAutoCancel(true)
      .setVibrate(if (actions.isEmpty()) null else longArrayOf(0, 250, 250, 250))
      .setContentIntent(chatPendingIntent(OpenChatAction, user.userId, chatId))
      .setSilent(if (actions.isEmpty()) recentNotification else false)

    for (action in actions) {
      val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      val actionIntent = Intent(SimplexApp.context, NtfActionReceiver::class.java)
      actionIntent.action = action.name
      actionIntent.putExtra(UserIdKey, user.userId)
      actionIntent.putExtra(ChatIdKey, chatId)
      val actionPendingIntent: PendingIntent = PendingIntent.getBroadcast(SimplexApp.context, 0, actionIntent, flags)
      val actionButton = when (action) {
        NotificationAction.ACCEPT_CONTACT_REQUEST -> generalGetString(MR.strings.accept)
      }
      builder.addAction(0, actionButton, actionPendingIntent)
    }
    val summary = NotificationCompat.Builder(context, MessageChannel)
      .setSmallIcon(R.drawable.ntf_icon)
      .setColor(0x88FFFF)
      .setGroup(MessageGroup)
      .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
      .setGroupSummary(true)
      .setContentIntent(chatPendingIntent(ShowChatsAction, null))
      .build()

    with(NotificationManagerCompat.from(context)) {
      // using cInfo.id only shows one notification per chat and updates it when the message arrives
      if (ActivityCompat.checkSelfPermission(SimplexApp.context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        notify(chatId.hashCode(), builder.build())
        notify(0, summary)
      }
    }
  }

  fun notifyCallInvitation(invitation: RcvCallInvitation): Boolean = false

  fun showMessage(title: String, text: String) {
    val builder = NotificationCompat.Builder(context, MessageChannel)
      .setContentTitle(title)
      .setContentText(text)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setGroup(MessageGroup)
      .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
      .setSmallIcon(R.drawable.ntf_icon)
      .setLargeIcon(null as Bitmap?)
      .setColor(0x88FFFF)
      .setAutoCancel(true)
      .setVibrate(null)
      .setContentIntent(chatPendingIntent(ShowChatsAction, null, null))
      .setSilent(false)

    val summary = NotificationCompat.Builder(context, MessageChannel)
      .setSmallIcon(R.drawable.ntf_icon)
      .setColor(0x88FFFF)
      .setGroup(MessageGroup)
      .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
      .setGroupSummary(true)
      .setContentIntent(chatPendingIntent(ShowChatsAction, null))
      .build()

    with(NotificationManagerCompat.from(context)) {
      if (ActivityCompat.checkSelfPermission(SimplexApp.context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        notify("MESSAGE".hashCode(), builder.build())
        notify(0, summary)
      }
    }
  }

  fun cancelCallNotification() {
    manager.cancel(CallNotificationId)
  }

  fun cancelAllNotifications() {
    manager.cancelAll()
  }

  fun hasNotificationsForChat(chatId: String): Boolean = manager.activeNotifications.any { it.id == chatId.hashCode() }

  private fun chatPendingIntent(intentAction: String, userId: Long?, chatId: String? = null, broadcast: Boolean = false): PendingIntent {
    Log.d(TAG, "chatPendingIntent for $intentAction")
    val uniqueInt = (System.currentTimeMillis() and 0xfffffff).toInt()
    var intent = Intent(context, if (!broadcast) MainActivity::class.java else NtfActionReceiver::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      .setAction(intentAction)
      .putExtra(UserIdKey, userId)
    if (chatId != null) intent = intent.putExtra(ChatIdKey, chatId)
    return if (!broadcast) {
      TaskStackBuilder.create(context).run {
        addNextIntentWithParentStack(intent)
        getPendingIntent(uniqueInt, PendingIntent.FLAG_IMMUTABLE)
      }
    } else {
      PendingIntent.getBroadcast(SimplexApp.context, uniqueInt, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
  }

  fun areNotificationsEnabledInSystem() = manager.areNotificationsEnabled()

  /**
   * This function creates notifications channels. On Android 13+ calling it for the first time will trigger system alert,
   * The alert asks a user to allow or disallow to show notifications for the app. That's why it should be called only when the user
   * already saw such alert or when you want to trigger showing the alert.
   * On the first app launch the channels will be created after user profile is created. Subsequent calls will create new channels and delete
   * old ones if needed
   * */
  fun createNtfChannelsMaybeShowAlert() {
    manager.createNotificationChannel(NotificationChannel(MessageChannel, generalGetString(MR.strings.ntf_channel_messages), NotificationManager.IMPORTANCE_HIGH))
    // Remove old channels since they can't be edited
    manager.deleteNotificationChannel("chat.simplex.app.CALL_NOTIFICATION")
    manager.deleteNotificationChannel("chat.simplex.app.CALL_NOTIFICATION_1")
    manager.deleteNotificationChannel("chat.simplex.app.LOCK_SCREEN_CALL_NOTIFICATION")
  }

  /**
   * Processes every action specified by [NotificationCompat.Builder.addAction] that comes with [NotificationAction]
   * and [ChatInfo.id] as [ChatIdKey] in extra
   * */
  class NtfActionReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      val userId = getUserIdFromIntent(intent)
      val chatId = intent?.getStringExtra(ChatIdKey) ?: return
      val m = SimplexApp.context.chatModel
      when (intent.action) {
        NotificationAction.ACCEPT_CONTACT_REQUEST.name -> ntfManager.acceptContactRequestAction(userId, incognito = false, chatId)
        else -> {
          Log.e(TAG, "Unknown action. Make sure you provide action from NotificationAction enum")
        }
      }
    }
  }
}
