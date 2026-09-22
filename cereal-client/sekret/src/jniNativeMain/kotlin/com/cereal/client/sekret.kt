package com.cereal.client

import dev.datlag.sekret.JNIEnvVar
import dev.datlag.sekret.SekretHelper
import dev.datlag.sekret.fill
import dev.datlag.sekret.getNativeValue
import dev.datlag.sekret.jIntArray
import dev.datlag.sekret.jObject
import dev.datlag.sekret.jString
import dev.datlag.sekret.newIntArray
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(
  ExperimentalForeignApi::class,
  ExperimentalNativeApi::class,
)
@CName("Java_com_cereal_client_Sekret_databaseEncryptionKeyDecrypted")
public fun databaseEncryptionKeyDecrypted(
  env: CPointer<JNIEnvVar>,
  clazz: jObject?,
  key: jString,
): jString? {
  val obfuscatedSecret = intArrayOf(0x54, 0x7, 0x5a, 0x10, 0x5e)
  return SekretHelper.getNativeValue(obfuscatedSecret, key, env)
}

@OptIn(
  ExperimentalForeignApi::class,
  ExperimentalNativeApi::class,
)
@CName("Java_com_cereal_client_Sekret__1databaseEncryptionKeyEncrypted")
public fun _databaseEncryptionKeyEncrypted(env: CPointer<JNIEnvVar>, clazz: jObject?): jIntArray? {
  val obfuscatedSecret = intArrayOf(0x54, 0x7, 0x5a, 0x10, 0x5e)
  val target = env.newIntArray(obfuscatedSecret.size) ?: return null
  return env.fill(target, obfuscatedSecret)
}

@OptIn(
  ExperimentalForeignApi::class,
  ExperimentalNativeApi::class,
)
@CName("Java_com_cereal_client_Sekret_fileEncryptionKeyDecrypted")
public fun fileEncryptionKeyDecrypted(
  env: CPointer<JNIEnvVar>,
  clazz: jObject?,
  key: jString,
): jString? {
  val obfuscatedSecret = intArrayOf(0x54, 0x7, 0x5a, 0x10, 0x5e)
  return SekretHelper.getNativeValue(obfuscatedSecret, key, env)
}

@OptIn(
  ExperimentalForeignApi::class,
  ExperimentalNativeApi::class,
)
@CName("Java_com_cereal_client_Sekret__1fileEncryptionKeyEncrypted")
public fun _fileEncryptionKeyEncrypted(env: CPointer<JNIEnvVar>, clazz: jObject?): jIntArray? {
  val obfuscatedSecret = intArrayOf(0x54, 0x7, 0x5a, 0x10, 0x5e)
  val target = env.newIntArray(obfuscatedSecret.size) ?: return null
  return env.fill(target, obfuscatedSecret)
}
