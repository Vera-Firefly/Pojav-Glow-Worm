/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.firefly.version.download.engine

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 跨文件共享的主机级熔断器：
 * 海量小文件逐个"官方源优先"时，单个源的停摆要等每个文件各自超时后才换源，
 * 整批表现为长时间的 0B/s 停滞。这里按主机统计滑动窗口内的读/连接超时，
 * 窗口内达到阈值即熔断该主机，后续文件直接跳过它，冷却到期后自动重新接纳。
 *
 * 计数采用"窗口内出现次数"而非"连续次数"：停摆开始时，停摆前发出的请求
 * 仍在陆续成功，若成功即清零计数，超时将永远凑不够连续阈值。
 */
internal class SourceHealth(
    /** 窗口内超时多少次后熔断该主机 */
    private val tripThreshold: Int = TRIP_THRESHOLD,
    /** 熔断后的冷却时长 */
    private val cooldownNanos: Long = COOLDOWN_NANOS,
    /** 超时计数的滑动窗口 */
    private val windowNanos: Long = TRIP_WINDOW_NANOS
) {
    private class Host {
        val recentTimeouts = ArrayDeque<Long>()
        val trippedUntilNanos = AtomicLong(0L)
    }

    private val hosts = ConcurrentHashMap<String, Host>()

    fun recordTimeout(url: String) {
        val host = hosts.computeIfAbsent(hostOf(url)) { Host() }
        synchronized(host) {
            val now = System.nanoTime()
            host.recentTimeouts.addLast(now)
            while (host.recentTimeouts.isNotEmpty() && now - host.recentTimeouts.first() > windowNanos) {
                host.recentTimeouts.removeFirst()
            }
            if (host.recentTimeouts.size >= tripThreshold) {
                host.trippedUntilNanos.set(now + cooldownNanos)
            }
        }
    }

    /** 冷却期内返回 false；从未见过失败的主机恒为 true */
    fun isViable(url: String): Boolean {
        val host = hosts[hostOf(url)] ?: return true
        return System.nanoTime() >= host.trippedUntilNanos.get()
    }

    private fun hostOf(url: String): String =
        //host:port 作为分键：同一主机的不同端口通常对应不同的服务（测试台架/自建镜像）
        url.toHttpUrlOrNull()?.let { "${it.host}:${it.port}" } ?: url

    companion object {
        internal const val TRIP_THRESHOLD = 4
        internal val TRIP_WINDOW_NANOS = 10_000_000_000L
        internal val COOLDOWN_NANOS = 45_000_000_000L
    }
}

/** 沿因果链识别读/连接超时，作为熔断的计数依据 */
internal fun Throwable.isTimeoutError(): Boolean =
    generateSequence(this) { it.cause }.any { it is SocketTimeoutException }
