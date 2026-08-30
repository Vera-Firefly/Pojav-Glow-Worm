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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences
import net.kdt.pojavlaunch.firefly.profiles.ProfileIconIds
import net.kdt.pojavlaunch.firefly.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.firefly.version.download.MinecraftDownloadMode
import net.kdt.pojavlaunch.firefly.version.download.MinecraftFileDownloader
import net.kdt.pojavlaunch.firefly.version.download.VersionDownloadTask
import net.kdt.pojavlaunch.firefly.version.download.engine.BatchProgress
import net.kdt.pojavlaunch.firefly.version.download.runVersionDownloadBatch
import net.kdt.pojavlaunch.firefly.version.io.calculateSha1
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class AddonSelection(
    val forge: LoaderVersion? = null,
    val neoForge: LoaderVersion? = null,
    val fabric: LoaderVersion? = null,
    val quilt: LoaderVersion? = null,
    val optiFine: LoaderVersion? = null,
    val fabricApi: ModrinthApiVersion? = null,
    val quiltedFabricApi: ModrinthApiVersion? = null
) {
    fun primary(): LoaderVersion? = listOfNotNull(forge, neoForge, fabric, quilt, optiFine).singleOrNull()
        ?: forge?.takeIf { optiFine != null }

    fun hasForgeAndOptiFine(): Boolean = forge != null && optiFine != null
}

data class VersionInstallRequest(
    val minecraftVersion: String,
    val targetVersionName: String,
    val addons: AddonSelection
)

enum class VersionInstallStage {
    IDLE,
    PREPARING,
    DOWNLOADING_MINECRAFT,
    DOWNLOADING_ADDON,
    RUNNING_INSTALLER,
    VERIFYING,
    COMMITTING,
    COMPLETED,
    CANCELLED,
    FAILED
}

enum class VersionInstallStep {
    CLEAR_CACHE,
    MINECRAFT,
    LOADER_MAIN_FILE,
    LOADER_LIBRARIES,
    API_MAIN_FILE,
    INSTALL_FILES
}

enum class VersionInstallStepStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    SKIPPED
}

data class VersionInstallPlan(
    val minecraftVersion: String,
    val loaderName: String? = null,
    val apiName: String? = null
)

data class VersionInstallStepProgress(
    val step: VersionInstallStep,
    val status: VersionInstallStepStatus = VersionInstallStepStatus.PENDING,
    val downloadedFiles: Int = 0,
    val totalFiles: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSecond: Long = 0,
    val indeterminate: Boolean = false
)

data class VersionInstallProgress(
    val stage: VersionInstallStage = VersionInstallStage.IDLE,
    val operation: String = "",
    val downloadedFiles: Int = 0,
    val totalFiles: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSecond: Long = 0,
    val error: Throwable? = null,
    val installedVersion: String? = null,
    val plan: VersionInstallPlan? = null,
    val steps: List<VersionInstallStepProgress> = emptyList()
) {
    val indeterminate: Boolean
        get() = stage in setOf(
            VersionInstallStage.PREPARING,
            VersionInstallStage.RUNNING_INSTALLER,
            VersionInstallStage.VERIFYING,
            VersionInstallStage.COMMITTING
        )
}

object VersionInstallRules {
    fun defaultProfileIcon(addons: AddonSelection): String = when {
        addons.optiFine != null -> ProfileIconIds.OPTIFINE
        addons.forge != null -> ProfileIconIds.FORGE
        addons.neoForge != null -> ProfileIconIds.NEOFORGE
        addons.fabric != null -> ProfileIconIds.FABRIC
        addons.quilt != null -> ProfileIconIds.QUILT
        else -> ProfileIconIds.MINECRAFT
    }

    fun generatedName(minecraftVersion: String, addons: AddonSelection): String {
        val suffix = when {
            addons.hasForgeAndOptiFine() -> "Forge ${addons.forge!!.loaderVersion}-OptiFine ${addons.optiFine!!.loaderVersion}"
            addons.forge != null -> "Forge ${addons.forge.loaderVersion}"
            addons.neoForge != null -> "NeoForge ${addons.neoForge.loaderVersion}"
            addons.fabric != null -> "Fabric ${addons.fabric.loaderVersion}"
            addons.quilt != null -> "Quilt ${addons.quilt.loaderVersion}"
            addons.optiFine != null -> "OptiFine ${addons.optiFine.loaderVersion}"
            else -> return minecraftVersion
        }
        return "$minecraftVersion $suffix"
    }

