package chat.simplex.common.views.chat

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material3.IconButton as Material3IconButton
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.material3.OutlinedButton as Material3OutlinedButton
import androidx.compose.material3.OutlinedTextField as Material3OutlinedTextField
import androidx.compose.material3.Surface as Material3Surface
import androidx.compose.material3.TextButton as Material3TextButton
import androidx.compose.material3.Text as Material3Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.platform.Log
import chat.simplex.common.platform.TAG
import chat.simplex.common.platform.onRightClick
import chat.simplex.common.platform.rememberFileChooserMultipleLauncher
import chat.simplex.common.platform.showToast
import chat.simplex.common.stickers.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun StickerPackManagementView(owner: StickerOwner, close: () -> Unit) {
  val scope = rememberCoroutineScope()
  val state by StickerRepository.state(owner).collectAsState()
  val selected = remember(owner) { mutableStateListOf<String>() }
  val packOrder = remember(owner) { mutableStateListOf<StickerPack>() }
  var selectMode by rememberSaveable(owner) { mutableStateOf(false) }
  val listState = rememberLazyListState()
  val repositoryPacks = state.library.packs

  fun closeSelection() {
    selectMode = false
    selected.clear()
  }

  val dragState = rememberDragDropState(
    lazyListState = listState,
    canDrag = { it in packOrder.indices },
    onDragFinished = { cancelled ->
      if (cancelled) {
        packOrder.replaceWith(repositoryPacks)
      } else {
        scope.launchStickerOperation(onFailure = { packOrder.replaceWith(repositoryPacks) }) {
          StickerRepository.setPackOrder(owner, packOrder.map(StickerPack::id))
        }
      }
    }
  ) { from, to ->
    if (from in packOrder.indices && to in packOrder.indices) {
      packOrder.add(to, packOrder.removeAt(from))
    }
  }

  LaunchedEffect(owner) { StickerRepository.load(owner) }
  LaunchedEffect(repositoryPacks) {
    if (dragState.draggingItemIndex == null) packOrder.replaceWith(repositoryPacks)
    selected.retainAll(repositoryPacks.mapTo(mutableSetOf(), StickerPack::id))
  }
  BackHandler {
    if (selectMode) closeSelection() else close()
  }

  Column(Modifier.fillMaxSize().background(Material3Theme.colorScheme.background)) {
    ManagementHeader(
      title = stringResource(MR.strings.sticker_packs),
      onNavigateBack = close,
      selectionCount = selected.size,
      selectionMode = selectMode,
      onToggleSelection = {
        selectMode = true
        selected.clear()
      },
      onCloseSelection = ::closeSelection,
      onDelete = {
        val ids = selected.toSet()
        AlertManager.shared.showAlertDialog(
          title = generalGetString(MR.strings.delete_sticker_packs_question).format(ids.size),
          confirmText = generalGetString(MR.strings.delete_verb),
          destructive = true,
          onConfirm = {
            scope.launchStickerOperation(
              successMessage = { generalGetString(MR.strings.sticker_packs_deleted).format(ids.size) },
              onSuccess = { closeSelection() }
            ) { StickerRepository.deletePacks(owner, ids) }
          }
        )
      },
      selectionAvailable = packOrder.isNotEmpty()
    )
    when {
      state.error != null -> ManagementMessage(stringResource(MR.strings.sticker_library_unavailable), error = true)
      !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      else -> LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        itemsIndexed(packOrder, key = { _, pack -> pack.id }) { index, pack ->
          DraggableItem(dragState, index) { dragging ->
            val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp)
            StickerPackRow(
              owner = owner,
              pack = pack,
              cover = pack.effectiveCoverHash?.let(state.library.assets::get),
              selected = pack.id in selected,
              selectMode = selectMode,
              dragging = dragging,
              elevation = elevation,
              dragModifier = Modifier.dragHandle(dragState, index),
              onClick = {
                if (selectMode) selected.toggle(pack.id) else openStickerPackEditor(owner, pack.id)
              },
              onLongClick = {
                if (selectMode) {
                  selected.toggle(pack.id)
                } else {
                  showStickerPackNameDialog(generalGetString(MR.strings.rename_sticker_pack), pack.name) { name ->
                    scope.launchStickerOperation(
                      successMessage = { generalGetString(MR.strings.sticker_pack_renamed) }
                    ) { StickerRepository.renamePack(owner, pack.id, name) }
                  }
                }
              }
            )
          }
        }
        if (!selectMode) {
          item(key = "create-sticker-pack") {
            CreateStickerPackRow {
              showStickerPackNameDialog(generalGetString(MR.strings.create_sticker_pack), "") { name ->
                scope.launchStickerOperation(
                  successMessage = { generalGetString(MR.strings.sticker_pack_created) }
                ) { StickerRepository.createPack(owner, name) }
              }
            }
          }
        }
      }
    }
  }
}

