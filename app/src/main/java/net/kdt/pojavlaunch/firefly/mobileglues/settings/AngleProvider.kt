package net.kdt.pojavlaunch.firefly.mobileglues.settings

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

/**
 * 一个能提供 ANGLE 的启动器。
 *
 * [libraryDir] 是它解压出来的 native 库目录（`nativeLibraryDir`），里面躺着
 * ANGLE 的那两个 .so。
 */
data class AngleSource(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val libraryDir: String,
)

/**
 * 找出设备上谁能借 ANGLE 给我们。
 *
 * ANGLE 不在本 App 里——它随启动器分发，MobileGlues 在游戏进程里能直接 dlopen 到它，
 * 是因为那时候进程属于启动器。本 App 自己 dlopen 渲染器跑分时没有这个便利：
 * [net.kdt.pojavlaunch.firefly.mobileglues.settings.AngleProvider] 就是用来补上这一段的。
 *
 * 能这么做的前提有三条，都验过：启动器开了 `extractNativeLibs`（.so 是磁盘上的真文件
 * 而不是压在 apk 里）、app 的 linker namespace 允许 `/data` 下的路径、以及那两个 .so
 * 只依赖系统库，不需要把启动器的其它库一起搬过来。
 *
 * 这终究是把别的应用的原生代码加载进本进程，所以选哪一个必须由用户明说，不能替他决定。
 */
object AngleProvider {

    const val GLES_LIBRARY = "libGLESv2_angle.so"
    const val EGL_LIBRARY = "libEGL_angle.so"

    /**
     * 已知会带 ANGLE 的启动器。
     *
     * 必须和 AndroidManifest 里的 `<queries>` 对上——Android 11 起，没在那儿声明过的
     * 包，PackageManager 一律当作不存在。
     */
    private val KNOWN_LAUNCHERS = listOf(
        "com.tungsten.fcl",
        "com.tungsten.fcm",
        "com.tungsten.fcl.ngg",
        "com.tungsten.fcl.qualcommdr",
        "com.tungsten.fcl.mgdebug.debug",
        "com.movtery.zalithlauncher.v2",
        "com.movtery.zalithlauncher.v2.debug",
        "org.fcl.enchantnet",
    )

    /**
     * 装了、并且两个库都齐全可读的启动器。
     *
     * 少一个都不算数：EGL 那个负责建上下文，GLESv2 那个负责画，只有一个的话
     * MobileGlues 会一半走 ANGLE 一半走系统驱动，比干脆用不了还糟。
     */
    fun sources(context: Context): List<AngleSource> {
        val pm = context.packageManager
        return KNOWN_LAUNCHERS.mapNotNull { packageName ->
            val info = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull() ?: return@mapNotNull null
            val dir = info.applicationInfo?.nativeLibraryDir ?: return@mapNotNull null
            if (!hasAngle(dir)) return@mapNotNull null
            AngleSource(
                packageName = packageName,
                label = runCatching {
                    pm.getApplicationLabel(info.applicationInfo!!).toString()
                }.getOrNull() ?: packageName,
                versionName = info.versionName,
                libraryDir = dir,
            )
        }
    }

    /** 这个目录里两个库都在，而且读得到。 */
    fun hasAngle(directory: String?): Boolean {
        if (directory.isNullOrEmpty()) return false
        return listOf(GLES_LIBRARY, EGL_LIBRARY).all { name ->
            val file = File(directory, name)
            runCatching { file.isFile && file.canRead() }.getOrDefault(false)
        }
    }
}


