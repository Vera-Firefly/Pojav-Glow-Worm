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

import com.movtery.ui.subassembly.customprofilepath.ProfilePathManager
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.extra.ExtraConstants
import net.kdt.pojavlaunch.firefly.extra.ExtraCore
import net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences
import net.kdt.pojavlaunch.firefly.profiles.ProfileIconCache
import net.kdt.pojavlaunch.firefly.profiles.ProfileIconIds
import net.kdt.pojavlaunch.firefly.value.launcherprofiles.LauncherProfiles
import net.kdt.pojavlaunch.firefly.value.launcherprofiles.MinecraftProfile
import net.kdt.pojavlaunch.firefly.version.model.GameManifest
import java.io.File
import java.io.IOException
import java.util.Locale

enum class LocalVersionKind {
    VANILLA,
    FORGE,
    NEOFORGE,
    FABRIC,
    QUILT,
    OPTIFINE,
    CUSTOM
}

data class LocalVersion(
    val id: String,
    val directory: File,
    val manifest: GameManifest?,
    val kind: LocalVersionKind,
    val valid: Boolean,
    val requiresRepair: Boolean
)

data class VersionRemovalResult(
    val profilesRemoved: Int,
    val filesDeleted: Int,
    val selectedProfileKey: String
)

/**
 * Provides a single local version view for the profile editor, installer and startup repair path.
 * Version metadata remains in the standard Minecraft directory and profile data stays in PGW's
 * launcher_profiles.json.
 */
