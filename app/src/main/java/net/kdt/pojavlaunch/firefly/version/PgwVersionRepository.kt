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

import android.graphics.Bitmap
import android.util.Base64
import com.movtery.ui.subassembly.customprofilepath.ProfilePathHome
import com.movtery.ui.subassembly.customprofilepath.ProfilePathManager
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences
import net.kdt.pojavlaunch.firefly.profiles.ProfileIconIds
import net.kdt.pojavlaunch.firefly.value.launcherprofiles.MinecraftLauncherProfiles
import net.kdt.pojavlaunch.firefly.value.launcherprofiles.MinecraftProfile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.LinkedHashMap

data class PgwVersionDeletionResult(
    val filesDeleted: Int,
    val selectedVersion: String?
)

object PgwVersionRepository {
    private const val STATE_SCHEMA_VERSION = 1
    private const val CONFIG_SCHEMA_VERSION = 1
    private const val PGW_DIRECTORY = ".pgw"
    private const val STATE_FILE = "version-manager.json"
    private const val CONFIG_FILE = "version.config"
    private const val ICON_FILE = "icon.webp"

    @Synchronized
    fun scan(): List<PgwInstalledVersion> {
        migrateLegacyProfilesIfNeeded()
        return LocalVersionManager.scan()
            .map { local -> PgwInstalledVersion(local, readOrCreateConfig(local)) }
            .sortedWith(
                compareByDescending<PgwInstalledVersion> { it.config.pinned }
                    .thenByDescending { it.local.manifest?.releaseTime.orEmpty() }
                    .thenBy { it.id.lowercase() }
            )
    }

    @Synchronized
    fun get(versionId: String): PgwInstalledVersion? {
        migrateLegacyProfilesIfNeeded()
        val local = LocalVersionManager.get(versionId) ?: return null
        return PgwInstalledVersion(local, readOrCreateConfig(local))
    }

    @Synchronized
    fun current(): PgwInstalledVersion? {
        val versions = scan()
        val state = readState()
        val selected = state.currentVersion?.let { id -> versions.firstOrNull { it.id == id && it.valid } }
        if (selected != null) return selected
        val fallback = versions.firstOrNull { it.valid }
        if (state.currentVersion != fallback?.id) {
            state.currentVersion = fallback?.id
            writeState(state)
        }
        return fallback
    }

    @Synchronized
    fun select(versionId: String) {
        val version = get(versionId)
        require(version != null && version.valid) { "Version does not exist or is invalid: $versionId" }
        val state = readState()
        state.currentVersion = versionId
        writeState(state)
    }

    fun currentLaunchProfile(): MinecraftProfile? = current()?.let { it.config.toLaunchProfile(it.id) }

    fun launchProfile(versionId: String): MinecraftProfile? = get(versionId)
        ?.takeIf { it.valid }
        ?.let { it.config.toLaunchProfile(it.id) }

    @Synchronized
    fun updateConfig(versionId: String, update: (PgwVersionConfig) -> Unit): PgwVersionConfig {
        val version = get(versionId) ?: throw IOException("Version does not exist: $versionId")
        update(version.config)
        version.config.schemaVersion = CONFIG_SCHEMA_VERSION
        writeConfig(version.id, version.config)
        return version.config
    }

    @Synchronized
    fun writeCustomIcon(versionId: String, bitmap: Bitmap) {
        val version = get(versionId) ?: throw IOException("Version does not exist: $versionId")
        val target = iconFile(version.id)
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { output ->
            if (!bitmap.compress(PgwVersionIcons.webpFormat(), 60, output)) {
                throw IOException("Unable to write version icon")
            }
        }
        if (version.config.iconId.isBlank()) {
            version.config.iconId = PgwVersionIcons.defaultFor(version.local.kind)
            writeConfig(version.id, version.config)
        }
    }

    fun iconFile(versionId: String): File = File(configDirectory(versionId), ICON_FILE)

    @Synchronized
    fun clearCustomIcon(versionId: String) {
        val version = get(versionId) ?: throw IOException("Version does not exist: $versionId")
        val icon = iconFile(version.id)
        if (icon.exists() && !icon.delete()) throw IOException("Unable to remove version icon")
    }

