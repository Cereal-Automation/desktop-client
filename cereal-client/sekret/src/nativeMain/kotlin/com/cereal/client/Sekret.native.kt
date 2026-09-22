package com.cereal.client

import dev.datlag.sekret.SekretConfig
import dev.datlag.sekret.SekretHelper.getNativeValue

public actual object Sekret {
  public actual fun databaseEncryptionKey(key: String, config: SekretConfig.Builder.() -> Unit): String? = databaseEncryptionKey(key, SekretConfig.Builder().apply(config).build())

  public actual fun databaseEncryptionKey(key: String, config: SekretConfig): String? {
    val obfuscatedSecret = intArrayOf(0x54, 0x7, 0x5a, 0x10, 0x5e)
    return getNativeValue(obfuscatedSecret, key)
  }

  public actual fun fileEncryptionKey(key: String, config: SekretConfig.Builder.() -> Unit): String? = fileEncryptionKey(key, SekretConfig.Builder().apply(config).build())

  public actual fun fileEncryptionKey(key: String, config: SekretConfig): String? {
    val obfuscatedSecret = intArrayOf(0x54, 0x7, 0x5a, 0x10, 0x5e)
    return getNativeValue(obfuscatedSecret, key)
  }
}
