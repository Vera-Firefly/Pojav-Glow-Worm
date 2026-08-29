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

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.kdt.pojavlaunch.firefly.R
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.multirt.MultiRTUtils
import net.kdt.pojavlaunch.firefly.utils.JREUtils
import java.io.File
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "InstallerJvm"
private const val EXTRA_RUNTIME = "version.installer.runtime"
private const val EXTRA_WORKING_DIRECTORY = "version.installer.working_directory"
private const val EXTRA_ARGUMENTS = "version.installer.arguments"
private const val EXTRA_RESULT_PORT = "version.installer.result_port"
private const val INSTALLER_NOTIFICATION_ID = 17391
private const val JVM_TIMEOUT_MILLIS = 5L * 60L * 1000L
private const val JVM_PROCESS_STOP_TIMEOUT_MILLIS = 15L * 1000L

/**
 * Runs Forge and NeoForge processors in a process which can be stopped without affecting the
 * launcher process that owns the installation transaction.
 */
class InstallerJvmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var processorJob: Job? = null
    private var resultPort = -1
    private val resultSent = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedPort = intent?.getIntExtra(EXTRA_RESULT_PORT, -1) ?: -1
        resultPort = requestedPort
        resultSent.set(false)
        try {
            postForegroundNotification()
            val runtimeName = intent?.getStringExtra(EXTRA_RUNTIME)
            val workingDirectory = intent?.getStringExtra(EXTRA_WORKING_DIRECTORY)
            val arguments = intent?.getStringArrayListExtra(EXTRA_ARGUMENTS)
            if (runtimeName.isNullOrBlank() || workingDirectory.isNullOrBlank() || arguments == null || requestedPort !in 1..65535) {
                throw IllegalArgumentException("Invalid installer JVM service parameters")
            }
            Log.i(TAG, "Starting processor JVM runtime=$runtimeName port=$requestedPort args=${arguments.size}")

            processorJob?.cancel()
            processorJob = scope.launch {
                val exitCode = runCatching {
                    val runtime = MultiRTUtils.forceReread(runtimeName)
                    require(runtime.javaVersion >= 8) { "Unsupported Java runtime: $runtimeName" }
                    Log.i(TAG, "Launching processor JVM home=${MultiRTUtils.getRuntimeHome(runtime.name)}")
                    JREUtils.launchInstallerJvm(runtime, File(workingDirectory), arguments)
                }.onFailure { error ->
                    Log.e(TAG, "Processor JVM terminated before completion", error)
                }.getOrDefault(1)

                sendExitCode(requestedPort, exitCode)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to start processor JVM service", error)
            sendExitCode(requestedPort, 1)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        processorJob?.cancel()
        if (resultPort in 1..65535 && !resultSent.get()) {
            sendExitCode(resultPort, 1)
        }
        super.onDestroy()
        scope.cancel()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun postForegroundNotification() {
        Tools.buildNotificationChannel(applicationContext)
        val notification = NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
            .setContentTitle(getString(R.string.app_short_name))
            .setContentText(getString(R.string.version_install_progress_downloading))
            .setSmallIcon(R.drawable.notif_icon)
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                INSTALLER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(INSTALLER_NOTIFICATION_ID, notification)
        }
    }

    private fun sendExitCode(port: Int, exitCode: Int) {
        if (port !in 1..65535 || !resultSent.compareAndSet(false, true)) return
        runCatching {
            DatagramSocket().use { socket ->
                val payload = exitCode.toString().toByteArray(Charsets.UTF_8)
                socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName("127.0.0.1"), port))
                Log.i(TAG, "Reported processor JVM exit code $exitCode to port $port")
            }
        }.onFailure { error ->
            Log.e(TAG, "Unable to report processor exit code", error)
        }
    }
}

/**
 * Coordinates one processor JVM and retries it with the bundled runtimes in the same order used
 * by ZL2's installation service.
 */
object InstallerJvmRunner {
    suspend fun runWithFallback(context: Context, workingDirectory: File, arguments: List<String>) {
        require(arguments.isNotEmpty()) { "Processor JVM arguments are empty" }
        ensureNoGameProcess(context)
        var failure: Throwable? = null
        for (runtimeName in installerRuntimeNames()) {
            currentCoroutineContext().ensureActive()
            try {
                if (runSingle(context, runtimeName, workingDirectory, arguments) == 0) return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failure = error
                Log.w(TAG, "Processor failed on $runtimeName", error)
            }
        }
        throw IOException("No installed Java runtime could run the loader processor", failure)
    }

    private suspend fun runSingle(
        context: Context,
        runtimeName: String,
        workingDirectory: File,
        arguments: List<String>
    ): Int = coroutineScope {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { socket ->
            Log.i(TAG, "Waiting for processor JVM exit on port ${socket.localPort}")
            val receiver = async(Dispatchers.IO) {
                val bytes = ByteArray(64)
                val packet = DatagramPacket(bytes, bytes.size)
                socket.receive(packet)
                String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim().toIntOrNull()
                    ?: throw IOException("Processor service returned an invalid exit code")
            }
            val serviceIntent = Intent(context, InstallerJvmService::class.java).apply {
                putExtra(EXTRA_RUNTIME, runtimeName)
                putExtra(EXTRA_WORKING_DIRECTORY, workingDirectory.absolutePath)
                putStringArrayListExtra(EXTRA_ARGUMENTS, ArrayList(arguments))
                putExtra(EXTRA_RESULT_PORT, socket.localPort)
            }
            try {
                Log.i(TAG, "Starting processor JVM service runtime=$runtimeName")
                ContextCompat.startForegroundService(context, serviceIntent)
                val exitCode = withTimeout(JVM_TIMEOUT_MILLIS) { receiver.await() }
                Log.i(TAG, "Processor JVM runtime=$runtimeName exited with code $exitCode")
                exitCode
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                throw IOException("Timed out waiting for the loader processor JVM", error)
            } finally {
                receiver.cancel()
                socket.close()
                context.stopService(Intent(context, InstallerJvmService::class.java))
                waitForJvmProcessStopped(context)
            }
        }
    }

    private suspend fun waitForJvmProcessStopped(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processName = "${context.packageName}:jvm"
        val deadline = System.currentTimeMillis() + JVM_PROCESS_STOP_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val running = activityManager.runningAppProcesses.orEmpty().any { it.processName == processName }
            if (!running) {
                Log.i(TAG, "Processor JVM process stopped")
                return
            }
            delay(100L)
        }
        Log.w(TAG, "Timed out waiting for processor JVM process to stop")
    }

    private fun installerRuntimeNames(): List<String> {
        val preferred = listOf(8, 17, 21, 25).mapNotNull { major ->
            runCatching { MultiRTUtils.getExactJreName(major) }.getOrNull()
        }
        val remaining = runCatching {
            MultiRTUtils.getRuntimes()
                .filter { it.javaVersion >= 8 }
                .sortedBy { it.javaVersion }
                .map { it.name }
        }.getOrDefault(emptyList())
        return (preferred + remaining).distinct()
    }

    private fun ensureNoGameProcess(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val gameProcess = "${context.packageName}:game"
        if (activityManager.runningAppProcesses.orEmpty().any { it.processName == gameProcess }) {
            throw IOException("The game process must exit before a loader can be installed")
        }
    }
}
