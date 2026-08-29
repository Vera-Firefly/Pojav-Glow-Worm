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

import com.movtery.ui.subassembly.customprofilepath.ProfilePathHome
import java.io.File

object VersionPaths {
    fun gameHome(): File = File(ProfilePathHome.getGameHome()).also { it.mkdirs() }

    fun versions(): File = File(ProfilePathHome.getVersionsHome()).also { it.mkdirs() }

    fun libraries(): File = File(ProfilePathHome.getLibrariesHome()).also { it.mkdirs() }

    fun assets(): File = File(ProfilePathHome.getAssetsHome()).also { it.mkdirs() }

    fun resources(): File = File(ProfilePathHome.getResourcesHome()).also { it.mkdirs() }

    fun versionDirectory(versionId: String): File = File(versions(), versionId)

    fun versionJson(versionId: String): File = File(versionDirectory(versionId), "$versionId.json")

    fun versionJar(versionId: String): File = File(versionDirectory(versionId), "$versionId.jar")

    fun assetIndex(assetId: String): File = File(assets(), "indexes/$assetId.json")
}
