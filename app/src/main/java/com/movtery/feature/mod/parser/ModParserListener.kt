package com.movtery.feature.mod.parser

/**
 * 模组解析进度监听器，用于回调当前已经处理的模组和模组总数
 */
interface ModParserListener {
    /**
     * 解析进度回调，通过这个函数回调当前模组的解析进度
     * @param recentlyParsedModInfo 刚刚解析完成的模组信息
     * @param totalFileCount 所有需要检查的文件的数量
     */
    fun onProgress(recentlyParsedModInfo: ModInfo, totalFileCount: Int)

    /**
     * 解析完成后通过这个函数将解析的结果进行回调
     * @param modInfoList 所有模组信息列表
     */
    fun onParseEnded(modInfoList: List<ModInfo>)
}