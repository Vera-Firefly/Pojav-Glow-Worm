package com.movtery.feature.mod.parser

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.moandjiezana.toml.Toml
import com.movtery.task.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.Tools.DIR_CACHE
import org.apache.commons.io.FileUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.jar.JarInputStream
import kotlin.collections.chunked
import kotlin.collections.map

class ModParser {
    companion object {
        /**
         * 获取当前版本的所有模组的模组信息
         */
        @JvmStatic
        fun checkAllMods(gameDir: File, parserListener: ModParserListener) {
            File(gameDir, "mods").apply {
                if (exists() && isDirectory && (listFiles()?.isNotEmpty() == true)) {
                    ModParser().parseAllMods(this, parserListener)
                    return
                }
            }
            parserListener.onParseEnded(emptyList())
        }
    }

    /**
     * 异步解析模组文件夹中的所有模组
     * @param modsFolder 模组文件夹
     * @param listener 解析过程监听器
     */
    fun parseAllMods(modsFolder: File, listener: ModParserListener) {
        val gson = GsonBuilder().disableHtmlEscaping().create()
        val cacheFileName = "${modsFolder.absolutePath.replace(File.separator, "-")}.cache"
        val infoCacheFile = File(DIR_CACHE, cacheFileName)
        val modInfoQueue = ConcurrentLinkedQueue<ModInfo>()
        val newCacheMap = ConcurrentHashMap<String, ModInfoCache>()

        Task.runTask {
            if (modsFolder.isDirectory) {
                val files = modsFolder.listFiles()
                    ?.filter { it.extension.equals("jar", true) }
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@runTask

                val existingCache = loadCache(gson, infoCacheFile)

                runBlocking {
                    val optimalThreads = calculateOptimalThreads(files.size)
                    val semaphore = Semaphore(optimalThreads)

                    files.chunked(calculateChunkSize(files.size)).forEach { batch ->
                        val deferredList = batch.map { file ->
                            async(Dispatchers.IO) {
                                semaphore.withPermit {
                                    parseModFile(file, files.size, existingCache, newCacheMap, modInfoQueue, listener)
                                }
                            }
                        }
                        //等待所有解析任务完成
                        deferredList.awaitAll()
                    }
                }
            }
        }.onThrowable { e ->
            Log.e("ModParser", "An exception occurred while parsing all mods!", e)
        }.finallyTask {
            persistCache(gson, infoCacheFile, newCacheMap.values.toList())
            listener.onParseEnded(modInfoQueue.toList())
        }.execute()
    }

    private fun parseModFile(
        modFile: File,
        totalCount: Int,
        existingCache: Map<String, ModInfoCache>,
        newCache: MutableMap<String, ModInfoCache>,
        modQueue: ConcurrentLinkedQueue<ModInfo>,
        listener: ModParserListener
    ) {
        val fileHash = calculateFileHash(modFile)

        existingCache[fileHash]?.let { cached ->
            val modInfo = cached.modInfo.apply { file = modFile }
            modQueue.add(modInfo)
            listener.onProgress(modInfo, totalCount)
            newCache[fileHash] = cached
            return
        }

        parseModContents(modFile)?.let { modInfo ->
            modQueue.add(modInfo)
            listener.onProgress(modInfo, totalCount)
            newCache[fileHash] = ModInfoCache(fileHash, modInfo)
        }
    }

    @Throws(Exception::class)
    fun calculateFileHash(file: File, algorithm: String = "SHA-256"): String {
        return calculateFileHash(file.inputStream(), algorithm)
    }

    @Throws(Exception::class)
    fun calculateFileHash(inputStream: InputStream, algorithm: String = "SHA-256"): String {
        val digest = MessageDigest.getInstance(algorithm)
        inputStream.use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().toHex()
    }

    /**
     * 字节数组转十六进制字符串（高效实现）
     */
    private fun ByteArray.toHex(): String {
        val hexChars = "0123456789abcdef"
        return joinToString("") { byte ->
            "${hexChars[byte.toInt() shr 4 and 0x0F]}${hexChars[byte.toInt() and 0x0F]}"
        }
    }

    private fun parseModContents(modFile: File): ModInfo? {
        return try {
            JarInputStream(FileInputStream(modFile)).use { jarStream ->
                locateModDescriptor(jarStream)?.let { entry ->
                    parseDescriptorContent(modFile, jarStream, entry.name)
                }
            }
        } catch (e: Exception) {
            Log.e("ModParser", "Error parsing ${modFile.name}", e)
            null
        }
    }

    private fun locateModDescriptor(jarStream: JarInputStream): java.util.jar.JarEntry? {
        val targetFiles = setOf(
            "fabric.mod.json",
            "quilt.mod.json",
            "META-INF/neoforge.mods.toml",
            "META-INF/mods.toml",
            "mcmod.info"
        )

        return generateSequence { jarStream.nextJarEntry }
            .firstOrNull { it.name in targetFiles }
    }

    private fun parseDescriptorContent(modFile: File, jarStream: JarInputStream, fileName: String): ModInfo? {
        return when (fileName) {
            "fabric.mod.json" -> parseFabricMod(modFile, jarStream)
            "quilt.mod.json" -> parseQuiltMod(modFile, jarStream)
            "META-INF/neoforge.mods.toml", "META-INF/mods.toml" -> parseForgeMod(modFile, jarStream)
            "mcmod.info" -> parseLegacyForgeMod(modFile, jarStream)
            else -> null
        }
    }

