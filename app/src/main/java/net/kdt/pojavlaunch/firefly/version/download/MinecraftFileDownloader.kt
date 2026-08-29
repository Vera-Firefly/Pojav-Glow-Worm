/*
 * Pojav Glow-Worm
 * Copyright (C) 2026 Pojav Glow-Worm contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.firefly.version.download

import com.google.gson.JsonParseException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.version.MirrorPolicy
import net.kdt.pojavlaunch.firefly.version.VersionCatalog
import net.kdt.pojavlaunch.firefly.version.VersionPaths
import net.kdt.pojavlaunch.firefly.version.VersionUrls
import net.kdt.pojavlaunch.firefly.version.io.calculateSha1
import net.kdt.pojavlaunch.firefly.version.model.AssetIndexJson
import net.kdt.pojavlaunch.firefly.version.model.GameManifest
import java.io.File
import java.io.IOException
import java.util.LinkedHashMap

enum class MinecraftDownloadMode {
    INSTALL,
    VERIFY_AND_REPAIR
}

data class MinecraftDownloadResult(
    val versionId: String,
    val downloadedFiles: Int,
    val totalFiles: Int
)

/**
 * Builds the complete Minecraft file set from version metadata. Installation and repair share this
 * class so the files verified before launch use the same source ordering and integrity checks.
 */
