package com.cereal.client.domain.model.extensions

import java.io.File
import java.io.IOException
import java.net.URL

fun File.isJar(): Boolean =
    this.name.endsWith(".jar") ||
        this.name.endsWith(".dat")

@Throws(IOException::class)
fun File.getJarUrl(): URL = java.net.URI("jar:" + this.toURI().toURL().toExternalForm() + "!/").toURL()
