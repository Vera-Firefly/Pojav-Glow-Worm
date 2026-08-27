package net.kdt.pojavlaunch.firefly.mobileglues.settings

import com.google.gson.JsonObject

/**
 * `MG/config.json` 的读写格式。
 *
 * 这里是键名、以及「磁盘上的值不合法时用什么」的唯一定义处；除此之外没有第二个地方
 * 知道 config.json 长什么样。
 *
 * 两条约定：
 *  - [decode] 永不抛异常。单个字段坏掉（缺失、越界、类型不对）只会让该字段回落到默认值，
 *    不会连累整份配置——只有 JSON 本身语法错误才算文件损坏，那由 [MGConfigStore] 处理。
 *  - [encode] 会带上 [foreignKeysOf] 取出的未知键。native 端读的键比 App 认识的多
 *    （例如 `hideMGEnvLevel`），不把它们写回去就等于每次保存都在删别人的设置。
 */
internal object MGConfigCodec {

    private const val KEY_ANGLE = "enableANGLE"
    private const val KEY_NO_ERROR = "enableNoError"
    private const val KEY_EXT_TIMER_QUERY = "enableExtTimerQuery"
    private const val KEY_EXT_COMPUTE_SHADER = "enableExtComputeShader"
    private const val KEY_EXT_DIRECT_STATE_ACCESS = "enableExtDirectStateAccess"
    private const val KEY_GLSL_CACHE = "maxGlslCacheSize"

    /**
     * 已废弃的三代旧键：native 不再读取，只会在它们还存在时打弃用警告。
     * 保留在 [KNOWN_KEYS] 里是为了让下一次保存把它们清掉，而不是当成未知键永久留着。
     */
    private const val KEY_MULTIDRAW_LEGACY = "multidrawMode"
    private const val KEY_MULTIDRAW_DISABLE_LEGACY = "multidrawDisableBackends"

    private const val KEY_MULTIDRAW_ORDER = "multidrawOrder"
    private const val KEY_DEPTH_CLEAR_FIX = "angleDepthClearFixMode"
    private const val KEY_GL_VERSION = "customGLVersion"
    private const val KEY_FSR1 = "fsr1Setting"

    private val KNOWN_KEYS = listOf(
        KEY_ANGLE,
        KEY_NO_ERROR,
        KEY_EXT_TIMER_QUERY,
        KEY_EXT_COMPUTE_SHADER,
        KEY_EXT_DIRECT_STATE_ACCESS,
        KEY_GLSL_CACHE,
        KEY_MULTIDRAW_LEGACY,
        KEY_MULTIDRAW_DISABLE_LEGACY,
        KEY_MULTIDRAW_ORDER,
        KEY_DEPTH_CLEAR_FIX,
        KEY_GL_VERSION,
        KEY_FSR1,
    ) + MultidrawEntry.entries.flatMap { listOf(it.orderKey, it.legacyModeKey) }

    fun decode(root: JsonObject): MGConfig {
        val defaults = MGConfig.Default
        return MGConfig(
            // 缺键的回落必须与渲染器一致：native 的 config_get_int 对缺键回 -1，越界
            // 一律按 DisableIfPossible 跑。这里若回落到 App 自己的默认（尽可能启用），
            // 界面就会宣称一个游戏里并不成立的档位——老版本遗留的 config.json 正是这样。
            angle = AngleConfig.entries.fromWire(root.intOrNull(KEY_ANGLE), AngleConfig.DisableIfPossible),
            noError = NoErrorConfig.entries.fromWire(root.intOrNull(KEY_NO_ERROR), defaults.noError),
            multidraw = decodeMultidraw(root),
            depthClearFix = DepthClearFixMode.entries
                .fromWire(root.intOrNull(KEY_DEPTH_CLEAR_FIX), defaults.depthClearFix),
            glVersion = GlVersion.fromWire(root.intOrNull(KEY_GL_VERSION)),
            glslCache = GlslCacheSize.fromWire(root.intOrNull(KEY_GLSL_CACHE)),
            extComputeShader = root.boolOrNull(KEY_EXT_COMPUTE_SHADER)
                ?: defaults.extComputeShader,
            extTimerQuery = root.boolOrNull(KEY_EXT_TIMER_QUERY) ?: defaults.extTimerQuery,
            extDirectStateAccess = root.boolOrNull(KEY_EXT_DIRECT_STATE_ACCESS)
                ?: defaults.extDirectStateAccess,
            fsr1 = Fsr1Preset.entries.fromWire(root.intOrNull(KEY_FSR1), defaults.fsr1),
        )
    }