object LocalVersionManager {
    fun scan(): List<LocalVersion> = VersionPaths.versions().listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isDirectory && !isInternalDirectory(it.name) }
        .map(::readVersion)
        .toList()
        .let(::applyInheritedRepairStatus)
        .sortedWith(compareByDescending<LocalVersion> { it.manifest?.releaseTime.orEmpty() }.thenBy { it.id.lowercase(Locale.ROOT) })

    fun get(versionId: String): LocalVersion? = versionId
        .takeUnless(::isInternalDirectory)
        ?.let { id -> scan().firstOrNull { it.id == id } }

    /** Returns every local instance that directly or transitively inherits from [versionId]. */
    fun dependentVersionIds(versionId: String): List<String> {
        val versions = scan()
        return VersionDependencyGraph.descendants(
            versionId,
            versions.associate { it.id to it.manifest?.inheritsFrom }
        )
    }

    fun rename(versionId: String, newVersionId: String) {
        validateVersionId(newVersionId)
        val source = VersionPaths.versionDirectory(versionId)
        val target = VersionPaths.versionDirectory(newVersionId)
        require(source.isDirectory) { "Version does not exist: $versionId" }
        require(!target.exists()) { "Version already exists: $newVersionId" }
        if (!source.renameTo(target)) throw IOException("Unable to rename version $versionId")

        val oldJson = File(target, "$versionId.json")
        val oldJar = File(target, "$versionId.jar")
        val newJson = File(target, "$newVersionId.json")
        val newJar = File(target, "$newVersionId.jar")
        if (oldJson.isFile && !oldJson.renameTo(newJson)) throw IOException("Unable to rename version JSON")
        if (oldJar.isFile && !oldJar.renameTo(newJar)) throw IOException("Unable to rename version JAR")
        updateProfiles(versionId, newVersionId)
    }

    fun copy(versionId: String, newVersionId: String, copyDirectory: Boolean) {
        validateVersionId(newVersionId)
        val source = VersionPaths.versionDirectory(versionId)
        val target = VersionPaths.versionDirectory(newVersionId)
        require(source.isDirectory) { "Version does not exist: $versionId" }
        require(!target.exists()) { "Version already exists: $newVersionId" }
        if (!target.mkdirs()) throw IOException("Unable to create version directory: $newVersionId")

        if (copyDirectory) {
            source.walkTopDown().forEach { file ->
                val relative = file.relativeTo(source)
                val destination = File(target, relative.path)
                when {
                    file.isDirectory -> destination.mkdirs()
                    file.isFile -> {
                        destination.parentFile?.mkdirs()
                        file.copyTo(destination, overwrite = false)
                    }
                }
            }
        } else {
            copyIfPresent(File(source, "$versionId.json"), File(target, "$newVersionId.json"))
            copyIfPresent(File(source, "$versionId.jar"), File(target, "$newVersionId.jar"))
        }

        val oldJson = File(target, "$versionId.json")
        val oldJar = File(target, "$versionId.jar")
        if (oldJson.isFile && !oldJson.renameTo(File(target, "$newVersionId.json"))) throw IOException("Unable to rename copied JSON")
        if (oldJar.isFile && !oldJar.renameTo(File(target, "$newVersionId.jar"))) throw IOException("Unable to rename copied JAR")
    }

    fun delete(versionId: String): VersionRemovalResult {
        val directory = VersionPaths.versionDirectory(versionId)
        require(directory.isDirectory) { "Version does not exist: $versionId" }
        val filesDeleted = directory.walkBottomUp().count { it.isFile }
        if (!directory.deleteRecursively()) throw IOException("Unable to delete version: $versionId")

        ensureProfilesLoaded()
        val profiles = LauncherProfiles.mainProfileJson.profiles
        val removedKeys = profiles
            .filterValues { it.lastVersionId == versionId }
            .keys
            .toList()
        removedKeys.forEach { key ->
            profiles.remove(key)
            ProfileIconCache.dropIcon(key)
        }

        val selected = selectedProfileAfterRemoval(profiles, removedKeys)
        LauncherProfiles.write(ProfilePathManager.getCurrentProfile())
        LauncherPreferences.DEFAULT_PREF.edit()
            .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, selected)
            .apply()
        return VersionRemovalResult(removedKeys.size, filesDeleted, selected)
    }

    /** Removes profiles that no longer have a matching local version directory. */
    fun removeProfilesWithoutLocalVersion(): Int {
        ensureProfilesLoaded()
        val profiles = LauncherProfiles.mainProfileJson.profiles
        val removedKeys = profiles
            .filterValues { profile ->
                val versionId = profile.lastVersionId
                versionId.isNullOrBlank() || !VersionPaths.versionDirectory(versionId).isDirectory
            }
            .keys
            .toList()
        if (removedKeys.isEmpty()) return 0

        removedKeys.forEach { key ->
            profiles.remove(key)
            ProfileIconCache.dropIcon(key)
        }

        val selected = selectedProfileAfterRemoval(profiles, removedKeys)
        LauncherProfiles.write(ProfilePathManager.getCurrentProfile())
        LauncherPreferences.DEFAULT_PREF.edit()
            .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, selected)
            .apply()
        return removedKeys.size
    }

    fun profilesUsing(versionId: String): List<Pair<String, MinecraftProfile>> {
        ensureProfilesLoaded()
        return LauncherProfiles.mainProfileJson.profiles
            .filterValues { it.lastVersionId == versionId }
            .toList()
    }

    /**
     * Selects every existing profile that already references the installed version. When no profile
     * references it, creates a separate UUID profile instead of mutating a similarly named profile.
     */
    fun associateInstalledVersion(
        versionId: String,
        defaultProfileIcon: String = ProfileIconIds.MINECRAFT
    ): String {
        ensureProfilesLoaded()
        val profiles = LauncherProfiles.mainProfileJson.profiles
        val selected = profiles.entries.firstOrNull { it.value.lastVersionId == versionId }?.key
            ?: LauncherProfiles.getFreeProfileKey().also { key ->
                val profile = MinecraftProfile.createTemplate()
                profile.name = versionId
                profile.lastVersionId = versionId
                profile.icon = defaultProfileIcon
                profiles[key] = profile
            }
        profiles.values.filter { it.lastVersionId == versionId }.forEach { it.lastVersionId = versionId }
        LauncherProfiles.write(ProfilePathManager.getCurrentProfile())
        LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, selected).apply()
        ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, selected)
        return selected
    }

    /** Removes unreferenced entries from assets/objects without touching saves, libraries or indexes. */
    fun clearUnreferencedAssets(): Int {
        val versions = scan()
        // A missing parent hides the only metadata that can name its asset index. Keep shared
        // objects intact until repair makes the inheritance graph complete again.
        if (versions.any { it.requiresRepair }) return 0
        val byId = versions.associateBy { it.id }
        val referenced = LinkedHashSet<String>()
        fun collectAssetIndexes(versionId: String, visited: MutableSet<String>) {
            if (!visited.add(versionId)) return
            val manifest = byId[versionId]?.manifest ?: return
            manifest.assetIndex?.id?.let(referenced::add)
            manifest.inheritsFrom?.takeIf { it.isNotBlank() }?.let { parentId ->
                collectAssetIndexes(parentId, visited)
            }
        }
        versions.forEach { collectAssetIndexes(it.id, LinkedHashSet()) }
        val objectsHome = File(VersionPaths.assets(), "objects")
        if (!objectsHome.isDirectory) return 0

        val activeHashes = referenced.flatMapTo(LinkedHashSet()) { assetId ->
            val file = VersionPaths.assetIndex(assetId)
            parse(file, AssetIndexJsonHolder::class.java)?.objects?.values.orEmpty()
                .mapNotNull { it.hash }
        }
        var removed = 0
        objectsHome.walkBottomUp().filter(File::isFile).forEach { file ->
            if (file.name !in activeHashes && file.delete()) removed++
        }
        objectsHome.walkBottomUp().filter(File::isDirectory).forEach { directory ->
            if (directory != objectsHome && directory.list().isNullOrEmpty()) directory.delete()
        }
        return removed
    }

    private fun readVersion(directory: File): LocalVersion {
        val id = directory.name
        val manifest = parse(File(directory, "$id.json"), GameManifest::class.java)
        return LocalVersion(
            id = id,
            directory = directory,
            manifest = manifest,
            kind = manifest?.let(::detectKind) ?: LocalVersionKind.CUSTOM,
            valid = manifest != null,
            requiresRepair = manifest != null && !File(directory, "$id.jar").isFile && manifest.inheritsFrom.isNullOrBlank()
        )
    }

    private fun applyInheritedRepairStatus(versions: List<LocalVersion>): List<LocalVersion> {
        val parentByVersion = versions.associate { it.id to it.manifest?.inheritsFrom.orEmpty() }
        val clientJarVersions = versions.filter { File(it.directory, "${it.id}.jar").isFile }.mapTo(LinkedHashSet()) { it.id }
        return versions.map { version ->
            version.copy(
                requiresRepair = version.requiresRepair ||
                    VersionDependencyGraph.requiresRepair(version.id, parentByVersion, clientJarVersions)
            )
        }
    }

    private fun detectKind(manifest: GameManifest): LocalVersionKind {
        val names = manifest.libraries.orEmpty().joinToString("\n") { it.name }
        val id = manifest.id.orEmpty().lowercase(Locale.ROOT)
        return when {
            "quilt-loader" in names || "quilt" in id -> LocalVersionKind.QUILT
            "fabric-loader" in names || "fabric" in id -> LocalVersionKind.FABRIC
            "net.neoforged" in names || "neoforge" in id -> LocalVersionKind.NEOFORGE
            "net.minecraftforge" in names || "forge" in id -> LocalVersionKind.FORGE
            "optifine" in names.lowercase(Locale.ROOT) || "optifine" in id -> LocalVersionKind.OPTIFINE
            manifest.inheritsFrom.isNullOrBlank() -> LocalVersionKind.VANILLA
            else -> LocalVersionKind.CUSTOM
        }
    }

    private fun updateProfiles(fromVersionId: String, toVersionId: String): Int {
        ensureProfilesLoaded()
        var count = 0
        LauncherProfiles.mainProfileJson.profiles.values.forEach { profile ->
            if (profile.lastVersionId == fromVersionId) {
                profile.lastVersionId = toVersionId
                count++
            }
        }
        if (count > 0) LauncherProfiles.write(ProfilePathManager.getCurrentProfile())
        return count
    }

    private fun selectedProfileAfterRemoval(
        profiles: MutableMap<String, MinecraftProfile>,
        removedKeys: List<String>
    ): String {
        val current = LauncherPreferences.DEFAULT_PREF
            .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "")
            .orEmpty()
        if (current !in removedKeys && profiles.containsKey(current)) return current
        return profiles.keys.firstOrNull().orEmpty()
    }

    private fun ensureProfilesLoaded() {
        if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load(ProfilePathManager.getCurrentProfile())
    }

    private fun copyIfPresent(source: File, target: File) {
        if (source.isFile) source.copyTo(target, overwrite = false)
    }

    private fun validateVersionId(value: String) {
        require(value.isNotBlank() && value.length <= 128) { "Invalid version name" }
        require(!value.contains('/') && !value.contains('\\') && value != "." && value != "..") { "Invalid version name" }
    }

    private fun isInternalDirectory(name: String): Boolean =
        name.startsWith(".pgw-stage-") || name.startsWith(".pgw-backup-")

    private fun <T> parse(file: File, type: Class<T>): T? = runCatching {
        if (!file.isFile) return null
        file.reader().use { Tools.GLOBAL_GSON.fromJson(it, type) }
    }.getOrNull()

    private class AssetIndexJsonHolder {
        val objects: Map<String, AssetObject>? = null
    }

    private class AssetObject {
        val hash: String? = null
    }
}
