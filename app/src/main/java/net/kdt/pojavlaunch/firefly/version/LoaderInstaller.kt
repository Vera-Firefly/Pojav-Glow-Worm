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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.version.download.MinecraftFileDownloader
import net.kdt.pojavlaunch.firefly.version.download.VersionDownloadTask
import net.kdt.pojavlaunch.firefly.version.download.runVersionDownloadBatch
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class LoaderKind { FABRIC, QUILT, FORGE, NEOFORGE, OPTIFINE }

data class LoaderVersion(
    val kind: LoaderKind,
    val minecraftVersion: String,
    val loaderVersion: String,
    val stable: Boolean = true,
    val downloadUrl: String? = null,
    val displayName: String = loaderVersion,
    val fileName: String? = null,
    /** The Forge build required by this OptiFine release, when the upstream publishes one. */
    val forgeCompatibility: String? = null
)

object LoaderCatalog {
    suspend fun fabric(minecraftVersion: String, force: Boolean = false): List<LoaderVersion> =
        fetchFabricLike("https://meta.fabricmc.net/v2", LoaderKind.FABRIC, minecraftVersion)

    suspend fun quilt(minecraftVersion: String, force: Boolean = false): List<LoaderVersion> =
        fetchFabricLike("https://meta.quiltmc.org/v3", LoaderKind.QUILT, minecraftVersion)

    suspend fun forge(minecraftVersion: String): List<LoaderVersion> = withContext(Dispatchers.IO) {
        val mirrorFirst = when (MirrorPreference.current()) {
            MirrorPreference.MIRROR_FIRST -> true
            MirrorPreference.OFFICIAL_FIRST -> false
            MirrorPreference.AUTO -> Locale.getDefault().country.equals("CN", ignoreCase = true)
        }
        val fetchers: List<() -> List<LoaderVersion>> = if (mirrorFirst) {
            listOf(
                { fetchForgeBmcl(minecraftVersion) },
                { fetchForgeMaven(minecraftVersion) }
            )
        } else {
            listOf(
                { fetchForgeMaven(minecraftVersion) },
                { fetchForgeBmcl(minecraftVersion) }
            )
        }
        var emptyResult: List<LoaderVersion>? = null
        var failure: Throwable? = null
        fetchers.forEach { fetch ->
            try {
                val versions = fetch()
                if (versions.isNotEmpty()) return@withContext versions
                emptyResult = versions
            } catch (error: Throwable) {
                failure = error
            }
        }
        emptyResult ?: throw IOException("Unable to fetch Forge versions", failure)
    }

    private fun fetchForgeMaven(minecraftVersion: String): List<LoaderVersion> {
        val encoded = minecraftVersion.replace("-", "_")
        val metadata = VersionCatalog.requestText(MirrorPolicy.candidates(
            "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"
        ))
        return Regex("<version>([^<]+)</version>").findAll(metadata)
            .map { it.groupValues[1] }
            .filter { it.startsWith("$minecraftVersion-") || it.startsWith("$encoded-") }
            .map { version ->
                LoaderVersion(
                    kind = LoaderKind.FORGE,
                    minecraftVersion = minecraftVersion,
                    loaderVersion = version.removePrefix("$minecraftVersion-").removePrefix("$encoded-"),
                    stable = !version.contains("beta", true),
                    downloadUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$version/forge-$version-installer.jar"
                )
            }.toList().sortedWith { first, second ->
                compareForgeVersions(first.loaderVersion, second.loaderVersion)
            }
    }

    private fun fetchForgeBmcl(minecraftVersion: String): List<LoaderVersion> {
        val encoded = minecraftVersion.replace("-", "_")
        val metadata = VersionCatalog.requestText(listOf(
            "https://bmclapi2.bangbang93.com/forge/minecraft/$encoded"
        ))
        return parseForgeBmclVersions(minecraftVersion, metadata)
    }