class MinecraftFileDownloader(
    private val requestedVersionId: String,
    private val installedVersionId: String = requestedVersionId,
    private val verifyIntegrity: Boolean = true,
    private val mode: MinecraftDownloadMode = MinecraftDownloadMode.INSTALL
) {
    private val plannedDownloads = LinkedHashMap<String, VersionDownloadTask>()
    private val copyActions = mutableListOf<CopyAction>()
    private val visitedVersions = LinkedHashSet<String>()

    suspend fun run(
        maxConnections: Int = DEFAULT_DOWNLOAD_CONNECTIONS,
        onProgress: suspend (net.kdt.pojavlaunch.firefly.version.download.engine.BatchProgress) -> Unit = {}
    ): MinecraftDownloadResult {
        val rootManifest = if (mode == MinecraftDownloadMode.INSTALL) {
            downloadRemoteManifest(requestedVersionId, installedVersionId)
        } else {
            readInstalledManifest(installedVersionId)
        }
        scheduleManifest(rootManifest, installedVersionId)
        val tasks = plannedDownloads.values.toList()
        runVersionDownloadBatch(tasks, maxConnections, onProgress)
        copyActions.forEach { action -> action.execute() }
        return MinecraftDownloadResult(installedVersionId, tasks.size, tasks.size)
    }

    suspend fun runFromInstalledManifest(
        manifestFile: File,
        maxConnections: Int = DEFAULT_DOWNLOAD_CONNECTIONS,
        onProgress: suspend (net.kdt.pojavlaunch.firefly.version.download.engine.BatchProgress) -> Unit = {}
    ): MinecraftDownloadResult {
        if (!manifestFile.isFile) throw IOException("Version metadata is missing: ${manifestFile.absolutePath}")
        val manifest = parseJson(manifestFile, GameManifest::class.java)
        scheduleManifest(manifest, installedVersionId)
        val tasks = plannedDownloads.values.toList()
        runVersionDownloadBatch(tasks, maxConnections, onProgress)
        copyActions.forEach { it.execute() }
        return MinecraftDownloadResult(installedVersionId, tasks.size, tasks.size)
    }

    private suspend fun scheduleManifest(manifest: GameManifest, storageVersionId: String) {
        currentCoroutineContext().ensureActive()
        val cycleKey = "$storageVersionId:${manifest.inheritsFrom.orEmpty()}"
        if (!visitedVersions.add(cycleKey)) return

        val parentId = manifest.inheritsFrom?.takeIf { it.isNotBlank() && it != storageVersionId }
        if (parentId != null) {
            val parentManifest = runCatching { readInstalledManifest(parentId) }
                .getOrElse {
                    if (VersionCatalog.find(parentId) == null) throw IOException("Inherited version is unavailable: $parentId", it)
                    downloadRemoteManifest(parentId, parentId)
                }
            scheduleManifest(parentManifest, parentId)
        }

        scheduleClient(manifest, storageVersionId, parentId)
        scheduleAssets(manifest)
        scheduleLibraries(manifest)
    }

    private fun scheduleClient(manifest: GameManifest, storageVersionId: String, parentId: String?) {
        val target = VersionPaths.versionJar(storageVersionId)
        val client = manifest.downloads?.client
        if (client?.url?.isNotBlank() == true) {
            schedule(
                urls = MirrorPolicy.candidates(client.url),
                target = target,
                sha1 = client.sha1,
                size = client.size
            )
            return
        }
        parentId?.let {
            copyActions += CopyAction(VersionPaths.versionJar(it), target)
        }
    }

    private suspend fun scheduleAssets(manifest: GameManifest) {
        val assetIndex = manifest.assetIndex ?: return
        val assetIndexFile = VersionPaths.assetIndex(assetIndex.id)
        downloadJson(assetIndexFile, MirrorPolicy.candidates(assetIndex.url), assetIndex.sha1)
        val parsedIndex = parseJson(assetIndexFile, AssetIndexJson::class.java)
        parsedIndex.objects.orEmpty().forEach { (relativePath, objectInfo) ->
            currentCoroutineContext().ensureActive()
            val hash = objectInfo.hash ?: return@forEach
            if (hash.length < 2) return@forEach
            val hashedPath = "${hash.substring(0, 2)}/$hash"
            val target = when {
                parsedIndex.isMapToResources -> File(VersionPaths.resources(), relativePath)
                parsedIndex.isVirtual -> File(VersionPaths.assets(), relativePath)
                else -> File(VersionPaths.assets(), "objects/$hashedPath")
            }
            schedule(
                urls = MirrorPolicy.candidates("${VersionUrls.MINECRAFT_ASSETS}$hashedPath", assets = true),
                target = target,
                sha1 = hash,
                size = objectInfo.size
            )
        }
    }

    private suspend fun scheduleLibraries(manifest: GameManifest) {
        manifest.libraries.orEmpty().forEach { library ->
            currentCoroutineContext().ensureActive()
            if (!GameManifest.Rule.checkRules(library.rules)) return@forEach
            if (library.name.startsWith("org.lwjgl")) return@forEach

            val replacement = libraryReplacement(library.name)
            val artifact = library.downloads?.artifact
            val path = replacement?.path ?: artifact?.path ?: artifactPath(library) ?: return@forEach
            val sha1 = replacement?.sha1 ?: artifact?.sha1 ?: library.sha1
            val size = replacement?.size ?: artifact?.size ?: library.size
            val officialUrl = replacement?.url ?: artifact?.url ?: buildLibraryUrl(library, path)
            val downloadable = artifact?.url?.isNotBlank() == true || library.url?.isNotBlank() == true || replacement != null

            if (officialUrl != null) {
                schedule(
                    urls = MirrorPolicy.candidates(officialUrl),
                    target = File(VersionPaths.libraries(), path),
                    sha1 = sha1,
                    size = size,
                    downloadable = downloadable
                )
            } else if (!File(VersionPaths.libraries(), path).isFile) {
                throw IOException("Required library is unavailable: ${library.name}")
            }
        }
    }

    private suspend fun downloadRemoteManifest(remoteVersionId: String, targetVersionId: String): GameManifest {
        val entry = VersionCatalog.find(remoteVersionId)
            ?: throw IllegalArgumentException("Minecraft version not found: $remoteVersionId")
        val target = VersionPaths.versionJson(targetVersionId)
        downloadJson(target, MirrorPolicy.candidates(entry.url), entry.sha1)
        return parseJson(target, GameManifest::class.java)
    }

    private suspend fun readInstalledManifest(versionId: String): GameManifest {
        val file = VersionPaths.versionJson(versionId)
        if (!file.isFile) {
            if (mode == MinecraftDownloadMode.VERIFY_AND_REPAIR && VersionCatalog.find(versionId) != null) {
                return downloadRemoteManifest(versionId, versionId)
            }
            throw IOException("Version metadata is missing: $versionId")
        }
        return parseJson(file, GameManifest::class.java)
    }

    private suspend fun downloadJson(target: File, urls: List<String>, sha1: String?) {
        val reusable = target.isFile && (!verifyIntegrity || sha1.isNullOrBlank() || sha1.equals(calculateSha1(target), true))
        if (!reusable) {
            if (target.isFile) target.delete()
            runVersionDownloadBatch(
                tasks = listOf(VersionDownloadTask(urls, target, sha1, downloadable = true)),
                maxConnections = 1
            )
        }
    }

    private fun <T> parseJson(file: File, type: Class<T>): T = try {
        Tools.GLOBAL_GSON.fromJson(file.reader(), type)
            ?: throw JsonParseException("Empty JSON: ${file.absolutePath}")
    } catch (error: Exception) {
        throw IOException("Unable to parse ${file.name}", error)
    }

    private fun schedule(
        urls: List<String>,
        target: File,
        sha1: String?,
        size: Long,
        downloadable: Boolean = true
    ) {
        val normalized = target.absoluteFile.path
        plannedDownloads.putIfAbsent(
            normalized,
            VersionDownloadTask(urls, target, sha1, size, downloadable)
        )
    }

    private fun artifactPath(library: GameManifest.Library): String? {
        val coordinates = library.name.split(':')
        if (coordinates.size < 3) return null
        val group = coordinates[0].replace('.', '/')
        val artifact = coordinates[1]
        val version = coordinates[2]
        var classifier = coordinates.getOrNull(3)?.let { "-$it" }.orEmpty()
        if (library.isNative) {
            library.natives?.get(net.kdt.pojavlaunch.firefly.version.model.OperatingSystem.Linux)
                ?.let { classifier = "-$it" }
        }
        return "$group/$artifact/$version/$artifact-$version$classifier.jar"
    }

    private fun buildLibraryUrl(library: GameManifest.Library, path: String): String? {
        val base = library.url?.takeIf { it.isNotBlank() } ?: VersionUrls.MINECRAFT_LIBRARIES
        return "${base.trimEnd('/')}/$path"
    }

    private data class CopyAction(val source: File, val target: File) {
        fun execute() {
            if (!source.isFile) throw IOException("Inherited client JAR is missing: ${source.absolutePath}")
            target.parentFile?.mkdirs()
            if (!target.isFile || source.length() != target.length()) source.copyTo(target, overwrite = true)
        }
    }

    private data class LibraryReplacement(val path: String, val sha1: String, val size: Long, val url: String)

    private fun libraryReplacement(name: String): LibraryReplacement? = when {
        name.startsWith("net.java.dev.jna:jna:") -> LibraryReplacement(
            "net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar",
            "1200e7ebeedbe0d10062093f32925a912020e747",
            0L,
            "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar"
        )
        name.startsWith("com.github.oshi:oshi-core:6.2.") -> LibraryReplacement(
            "com/github/oshi/oshi-core/6.3.0/oshi-core-6.3.0.jar",
            "9e98cf55be371cafdb9c70c35d04ec2a8c2b42ac",
            0L,
            "https://repo1.maven.org/maven2/com/github/oshi/oshi-core/6.3.0/oshi-core-6.3.0.jar"
        )
        name.startsWith("org.ow2.asm:asm-all:") && name.substringAfterLast(':').substringBefore('.').toIntOrNull()?.let { it < 5 } == true -> LibraryReplacement(
            "org/ow2/asm/asm-all/5.0.4/asm-all-5.0.4.jar",
            "e6244859997b3d4237a552669279780876228909",
            0L,
            "https://repo1.maven.org/maven2/org/ow2/asm/asm-all/5.0.4/asm-all-5.0.4.jar"
        )
        else -> null
    }

    companion object {
        const val DEFAULT_DOWNLOAD_CONNECTIONS = 64
    }
}
