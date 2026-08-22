package chat.simplex.common.platform

import androidx.compose.runtime.*
import chat.simplex.common.*
import chat.simplex.common.views.helpers.AlertManager
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.res.MR
import java.awt.Desktop
import java.io.*
import java.net.URI

actual val dataDir: File = File(desktopPlatform.dataPath)
// No deleteOnExit() here: a transient second instance also inits this val, and its exit
// would delete the shared folder while the primary runs. Registered in Main instead.
actual val tmpDir: File = File(System.getProperty("java.io.tmpdir") + File.separator + "grayheterotopia")
actual val filesDir: File = File(dataDir.absolutePath + File.separator + "grayheterotopia_v1_files")
actual val appFilesDir: File = filesDir
actual val wallpapersDir: File = File(dataDir.absolutePath + File.separator + "grayheterotopia_v1_assets" + File.separator + "wallpapers").also { it.mkdirs() }
actual val coreTmpDir: File = File(dataDir.absolutePath + File.separator + "tmp")
actual val dbAbsolutePrefixPath: String = dataDir.absolutePath + File.separator + "grayheterotopia_v1"
actual val preferencesDir = File(desktopPlatform.configPath).also { it.parentFile.mkdirs() }
// No deleteRecursively() here (see tmpDir): a second instance would wipe this shared
// folder while the primary runs. Cleaned in Main instead.
actual val preferencesTmpDir = File(desktopPlatform.configPath, "tmp")

actual val chatDatabaseFileName: String = "grayheterotopia_v1_chat.db"
actual val agentDatabaseFileName: String = "grayheterotopia_v1_agent.db"

actual val databaseExportDir: File = tmpDir

actual val remoteHostsDir: File = File(dataDir.absolutePath + File.separator + "remote_hosts")

actual fun desktopOpenDatabaseDir() {
  desktopOpenDir(dataDir)
}

actual fun desktopOpenDir(dir: File) {
  if (Desktop.isDesktopSupported()) {
    try {
      Desktop.getDesktop().open(dir);
    } catch (e: IOException) {
      Log.e(TAG, e.stackTraceToString())
      AlertManager.shared.showAlertMsg(
        title = generalGetString(MR.strings.unknown_error),
        text = e.stackTraceToString()
      )
    }
  }
}

@Composable
actual fun rememberFileChooserLauncher(getContent: Boolean, rememberedValue: Any?, onResult: (URI?) -> Unit): FileChooserLauncher =
  remember(rememberedValue) { FileChooserLauncher(getContent, onResult) }

@Composable
actual fun rememberFileChooserMultipleLauncher(onResult: (List<URI>) -> Unit): FileChooserMultipleLauncher =
  remember { FileChooserMultipleLauncher(onResult) }

actual class FileChooserLauncher actual constructor() {
  var getContent: Boolean = false
  lateinit var onResult: (URI?) -> Unit

  constructor(getContent: Boolean, onResult: (URI?) -> Unit): this() {
    this.getContent = getContent
    this.onResult = onResult
  }

  actual suspend fun launch(input: String) {
    val res: File? = if (getContent) {
      simplexWindowState.openDialog.awaitResult(
        DialogParams(
          allowMultiple = false,
          fileFilter = fileFilter(input),
          fileFilterDescription = fileFilterDescription(input),
          fileExtensions = fileExtensions(input),
        )
      )
    } else {
      simplexWindowState.saveDialog.awaitResult(DialogParams(filename = input))
    }
    onResult(res?.toURI())
  }
}

actual class FileChooserMultipleLauncher actual constructor() {
  lateinit var onResult: (List<URI>) -> Unit

  constructor(onResult: (List<URI>) -> Unit): this() {
    this.onResult = onResult
  }

  actual suspend fun launch(input: String) {
    val params = DialogParams(
      allowMultiple = true,
      fileFilter = fileFilter(input),
      fileFilterDescription = fileFilterDescription(input),
      fileExtensions = fileExtensions(input),
    )
    onResult(simplexWindowState.openMultipleDialog.awaitResult(params).map { it.toURI() })
  }
}

private fun fileFilter(input: String): (File?) -> Boolean = when(input) {
  "image/*" -> { file -> if (file?.isDirectory == true) true else if (file != null) isImage(file.toURI()) else false }
  "video/*" -> { file -> if (file?.isDirectory == true) true else if (file != null) isVideo(file.toURI()) else false }
  "*/*" -> { _ -> true }
  else -> { _ -> true }
}

private fun fileExtensions(input: String): Set<String> = when (input) {
  "image/*" -> setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
  "video/*" -> setOf("mp4", "m4v", "mov", "avi", "mkv", "webm", "3gp")
  "application/zip" -> setOf("zip")
  "*/*" -> emptySet()
  else -> input.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.let { setOf(it) } ?: emptySet()
}

private fun fileFilterDescription(input: String): String = when(input) {
  "image/*" -> generalGetString(MR.strings.gallery_image_button)
  "video/*" -> generalGetString(MR.strings.gallery_video_button)
  "*/*" -> generalGetString(MR.strings.choose_file)
  else -> ""
}

actual fun URI.inputStream(): InputStream? = toFile().inputStream()
actual fun URI.outputStream(): OutputStream = toFile().outputStream()
