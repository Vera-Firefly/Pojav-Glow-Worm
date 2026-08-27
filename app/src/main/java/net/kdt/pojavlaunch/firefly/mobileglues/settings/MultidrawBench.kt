package net.kdt.pojavlaunch.firefly.mobileglues.settings

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** 某一个函数这次测得怎么样。 */
data class MultidrawBenchQuality(
    /** 各轮之间的离散度（MAD/中位数）：这个函数上的数字之间差多少才算真的差。 */
    val noise: Double,
    val rounds: Int,
    /** 这个函数测了几遍。抖得压不下去时 native 会放大场景单独重测它。 */
    val attempts: Int,
    /**
     * 这个函数的数字是在多大的场景上量的。
     *
     * 抖动会让 native 把场景翻倍重测，而先压到目标的函数就此定格在较小的场景上——
     * 于是同一份结果里，不同函数的微秒数可能来自不同规模。名次不受影响（每个函数
     * 各自在同一趟里排），但把两个函数的绝对耗时摆在一起比就是错的。
     */
    val sections: Int,
    /** 重测到头仍然没压到目标，这个函数的排名只能算参考。 */
    val noisy: Boolean,
)

/**
 * MultiDraw 微基准的结果：每个入口点 → 每个后端 → 每次调用多少微秒（多轮的中位数）。
 *
 * JSON 由 native 端 `gl/multidraw_bench.cpp` 生成。没测出来的后端（设备不支持、
 * 或测量中发生了降级）直接缺席——宁缺毋滥，一个错的数字比没有数字糟得多。
 *
 * 成色是分函数记的（[quality]）：每个函数各自排名，也就各自判抖不抖、各自重测。
 * 老版本渲染器不报这一段，缺席即空。
 */