private fun openStickerPackEditor(owner: StickerOwner, packId: String) {
  ModalManager.end.showCustomModal { close ->
    ModalView(close, showAppBar = false, cardScreen = false) {
      StickerPackEditorView(owner, packId, close)
    }
  }
}

@Composable
private fun StickerPackRow(
  owner: StickerOwner,
  pack: StickerPack,
  cover: StickerAsset?,
  selected: Boolean,
  selectMode: Boolean,
  dragging: Boolean,
  elevation: Dp,
  dragModifier: Modifier,
  onClick: () -> Unit,
  onLongClick: () -> Unit
) {
  val containerColor = when {
    dragging -> Material3Theme.colorScheme.surfaceContainerHigh
    selected -> Material3Theme.colorScheme.primaryContainer
    else -> Material3Theme.colorScheme.surface
  }
  Material3Surface(
    modifier = Modifier.fillMaxWidth().clip(Material3Theme.shapes.large),
    shape = Material3Theme.shapes.large,
    color = containerColor,
    tonalElevation = if (selected) 2.dp else 0.dp,
    shadowElevation = elevation
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .onRightClick(onLongClick)
        .padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      StickerPackCover(owner, pack.name, cover, Modifier.size(56.dp))
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Material3Text(
          pack.name,
          style = Material3Theme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Material3Text(
          stringResource(MR.strings.sticker_count).format(pack.stickerHashes.size),
          style = Material3Theme.typography.bodyMedium,
          color = Material3Theme.colorScheme.onSurfaceVariant
        )
      }
      if (selectMode) {
        SelectionCircle(selected, Modifier.padding(10.dp))
      } else {
        Box(
          dragModifier.size(width = 44.dp, height = 48.dp),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            painterResource(MR.images.ic_drag_handle),
            stringResource(MR.strings.reorder),
            tint = Material3Theme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
private fun StickerPackEditorView(owner: StickerOwner, packId: String, close: () -> Unit) {
  val scope = rememberCoroutineScope()
  val state by StickerRepository.state(owner).collectAsState()
  val selected = remember(packId) { mutableStateListOf<String>() }
  var selectMode by rememberSaveable(packId) { mutableStateOf(false) }
  var importing by remember { mutableStateOf(false) }
  val pack = state.library.packs.firstOrNull { it.id == packId }

  fun closeSelection() {
    selectMode = false
    selected.clear()
  }

  LaunchedEffect(owner) { StickerRepository.load(owner) }
  LaunchedEffect(pack?.stickerHashes) {
    selected.retainAll(pack?.stickerHashes?.toSet().orEmpty())
  }
  BackHandler {
    if (selectMode) closeSelection() else close()
  }

  val imagePicker = rememberFileChooserMultipleLauncher { uris ->
    scope.launch {
      if (uris.isEmpty()) return@launch
      importing = true
      val errors = mutableMapOf<StickerProcessingError, Int>()
      var added = 0
      var unchanged = 0
      try {
        uris.forEach { uri ->
          when (val result = processSticker(uri)) {
            is StickerProcessingResult.Success -> {
              if (StickerRepository.addSticker(owner, packId, result.sticker).addedToPack) added++ else unchanged++
            }
            is StickerProcessingResult.Error -> errors[result.reason] = (errors[result.reason] ?: 0) + 1
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        showStickerOperationFailure(e)
        return@launch
      } finally {
        importing = false
      }
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

  Column(Modifier.fillMaxSize().background(Material3Theme.colorScheme.background)) {
    ManagementHeader(
      title = pack?.name ?: stringResource(MR.strings.sticker_packs),
      onNavigateBack = close,
      selectionCount = selected.size,
      selectionMode = selectMode,
      onToggleSelection = {
        selectMode = true
        selected.clear()
      },
      onCloseSelection = ::closeSelection,
      onDelete = {
        val hashes = selected.toSet()
        AlertManager.shared.showAlertDialog(
          title = generalGetString(MR.strings.delete_stickers_question).format(hashes.size),
          confirmText = generalGetString(MR.strings.delete_verb),
          destructive = true,
          onConfirm = {
            scope.launchStickerOperation(
              successMessage = { generalGetString(MR.strings.stickers_deleted).format(hashes.size) },
              onSuccess = { closeSelection() }
            ) { StickerRepository.removeStickers(owner, packId, hashes) }
          }
        )
      },
      selectionAvailable = pack?.stickerHashes?.isNotEmpty() == true
    )
    if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
    when {
      state.error != null -> ManagementMessage(stringResource(MR.strings.sticker_library_unavailable), error = true)
      !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      pack == null -> LaunchedEffect(packId) { close() }
      else -> StickerEditorGrid(
        owner = owner,
        pack = pack,
        assets = state.library.assets,
        selectMode = selectMode,
        selected = selected,
        importing = importing,
        onPersistOrder = { hashes, onFailure ->
          scope.launchStickerOperation(onFailure = onFailure) {
            StickerRepository.setStickerOrder(owner, packId, hashes)
          }
        },
        onAdd = { scope.launchStickerOperation { imagePicker.launch("image/*") } }
      )
    }
  }
}

@Composable
private fun StickerEditorGrid(
  owner: StickerOwner,
  pack: StickerPack,
  assets: Map<String, StickerAsset>,
  selectMode: Boolean,
  selected: MutableList<String>,
  importing: Boolean,
  onPersistOrder: (List<String>, onFailure: () -> Unit) -> Unit,
  onAdd: () -> Unit
) {
  val gridState = rememberLazyGridState()
  val order = remember(pack.id, pack.stickerHashes) {
    mutableStateListOf<String>().apply { addAll(pack.stickerHashes) }
  }
  var draggingIndex by remember(pack.id, pack.stickerHashes) { mutableStateOf<Int?>(null) }
  var dragPosition by remember { mutableStateOf(Offset.Zero) }

  fun itemIndexAt(position: Offset): Int? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
    position.x >= item.offset.x && position.x < item.offset.x + item.size.width &&
        position.y >= item.offset.y && position.y < item.offset.y + item.size.height
  }?.index?.takeIf { it in order.indices }

  val reorderModifier = if (selectMode) {
    Modifier
  } else {
    Modifier.pointerInput(order.size) {
      detectDragGesturesAfterLongPress(
        onDragStart = { position ->
          draggingIndex = itemIndexAt(position)
          dragPosition = position
        },
        onDrag = { change, amount ->
          change.consume()
          dragPosition += amount
          val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
          val to = itemIndexAt(dragPosition) ?: return@detectDragGesturesAfterLongPress
          if (from != to) {
            val item = order.removeAt(from)
            order.add(to, item)
            draggingIndex = to
          }
        },
        onDragCancel = {
          draggingIndex = null
          order.clear()
          order.addAll(pack.stickerHashes)
        },
        onDragEnd = {
          draggingIndex = null
          onPersistOrder(order.toList()) { order.replaceWith(pack.stickerHashes) }
        }
      )
    }
  }

  LazyVerticalGrid(
    columns = GridCells.Adaptive(72.dp),
    state = gridState,
    modifier = Modifier.fillMaxSize().navigationBarsPadding().then(reorderModifier),
    contentPadding = PaddingValues(12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    items(count = order.size, key = { order[it] }) { index ->
      val hash = order[index]
      val selectedItem = hash in selected
      val dragging = index == draggingIndex
      Box(
        Modifier
          .aspectRatio(1f)
          .clip(RoundedCornerShape(8.dp))
          .then(if (selectMode) Modifier.clickable { selected.toggle(hash) } else Modifier)
          .padding(2.dp),
        contentAlignment = Alignment.Center
      ) {
        assets[hash]?.let { asset ->
          StickerImage(owner, asset, Modifier.fillMaxSize().alpha(if (dragging) 0.72f else 1f))
        }
        if (selectMode) {
          SelectionCircle(
            selected = selectedItem,
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
            compact = true
          )
        }
      }
    }
    if (!selectMode) {
      item(key = "add-sticker-placeholder") {
        StickerAddPlaceholder(enabled = !importing, onClick = onAdd)
      }
    }
  }
}

@Composable
private fun ManagementHeader(
  title: String,
  selectionCount: Int,
  selectionMode: Boolean,
  onNavigateBack: () -> Unit,
  onToggleSelection: () -> Unit,
  onCloseSelection: () -> Unit,
  onDelete: () -> Unit,
  selectionAvailable: Boolean = true,
  trailingActions: @Composable RowScope.() -> Unit = {}
) {
  Material3Surface(
    color = Material3Theme.colorScheme.surface,
    shadowElevation = 2.dp
  ) {
    Row(
      Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Material3IconButton(onClick = if (selectionMode) onCloseSelection else onNavigateBack) {
        if (selectionMode) {
          Icon(
            painterResource(MR.images.ic_close),
            stringResource(MR.strings.cancel_verb),
            tint = Material3Theme.colorScheme.onSurface
          )
        } else {
          Icon(
            painterResource(MR.images.ic_arrow_back_ios_new),
            stringResource(MR.strings.back),
            tint = Material3Theme.colorScheme.onSurface
          )
        }
      }
      Material3Text(
        if (selectionMode) {
          if (selectionCount == 0) stringResource(MR.strings.selected_chat_items_nothing_selected)
          else stringResource(MR.strings.selected_chat_items_selected_n).format(selectionCount)
        } else title,
        Modifier.weight(1f).padding(horizontal = 8.dp),
        style = Material3Theme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      if (selectionMode) {
        Material3IconButton(onClick = onDelete, enabled = selectionCount > 0) {
          Icon(
            painterResource(MR.images.ic_delete),
            stringResource(MR.strings.delete_verb),
            tint = if (selectionCount > 0) Material3Theme.colorScheme.error else Material3Theme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
          )
        }
      } else {
        if (selectionAvailable) {
          Material3IconButton(onClick = onToggleSelection) {
            Icon(
              painterResource(MR.images.ic_checklist),
              stringResource(MR.strings.select_verb),
              tint = Material3Theme.colorScheme.onSurfaceVariant
            )
          }
        }
        trailingActions()
      }
    }
  }
}

@Composable
private fun ManagementMessage(text: String, error: Boolean) {
  Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Material3Text(
      text,
      style = Material3Theme.typography.bodyLarge,
      color = if (error) Material3Theme.colorScheme.error else Material3Theme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun CreateStickerPackRow(onClick: () -> Unit) {
  Material3OutlinedButton(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    shape = Material3Theme.shapes.medium,
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
  ) {
    Icon(
      painterResource(MR.images.ic_add),
      stringResource(MR.strings.create_sticker_pack),
      Modifier.size(22.dp)
    )
    Spacer(Modifier.width(10.dp))
    Material3Text(
      stringResource(MR.strings.create_sticker_pack),
      style = Material3Theme.typography.labelLarge
    )
  }
}

@Composable
private fun SelectionCircle(selected: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
  val size = if (compact) 22.dp else 24.dp
  val borderColor = if (selected) Material3Theme.colorScheme.primary else Material3Theme.colorScheme.outline
  Box(
    modifier
      .size(size)
      .clip(CircleShape)
      .background(if (selected) Material3Theme.colorScheme.primary else Material3Theme.colorScheme.surface.copy(alpha = 0.92f))
      .border(2.dp, borderColor, CircleShape),
    contentAlignment = Alignment.Center
  ) {
    if (selected) {
      Icon(
        painterResource(MR.images.ic_done_filled),
        null,
        Modifier.size(if (compact) 14.dp else 16.dp),
        tint = Material3Theme.colorScheme.onPrimary
      )
    }
  }
}

internal fun showStickerPackNameDialog(title: String, initialName: String, onConfirm: (String) -> Unit) {
  AlertManager.shared.showAlertDialogContent(
    title = { Material3Text(title, Modifier.padding(horizontal = 24.dp), style = Material3Theme.typography.titleLarge) },
    onDismissRequest = { AlertManager.shared.hideAlert() },
    content = { StickerPackNameDialogContent(initialName, onConfirm) }
  )
}

@Composable
private fun StickerPackNameDialogContent(initialName: String, onConfirm: (String) -> Unit) {
  val name = remember { mutableStateOf(initialName) }
  val trimmedName by remember { derivedStateOf { name.value.trim() } }
  AppDialogBody {
    Material3OutlinedTextField(
      value = name.value,
      onValueChange = { name.value = it.take(40) },
      singleLine = true,
      label = { Material3Text(stringResource(MR.strings.sticker_pack_name)) },
      modifier = Modifier.fillMaxWidth()
    )
  }
  AppDialogActions {
    Material3TextButton(onClick = { AlertManager.shared.hideAlert() }) {
      Material3Text(stringResource(MR.strings.cancel_verb))
    }
    Material3TextButton(
      onClick = {
        onConfirm(trimmedName)
        AlertManager.shared.hideAlert()
      },
      enabled = trimmedName.isNotEmpty()
    ) {
      Material3Text(stringResource(MR.strings.save_verb))
    }
  }
}

@Composable
fun AddStickerToPackView(owner: StickerOwner, hash: String, close: () -> Unit) {
  val scope = rememberCoroutineScope()
  val state by StickerRepository.state(owner).collectAsState()
  BackHandler(onBack = close)
  LaunchedEffect(owner) { StickerRepository.load(owner) }
  Column(Modifier.fillMaxSize().background(Material3Theme.colorScheme.background)) {
    ManagementHeader(
      title = stringResource(MR.strings.add_to_sticker_pack),
      selectionCount = 0,
      selectionMode = false,
      onNavigateBack = close,
      onToggleSelection = {},
      onCloseSelection = {},
      onDelete = {},
      selectionAvailable = false,
      trailingActions = {
        Material3IconButton(onClick = {
          showStickerPackNameDialog(generalGetString(MR.strings.create_sticker_pack), "") { name ->
            scope.launchStickerOperation(
              successMessage = { generalGetString(MR.strings.sticker_added_to_pack) },
              onSuccess = { close() }
            ) { StickerRepository.createPack(owner, name, initialStickerHash = hash) }
          }
        }) {
          Icon(painterResource(MR.images.ic_add), stringResource(MR.strings.create_sticker_pack))
        }
      }
    )
    LazyColumn(
      Modifier.fillMaxSize().navigationBarsPadding(),
      contentPadding = PaddingValues(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(state.library.packs.size) { index ->
        val pack = state.library.packs[index]
        Material3Surface(
          modifier = Modifier.fillMaxWidth().clip(Material3Theme.shapes.large).clickable {
            scope.launchStickerOperation(
              successMessage = { added ->
                generalGetString(if (added) MR.strings.sticker_added_to_pack else MR.strings.sticker_already_in_pack)
              },
              onSuccess = { close() }
            ) { StickerRepository.addExistingStickerToPack(owner, hash, pack.id) }
          },
          shape = Material3Theme.shapes.large,
          color = Material3Theme.colorScheme.surface
        ) {
          Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StickerPackCover(owner, pack.name, pack.effectiveCoverHash?.let(state.library.assets::get), Modifier.size(52.dp))
            Spacer(Modifier.width(14.dp))
            Material3Text(pack.name, style = Material3Theme.typography.titleMedium)
          }
        }
      }
    }
  }
}

private fun <T> MutableList<T>.toggle(value: T) {
  if (!remove(value)) add(value)
}

internal fun <T> CoroutineScope.launchStickerOperation(
  successMessage: (T) -> String? = { null },
  onSuccess: (T) -> Unit = {},
  onFailure: () -> Unit = {},
  operation: suspend () -> T
) {
  launch {
    try {
      val result = operation()
      onSuccess(result)
      successMessage(result)?.let(::showToast)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      onFailure()
      showStickerOperationFailure(e)
    }
  }
}

private fun showStickerOperationFailure(error: Throwable) {
  Log.e(TAG, "Sticker operation failed: ${error.stackTraceToString()}")
  AlertManager.shared.showAlertMsg(
    generalGetString(MR.strings.sticker_operation_failed),
    error.message ?: generalGetString(MR.strings.error)
  )
}

private fun <T> MutableList<T>.replaceWith(values: Collection<T>) {
  clear()
  addAll(values)
}
