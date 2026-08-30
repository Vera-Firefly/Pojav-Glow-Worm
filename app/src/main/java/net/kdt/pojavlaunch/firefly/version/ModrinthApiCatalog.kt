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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jackhuang.hmcl.util.versioning.VersionNumber
import java.time.Instant

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
            parseVersions(projectId, minecraftVersion, json)
        }

    internal fun parseVersions(
        projectId: String,
        minecraftVersion: String,
        json: String
    ): List<ModrinthApiVersion> {
        val releases = JsonParser.parseString(json)
            .takeUnless { it.isJsonNull }
            ?.asJsonArray
            ?: JsonArray()
        return releases.mapNotNull { element ->
            val release = element.asJsonObject
            val gameVersions = release["game_versions"]?.asJsonArray?.map { it.asString }.orEmpty()
            if (minecraftVersion !in gameVersions) return@mapNotNull null
            val files = release["files"]?.asJsonArray?.mapNotNull { it.asJsonObject }.orEmpty()
            val file = files.firstOrNull { it["primary"]?.asBoolean == true } ?: files.firstOrNull()
                ?: return@mapNotNull null
            val url = file["url"]?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CatalogEntry(
                api = ModrinthApiVersion(
                    projectId = projectId,
                    minecraftVersion = minecraftVersion,
                    version = release["version_number"]?.asString ?: release["name"]?.asString ?: "unknown",
                    fileName = file["filename"]?.asString ?: return@mapNotNull null,
                    downloadUrl = url,
                    sha1 = file["hashes"]?.asJsonObject?.get("sha1")?.asString,
                    size = file["size"]?.asLong ?: -1L
                ),
                publishedAt = release.instantValue("date_published")
            )
        }.sortedWith(CATALOG_ENTRY_ORDER).map(CatalogEntry::api)
    }

    private fun JsonObject.instantValue(name: String): Instant? = get(name)
        ?.takeUnless { it.isJsonNull }
        ?.asString
        ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }

    private data class CatalogEntry(
        val api: ModrinthApiVersion,
        val publishedAt: Instant?
    )

    private val CATALOG_ENTRY_ORDER = Comparator<CatalogEntry> { first, second ->
        val publishedOrder = when {
            first.publishedAt == null && second.publishedAt == null -> 0
            first.publishedAt == null -> 1
            second.publishedAt == null -> -1
            else -> second.publishedAt.compareTo(first.publishedAt)
        }
        if (publishedOrder != 0) {
            publishedOrder
        } else {
            val versionOrder = runCatching {
                VersionNumber.asVersion(second.api.version).compareTo(VersionNumber.asVersion(first.api.version))
            }.getOrElse {
                second.api.version.compareTo(first.api.version, ignoreCase = true)
            }
            if (versionOrder != 0) versionOrder
            else first.api.fileName.compareTo(second.api.fileName, ignoreCase = true)
        }
    }
}
