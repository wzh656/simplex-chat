package chat.simplex.common.platform

interface CryptorInterface {
  fun decryptBytes(data: ByteArray, iv: ByteArray, alias: String): ByteArray?
  fun encryptBytes(data: ByteArray, alias: String): Pair<ByteArray, ByteArray>
  fun decryptData(data: ByteArray, iv: ByteArray, alias: String): String? =
    decryptBytes(data, iv, alias)?.toString(Charsets.UTF_8)
  fun encryptText(text: String, alias: String): Pair<ByteArray, ByteArray> =
    encryptBytes(text.toByteArray(Charsets.UTF_8), alias)
  fun deleteKey(alias: String)
}

expect val cryptor: CryptorInterface
