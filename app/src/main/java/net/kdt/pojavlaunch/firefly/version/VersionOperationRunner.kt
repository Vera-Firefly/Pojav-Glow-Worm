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

import com.kdt.mcgui.ProgressLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.version.download.MinecraftDownloadMode
import net.kdt.pojavlaunch.firefly.version.download.MinecraftFileDownloader
import java.util.concurrent.atomic.AtomicBoolean

fun interface VersionOperationCallback {
    fun onComplete(error: Throwable?)
}

/** Runs file integrity work outside the launcher UI thread and reports through the shared progress bar. */
object VersionOperationRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repairing = AtomicBoolean(false)

    @JvmStatic
    fun repairBeforeLaunch(versionId: String, callback: VersionOperationCallback) {
        if (!repairing.compareAndSet(false, true)) {
            callback.onComplete(IllegalStateException("A version operation is already running"))
            return
        }
        ProgressLayout.setProgress(ProgressLayout.MINECRAFT_VERSION_REPAIR, 0, R.string.newdl_downloading_game_files, 0, 0, 0.0)
        scope.launch {
            var failure: Throwable? = null
            try {
                MinecraftFileDownloader(
                    requestedVersionId = versionId,
                    installedVersionId = versionId,
                    verifyIntegrity = true,
                    mode = MinecraftDownloadMode.VERIFY_AND_REPAIR
                ).run { progress ->
                    val percent = if (progress.totalFiles > 0) progress.downloadedFiles * 100 / progress.totalFiles else 0
                    ProgressLayout.setProgress(
                        ProgressLayout.MINECRAFT_VERSION_REPAIR,
                        percent,
                        R.string.newdl_downloading_game_files,
                        progress.downloadedFiles,
                        progress.totalFiles,
                        progress.speedBytesPerSec / (1024.0 * 1024.0)
                    )
                }
            } catch (error: Throwable) {
                failure = error
            } finally {
                repairing.set(false)
                withContext(Dispatchers.Main.immediate) {
                    ProgressLayout.clearProgress(ProgressLayout.MINECRAFT_VERSION_REPAIR)
                    callback.onComplete(failure)
                }
            }
        }
    }
}
