package chat.simplex.common.stickers

import chat.simplex.common.model.jsonShort
import chat.simplex.common.model.readCryptoFile
import chat.simplex.common.model.writeCryptoFile
import chat.simplex.common.platform.Log
import chat.simplex.common.platform.TAG
import chat.simplex.common.platform.cryptor
import chat.simplex.common.platform.filesDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val STICKER_MANIFEST_MAGIC = 0x47485331 // GHS1
private const val MAX_UNREFERENCED_CACHE_BYTES = 256L * 1024L * 1024L

fun sha256Hex(bytes: ByteArray): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
  val chars = CharArray(digest.size * 2)
  val alphabet = "0123456789abcdef"
  digest.forEachIndexed { index, byte ->
    val value = byte.toInt() and 0xff
    chars[index * 2] = alphabet[value ushr 4]
    chars[index * 2 + 1] = alphabet[value and 0x0f]
  }
  return chars.concatToString()
}

object StickerRepository {
  private class Holder {
    val mutex = Mutex()
    val state = MutableStateFlow(StickerLibraryState())
  }

  private val holders = ConcurrentHashMap<StickerOwner, Holder>()
  private val rootDir: File
    get() = File(filesDir, "assets/stickers")

  fun state(owner: StickerOwner): StateFlow<StickerLibraryState> = holder(owner).state

  suspend fun load(owner: StickerOwner): StickerLibraryState = withContext(Dispatchers.IO) {
    val holder = holder(owner)
    holder.mutex.withLock {
      if (!holder.state.value.loaded) loadLocked(owner, holder)
      holder.state.value
    }
  }

  suspend fun createPack(owner: StickerOwner, name: String, initialStickerHash: String? = null): StickerPack = mutate(owner) { library ->
    val trimmedName = name.trim().take(40)
    require(trimmedName.isNotEmpty()) { "Sticker pack name is empty" }
    if (initialStickerHash != null) require(library.assets.containsKey(initialStickerHash)) { "Sticker asset not found" }
    val pack = StickerPack(
      id = UUID.randomUUID().toString(),
      name = trimmedName,
      stickerHashes = listOfNotNull(initialStickerHash)
    )
    library.copy(packs = library.packs + pack) to pack
  }

  suspend fun renamePack(owner: StickerOwner, packId: String, name: String) {
    mutate(owner) { library ->
      val trimmedName = name.trim().take(40)
      require(trimmedName.isNotEmpty()) { "Sticker pack name is empty" }
      val index = library.packs.indexOfFirst { it.id == packId }
      require(index >= 0) { "Sticker pack not found" }
      val packs = library.packs.toMutableList().also { it[index] = it[index].copy(name = trimmedName) }
      library.copy(packs = packs) to Unit
    }
  }

  suspend fun deletePacks(owner: StickerOwner, packIds: Set<String>) {
    if (packIds.isEmpty()) return
    mutate(owner) { library ->
      library.copy(packs = library.packs.filterNot { it.id in packIds }) to Unit
    }
  }

  suspend fun setPackOrder(owner: StickerOwner, packIds: List<String>) {
    mutate(owner) { library ->
      library.copy(packs = library.packs.orderedByIds(packIds)) to Unit
    }
  }

  suspend fun addSticker(owner: StickerOwner, packId: String, sticker: ProcessedSticker): StickerAddResult =
    mutate(owner) { library ->
      val packIndex = library.packs.indexOfFirst { it.id == packId }
      require(packIndex >= 0) { "Sticker pack not found" }
      val existing = library.assets[sticker.sha256]
      val asset = existing ?: storeAsset(owner, sticker)
      val pack = library.packs[packIndex]
      val added = sticker.sha256 !in pack.stickerHashes
      val packs = if (added) {
        library.packs.toMutableList().also {
          it[packIndex] = pack.copy(stickerHashes = pack.stickerHashes + sticker.sha256)
        }
      } else {
        library.packs
      }
      val assets = library.assets + (asset.sha256 to asset.copy(lastUsedAt = System.currentTimeMillis()))
      library.copy(packs = packs, assets = assets) to StickerAddResult(asset, added)
    }

  suspend fun cacheReceived(owner: StickerOwner, sticker: ProcessedSticker): StickerAsset = mutate(owner) { library ->
    val existing = library.assets[sticker.sha256]
    val asset = existing ?: storeAsset(owner, sticker)
    val touched = asset.copy(lastUsedAt = System.currentTimeMillis())
    library.copy(assets = library.assets + (touched.sha256 to touched)) to touched
  }