    @Synchronized
    fun rename(versionId: String, newVersionId: String) {
        validateVersionName(newVersionId)
        val source = VersionPaths.versionDirectory(versionId)
        val target = VersionPaths.versionDirectory(newVersionId)
        require(source.isDirectory) { "Version does not exist: $versionId" }
        require(!target.exists()) { "Version already exists: $newVersionId" }
        if (!source.renameTo(target)) throw IOException("Unable to rename version: $versionId")

        val oldJson = File(target, "$versionId.json")
        val newJson = File(target, "$newVersionId.json")
        val oldJar = File(target, "$versionId.jar")
        val newJar = File(target, "$newVersionId.jar")
        var jsonRenamed = false
        var jarRenamed = false
        try {
            jsonRenamed = renameIfPresent(oldJson, newJson)
            jarRenamed = renameIfPresent(oldJar, newJar)
        } catch (error: Throwable) {
            if (jarRenamed) newJar.renameTo(oldJar)
            if (jsonRenamed) newJson.renameTo(oldJson)
            target.renameTo(source)
            throw error
        }

        val state = readState()
        if (state.currentVersion == versionId) {
            state.currentVersion = newVersionId
            writeState(state)
        }
    }

    @Synchronized
    fun copy(versionId: String, newVersionId: String, copyAllFiles: Boolean) {
        validateVersionName(newVersionId)
        val source = get(versionId) ?: throw IOException("Version does not exist: $versionId")
        val target = VersionPaths.versionDirectory(newVersionId)
        require(!target.exists()) { "Version already exists: $newVersionId" }
        if (!target.mkdirs()) throw IOException("Unable to create version directory: $newVersionId")

        try {
            if (copyAllFiles) {
                source.directory.copyRecursively(target, overwrite = false)
            } else {
                copyIfPresent(File(source.directory, "$versionId.json"), File(target, "$newVersionId.json"))
                copyIfPresent(File(source.directory, "$versionId.jar"), File(target, "$newVersionId.jar"))
            }
            renameIfPresent(File(target, "$versionId.json"), File(target, "$newVersionId.json"))
            renameIfPresent(File(target, "$versionId.jar"), File(target, "$newVersionId.jar"))
            writeConfig(newVersionId, source.config.copyForNewInstance())
        } catch (error: Throwable) {
            target.deleteRecursively()
            throw error
        }
    }

    @Synchronized
    fun delete(versionId: String): PgwVersionDeletionResult {
        val directory = VersionPaths.versionDirectory(versionId)
        require(directory.isDirectory) { "Version does not exist: $versionId" }
        val filesDeleted = directory.walkBottomUp().count { it.isFile }
        if (!directory.deleteRecursively()) throw IOException("Unable to delete version: $versionId")

        val state = readState()
        val selected = if (state.currentVersion == versionId) {
            scanWithoutMigration().firstOrNull { it.valid }?.id
        } else {
            state.currentVersion
        }
        if (state.currentVersion != selected) {
            state.currentVersion = selected
            writeState(state)
        }
        return PgwVersionDeletionResult(filesDeleted, selected)
    }

    @Synchronized
    fun clearUnreferencedAssets(): Int = LocalVersionManager.clearUnreferencedAssets()

    @Synchronized
    fun ensureInstalledVersion(versionId: String, defaultIcon: String) {
        val version = LocalVersionManager.get(versionId) ?: throw IOException("Installed version is unavailable: $versionId")
        val config = readConfig(version.id) ?: PgwVersionConfig(
            schemaVersion = CONFIG_SCHEMA_VERSION,
            iconId = defaultIcon.ifBlank { PgwVersionIcons.defaultFor(version.kind) }
        ).also { writeConfig(version.id, it) }
        if (config.iconId.isBlank()) {
            config.iconId = defaultIcon.ifBlank { PgwVersionIcons.defaultFor(version.kind) }
            writeConfig(version.id, config)
        }
        select(versionId)
    }

    @Synchronized
    fun migrateLegacyProfilesIfNeeded() {
        val state = readState()
        if (state.legacyProfilesMigrated) return

        val profileFile = ProfilePathManager.getCurrentProfile()
        if (!profileFile.isFile) {
            state.legacyProfilesMigrated = true
            state.schemaVersion = STATE_SCHEMA_VERSION
            writeState(state)
            return
        }

        val legacy = readJson(profileFile, MinecraftLauncherProfiles::class.java)
            ?: throw IOException("Unable to read launcher profiles")
        val profiles = legacy.profiles.orEmpty()
        val currentKey = LauncherPreferences.DEFAULT_PREF
            .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "")
            .orEmpty()
        val validProfiles = profiles.entries.filter { (_, profile) ->
            profile.lastVersionId?.let { id -> LocalVersionManager.get(id)?.valid == true } == true
        }
        val grouped = validProfiles.groupBy { it.value.lastVersionId!! }
        val migrated = LinkedHashMap<String, MinecraftProfile>()

