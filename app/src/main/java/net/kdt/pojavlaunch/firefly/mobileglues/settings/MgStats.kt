package net.kdt.pojavlaunch.firefly.mobileglues.settings

import com.google.gson.JsonParser

/**
 * `MG/stats.json`：native 库自己记的运行数据。
 *
 * 只读。写的一方是渲染器——它被游戏加载时把启动次数加一，本 App 的进程根本不在场，
 * 所以这里既不该、也没办法去改它。
 */
data class MgStats(val launchCount: Int) {

    companion object {
        val Empty = MgStats(launchCount = 0)

        /**
         * 解析 stats.json。
         *
         * 任何读不懂的情况都回落到 [Empty]：这个文件坏了顶多让赞助弹窗晚问一阵子，
         * 不值得为它打断用户——和 config.json 损坏时的处理完全不同，那个必须叫住用户。
         */
        fun parse(text: String?): MgStats {
            if (text.isNullOrBlank()) return Empty
            return runCatching {
                val root = JsonParser.parseString(text).asJsonObject
                val count = root.get(KEY_LAUNCH_COUNT)
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asInt
                    ?.coerceAtLeast(0)
                    ?: 0
                MgStats(launchCount = count)
            }.getOrDefault(Empty)
        }

        /** 与 native 的 config/stats.cpp 一致。 */
        private const val KEY_LAUNCH_COUNT = "launchCount"
    }
}


