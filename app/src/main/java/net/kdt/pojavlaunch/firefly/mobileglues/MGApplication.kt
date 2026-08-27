package net.kdt.pojavlaunch.firefly.mobileglues

import android.content.Context
import net.kdt.pojavlaunch.firefly.mobileglues.settings.AuthController
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MGCacheExporter
import net.kdt.pojavlaunch.firefly.mobileglues.settings.MGConfigStore
import net.kdt.pojavlaunch.firefly.mobileglues.settings.PluginConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 持有各仓库与授权控制器。
 *
 * 放在 Application 而不是 Activity 上，是因为去抖写盘的协程需要一个比界面更长的生命周期：
 * 用户改完设置立刻按 Home 键时，落盘不应该被 Activity 的销毁打断。
 * 启动次数也记在这里：旋转屏幕会重建 Activity，但不算一次启动。
 */
class MGApplication(context: Context) {

    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val pluginConfigStore: PluginConfigStore by lazy { PluginConfigStore(appContext) }

    val authController: AuthController by lazy { AuthController(appContext, pluginConfigStore) }

    val configStore: MGConfigStore by lazy {
        MGConfigStore(
            storage = null,
            scope = scope,
        )
    }

    val cacheExporter: MGCacheExporter by lazy { MGCacheExporter(appContext, configStore) }

    /** 赞助弹窗每个进程最多弹一次——旋转屏幕重建 Activity 不该再弹。 */
    var sponsorPromptedThisProcess: Boolean = false

    init {
        authController.refresh()
    }
}


