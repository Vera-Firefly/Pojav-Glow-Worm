package net.kdt.pojavlaunch.firefly.mobileglues

import java.io.File

object MGInfoGetter {

    init {
        System.loadLibrary("mobileglues_info_getter")
    }

    external fun setenv(key: String, value: String, overwrite: Int): Int

    external fun getMobileGluesGLInfo(): String

    /**
     * 查询 MobileGlues 的 GL 信息。
     *
     * 这是一个阻塞调用：native 侧会 dlopen libmobileglues 并创建 EGL 上下文，必须放在后台线程。
     * [mgDirectory] 显式传入，而不是像以前那样从一个可变静态字段里读。
     */
    /**
     * @param angleDirectory 借来的 ANGLE 所在目录（某个启动器的 native 库目录）；
     *   null = 不借。配置若要求 ANGLE 而这里不借，渲染器会退回系统驱动，那这份信息
     *   讲的就不是游戏里的那个驱动了。
     */
    fun info(mgDirectory: File, angleDirectory: String? = null): String = try {
        // 这里不设 MG_COUNT_LAUNCH：我们自己把渲染器加载起来问一句话，不是一次「启动」。
        setenv("MG_PLUGIN_STATUS", "1", 1)
        setenv("MG_DIR_PATH", mgDirectory.path, 1)
        // 空串等于没设：渲染器那边只认非空值，游戏进程里本来也不会有这个变量。
        setenv("MG_ANGLE_DIR", angleDirectory.orEmpty(), 1)
        getMobileGluesGLInfo()
    } catch (e: Throwable) {
        "Error: ${e.message}"
    }
}


