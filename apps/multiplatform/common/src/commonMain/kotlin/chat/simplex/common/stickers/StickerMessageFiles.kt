package chat.simplex.common.stickers

import chat.simplex.common.model.ChatController
import chat.simplex.common.model.CryptoFile
import chat.simplex.common.model.writeCryptoFile
import chat.simplex.common.platform.getAppFilePath
import chat.simplex.common.platform.tmpDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

suspend fun createStickerMessageFile(asset: StickerAsset, bytes: ByteArray, remote: Boolean): CryptoFile? =
  withContext(Dispatchers.IO) {
    if (bytes.size.toLong() > MAX_STICKER_FILE_SIZE || sha256Hex(bytes) != asset.sha256) return@withContext null
    val extension = asset.fileExtension ?: return@withContext null
    val fileName = "STK_${UUID.randomUUID()}.$extension"
    val destination = if (remote) File(tmpDir, fileName) else File(getAppFilePath(fileName))
    destination.parentFile?.mkdirs()
    try {
      if (remote || !ChatController.appPrefs.privacyEncryptLocalFiles.get()) {
        destination.writeBytes(bytes)
        CryptoFile.plain(if (remote) destination.absolutePath else fileName)
      } else {
        val args = writeCryptoFile(destination.absolutePath, bytes)
        CryptoFile(fileName, args)
      }
    } catch (_: Throwable) {
      destination.delete()
      null
    }
  }
