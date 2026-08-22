package chat.simplex.common.views.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal object OnboardingServerState {
  var verifiedServers by mutableStateOf<VerifiedOnboardingServers?>(null)
}

internal data class VerifiedOnboardingServers(val smp: String, val xftp: String)

private enum class ServerTestState { NotTested, Testing, Passed, Failed }

@Composable
fun ConfigureServers(chatModel: ChatModel) {
  val scope = rememberCoroutineScope()
  val saved = OnboardingServerState.verifiedServers
  val smp = remember { mutableStateOf(TextFieldValue(saved?.smp.orEmpty())) }
  val xftp = remember { mutableStateOf(TextFieldValue(saved?.xftp.orEmpty())) }
  var smpState by remember { mutableStateOf(if (saved == null) ServerTestState.NotTested else ServerTestState.Passed) }
  var xftpState by remember { mutableStateOf(if (saved == null) ServerTestState.NotTested else ServerTestState.Passed) }
  var failureText by remember { mutableStateOf<String?>(null) }
  var testJob by remember { mutableStateOf<Job?>(null) }
  val testing = testJob?.isActive == true || smpState == ServerTestState.Testing || xftpState == ServerTestState.Testing

  CompositionLocalProvider(LocalAppBarHandler provides rememberAppBarHandler()) {
    ModalView(
      close = {
        OnboardingServerState.verifiedServers = null
        chatModel.controller.appPrefs.onboardingStage.set(OnboardingStage.Step1_SimpleXInfo)
      }
    ) {
      ColumnWithScrollBar(
        Modifier.fillMaxSize().padding(horizontal = DEFAULT_ONBOARDING_HORIZONTAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        AppBarTitle(stringResource(MR.strings.onboarding_configure_servers), bottomPadding = DEFAULT_PADDING)
        Text(
          stringResource(MR.strings.onboarding_configure_servers_desc),
          color = MaterialTheme.colors.secondary,
          textAlign = TextAlign.Center,
          modifier = Modifier.widthIn(max = 600.dp)
        )
        Spacer(Modifier.height(DEFAULT_PADDING * 1.5f))
        ServerAddressField(
          title = stringResource(MR.strings.smp_server),
          value = smp,
          state = smpState,
          enabled = !testing,
          onChange = {
            smp.value = it
            smpState = ServerTestState.NotTested
            OnboardingServerState.verifiedServers = null
            failureText = null
          }
        )
        Spacer(Modifier.height(DEFAULT_PADDING))
        ServerAddressField(
          title = stringResource(MR.strings.xftp_server),
          value = xftp,
          state = xftpState,
          enabled = !testing,
          onChange = {
            xftp.value = it
            xftpState = ServerTestState.NotTested
            OnboardingServerState.verifiedServers = null
            failureText = null
          }
        )
        failureText?.let {
          Text(
            it,
            color = MaterialTheme.colors.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = DEFAULT_PADDING).widthIn(max = 600.dp)
          )
        }
        Spacer(Modifier.weight(1f))
        Column(
          Modifier.widthIn(max = 450.dp).fillMaxWidth().padding(vertical = DEFAULT_PADDING * 2),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          OnboardingActionButton(
            Modifier.fillMaxWidth(),
            labelId = MR.strings.onboarding_test_servers,
            onboarding = null,
            enabled = !testing && smp.value.text.isNotBlank() && xftp.value.text.isNotBlank(),
            onclick = {
              if (testJob?.isActive == true) return@OnboardingActionButton
              OnboardingServerState.verifiedServers = null
              val smpAddress = smp.value.text.trim()
              val xftpAddress = xftp.value.text.trim()
              val parsedSmp = ServerAddress.parseServerAddress(smpAddress)
              val parsedXftp = ServerAddress.parseServerAddress(xftpAddress)
              when {
                parsedSmp?.serverProtocol != ServerProtocol.SMP || !parsedSmp.valid -> {
                  smpState = ServerTestState.Failed
                  failureText = generalGetString(MR.strings.onboarding_invalid_smp_server)
                }
                parsedXftp?.serverProtocol != ServerProtocol.XFTP || !parsedXftp.valid -> {
                  xftpState = ServerTestState.Failed
                  failureText = generalGetString(MR.strings.onboarding_invalid_xftp_server)
                }
                else -> {
                  smpState = ServerTestState.Testing
                  xftpState = ServerTestState.Testing
                  failureText = null
                  testJob = scope.launch {
                    try {
                      val (smpFailure, xftpFailure) = testOnboardingServers(chatModel.controller, smpAddress, xftpAddress)
                      smpState = if (smpFailure == null) ServerTestState.Passed else ServerTestState.Failed
                      xftpState = if (xftpFailure == null) ServerTestState.Passed else ServerTestState.Failed
                      if (smpFailure == null && xftpFailure == null) {
                        OnboardingServerState.verifiedServers = VerifiedOnboardingServers(smpAddress, xftpAddress)
                      } else {
                        failureText = listOfNotNull(
                          smpFailure?.let { "SMP: ${it.localizedDescription}" },
                          xftpFailure?.let { "XFTP: ${it.localizedDescription}" }
                        ).joinToString("\n")
                      }
                    } catch (e: CancellationException) {
                      throw e
                    } catch (e: Throwable) {
                      Log.e(TAG, "Unable to test onboarding servers: ${e.stackTraceToString()}")
                      smpState = ServerTestState.Failed
                      xftpState = ServerTestState.Failed
                      failureText = e.message ?: generalGetString(MR.strings.smp_servers_test_failed)
                    } finally {
                      testJob = null
                    }
                  }
                }
              }
            }
          )
          Spacer(Modifier.height(DEFAULT_PADDING_HALF))
          OnboardingActionButton(
            Modifier.fillMaxWidth(),
            labelId = MR.strings.continue_to_next_step,
            onboarding = OnboardingStage.Step2_CreateProfile,
            enabled = OnboardingServerState.verifiedServers != null && !testing,
            onclick = null
          )
        }
      }
    }
  }
}

