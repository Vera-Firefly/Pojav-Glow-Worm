/*
 * Pojav Glow-Worm
 * Copyright (C) 2026 Pojav Glow-Worm contributors
 *
 * Portions are derived from Zalith Launcher 2's Forge-like installer flow.
 * Copyright (C) 2025 MovTery and contributors.
 * Licensed under GPL-3.0-or-later.
 */

package net.kdt.pojavlaunch.firefly.version

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.version.download.VersionDownloadTask
import net.kdt.pojavlaunch.firefly.version.download.engine.BatchProgress
import net.kdt.pojavlaunch.firefly.version.download.runVersionDownloadBatch
import net.kdt.pojavlaunch.firefly.version.io.calculateSha1
import net.kdt.pojavlaunch.firefly.version.model.GameManifest
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.zip.ZipFile

private const val FORGE_MAVEN = "https://maven.minecraftforge.net"
private const val NEOFORGE_MAVEN = "https://maven.neoforged.net/releases"
private const val DEFAULT_LAUNCHER_PROFILES = "{\"profiles\":{\"default\":{\"lastVersionId\":\"latest-release\"}},\"selectedProfile\":\"default\"}"

private data class ForgeProcessor(
    val sides: List<String>? = null,
    val jar: String,
    val classpath: List<String>? = null,
    val args: List<String>? = null,
    val outputs: Map<String, String>? = null
) {
    fun appliesToClient(): Boolean = sides == null || "client" in sides
}

/**
 * Installs Forge-family archives by extracting their metadata, resolving processor libraries and
 * executing the listed processors through the isolated JVM service.
 */
object ForgeLikeInstaller {
    suspend fun install(
        context: android.content.Context,
        loader: LoaderVersion,
        installer: File,
        temporaryMinecraftHome: File,
        minecraftVersion: String,
        metadataTarget: File,
        onLibraryProgress: suspend (BatchProgress) -> Unit = {},
        onProcessor: (String, Int, Int) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        require(loader.kind == LoaderKind.FORGE || loader.kind == LoaderKind.NEOFORGE) {
            "Forge-like installer required"
        }
        require(installer.isFile) { "Loader installer is missing: ${installer.absolutePath}" }
        prepareLauncherProfiles(temporaryMinecraftHome)
        ZipFile(installer).use { zip ->
            val installProfile = readObject(zip, "install_profile.json")
            if (installProfile.has("processors")) {
                installModern(
                    context = context,
                    loader = loader,
                    zip = zip,
                    installer = installer,
                    installProfile = installProfile,
                    temporaryMinecraftHome = temporaryMinecraftHome,
                    minecraftVersion = minecraftVersion,
                    metadataTarget = metadataTarget,
                    onLibraryProgress = onLibraryProgress,
                    onProcessor = onProcessor
                )
            } else {
                installLegacy(
                    loader = loader,
                    zip = zip,
                    installProfile = installProfile,
                    temporaryMinecraftHome = temporaryMinecraftHome,
                    minecraftVersion = minecraftVersion,
                    metadataTarget = metadataTarget,
                    onLibraryProgress = onLibraryProgress
                )
            }
        }
    }

    private suspend fun installModern(
        context: android.content.Context,
        loader: LoaderVersion,
        zip: ZipFile,
        installer: File,
        installProfile: JsonObject,
        temporaryMinecraftHome: File,
        minecraftVersion: String,
        metadataTarget: File,
        onLibraryProgress: suspend (BatchProgress) -> Unit,
        onProcessor: (String, Int, Int) -> Unit
    ) {
        val versionMetadata = readObject(zip, "version.json")
        metadataTarget.parentFile?.mkdirs()
        metadataTarget.writeText(Tools.GLOBAL_GSON.toJson(versionMetadata))

        val libraryRoot = File(temporaryMinecraftHome, "libraries")
        extractEmbeddedLibraries(zip, installProfile, libraryRoot)
        val variables = resolveInstallerData(zip, installProfile, temporaryMinecraftHome, installer, minecraftVersion)
        val downloadTasks = linkedMapOf<String, VersionDownloadTask>()
        collectLibraries(installProfile, versionMetadata).forEach { library ->
            libraryDownloadTask(library, libraryRoot, loader.kind)?.let { task ->
                downloadTasks.putIfAbsent(task.target.absolutePath, task)
            }
        }
        scheduleMojangMappings(
            installProfile = installProfile,
            temporaryMinecraftHome = temporaryMinecraftHome,
            variables = variables,
            downloads = downloadTasks
        )
        runVersionDownloadBatch(downloadTasks.values.toList(), maxConnections = 8, onProgress = onLibraryProgress)

        val processors = installProfile["processors"].asJsonArrayOrNull().orEmpty().mapNotNull { element ->
            runCatching { Tools.GLOBAL_GSON.fromJson(element, ForgeProcessor::class.java) }.getOrNull()
        }
        runProcessors(
            context = context,
            processors = processors,
            temporaryMinecraftHome = temporaryMinecraftHome,
            variables = variables,
            onProcessor = onProcessor
        )
    }

