package chat.simplex.common.views.chat.item

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.stringResource
import org.jetbrains.compose.animatedimage.AnimatedImage
import org.jetbrains.compose.animatedimage.animate
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

@Composable
actual fun AnimatedStickerImage(data: ByteArray, fallback: ImageBitmap, modifier: Modifier) {
  val animated = remember(data) {
    runCatching { AnimatedImage(Codec.makeFromData(Data.makeFromBytes(data))) }.getOrNull()
  }
  Image(
    bitmap = animated?.animate() ?: fallback,
    contentDescription = stringResource(MR.strings.sticker),
    modifier = modifier,
    contentScale = ContentScale.Fit
  )
}
