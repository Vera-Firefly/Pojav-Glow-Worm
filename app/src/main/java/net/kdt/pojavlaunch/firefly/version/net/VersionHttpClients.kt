/*
 * Pojav Glow-Worm
 * Copyright (C) 2026 Pojav Glow-Worm contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.firefly.version.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

object VersionHttpClients {
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L

    val DOWNLOAD_OKHTTP_CLIENT: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val DOWNLOAD_OKHTTP_CLIENT_MULTIPLEX: OkHttpClient = DOWNLOAD_OKHTTP_CLIENT.newBuilder()
        .build()
}

private const val VERSION_DOWNLOAD_USER_AGENT = "Pojav-Glow-Worm/1.0"

fun createVersionRequestBuilder(url: String): Request.Builder = Request.Builder()
    .url(url)
    .header("User-Agent", VERSION_DOWNLOAD_USER_AGENT)

fun Throwable.isInterruptedVersionDownload(): Boolean =
    generateSequence(this) { it.cause }.any { it is InterruptedIOException }