  suspend fun removeStickers(owner: StickerOwner, packId: String, hashes: Set<String>) {
    if (hashes.isEmpty()) return
    mutate(owner) { library ->
      val index = library.packs.indexOfFirst { it.id == packId }
      require(index >= 0) { "Sticker pack not found" }
      val pack = library.packs[index]
      val updated = pack.copy(
        stickerHashes = pack.stickerHashes.filterNot(hashes::contains),
        coverHash = pack.coverHash?.takeUnless(hashes::contains)
      )
      val packs = library.packs.toMutableList().also { it[index] = updated }
      library.copy(packs = packs) to Unit
    }
  }

  suspend fun addExistingStickerToPack(owner: StickerOwner, hash: String, packId: String): Boolean =
    mutate(owner) { library ->
      require(library.assets.containsKey(hash)) { "Sticker asset not found" }
      val index = library.packs.indexOfFirst { it.id == packId }
      require(index >= 0) { "Sticker pack not found" }
      val pack = library.packs[index]
      val added = hash !in pack.stickerHashes
      val updated = if (added) pack.copy(stickerHashes = pack.stickerHashes + hash) else pack
      val packs = library.packs.toMutableList().also { it[index] = updated }
      library.copy(packs = packs) to added
    }

  suspend fun setStickerOrder(owner: StickerOwner, packId: String, hashes: List<String>) {
    mutate(owner) { library ->
      val index = library.packs.indexOfFirst { it.id == packId }
      require(index >= 0) { "Sticker pack not found" }
      val pack = library.packs[index]
      require(hashes.size == hashes.toSet().size && hashes.toSet() == pack.stickerHashes.toSet()) { "Invalid sticker order" }
      val packs = library.packs.toMutableList().also { it[index] = pack.copy(stickerHashes = hashes) }
      library.copy(packs = packs) to Unit
    }
  }

  suspend fun verifiedBytes(owner: StickerOwner, hash: String): ByteArray? = withContext(Dispatchers.IO) {
    if (!isValidSha256(hash)) return@withContext null
    val holder = holder(owner)
    val library = holder.mutex.withLock {
      if (!holder.state.value.loaded) loadLocked(owner, holder)
      holder.state.value.library
    }
    val asset = library.assets[hash] ?: return@withContext null
    val file = assetFile(asset)
    if (!file.isFile) return@withContext null
    runCatching { readCryptoFile(file.absolutePath, asset.cryptoArgs) }
      .onFailure { Log.e(TAG, "Unable to read cached sticker $hash: ${it.stackTraceToString()}") }
      .getOrNull()
      ?.takeIf { sha256Hex(it) == hash }
  }

  suspend fun matchingBytes(
    owner: StickerOwner,
    hash: String,
    mime: String,
    animated: Boolean,
    width: Int,
    height: Int
  ): ByteArray? {
    val state = load(owner)
    val asset = state.library.assets[hash] ?: return null
    if (asset.mime != mime || asset.animated != animated || asset.width != width || asset.height != height) return null
    return verifiedBytes(owner, hash)
  }

  suspend fun clear(owner: StickerOwner) = withContext(Dispatchers.IO) {
    val holder = holder(owner)
    holder.mutex.withLock {
      val ownerStorageKey = storageKey(owner)
      rootDir.listFiles()?.filter { it.name.startsWith("asset-$ownerStorageKey-") }?.forEach(File::delete)
      manifestFile(owner).delete()
      cryptor.deleteKey(alias(owner))
      holder.state.value = StickerLibraryState(loaded = true)
    }
  }

  private fun holder(owner: StickerOwner): Holder = holders.computeIfAbsent(owner) { Holder() }

  private fun loadLocked(owner: StickerOwner, holder: Holder): StickerLibraryState {
    val state = try {
      StickerLibraryState(library = readManifest(owner), loaded = true)
    } catch (e: Throwable) {
      Log.e(TAG, "Unable to load sticker library: ${e.stackTraceToString()}")
      StickerLibraryState(loaded = true, error = e.message ?: e::class.simpleName)
    }
    holder.state.value = state
    return state
  }

  private suspend fun <T> mutate(
    owner: StickerOwner,
    transform: (StickerLibrary) -> Pair<StickerLibrary, T>
  ): T = withContext(Dispatchers.IO) {
    val holder = holder(owner)
    holder.mutex.withLock {
      val current = if (holder.state.value.loaded) holder.state.value else loadLocked(owner, holder)
      check(current.error == null) { current.error ?: "Sticker library unavailable" }
      val (updated, result) = transform(current.library)
      val trimmed = trimCache(updated)
      writeManifest(owner, trimmed)
      holder.state.value = StickerLibraryState(library = trimmed, loaded = true)
      result
    }
  }

