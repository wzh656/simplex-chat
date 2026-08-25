package chat.simplex.common.stickers

import androidx.compose.ui.graphics.asComposeImageBitmap
import chat.simplex.common.platform.compressImageData
import chat.simplex.common.platform.inputStream
import chat.simplex.common.platform.resizeImageToStrSize
import chat.simplex.common.platform.hasAlpha
import chat.simplex.common.platform.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import java.io.ByteArrayOutputStream
import java.net.URI
import kotlin.math.max
import kotlin.math.sqrt

private const val MAX_STICKER_SOURCE_SIZE = 50 * 1024 * 1024
private const val MAX_STICKER_SOURCE_DIMENSION = 16_384
private const val MAX_STICKER_SOURCE_PIXELS = 40_000_000L
private const val STICKER_PREVIEW_DATA_LENGTH = 14_000L

actual suspend fun processSticker(uri: URI): StickerProcessingResult = withContext(Dispatchers.IO) {
  val bytes = uri.inputStream()?.use { input ->
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      if (output.size() + read > MAX_STICKER_SOURCE_SIZE) return@withContext StickerProcessingResult.Error(StickerProcessingError.FileTooLarge)
      output.write(buffer, 0, read)
    }
    output.toByteArray()
  } ?: return@withContext StickerProcessingResult.Error(StickerProcessingError.InvalidImage)
  processStickerBytes(bytes)
}

actual suspend fun processStickerBytes(bytes: ByteArray): StickerProcessingResult = withContext(Dispatchers.IO) {
  if (bytes.size > MAX_STICKER_SOURCE_SIZE) return@withContext StickerProcessingResult.Error(StickerProcessingError.FileTooLarge)
  try {

    val data = Data.makeFromBytes(bytes)
    val codec = runCatching { Codec.makeFromData(data) }.getOrNull()
      ?: return@withContext StickerProcessingResult.Error(StickerProcessingError.UnsupportedFormat)
    val width = codec.imageInfo.width
    val height = codec.imageInfo.height

    val animation = inspectAnimatedSticker(bytes)
    if (animation != null) {
      validateAnimatedSticker(animation, bytes.size)?.let {
        return@withContext StickerProcessingResult.Error(it)
      }
      val frame = Bitmap().apply { allocPixels(codec.imageInfo) }
      codec.readPixels(frame, 0)
      val preview = resizeImageToStrSize(frame.asComposeImageBitmap(), STICKER_PREVIEW_DATA_LENGTH)
      return@withContext StickerProcessingResult.Success(
        ProcessedSticker(
          bytes = bytes,
          sha256 = sha256Hex(bytes),
          mime = animation.mime,
          animated = true,
          width = animation.width,
          height = animation.height,
          preview = preview
        )
      )
    }

    if (width > MAX_STICKER_SOURCE_DIMENSION || height > MAX_STICKER_SOURCE_DIMENSION ||
      width.toLong() * height > MAX_STICKER_SOURCE_PIXELS
    ) {
      return@withContext StickerProcessingResult.Error(StickerProcessingError.DimensionsTooLarge)
    }
    val frame = Bitmap().apply { allocPixels(codec.imageInfo) }
    codec.readPixels(frame, 0)
    var bitmap = frame.asComposeImageBitmap()
    if (max(bitmap.width, bitmap.height) > MAX_STICKER_DIMENSION) {
      val ratio = MAX_STICKER_DIMENSION.toDouble() / max(bitmap.width, bitmap.height)
      bitmap = bitmap.scale(
        (bitmap.width * ratio).toInt().coerceAtLeast(1),
        (bitmap.height * ratio).toInt().coerceAtLeast(1)
      )
    }
    val hasAlpha = bitmap.hasAlpha()
    var encoded = compressImageData(bitmap, hasAlpha).toByteArray()
    while (encoded.size.toLong() > MAX_STATIC_STICKER_SIZE) {
      val ratio = sqrt(MAX_STATIC_STICKER_SIZE.toDouble() / encoded.size).coerceAtMost(0.9)
      val scaled = bitmap.scale(
        (bitmap.width * ratio).toInt().coerceAtLeast(64),
        (bitmap.height * ratio).toInt().coerceAtLeast(64)
      )
      if (scaled.width == bitmap.width && scaled.height == bitmap.height) break
      bitmap = scaled
      encoded = compressImageData(bitmap, hasAlpha).toByteArray()
    }
    if (encoded.size.toLong() > MAX_STATIC_STICKER_SIZE) {
      return@withContext StickerProcessingResult.Error(StickerProcessingError.CompressionFailed)
    }
    val preview = resizeImageToStrSize(bitmap, STICKER_PREVIEW_DATA_LENGTH)
    StickerProcessingResult.Success(
      ProcessedSticker(
        bytes = encoded,
        sha256 = sha256Hex(encoded),
        mime = if (hasAlpha) "image/png" else "image/jpeg",
        animated = false,
        width = bitmap.width,
        height = bitmap.height,
        preview = preview
      )
    )
  } catch (_: Throwable) {
    StickerProcessingResult.Error(StickerProcessingError.InvalidImage)
  }
}

actual fun inspectStaticSticker(bytes: ByteArray): Pair<Int, Int>? = runCatching {
  val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
  val info = codec.imageInfo
  if (info.width !in 1..MAX_STICKER_DIMENSION || info.height !in 1..MAX_STICKER_DIMENSION) return@runCatching null
  Bitmap().apply { allocPixels(info) }.also { codec.readPixels(it, 0) }
  info.width to info.height
}.getOrNull()
