package chat.simplex.common.views.chat.item

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import chat.simplex.res.MR
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import com.github.penfeizhou.animation.loader.ByteBufferLoader
import com.github.penfeizhou.animation.webp.WebPDrawable
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import dev.icerock.moko.resources.compose.stringResource
import java.nio.ByteBuffer

@Composable
actual fun AnimatedStickerImage(data: ByteArray, fallback: ImageBitmap, modifier: Modifier) {
  if (Build.VERSION.SDK_INT < 28 && data.isAnimatedWebP()) {
    val drawable = remember(data) {
      WebPDrawable(object : ByteBufferLoader() {
        override fun getByteBuffer(): ByteBuffer = ByteBuffer.wrap(data)
      })
    }
    DisposableEffect(drawable) {
      drawable.start()
      onDispose { drawable.stop() }
    }
    AndroidView(
      factory = { context -> android.widget.ImageView(context).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } },
      modifier = modifier,
      update = { if (it.drawable !== drawable) it.setImageDrawable(drawable) }
    )
  } else {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
      model = ImageRequest.Builder(context).data(data).size(coil.size.Size.ORIGINAL).build(),
      placeholder = BitmapPainter(fallback),
      error = BitmapPainter(fallback),
      imageLoader = stickerImageLoader
    )
    Image(
      painter = painter,
      contentDescription = stringResource(MR.strings.sticker),
      modifier = modifier,
      contentScale = ContentScale.Fit
    )
  }
}

private fun ByteArray.isAnimatedWebP(): Boolean {
  if (size < 30 || String(this, 0, 4, Charsets.US_ASCII) != "RIFF" || String(this, 8, 4, Charsets.US_ASCII) != "WEBP") return false
  var offset = 12
  while (offset + 8 <= size) {
    val type = String(this, offset, 4, Charsets.US_ASCII)
    val length = (this[offset + 4].toInt() and 0xff) or ((this[offset + 5].toInt() and 0xff) shl 8) or
        ((this[offset + 6].toInt() and 0xff) shl 16) or ((this[offset + 7].toInt() and 0xff) shl 24)
    if (length < 0 || offset + 8L + length > size) return false
    if (type == "ANIM") return true
    offset += 8 + length + (length and 1)
  }
  return false
}

private val stickerImageLoader = ImageLoader.Builder(chat.simplex.common.platform.androidAppContext)
  .networkObserverEnabled(false)
  .components {
    if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
  }
  .build()
