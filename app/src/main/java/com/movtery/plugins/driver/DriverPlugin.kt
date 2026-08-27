package com.movtery.plugins.driver

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File

/** Discovers FCL-compatible APKs that provide a bundled Vulkan driver. */
object DriverPlugin {
    data class Driver(val packageName: String, val name: String, val libraryPath: String)

    private const val META_FCL_PLUGIN = "fclPlugin"
    private const val META_DRIVER_NAME = "driver"
    private const val VULKAN_DRIVER_LIBRARY = "libvulkan_freedreno.so"
    private const val PACKAGE_FLAGS = PackageManager.GET_META_DATA or PackageManager.GET_SHARED_LIBRARY_FILES

    private val drivers = mutableListOf<Driver>()

    @JvmStatic
    fun initDrivers(context: Context) {
        drivers.clear()
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PACKAGE_FLAGS
        ).forEach { resolveInfo ->
            val info = resolveInfo.activityInfo.applicationInfo
            if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) return@forEach

            val metadata = info.metaData ?: return@forEach
            if (!metadata.getBoolean(META_FCL_PLUGIN, false)) return@forEach

            val libraryPath = info.nativeLibraryDir ?: return@forEach
            if (!File(libraryPath, VULKAN_DRIVER_LIBRARY).isFile) return@forEach

            val name = metadata.getString(META_DRIVER_NAME)
                ?.takeIf { it.isNotBlank() }
                ?: context.packageManager.getApplicationLabel(info).toString()
            drivers.add(Driver(info.packageName, name, libraryPath))
        }
    }

    @JvmStatic
    fun getDrivers(): List<Driver> = ArrayList(drivers)

    @JvmStatic
    fun getSelectedDriver(packageName: String?): Driver? =
        packageName?.takeUnless { it == "default" }?.let { selected ->
            drivers.firstOrNull { it.packageName == selected }
        }
}
