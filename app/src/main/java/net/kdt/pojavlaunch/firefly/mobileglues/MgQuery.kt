package net.kdt.pojavlaunch.firefly.mobileglues

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 把一次渲染器查询放进一次性的 [MgQueryService] 进程里执行。
 *
 * 为什么不能就地跑：见 [MgQueryService] 顶部——库进了进程就出不去，环境和配置只被
 * 第一次查询读到。这里每次查询绑一次服务、查完解绑，服务进程随解绑自杀，于是
 * 「每次查询都从头初始化」从祈愿变成结构保证。
 *
 * 同一时刻只允许一个查询在跑：两个查询若共用一个服务进程，先结束的那个解绑会把进程
 * 连同另一个查询一起杀掉。互斥而不是并发，是这个设计的一部分。
 */
class MgQuery(private val context: Context) {

    /** 查询进程没绑上，或半路死了（多半是驱动在里面崩了）。 */
    class RemoteFailure(message: String, cause: Throwable? = null) : IOException(message, cause)

    private val mutex = Mutex()

    /**
     * 绑定 → 执行 [block] → 解绑（进而杀掉查询进程）。
     *
     * [block] 里的 binder 调用是阻塞的（跑分要以分钟计），必须已经在后台调度器上。
     * 查询进程死亡时，进行中的调用抛 DeadObjectException，由调用方兜成错误报告。
     */
    suspend fun <T> use(block: suspend (IMgQuery) -> T): T = mutex.withLock {
        val ready = CompletableDeferred<IMgQuery>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                ready.complete(IMgQuery.Stub.asInterface(service))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // 只影响还没连上的等待者；已经在飞的调用自己会收到 DeadObjectException。
                ready.completeExceptionally(RemoteFailure("query process died while connecting"))
            }

            override fun onNullBinding(name: ComponentName?) {
                ready.completeExceptionally(RemoteFailure("query service refused the binding"))
            }
        }
        // bindService 返回 false 也要求 unbind，所以两条路径都走同一个 finally。
        val requested = context.bindService(
            Intent(context, MgQueryService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        try {
            if (!requested) throw RemoteFailure("bindService failed")
            block(ready.await())
        } finally {
            runCatching { context.unbindService(connection) }
        }
    }
}