    internal fun parseForgeBmclVersions(minecraftVersion: String, metadata: String): List<LoaderVersion> {
        val values = runCatching { JsonParser.parseString(metadata).asJsonArray }.getOrNull()
            ?: return emptyList()
        return values.mapNotNull { item ->
            val version = item.asJsonObject["version"]?.asString ?: return@mapNotNull null
            val branch = item.asJsonObject["branch"]?.takeUnless { it.isJsonNull }?.asString
            val hasInstaller = item.asJsonObject["files"]?.asJsonArray?.any { file ->
                val details = file.asJsonObject
                details["category"]?.asString == "installer" && details["format"]?.asString == "jar"
            } == true
            if (!hasInstaller) return@mapNotNull null
            val coordinate = "$minecraftVersion-$version${branch?.let { "-$it" }.orEmpty()}"
            LoaderVersion(
                kind = LoaderKind.FORGE,
                minecraftVersion = minecraftVersion,
                loaderVersion = "$version${branch?.let { "-$it" }.orEmpty()}",
                stable = !version.contains("beta", true),
                downloadUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$coordinate/forge-$coordinate-installer.jar"
            )
        }.sortedWith { first, second ->
            compareForgeVersions(first.loaderVersion, second.loaderVersion)
        }
    }

    suspend fun neoforge(minecraftVersion: String): List<LoaderVersion> = withContext(Dispatchers.IO) {
        val current = fetchNeoForgeArtifact("neoforge", minecraftVersion)
        val legacy = fetchNeoForgeArtifact("forge", minecraftVersion)
        (current + legacy).sortedWith { first, second ->
            compareForgeVersions(first.loaderVersion, second.loaderVersion)
        }
    }

    private fun fetchNeoForgeArtifact(artifact: String, minecraftVersion: String): List<LoaderVersion> {
        val metadata = VersionCatalog.requestText(MirrorPolicy.candidates(
            "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/$artifact"
        ))
        val root = Tools.GLOBAL_GSON.fromJson(metadata, com.google.gson.JsonObject::class.java)
        val values = root["versions"]?.asJsonArray ?: com.google.gson.JsonArray()
        return values.mapNotNull { item ->
            val version = item.asString
            if (neoForgeMinecraftVersion(version) != minecraftVersion) return@mapNotNull null
            LoaderVersion(
                kind = LoaderKind.NEOFORGE,
                minecraftVersion = minecraftVersion,
                loaderVersion = if (version.contains("1.20.1")) version.removePrefix("1.20.1-") else version,
                stable = !version.contains("beta", true),
                downloadUrl = "https://maven.neoforged.net/releases/net/neoforged/$artifact/$version/$artifact-$version-installer.jar"
            )
        }
    }

    private fun neoForgeMinecraftVersion(version: String): String {
        if (version.contains("1.20.1")) return "1.20.1"
        if (version.startsWith("0.")) return version.removePrefix("0.").substringBefore('-').substringBeforeLast('.')
        val parts = forgeParts(version)
        if (parts.isEmpty()) return ""
        return if (parts[0] >= 26) {
            buildString {
                append(parts[0]).append('.').append(parts.getOrElse(1) { 0 })
                parts.getOrElse(2) { 0 }.takeIf { it > 0 }?.let { append('.').append(it) }
            }
        } else {
            "1.${parts[0]}" + parts.getOrElse(1) { 0 }.takeIf { it != 0 }?.let { ".$it" }.orEmpty()
        }
    }

    private fun forgeParts(version: String): List<Int> = version.split('.', '-')
        .mapNotNull { it.toIntOrNull() }

    private fun compareForgeVersions(first: String, second: String): Int {
        val firstParts = forgeParts(first)
        val secondParts = forgeParts(second)
        val largest = maxOf(firstParts.size, secondParts.size)
        for (index in 0 until largest) {
            val left = firstParts.getOrElse(index) { 0 }
            val right = secondParts.getOrElse(index) { 0 }
            if (left != right) return right.compareTo(left)
        }
        return second.compareTo(first)
    }

