package chat.simplex.common.views.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.Base64AsyncImage
import chat.simplex.common.platform.appPlatform
import chat.simplex.common.platform.base64ToBitmap
import chat.simplex.common.platform.rememberFileChooserMultipleLauncher
import chat.simplex.common.platform.showToast
import chat.simplex.common.stickers.*
import chat.simplex.common.ui.theme.EmojiFont
import chat.simplex.common.views.chat.item.AnimatedStickerImage
import chat.simplex.common.views.helpers.AlertManager
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

@Composable
fun StickerPanel(
  owner: StickerOwner,
  onSend: (StickerAsset) -> Unit,
  onEmoji: (String) -> Unit,
  onManage: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val repositoryState by StickerRepository.state(owner).collectAsState()
  val packs = repositoryState.library.packs
  val assets = repositoryState.library.assets
  var selectedPackId by rememberSaveable(owner) { mutableStateOf<String?>(null) }
  var emojiMode by rememberSaveable(owner) { mutableStateOf(false) }
  var importing by remember { mutableStateOf(false) }

  LaunchedEffect(owner) {
    StickerRepository.load(owner)
  }
  LaunchedEffect(repositoryState.loaded, packs.isEmpty()) {
    if (repositoryState.loaded && packs.isEmpty() && repositoryState.error == null) {
      val pack = StickerRepository.createPack(owner, generalGetString(MR.strings.default_sticker_pack_name))
      selectedPackId = pack.id
    }
  }
  LaunchedEffect(packs, selectedPackId) {
    if (packs.none { it.id == selectedPackId }) selectedPackId = packs.firstOrNull()?.id
  }

  val selectedPack = packs.firstOrNull { it.id == selectedPackId }

  suspend fun addUris(uris: List<URI>, packId: String) {
    importing = true
    val errors = mutableMapOf<StickerProcessingError, Int>()
    var added = 0
    var unchanged = 0
    try {
      uris.forEach { uri ->
        when (val processed = processSticker(uri)) {
          is StickerProcessingResult.Success -> {
            if (StickerRepository.addSticker(owner, packId, processed.sticker).addedToPack) added++ else unchanged++
          }
          is StickerProcessingResult.Error -> errors[processed.reason] = (errors[processed.reason] ?: 0) + 1
        }
      }
    } finally {
      importing = false
    }
    withContext(Dispatchers.Main) {
      when {
        errors.isNotEmpty() -> AlertManager.shared.showAlertMsg(
          generalGetString(MR.strings.sticker_add_failed_title),
          buildString {
            append(generalGetString(MR.strings.sticker_add_result).format(added, errors.values.sum()))
            errors.forEach { (reason, count) -> append("\n$count × ${stickerProcessingErrorText(reason)}") }
          }
        )
        added > 0 -> showToast(generalGetString(MR.strings.stickers_added).format(added))
        unchanged > 0 -> showToast(generalGetString(MR.strings.sticker_already_in_pack))
      }
    }
  }

  val imagePicker = rememberFileChooserMultipleLauncher { uris ->
    selectedPackId?.let { packId -> scope.launchStickerOperation { addUris(uris, packId) } }
  }
  val panelHeight = if (appPlatform.isDesktop) 440.dp else 320.dp

  Surface(elevation = 6.dp) {
    Column(Modifier.fillMaxWidth().height(panelHeight).background(MaterialTheme.colors.background)) {
      Divider()
      Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
          repositoryState.error != null -> Text(
            stringResource(MR.strings.sticker_library_unavailable),
            Modifier.align(Alignment.Center).padding(24.dp),
            color = MaterialTheme.colors.error,
            textAlign = TextAlign.Center
          )
          !repositoryState.loaded || selectedPack == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
          emojiMode -> EmojiGrid(onEmoji)
          else -> StickerGrid(
            owner = owner,
            pack = selectedPack,
            assets = assets,
            importing = importing,
            onSend = onSend,
            onAdd = { scope.launchStickerOperation { imagePicker.launch("image/*") } }
          )
        }
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
      }
      Divider()
      Row(
        Modifier.fillMaxWidth().height(64.dp).background(MaterialTheme.colors.surface).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        PanelBarButton(
          selected = emojiMode,
          onClick = { emojiMode = true },
          contentDescription = stringResource(MR.strings.emoji)
        ) {
          Icon(painterResource(MR.images.ic_add_reaction), null, Modifier.size(28.dp))
        }
        LazyRow(
          Modifier.weight(1f).fillMaxHeight(),
          contentPadding = PaddingValues(horizontal = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          items(packs, key = { it.id }) { pack ->
            StickerPackButton(
              owner = owner,
              pack = pack,
              cover = pack.effectiveCoverHash?.let(assets::get),
              selected = !emojiMode && pack.id == selectedPackId,
              onClick = {
                emojiMode = false
                selectedPackId = pack.id
              }
            )
          }
        }
        PanelBarButton(
          onClick = {
            showStickerPackNameDialog(generalGetString(MR.strings.create_sticker_pack), "") { name: String ->
              scope.launchStickerOperation(
                successMessage = { generalGetString(MR.strings.sticker_pack_created) },
                onSuccess = { pack ->
                  emojiMode = false
                  selectedPackId = pack.id
                }
              ) { StickerRepository.createPack(owner, name) }
            }
          },
          contentDescription = stringResource(MR.strings.create_sticker_pack)
        ) {
          Icon(painterResource(MR.images.ic_add), null, Modifier.size(28.dp))
        }
        PanelBarButton(onClick = onManage, contentDescription = stringResource(MR.strings.manage_sticker_packs)) {
          Icon(painterResource(MR.images.ic_filter_list), null, Modifier.size(28.dp))
        }
      }
    }
  }
}

