/*
 * Pojav Glow-Worm
 * Copyright (C) 2026 Pojav Glow-Worm contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.firefly.version

import java.io.File

object VersionIsolation {
    @JvmStatic
    fun resolveGameDirectory(
        gameHome: File,
        customGameDirectory: File?,
        versionId: String?,
        enabled: Boolean
    ): File {
        if (enabled) return defaultGameDirectory(gameHome, versionId, true)
        return customGameDirectory ?: gameHome
    }

    @JvmStatic
    fun defaultGameDirectory(gameHome: File, versionId: String?, enabled: Boolean): File {
        if (!enabled || !isDirectoryName(versionId)) return gameHome
        return File(gameHome, "versions/$versionId")
    }

    @JvmStatic
    fun displayRelativeGameDirectory(gameHome: File, versionId: String?): String {
        val rootName = gameHome.name.ifBlank { ".minecraft" }
        if (!isDirectoryName(versionId)) return rootName
        return "$rootName/versions/$versionId"
    }

    @JvmStatic
    fun isDirectoryName(value: String?): Boolean = !value.isNullOrBlank() &&
        value.length <= 128 &&
        !value.contains('/') &&
        !value.contains('\\') &&
        value != "." &&
        value != ".."
}
