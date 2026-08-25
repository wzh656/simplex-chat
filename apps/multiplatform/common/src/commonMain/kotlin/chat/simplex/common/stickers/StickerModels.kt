package chat.simplex.common.stickers

import chat.simplex.common.model.CryptoFileArgs
import kotlinx.serialization.Serializable

const val STICKER_SCHEMA_VERSION = 1
const val MAX_STICKER_FILE_SIZE = 2L * 1024L * 1024L
const val MAX_STATIC_STICKER_SIZE = 512L * 1024L
const val MAX_STICKER_AUTO_RECEIVE_SIZE = 512L * 1024L
const val MAX_STICKER_DIMENSION = 512
const val MAX_STICKER_FRAMES = 180
const val MAX_STICKER_DURATION_MS = 15_000L
const val MAX_STICKER_DECODED_PIXELS = 50_000_000L

@Serializable
data class StickerOwner(
  val remoteHostId: Long?,
  val userId: Long
)

@Serializable
data class StickerLibrary(
  val schemaVersion: Int = STICKER_SCHEMA_VERSION,
  val packs: List<StickerPack> = emptyList(),
  val assets: Map<String, StickerAsset> = emptyMap()
)

@Serializable
data class StickerPack(
  val id: String,
  val name: String,
  val stickerHashes: List<String> = emptyList(),
  val coverHash: String? = null
) {
  val effectiveCoverHash: String?
    get() = coverHash?.takeIf(stickerHashes::contains) ?: stickerHashes.firstOrNull()
}

@Serializable
data class StickerAsset(
  val sha256: String,
  val mime: String,
  val animated: Boolean,
  val width: Int,
  val height: Int,
  val byteSize: Long,
  val encryptedFileName: String,
  val cryptoArgs: CryptoFileArgs,
  val preview: String,
  val lastUsedAt: Long
) {
  val fileExtension: String?
    get() = when (mime) {
      "image/png" -> "png"
      "image/jpeg" -> "jpg"
      "image/gif" -> "gif"
      "image/webp" -> "webp"
      else -> null
    }
}

data class ProcessedSticker(
  val bytes: ByteArray,
  val sha256: String,
  val mime: String,
  val animated: Boolean,
  val width: Int,
  val height: Int,
  val preview: String
)

data class StickerLibraryState(
  val library: StickerLibrary = StickerLibrary(),
  val loaded: Boolean = false,
  val error: String? = null
)

data class StickerAddResult(
  val asset: StickerAsset,
  val addedToPack: Boolean
)
