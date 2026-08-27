package net.kdt.pojavlaunch.firefly.mobileglues.settings

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 对 `MG/` 目录的访问抽象。
 *
 * 两种实现：[DirectMgStorage]（「所有文件访问」或旧版存储权限，直接走文件系统）和
 * [SafMgStorage]（SAF 目录授权，走 DocumentsContract）。native 端永远读 `/sdcard/MG` 的真实路径，
 * 这个抽象只对 App 自己的读写生效——两种授权方式写出来的是同一个目录、同一个 config.json。
 *
 * 所有函数都可能做 IO，调用方负责放到后台线程。
 */
interface MgStorage {

    /** 给用户看的路径，两种模式下都指 `/sdcard/MG`。 */
    val displayPath: String

    /** 授权此刻是否仍然有效：SAF 的 URI 可能被吊销，目录也可能被用户在外部删掉。 */
    fun isAccessible(): Boolean

    fun configExists(): Boolean

    /** 读配置全文。文件不存在或不可读时抛 [IOException]。 */
    @Throws(IOException::class)
    fun readConfig(): String

    /**
     * 写入配置全文。
     *
     * [DirectMgStorage] 是原子写（临时文件 + rename）；SAF 没有 rename 可用，
     * [SafMgStorage] 只能截断式覆写，是尽力而为。
     */
    @Throws(IOException::class)
    fun writeConfig(text: String)

    /** 配置损坏时把原文备份到配置旁边，返回备份文件名；备份失败返回 null。 */
    fun writeCorruptBackup(text: String): String?

    /**
     * 读 native 库写的 `stats.json`；文件不存在或读不动时返回 null。
     *
     * 这个文件只有 native 库写、本 App 只读：启动次数由渲染器在被游戏加载时自增，
     * 本 App 无从知道那件事发生过，只能来这里看。
     */
    fun readStats(): String?

    /** GLSL 缓存文件的字节数；文件不存在为 null。 */
    fun glslCacheBytes(): Long?

    @Throws(IOException::class)
    fun deleteGlslCache()

    /**
     * 删除 MobileGlues 和本插件在 MG 目录下自己创建的全部文件（危险区域的「移除 MobileGlues」）。
     *
     * 只动 [KNOWN_MG_FILE_NAMES] 里的文件，用户手动放进 MG 目录的其他东西不会被碰；
     * 删完之后目录若已经空了，会连目录一起删掉。
     */
    @Throws(IOException::class)
    fun deleteAll()
}

/** 直接文件访问：拥有「所有文件访问」或旧版存储权限时使用。 */
class DirectMgStorage(private val root: File) : MgStorage {

    private val configFile = File(root, CONFIG_FILE_NAME)
    private val glslCacheFile = File(root, GLSL_CACHE_FILE_NAME)

    override val displayPath: String get() = root.absolutePath

    override fun isAccessible(): Boolean = root.isDirectory || root.exists().not()

    override fun configExists(): Boolean = configFile.isFile

    override fun readConfig(): String = configFile.readText()

    override fun writeConfig(text: String) = configFile.writeAtomically(text)

    override fun writeCorruptBackup(text: String): String? = runCatching {
        val backup = File(root, CONFIG_FILE_NAME + CORRUPT_BACKUP_SUFFIX)
        backup.writeText(text)
        backup.name
    }.getOrNull()

    override fun readStats(): String? =
        runCatching { File(root, STATS_FILE_NAME).takeIf { it.isFile }?.readText() }.getOrNull()

    override fun glslCacheBytes(): Long? = glslCacheFile.takeIf { it.isFile }?.length()

    override fun deleteGlslCache() {
        if (glslCacheFile.exists() && !glslCacheFile.delete()) {
            throw IOException("Could not delete ${glslCacheFile.path}")
        }
    }

    override fun deleteAll() {
        if (!root.isDirectory) return
        for (name in KNOWN_MG_FILE_NAMES) {
            val file = File(root, name)
            if (file.exists() && !file.delete()) {
                throw IOException("Could not delete ${file.path}")
            }
        }
        // 目录本身只有在清空之后才顺手删掉：用户自己塞进来的文件会让它继续留着。
        if (root.list()?.isEmpty() == true) {
            root.delete()
        }
    }
}

/**
 * SAF 目录授权访问：用户通过系统文件选择器把 MG 目录授给本 App。
 *
 * URI 已经过 `takePersistableUriPermission` 持久化，但仍有失效路径——用户在系统设置里
 * 吊销授权、或者直接把目录删了，[isAccessible] 都会返回 false。
 */
