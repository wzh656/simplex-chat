package chat.simplex.common.platform

import com.sun.jna.platform.win32.Crypt32Util

actual val cryptor: CryptorInterface = object : CryptorInterface {
  override fun decryptBytes(data: ByteArray, iv: ByteArray, alias: String): ByteArray? =
    runCatching { Crypt32Util.cryptUnprotectData(data) }
      .onFailure { Log.e(TAG, "DPAPI decrypt failed for $alias: ${it.stackTraceToString()}") }
      .getOrNull()

  override fun encryptBytes(data: ByteArray, alias: String): Pair<ByteArray, ByteArray> =
    Crypt32Util.cryptProtectData(data) to ByteArray(0)

  override fun deleteKey(alias: String) = Unit
}
