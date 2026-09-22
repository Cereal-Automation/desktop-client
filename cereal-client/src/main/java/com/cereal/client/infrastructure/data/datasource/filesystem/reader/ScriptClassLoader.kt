package com.cereal.client.infrastructure.data.datasource.filesystem.reader

import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import com.cereal.client.infrastructure.data.datasource.filesystem.security.EncryptionKey
import java.io.File

class ScriptClassLoader(
    private val file: File,
    private val encryptionKey: EncryptionKey,
    private val encryption: Encryption,
) : ClassLoader() {
    // List of packages that are available in the main classloader. These packages are either part of the public
    // interface of the script SDK or large libraries that are packaged with Cereal.
    // Note that the packages in the script SDK should not be obfuscated or else it's impossible to replace their
    // implementation.
    private val parentClassloaderPackages =
        arrayListOf("com.cereal.sdk", "kotlinx.coroutines")

    // Cache for decrypted class bytes to avoid repeated decryption
    private val classCache = mutableMapOf<String, ByteArray>()

    /**
     * Clears the internal class cache to free memory
     */
    fun clearCache() {
        classCache.clear()
    }

    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            var clazz = findLoadedClass(name)

            if (clazz == null) {
                clazz =
                    if (parentClassloaderPackages.any { name.startsWith(it) }) {
                        // Prevent conflicts by loading specific packages from parent first.
                        super.loadClass(name, resolve)
                    } else {
                        try {
                            findClass(name)
                        } catch (e: ClassNotFoundException) {
                            // If the class is not found, delegate to the parent if not in the cereal package.
                            // This is needed to prevent a script sideloading cereal classes.
                            if (!name.startsWith("com.cereal")) {
                                super.loadClass(name, resolve)
                            } else {
                                throw e
                            }
                        }
                    }
            }
            // Resolve the class if necessary
            if (resolve) {
                resolveClass(clazz)
            }

            return clazz
        }
    }

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*> {
        val classPath = name.replace('.', '/') + ".class"

        // Check cache first
        val classBytes =
            classCache[name] ?: run {
                val bytes =
                    encryption.readJarEntry(file, classPath, encryptionKey)
                        ?: throw ClassNotFoundException(name)
                classCache[name] = bytes
                bytes
            }

        return defineClass(name, classBytes, 0, classBytes.size)
    }
}