    private suspend fun installLegacy(
        loader: LoaderVersion,
        zip: ZipFile,
        installProfile: JsonObject,
        temporaryMinecraftHome: File,
        minecraftVersion: String,
        metadataTarget: File,
        onLibraryProgress: suspend (BatchProgress) -> Unit
    ) {
        val libraryRoot = File(temporaryMinecraftHome, "libraries")
        if (!installProfile.has("install")) {
            val metadataPath = installProfile.stringOrNull("json")?.trimStart('/')
                ?: throw IOException("Legacy Forge installer has no version metadata path")
            val metadata = readObject(zip, metadataPath).apply {
                addProperty("inheritsFrom", stringOrNull("inheritsFrom") ?: minecraftVersion)
            }
            metadataTarget.parentFile?.mkdirs()
            metadataTarget.writeText(Tools.GLOBAL_GSON.toJson(metadata))
            extractZipPrefix(zip, "maven/", libraryRoot)
            return
        }

        val install = installProfile["install"].asJsonObjectOrNull()
            ?: throw IOException("Legacy Forge installer has no install descriptor")
        val descriptor = install.stringOrNull("path")
            ?: throw IOException("Legacy Forge installer has no library descriptor")
        val target = File(libraryRoot, descriptorToPath(descriptor))
        extractEntry(zip, install.stringOrNull("filePath") ?: throw IOException("Legacy Forge archive path is missing"), target)

        val metadata = installProfile["versionInfo"].asJsonObjectOrNull()?.deepCopy()
            ?: throw IOException("Legacy Forge installer has no version information")
        if (!metadata.has("inheritsFrom")) metadata.addProperty("inheritsFrom", minecraftVersion)
        metadata["libraries"].asJsonArrayOrNull()?.removeAll { library ->
            library.isJsonObject && library.asJsonObject.stringOrNull("name") == descriptor
        }
        metadataTarget.parentFile?.mkdirs()
        metadataTarget.writeText(Tools.GLOBAL_GSON.toJson(metadata))

        val downloads = linkedMapOf<String, VersionDownloadTask>()
        collectLibraries(metadata).forEach { library ->
            libraryDownloadTask(library, libraryRoot, loader.kind)?.let { task ->
                downloads.putIfAbsent(task.target.absolutePath, task)
            }
        }
        runVersionDownloadBatch(downloads.values.toList(), maxConnections = 8, onProgress = onLibraryProgress)
    }