    fun encode(config: MGConfig, foreignKeys: JsonObject?): JsonObject =
        (foreignKeys?.deepCopy() ?: JsonObject()).apply {
            addProperty(KEY_ANGLE, config.angle.wire)
            addProperty(KEY_NO_ERROR, config.noError.wire)
            addProperty(KEY_EXT_TIMER_QUERY, config.extTimerQuery.wire)
            addProperty(KEY_EXT_COMPUTE_SHADER, config.extComputeShader.wire)
            addProperty(KEY_EXT_DIRECT_STATE_ACCESS, config.extDirectStateAccess.wire)
            addProperty(KEY_GLSL_CACHE, config.glslCache.wire)
            addProperty(KEY_DEPTH_CLEAR_FIX, config.depthClearFix.wire)
            addProperty(KEY_GL_VERSION, config.glVersion.wire)
            addProperty(KEY_FSR1, config.fsr1.wire)
            encodeMultidraw(config.multidraw)
        }

    private fun decodeMultidraw(root: JsonObject): MultidrawSettings = MultidrawSettings(
        // 与 native 一致：不认识的名字丢弃，重复项保留首次出现，漏掉的项按默认顺序补齐。
        globalOrder = MultidrawOrderItem.normalize(
            root.stringOrNull(KEY_MULTIDRAW_ORDER)
                .orEmpty()
                .split(',', ';')
                .mapNotNull { MultidrawOrderItem.parse(it) },
        ),
        exceptions = MultidrawEntry.entries.mapNotNull { entry ->
            // 键存在即例外开启；内容再怎么残缺，normalize 都会补成全项置换。
            val raw = root.stringOrNull(entry.orderKey) ?: return@mapNotNull null
            entry to entry.normalize(
                raw.split(',', ';').mapNotNull { MultidrawBackend.parse(it) },
            )
        }.toMap(),
    )

    private fun JsonObject.encodeMultidraw(settings: MultidrawSettings) {
        // 默认顺序就是「不写这个键」，两种等价表示只能留一种。
        if (settings.globalOrder == MultidrawOrderItem.DefaultOrder) {
            remove(KEY_MULTIDRAW_ORDER)
        } else {
            addProperty(KEY_MULTIDRAW_ORDER, settings.globalOrder.joinToString(",") { it.key })
        }

        MultidrawEntry.entries.forEach { entry ->
            val exception = settings.exceptions[entry]
            if (exception == null) {
                remove(entry.orderKey)
            } else {
                addProperty(entry.orderKey, exception.joinToString(",") { it.key })
            }
            // 三代旧键 native 已不再读取，留着只会让它每次启动都打弃用警告。
            remove(entry.legacyModeKey)
        }
        remove(KEY_MULTIDRAW_LEGACY)
        remove(KEY_MULTIDRAW_DISABLE_LEGACY)
    }

    /** 取出配置文件里本 App 不认识的键，保存时原样写回。 */
    fun foreignKeysOf(root: JsonObject): JsonObject =
        root.deepCopy().apply { KNOWN_KEYS.forEach { remove(it) } }

    /** native 端一律用 `> 0` 判断布尔开关，这里保持一致。 */
    private val Boolean.wire: Int get() = if (this) 1 else 0

    private fun JsonObject.intOrNull(key: String): Int? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return runCatching {
            // 容忍被手工改成字符串的数字（"32"）：读进来之后下一次保存会写回真正的整数。
            if (primitive.isNumber) primitive.asInt else primitive.asString.trim().toInt()
        }.getOrNull()
    }

    private fun JsonObject.boolOrNull(key: String): Boolean? = intOrNull(key)?.let { it > 0 }

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asString }.getOrNull()
    }
}