data class MultidrawBenchReport(
    val timings: Map<MultidrawEntry, Map<MultidrawBackend, Double>>,
    /** 每个函数各自测得怎么样。抖与不抖是分函数判的，重测也是分函数做的。 */
    val quality: Map<MultidrawEntry, MultidrawBenchQuality> = emptyMap(),
    val elapsedMs: Double = 0.0,
    /** 配置要求用 ANGLE。 */
    val angleRequested: Boolean = false,
    /** ANGLE 真的加载上了。渲染器加载不到会不声不响退回系统驱动。 */
    val angleInUse: Boolean = false,
    /** 用户配置的原始档位（[AngleConfig] 的 wire 值）；老渲染器不报，缺席为 -1。 */
    val angleConfigured: Int = -1,
    /** 设备过不过得了 ANGLE 探测（Vulkan 1.2 且非 Adreno 730/740）；老渲染器缺席按 true。 */
    val angleSupported: Boolean = true,
    val renderer: String? = null,
    /**
     * 这一趟结束时场景有多大（section 数）；0 = 渲染器没报（旧版本）。
     *
     * [error] 是 "context-lost" 时这就是把上下文撑爆的那个规模——调用方要靠它算出
     * 下一次的上限。所以它在出错的那条路径上也必须解出来，不能跟着结果一起丢掉。
     */
    val sections: Int = 0,
    val error: String? = null,
) {
    /**
     * 这次是在错的驱动上测的：配置要 ANGLE，实际跑的却是系统驱动。
     *
     * ANGLE 是 GLES-on-Vulkan，扩展支持、baseVertex 的实现方式、compute 的开销都与原生
     * 驱动是两套东西，此时的名次挪到游戏里不成立，采用它反而会把配置带偏。
     */
    val wrongDriver: Boolean get() = angleRequested && !angleInUse

    companion object {

        fun parse(text: String?): MultidrawBenchReport {
            val root = runCatching {
                JsonParser.parseString(text.orEmpty()).asJsonObject
            }.getOrNull() ?: return MultidrawBenchReport(emptyMap(), error = "unparseable result")

            val sections = root.get("sections")?.let { runCatching { it.asInt }.getOrNull() } ?: 0

            root.get("error")?.takeIf { it.isJsonPrimitive }?.let {
                // sections 要跟着错误一起带出去：上下文丢失时，撑爆它的那个规模就是
                // 调用方唯一能拿来退一步的依据。
                return MultidrawBenchReport(emptyMap(), sections = sections, error = it.asString)
            }

            val entriesObj = root.get("entries")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return MultidrawBenchReport(emptyMap(), error = "no entries in result")
            val qualityObj = root.get("quality")?.takeIf { it.isJsonObject }?.asJsonObject

            val timings = MultidrawEntry.entries.mapNotNull { entry ->
                val obj = entriesObj.get(entry.glFunction)
                    ?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val perBackend = obj.entrySet().mapNotNull { (name, value) ->
                    val backend = MultidrawBackend.parse(name) ?: return@mapNotNull null
                    val us = runCatching { value.asDouble }.getOrNull() ?: return@mapNotNull null
                    backend to us
                }.toMap()
                if (perBackend.isEmpty()) null else entry to perBackend
            }.toMap()

            val quality = MultidrawEntry.entries.mapNotNull { entry ->
                val obj = qualityObj?.get(entry.glFunction)
                    ?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                entry to MultidrawBenchQuality(
                    noise = obj.get("noise")?.let { runCatching { it.asDouble }.getOrNull() } ?: 0.0,
                    rounds = obj.get("rounds")?.let { runCatching { it.asInt }.getOrNull() } ?: 0,
                    attempts = obj.get("attempts")?.let { runCatching { it.asInt }.getOrNull() } ?: 1,
                    sections = obj.get("sections")?.let { runCatching { it.asInt }.getOrNull() } ?: sections,
                    noisy = obj.get("noisy")?.let { runCatching { it.asBoolean }.getOrNull() } == true,
                )
            }.toMap()

            return if (timings.isEmpty()) {
                MultidrawBenchReport(emptyMap(), sections = sections, error = "empty result")
            } else {
                MultidrawBenchReport(
                    timings = timings,
                    quality = quality,
                    elapsedMs = root.get("elapsedMs")
                        ?.let { runCatching { it.asDouble }.getOrNull() } ?: 0.0,
                    angleRequested = root.get("angleRequested")
                        ?.let { runCatching { it.asBoolean }.getOrNull() } == true,
                    angleInUse = root.get("angleInUse")
                        ?.let { runCatching { it.asBoolean }.getOrNull() } == true,
                    angleConfigured = root.get("angleConfigured")
                        ?.let { runCatching { it.asInt }.getOrNull() } ?: -1,
                    angleSupported = root.get("angleSupported")
                        ?.let { runCatching { it.asBoolean }.getOrNull() } != false,
                    renderer = root.get("renderer")
                        ?.let { runCatching { it.asString }.getOrNull() },
                    sections = sections,
                )
            }
        }
    }
}

/** 排好序的一项推荐：条目 + 相对耗时（最快 = 1.0；没测出来为 null，排在末尾）。 */
data class RankedItem<T>(val item: T, val relativeCost: Double?)

/**
 * 从跑分结果算推荐排序。
 *
 * 只按函数排，不合成全局排序：全局排序要一个次序同时管住五个函数，而这五个函数的绝对耗时
 * 差着数量级（unroll 一次 96 个驱动调用，multiindirect 一次一个），怎么加权都是拿一个
 * 在每个函数上都不是最优的顺序冒充最优。测出来的东西本来就是分函数的，就分函数交出去。
 */
object MultidrawBenchAnalyzer {

    fun rankEntry(
        report: MultidrawBenchReport,
        entry: MultidrawEntry,
    ): List<RankedItem<MultidrawBackend>> {
        val perBackend = report.timings[entry] ?: emptyMap()
        val fastest = perBackend.values.minOrNull()
        val ranked = perBackend.entries.sortedBy { it.value }
            .map { RankedItem(it.key, it.value / fastest!!) }
        val unmeasured = entry.implemented
            .filter { it !in perBackend }
            .map { RankedItem(it, null) }
        return ranked + unmeasured
    }
}


