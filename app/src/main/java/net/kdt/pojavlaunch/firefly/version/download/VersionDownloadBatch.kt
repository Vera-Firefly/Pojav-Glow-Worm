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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.firefly.version.download.engine.BatchDownloader
import net.kdt.pojavlaunch.firefly.version.download.engine.BatchProgress
import net.kdt.pojavlaunch.firefly.version.download.engine.DownloadRequest
import net.kdt.pojavlaunch.firefly.version.io.calculateSha1
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

data class VersionDownloadTask(
    val urls: List<String>,
    val target: File,
    val sha1: String?,
    val size: Long = -1L,
    val downloadable: Boolean = true,
    val afterDownload: suspend () -> Unit = {}
)

suspend fun runVersionDownloadBatch(
    tasks: List<VersionDownloadTask>,
    maxConnections: Int = 64,
    onProgress: suspend (BatchProgress) -> Unit = {}
) {
    val (available, pending) = withContext(Dispatchers.IO.limitedParallelism(4)) {
        coroutineScope {
            tasks.map { task -> async { task to task.isAvailable() } }.awaitAll()
        }.partition { it.second }.let { (valid, invalid) -> valid.map { it.first } to invalid.map { it.first } }
    }
    available.forEach { it.afterDownload() }
    if (pending.isEmpty()) {
        onProgress(BatchProgress(0L, tasks.sumOf { it.size.coerceAtLeast(0L) }, tasks.size, tasks.size, 0L))
        return
    }

    val batch = BatchDownloader(
        requests = pending.map { DownloadRequest(it.urls, it.target, it.sha1, it.size, it) },
        maxConnections = maxConnections,
        retryRounds = 1
    )
    batch.onUpdate = onProgress
    batch.onFileSuccess = { request -> (request.tag as? VersionDownloadTask)?.afterDownload?.invoke() }
    batch.run()
}

private fun VersionDownloadTask.isAvailable(): Boolean {
    if (!target.isFile) return false
    if (sha1.isNullOrBlank()) {
        if (!downloadable) return true
        return runCatching {
            if (target.extension.equals("jar", true) || target.extension.equals("zip", true)) ZipFile(target).close()
            true
        }.getOrDefault(false)
    }
    val valid = runCatching { calculateSha1(target).equals(sha1, ignoreCase = true) }.getOrDefault(false)
    if (!valid) target.delete()
    return valid
}
