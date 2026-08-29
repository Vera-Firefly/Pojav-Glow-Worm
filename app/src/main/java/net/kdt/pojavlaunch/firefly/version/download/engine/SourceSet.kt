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

import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

/**
 * 一个下载任务的候选源集合
 * 带失败计数与健康轮转，全部失效后由 [FileDownloader] 进入降级阶段逐源单流重试。
 * [health] 由整批共享时，超时风暴会触发主机级熔断，让后续文件直接跳到其他源，而不是逐文件各付一次超时。
 */
internal class SourceSet(
    urls: List<String>,
    private val health: SourceHealth = SourceHealth()
) {
    inner class Source internal constructor(
        @JvmField val url: String,
        @JvmField val index: Int
    ) {
        private val failCount = AtomicInteger(0)

        @Volatile
        internal var disabled = false

        /** 404/502/DNS 解析失败等，本轮直接出局 */
        @Volatile
        internal var fatal = false

        /** 该源对 Range 请求返回了无范围应答 */
        @Volatile
        internal var noRange = false

        @Volatile
        internal var lastReason: String? = null

        val supportsRange: Boolean get() = !noRange

        fun recordSuccess() {
            failCount.set(0)
            lastReason = null
        }

        /** 记录一次失败；返回该源是否仍然可用 */
        fun recordFailure(error: Throwable): Boolean {
            lastReason = error.message ?: error.toString()
            if (error.isTimeoutError()) health.recordTimeout(url)
            if (disableImmediately(error)) {
                fatal = true
                disabled = true
                return false
            }
            if (failCount.incrementAndGet() >= SOFT_FAILURE_LIMIT) {
                disabled = true
                return false
            }
            return true
        }

        fun markNoRangeSupport() {
            noRange = true
        }
    }

    private val sources: List<Source> = urls.distinct().mapIndexed { index, url -> Source(url, index) }
    private val cursor = AtomicInteger(0)

    /**
     * 从游标处轮转挑出下一个健康源，要求支持 Range 时跳过不支持的源。
     * 第一轮会跳过处于熔断冷却中的主机
     * 若所有源都被熔断（或不可用），退回"仅按健康状态"再扫一遍，保证极端情况下仍有源可试。
     */
    fun acquire(requireRange: Boolean): Source? {
        val size = sources.size
        repeat(size) {
            val candidate = sources[Math.floorMod(cursor.getAndAdd(1), size)]
            if (isUsable(candidate, requireRange) && health.isViable(candidate.url)) {
                return candidate
            }
        }
        repeat(size) {
            val candidate = sources[Math.floorMod(cursor.getAndAdd(1), size)]
            if (isUsable(candidate, requireRange)) {
                return candidate
            }
        }
        return null
    }

    private fun isUsable(source: Source, requireRange: Boolean): Boolean =
        !source.disabled && !source.fatal && !(requireRange && source.noRange)

    val hasUsable: Boolean get() = sources.any { !it.disabled && !it.fatal }

    /**
     * 进入降级阶段：清空非致命源的失败名单，允许它们以单流模式再轮一遍。
     * 致命源（如确切的 404）不会复活。
     */
    fun degrade() {
        sources.forEach { source ->
            if (!source.fatal) {
                source.disabled = false
                source.lastReason = null
            }
        }
    }

    fun describe(): String =
        sources.joinToString("\n") { source ->
            "- ${source.url}${source.lastReason?.let { reason -> ": $reason" } ?: ""}"
        }

    companion object {
        internal const val SOFT_FAILURE_LIMIT = 3

        fun disableImmediately(error: Throwable): Boolean = when (error) {
            is HttpResultException -> error.code == 404 || error.code == 410 || error.code == 502
            is UnknownHostException -> true
            else -> false
        }
    }
}
