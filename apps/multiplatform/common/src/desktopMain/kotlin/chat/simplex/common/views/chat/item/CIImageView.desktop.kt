package chat.simplex.common.views.chat.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import chat.simplex.common.model.CIFile
import chat.simplex.common.platform.*
import chat.simplex.common.views.helpers.ModalManager
import org.jetbrains.compose.animatedimage.AnimatedImage
import org.jetbrains.compose.animatedimage.animate
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

@Composable
actual fun SimpleAndAnimatedImageView(
  data: ByteArray,
  imageBitmap: ImageBitmap,
  file: CIFile?,
  imageProvider: () -> ImageGalleryProvider,
  smallView: Boolean,
  ImageView: @Composable (painter: Painter, onClick: () -> Unit) -> Unit
) {
  val animated = remember(data) {
    runCatching { AnimatedImage(Codec.makeFromData(Data.makeFromBytes(data))) }.getOrNull()
  }
  ImageView(BitmapPainter(animated?.animate() ?: imageBitmap)) {
    if (getLoadedFilePath(file) != null) {
      ModalManager.fullscreen.showCustomModal(animated = false) { close ->
        ImageFullScreenView(imageProvider, close)
      }
    }
  }
}