    private suspend fun fetchFabricLike(base: String, kind: LoaderKind, minecraftVersion: String): List<LoaderVersion> = withContext(Dispatchers.IO) {
        val text = VersionCatalog.requestText(MirrorPolicy.candidates("$base/versions"))
        val parsed = Tools.GLOBAL_GSON.fromJson(text, com.google.gson.JsonElement::class.java)
        if (parsed.isJsonObject) {
            val games = parsed.asJsonObject["game"]?.asJsonArray
            if (games != null && games.none { it.asJsonObject["version"]?.asString == minecraftVersion }) return@withContext emptyList()
        }
        val root = if (parsed.isJsonObject) parsed.asJsonObject["loader"]?.asJsonArray
        else if (parsed.isJsonArray) parsed.asJsonArray else null
        (root ?: com.google.gson.JsonArray()).mapNotNull { item ->
            val version = item.asJsonObject["version"]?.asString ?: return@mapNotNull null
            val stable = item.asJsonObject["stable"]?.asBoolean ?: !version.contains("beta", true)
            LoaderVersion(kind, minecraftVersion, version, stable,
                "$base/versions/loader/$minecraftVersion/$version/profile/json")
        }
    }

    suspend fun installFabricLike(
        loader: LoaderVersion,
        customName: String = "${loader.minecraftVersion}-${loader.kind.name.lowercase()}-${loader.loaderVersion}",
        onProgress: suspend (net.kdt.pojavlaunch.firefly.version.download.engine.BatchProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        require(loader.kind == LoaderKind.FABRIC || loader.kind == LoaderKind.QUILT)
        val url = loader.downloadUrl ?: throw IOException("Loader metadata URL is unavailable")
        MinecraftFileDownloader(loader.minecraftVersion).run(onProgress = onProgress)
        val json = VersionCatalog.requestText(MirrorPolicy.candidates(url))
        val target = VersionPaths.versionJson(customName)
        target.parentFile?.mkdirs()
        target.writeText(json)
        MinecraftFileDownloader(
            requestedVersionId = loader.minecraftVersion,
            installedVersionId = customName,
            verifyIntegrity = true,
            mode = net.kdt.pojavlaunch.firefly.version.download.MinecraftDownloadMode.VERIFY_AND_REPAIR
        ).runFromInstalledManifest(target, onProgress = onProgress)
        target
    }

    suspend fun optifine(minecraftVersion: String): List<LoaderVersion> = withContext(Dispatchers.IO) {
        val html = VersionCatalog.requestText(MirrorPolicy.candidates("https://optifine.net/downloads"))
        parseOptiFineVersions(minecraftVersion, html)
    }

    internal fun parseOptiFineVersions(minecraftVersion: String, html: String): List<LoaderVersion> {
        val names = Regex("<td[^>]*class=['\"]colFile['\"][^>]*>([^<]+)</td>")
            .findAll(html).map { it.groupValues[1].trim() }.toList()
        val forges = Regex("<td[^>]*class=['\"]colForge['\"][^>]*>([^<]+)</td>")
            .findAll(html).map { it.groupValues[1].trim() }.toList()
        val jars = Regex("(?:adfoc\\.us|[?&])[^>\"']*?(?:[?&]|&amp;)f=([^&\"']+\\.jar)")
            .findAll(html).map { it.groupValues[1] }.toList()
        if (names.size != forges.size || names.size != jars.size) return emptyList()
        return names.mapIndexedNotNull { index, display ->
            val fileName = jars.getOrNull(index) ?: return@mapIndexedNotNull null
            val normalized = fileName.removePrefix("preview_").removeSuffix(".jar")
            val inherit = normalized.removePrefix("OptiFine_").substringBefore("_HD_")
                .removeSuffix(".0")
            if (inherit != minecraftVersion) return@mapIndexedNotNull null
            val compat = forges.getOrNull(index)
                ?.takeUnless { it.contains("N/A", ignoreCase = true) }
                ?.removePrefix("Forge ")
                ?.replace("#", "")
                ?.trim()
            LoaderVersion(
                kind = LoaderKind.OPTIFINE,
                minecraftVersion = minecraftVersion,
                loaderVersion = normalized.removePrefix("OptiFine_${minecraftVersion}_"),
                stable = !fileName.contains("preview", true),
                downloadUrl = "https://optifine.net/adloadx?f=$fileName",
                displayName = display,
                fileName = fileName,
                forgeCompatibility = compat
            )
        }.sortedByDescending { it.loaderVersion }
    }

    suspend fun resolveOptiFineDownload(loader: LoaderVersion): String = withContext(Dispatchers.IO) {
        val fileName = loader.fileName ?: throw IOException("OptiFine file metadata is unavailable")
        val landing = VersionCatalog.requestText(MirrorPolicy.candidates(
            loader.downloadUrl ?: "https://optifine.net/adloadx?f=$fileName"
        ))
        val relative = Regex("downloadx\\?f=[^\"'<> ]+").find(landing)?.value
            ?: throw IOException("OptiFine download URL was not returned")
        "https://optifine.net/$relative"
    }

    suspend fun installOptiFine(
        loader: LoaderVersion,
        customName: String = "${loader.minecraftVersion}-OptiFine-${loader.loaderVersion}",
        onProgress: suspend (net.kdt.pojavlaunch.firefly.version.download.engine.BatchProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        require(loader.kind == LoaderKind.OPTIFINE)
        MinecraftFileDownloader(loader.minecraftVersion).run(onProgress = onProgress)
        val landing = VersionCatalog.requestText(MirrorPolicy.candidates(loader.downloadUrl ?: throw IOException("OptiFine URL unavailable")))
        val relative = Regex("downloadx\\?f=[^\"'<> ]+").find(landing)?.value
            ?: throw IOException("OptiFine download URL was not returned")
        val fileName = java.net.URLDecoder.decode(relative.substringAfter("f="), StandardCharsets.UTF_8.name())
        val directUrl = "https://optifine.net/downloadx?f=$fileName"
        val installer = File(Tools.DIR_CACHE, "$customName.installer.jar")
        runVersionDownloadBatch(listOf(VersionDownloadTask(MirrorPolicy.candidates(directUrl), installer, null)), 2, onProgress)
        val vanillaJson = VersionPaths.versionJson(loader.minecraftVersion)
        val vanillaManifest = Tools.GLOBAL_GSON.fromJson(vanillaJson.reader(), net.kdt.pojavlaunch.firefly.version.model.GameManifest::class.java)
        val targetJson = VersionPaths.versionJson(customName)
        val targetJar = VersionPaths.versionJar(customName)
        targetJar.parentFile?.mkdirs()
        VersionPaths.versionJar(loader.minecraftVersion).copyTo(targetJar, overwrite = true)
        val json = com.google.gson.JsonObject().apply {
            addProperty("id", customName)
            addProperty("inheritsFrom", loader.minecraftVersion)
            addProperty("type", "release")
            addProperty("mainClass", vanillaManifest.mainClass)
            add("libraries", com.google.gson.JsonArray().apply {
                add(com.google.gson.JsonObject().apply { addProperty("name", "optifine:OptiFine:${loader.loaderVersion}") })
            })
            add("arguments", com.google.gson.JsonObject().apply {
                add("game", com.google.gson.JsonArray().apply { add("--tweakClass"); add("optifine.OptiFineTweaker") })
            })
        }
        targetJson.writeText(Tools.GLOBAL_GSON.toJson(json))
        val libraryTarget = File(VersionPaths.libraries(), "optifine/OptiFine/${loader.loaderVersion}/OptiFine-${loader.loaderVersion}.jar")
        installer.copyTo(libraryTarget, overwrite = true)
        installer.delete()
        targetJson
    }
}
