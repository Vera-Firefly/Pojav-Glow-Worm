package net.kdt.pojavlaunch.firefly.mobileglues

import java.io.File

object MGBench {

    init {
        System.loadLibrary("mobileglues_info_getter")
    }

    private external fun runMultidrawBench(startSections: Int, maxSections: Int): String

    private external fun benchProgress(): Int

    /**
     * 在本进程内跑 MultiDraw 微基准。
     *
     * 与 [MGInfoGetter.info] 同一条通道：dlopen libmobileglues，经渲染器自己的 EGL 层
     * 建上下文，调用 `mg_multidraw_bench_run`，拿回一段 JSON。阻塞且耗时（默认预算 8 秒，
     * 见 native 侧的 MG_BENCH_BUDGET_MS），必须放在后台线程。
     *
     * 只应从 [MgQueryService] 的进程里调用：环境变量和配置都在库的静态构造里读，
     * 而库离不开进程，所以「每次都读到新值」只有一次性进程给得起。
     */
    /**
     * @param angleDirectory 借来的 ANGLE 所在目录（某个启动器的 native 库目录）；
     *   null = 不借，配置若要求 ANGLE 则渲染器会退回系统驱动，结果里会如实写明。
     * @param startSections 起手的场景规模；0 = 用渲染器的默认值。
     * @param maxSections 本次不得越过的规模上限；0 = 没有上限。上一次跑分丢了上下文
     *   之后由 [BenchRunner] 填进来——崩掉的那个规模不可能在同一个进程里再探一次，
     *   要探就得靠新进程,而新进程不知道上一个进程撞到过什么。
     */
    fun run(
        mgDirectory: File,
        angleDirectory: String? = null,
        startSections: Int = 0,
        maxSections: Int = 0,
    ): String = try {
        // 跑分不是一次「启动」，不设 MG_COUNT_LAUNCH。
        MGInfoGetter.setenv("MG_PLUGIN_STATUS", "1", 1)
        MGInfoGetter.setenv("MG_DIR_PATH", mgDirectory.path, 1)
        // 空串等于没设：渲染器那边只认非空值，游戏进程里本来也不会有这个变量。
        MGInfoGetter.setenv("MG_ANGLE_DIR", angleDirectory.orEmpty(), 1)
        runMultidrawBench(startSections, maxSections)
    } catch (e: Throwable) {
        """{"error":"${e.message ?: e.javaClass.simpleName}"}"""
    }

    /** 第 [attempt] 次测量（1 起）跑到 [fraction]（0f..1f）。 */
    data class Progress(val attempt: Int, val fraction: Float)

    /**
     * 当前进度的原始编码；-1 = 没有在跑（或渲染器版本太老没有这个计数器）。
     *
     * 在查询进程里由 binder 线程调用（[run] 正阻塞着另一条），原样送回主进程，
     * 那边用 [decodeProgress] 解开。native 把「第几次」和「这次跑到哪」编在同一个
     * 原子量里，就是为了让一次读到的两个数一定是同一时刻的。
     */
    fun rawProgress(): Int = try {
        benchProgress()
    } catch (_: Throwable) {
        -1
    }

    /** [rawProgress] 的解码侧；负数即「没有进度」。 */
    fun decodeProgress(raw: Int): Progress? = raw.takeIf { it >= 0 }?.let {
        Progress(attempt = it / 1000 + 1, fraction = (it % 1000) / 1000f)
    }
}