    private suspend fun runProcessors(
        context: android.content.Context,
        processors: List<ForgeProcessor>,
        temporaryMinecraftHome: File,
        variables: Map<String, String>,
        onProcessor: (String, Int, Int) -> Unit
    ) {
        val runnable = processors.mapNotNull { processor ->
            if (!processor.appliesToClient()) return@mapNotNull null
            val outputs = processor.outputs.orEmpty().map { (path, expectedHash) ->
                val resolved = parseLiteral(temporaryMinecraftHome, path, variables)
                    ?: throw IOException("Invalid processor output: $path")
                outputFile(temporaryMinecraftHome, resolved) to parseLiteral(temporaryMinecraftHome, expectedHash, variables).orEmpty()
            }
            val needsRun = outputs.isEmpty() || outputs.any { (file, expectedHash) ->
                !file.isFile || expectedHash.isBlank() || !calculateSha1(file).equals(expectedHash, ignoreCase = true)
            }
            if (!needsRun) return@mapNotNull null

            val libraryRoot = File(temporaryMinecraftHome, "libraries")
            val processorJar = File(libraryRoot, descriptorToPath(processor.jar))
            if (!processorJar.isFile) throw FileNotFoundException("Processor JAR is missing: ${processorJar.absolutePath}")
            val mainClass = JarFile(processorJar).use { archive ->
                archive.manifest?.mainAttributes?.getValue(Attributes.Name.MAIN_CLASS)
            }?.takeIf { it.isNotBlank() } ?: throw IOException("Processor has no Main-Class: ${processor.jar}")
            val classpath = (processor.classpath.orEmpty() + processor.jar).map { descriptor ->
                File(libraryRoot, descriptorToPath(descriptor)).also { file ->
                    if (!file.isFile) throw FileNotFoundException("Processor dependency is missing: ${file.absolutePath}")
                }.absolutePath
            }
            val arguments = buildList {
                add("-cp")
                add(classpath.joinToString(File.pathSeparator))
                add(mainClass)
                addAll(processor.args.orEmpty().map { argument ->
                    parseLiteral(temporaryMinecraftHome, argument, variables)
                        ?: throw IOException("Invalid processor argument: $argument")
                })
            }
            ProcessorCommand(processor.jar, arguments, outputs)
        }

        runnable.forEachIndexed { index, command ->
            currentCoroutineContext().ensureActive()
            onProcessor(command.processor, index + 1, runnable.size)
            InstallerJvmRunner.runWithFallback(context, temporaryMinecraftHome.parentFile ?: temporaryMinecraftHome, command.arguments)
            command.outputs.forEach { (file, expectedHash) ->
                if (!file.isFile) throw FileNotFoundException("Processor output is missing: ${file.absolutePath}")
                if (expectedHash.isNotBlank() && !calculateSha1(file).equals(expectedHash, ignoreCase = true)) {
                    file.delete()
                    throw IOException("Processor output checksum mismatch: ${file.absolutePath}")
                }
            }
        }
    }

    private suspend fun scheduleMojangMappings(
        installProfile: JsonObject,
        temporaryMinecraftHome: File,
        variables: Map<String, String>,
        downloads: MutableMap<String, VersionDownloadTask>
    ) {
        val processors = installProfile["processors"].asJsonArrayOrNull().orEmpty().mapNotNull { element ->
            runCatching { Tools.GLOBAL_GSON.fromJson(element, ForgeProcessor::class.java) }.getOrNull()
        }
        processors.filter { it.appliesToClient() }.forEach { processor ->
            val options = parseOptions(temporaryMinecraftHome, processor.args.orEmpty(), variables)
            if (options["task"] != "DOWNLOAD_MOJMAPS" || options["side"] != "client") return@forEach
            val version = options["version"] ?: return@forEach
            val output = options["output"] ?: return@forEach
            val manifestEntry = VersionCatalog.find(version)
                ?: throw IOException("Minecraft metadata is unavailable for mappings: $version")
            val manifest = Tools.GLOBAL_GSON.fromJson(
                VersionCatalog.requestText(MirrorPolicy.candidates(manifestEntry.url)),
                GameManifest::class.java
            ) ?: throw IOException("Minecraft metadata is empty for mappings: $version")
            val mappings = manifest.downloads?.clientMappings
                ?: throw IOException("Client mappings are unavailable for Minecraft $version")
            val target = outputFile(temporaryMinecraftHome, output)
            val task = VersionDownloadTask(
                urls = MirrorPolicy.candidates(mappings.url),
                target = target,
                sha1 = mappings.sha1,
                size = mappings.size
            )
            downloads.putIfAbsent(target.absolutePath, task)
        }
    }

