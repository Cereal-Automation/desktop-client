package com.cereal.client.domain.model.extensions

import java.io.File

fun File.isJar(): Boolean =
    this.name.endsWith(".jar") ||
        this.name.endsWith(".dat")