    @Throws(Exception::class)
    private fun parseFabricMod(modFile: File, jarStream: JarInputStream): ModInfo {
        val content = jarStream.bufferedReader().use(BufferedReader::readText)
        val jsonObject = JsonParser.parseString(content).asJsonObject
        return ModInfo(
            jsonObject["id"].asString,
            jsonObject["version"].asString,
            jsonObject["name"].asString,
            jsonObject["description"].asString,
            jsonObject.get("authors").asJsonArray.let { authorsArray ->
                val authorsList = mutableListOf<String>()
                authorsArray.forEach { authorElement ->
                    val authorName: String = (
                            if (authorElement.isJsonObject) authorElement.asJsonObject?.get("name")?.asString
                            else authorElement.asString
                            ) ?: return@forEach
                    authorsList.add(authorName)
                }
                authorsList.toTypedArray()
            }
        ).apply { file = modFile }
    }

    @Throws(Exception::class)
    private fun parseQuiltMod(modFile: File, jarStream: JarInputStream): ModInfo {
        val content = jarStream.bufferedReader().use(BufferedReader::readText)
        val quiltLoader = JsonParser.parseString(content).asJsonObject["quilt_loader"].asJsonObject
        val metadata = quiltLoader["metadata"].asJsonObject
        return ModInfo(
            quiltLoader["id"].asString,
            quiltLoader["version"].asString,
            metadata["name"].asString,
            metadata["description"].asString,
            metadata["contributors"].asJsonObject.keySet().toTypedArray()
        ).apply { file = modFile }
    }

    @Throws(Exception::class)
    private fun parseForgeMod(modFile: File, jarStream: JarInputStream): ModInfo? {
        val content = jarStream.bufferedReader().use(BufferedReader::readText)
        val toml = Toml().read(content)
        val modEntry = toml.getTables("mods").firstOrNull() ?: return null

        return ModInfo(
            modEntry.getString("modId") ?: return null,
            modEntry.getString("version") ?: return null,
            modEntry.getString("displayName") ?: return null,
            modEntry.getString("description") ?: "",
            parseForgeAuthors(modEntry)
        ).apply { file = modFile }
    }

    private fun parseForgeAuthors(modEntry: Toml): Array<String> {
        return when {
            modEntry.contains("authors") -> {
                modEntry.getString("authors")
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.toTypedArray() ?: emptyArray()
            }
            else -> emptyArray()
        }
    }

    @Throws(Exception::class)
    private fun parseLegacyForgeMod(modFile: File, jarStream: JarInputStream): ModInfo? {
        val content = jarStream.bufferedReader().use(BufferedReader::readText)
        val jsonArray = JsonParser.parseString(content).asJsonArray
        val mainEntry = jsonArray[0].asJsonObject

        return ModInfo(
            mainEntry["modid"].asString ?: return null,
            mainEntry["version"].asString ?: return null,
            mainEntry["name"].asString ?: return null,
            mainEntry["description"]?.asString ?: "",
            parseLegacyForgeAuthors(mainEntry)
        ).apply { file = modFile }
    }

    private fun parseLegacyForgeAuthors(entry: com.google.gson.JsonObject): Array<String> {
        return when {
            entry.has("authorList") -> entry["authorList"].asJsonArray.toStringArray()
            entry.has("authors") -> entry["authors"].asJsonArray.toStringArray()
            else -> emptyArray()
        }
    }

    private fun JsonArray.toStringArray(): Array<String> {
        return this.map { it.asString }.toTypedArray()
    }

    private fun loadCache(gson: Gson, cacheFile: File): Map<String, ModInfoCache> {
        return cacheFile.takeIf { it.exists() }?.let {
            runCatching {
                gson.fromJson<List<ModInfoCache>>(
                    Tools.read(it),
                    object : TypeToken<List<ModInfoCache>>() {}.type
                )?.associateBy { it.fileHash }
            }.getOrElse { e ->
                Log.e("ModParser", "Cache load failed: ${it.absolutePath}", e)
                emptyMap()
            }
        } ?: emptyMap()
    }

    private fun persistCache(gson: Gson, cacheFile: File, data: List<ModInfoCache>) {
        Task.runTask {
            runCatching {
                cacheFile.parentFile?.mkdirs()
                FileUtils.write(cacheFile, gson.toJson(data), Charsets.UTF_8)
            }.onFailure { e ->
                Log.e("ModParser", "Cache save failed", e)
            }
        }.execute()
    }

    private fun calculateOptimalThreads(fileCount: Int): Int {
        val coreCount = Runtime.getRuntime().availableProcessors()
        return (fileCount.coerceAtMost(coreCount * 8))
            .coerceAtLeast(4)
    }

    private fun calculateChunkSize(totalFiles: Int): Int {
        return when {
            totalFiles < 50 -> 4
            totalFiles < 200 -> 32
            else -> 64
        }
    }

    private suspend fun <T> Semaphore.withPermit(action: suspend () -> T): T {
        acquire()
        try {
            return action()
        } finally {
            release()
        }
    }
}