    fun validate(request: VersionInstallRequest) {
        val value = request.targetVersionName
        require(VersionIsolation.isDirectoryName(value)) { "Invalid version name" }
        val selections = request.addons
        val primary = listOfNotNull(selections.forge, selections.neoForge, selections.fabric, selections.quilt, selections.optiFine)
        require(primary.size <= 1 || selections.hasForgeAndOptiFine() && primary.size == 2) {
            "Only Forge and OptiFine can be combined"
        }
        require(selections.fabricApi == null || selections.fabric != null) { "Fabric API requires Fabric" }
        require(selections.quiltedFabricApi == null || selections.quilt != null) { "Quilted Fabric API requires Quilt" }
        require(request.targetVersionName != request.minecraftVersion || primary.isEmpty()) {
            "A loader instance must not use the Minecraft version name"
        }
    }
}

/**
 * Holds one activity's installation work. The target version is touched only by commit(), after
 * all downloads, loader output, and version metadata have passed their own completion steps.
 */
class VersionInstallController(private val appContext: Context) : ViewModel() {
    private val _progress = MutableStateFlow(VersionInstallProgress())
    val progress: StateFlow<VersionInstallProgress> = _progress.asStateFlow()
    private var installJob: Job? = null

    fun install(request: VersionInstallRequest) {
        check(installJob?.isActive != true) { "A version installation is already running" }
        check(INSTALLATION_IN_PROGRESS.compareAndSet(false, true)) { "A version installation is already running" }
        try {
            VersionInstallRules.validate(request)
            check(!VersionPaths.versionDirectory(request.targetVersionName).exists()) {
                "Version already exists: ${request.targetVersionName}"
            }
            installJob = viewModelScope.launch {
                ProgressKeeper.submitProgress(PROGRESS_RECORD, 0, R.string.version_install_progress_starting)
                try {
                    val installed = withContext(Dispatchers.IO) { VersionInstallTransaction(appContext, request, ::report).run() }
                    PgwVersionRepository.ensureInstalledVersion(
                        installed,
                        VersionInstallRules.defaultProfileIcon(request.addons)
                    )
                    _progress.value = VersionInstallProgress(
                        stage = VersionInstallStage.COMPLETED,
                        operation = installed,
                        installedVersion = installed
                    )
                } catch (cancelled: CancellationException) {
                    _progress.value = VersionInstallProgress(VersionInstallStage.CANCELLED)
                    throw cancelled
                } catch (error: Throwable) {
                    _progress.value = VersionInstallProgress(VersionInstallStage.FAILED, error = error)
                } finally {
                    INSTALLATION_IN_PROGRESS.set(false)
                    ProgressKeeper.submitProgress(PROGRESS_RECORD, -1, -1)
                }
            }
        } catch (error: Throwable) {
            INSTALLATION_IN_PROGRESS.set(false)
            throw error
        }
    }

    fun cancel() {
        installJob?.cancel()
    }

    fun clearResult() {
        if (installJob?.isActive != true) _progress.value = VersionInstallProgress()
    }

    private fun report(progress: VersionInstallProgress) {
        _progress.value = progress
        val percent = if (progress.totalFiles > 0) progress.downloadedFiles * 100 / progress.totalFiles else 0
        ProgressKeeper.submitProgress(
            PROGRESS_RECORD,
            percent,
            R.string.version_install_progress_downloading,
            progress.downloadedFiles,
            progress.totalFiles
        )
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(VersionInstallController::class.java))
            return VersionInstallController(appContext) as T
        }
    }

    companion object {
        const val PROGRESS_RECORD = "version_install_transaction"
        private val INSTALLATION_IN_PROGRESS = AtomicBoolean(false)
    }
}