@Composable
private fun ServerAddressField(
  title: String,
  value: MutableState<TextFieldValue>,
  state: ServerTestState,
  enabled: Boolean,
  onChange: (TextFieldValue) -> Unit
) {
  Column(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(title, style = MaterialTheme.typography.h3)
      Spacer(Modifier.weight(1f))
      when (state) {
        ServerTestState.Testing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        ServerTestState.Passed -> Icon(painterResource(MR.images.ic_check), null, tint = SimplexGreen)
        ServerTestState.Failed -> Icon(painterResource(MR.images.ic_close), null, tint = MaterialTheme.colors.error)
        ServerTestState.NotTested -> Unit
      }
    }
    Spacer(Modifier.height(DEFAULT_PADDING_HALF))
    OutlinedTextField(
      value = value.value,
      onValueChange = onChange,
      modifier = Modifier.fillMaxWidth(),
      enabled = enabled,
      singleLine = true,
      placeholder = { Text(stringResource(MR.strings.server_address)) },
      shape = RoundedCornerShape(6.dp)
    )
  }
}

private suspend fun testOnboardingServers(
  controller: ChatController,
  smp: String,
  xftp: String
): Pair<ProtocolTestFailure?, ProtocolTestFailure?> = withContext(Dispatchers.IO) {
  val root = File(dataDir, "server_test_${UUID.randomUUID()}")
  val database = File(root, "simplex_test")
  var ctrl: Long? = null
  var primaryFailure: Throwable? = null
  try {
    if (!root.mkdirs()) throw IllegalStateException("Unable to create temporary server test directory")
    val (status, temporaryCtrl) = chatInitTemporaryDatabase(database.absolutePath)
    if (status != DBMigrationResult.OK || temporaryCtrl == null) {
      throw IllegalStateException("Unable to initialize temporary server test database: $status")
    }
    ctrl = temporaryCtrl
    val user = controller.apiCreateActiveUser(
      null,
      Profile(displayName = "Temp", fullName = "", shortDescr = null),
      ctrl = temporaryCtrl
    ) ?: throw IllegalStateException("Unable to create temporary server test user")
    if (!controller.apiSetNetworkConfig(controller.getNetCfg(), showAlertOnError = false, ctrl = temporaryCtrl)) {
      throw IllegalStateException("Unable to configure temporary server test controller")
    }
    controller.apiSetAppFilePaths(root.absolutePath, root.absolutePath, root.absolutePath, root.absolutePath, temporaryCtrl)
    controller.apiStartChat(temporaryCtrl, mainApp = false)
    controller.testProtoServer(null, smp, user.userId, temporaryCtrl) to
        controller.testProtoServer(null, xftp, user.userId, temporaryCtrl)
  } catch (e: Throwable) {
    primaryFailure = e
    throw e
  } finally {
    withContext(NonCancellable + Dispatchers.IO) {
      val cleanupErrors = mutableListOf<String>()
      var canDelete = true
      ctrl?.let {
        runCatching { controller.apiStopChat(it) }
          .onFailure { error ->
            canDelete = false
            cleanupErrors += "Unable to stop temporary server test chat: ${error.message}"
          }
        delay(200)
        if (canDelete) {
          val closeError = runCatching { chatCloseStore(it) }.getOrElse { error -> error.stackTraceToString() }
          if (closeError.isNotEmpty()) {
            canDelete = false
            cleanupErrors += "Unable to close temporary server test database: $closeError"
          }
        }
      }
      if (canDelete) {
        for (attempt in 0 until 10) {
          root.deleteRecursively()
          if (!root.exists()) break
          delay(100)
        }
        if (root.exists()) cleanupErrors += "Unable to delete temporary server test database: ${root.absolutePath}"
      }
      if (cleanupErrors.isNotEmpty()) {
        val cleanupFailure = IllegalStateException(cleanupErrors.joinToString("\n"))
        Log.e(TAG, cleanupFailure.message ?: "Unable to clean temporary server test database")
        primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
      }
    }
  }
}
