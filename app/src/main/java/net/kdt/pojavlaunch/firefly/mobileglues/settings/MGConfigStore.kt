package net.kdt.pojavlaunch.firefly.mobileglues.settings

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** [MGConfigStore.load] 的结果。加载失败不等于「用默认值覆盖」，由调用方决定怎么办。 */
sealed interface ConfigLoadResult {

    /** 配置文件不存在。此时 [MGConfigStore.config] 会变成默认值，但磁盘上什么都没写。 */
    data object Missing : ConfigLoadResult

    data class Loaded(val config: MGConfig) : ConfigLoadResult

    /** JSON 本身无法解析。原文件已尽量备份到 [backup]，配置保持「未加载」直到用户决定。 */
    data class Corrupt(val backupName: String?, val cause: Throwable) : ConfigLoadResult
}

sealed interface ConfigStoreEvent {
    data class SaveFailed(val cause: Throwable) : ConfigStoreEvent
}

/**
 * 全 App 唯一会碰 `MG/config.json` 的地方。
 *
 * 设计要点：
 *  - [config] 为 `null` 表示「尚未加载」。UI 的回调在这个阶段一律无视——这取代了原先的
 *    `isSpinnerInitialized` / `config == null` 两个标志位，而且含义是明确的状态而不是时序。
 *  - [update] 只改内存并立刻发出新状态，落盘去抖后异步进行。连续敲键盘不再等于连续写外部存储。
 *  - 直接文件访问下写入是原子的（临时文件 + fsync + rename，见 [MgStorage.writeConfig]）。
 *    native 端会在游戏启动时读这个文件，截断式写入可能让它读到半截 JSON。
 *  - 写入失败通过 [events] 上报，不再被 `runCatching {}` 静默吞掉。
 */
