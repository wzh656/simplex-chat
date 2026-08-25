package chat.simplex.common.stickers

import chat.simplex.common.model.MsgContent
import chat.simplex.common.model.jsonShort
import kotlinx.serialization.encodeToString
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StickerContractTest {
  @Test
  fun stickerMessageRoundTripsWithEmptyText() {
    val sticker = MsgContent.MCSticker(
      text = "",
      image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
      sha256 = "a".repeat(64),
      mime = "image/png",
      animated = false,
      width = 1,
      height = 1
    )

    val encoded = jsonShort.encodeToString<MsgContent>(sticker)
    assertTrue("\"type\":\"sticker\"" in encoded)
    assertTrue("\"text\":\"\"" in encoded)

    val decoded = assertIs<MsgContent.MCSticker>(jsonShort.decodeFromString<MsgContent>(encoded))
    assertEquals(sticker.text, decoded.text)
    assertEquals(sticker.image, decoded.image)
    assertEquals(sticker.sha256, decoded.sha256)
    assertEquals(sticker.mime, decoded.mime)
    assertEquals(sticker.animated, decoded.animated)
    assertEquals(sticker.width, decoded.width)
    assertEquals(sticker.height, decoded.height)
    assertEquals(1, decoded.version)
  }

  @Test
  fun invalidStickerMetadataFallsBackToUnknownContent() {
    val invalid = """{"type":"sticker","version":1,"text":"","image":"data:image/png;base64,aQ==","sha256":"ABC","mime":"image/png","animated":false,"width":1,"height":1}"""
    assertIs<MsgContent.MCUnknown>(jsonShort.decodeFromString<MsgContent>(invalid))
  }

  @Test
  fun contentHashUsesExactNormalizedBytes() {
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc".encodeToByteArray()))
  }

  @Test
  fun animatedLimitsAreInclusiveAtTwoMiB() {
    val valid = AnimatedStickerInfo("image/gif", 512, 512, 180, 15_000)
    assertNull(validateAnimatedSticker(valid, MAX_STICKER_FILE_SIZE.toInt()))
    assertEquals(StickerProcessingError.FileTooLarge, validateAnimatedSticker(valid, MAX_STICKER_FILE_SIZE.toInt() + 1))
    assertEquals(
      StickerProcessingError.DimensionsTooLarge,
      validateAnimatedSticker(valid.copy(width = 513), 100)
    )
    assertEquals(
      StickerProcessingError.TooManyFrames,
      validateAnimatedSticker(valid.copy(frameCount = 181), 100)
    )
    assertEquals(
      StickerProcessingError.DurationTooLong,
      validateAnimatedSticker(valid.copy(durationMs = 15_001), 100)
    )
  }

  @Test
  fun packCoverFallsBackWhenExplicitCoverIsRemoved() {
    val pack = StickerPack("pack", "Pack", listOf("first", "second"), coverHash = "removed")
    assertEquals("first", pack.effectiveCoverHash)
  }

  @Test
  fun packOrderUsesStableIdsAndIgnoresStaleOrders() {
    val first = StickerPack("first", "First")
    val second = StickerPack("second", "Second")
    val packs = listOf(first, second)

    assertEquals(listOf(second, first), packs.orderedByIds(listOf("second", "first")))
    assertEquals(packs, packs.orderedByIds(listOf("first")))
    assertEquals(packs, packs.orderedByIds(listOf("first", "missing")))
    assertEquals(packs, packs.orderedByIds(listOf("first", "first")))
  }

  @Test
  fun malformedAnimationIsNotAccepted() {
    assertNull(inspectAnimatedSticker("GIF89a".encodeToByteArray()))
    assertNull(inspectAnimatedSticker("RIFFinvalidWEBP".encodeToByteArray()))
  }

  @Test
  fun detectsSupportedImageSignatures() {
    val gif = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==")
    val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
    assertEquals("image/gif", detectStickerMime(gif))
    assertEquals("image/png", detectStickerMime(png))
    val gifInfo = inspectAnimatedSticker(gif)
    assertEquals(1, gifInfo?.width)
    assertEquals(1, gifInfo?.height)
    assertEquals(1, gifInfo?.frameCount)
    assertTrue(isValidStickerPreview("data:image/png;base64,${Base64.getEncoder().encodeToString(png)}"))
    assertTrue(!isValidStickerPreview("data:image/png;base64,not-base64"))
  }
}