    private fun resolveInstallerData(
        zip: ZipFile,
        installProfile: JsonObject,
        temporaryMinecraftHome: File,
        installer: File,
        minecraftVersion: String
    ): MutableMap<String, String> {
        val cacheDirectory = File(temporaryMinecraftHome, ".pgw-installer-data").apply { mkdirs() }
        val variables = linkedMapOf<String, String>()
        installProfile["data"]?.asJsonObject?.entrySet()?.forEach { (name, entry) ->
            val clientValue = entry.asJsonObjectOrNull()?.get("client")?.takeIf { it.isJsonPrimitive }?.asString ?: return@forEach
            parseLiteral(temporaryMinecraftHome, clientValue, variables) { raw ->
                val entryName = raw.trimStart('/', '\\').replace('\\', '/')
                val file = File(cacheDirectory, name)
                extractEntry(zip, entryName, file)
                file.absolutePath
            }?.let { variables[name] = it }
        }
        val vanillaJar = File(temporaryMinecraftHome, "versions/$minecraftVersion/$minecraftVersion.jar")
        variables["SIDE"] = "client"
        variables["MINECRAFT_JAR"] = vanillaJar.absolutePath
        variables["MINECRAFT_VERSION"] = vanillaJar.absolutePath
        variables["ROOT"] = temporaryMinecraftHome.absolutePath
        variables["INSTALLER"] = installer.absolutePath
        variables["LIBRARY_DIR"] = File(temporaryMinecraftHome, "libraries").absolutePath
        return variables
    }

    private fun extractEmbeddedLibraries(zip: ZipFile, installProfile: JsonObject, libraryRoot: File) {
        collectLibraries(installProfile).forEach { library ->
            val path = embeddedLibraryPath(library) ?: return@forEach
            val entry = zip.getEntry("maven/$path") ?: return@forEach
            extractEntry(zip, entry.name, File(libraryRoot, path))
        }
        installProfile.stringOrNull("path")?.let { descriptor ->
            val path = runCatching { descriptorToPath(descriptor) }.getOrNull() ?: return@let
            zip.getEntry("maven/$path")?.let { entry -> extractEntry(zip, entry.name, File(libraryRoot, path)) }
        }
    }