class MGConfigStore(
    storage: MgStorage?,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val saveDebounceMillis: Long = SAVE_DEBOUNCE_MILLIS,
) {

    private val gson = Gson()

    /**
     * 当前生效的存储访问。授权方式（所有文件访问 / SAF）由外面的 AuthController 决定，
     * store 只关心「往哪里读写」；授权失效时外面会调 [detachStorage]。
     */
    @Volatile
    private var storage: MgStorage? = storage

    private val mutableConfig = MutableStateFlow<MGConfig?>(null)

    /** `null` = 尚未加载。 */
    val config: StateFlow<MGConfig?> = mutableConfig.asStateFlow()

    private val mutableEvents = MutableSharedFlow<ConfigStoreEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ConfigStoreEvent> = mutableEvents.asSharedFlow()

    /**
     * GLSL 缓存文件的大小（字节），`null` 表示文件不存在。
     *
     * 界面据此决定要不要给出「删除缓存」按钮、以及在按钮上显示多大，所以它是状态而不是
     * 一次性查询：删除之后必须立刻反映出来，把按钮收回去。
     */
    private val mutableGlslCacheBytes = MutableStateFlow<Long?>(null)
    val glslCacheBytes: StateFlow<Long?> = mutableGlslCacheBytes.asStateFlow()

    /** 配置文件里本 App 不认识的键（例如 native 的 `hideMGEnvLevel`），保存时原样写回。 */
    private var foreignKeys: JsonObject? = null

    /**
     * 内存里的配置和磁盘上的不一致。
     *
     * 只有它为 true 才会真的写文件。没有这个判据的话，「退到后台就 flush 一次」会在好几种场景下
     * 写出不该写的东西：配置文件损坏后用户选了取消、用户在外部删掉了 MG 目录、或者用户根本什么都没改。
     */
    @Volatile
    private var dirty = false

    private val pendingSave = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 串行化所有对 [configFile] 的读写。 */
    private val fileLock = Mutex()

    init {
        scope.launch { collectPendingSaves() }
    }

    /** 授权建立后接入存储。更换授权方式时先 [detachStorage] 再 attach。 */
    fun attachStorage(storage: MgStorage) {
        this.storage = storage
    }

    /**
     * 授权失效后摘掉存储，store 回到「未加载」，也不再可能写盘。
     * 否则用户在外部删掉 MG 目录后，退到后台的那次保存会把目录连同配置一起重建出来。
     */
    fun detachStorage() {
        storage = null
        forget()
    }

    @Throws(IOException::class)
    private fun requireStorage(): MgStorage =
        storage ?: throw IOException("MG directory is not authorized")

    @OptIn(FlowPreview::class)
    private suspend fun collectPendingSaves() {
        pendingSave.debounce(saveDebounceMillis).collect { persist() }
    }

    suspend fun load(): ConfigLoadResult = fileLock.withLock {
        // 先把还没落盘的改动写出去。否则重新读盘会把用户刚刚改的值覆盖回旧值——
        // Activity 重建（旋转屏幕）时 onResume 的这次 load 正好会撞进去抖窗口里。
        persistLocked()
        withContext(io) { readConfigFile() }
    }

    /**
     * 修改配置。[transform] 返回的结果与当前值相等时什么都不会发生——这让所有
     * 「界面回灌触发的回调」天然是幂等的。
     */
    fun update(transform: (MGConfig) -> MGConfig) {
        val current = mutableConfig.value ?: return
        val next = transform(current)
        if (next == current) return
        mutableConfig.value = next
        dirty = true
        pendingSave.tryEmit(Unit)
    }

    /** 把去抖队列里还没写的改动立刻落盘，并等待完成。 */
    suspend fun flush() = persist()

    /**
     * 同 [flush]，但跑在 store 自己的作用域上。
     *
     * Activity 的 onStop 必须用这个：`lifecycleScope` 会在 onDestroy 时被取消，
     * 旋转屏幕产生的那次 flush 可能还没轮到 IO 线程执行就被砍掉了。
     */
    fun flushAsync() {
        scope.launch { persist() }
    }

    /** 配置文件损坏后由用户确认的重置。 */
    suspend fun resetToDefaults() {
        foreignKeys = null
        mutableConfig.value = MGConfig.Default
        dirty = true
        persist()
    }

    /**
     * 回到「未加载」状态，UI 的回调随即失效，并且不会再写盘。
     *
     * 在两种情况下必须调用：MG 目录被删除（不管是本 App 删的还是用户在外部删的），
     * 以及配置文件损坏而用户选择了不重置。
     */
    fun forget() {
        foreignKeys = null
        mutableConfig.value = null
        dirty = false
    }

    /**
     * 删除 GLSL 缓存文件。
     *
     * 这是一条只由用户显式触发的命令：既不是「把缓存上限设成关闭」的副作用，也不会在读取配置时
     * 顺手执行——删掉的是用户已经积累好的着色器缓存，不能由赋值语句代劳。
     */
    suspend fun clearGlslCache(): Result<Unit> = withContext(io) {
        runCatching {
            requireStorage().deleteGlslCache()
        }.also { refreshGlslCacheFile() }
    }

    private fun refreshGlslCacheFile() {
        mutableGlslCacheBytes.value = runCatching { storage?.glslCacheBytes() }.getOrNull()
    }

    /** 把当前配置导出到应用私有目录（供 MGInfoGetter 读取），不影响用户的配置文件。 */
    suspend fun exportTo(file: File): Result<File> = withContext(io) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(serialize(mutableConfig.value ?: MGConfig.Default))
            file
        }
    }

    private fun readConfigFile(): ConfigLoadResult {
        // 顺便刷新缓存文件的状态：游戏在后台跑过一轮之后它可能才出现、或者变大了。
        refreshGlslCacheFile()

        val storage = try {
            requireStorage()
        } catch (e: IOException) {
            forget()
            return ConfigLoadResult.Corrupt(backupName = null, cause = e)
        }

        if (!storage.configExists()) {
            foreignKeys = null
            mutableConfig.value = MGConfig.Default
            // 内存里有默认值而磁盘上什么都没有，两边确实不一致：让调用方 flush 一次即可建立文件。
            dirty = true
            return ConfigLoadResult.Missing
        }

        val text = try {
            storage.readConfig()
        } catch (e: Exception) {
            forget()
            return ConfigLoadResult.Corrupt(backupName = null, cause = e)
        }

        val root = try {
            JsonParser.parseString(text).asJsonObject
        } catch (e: Exception) {
            // forget() 之后 store 不再持有任何配置，也就不可能有哪次 flush 把损坏的文件覆盖掉。
            val backup = storage.writeCorruptBackup(text)
            forget()
            return ConfigLoadResult.Corrupt(backupName = backup, cause = e)
        }

        val config = MGConfigCodec.decode(root)
        foreignKeys = MGConfigCodec.foreignKeysOf(root)
        mutableConfig.value = config
        dirty = false
        return ConfigLoadResult.Loaded(config)
    }

    private suspend fun persist() = fileLock.withLock { persistLocked() }

    /** 调用方必须已经持有 [fileLock]。 */
    private suspend fun persistLocked() {
        val config = mutableConfig.value
        if (!dirty || config == null) return

        val payload = serialize(config)
        // NonCancellable：写到一半被取消会留下一个只有临时文件、没有落地的保存。
        val outcome = withContext(NonCancellable + io) {
            runCatching { requireStorage().writeConfig(payload) }
        }

        outcome.onFailure { mutableEvents.tryEmit(ConfigStoreEvent.SaveFailed(it)) }
        // 写的过程中配置又被改了的话保持 dirty，那次改动自己排了一次保存。
        if (outcome.isSuccess && mutableConfig.value === config) dirty = false
    }

    private fun serialize(config: MGConfig): String =
        gson.toJson(MGConfigCodec.encode(config, foreignKeys))

    companion object {
        private const val SAVE_DEBOUNCE_MILLIS = 300L
    }
}