        grouped.forEach { (versionId, entries) ->
            val profile = entries.firstOrNull { it.key == currentKey } ?: entries.first()
            migrated[versionId] = profile.value
            if (readConfig(versionId) == null) {
                val local = LocalVersionManager.get(versionId) ?: return@forEach
                val config = PgwVersionConfig(
                    schemaVersion = CONFIG_SCHEMA_VERSION,
                    summary = profile.value.name?.takeIf { it.isNotBlank() && it != versionId }.orEmpty(),
                    iconId = profile.value.icon?.takeUnless { it.startsWith("data:") }
                        ?: PgwVersionIcons.defaultFor(local.kind),
                    runtimeName = Tools.getRuntimeName(profile.value.javaDir).orEmpty(),
                    jvmArgs = profile.value.javaArgs.orEmpty(),
                    rendererName = profile.value.pojavRendererName.orEmpty(),
                    controlFile = profile.value.controlFile.orEmpty(),
                    enableModsCheck = profile.value.enableModsCheck,
                    customGameDir = profile.value.gameDir.orEmpty()
                )
                writeConfig(versionId, config)
                writeLegacyIcon(versionId, profile.value.icon)
            }
        }

        legacy.profiles = LinkedHashMap()
        writeJson(profileFile, legacy)
        val selectedVersion = profiles[currentKey]?.lastVersionId
            ?.takeIf { migrated.containsKey(it) }
            ?: migrated.keys.firstOrNull()
        state.currentVersion = selectedVersion
        state.legacyProfilesMigrated = true
        state.schemaVersion = STATE_SCHEMA_VERSION
        writeState(state)
    }

    private fun scanWithoutMigration(): List<PgwInstalledVersion> = LocalVersionManager.scan()
        .map { PgwInstalledVersion(it, readOrCreateConfig(it)) }
        .sortedWith(
            compareByDescending<PgwInstalledVersion> { it.config.pinned }
                .thenByDescending { it.local.manifest?.releaseTime.orEmpty() }
                .thenBy { it.id.lowercase() }
        )

    private fun readOrCreateConfig(local: LocalVersion): PgwVersionConfig = readConfig(local.id)
        ?: PgwVersionConfig(
            schemaVersion = CONFIG_SCHEMA_VERSION,
            iconId = PgwVersionIcons.defaultFor(local.kind)
        ).also { writeConfig(local.id, it) }

    private fun configDirectory(versionId: String): File = File(VersionPaths.versionDirectory(versionId), PGW_DIRECTORY)

    private fun configFile(versionId: String): File = File(configDirectory(versionId), CONFIG_FILE)

    private fun stateFile(): File = File(File(ProfilePathHome.getGameHome()), "$PGW_DIRECTORY/$STATE_FILE")

    private fun readConfig(versionId: String): PgwVersionConfig? = readJson(configFile(versionId), PgwVersionConfig::class.java)

    private fun writeConfig(versionId: String, config: PgwVersionConfig) {
        config.schemaVersion = CONFIG_SCHEMA_VERSION
        writeJson(configFile(versionId), config)
    }

    private fun readState(): PgwVersionManagerState = readJson(stateFile(), PgwVersionManagerState::class.java)
        ?: PgwVersionManagerState(schemaVersion = STATE_SCHEMA_VERSION)

    private fun writeState(state: PgwVersionManagerState) {
        state.schemaVersion = STATE_SCHEMA_VERSION
        writeJson(stateFile(), state)
    }

    private fun writeLegacyIcon(versionId: String, icon: String?) {
        if (icon == null || !icon.startsWith("data:")) return
        val separator = icon.indexOf(',')
        if (separator < 0) return
        val data = runCatching { Base64.decode(icon.substring(separator + 1), Base64.DEFAULT) }.getOrNull() ?: return
        val target = iconFile(versionId)
        target.parentFile?.mkdirs()
        target.writeBytes(data)
    }

    private fun renameIfPresent(source: File, target: File): Boolean {
        if (!source.isFile) return false
        if (!source.renameTo(target)) throw IOException("Unable to rename ${source.name}")
        return true
    }

    private fun copyIfPresent(source: File, target: File) {
        if (!source.isFile) return
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = false)
    }

    private fun validateVersionName(value: String) {
        require(value.isNotBlank() && value.length <= 128) { "Invalid version name" }
        require(value != "." && value != ".." && value.none { it in "\\/:*?\"<>|" }) { "Invalid version name" }
    }

    private fun <T> readJson(file: File, type: Class<T>): T? = runCatching {
        if (!file.isFile) null else file.reader().use { Tools.GLOBAL_GSON.fromJson(it, type) }
    }.getOrNull()

    private fun writeJson(file: File, value: Any) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, ".${file.name}.tmp")
        temporary.writeText(Tools.GLOBAL_GSON.toJson(value))
        if (file.exists() && !file.delete()) throw IOException("Unable to replace ${file.name}")
        if (!temporary.renameTo(file)) throw IOException("Unable to write ${file.name}")
    }
}
