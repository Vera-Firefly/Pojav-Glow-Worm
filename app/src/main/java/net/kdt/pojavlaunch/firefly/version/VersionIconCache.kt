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

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.profiles.ProfileIconIds
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object VersionIconCache {
    private val cache = ConcurrentHashMap<String, Drawable>()

    @JvmStatic
    fun fetch(resources: Resources, version: PgwInstalledVersion): Drawable {
        val key = "${version.id}:${version.config.iconId}:${PgwVersionRepository.iconFile(version.id).lastModified()}"
        return cache.getOrPut(key) {
            readCustom(resources, PgwVersionRepository.iconFile(version.id))
                ?: staticIcon(resources, version.config.iconId)
                ?: staticIcon(resources, PgwVersionIcons.defaultFor(version.local.kind))
                ?: requireNotNull(ResourcesCompat.getDrawable(resources, R.drawable.ic_pojav_full, null))
        }
    }

    @JvmStatic
    fun drop(versionId: String) {
        cache.keys.removeIf { it.startsWith("$versionId:") }
    }

    private fun readCustom(resources: Resources, file: File): Drawable? {
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return BitmapDrawable(resources, bitmap)
    }

    private fun staticIcon(resources: Resources, iconId: String): Drawable? {
        val resource = when (iconId) {
            ProfileIconIds.MINECRAFT -> R.drawable.ic_minecraft
            ProfileIconIds.FORGE -> R.drawable.ic_forge
            ProfileIconIds.NEOFORGE -> R.drawable.ic_neoforge
            ProfileIconIds.FABRIC -> R.drawable.ic_fabric
            ProfileIconIds.QUILT -> R.drawable.ic_quilt
            ProfileIconIds.OPTIFINE -> R.drawable.ic_optifine
            ProfileIconIds.DEFAULT -> R.drawable.ic_pojav_full
            else -> return null
        }
        return ResourcesCompat.getDrawable(resources, resource, null)
    }
}
