package net.kdt.pojavlaunch.firefly.mobileglues.utils

import net.kdt.pojavlaunch.firefly.Tools
import java.io.File

object Constants {
    const val CONFIG_FILE_NAME: String = "config.json"

    val MG_DIRECTORY: String
        get() = requireNotNull(Tools.MOBILEGLES_DIR) { "MobileGlues directory is not initialized" }

    val MG_DIRECTORY_FILE: File
        get() = File(MG_DIRECTORY)

    val CONFIG_FILE_PATH: String = "$MG_DIRECTORY/$CONFIG_FILE_NAME"

    val GLSL_CACHE_FILE_PATH: String = "$MG_DIRECTORY/glsl_cache.tmp"
}


