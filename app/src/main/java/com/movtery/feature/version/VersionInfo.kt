package com.movtery.feature.version

class VersionInfo(
    val minecraftVersion: String,
    val loaderInfo: Array<LoaderInfo>?
) {
    /**
     * 拼接Minecraft的版本信息，包括ModLoader信息
     * @return 用", "分割的信息字符串
     */
    fun getInfoString(): String {
        val infoList = mutableListOf<String>().apply {
            add(minecraftVersion)
            loaderInfo?.forEach { info ->
                when {
                    info.name.isNotBlank() && info.version.isNotBlank() -> add("${info.name} - ${info.version}")
                    info.name.isNotBlank() -> add(info.name)
                    info.version.isNotBlank() -> add(info.version)
                }
            }
        }
        return infoList.joinToString(", ")
    }

    data class LoaderInfo(
        val name: String,
        val version: String
    ) {
        /**
         * 通过加载器名称，获得对应的环境变量键名
         */
        fun getLoaderEnvKey(): String? {
            return when(name) {
                "OptiFine" -> "INST_OPTIFINE"
                "Forge" -> "INST_FORGE"
                "NeoForge" -> "INST_NEOFORGE"
                "Fabric" -> "INST_FABRIC"
                "Quilt" -> "INST_QUILT"
                "LiteLoader" -> "INST_LITELOADER"
                else -> null
            }
        }
    }
}