package chat.simplex.common.stickers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.ui.graphics.asImageBitmap
import chat.simplex.common.platform.inputStream
import chat.simplex.common.platform.resizeImageToStrSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import kotlin.math.max
import kotlin.math.roundToInt
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

    val animation = inspectAnimatedSticker(bytes)
    if (animation != null) {
      validateAnimatedSticker(animation, bytes.size)?.let {
        return@withContext StickerProcessingResult.Error(it)
      }
      val firstFrame = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: return@withContext StickerProcessingResult.Error(StickerProcessingError.InvalidImage)
      val preview = resizeImageToStrSize(firstFrame.asImageBitmap(), STICKER_PREVIEW_DATA_LENGTH)
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

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType !in SUPPORTED_STATIC_MIME_TYPES) {
      return@withContext StickerProcessingResult.Error(StickerProcessingError.UnsupportedFormat)
    }
    if (bounds.outWidth > MAX_STICKER_SOURCE_DIMENSION || bounds.outHeight > MAX_STICKER_SOURCE_DIMENSION ||
      bounds.outWidth.toLong() * bounds.outHeight > MAX_STICKER_SOURCE_PIXELS
    ) {
      return@withContext StickerProcessingResult.Error(StickerProcessingError.DimensionsTooLarge)
    }

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > MAX_STICKER_DIMENSION * 2 || bounds.outHeight / sampleSize > MAX_STICKER_DIMENSION * 2) {
      sampleSize *= 2
    }
    val decoded = BitmapFactory.decodeByteArray(
      bytes,
      0,
      bytes.size,
      BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
    ) ?: return@withContext StickerProcessingResult.Error(StickerProcessingError.InvalidImage)
    var bitmap = applyExifOrientation(decoded, bytes)
    if (max(bitmap.width, bitmap.height) > MAX_STICKER_DIMENSION) {
      val ratio = MAX_STICKER_DIMENSION.toFloat() / max(bitmap.width, bitmap.height)
      bitmap = Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
        (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
        true
      )
    }
    val encoded = encodeStaticSticker(bitmap)
      ?: return@withContext StickerProcessingResult.Error(StickerProcessingError.CompressionFailed)
    val preview = resizeImageToStrSize(bitmap.asImageBitmap(), STICKER_PREVIEW_DATA_LENGTH)
    StickerProcessingResult.Success(
      ProcessedSticker(
        bytes = encoded.bytes,
        sha256 = sha256Hex(encoded.bytes),
        mime = encoded.mime,
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

actual fun inspectStaticSticker(bytes: ByteArray): Pair<Int, Int>? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
  if (bounds.outWidth !in 1..MAX_STICKER_DIMENSION || bounds.outHeight !in 1..MAX_STICKER_DIMENSION || bounds.outMimeType !in SUPPORTED_STATIC_MIME_TYPES) return null
  val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
  return decoded.width to decoded.height
}

private data class EncodedSticker(val bytes: ByteArray, val mime: String)

private fun encodeStaticSticker(source: Bitmap): EncodedSticker? {
  var bitmap = source
  val hasAlpha = bitmap.hasAlpha()
  var quality = 80
  repeat(8) {
    val output = ByteArrayOutputStream()
    val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    if (!bitmap.compress(format, quality, output)) return null
    val bytes = output.toByteArray()
    if (bytes.size.toLong() <= MAX_STATIC_STICKER_SIZE) {
      return EncodedSticker(bytes, if (hasAlpha) "image/png" else "image/jpeg")
    }
    if (!hasAlpha && quality > 60) {
      quality -= 10
    } else {
      val ratio = sqrt(MAX_STATIC_STICKER_SIZE.toDouble() / bytes.size).coerceAtMost(0.9)
      val width = (bitmap.width * ratio).roundToInt().coerceAtLeast(64)
      val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(64)
      if (width == bitmap.width && height == bitmap.height) return null
      bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
  }
  return null
}

private fun applyExifOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
  val orientation = runCatching {
    ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
  }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
  val matrix = Matrix()
  when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
    ExifInterface.ORIENTATION_TRANSPOSE -> {
      matrix.setRotate(90f)
      matrix.postScale(-1f, 1f)
    }
    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
    ExifInterface.ORIENTATION_TRANSVERSE -> {
      matrix.setRotate(-90f)
      matrix.postScale(-1f, 1f)
    }
    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
    else -> return bitmap
  }
  return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private val SUPPORTED_STATIC_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