@Composable
private fun StickerGrid(
  owner: StickerOwner,
  pack: StickerPack,
  assets: Map<String, StickerAsset>,
  importing: Boolean,
  onSend: (StickerAsset) -> Unit,
  onAdd: () -> Unit
) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(72.dp),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    items(pack.stickerHashes, key = { it }) { hash ->
      assets[hash]?.let { asset -> StickerGridItem(owner, asset) { onSend(asset) } }
    }
    item(key = "add-sticker-placeholder") {
      StickerAddPlaceholder(enabled = !importing, onClick = onAdd)
    }
  }
}

@Composable
private fun EmojiGrid(onEmoji: (String) -> Unit) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(48.dp),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(10.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    items(PANEL_EMOJIS, key = { it }) { emoji ->
      Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { onEmoji(emoji) },
        contentAlignment = Alignment.Center
      ) {
        Text(emoji, fontFamily = EmojiFont, fontSize = 28.sp, lineHeight = 32.sp)
      }
    }
  }
}

@Composable
private fun StickerGridItem(owner: StickerOwner, asset: StickerAsset, onClick: () -> Unit) {
  Box(
    Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(2.dp),
    contentAlignment = Alignment.Center
  ) {
    StickerImage(owner, asset, Modifier.fillMaxSize())
  }
}

@Composable
internal fun StickerImage(owner: StickerOwner, asset: StickerAsset, modifier: Modifier = Modifier) {
  val stickerBytes by produceState<ByteArray?>(null, owner, asset.sha256) {
    value = StickerRepository.verifiedBytes(owner, asset.sha256)
  }
  val fallback = remember(asset.preview) { base64ToBitmap(asset.preview) }
  val bytes = stickerBytes
  if (bytes != null) {
    AnimatedStickerImage(bytes, fallback, modifier)
  } else {
    Base64AsyncImage(asset.preview, stringResource(MR.strings.sticker), ContentScale.Fit, modifier)
  }
}

@Composable
internal fun StickerAddPlaceholder(enabled: Boolean, onClick: () -> Unit) {
  Box(
    Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    val borderColor = MaterialTheme.colors.secondary.copy(alpha = 0.45f)
    Canvas(Modifier.matchParentSize().padding(2.dp)) {
      drawRoundRect(
        color = borderColor,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())))
      )
    }
    if (enabled) {
      Icon(painterResource(MR.images.ic_add), stringResource(MR.strings.add_stickers), Modifier.size(34.dp), tint = MaterialTheme.colors.secondary)
    } else {
      CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
    }
  }
}

@Composable
private fun StickerPackButton(owner: StickerOwner, pack: StickerPack, cover: StickerAsset?, selected: Boolean, onClick: () -> Unit) {
  Box(
    Modifier
      .size(48.dp)
      .clip(RoundedCornerShape(13.dp))
      .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.18f) else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(6.dp),
    contentAlignment = Alignment.Center
  ) {
    StickerPackCover(owner, pack.name, cover, Modifier.fillMaxSize())
  }
}

@Composable
internal fun StickerPackCover(owner: StickerOwner, name: String, cover: StickerAsset?, modifier: Modifier = Modifier) {
  Box(modifier, contentAlignment = Alignment.Center) {
    if (cover != null) {
      StickerImage(owner, cover, Modifier.fillMaxSize())
    } else {
      Icon(painterResource(MR.images.ic_add_reaction_filled), name, Modifier.size(28.dp), tint = MaterialTheme.colors.secondary)
    }
  }
}

@Composable
private fun PanelBarButton(
  selected: Boolean = false,
  onClick: () -> Unit,
  contentDescription: String,
  content: @Composable BoxScope.() -> Unit
) {
  Box(
    Modifier
      .size(48.dp)
      .clip(CircleShape)
      .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.16f) else Color.Transparent)
      .clickable(onClick = onClick)
      .semantics {
        this.contentDescription = contentDescription
        role = Role.Button
      },
    contentAlignment = Alignment.Center
  ) {
    CompositionLocalProvider(LocalContentColor provides if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.secondary) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
  }
}

internal fun stickerProcessingErrorText(error: StickerProcessingError): String = generalGetString(
  when (error) {
    StickerProcessingError.UnsupportedFormat -> MR.strings.sticker_error_unsupported_format
    StickerProcessingError.InvalidImage -> MR.strings.sticker_error_invalid_image
    StickerProcessingError.FileTooLarge -> MR.strings.sticker_error_file_too_large
    StickerProcessingError.DimensionsTooLarge -> MR.strings.sticker_error_dimensions_too_large
    StickerProcessingError.TooManyFrames -> MR.strings.sticker_error_too_many_frames
    StickerProcessingError.DurationTooLong -> MR.strings.sticker_error_duration_too_long
    StickerProcessingError.DecodeBudgetExceeded -> MR.strings.sticker_error_decode_budget
    StickerProcessingError.CompressionFailed -> MR.strings.sticker_error_compression_failed
  }
)

private val PANEL_EMOJIS = listOf(
  "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
  "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒",
  "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡",
  "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🫡", "🤭", "🤫", "🤥",
  "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
  "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "👏", "🙌", "🫶", "🙏", "💪", "❤️", "🧡", "💛", "💚",
  "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💯", "✨"
)
