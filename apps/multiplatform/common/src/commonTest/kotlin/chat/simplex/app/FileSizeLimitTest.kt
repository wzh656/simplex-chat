package chat.simplex.app

import chat.simplex.common.model.CIFile
import chat.simplex.common.model.CIFileStatus
import chat.simplex.common.model.FileProtocol
import chat.simplex.common.views.chat.fileIdsWithinSizeLimit
import chat.simplex.common.views.chat.item.fileSizeValid
import chat.simplex.common.views.helpers.MAX_FILE_SIZE_XFTP
import chat.simplex.common.views.helpers.getMaxFileSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSizeLimitTest {
  @Test
  fun testXftpLimitIs50MiB() {
    assertEquals(50L * 1024 * 1024, MAX_FILE_SIZE_XFTP)
    assertEquals(MAX_FILE_SIZE_XFTP, getMaxFileSize(FileProtocol.XFTP))
  }

  @Test
  fun testReceivedXftpFileBoundary() {
    assertTrue(fileSizeValid(receivedFile(1, MAX_FILE_SIZE_XFTP)))
    assertFalse(fileSizeValid(receivedFile(2, MAX_FILE_SIZE_XFTP + 1)))
  }

  @Test
  fun testBatchReceiveExcludesOversizedAndUnknownFiles() {
    val files = listOf(
      receivedFile(1, MAX_FILE_SIZE_XFTP),
      receivedFile(2, MAX_FILE_SIZE_XFTP + 1)
    )

    assertEquals(listOf(1L), fileIdsWithinSizeLimit(listOf(1L, 2L, 3L), files))
  }

  private fun receivedFile(fileId: Long, fileSize: Long) = CIFile(
    fileId = fileId,
    fileName = "attachment.bin",
    fileSize = fileSize,
    fileStatus = CIFileStatus.RcvInvitation,
    fileProtocol = FileProtocol.XFTP
  )
}
