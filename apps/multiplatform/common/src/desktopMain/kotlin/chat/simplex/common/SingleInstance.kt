package chat.simplex.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import chat.simplex.common.platform.isInNightMode
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.common.views.helpers.AppDialogActions
import chat.simplex.common.views.helpers.AppDialogBody
import chat.simplex.common.views.helpers.AppDialogSurface
import chat.simplex.res.MR

import chat.simplex.common.platform.Log
import chat.simplex.common.platform.TAG
import chat.simplex.common.platform.dataDir
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.*
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

private var lockHandle: FileLock? = null
private var watcher: WatchService? = null

private val lockPath get() = dataDir.resolve("grayheterotopia.started").toPath()
private val showPath get() = dataDir.resolve("grayheterotopia.show").toPath()

var singleInstanceLock = false
  private set

private sealed interface LockResult {
  class Acquired(val lock: FileLock) : LockResult
  object Taken : LockResult
  object Failed : LockResult
}

fun acquireSingleInstance(): Boolean {
  dataDir.mkdirs()
  when (val result = tryAcquireLock()) {
    is LockResult.Acquired -> {
      lockHandle = result.lock
      singleInstanceLock = true
      deleteShowFile()
      startShowFileWatcher()
      return true
    }
    LockResult.Failed -> {
      return true
    }
    LockResult.Taken -> {
      // Ensure the signal file exists (createShowFile is a no-op if it does)
      // and wait up to 1s for the primary's watcher to consume it. If still
      // there after the wait, the primary is hung — let the user decide.
      createShowFile()
      val deadline = System.currentTimeMillis() + 1000
      while (Files.exists(showPath) && System.currentTimeMillis() < deadline) {
        try { Thread.sleep(50) } catch (_: InterruptedException) { break }
      }
      if (!Files.exists(showPath)) return false
      val start = showSingleInstanceAlert()
      if (start) deleteShowFile()
      return start
    }
  }
}

private fun tryAcquireLock(): LockResult {
  val channel = try {
    FileChannel.open(lockPath, READ, WRITE, CREATE)
  } catch (e: IOException) {
    Log.w(TAG, "single-instance: cannot open lock file: ${e.message}")
    return LockResult.Failed
  }
  return try {
    val lock = channel.tryLock(0L, 1L, false)
    if (lock != null) {
      LockResult.Acquired(lock)
    } else {
      channel.close()
      LockResult.Taken
    }
  } catch (_: OverlappingFileLockException) {
    Log.w(TAG, "single-instance: overlapping lock in same JVM")
    LockResult.Failed
  } catch (e: IOException) {
    Log.w(TAG, "single-instance: tryLock failed: ${e.message}")
    channel.close(); LockResult.Failed
  }
}

private fun deleteShowFile() {
  try { Files.deleteIfExists(showPath) } catch (e: IOException) {
    Log.w(TAG, "single-instance: cannot delete show file: ${e.message}")
  }
}

private fun createShowFile() {
  try { Files.createFile(showPath) } catch (_: FileAlreadyExistsException) {
    // Another duplicate already signalled; primary will pick it up.
  } catch (e: IOException) {
    Log.w(TAG, "single-instance: cannot create show file: ${e.message}")
  }
}

private fun showSingleInstanceAlert(): Boolean {
  val title = generalGetString(MR.strings.another_instance_title)
  val message = generalGetString(MR.strings.another_instance_not_responding)
  val cancel = generalGetString(MR.strings.cancel_verb)
  val continueText = generalGetString(MR.strings.continue_to_next_step)
  var start = false

  application(exitProcessOnExit = false) {
    DialogWindow(
      onCloseRequest = ::exitApplication,
      title = title,
      state = rememberDialogState(width = 460.dp, height = 240.dp),
      undecorated = true,
      transparent = true,
      resizable = false,
    ) {
      val colorScheme = if (isInNightMode()) {
        darkColorScheme(
          primary = PortalVioletLight,
          surface = Graphite,
          onSurface = OnGraphite,
          onSurfaceVariant = SlateDark,
          error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
        )
      } else {
        lightColorScheme(
          primary = PortalViolet,
          surface = Fog,
          onSurface = Night,
          onSurfaceVariant = Slate,
          error = androidx.compose.ui.graphics.Color(0xFFBA1A1A),
        )
      }
      MaterialTheme(colorScheme = colorScheme, typography = GrayTypography, shapes = GrayShapes) {
        AppDialogSurface(Modifier.fillMaxSize()) {
          Text(
            title,
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            style = MaterialTheme.typography.titleLarge,
          )
          AppDialogBody {
            Text(
              message,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          AppDialogActions {
            TextButton(onClick = ::exitApplication) { Text(cancel) }
            TextButton(onClick = {
              start = true
              exitApplication()
            }) { Text(continueText) }
          }
        }
      }
    }
  }
  return start
}

private fun startShowFileWatcher() {
  if (watcher != null) return
  val ws = try {
    dataDir.toPath().fileSystem.newWatchService()
  } catch (e: IOException) {
    Log.w(TAG, "single-instance: WatchService failed: ${e.message}")
    return
  }
  dataDir.toPath().register(ws, StandardWatchEventKinds.ENTRY_CREATE)
  watcher = ws
  thread(name = "grayheterotopia-single-instance", isDaemon = true) {
    while (true) {
      val key = try { ws.take() } catch (_: ClosedWatchServiceException) { return@thread } catch (_: InterruptedException) { return@thread }
      for (event in key.pollEvents()) {
        if ((event.context() as? Path)?.fileName?.toString() == "grayheterotopia.show") {
          deleteShowFile()
          SwingUtilities.invokeLater { showWindow() }
        }
      }
      if (!key.reset()) return@thread
    }
  }
}
