package chat.simplex.common.views.chat.item

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.dp
import chat.simplex.common.model.*
import chat.simplex.common.platform.appPlatform
import chat.simplex.common.platform.base64ToBitmap
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.views.helpers.getLoadedImage
import chat.simplex.common.stickers.*
import chat.simplex.common.views.helpers.AlertManager
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.res.MR
import chat.simplex.common.views.helpers.ModalManager
import chat.simplex.common.views.helpers.ModalView
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CIStickerView(
  sticker: MsgContent.MCSticker,
  file: CIFile?,
  owner: StickerOwner?,
  showMenu: MutableState<Boolean>,
  receiveFile: (Long) -> Unit
) {
  val preview = remember(sticker.image) { base64ToBitmap(sticker.image) }
  val data by produceState<ByteArray?>(initialValue = null, sticker.sha256, file, owner) {
    value = withContext(Dispatchers.IO) {
      val cached = owner?.let { StickerRepository.matchingBytes(it, sticker.sha256, sticker.mime, sticker.animated, sticker.width, sticker.height) }
      if (cached != null) return@withContext cached
      val loaded = getLoadedImage(file)?.second ?: return@withContext null
      if (!validateStickerContent(loaded, sticker.sha256, sticker.mime, sticker.animated, sticker.width, sticker.height)) return@withContext null
      owner?.let {
        StickerRepository.cacheReceived(
          it,
          ProcessedSticker(
            bytes = loaded,
            sha256 = sticker.sha256,
            mime = sticker.mime,
            animated = sticker.animated,
            width = sticker.width,
            height = sticker.height,
            preview = sticker.image
          )
        )
      }
      loaded
    }
  }
  val ratio = (sticker.width.toFloat() / sticker.height.coerceAtLeast(1)).coerceIn(0.25f, 4f)
  val maxSize = if (appPlatform.isDesktop) 160.dp else 144.dp
  val stickerSize = if (ratio >= 1f) {
    Modifier.width(maxSize).aspectRatio(ratio)
  } else {
    Modifier.height(maxSize).aspectRatio(ratio)
  }
  Box(
    stickerSize
      .layoutId(CHAT_IMAGE_LAYOUT_ID)
      .combinedClickable(
        onLongClick = { showMenu.value = true },
        onClick = {
          val bytes = data
          if (bytes != null) {
            ModalManager.fullscreen.showCustomModal(animated = false) { close ->
              BackHandler(onBack = close)
              ModalView(close, showAppBar = false, background = Color.Black) {
                Box(
                  Modifier.fillMaxSize().background(Color.Black).clickable(onClick = close).padding(24.dp),
                  contentAlignment = Alignment.Center
                ) {
                  AnimatedStickerImage(bytes, preview, Modifier.fillMaxSize())
                }
              }
            }
          } else if (file != null && (file.fileStatus is CIFileStatus.RcvInvitation || file.fileStatus is CIFileStatus.RcvAborted)) {
            if (file.fileSize <= MAX_STICKER_FILE_SIZE) {
              receiveFileIfValidSize(file, receiveFile, maxFileSize = MAX_STICKER_FILE_SIZE)
            } else {
              AlertManager.shared.showAlertMsg(
                generalGetString(MR.strings.large_file),
                generalGetString(MR.strings.sticker_too_large)
              )
            }
          }
        }
      ),
    contentAlignment = Alignment.Center
  ) {
    val bytes = data
    if (bytes != null) {
      AnimatedStickerImage(bytes, preview, Modifier.matchParentSize())
    } else {
      androidx.compose.foundation.Image(
        bitmap = preview,
        contentDescription = generalGetString(MR.strings.sticker),
        modifier = Modifier.matchParentSize()
      )
      when {
        file?.fileStatus is CIFileStatus.RcvAccepted || file?.fileStatus is CIFileStatus.RcvTransfer ->
          CircularProgressIndicator(Modifier.sizeIn(maxWidth = 24.dp, maxHeight = 24.dp), color = MaterialTheme.colors.primary, strokeWidth = 2.dp)
        file?.fileStatus is CIFileStatus.RcvInvitation || file?.fileStatus is CIFileStatus.RcvAborted ->
          Box(Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Icon(
              painterResource(MR.images.ic_arrow_downward),
              generalGetString(MR.strings.download_file),
              Modifier.size(22.dp),
              tint = Color.White
            )
          }
      }
    }
  }
}

@Composable
expect fun AnimatedStickerImage(data: ByteArray, fallback: ImageBitmap, modifier: Modifier)
