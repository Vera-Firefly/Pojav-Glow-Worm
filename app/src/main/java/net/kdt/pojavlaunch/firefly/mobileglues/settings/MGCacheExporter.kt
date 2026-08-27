package net.kdt.pojavlaunch.firefly.mobileglues.settings

import android.content.Context
import net.kdt.pojavlaunch.firefly.mobileglues.utils.Constants
import java.io.File

/**
 * 把当前配置导出到应用私有缓存目录，供 MGInfoGetter 通过 `MG_DIR_PATH` 读取。
 *
 * 取代了原先挂在 MGConfig 伴生对象上的两个可变静态字段（`cacheConfigPath` / `cacheMGDir`）：
 * 那两个字段默认是 `File("")`，能工作只是因为调用方恰好先调了一次导出。
 */
class MGCacheExporter(context: Context, private val store: MGConfigStore) {

    private val appContext = context.applicationContext

    val directory: File
        get() = File(appContext.externalCacheDir ?: appContext.cacheDir, DIRECTORY_NAME)

    suspend fun export(): Result<File> =
        store.exportTo(File(directory, Constants.CONFIG_FILE_NAME)).map { directory }

    /**
     * 删掉导出的那份副本。
     *
     * 这里躺着的是用户配置的完整拷贝（还有 native 库查询时顺手写的日志）。用户收回授权
     * 或者要求删干净的时候，只删 `/sdcard/MG` 而把这份副本留在应用缓存里，是说话不算数。
     */
    fun clear(): Result<Unit> = runCatching {
        val target = directory
        if (target.exists() && !target.deleteRecursively()) {
            throw java.io.IOException("Could not delete ${target.path}")
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "MG"
    }
}


