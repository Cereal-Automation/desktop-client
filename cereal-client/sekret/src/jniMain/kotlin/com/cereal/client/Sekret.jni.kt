package com.cereal.client

import dev.datlag.sekret.EncryptedSecret
import dev.datlag.sekret.SekretConfig
import kotlin.jvm.JvmStatic

public actual object Sekret {
  @JvmStatic
  public actual fun databaseEncryptionKey(key: String, config: SekretConfig.Builder.() -> Unit): String? = databaseEncryptionKey(key, SekretConfig.Builder().apply(config).build())

  @JvmStatic
  public actual fun databaseEncryptionKey(key: String, config: SekretConfig): String? = if (config.jni.decryptDirectly) {
    databaseEncryptionKeyDecrypted(key)
  } else {
    databaseEncryptionKeyEncrypted()?.decrypt(key) ?:
    if (config.jni.fallbackToDirectDecryption) {
      databaseEncryptionKeyDecrypted(key)
    } else {
      null
    }
  }

  @JvmStatic
  public fun databaseEncryptionKey(key: String): String? = databaseEncryptionKey(key, SekretConfig())

  @JvmStatic
  private external fun databaseEncryptionKeyDecrypted(key: String): String?

  @JvmStatic
  private external fun _databaseEncryptionKeyEncrypted(): IntArray?

  @JvmStatic
  private fun databaseEncryptionKeyEncrypted(): EncryptedSecret? = _databaseEncryptionKeyEncrypted()?.let(EncryptedSecret::invoke)

  @JvmStatic
  public actual fun fileEncryptionKey(key: String, config: SekretConfig.Builder.() -> Unit): String? = fileEncryptionKey(key, SekretConfig.Builder().apply(config).build())

  @JvmStatic
  public actual fun fileEncryptionKey(key: String, config: SekretConfig): String? = if (config.jni.decryptDirectly) {
    fileEncryptionKeyDecrypted(key)
  } else {
    fileEncryptionKeyEncrypted()?.decrypt(key) ?:
    if (config.jni.fallbackToDirectDecryption) {
      fileEncryptionKeyDecrypted(key)
    } else {
      null
    }
  }

  @JvmStatic
  public fun fileEncryptionKey(key: String): String? = fileEncryptionKey(key, SekretConfig())

  @JvmStatic
  private external fun fileEncryptionKeyDecrypted(key: String): String?

  @JvmStatic
  private external fun _fileEncryptionKeyEncrypted(): IntArray?

  @JvmStatic
  private fun fileEncryptionKeyEncrypted(): EncryptedSecret? = _fileEncryptionKeyEncrypted()?.let(EncryptedSecret::invoke)
}
