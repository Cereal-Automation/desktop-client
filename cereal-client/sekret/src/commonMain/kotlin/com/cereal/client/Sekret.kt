package com.cereal.client

import dev.datlag.sekret.SekretConfig

public expect object Sekret {
  public fun databaseEncryptionKey(key: String, config: SekretConfig.Builder.() -> Unit): String?

  public fun databaseEncryptionKey(key: String, config: SekretConfig = SekretConfig()): String?

  public fun fileEncryptionKey(key: String, config: SekretConfig.Builder.() -> Unit): String?

  public fun fileEncryptionKey(key: String, config: SekretConfig = SekretConfig()): String?
}