  private fun storeAsset(owner: StickerOwner, sticker: ProcessedSticker): StickerAsset {
    require(isValidSha256(sticker.sha256) && sha256Hex(sticker.bytes) == sticker.sha256) { "Invalid sticker hash" }
    require(detectStickerMime(sticker.bytes) == sticker.mime) { "Invalid sticker MIME type" }
    require(sticker.width in 1..MAX_STICKER_DIMENSION && sticker.height in 1..MAX_STICKER_DIMENSION) { "Invalid sticker dimensions" }
    require(isValidStickerPreview(sticker.preview)) { "Invalid sticker preview" }
    require(sticker.bytes.size.toLong() <= if (sticker.animated) MAX_STICKER_FILE_SIZE else MAX_STATIC_STICKER_SIZE) { "Sticker exceeds size limit" }
    val fileName = "asset-${storageKey(owner)}-${sticker.sha256}.bin"
    val file = File(rootDir, fileName)
    rootDir.mkdirs()
    file.delete()
    val cryptoArgs = try {
      writeCryptoFile(file.absolutePath, sticker.bytes)
    } catch (e: Throwable) {
      file.delete()
      throw e
    }
    return StickerAsset(
      sha256 = sticker.sha256,
      mime = sticker.mime,
      animated = sticker.animated,
      width = sticker.width,
      height = sticker.height,
      byteSize = sticker.bytes.size.toLong(),
      encryptedFileName = fileName,
      cryptoArgs = cryptoArgs,
      preview = sticker.preview,
      lastUsedAt = System.currentTimeMillis()
    )
  }

  private fun trimCache(library: StickerLibrary): StickerLibrary {
    val referenced = library.packs.flatMapTo(mutableSetOf()) { it.stickerHashes }
    var cacheBytes = library.assets.values.filterNot { it.sha256 in referenced }.sumOf { it.byteSize }
    if (cacheBytes <= MAX_UNREFERENCED_CACHE_BYTES) return library
    val assets = library.assets.toMutableMap()
    library.assets.values
      .asSequence()
      .filterNot { it.sha256 in referenced }
      .sortedBy { it.lastUsedAt }
      .forEach { asset ->
        if (cacheBytes <= MAX_UNREFERENCED_CACHE_BYTES) return@forEach
        if (assetFile(asset).delete() || !assetFile(asset).exists()) {
          assets.remove(asset.sha256)
          cacheBytes -= asset.byteSize
        }
      }
    return library.copy(assets = assets)
  }

  private fun readManifest(owner: StickerOwner): StickerLibrary {
    val file = manifestFile(owner)
    if (!file.exists()) return StickerLibrary()
    DataInputStream(file.inputStream().buffered()).use { input ->
      require(input.readInt() == STICKER_MANIFEST_MAGIC) { "Invalid sticker library header" }
      val ivLength = input.readInt()
      require(ivLength in 0..64) { "Invalid sticker library IV" }
      val iv = ByteArray(ivLength).also(input::readFully)
      val encrypted = input.readBytes()
      val plain = cryptor.decryptBytes(encrypted, iv, alias(owner))
        ?: error("Sticker library key is unavailable")
      return jsonShort.decodeFromString(StickerLibrary.serializer(), plain.toString(Charsets.UTF_8))
        .also { require(it.schemaVersion == STICKER_SCHEMA_VERSION) { "Unsupported sticker library version" } }
    }
  }

  private fun writeManifest(owner: StickerOwner, library: StickerLibrary) {
    rootDir.mkdirs()
    val plain = jsonShort.encodeToString(StickerLibrary.serializer(), library).toByteArray(Charsets.UTF_8)
    val (encrypted, iv) = cryptor.encryptBytes(plain, alias(owner))
    val file = manifestFile(owner)
    val temp = File(rootDir, "${file.name}.${UUID.randomUUID()}.tmp")
    try {
      DataOutputStream(temp.outputStream().buffered()).use { output ->
        output.writeInt(STICKER_MANIFEST_MAGIC)
        output.writeInt(iv.size)
        output.write(iv)
        output.write(encrypted)
      }
      try {
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
      }
    } finally {
      temp.delete()
    }
  }

  private fun manifestFile(owner: StickerOwner): File = File(rootDir, "library-${storageKey(owner)}.bin")
  private fun assetFile(asset: StickerAsset): File = File(rootDir, asset.encryptedFileName)
  private fun alias(owner: StickerOwner): String = "grayheterotopia.stickers.${storageKey(owner)}"

  private fun storageKey(owner: StickerOwner): String =
    sha256Hex("${owner.remoteHostId ?: "local"}:${owner.userId}".toByteArray(Charsets.UTF_8)).take(24)

  private fun isValidSha256(hash: String): Boolean =
    hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }
}

internal fun List<StickerPack>.orderedByIds(packIds: List<String>): List<StickerPack> {
  if (packIds.size != size || packIds.toSet().size != size) return this
  val packsById = associateBy(StickerPack::id)
  if (packIds.any { it !in packsById }) return this
  return packIds.map(packsById::getValue)
}
