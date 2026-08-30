/*
 * Pojav Glow-Worm
 * Copyright (C) 2026 Pojav Glow-Worm contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.firefly.version.io

import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.io.IOException

fun File.ensureParentDirectory(): File {
    val parent = parentFile ?: throw IOException("Target file has no parent: $absolutePath")
    if (parent.isFile || (!parent.exists() && !parent.mkdirs())) {
        throw IOException("Unable to create target directory: $parent")
    }
    if (!parent.canWrite()) throw IOException("Target directory is not writable: $parent")
    return this
}

fun calculateSha1(file: File): String = file.inputStream().use(DigestUtils::sha1Hex)