class SafMgStorage(
    context: Context,
    private val treeUri: Uri,
) : MgStorage {

    private val appContext = context.applicationContext

    override val displayPath: String get() = "/sdcard/$MG_DIRECTORY_NAME"

    private fun tree(): DocumentFile? =
        DocumentFile.fromTreeUri(appContext, treeUri)?.takeIf { it.isDirectory }

    override fun isAccessible(): Boolean {
        val persisted = appContext.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
        return persisted && tree() != null
    }

    private fun child(name: String): DocumentFile? = tree()?.findFile(name)

    @Throws(IOException::class)
    private fun childForWrite(name: String, mimeType: String): DocumentFile {
        child(name)?.takeIf { it.isFile }?.let { return it }
        val dir = tree() ?: throw IOException("MG directory is not accessible")
        return dir.createFile(mimeType, name)
            ?: throw IOException("Could not create $name")
    }

    override fun configExists(): Boolean = child(CONFIG_FILE_NAME)?.isFile == true

    override fun readConfig(): String {
        val doc = child(CONFIG_FILE_NAME)?.takeIf { it.isFile }
            ?: throw IOException("$MG_DIRECTORY_NAME/$CONFIG_FILE_NAME not found")
        return appContext.contentResolver.openInputStream(doc.uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw IOException("Could not open ${doc.uri}")
    }

    override fun writeConfig(text: String) {
        val doc = childForWrite(CONFIG_FILE_NAME, JSON_MIME_TYPE)
        appContext.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("Could not open ${doc.uri} for writing")
    }

    override fun writeCorruptBackup(text: String): String? = runCatching {
        val name = CONFIG_FILE_NAME + CORRUPT_BACKUP_SUFFIX
        // 不能用 JSON_MIME_TYPE：扩展名对不上时系统提供器可能擅自再补一个 .json 后缀。
        val doc = childForWrite(name, FALLBACK_MIME_TYPE)
        appContext.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        }
        name
    }.getOrNull()

    override fun readStats(): String? = runCatching {
        val doc = child(STATS_FILE_NAME)?.takeIf { it.isFile } ?: return null
        appContext.contentResolver.openInputStream(doc.uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()

    override fun glslCacheBytes(): Long? =
        child(GLSL_CACHE_FILE_NAME)?.takeIf { it.isFile }?.length()

    override fun deleteGlslCache() {
        val doc = child(GLSL_CACHE_FILE_NAME) ?: return
        if (!doc.delete()) throw IOException("Could not delete $GLSL_CACHE_FILE_NAME")
    }

    override fun deleteAll() {
        val dir = tree() ?: return
        for (name in KNOWN_MG_FILE_NAMES) {
            val doc = dir.findFile(name) ?: continue
            if (!doc.delete()) throw IOException("Could not delete $name")
        }
        // 目录本身只有在清空之后才顺手删掉：用户自己塞进来的文件会让它继续留着。
        if (dir.listFiles().isEmpty()) {
            dir.delete()
        }
    }

    companion object {
        const val MG_DIRECTORY_NAME = "MG"
        private const val JSON_MIME_TYPE = "application/json"
        private const val FALLBACK_MIME_TYPE = "application/octet-stream"
    }
}

internal const val CONFIG_FILE_NAME = "config.json"
internal const val GLSL_CACHE_FILE_NAME = "glsl_cache.tmp"
internal const val STATS_FILE_NAME = "stats.json"
internal const val LOG_FILE_NAME = "latest.log"
internal const val GL_CALLS_FILE_NAME = "glcalls.txt"
internal const val CORRUPT_BACKUP_SUFFIX = ".corrupt"
private const val CONFIG_TEMP_FILE_NAME = CONFIG_FILE_NAME + ".tmp"

/**
 * MobileGlues（native 库）和本插件会在 MG 目录下主动创建的全部文件名。
 *
 * 「撤销并删除全部文件」只删这些——MG 目录是用户看得见的目录，谁都可能手动放点别的东西
 * 进去，那些文件不是我们创建的，也就没资格被我们删掉。
 */
internal val KNOWN_MG_FILE_NAMES = listOf(
    CONFIG_FILE_NAME,
    CONFIG_TEMP_FILE_NAME,
    CONFIG_FILE_NAME + CORRUPT_BACKUP_SUFFIX,
    GLSL_CACHE_FILE_NAME,
    STATS_FILE_NAME,
    LOG_FILE_NAME,
    GL_CALLS_FILE_NAME,
)

/**
 * 先写临时文件再 rename。
 *
 * 同目录下的 rename 是原子的，所以读的一方（游戏里的 MobileGlues）要么看到旧内容，
 * 要么看到完整的新内容，不会看到写到一半的文件。
 */
private fun File.writeAtomically(text: String) {
    parentFile?.mkdirs()
    val temporary = File(parentFile, "$name.tmp")
    FileOutputStream(temporary).use { output ->
        output.write(text.toByteArray())
        output.flush()
        output.fd.sync()
    }
    if (!temporary.renameTo(this)) {
        // 同目录 rename 正常不会失败；万一失败就退回直接写，至少不会把配置丢掉。
        temporary.delete()
        writeText(text)
    }
}


