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

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.firefly.Tools

data class ModrinthApiVersion(
    val projectId: String,
    val minecraftVersion: String,
    val version: String,
    val fileName: String,
    val downloadUrl: String,
    val sha1: String?,
    val size: Long
)

/**
 * Reads the fixed API projects used by the version installer. Only a release's primary file is
 * eligible so optional sources and additional files are never placed in an instance's mods folder.
 */
object ModrinthApiCatalog {
    const val FABRIC_API_PROJECT = "P7dR8mSH"
    const val QUILTED_FABRIC_API_PROJECT = "qvIfYCYJ"
    private const val MODRINTH_API = "https://api.modrinth.com/v2"

    suspend fun fabricApi(minecraftVersion: String): List<ModrinthApiVersion> =
        versions(FABRIC_API_PROJECT, minecraftVersion)

    suspend fun quiltedFabricApi(minecraftVersion: String): List<ModrinthApiVersion> =
        versions(QUILTED_FABRIC_API_PROJECT, minecraftVersion)

    private suspend fun versions(projectId: String, minecraftVersion: String): List<ModrinthApiVersion> =
        withContext(Dispatchers.IO) {
            val json = VersionCatalog.requestText(MirrorPolicy.modrinthCandidates(
                "$MODRINTH_API/project/$projectId/version"
            ))
            val releases = Tools.GLOBAL_GSON.fromJson(json, com.google.gson.JsonArray::class.java)
                ?: com.google.gson.JsonArray()
            releases.mapNotNull { element ->
                val release = element.asJsonObject
                val gameVersions = release["game_versions"]?.asJsonArray?.map { it.asString }.orEmpty()
                if (minecraftVersion !in gameVersions) return@mapNotNull null
                val files = release["files"]?.asJsonArray?.mapNotNull { it.asJsonObject }.orEmpty()
                val file = files.firstOrNull { it["primary"]?.asBoolean == true } ?: files.firstOrNull()
                    ?: return@mapNotNull null
                val url = file["url"]?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ModrinthApiVersion(
                    projectId = projectId,
                    minecraftVersion = minecraftVersion,
                    version = release["version_number"]?.asString ?: release["name"]?.asString ?: "unknown",
                    fileName = file["filename"]?.asString ?: return@mapNotNull null,
                    downloadUrl = url,
                    sha1 = file["hashes"]?.asJsonObject?.get("sha1")?.asString,
                    size = file["size"]?.asLong ?: -1L
                )
            }
        }
}
