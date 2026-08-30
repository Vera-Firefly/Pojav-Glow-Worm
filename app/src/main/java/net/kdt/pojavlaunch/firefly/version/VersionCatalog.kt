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

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.version.net.VersionHttpClients
import net.kdt.pojavlaunch.firefly.version.net.createVersionRequestBuilder
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class MinecraftVersionManifest(
    @SerializedName("latest") val latest: Latest,
    @SerializedName("versions") val versions: List<Entry>
) {
    data class Latest(
        @SerializedName("release") val release: String,
        @SerializedName("snapshot") val snapshot: String
    )

    data class Entry(
        @SerializedName("id") val id: String,
        @SerializedName("type") val type: String,
        @SerializedName("url") val url: String,
        @SerializedName("time") val time: String,
        @SerializedName("releaseTime") val releaseTime: String,
        @SerializedName("sha1") val sha1: String? = null
    )
}

enum class MinecraftVersionType { RELEASE, SNAPSHOT, OLD_BETA, OLD_ALPHA, APRIL_FOOLS, UNKNOWN }

data class MinecraftVersion(
    val entry: MinecraftVersionManifest.Entry,
    val type: MinecraftVersionType
)

object VersionCatalog {
    private const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
    private var cached: MinecraftVersionManifest? = null

    suspend fun getManifest(force: Boolean = false): MinecraftVersionManifest = withContext(Dispatchers.IO) {
        if (!force) cached?.let { return@withContext it }
        val cacheFile = File(Tools.DIR_CACHE, "minecraft-version-manifest-v2.json")
        val current = cacheFile.takeIf { it.isFile && !force && System.currentTimeMillis() - it.lastModified() < MAX_AGE_MILLIS }
            ?.let { runCatching { Tools.GLOBAL_GSON.fromJson(it.readText(), MinecraftVersionManifest::class.java) }.getOrNull() }
        val manifest = current ?: downloadManifest(cacheFile)
        cached = manifest
        manifest
    }

    suspend fun versions(force: Boolean = false): List<MinecraftVersion> = getManifest(force).versions
        .map { MinecraftVersion(it, classify(it)) }
        .sortedByDescending { it.entry.releaseTime }

    suspend fun find(versionId: String): MinecraftVersionManifest.Entry? =
        getManifest().versions.firstOrNull { it.id == versionId }

    private fun classify(entry: MinecraftVersionManifest.Entry): MinecraftVersionType = when (entry.type) {
        "release" -> MinecraftVersionType.RELEASE
        "snapshot", "pending", "unobfuscated" -> if (entry.id in APRIL_FOOLS) MinecraftVersionType.APRIL_FOOLS else MinecraftVersionType.SNAPSHOT
        "old_beta" -> MinecraftVersionType.OLD_BETA
        "old_alpha" -> MinecraftVersionType.OLD_ALPHA
        else -> MinecraftVersionType.UNKNOWN
    }

    private fun downloadManifest(cacheFile: File): MinecraftVersionManifest {
        val text = requestText(MirrorPolicy.candidates(VersionUrls.VERSION_MANIFEST))
        val manifest = Tools.GLOBAL_GSON.fromJson(text, MinecraftVersionManifest::class.java)
            ?: throw IOException("Minecraft version manifest is empty")
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(text)
        return manifest
    }

    fun requestText(urls: List<String>): String {
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                VersionHttpClients.DOWNLOAD_OKHTTP_CLIENT.newCall(createVersionRequestBuilder(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                    return response.body?.string()?.takeIf { it.isNotBlank() }
                        ?: throw IOException("Empty response from $url")
                }
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IOException("No download source succeeded", lastError)
    }

    private val APRIL_FOOLS = setOf(
        "26w14a", "25w14craftmine", "24w14potato", "23w13a_or_b", "22w13oneblockatatime",
        "20w14infinite", "20w14∞", "3D Shareware v1.34", "1.RV-Pre1", "15w14a",
        "2.0_blue", "2.0_red", "2.0_purple"
    )
}
