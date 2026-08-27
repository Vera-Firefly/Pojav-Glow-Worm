package com.movtery.utils

import android.content.res.AssetManager
import android.util.Log
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.multirt.MultiRTUtils
import net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences
import net.kdt.pojavlaunch.firefly.Architecture.archAsString
import net.kdt.pojavlaunch.firefly.PojavApplication.sExecutorService
import java.io.IOException

object UnpackJRE {
    fun unpackAllJre(assetManager: AssetManager) {
        sExecutorService.execute {
            checkInternalRuntime(assetManager, InternalRuntime.JRE_8)
            checkInternalRuntime(assetManager, InternalRuntime.JRE_17)
            checkInternalRuntime(assetManager, InternalRuntime.JRE_21)
            checkInternalRuntime(assetManager, InternalRuntime.JRE_25)

            LauncherPreferences.reloadRuntime()
        }
    }

    private fun checkInternalRuntime(assetManager: AssetManager, internalRuntime: InternalRuntime) {
        val installedRuntimeVersion = MultiRTUtils.readInternalRuntimeVersion(internalRuntime.runtimeName)
        val launcherRuntimeVersion = try {
            Tools.read(assetManager.open("${internalRuntime.path}/version"))
        } catch (exc: IOException) {
            return
        }

        if (launcherRuntimeVersion != installedRuntimeVersion) {
            unpackInternalRuntime(assetManager, internalRuntime, launcherRuntimeVersion)
        }
    }

    private fun unpackInternalRuntime(
        assetManager: AssetManager,
        internalRuntime: InternalRuntime,
        version: String
    ) {
        try {
            MultiRTUtils.installRuntimeNamedBinpack(
                assetManager.open("${internalRuntime.path}/universal.tar.xz"),
                assetManager.open("${internalRuntime.path}/bin-${archAsString(Tools.DEVICE_ARCHITECTURE)}.tar.xz"),
                internalRuntime.runtimeName,
                version
            )
            MultiRTUtils.postPrepare(internalRuntime.runtimeName)
        } catch (e: IOException) {
            Log.e("UnpackJREAuto", "Internal JRE unpack failed", e)
        }
    }

     enum class InternalRuntime(
        val majorVersion: Int,
        val runtimeName: String,
        val path: String
    ) {
        JRE_8(8, "Internal-8", "components/jre-8"),
        JRE_17(17, "Internal-17", "components/jre-17"),
        JRE_21(21, "Internal-21", "components/jre-21"),
        JRE_25(25, "Internal-25", "components/jre-25")
    }
}
