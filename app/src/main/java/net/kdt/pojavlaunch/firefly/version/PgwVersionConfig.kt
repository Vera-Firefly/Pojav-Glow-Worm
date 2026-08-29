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

import android.os.Build
import com.google.gson.annotations.SerializedName
import com.movtery.ui.subassembly.customprofilepath.ProfilePathHome
import com.movtery.ui.subassembly.customprofilepath.ProfilePathManager
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences
import net.kdt.pojavlaunch.firefly.profiles.ProfileIconIds
import net.kdt.pojavlaunch.firefly.value.launcherprofiles.MinecraftProfile
import java.io.File

enum class VersionIsolationMode {
    FOLLOW_GLOBAL,
    ENABLE,
    DISABLE
}

class PgwVersionConfig(
    @SerializedName("schemaVersion") var schemaVersion: Int = 1,
    @SerializedName("pinned") var pinned: Boolean = false,
    @SerializedName("summary") var summary: String = "",
    @SerializedName("iconId") var iconId: String = ProfileIconIds.DEFAULT,
    @SerializedName("isolation") var isolation: VersionIsolationMode = VersionIsolationMode.FOLLOW_GLOBAL,
    @SerializedName("runtimeName") var runtimeName: String = "",
    @SerializedName("jvmArgs") var jvmArgs: String = "",
    @SerializedName("rendererName") var rendererName: String = "",
    @SerializedName("graphicsApi") var graphicsApi: String = "",
    @SerializedName("controlFile") var controlFile: String = "",
    @SerializedName("enableModsCheck") var enableModsCheck: Boolean = true,
    @SerializedName("customGameDir") var customGameDir: String = ""
) {
    fun effectiveGameDirectory(versionId: String): File {
        val versionDirectory = VersionPaths.versionDirectory(versionId)
        val gameHome = File(ProfilePathHome.getGameHome())
        val customDirectory = customGameDirectory()
        return VersionIsolationPolicy.resolve(
            gameHome,
            versionDirectory,
            customDirectory,
            isolation,
            LauncherPreferences.PREF_VERSION_ISOLATION
        )
    }

    fun toLaunchProfile(versionId: String): MinecraftProfile = MinecraftProfile.createTemplate().apply {
        lastVersionId = versionId
        javaDir = runtimeName.takeIf { it.isNotBlank() }?.let { Tools.LAUNCHERPROFILES_RTPREFIX + it }
        javaArgs = jvmArgs.takeIf { it.isNotBlank() }
        pojavRendererName = rendererName.takeIf { it.isNotBlank() }
        controlFile = controlFile.takeIf { it.isNotBlank() }
        enableModsCheck = this@PgwVersionConfig.enableModsCheck
        pgwManagedGameDir = effectiveGameDirectory(versionId).absolutePath
        pgwGraphicsApi = graphicsApi.takeIf { it.isNotBlank() }
    }

    fun copyForNewInstance(): PgwVersionConfig = PgwVersionConfig(
        schemaVersion = schemaVersion,
        pinned = false,
        summary = "",
        iconId = iconId,
        isolation = VersionIsolationMode.ENABLE,
        runtimeName = runtimeName,
        jvmArgs = jvmArgs,
        rendererName = rendererName,
        graphicsApi = graphicsApi,
        controlFile = controlFile,
        enableModsCheck = enableModsCheck,
        customGameDir = ""
    )

    private fun customGameDirectory(): File? {
        if (customGameDir.isBlank()) return null
        return if (customGameDir.startsWith(Tools.LAUNCHERPROFILES_RTPREFIX)) {
            File(customGameDir.replace(Tools.LAUNCHERPROFILES_RTPREFIX, ProfilePathManager.getCurrentPath() + "/"))
        } else {
            File(ProfilePathManager.getCurrentPath(), customGameDir)
        }
    }
}

object VersionIsolationPolicy {
    @JvmStatic
    fun resolve(
        gameHome: File,
        versionDirectory: File,
        customDirectory: File?,
        mode: VersionIsolationMode,
        globalIsolation: Boolean
    ): File = when (mode) {
        VersionIsolationMode.ENABLE -> versionDirectory
        VersionIsolationMode.DISABLE -> customDirectory ?: gameHome
        VersionIsolationMode.FOLLOW_GLOBAL -> if (globalIsolation) versionDirectory else customDirectory ?: gameHome
    }
}

class PgwVersionManagerState(
    @SerializedName("schemaVersion") var schemaVersion: Int = 1,
    @SerializedName("legacyProfilesMigrated") var legacyProfilesMigrated: Boolean = false,
    @SerializedName("currentVersion") var currentVersion: String? = null
)

data class PgwInstalledVersion(
    val local: LocalVersion,
    val config: PgwVersionConfig
) {
    val id: String get() = local.id
    val valid: Boolean get() = local.valid
    val directory: File get() = local.directory
}

object PgwVersionIcons {
    fun defaultFor(kind: LocalVersionKind): String = when (kind) {
        LocalVersionKind.FORGE -> ProfileIconIds.FORGE
        LocalVersionKind.NEOFORGE -> ProfileIconIds.NEOFORGE
        LocalVersionKind.FABRIC -> ProfileIconIds.FABRIC
        LocalVersionKind.QUILT -> ProfileIconIds.QUILT
        LocalVersionKind.OPTIFINE -> ProfileIconIds.OPTIFINE
        LocalVersionKind.VANILLA -> ProfileIconIds.MINECRAFT
        LocalVersionKind.CUSTOM -> ProfileIconIds.DEFAULT
    }

    fun webpFormat(): android.graphics.Bitmap.CompressFormat = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        android.graphics.Bitmap.CompressFormat.WEBP
    } else {
        android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
    }
}