    private fun extractZipPrefix(zip: ZipFile, prefix: String, targetRoot: File) {
        zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(prefix) }.forEach { entry ->
            val path = entry.name.removePrefix(prefix)
            extractEntry(zip, entry.name, File(targetRoot, path))
        }
    }

    private fun extractEntry(zip: ZipFile, entryName: String, target: File) {
        val entry = zip.getEntry(entryName) ?: throw FileNotFoundException("Installer entry is missing: $entryName")
        target.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input -> target.outputStream().use(input::copyTo) }
    }

    private fun collectLibraries(vararg objects: JsonObject): List<JsonObject> = buildList {
        objects.forEach { source ->
            source["libraries"].asJsonArrayOrNull().orEmpty().forEach { entry ->
                entry.asJsonObjectOrNull()?.let(::add)
            }
        }
    }.distinctBy { it.stringOrNull("name") ?: it.toString() }

    private fun libraryDownloadTask(library: JsonObject, libraryRoot: File, kind: LoaderKind): VersionDownloadTask? {
        val artifact = library["downloads"]?.asJsonObjectOrNull()?.get("artifact")?.asJsonObjectOrNull()
        val descriptor = library.stringOrNull("name")
        val path = artifact?.stringOrNull("path") ?: descriptor?.let(::descriptorToPath) ?: return null
        val target = File(libraryRoot, path)
        val artifactUrl = artifact?.stringOrNull("url")?.takeIf { it.isNotBlank() }
        val libraryUrl = library.stringOrNull("url")?.takeIf { it.isNotBlank() }
        val fallback = when (kind) {
            LoaderKind.NEOFORGE -> NEOFORGE_MAVEN
            else -> FORGE_MAVEN
        }
        val url = artifactUrl ?: libraryUrl?.trimEnd('/')?.plus("/$path") ?: "$fallback/$path"
        return VersionDownloadTask(
            urls = MirrorPolicy.candidates(url),
            target = target,
            sha1 = artifact?.stringOrNull("sha1") ?: library.stringOrNull("sha1"),
            size = artifact?.longOrNull("size") ?: library.longOrNull("size") ?: -1L
        )
    }

    private fun parseOptions(baseDir: File, args: List<String>, variables: Map<String, String>): Map<String, String> {
        val options = linkedMapOf<String, String>()
        var key: String? = null
        args.forEach { argument ->
            if (argument.startsWith("--")) {
                key?.let { options[it] = "" }
                key = argument.removePrefix("--")
            } else if (key != null) {
                options[key!!] = parseLiteral(baseDir, argument, variables)
                    ?: throw IOException("Invalid processor option: $argument")
                key = null
            }
        }
        key?.let { options[it] = "" }
        return options
    }

    private fun parseLiteral(
        baseDir: File,
        literal: String,
        variables: Map<String, String>,
        plainConverter: (String) -> String = { it }
    ): String? = when {
        literal.startsWith('{') && literal.endsWith('}') -> variables[literal.removeSurrounding("{", "}")]
        literal.startsWith('\'') && literal.endsWith('\'') -> literal.removeSurrounding("'", "'")
        literal.startsWith('[') && literal.endsWith(']') -> File(baseDir, "libraries/${descriptorToPath(literal.removeSurrounding("[", "]"))}").absolutePath
        else -> plainConverter(replaceTokens(variables, literal))
    }

    private fun replaceTokens(tokens: Map<String, String>, value: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < value.length) {
            when (val character = value[index]) {
                '\\' -> {
                    require(index + 1 < value.length) { "Invalid escaped token: $value" }
                    result.append(value[++index])
                }
                '{', '\'' -> {
                    val closing = if (character == '{') '}' else '\''
                    val end = value.indexOf(closing, index + 1)
                    require(end >= 0) { "Unclosed token: $value" }
                    val token = value.substring(index + 1, end)
                    result.append(if (character == '{') tokens[token] ?: throw IllegalArgumentException("Missing installer token: $token") else token)
                    index = end
                }
                else -> result.append(character)
            }
            index++
        }
        return result.toString()
    }

    private fun descriptorToPath(descriptor: String): String {
        val parts = descriptor.split(':', limit = 4).toMutableList()
        require(parts.size == 3 || parts.size == 4) { "Malformed library descriptor: $descriptor" }
        val last = parts.lastIndex
        val extensionParts = parts[last].split('@', limit = 2)
        parts[last] = extensionParts[0]
        val extension = extensionParts.getOrElse(1) { "jar" }
        val classifier = parts.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { "-$it" }.orEmpty()
        val group = parts[0].replace('.', '/')
        return "$group/${parts[1]}/${parts[2]}/${parts[1]}-${parts[2]}$classifier.$extension"
    }

    internal fun embeddedLibraryPath(library: JsonObject): String? {
        val artifact = library["downloads"].asJsonObjectOrNull()?.get("artifact").asJsonObjectOrNull()
        return artifact?.stringOrNull("path")
            ?: library.stringOrNull("name")?.let { descriptor -> runCatching { descriptorToPath(descriptor) }.getOrNull() }
    }

    private fun outputFile(baseDir: File, value: String): File = File(value).let { file ->
        if (file.isAbsolute) file else File(baseDir, value)
    }

    private fun readObject(zip: ZipFile, entryName: String): JsonObject {
        val entry = zip.getEntry(entryName) ?: throw FileNotFoundException("Installer metadata is missing: $entryName")
        return zip.getInputStream(entry).bufferedReader().use { reader ->
            Tools.GLOBAL_GSON.fromJson(reader, JsonObject::class.java)
                ?: throw IOException("Installer metadata is empty: $entryName")
        }
    }

    private fun prepareLauncherProfiles(temporaryMinecraftHome: File) {
        val profiles = File(temporaryMinecraftHome, "launcher_profiles.json")
        profiles.parentFile?.mkdirs()
        if (!profiles.exists()) profiles.writeText(DEFAULT_LAUNCHER_PROFILES)
    }

    private data class ProcessorCommand(
        val processor: String,
        val arguments: List<String>,
        val outputs: List<Pair<File, String>>
    )
}

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonElement?.asJsonArrayOrNull(): JsonArray? = this?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.stringOrNull(name: String): String? = this[name]
    ?.takeUnless { it.isJsonNull }
    ?.takeIf { it.isJsonPrimitive }
    ?.asString

private fun JsonObject.longOrNull(name: String): Long? = this[name]
    ?.takeUnless { it.isJsonNull }
    ?.takeIf { it.isJsonPrimitive }
    ?.let { runCatching { it.asLong }.getOrNull() }

private fun JsonArray?.orEmpty(): List<JsonElement> = this?.toList() ?: emptyList()
