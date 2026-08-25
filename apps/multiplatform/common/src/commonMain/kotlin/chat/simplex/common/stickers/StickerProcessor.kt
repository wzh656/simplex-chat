package chat.simplex.common.stickers

import java.net.URI

sealed class StickerProcessingResult {
  data class Success(val sticker: ProcessedSticker) : StickerProcessingResult()
  data class Error(val reason: StickerProcessingError) : StickerProcessingResult()
}

enum class StickerProcessingError {
  UnsupportedFormat,
  InvalidImage,
  FileTooLarge,
  DimensionsTooLarge,
  TooManyFrames,
  DurationTooLong,
  DecodeBudgetExceeded,
  CompressionFailed
}

expect suspend fun processSticker(uri: URI): StickerProcessingResult
expect suspend fun processStickerBytes(bytes: ByteArray): StickerProcessingResult

internal fun detectStickerMime(bytes: ByteArray): String? = when {
  bytes.size >= 6 && (bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a")) -> "image/gif"
  bytes.size >= 12 && bytes.startsWithAscii("RIFF") && bytes.startsWithAscii("WEBP", 8) -> "image/webp"
  bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes.startsWithAscii("PNG", 1) &&
      bytes[4] == 0x0d.toByte() && bytes[5] == 0x0a.toByte() && bytes[6] == 0x1a.toByte() && bytes[7] == 0x0a.toByte() -> "image/png"
  bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
  else -> null
}

suspend fun validateStickerContent(
  bytes: ByteArray,
  sha256: String,
  mime: String,
  animated: Boolean,
  width: Int,
  height: Int
): Boolean {
  if (sha256Hex(bytes) != sha256 || detectStickerMime(bytes) != mime || width !in 1..MAX_STICKER_DIMENSION || height !in 1..MAX_STICKER_DIMENSION) return false
  if (animated) {
    val info = inspectAnimatedSticker(bytes) ?: return false
    return info.mime == mime && info.width == width && info.height == height && validateAnimatedSticker(info, bytes.size) == null
  }
  if (bytes.size.toLong() > MAX_STATIC_STICKER_SIZE || mime == "image/gif") return false
  val info = inspectStaticSticker(bytes) ?: return false
  return info.first == width && info.second == height
}

expect fun inspectStaticSticker(bytes: ByteArray): Pair<Int, Int>?

internal fun isValidStickerPreview(preview: String): Boolean {
  if (preview.length > 20_000) return false
  val encoded = when {
    preview.startsWith("data:image/png;base64,") -> preview.removePrefix("data:image/png;base64,")
    preview.startsWith("data:image/jpg;base64,") -> preview.removePrefix("data:image/jpg;base64,")
    else -> return false
  }
  val bytes = runCatching { java.util.Base64.getDecoder().decode(encoded) }.getOrNull() ?: return false
  return bytes.size <= 15_000 && detectStickerMime(bytes) in setOf("image/png", "image/jpeg")
}

internal data class AnimatedStickerInfo(
  val mime: String,
  val width: Int,
  val height: Int,
  val frameCount: Int,
  val durationMs: Long
)

internal fun inspectAnimatedSticker(bytes: ByteArray): AnimatedStickerInfo? =
  inspectGif(bytes) ?: inspectAnimatedWebP(bytes)

private fun inspectGif(bytes: ByteArray): AnimatedStickerInfo? {
  if (bytes.size < 13 || !(bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a"))) return null
  val width = bytes.u16le(6)
  val height = bytes.u16le(8)
  var offset = 13
  val globalColorTable = bytes[10].toInt() and 0x80 != 0
  if (globalColorTable) offset += 3 * (1 shl ((bytes[10].toInt() and 0x07) + 1))
  var frames = 0
  var duration = 0L
  var pendingDelay = 0
  while (offset < bytes.size) {
    when (bytes[offset++].toInt() and 0xff) {
      0x21 -> {
        if (offset >= bytes.size) return null
        val label = bytes[offset++].toInt() and 0xff
        if (label == 0xf9) {
          if (offset + 5 >= bytes.size || bytes[offset].toInt() and 0xff != 4) return null
          pendingDelay = bytes.u16le(offset + 2) * 10
          offset += 6
        } else {
          offset = bytes.skipSubBlocks(offset) ?: return null
        }
      }
      0x2c -> {
        if (offset + 9 > bytes.size) return null
        val packed = bytes[offset + 8].toInt() and 0xff
        offset += 9
        if (packed and 0x80 != 0) offset += 3 * (1 shl ((packed and 0x07) + 1))
        if (offset >= bytes.size) return null
        offset++ // LZW code size
        offset = bytes.skipSubBlocks(offset) ?: return null
        frames++
        duration += pendingDelay.coerceAtLeast(20)
        pendingDelay = 0
      }
      0x3b -> break
      else -> return null
    }
  }
  return if (frames > 0) AnimatedStickerInfo("image/gif", width, height, frames, duration) else null
}

private fun inspectAnimatedWebP(bytes: ByteArray): AnimatedStickerInfo? {
  if (bytes.size < 30 || !bytes.startsWithAscii("RIFF") || !bytes.startsWithAscii("WEBP", 8)) return null
  var offset = 12
  var width = 0
  var height = 0
  var frames = 0
  var duration = 0L
  var animated = false
  while (offset + 8 <= bytes.size) {
    val type = bytes.ascii(offset, 4)
    val size = bytes.u32le(offset + 4)
    if (size < 0 || offset + 8L + size > bytes.size) return null
    val data = offset + 8
    when (type) {
      "VP8X" -> if (size >= 10) {
        animated = bytes[data].toInt() and 0x02 != 0
        width = 1 + bytes.u24le(data + 4)
        height = 1 + bytes.u24le(data + 7)
      }
      "ANMF" -> if (size >= 16) {
        frames++
        duration += bytes.u24le(data + 12).coerceAtLeast(20)
      }
    }
    offset = (offset + 8 + size + (size and 1)).toInt()
  }
  return if (animated && frames > 0) AnimatedStickerInfo("image/webp", width, height, frames, duration) else null
}

internal fun validateAnimatedSticker(info: AnimatedStickerInfo, byteSize: Int): StickerProcessingError? = when {
  byteSize.toLong() > MAX_STICKER_FILE_SIZE -> StickerProcessingError.FileTooLarge
  info.width <= 0 || info.height <= 0 -> StickerProcessingError.InvalidImage
  info.width > MAX_STICKER_DIMENSION || info.height > MAX_STICKER_DIMENSION -> StickerProcessingError.DimensionsTooLarge
  info.frameCount > MAX_STICKER_FRAMES -> StickerProcessingError.TooManyFrames
  info.durationMs > MAX_STICKER_DURATION_MS -> StickerProcessingError.DurationTooLong
  info.width.toLong() * info.height * info.frameCount > MAX_STICKER_DECODED_PIXELS -> StickerProcessingError.DecodeBudgetExceeded
  else -> null
}

private fun ByteArray.startsWithAscii(value: String, offset: Int = 0): Boolean =
  size >= offset + value.length && value.indices.all { this[offset + it].toInt() and 0xff == value[it].code }

private fun ByteArray.ascii(offset: Int, length: Int): String =
  String(this, offset, length, Charsets.US_ASCII)

private fun ByteArray.u16le(offset: Int): Int =
  (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

private fun ByteArray.u24le(offset: Int): Int =
  u16le(offset) or ((this[offset + 2].toInt() and 0xff) shl 16)

private fun ByteArray.u32le(offset: Int): Long =
  u16le(offset).toLong() or (u16le(offset + 2).toLong() shl 16)

private fun ByteArray.skipSubBlocks(start: Int): Int? {
  var offset = start
  while (offset < size) {
    val length = this[offset++].toInt() and 0xff
    if (length == 0) return offset
    if (offset + length > size) return null
    offset += length
  }
  return null
}
