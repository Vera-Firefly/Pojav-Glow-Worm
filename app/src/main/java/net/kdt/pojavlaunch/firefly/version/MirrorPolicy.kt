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

import net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences
import java.util.Locale

enum class MirrorPreference {
    AUTO,
    OFFICIAL_FIRST,
    MIRROR_FIRST;

    companion object {
        const val PREFERENCE_KEY = "minecraft_download_mirror"

        fun current(): MirrorPreference = runCatching {
            valueOf(LauncherPreferences.DEFAULT_PREF.getString(PREFERENCE_KEY, AUTO.name) ?: AUTO.name)
        }.getOrDefault(AUTO)
    }
}

object MirrorPolicy {
    private const val MODRINTH_OFFICIAL = "https://api.modrinth.com/v2"
    private const val MODRINTH_MCIM = "https://mod.mcimirror.top/modrinth/v2"
    private val mappings = linkedMapOf(
        "https://launchermeta.mojang.com" to VersionUrls.BMCL_ROOT,
        "https://piston-meta.mojang.com" to VersionUrls.BMCL_ROOT,
        "https://piston-data.mojang.com" to VersionUrls.BMCL_ROOT,
        "https://launcher.mojang.com" to VersionUrls.BMCL_ROOT,
        "https://libraries.minecraft.net" to "${VersionUrls.BMCL_ROOT}/libraries",
        "https://resources.download.minecraft.net" to "${VersionUrls.BMCL_ROOT}/assets",
        "https://files.minecraftforge.net/maven" to "${VersionUrls.BMCL_ROOT}/maven",
        "https://maven.minecraftforge.net" to "${VersionUrls.BMCL_ROOT}/maven",
        "https://maven.fabricmc.net" to "${VersionUrls.BMCL_ROOT}/maven",
        "https://meta.fabricmc.net" to "${VersionUrls.BMCL_ROOT}/fabric-meta"
    )

    fun candidates(url: String, assets: Boolean = false): List<String> {
        val mirror = mappings.entries.firstOrNull { url.startsWith(it.key) }
            ?.let { (source, target) -> url.replaceFirst(source, target) }
            ?: return listOf(url)
        val mirrorFirst = when (MirrorPreference.current()) {
            MirrorPreference.MIRROR_FIRST -> true
            MirrorPreference.OFFICIAL_FIRST -> false
            MirrorPreference.AUTO -> Locale.getDefault().country.equals("CN", ignoreCase = true)
        }
        return (if (assets || !mirrorFirst) listOf(url, mirror) else listOf(mirror, url)).distinct()
    }

    fun modrinthCandidates(url: String): List<String> {
        if (!url.startsWith(MODRINTH_OFFICIAL)) return listOf(url)
        val mirror = url.replaceFirst(MODRINTH_OFFICIAL, MODRINTH_MCIM)
        val mirrorFirst = when (MirrorPreference.current()) {
            MirrorPreference.MIRROR_FIRST -> true
            MirrorPreference.OFFICIAL_FIRST -> false
            MirrorPreference.AUTO -> Locale.getDefault().country.equals("CN", ignoreCase = true)
        }
        return (if (mirrorFirst) listOf(mirror, url) else listOf(url, mirror)).distinct()
    }
}
