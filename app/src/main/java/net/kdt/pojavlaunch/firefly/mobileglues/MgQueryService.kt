package net.kdt.pojavlaunch.firefly.mobileglues

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import java.io.File

/**
 * 一次性查询进程（`android:process=":mgquery"`）。
 *
 * 渲染器只在 libmobileglues 第一次进入进程时读环境和配置：MG_ANGLE_DIR、config.json
 * 都在它的静态构造函数里消费，而这个库一旦载入就退不出去——GL 线程的 thread_local
 * 析构攥着 dlopen 引用，dlclose 的计数永远减不到零，构造函数不会再跑。于是同一个进程里
 * 第一次查询选定的驱动会钉死后面的每一次：后来借的 ANGLE 不生效，后来改选的系统驱动
 * 仍然是 ANGLE。
 *
 * 解法不在进程里，而在进程外：查询挪进这个进程，查完即由 [onUnbind] 自杀。下一次查询
 * 绑定时系统重新拉起进程，一切从头初始化，环境和配置必然是新的。附带的好处是隔离：
 * 驱动在跑分中崩溃，死的是这个进程，主界面只会收到一个 binder 异常。
 */
class MgQueryService : Service() {

    private val binder = object : IMgQuery.Stub() {
        override fun runBench(
            mgDirectory: String,
            angleDirectory: String,
            startSections: Int,
            maxSections: Int,
        ): String = MGBench.run(
            File(mgDirectory),
            angleDirectory.takeUnless { it.isEmpty() },
            startSections,
            maxSections,
        )

        override fun benchProgress(): Int = MGBench.rawProgress()

        override fun glInfo(mgDirectory: String, angleDirectory: String): String =
            MGInfoGetter.info(File(mgDirectory), angleDirectory.takeUnless { it.isEmpty() })
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        // 客户端在拿到返回值之后才解绑，所以走到这里时结果一定已经送出去了。
        // 自杀而不是等系统回收：回收「最终会发生」，而下一次查询要求的是「必然全新」。
        Process.killProcess(Process.myPid())
        return false
    }
}