private class VersionInstallTransaction(
    private val context: Context,
    private val request: VersionInstallRequest,
    private val report: (VersionInstallProgress) -> Unit
) {
    private val transactionId = UUID.randomUUID().toString()
    private val stageId = "pgw-stage-$transactionId"
    private val workspace = File(context.cacheDir, "version-installer/$transactionId")
    private val stageDirectory = VersionPaths.versionDirectory(stageId)
    private val isolateGameFiles = LauncherPreferences.PREF_VERSION_ISOLATION
    private val baseVersionExisted = VersionPaths.versionDirectory(request.minecraftVersion).exists()
    private var baseVersionCreated = false
    private var backupDirectory: File? = null
    private val committedModFiles = mutableListOf<CommittedModFile>()
    private var targetDirectoryCommitted = false
    private var committed = false
    private val plan = VersionInstallPlan(
        minecraftVersion = request.minecraftVersion,
        loaderName = request.addons.primary()?.let(::loaderDisplayName),
        apiName = selectedApi()?.let(::apiDisplayName)
    )
    private val steps = VersionInstallStep.entries.associateWith { VersionInstallStepProgress(it) }.toMutableMap()
    private var currentStage = VersionInstallStage.IDLE
    private var currentOperation = ""
    private var currentStep: VersionInstallStep? = null

    suspend fun run(): String {
        try {
            check(!VersionPaths.versionDirectory(request.targetVersionName).exists()) {
                "Version already exists: ${request.targetVersionName}"
            }
            beginStep(VersionInstallStep.CLEAR_CACHE, VersionInstallStage.PREPARING)
            workspace.deleteRecursively()
            workspace.mkdirs()
            removeStaleTransactionDirectories()
            stageDirectory.deleteRecursively()
            if (!stageDirectory.mkdirs() && !stageDirectory.isDirectory) {
                throw IOException("Unable to create version staging directory: ${stageDirectory.absolutePath}")
            }
            if (!stageDirectory.canWrite()) {
                throw IOException("Version staging directory is not writable: ${stageDirectory.absolutePath}")
            }
            completeStep(VersionInstallStep.CLEAR_CACHE)

            val addons = request.addons
            if (addons.primary() == null) {
                skipStep(VersionInstallStep.LOADER_MAIN_FILE)
                skipStep(VersionInstallStep.LOADER_LIBRARIES)
            }
            if (selectedApi() == null) skipStep(VersionInstallStep.API_MAIN_FILE)

            beginStep(VersionInstallStep.MINECRAFT, VersionInstallStage.DOWNLOADING_MINECRAFT)
            downloadMinecraft()
            completeStep(VersionInstallStep.MINECRAFT)
            when {
                addons.hasForgeAndOptiFine() -> installForge(addons.forge!!).also { installOptiFineAsForgeMod(addons.optiFine!!) }
                addons.forge != null -> installForge(addons.forge)
                addons.neoForge != null -> installForge(addons.neoForge)
                addons.fabric != null -> installFabricLike(addons.fabric)
                addons.quilt != null -> installFabricLike(addons.quilt)
                addons.optiFine != null -> installStandaloneOptiFine(addons.optiFine)
            }

            selectedApi()?.let { api ->
                beginStep(VersionInstallStep.API_MAIN_FILE, VersionInstallStage.DOWNLOADING_ADDON)
                stageApi(api)
                completeStep(VersionInstallStep.API_MAIN_FILE)
            }

            beginStep(VersionInstallStep.INSTALL_FILES, VersionInstallStage.VERIFYING)
            verifyStage()
            setActiveStage(VersionInstallStep.INSTALL_FILES, VersionInstallStage.COMMITTING)
            commit()
            completeStep(VersionInstallStep.INSTALL_FILES)
            committed = true
            return request.targetVersionName
        } finally {
            if (!committed) restoreBackup()
            if (!committed) rollbackCommittedModFiles()
            // A loader manifest still inherits this local vanilla parent after the staged instance
            // is committed. A standalone custom-named vanilla instance does not need the temporary parent.
            if (baseVersionCreated && request.targetVersionName != request.minecraftVersion &&
                (!committed || request.addons.primary() == null)
            ) {
                VersionPaths.versionDirectory(request.minecraftVersion).deleteRecursively()
            }
            cleanup()
        }
    }

    private suspend fun downloadMinecraft() {
        MinecraftFileDownloader(request.minecraftVersion).run { batch ->
            reportBatch(VersionInstallStep.MINECRAFT, VersionInstallStage.DOWNLOADING_MINECRAFT, batch)
        }
        baseVersionCreated = !baseVersionExisted && VersionPaths.versionDirectory(request.minecraftVersion).isDirectory
        stageBaseVersion()
        val metadata = stageJson()
        if (!metadata.isFile) {
            throw IOException("Minecraft download did not create version metadata: ${metadata.absolutePath}")
        }
        val root = readJson(metadata)
        root.addProperty("id", stageId)
        stageJson().writeText(Tools.GLOBAL_GSON.toJson(root))
        ensureActive()
    }

    private suspend fun installFabricLike(loader: LoaderVersion) {
        beginStep(VersionInstallStep.LOADER_MAIN_FILE, VersionInstallStage.DOWNLOADING_ADDON)
        val url = loader.downloadUrl ?: throw IOException("Loader metadata URL is unavailable")
        val json = VersionCatalog.requestText(MirrorPolicy.candidates(url))
        stageJson().writeText(json)
        val root = readJson(stageJson())
        root.addProperty("id", stageId)
        stageJson().writeText(Tools.GLOBAL_GSON.toJson(root))
        completeStep(VersionInstallStep.LOADER_MAIN_FILE)
        beginStep(VersionInstallStep.LOADER_LIBRARIES, VersionInstallStage.VERIFYING)
        MinecraftFileDownloader(
            request.minecraftVersion,
            stageId,
            verifyIntegrity = true,
            mode = MinecraftDownloadMode.VERIFY_AND_REPAIR
        ).runFromInstalledManifest(stageJson()) { batch ->
            reportBatch(VersionInstallStep.LOADER_LIBRARIES, VersionInstallStage.VERIFYING, batch)
        }
        completeStep(VersionInstallStep.LOADER_LIBRARIES)
    }

    private suspend fun installForge(loader: LoaderVersion) {
        beginStep(VersionInstallStep.LOADER_MAIN_FILE, VersionInstallStage.DOWNLOADING_ADDON)
        val url = loader.downloadUrl ?: throw IOException("Loader installer URL is unavailable")
        val installer = File(workspace, "${loader.kind.name.lowercase()}-installer.jar")
        runVersionDownloadBatch(
            listOf(VersionDownloadTask(MirrorPolicy.candidates(url), installer, null)),
            maxConnections = 2
        ) { batch -> reportBatch(VersionInstallStep.LOADER_MAIN_FILE, VersionInstallStage.DOWNLOADING_ADDON, batch) }
        ensureActive()
        completeStep(VersionInstallStep.LOADER_MAIN_FILE)

        beginStep(VersionInstallStep.LOADER_LIBRARIES, VersionInstallStage.RUNNING_INSTALLER)
        val temporaryGameHome = File(workspace, "loader-game/.minecraft")
        val temporaryVersion = File(temporaryGameHome, "versions/${request.minecraftVersion}")
        temporaryVersion.mkdirs()
        val sourceJson = stageJson()
        val vanillaRoot = readJson(sourceJson)
        vanillaRoot.addProperty("id", request.minecraftVersion)
        File(temporaryVersion, "${request.minecraftVersion}.json").writeText(Tools.GLOBAL_GSON.toJson(vanillaRoot))
        VersionPaths.versionJar(stageId).copyTo(File(temporaryVersion, "${request.minecraftVersion}.jar"), overwrite = true)
        ForgeLikeInstaller.install(
            context = context,
            loader = loader,
            installer = installer,
            temporaryMinecraftHome = temporaryGameHome,
            minecraftVersion = request.minecraftVersion,
            metadataTarget = stageJson(),
            onLibraryProgress = { batch ->
                reportBatch(VersionInstallStep.LOADER_LIBRARIES, VersionInstallStage.VERIFYING, batch)
            },
            onProcessor = { processor, index, total ->
                reportProcessor(processor, index, total)
            }
        )
        ensureActive()
        copyDirectory(File(temporaryGameHome, "libraries"), File(workspace, "libraries"))
        val root = readJson(stageJson())
        root.addProperty("id", stageId)
        stageJson().writeText(Tools.GLOBAL_GSON.toJson(root))
        completeStep(VersionInstallStep.LOADER_LIBRARIES)
    }

    private suspend fun installOptiFineAsForgeMod(loader: LoaderVersion) {
        val target = File(workspace, "mods/${optiFineArchiveFileName(loader)}")
        downloadOptiFine(loader, target)
    }

    private suspend fun installStandaloneOptiFine(loader: LoaderVersion) {
        val archive = File(workspace, optiFineArchiveFileName(loader))
        downloadOptiFine(loader, archive)
        beginStep(VersionInstallStep.LOADER_LIBRARIES, VersionInstallStage.VERIFYING)
        val target = File(VersionPaths.libraries(), "optifine/OptiFine/${loader.loaderVersion}/OptiFine-${loader.loaderVersion}.jar")
        val pendingLibrary = File(workspace, "libraries/${target.relativeTo(VersionPaths.libraries()).path}")
        pendingLibrary.parentFile?.mkdirs()
        archive.copyTo(pendingLibrary, overwrite = true)

        val root = JsonObject().apply {
            addProperty("inheritsFrom", request.minecraftVersion)
            addProperty("type", "release")
            readJson(stageJson())["mainClass"]?.let { add("mainClass", it) }
        }
        root.addProperty("id", stageId)
        val libraries = root["libraries"]?.asJsonArray ?: JsonArray().also { root.add("libraries", it) }
        if (libraries.none { it.isJsonObject && it.asJsonObject["name"]?.asString == "optifine:OptiFine:${loader.loaderVersion}" }) {
            libraries.add(JsonObject().apply { addProperty("name", "optifine:OptiFine:${loader.loaderVersion}") })
        }
        if (root.has("minecraftArguments")) {
            val old = root["minecraftArguments"].asString
            if (!old.contains("optifine.OptiFineTweaker")) root.addProperty("minecraftArguments", "$old --tweakClass optifine.OptiFineTweaker")
        } else {
            val args = root["arguments"]?.asJsonObject ?: JsonObject().also { root.add("arguments", it) }
            val game = args["game"]?.asJsonArray ?: JsonArray().also { args.add("game", it) }
            if (game.none { it.asString == "optifine.OptiFineTweaker" }) {
                game.add("--tweakClass")
                game.add("optifine.OptiFineTweaker")
            }
        }
        stageJson().writeText(Tools.GLOBAL_GSON.toJson(root))
        completeStep(VersionInstallStep.LOADER_LIBRARIES)
    }

    private suspend fun downloadOptiFine(loader: LoaderVersion, target: File) {
        beginStep(VersionInstallStep.LOADER_MAIN_FILE, VersionInstallStage.DOWNLOADING_ADDON)
        val url = LoaderCatalog.resolveOptiFineDownload(loader)
        runVersionDownloadBatch(
            listOf(VersionDownloadTask(MirrorPolicy.candidates(url), target, null)),
            maxConnections = 2
        ) { batch -> reportBatch(VersionInstallStep.LOADER_MAIN_FILE, VersionInstallStage.DOWNLOADING_ADDON, batch) }
        ensureActive()
        completeStep(VersionInstallStep.LOADER_MAIN_FILE)
    }

    private fun optiFineArchiveFileName(loader: LoaderVersion): String =
        loader.fileName?.takeIf { it.isNotBlank() } ?: "OptiFine-${loader.loaderVersion}.jar"

    private suspend fun stageApi(api: ModrinthApiVersion) {
        val target = File(workspace, "mods/${api.fileName}")
        runVersionDownloadBatch(
            listOf(VersionDownloadTask(MirrorPolicy.modrinthCandidates(api.downloadUrl), target, api.sha1, api.size)),
            maxConnections = 2
        ) { batch -> reportBatch(VersionInstallStep.API_MAIN_FILE, VersionInstallStage.DOWNLOADING_ADDON, batch) }
    }

    private suspend fun verifyStage() {
        MinecraftFileDownloader(
            request.minecraftVersion,
            stageId,
            verifyIntegrity = true,
            mode = MinecraftDownloadMode.VERIFY_AND_REPAIR
        ).runFromInstalledManifest(stageJson()) { batch ->
            reportBatch(VersionInstallStep.INSTALL_FILES, VersionInstallStage.VERIFYING, batch)
        }
        ensureActive()
    }

    private fun commit() {
        val target = VersionPaths.versionDirectory(request.targetVersionName)
        if (target.exists()) {
            val provisionedBaseIsTarget = baseVersionCreated && target.name == request.minecraftVersion
            require(provisionedBaseIsTarget) { "Version already exists: ${request.targetVersionName}" }
            val backup = File(VersionPaths.versions(), ".pgw-backup-$transactionId")
            if (!target.renameTo(backup)) throw IOException("Unable to back up existing version")
            backupDirectory = backup
        }
        if (isolateGameFiles) commitStagedMods(stageDirectory)
        renameStageFiles()
        if (!stageDirectory.renameTo(target)) throw IOException("Unable to commit installed version")
        targetDirectoryCommitted = true
        copyDirectory(File(workspace, "libraries"), VersionPaths.libraries())
        if (!isolateGameFiles) commitStagedMods(VersionPaths.gameHome())
        backupDirectory?.deleteRecursively()
        backupDirectory = null
    }

    private fun renameStageFiles() {
        val json = stageJson()
        val root = readJson(json)
        root.addProperty("id", request.targetVersionName)
        json.writeText(Tools.GLOBAL_GSON.toJson(root))
        val targetJson = File(stageDirectory, "${request.targetVersionName}.json")
        if (!json.renameTo(targetJson)) throw IOException("Unable to prepare version metadata")
        val jar = VersionPaths.versionJar(stageId)
        if (jar.isFile && !jar.renameTo(File(stageDirectory, "${request.targetVersionName}.jar"))) {
            throw IOException("Unable to prepare version client JAR")
        }
    }

    private fun restoreBackup() {
        val target = VersionPaths.versionDirectory(request.targetVersionName)
        if (targetDirectoryCommitted && target.exists()) target.deleteRecursively()
        val backup = backupDirectory ?: return
        if (target.exists()) target.deleteRecursively()
        if (!backup.renameTo(target)) throw IOException("Unable to restore the previous version")
        backupDirectory = null
    }

    private fun cleanup() {
        stageDirectory.deleteRecursively()
        workspace.deleteRecursively()
    }

    private fun removeStaleTransactionDirectories() {
        VersionPaths.versions().listFiles().orEmpty()
            .filter { it.isDirectory && (it.name.startsWith(".pgw-stage-") || it.name.startsWith(".pgw-backup-")) }
            .forEach { it.deleteRecursively() }
    }

    private fun stageJson(): File = VersionPaths.versionJson(stageId)

    private fun stageBaseVersion() {
        val baseMetadata = VersionPaths.versionJson(request.minecraftVersion)
        if (!baseMetadata.isFile) {
            throw IOException("Minecraft download did not create base version metadata: ${baseMetadata.absolutePath}")
        }
        baseMetadata.copyTo(stageJson(), overwrite = true)
        val baseClient = VersionPaths.versionJar(request.minecraftVersion)
        if (baseClient.isFile) {
            baseClient.copyTo(VersionPaths.versionJar(stageId), overwrite = true)
        }
    }

    private fun readJson(file: File): JsonObject = file.reader().use {
        Tools.GLOBAL_GSON.fromJson(it, JsonObject::class.java) ?: throw IOException("Version metadata is empty")
    }

    private fun copyDirectory(from: File, to: File) {
        if (!from.isDirectory) return
        from.walkTopDown().forEach { source ->
            val target = File(to, source.relativeTo(from).path)
            when {
                source.isDirectory -> target.mkdirs()
                source.isFile -> {
                    target.parentFile?.mkdirs()
                    source.copyTo(target, overwrite = true)
                }
            }
        }
    }

    private fun commitStagedMods(gameDirectory: File) {
        val sourceMods = File(workspace, "mods")
        if (!sourceMods.isDirectory) return
        val targetMods = File(gameDirectory, "mods")
        sourceMods.walkTopDown().filter(File::isFile).forEach { source ->
            val target = File(targetMods, source.relativeTo(sourceMods).path)
            target.parentFile?.mkdirs()
            val expectedSha1 = selectedApi()?.takeIf { source.name == it.fileName }?.sha1
            val targetMatches = target.isFile && (expectedSha1.isNullOrBlank() ||
                calculateSha1(target).equals(expectedSha1, ignoreCase = true))
            if (!targetMatches) {
                val backup = if (!isolateGameFiles && target.isFile) {
                    File(workspace, "mods-backup/${source.relativeTo(sourceMods).path}").also { backup ->
                        backup.parentFile?.mkdirs()
                        target.copyTo(backup, overwrite = true)
                    }
                } else null
                source.copyTo(target, overwrite = true)
                if (!isolateGameFiles) committedModFiles += CommittedModFile(target, backup)
            }
        }
    }

    private fun rollbackCommittedModFiles() {
        committedModFiles.asReversed().forEach { committedFile ->
            val backup = committedFile.backup
            if (backup?.isFile == true) {
                backup.copyTo(committedFile.target, overwrite = true)
            } else {
                committedFile.target.delete()
            }
        }
        committedModFiles.clear()
    }

    private data class CommittedModFile(val target: File, val backup: File?)

    private fun beginStep(step: VersionInstallStep, stage: VersionInstallStage) {
        currentStage = stage
        currentStep = step
        currentOperation = step.name
        steps[step] = VersionInstallStepProgress(
            step = step,
            status = VersionInstallStepStatus.ACTIVE,
            indeterminate = stage in setOf(
                VersionInstallStage.PREPARING,
                VersionInstallStage.RUNNING_INSTALLER,
                VersionInstallStage.VERIFYING,
                VersionInstallStage.COMMITTING
            )
        )
        publish()
    }

    private fun setActiveStage(step: VersionInstallStep, stage: VersionInstallStage) {
        currentStage = stage
        currentStep = step
        currentOperation = step.name
        steps[step] = steps.getValue(step).copy(
            status = VersionInstallStepStatus.ACTIVE,
            indeterminate = stage in setOf(
                VersionInstallStage.PREPARING,
                VersionInstallStage.RUNNING_INSTALLER,
                VersionInstallStage.VERIFYING,
                VersionInstallStage.COMMITTING
            )
        )
        publish()
    }

    private fun completeStep(step: VersionInstallStep) {
        steps[step] = steps.getValue(step).copy(
            status = VersionInstallStepStatus.COMPLETED,
            indeterminate = false,
            speedBytesPerSecond = 0
        )
        publish()
    }

    private fun skipStep(step: VersionInstallStep) {
        steps[step] = steps.getValue(step).copy(status = VersionInstallStepStatus.SKIPPED)
    }

    private fun reportBatch(step: VersionInstallStep, stage: VersionInstallStage, batch: BatchProgress) {
        currentStage = stage
        currentStep = step
        currentOperation = step.name
        steps[step] = VersionInstallStepProgress(
            step = step,
            status = VersionInstallStepStatus.ACTIVE,
            downloadedFiles = batch.downloadedFiles,
            totalFiles = batch.totalFiles,
            downloadedBytes = batch.downloadedBytes,
            totalBytes = batch.totalBytes,
            speedBytesPerSecond = batch.speedBytesPerSec,
            indeterminate = false
        )
        publish()
    }

    private fun reportProcessor(processor: String, index: Int, total: Int) {
        currentStage = VersionInstallStage.RUNNING_INSTALLER
        currentStep = VersionInstallStep.LOADER_LIBRARIES
        currentOperation = "$processor ($index/$total)"
        steps[VersionInstallStep.LOADER_LIBRARIES] = VersionInstallStepProgress(
            step = VersionInstallStep.LOADER_LIBRARIES,
            status = VersionInstallStepStatus.ACTIVE,
            indeterminate = true
        )
        publish()
    }

    private fun publish() {
        val active = currentStep?.let { steps[it] }
        report(
            VersionInstallProgress(
                stage = currentStage,
                operation = currentOperation,
                downloadedFiles = active?.downloadedFiles ?: 0,
                totalFiles = active?.totalFiles ?: 0,
                downloadedBytes = active?.downloadedBytes ?: 0,
                totalBytes = active?.totalBytes ?: 0,
                speedBytesPerSecond = active?.speedBytesPerSecond ?: 0,
                plan = plan,
                steps = VersionInstallStep.entries.map(steps::getValue)
            )
        )
    }

    private fun selectedApi(): ModrinthApiVersion? = request.addons.fabricApi ?: request.addons.quiltedFabricApi

    private fun loaderDisplayName(loader: LoaderVersion): String = buildString {
        append(loader.kind.name.lowercase().replaceFirstChar { it.uppercaseChar() })
        append(' ')
        append(loader.loaderVersion)
        request.addons.optiFine?.takeIf { request.addons.hasForgeAndOptiFine() }?.let { optiFine ->
            append(" + OptiFine ")
            append(optiFine.loaderVersion)
        }
    }

    private fun apiDisplayName(api: ModrinthApiVersion): String = when {
        request.addons.fabricApi === api -> "Fabric API ${api.version}"
        else -> "Quilted Fabric API ${api.version}"
    }

    private suspend fun ensureActive() {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
    }
}
