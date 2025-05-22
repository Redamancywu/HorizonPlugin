// 作者：Redamancy  时间：2025-05-19
// 资源隔离与自动重命名工具类
package com.neil.plugin.resource

import com.neil.plugin.logger.PluginLogger
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * 资源隔离与自动重命名工具
 * 支持：
 * 1. 文件级资源自动重命名（加前缀/后缀/md5）
 * 2. values资源项自动添加前缀/后缀
 * @param prefix 资源前缀
 * @param suffix 资源后缀
 * @param enableMd5 是否启用md5重命名
 * 作者：Redamancy  时间：2025-05-27
 */
object ResourceIsolationHelper {
    // 添加处理标记文件名常量，改为public修饰符
    const val PROCESS_MARKER_FILENAME = ".resource_processed"
    private const val RENAME_MAP_FILENAME = "resource_rename_map.json"
    
    // 白名单匹配工具
    private fun isInWhiteList(name: String, type: String?, whiteList: List<String>): Boolean {
        return whiteList.any { rule ->
            when {
                rule.contains(":") -> {
                    val (t, pattern) = rule.split(":", limit = 2)
                    t == type && Regex(pattern.replace("*", ".*")).matches(name)
                }
                rule.contains("*") -> Regex(rule.replace("*", ".*")).matches(name)
                else -> rule == name
            }
        }
    }

    // 资源名合法性校验
    private fun legalizeResourceName(name: String): String {
        var n = name.lowercase().replace("[^a-z0-9_]".toRegex(), "_")
        if (n.firstOrNull()?.isDigit() == true) n = "r_$n"
        return n
    }

    /**
     * 处理资源目录，进行资源隔离和重命名
     * @param resDir 资源目录
     * @param prefix 资源前缀
     * @param suffix 资源后缀
     * @param enableMd5 是否启用MD5计算
     * @param moduleName 模块名称
     * @param flavorName flavor名称
     * @param resourcePrefixPattern 资源前缀模式
     * @param resourceSuffixPattern 资源后缀模式
     * @param namingStrategy 资源命名策略
     * @param keepOriginalName 是否保留原始资源名
     * @param md5Length MD5长度
     * @param mapOutputPath 资源映射表输出路径
     * @param whiteList 白名单列表
     * @param forceReprocess 是否强制重新处理，即使已经处理过
     * @param includeMd5InFileName 是否将MD5值加入到文件名中
     * @param enableParallelProcessing 是否启用并行处理
     * @param processorThreadCount 处理线程数
     * @param generateReport 是否生成处理报告
     * @param reportFormat 报告格式(json/html)
     * @param processSpecialResources 是否处理特定资源类型
     * @return 处理的文件数量
     */
    fun processResDir(
        resDir: File,
        prefix: String,
        suffix: String = "",
        enableMd5: Boolean = false,
        moduleName: String? = null,
        flavorName: String? = null,
        resourcePrefixPattern: String? = null,
        resourceSuffixPattern: String? = null,
        namingStrategy: ResourceNamingStrategy = ResourceNamingStrategy.PREFIX,
        keepOriginalName: Boolean = true,
        md5Length: Int = 8,
        mapOutputPath: String = "build/reports/resources/resource_rename_map.json",
        whiteList: List<String> = emptyList(),
        forceReprocess: Boolean = false,
        includeMd5InFileName: Boolean = true,
        enableParallelProcessing: Boolean = true,
        processorThreadCount: Int = Runtime.getRuntime().availableProcessors(),
        generateReport: Boolean = false,
        reportFormat: String = "json",
        processSpecialResources: Boolean = false
    ): Int {
        // 初始化日志和性能优化器
        if (generateReport) {
            ResourceLogger.clear()
            ResourceLogger.startProcessing("all")
        }
        
        // 检查是否已经处理过资源
        val markerFile = File(resDir.parentFile, PROCESS_MARKER_FILENAME)
        val outputMapFile = File(resDir.parentFile.parentFile, mapOutputPath)
        
        // 创建资源映射文件目录
        outputMapFile.parentFile.mkdirs()
        
        // 如果已经处理过且不是强制重新处理，则跳过
        if (markerFile.exists() && !forceReprocess) {
            val message = "资源目录 ${resDir.absolutePath} 已经处理过，跳过重命名。使用forceReprocess=true参数可强制重新处理。"
            if (generateReport) {
                ResourceLogger.info(message)
            } else {
                PluginLogger.info(message)
            }
            return 0
        }
        
        // 清除缓存，确保从新状态开始处理
        ResourcePerformanceOptimizer.clearAllCaches()
        
        // 读取现有映射文件
        val existingMap = if (outputMapFile.exists()) {
            try {
                org.json.JSONObject(outputMapFile.readText()).let { json ->
                    val map = mutableMapOf<String, String>()
                    json.keys().forEach { key ->
                        map[key] = json.getString(key)
                    }
                    map
                }
            } catch (e: Exception) {
                PluginLogger.warn("读取资源映射文件失败: ${e.message}，将创建新映射文件")
                mutableMapOf()
            }
        } else {
            mutableMapOf()
        }
        
        val finalPrefix = if (namingStrategy == ResourceNamingStrategy.PREFIX) {
            resourcePrefixPattern
                ?.replace("{module}", moduleName ?: "")
                ?.replace("{flavor}", flavorName ?: "")
                ?: prefix
        } else ""
        
        val finalSuffix = if (namingStrategy == ResourceNamingStrategy.SUFFIX) {
            resourceSuffixPattern
                ?.replace("{module}", moduleName ?: "")
                ?.replace("{flavor}", flavorName ?: "")
                ?: suffix
        } else ""
        
        val renameMap = mutableMapOf<String, String>()
        // 载入已有映射
        renameMap.putAll(existingMap)
        
        // 统计处理的资源数量
        var processedCount = 0
        
        // 处理文件级资源
        if (enableParallelProcessing) {
            processFileResourcesParallel(
                resDir = resDir,
                finalPrefix = finalPrefix,
                finalSuffix = finalSuffix,
                namingStrategy = namingStrategy,
                enableMd5 = enableMd5,
                includeMd5InFileName = includeMd5InFileName,
                keepOriginalName = keepOriginalName,
                md5Length = md5Length,
                whiteList = whiteList,
                renameMap = renameMap,
                threadCount = processorThreadCount,
                processedCount = { count -> processedCount += count }
            )
        } else {
            processFileResources(
                resDir = resDir,
                finalPrefix = finalPrefix,
                finalSuffix = finalSuffix,
                namingStrategy = namingStrategy,
                enableMd5 = enableMd5,
                includeMd5InFileName = includeMd5InFileName,
                keepOriginalName = keepOriginalName,
                md5Length = md5Length,
                whiteList = whiteList,
                renameMap = renameMap,
                processedCount = { count -> processedCount += count }
            )
        }
        
        // 处理values资源
        if (enableParallelProcessing) {
            processValuesResourcesParallel(
                resDir = resDir,
                finalPrefix = finalPrefix,
                finalSuffix = finalSuffix,
                namingStrategy = namingStrategy,
                enableMd5 = enableMd5,
                includeMd5InFileName = includeMd5InFileName,
                keepOriginalName = keepOriginalName,
                md5Length = md5Length,
                whiteList = whiteList,
                renameMap = renameMap,
                threadCount = processorThreadCount,
                processedCount = { count -> processedCount += count }
            )
        } else {
            processValuesResources(
                resDir = resDir,
                finalPrefix = finalPrefix,
                finalSuffix = finalSuffix,
                namingStrategy = namingStrategy,
                enableMd5 = enableMd5,
                includeMd5InFileName = includeMd5InFileName,
                keepOriginalName = keepOriginalName,
                md5Length = md5Length,
                whiteList = whiteList,
                renameMap = renameMap,
                processedCount = { count -> processedCount += count }
            )
        }
        
        // 处理XML引用
        if (enableParallelProcessing) {
            processXmlReferencesParallel(
                resDir = resDir,
                finalPrefix = finalPrefix,
                finalSuffix = finalSuffix,
                namingStrategy = namingStrategy,
                enableMd5 = enableMd5,
                threadCount = processorThreadCount,
                processedCount = { count -> processedCount += count }
            )
        } else {
            processXmlReferences(
                resDir = resDir,
                finalPrefix = finalPrefix,
                finalSuffix = finalSuffix,
                namingStrategy = namingStrategy,
                enableMd5 = enableMd5,
                processedCount = { count -> processedCount += count }
            )
        }
        
        // 处理特定资源类型
        if (processSpecialResources) {
            val specialCount = SpecialResourceProcessor.batchProcessSpecialResources(resDir)
            processedCount += specialCount
        }
        
        // 保存映射文件
        saveRenameMap(renameMap, outputMapFile)
        
        // 创建标记文件，表示已处理过
        markerFile.writeText("Processed at ${java.time.LocalDateTime.now()}")
        
        // 生成处理报告
        if (generateReport) {
            ResourceLogger.endProcessing("all", processedCount)
            val reportPath = "${resDir.parentFile.parentFile.absolutePath}/build/reports/resources/resource_processing_report.${reportFormat}"
            ResourceLogger.generateReport(reportPath, reportFormat)
        }
        
        return processedCount
    }
    
    /**
     * 处理文件级资源
     */
    private fun processFileResources(
        resDir: File,
        finalPrefix: String,
        finalSuffix: String,
        namingStrategy: ResourceNamingStrategy,
        enableMd5: Boolean,
        includeMd5InFileName: Boolean,
        keepOriginalName: Boolean,
        md5Length: Int,
        whiteList: List<String>,
        renameMap: MutableMap<String, String>,
        processedCount: (Int) -> Unit
    ) {
        var count = 0
        
        // 改用map+filterNotNull而不是forEach+continue
        val processed = resDir.walkTopDown()
            .filter { 
                it.isFile && 
                it.parentFile.name != "." && 
                it.parentFile.name != "values" && 
                it.name != "AndroidManifest.xml" && 
                !it.name.endsWith(".bak_resreplace") // 排除备份文件
            }
            .map { resFile ->
                val nameWithoutExt = resFile.nameWithoutExtension
                val type = resFile.parentFile.name
                
                // 检查是否在白名单中
                if (isInWhiteList(nameWithoutExt, type, whiteList)) {
                    PluginLogger.info("资源白名单跳过：$type/$nameWithoutExt")
                    return@map null // 使用null跳过此项
                }
                
                // 计算原始文件的MD5值
                val oldMd5 = md5(resFile.readBytes()).substring(0, md5Length)
                
                // 检查是否已经在重命名映射中
                if (renameMap.containsKey(nameWithoutExt)) {
                    PluginLogger.info("资源已处理过：$type/$nameWithoutExt -> ${renameMap[nameWithoutExt]}")
                    return@map null // 使用null跳过此项
                }
                
                val legalName = legalizeResourceName(nameWithoutExt)
                
                // 根据enableResourceIsolation决定是否需要重命名
                val needRename = when (namingStrategy) {
                    ResourceNamingStrategy.PREFIX -> !resFile.name.startsWith(finalPrefix)
                    ResourceNamingStrategy.SUFFIX -> {
                        val dotIndex = resFile.name.lastIndexOf(".")
                        if (dotIndex > 0) {
                            !resFile.name.substring(0, dotIndex).endsWith(finalSuffix)
                        } else true
                    }
                    ResourceNamingStrategy.MIXED -> 
                        !resFile.name.startsWith(finalPrefix) || 
                        !resFile.name.substring(0, resFile.name.lastIndexOf(".")).endsWith(finalSuffix)
                    ResourceNamingStrategy.HASH -> true // 总是重命名
                    ResourceNamingStrategy.MODULE_MAPPING -> true // 总是重命名
                    ResourceNamingStrategy.DIRECTORY_ISOLATION -> false // 不重命名
                    ResourceNamingStrategy.SEMANTIC -> true // 总是重命名
                    ResourceNamingStrategy.VERSIONED -> true // 总是重命名
                }
                
                // 创建备份，无论是否需要重命名，只要启用了MD5计算，都需要创建备份
                if (needRename || enableMd5) {
                    val backupFile = File(resFile.parent, resFile.name + ".bak_resreplace")
                    if (!backupFile.exists()) resFile.copyTo(backupFile)
                }
                
                // 如果只启用了MD5计算，但不需要将MD5加入文件名，则只修改内容的MD5值
                if (enableMd5 && !includeMd5InFileName) {
                    // 不重命名文件，只计算和记录MD5变更
                    val newMd5 = md5(resFile.readBytes()).substring(0, md5Length)
                    PluginLogger.info("仅修改资源内容MD5值：${resFile.name}，MD5: $oldMd5 -> $newMd5")
                    return@map true // 表示处理了一个文件
                }
                
                // 如果需要重命名或者启用了MD5计算且需要将MD5加入文件名
                if ((namingStrategy != ResourceNamingStrategy.PREFIX || finalPrefix.isNotEmpty() || 
                     namingStrategy != ResourceNamingStrategy.SUFFIX || finalSuffix.isNotEmpty()) && 
                    (needRename || (enableMd5 && includeMd5InFileName))) {
                
                    // 根据配置和策略决定新文件名
                    val newName = when {
                        // 如果只启用MD5计算，但不需要将MD5加入文件名，则保持原文件名
                        enableMd5 && !includeMd5InFileName -> resFile.name
                        
                        // 否则按照命名策略生成新文件名
                        else -> when (namingStrategy) {
                            ResourceNamingStrategy.PREFIX -> {
                                if (enableMd5 && includeMd5InFileName) {
                                    if (keepOriginalName) {
                                        "$finalPrefix${oldMd5}_$legalName.${resFile.extension}"
                                    } else {
                                        "$finalPrefix${oldMd5}.${resFile.extension}"
                                    }
                                } else {
                                    "$finalPrefix$legalName.${resFile.extension}"
                                }
                            }
                            ResourceNamingStrategy.SUFFIX -> {
                                if (enableMd5 && includeMd5InFileName) {
                                    if (keepOriginalName) {
                                        "$legalName${finalSuffix}_$oldMd5.${resFile.extension}"
                                    } else {
                                        "res${finalSuffix}_$oldMd5.${resFile.extension}"
                                    }
                                } else {
                                    "$legalName$finalSuffix.${resFile.extension}"
                                }
                            }
                            ResourceNamingStrategy.MIXED -> {
                                if (enableMd5 && includeMd5InFileName) {
                                    if (keepOriginalName) {
                                        "$finalPrefix${legalName}${finalSuffix}_$oldMd5.${resFile.extension}"
                                    } else {
                                        "$finalPrefix${oldMd5}${finalSuffix}.${resFile.extension}"
                                    }
                                } else {
                                    "$finalPrefix${legalName}${finalSuffix}.${resFile.extension}"
                                }
                            }
                            ResourceNamingStrategy.HASH -> {
                                "h${oldMd5}.${resFile.extension}"
                            }
                            ResourceNamingStrategy.MODULE_MAPPING -> {
                                "m_${legalName}.${resFile.extension}"
                            }
                            ResourceNamingStrategy.DIRECTORY_ISOLATION -> {
                                // 不重命名，保持原名
                                resFile.name
                            }
                            ResourceNamingStrategy.SEMANTIC -> {
                                val fileType = when {
                                    type.startsWith("drawable") -> "img"
                                    type.startsWith("layout") -> "layout"
                                    else -> "res"
                                }
                                "${fileType}_${legalName}.${resFile.extension}"
                            }
                            ResourceNamingStrategy.VERSIONED -> {
                                "${legalName}_v1.${resFile.extension}"
                            }
                        }
                    }
                    
                    // 如果生成的新文件名与原文件名相同，表示不需要重命名
                    if (newName == resFile.name) {
                        // 仅在启用了MD5计算的情况下，记录原文件的MD5值变更
                        if (enableMd5) {
                            val newMd5 = md5(resFile.readBytes()).substring(0, md5Length)
                            PluginLogger.info("仅修改资源内容MD5值：${resFile.name}，MD5: $oldMd5 -> $newMd5")
                            return@map true // 表示处理了一个文件
                        }
                        return@map null // 使用null跳过此项
                    }
                    
                    val newFile = File(resFile.parent + File.separator + newName)
                    if (newFile.exists() && newFile.absolutePath != resFile.absolutePath) {
                        PluginLogger.warn("资源重命名冲突：${newFile.name} 已存在，跳过 ${resFile.name}")
                        return@map null // 使用null跳过此项
                    } else {
                        // 执行重命名
                        val renameSuccess = if (newFile.absolutePath != resFile.absolutePath) {
                            resFile.renameTo(newFile)
                        } else {
                            true // 如果新旧文件路径一样，视为成功
                        }
                        
                        if (renameSuccess) {
                            val targetFile = if (newFile.exists()) newFile else resFile
                            val newMd5 = md5(targetFile.readBytes()).substring(0, md5Length)
                            
                            if (newFile.absolutePath != resFile.absolutePath) {
                                PluginLogger.info("资源自动重命名：${resFile.name} -> ${newFile.name}，MD5: $oldMd5 -> $newMd5")
                            } else {
                                PluginLogger.info("仅修改资源内容MD5值：${resFile.name}，MD5: $oldMd5 -> $newMd5")
                            }
                            
                            // 更新映射
                            renameMap[nameWithoutExt] = targetFile.nameWithoutExtension
                            return@map true // 表示处理了一个文件
                        } else {
                            PluginLogger.warn("资源重命名失败：${resFile.name} -> ${newFile.name}，请检查文件权限或是否已存在同名文件")
                            return@map null // 使用null跳过此项
                        }
                    }
                } else if (enableMd5) {
                    // 如果只启用了MD5计算，但不需要重命名，则只记录MD5值的变更
                    val newMd5 = md5(resFile.readBytes()).substring(0, md5Length)
                    PluginLogger.info("仅修改资源内容MD5值：${resFile.name}，MD5: $oldMd5 -> $newMd5")
                    return@map true // 表示处理了一个文件
                }
                
                // 处理XML内容
                if (resFile.extension.equals("xml", ignoreCase = true)) {
                    val xmlChanged = processXmlResourceContent(resFile, finalPrefix, finalSuffix, namingStrategy, enableMd5)
                    if (xmlChanged) {
                        return@map true // 表示处理了一个文件
                    }
                }
                
                null // 表示未处理此项
            }
            .filterNotNull()
            .count()
        
        count += processed
        processedCount(count)
    }
    
    /**
     * 处理values资源
     */
    private fun processValuesResources(
        resDir: File,
        finalPrefix: String,
        finalSuffix: String,
        namingStrategy: ResourceNamingStrategy,
        enableMd5: Boolean,
        includeMd5InFileName: Boolean,
        keepOriginalName: Boolean,
        md5Length: Int,
        whiteList: List<String>,
        renameMap: MutableMap<String, String>,
        processedCount: (Int) -> Unit
    ) {
        var count = 0
        val valuesDir = File(resDir, "values")
        
        if (!valuesDir.exists() || !valuesDir.isDirectory) {
            return
        }
        
        // 使用安全调用操作符和map方法来替代forEach
        valuesDir.listFiles { file -> file.extension.equals("xml", ignoreCase = true) }?.let { xmlFiles ->
            for (xmlFile in xmlFiles) {
                val oldMd5 = md5(xmlFile.readBytes()).substring(0, md5Length)
                
                // 创建备份
                val backupFile = File(xmlFile.parent, xmlFile.name + ".bak_resreplace")
                if (!backupFile.exists()) xmlFile.copyTo(backupFile)
                
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
                val nodes = doc.getElementsByTagName("*")
                var changed = false
                
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as? org.w3c.dom.Element ?: continue
                    if (!node.hasAttribute("name")) continue
                    
                    val name = node.getAttribute("name")
                    val type = node.tagName
                    
                    // 检查是否在白名单中，使用if条件代替continue
                    if (!isInWhiteList(name, type, whiteList)) {
                        // 检查是否已经在重命名映射中
                        synchronized(renameMap) {
                            if (renameMap.containsKey(name)) {
                                // 如果已经处理过，直接应用映射
                                val newName = renameMap[name]
                                node.setAttribute("name", newName)
                                PluginLogger.info("values资源应用映射：$name -> $newName")
                                changed = true
                            } else {
                                val legalName = legalizeResourceName(name)
                                
                                // 检查是否需要重命名
                                val needRename = when (namingStrategy) {
                                    ResourceNamingStrategy.PREFIX -> !name.startsWith(finalPrefix)
                                    ResourceNamingStrategy.SUFFIX -> !name.endsWith(finalSuffix)
                                    ResourceNamingStrategy.MIXED -> !name.startsWith(finalPrefix) || !name.endsWith(finalSuffix)
                                    ResourceNamingStrategy.HASH -> true // 总是重命名
                                    ResourceNamingStrategy.MODULE_MAPPING -> true // 总是重命名
                                    ResourceNamingStrategy.DIRECTORY_ISOLATION -> false // 不重命名
                                    ResourceNamingStrategy.SEMANTIC -> true // 总是重命名
                                    ResourceNamingStrategy.VERSIONED -> true // 总是重命名
                                }
                                
                                if (needRename) {
                                    // 如果只启用MD5计算，但不将MD5加入文件名，则保持原名
                                    if (enableMd5 && !includeMd5InFileName) {
                                        // 只修改内容的MD5值，不重命名
                                        PluginLogger.info("values资源仅修改内容MD5值：$name")
                                        changed = true
                                    } else {
                                        // 计算资源的md5散列值
                                        val nameMd5 = md5(name.toByteArray()).substring(0, md5Length)
                                        
                                        // 根据资源类型生成前缀
                                        val resourcePrefix = when (type) {
                                            "string" -> "str"
                                            "color" -> "color"
                                            "dimen" -> "dim"
                                            "style" -> "style"
                                            else -> "res"
                                        }
                                        
                                        // 生成新的资源名称
                                        val newName = when (namingStrategy) {
                                            ResourceNamingStrategy.PREFIX -> {
                                                if (enableMd5 && includeMd5InFileName) {
                                                    if (keepOriginalName) {
                                                        "$finalPrefix${nameMd5}_$legalName"
                                                    } else {
                                                        "$finalPrefix$nameMd5"
                                                    }
                                                } else {
                                                    "$finalPrefix$legalName"
                                                }
                                            }
                                            ResourceNamingStrategy.SUFFIX -> {
                                                if (enableMd5 && includeMd5InFileName) {
                                                    if (keepOriginalName) {
                                                        "$legalName${finalSuffix}_$nameMd5"
                                                    } else {
                                                        "res${finalSuffix}_$nameMd5"
                                                    }
                                                } else {
                                                    "$legalName$finalSuffix"
                                                }
                                            }
                                            ResourceNamingStrategy.MIXED -> {
                                                if (enableMd5 && includeMd5InFileName) {
                                                    if (keepOriginalName) {
                                                        "$finalPrefix${legalName}${finalSuffix}_$nameMd5"
                                                    } else {
                                                        "$finalPrefix${nameMd5}${finalSuffix}"
                                                    }
                                                } else {
                                                    "$finalPrefix${legalName}${finalSuffix}"
                                                }
                                            }
                                            ResourceNamingStrategy.HASH -> {
                                                "h$nameMd5"
                                            }
                                            ResourceNamingStrategy.MODULE_MAPPING -> {
                                                "m_$legalName"
                                            }
                                            ResourceNamingStrategy.DIRECTORY_ISOLATION -> {
                                                // 不重命名，保持原名
                                                name
                                            }
                                            ResourceNamingStrategy.SEMANTIC -> {
                                                "${resourcePrefix}_$legalName"
                                            }
                                            ResourceNamingStrategy.VERSIONED -> {
                                                "${legalName}_v1"
                                            }
                                        }
                                        
                                        // 检查是否存在命名冲突
                                        val hasConflict = synchronized(renameMap) {
                                            renameMap.values.contains(newName)
                                        }
                                        
                                        if (hasConflict) {
                                            PluginLogger.warn("values资源命名冲突：$newName 已存在，跳过 $name")
                                        } else {
                                            node.setAttribute("name", newName)
                                            val nameTypeText = when (namingStrategy) {
                                                ResourceNamingStrategy.PREFIX -> "前缀"
                                                ResourceNamingStrategy.SUFFIX -> "后缀"
                                                ResourceNamingStrategy.MIXED -> "混合"
                                                else -> "重命名"
                                            }
                                            PluginLogger.info("values资源自动${nameTypeText}：$name -> $newName")
                                            
                                            // 更新映射
                                            synchronized(renameMap) {
                                                renameMap[name] = newName
                                            }
                                            
                                            // 日志记录
                                            try {
                                                ResourceLogger.logRenameOperation(name, newName, "values-$type", namingStrategy)
                                            } catch (e: Exception) {
                                                // 忽略异常
                                            }
                                            
                                            changed = true
                                        }
                                    }
                                } else if (enableMd5) {
                                    // 如果已有正确前缀/后缀但需要修改MD5值
                                    PluginLogger.info("values资源仅修改内容MD5值：$name")
                                    changed = true
                                }
                            }
                        }
                    } else {
                        PluginLogger.info("资源白名单跳过：$type/$name")
                    }
                }
                
                if (changed) {
                    val transformer = TransformerFactory.newInstance().newTransformer()
                    transformer.transform(DOMSource(doc), StreamResult(xmlFile))
                    val newMd5 = md5(xmlFile.readBytes()).substring(0, md5Length)
                    PluginLogger.info("values资源变更MD5: ${xmlFile.name} $oldMd5 -> $newMd5")
                    count++
                }
            }
        }
        
        processedCount(count)
    }
    
    /**
     * 处理XML引用
     */
    private fun processXmlReferences(
        resDir: File,
        finalPrefix: String,
        finalSuffix: String,
        namingStrategy: ResourceNamingStrategy,
        enableMd5: Boolean,
        processedCount: (Int) -> Unit
    ) {
        var count = 0
        val xmlFiles = resDir.walkTopDown().filter { 
            it.isFile && 
            it.extension.equals("xml", ignoreCase = true) && 
            it.name != "AndroidManifest.xml" && 
            !it.name.endsWith(".bak_resreplace") // 排除备份文件
        }.toList()
        
        for (xmlFile in xmlFiles) {
            val xmlChanged = processXmlResourceContent(xmlFile, finalPrefix, finalSuffix, namingStrategy, enableMd5)
            if (xmlChanged) count++
        }
        
        processedCount(count)
    }
    
    /**
     * 保存重命名映射到文件
     */
    private fun saveRenameMap(renameMap: Map<String, String>, outputFile: File) {
        if (renameMap.isEmpty()) return
        
        try {
            outputFile.parentFile.mkdirs()
            val json = org.json.JSONObject()
            renameMap.forEach { (key, value) -> json.put(key, value) }
            outputFile.writeText(json.toString(2))
            PluginLogger.info("资源映射保存成功: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            PluginLogger.error("保存资源映射文件失败: ${e.message}")
        }
    }
    
    /**
     * 加载资源白名单
     */
    fun loadWhiteListFromConfig(config: Any?, projectDir: File): List<String> {
        val whiteList = mutableListOf<String>()
        
        when (config) {
            is List<*> -> {
                // 直接列表配置
                config.filterIsInstance<String>().forEach { whiteList.add(it) }
                
                // 检查是否有文件路径
                config.filterIsInstance<String>().forEach { path ->
                    if (path.endsWith(".txt") || path.endsWith(".json")) {
                        try {
                            val file = if (File(path).isAbsolute) File(path) else File(projectDir, path)
                            if (file.exists()) {
                                val rules = loadWhiteListFromFile(file)
                                whiteList.addAll(rules)
                                PluginLogger.info("从文件加载资源白名单: ${file.absolutePath}, ${rules.size}条规则")
                            } else {
                                PluginLogger.warn("资源白名单文件不存在: ${file.absolutePath}")
                            }
                        } catch (e: Exception) {
                            PluginLogger.error("加载资源白名单文件失败: $path, ${e.message}")
                        }
                    }
                }
            }
            is String -> {
                // 单文件路径
                val path = config
                try {
                    val file = if (File(path).isAbsolute) File(path) else File(projectDir, path)
                    if (file.exists()) {
                        val rules = loadWhiteListFromFile(file)
                        whiteList.addAll(rules)
                        PluginLogger.info("从文件加载资源白名单: ${file.absolutePath}, ${rules.size}条规则")
                    } else {
                        PluginLogger.warn("资源白名单文件不存在: ${file.absolutePath}")
                    }
                } catch (e: Exception) {
                    PluginLogger.error("加载资源白名单文件失败: $path, ${e.message}")
                }
            }
        }
        
        // 去重和空行
        return whiteList.filter { it.isNotBlank() }.distinct()
    }
    
    /**
     * 从文件加载白名单规则
     */
    private fun loadWhiteListFromFile(file: File): List<String> {
        val rules = mutableListOf<String>()
        
        when {
            file.name.endsWith(".json") -> {
                try {
                    val jsonText = file.readText()
                    val jsonArray = org.json.JSONArray(jsonText)
                    for (i in 0 until jsonArray.length()) {
                        val rule = jsonArray.getString(i)
                        if (rule.isNotBlank() && !rule.startsWith("#")) {
                            rules.add(rule)
                        }
                    }
                } catch (e: Exception) {
                    PluginLogger.error("解析JSON白名单文件失败: ${file.absolutePath}, ${e.message}")
                }
            }
            else -> {
                // 默认按txt处理，每行一条规则
                file.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                        rules.add(trimmed)
                    }
                }
            }
        }
        
        return rules
    }

    private fun md5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // 修改处理xml内容中的资源名功能，支持前缀和后缀两种模式
    private fun processXmlResourceContent(
        xmlFile: File, 
        prefix: String, 
        suffix: String,
        namingStrategy: ResourceNamingStrategy,
        enableMd5: Boolean
    ): Boolean {
        try {
            val oldMd5 = md5(xmlFile.readBytes())
            // 用文本处理替代DOM解析，确保格式正确
            var content = xmlFile.readText()
            var changed = false
            
            // 支持的资源类型
            val resourceTypes = listOf("id", "drawable", "layout", "color", "style", "string", "menu", "anim", "array", "attr", "plurals", "dimen", "fraction", "bool", "integer", "interpolator")
            
            // 修复严重错误格式：@sdkdemo_sdkdemo_id/main 应为 @+id/sdkdemo_main
            val brokenRegex = Regex("@([a-zA-Z0-9_]+)_([a-zA-Z0-9_]+)/([a-zA-Z0-9_]+)")
            content = content.replace(brokenRegex) { matchResult ->
                val ns = matchResult.groupValues[1]     // sdkdemo
                val resType = matchResult.groupValues[2] // id
                val name = matchResult.groupValues[3]   // main
                
                // 检查resType是否为有效的资源类型
                if (resourceTypes.contains(resType)) {
                    // 根据命名策略生成新的资源名
                    val newName = when (namingStrategy) {
                        ResourceNamingStrategy.PREFIX -> "${ns}_$name"
                        ResourceNamingStrategy.SUFFIX -> "$name${suffix.ifEmpty { "_$ns" }}"
                        ResourceNamingStrategy.MIXED -> "${ns}_${name}${suffix.ifEmpty { "_$ns" }}"
                        ResourceNamingStrategy.HASH -> name // 保持原名，哈希策略不适用于XML引用
                        ResourceNamingStrategy.MODULE_MAPPING -> "m_${name}"
                        ResourceNamingStrategy.DIRECTORY_ISOLATION -> name // 保持原名
                        ResourceNamingStrategy.SEMANTIC -> {
                            val typePrefix = when (resType) {
                                "drawable" -> "img"
                                "layout" -> "layout"
                                else -> "res"
                            }
                            "${typePrefix}_$name"
                        }
                        ResourceNamingStrategy.VERSIONED -> "${name}_v1"
                    }
                    // 恢复成正确格式 @+id/前缀_name 或 @+id/name_后缀
                    val newValue = "@+$resType/$newName"
                    changed = true
                    PluginLogger.info("修复严重错误格式：${matchResult.value} -> $newValue")
                    newValue
                } else {
                    // 如果不是有效的资源类型，保持原样
                    matchResult.value
                }
            }
            
            // 根据命名策略匹配每个资源类型
            resourceTypes.forEach { type ->
                // 匹配 @+type/name 或 @type/name，只替换name部分
                val regex = Regex("(@\\+?$type/)([a-zA-Z0-9_]+)")
                content = content.replace(regex) { matchResult ->
                    val prefix1 = matchResult.groupValues[1] // @+id/ or @id/
                    val name = matchResult.groupValues[2]   // 资源名
                    
                    // 根据命名策略检查是否已有前缀/后缀
                    val needUpdate = when (namingStrategy) {
                        ResourceNamingStrategy.PREFIX -> !name.startsWith(prefix)
                        ResourceNamingStrategy.SUFFIX -> !name.endsWith(suffix)
                        ResourceNamingStrategy.MIXED -> !name.startsWith(prefix) || !name.endsWith(suffix)
                        ResourceNamingStrategy.HASH -> false // 哈希策略不适用于XML引用
                        ResourceNamingStrategy.MODULE_MAPPING -> !name.startsWith("m_")
                        ResourceNamingStrategy.DIRECTORY_ISOLATION -> false // 目录隔离不需要重命名
                        ResourceNamingStrategy.SEMANTIC -> {
                            // 检查是否已有语义化前缀
                            val semanticPrefixes = listOf("img_", "btn_", "ic_", "bg_", "layout_", "res_", "str_", "color_", "dim_")
                            !semanticPrefixes.any { name.startsWith(it) }
                        }
                        ResourceNamingStrategy.VERSIONED -> !name.contains("_v")
                    }
                    
                    if (needUpdate) {
                        val newName = when (namingStrategy) {
                            ResourceNamingStrategy.PREFIX -> "$prefix$name"
                            ResourceNamingStrategy.SUFFIX -> "$name$suffix"
                            ResourceNamingStrategy.MIXED -> "$prefix${name}$suffix"
                            ResourceNamingStrategy.HASH -> name // 保持原名
                            ResourceNamingStrategy.MODULE_MAPPING -> "m_$name"
                            ResourceNamingStrategy.DIRECTORY_ISOLATION -> name // 保持原名
                            ResourceNamingStrategy.SEMANTIC -> {
                                val typePrefix = when (type) {
                                    "drawable" -> "img"
                                    "layout" -> "layout"
                                    "string" -> "str"
                                    "color" -> "color"
                                    "dimen" -> "dim"
                                    else -> "res"
                                }
                                "${typePrefix}_$name"
                            }
                            ResourceNamingStrategy.VERSIONED -> "${name}_v1"
                        }
                        val strategyName = when (namingStrategy) {
                            ResourceNamingStrategy.PREFIX -> "前缀"
                            ResourceNamingStrategy.SUFFIX -> "后缀"
                            ResourceNamingStrategy.MIXED -> "混合"
                            ResourceNamingStrategy.HASH -> "哈希"
                            ResourceNamingStrategy.MODULE_MAPPING -> "模块映射"
                            ResourceNamingStrategy.DIRECTORY_ISOLATION -> "目录隔离"
                            ResourceNamingStrategy.SEMANTIC -> "语义命名"
                            ResourceNamingStrategy.VERSIONED -> "版本化"
                        }
                        changed = true
                        PluginLogger.info("xml资源引用正确格式添加$strategyName：${matchResult.value} -> ${prefix1}${newName}")
                        "${prefix1}${newName}"
                    } else {
                        matchResult.value
                    }
                }
            }
            
            if (changed) {
                xmlFile.writeText(content)
                val newMd5 = md5(xmlFile.readBytes())
                PluginLogger.info("xml资源内容变更MD5: ${xmlFile.name} $oldMd5 -> $newMd5")
            }
            
            return changed
        } catch (e: Exception) {
            PluginLogger.warn("处理xml资源内容时出错：${xmlFile.name}，原因：${e.message}")
            PluginLogger.error("处理xml资源内容时出错：${xmlFile.name}，原因：${e.message}")
            return false
        }
    }

    /**
     * 并行处理文件级资源
     */
    private fun processFileResourcesParallel(
        resDir: File,
        finalPrefix: String,
        finalSuffix: String,
        namingStrategy: ResourceNamingStrategy,
        enableMd5: Boolean,
        includeMd5InFileName: Boolean,
        keepOriginalName: Boolean,
        md5Length: Int,
        whiteList: List<String>,
        renameMap: MutableMap<String, String>,
        threadCount: Int,
        processedCount: (Int) -> Unit
    ) {
        val logger = if (ResourceLogger != null) PluginLogger else PluginLogger
        logger.info("开始并行处理文件级资源，线程数：$threadCount")
        
        // 收集需要处理的文件
        val filesToProcess = resDir.walkTopDown().filter { file -> 
            file.isFile && 
            file.parentFile.name != "." && 
            file.parentFile.name != "values" && 
            file.name != "AndroidManifest.xml" && 
            !file.name.endsWith(".bak_resreplace") // 排除备份文件
        }.toList()
        
        // 过滤出需要增量处理的文件
        val filesToProcessFiltered = ResourcePerformanceOptimizer.filterFilesForIncrementalProcessing(filesToProcess)
        
        logger.info("找到 ${filesToProcessFiltered.size} 个需要处理的文件级资源（共 ${filesToProcess.size} 个文件）")
        
        // 使用性能优化器并行处理
        val count = ResourcePerformanceOptimizer.processFilesInParallel(
            files = filesToProcessFiltered,
            processor = { file ->
                processFileResource(
                    file = file,
                    finalPrefix = finalPrefix,
                    finalSuffix = finalSuffix,
                    namingStrategy = namingStrategy,
                    enableMd5 = enableMd5,
                    includeMd5InFileName = includeMd5InFileName,
                    keepOriginalName = keepOriginalName,
                    md5Length = md5Length,
                    whiteList = whiteList,
                    renameMap = renameMap
                )
            },
            threadCount = threadCount
        )
        
        processedCount(count)
    }
    
    /**
     * 处理单个文件资源（供并行处理使用）
     */
    private fun processFileResource(
        file: File,
        finalPrefix: String,
        finalSuffix: String,
        namingStrategy: ResourceNamingStrategy,
        enableMd5: Boolean,
        includeMd5InFileName: Boolean,
        keepOriginalName: Boolean,
        md5Length: Int,
        whiteList: List<String>,
        renameMap: MutableMap<String, String>
    ): Boolean {
        val nameWithoutExt = file.nameWithoutExtension
        val type = file.parentFile.name
        val logger = if (ResourceLogger != null) PluginLogger else PluginLogger
        
        // 检查是否在白名单中
        if (isInWhiteList(nameWithoutExt, type, whiteList)) {
            logger.info("资源白名单跳过：$type/$nameWithoutExt")
            return false
        }
        
        // 计算原始文件的MD5值，使用缓存机制
        val oldMd5 = ResourcePerformanceOptimizer.calculateMd5WithCache(file, md5Length)
        
        // 检查是否已经在重命名映射中
        synchronized(renameMap) {
            if (renameMap.containsKey(nameWithoutExt)) {
                logger.info("资源已处理过：$type/$nameWithoutExt -> ${renameMap[nameWithoutExt]}")
                return false
            }
        }
        
        val legalName = legalizeResourceName(nameWithoutExt)
        
        // 根据命名策略决定是否需要重命名
        val needRename = needRename(file.name, finalPrefix, finalSuffix, namingStrategy)
        
        // 创建备份，无论是否需要重命名，只要启用了MD5计算，都需要创建备份
        if (needRename || enableMd5) {
            val backupFile = File(file.parent, file.name + ".bak_resreplace")
            if (!backupFile.exists()) file.copyTo(backupFile)
        }
        
        // 如果只启用了MD5计算，但不需要将MD5加入文件名，则只修改内容的MD5值
        if (enableMd5 && !includeMd5InFileName) {
            // 不重命名文件，只计算和记录MD5变更
            val newMd5 = ResourcePerformanceOptimizer.calculateMd5WithCache(file, md5Length)
            logger.info("仅修改资源内容MD5值：${file.name}，MD5: $oldMd5 -> $newMd5")
            return true
        }
        
        // 如果需要重命名或者启用了MD5计算且需要将MD5加入文件名
        if ((namingStrategy != ResourceNamingStrategy.PREFIX || finalPrefix.isNotEmpty() || 
             namingStrategy != ResourceNamingStrategy.SUFFIX || finalSuffix.isNotEmpty()) && 
            (needRename || (enableMd5 && includeMd5InFileName))) {
            
            // 生成新的文件名
            val newName = generateNewName(
                namingStrategy = namingStrategy,
                legalName = legalName,
                finalPrefix = finalPrefix,
                finalSuffix = finalSuffix,
                enableMd5 = enableMd5,
                includeMd5InFileName = includeMd5InFileName,
                keepOriginalName = keepOriginalName,
                md5Hash = oldMd5,
                extension = file.extension,
                fileType = type
            )
            
            // 如果生成的新文件名与原文件名相同，表示不需要重命名
            if (newName == file.name) {
                // 仅在启用了MD5计算的情况下，记录原文件的MD5值变更
                if (enableMd5) {
                    val newMd5 = ResourcePerformanceOptimizer.calculateMd5WithCache(file, md5Length)
                    logger.info("仅修改资源内容MD5值：${file.name}，MD5: $oldMd5 -> $newMd5")
                    return true
                }
                return false
            }
            
            val newFile = File(file.parent + File.separator + newName)
            if (newFile.exists() && newFile.absolutePath != file.absolutePath) {
                logger.warn("资源重命名冲突：${newFile.name} 已存在，跳过 ${file.name}")
                return false
            } else {
                // 执行重命名
                val renameSuccess = if (newFile.absolutePath != file.absolutePath) {
                    file.renameTo(newFile)
                } else {
                    true // 如果新旧文件路径一样，视为成功
                }
                
                if (renameSuccess) {
                    val targetFile = if (newFile.exists()) newFile else file
                    val newMd5 = ResourcePerformanceOptimizer.calculateMd5WithCache(targetFile, md5Length)
                    
                    if (newFile.absolutePath != file.absolutePath) {
                        logger.info("资源自动重命名：${file.name} -> ${newFile.name}，MD5: $oldMd5 -> $newMd5")
                        // 记录到ResourceLogger（如果可用）
                        try {
                            ResourceLogger.logRenameOperation(file.name, newFile.name, type, namingStrategy)
                        } catch (e: Exception) {
                            // 忽略异常
                        }
                    } else {
                        logger.info("仅修改资源内容MD5值：${file.name}，MD5: $oldMd5 -> $newMd5")
                    }
                    
                    // 更新映射
                    synchronized(renameMap) {
                        renameMap[nameWithoutExt] = targetFile.nameWithoutExtension
                    }
                    return true
                } else {
                    logger.warn("资源重命名失败：${file.name} -> ${newFile.name}，请检查文件权限或是否已存在同名文件")
                    return false
                }
            }
        } else if (enableMd5) {
            // 如果只启用了MD5计算，但不需要重命名，则只记录MD5值的变更
            val newMd5 = ResourcePerformanceOptimizer.calculateMd5WithCache(file, md5Length)
            logger.info("仅修改资源内容MD5值：${file.name}，MD5: $oldMd5 -> $newMd5")
            return true
        }
        
        return false
    }
    
    /**
     * 并行处理values资源
     */
    private fun processValuesResourcesParallel(
        resDir: File,
        finalPrefix: String,
        finalSuffix: String,
        namingStrategy: ResourceNamingStrategy,
        enableMd5: Boolean,
        includeMd5InFileName: Boolean,
        keepOriginalName: Boolean,
        md5Length: Int,
        whiteList: List<String>,
        renameMap: MutableMap<String, String>,
        threadCount: Int,
        processedCount: (Int) -> Unit
    ) {
        val logger = if (ResourceLogger != null) PluginLogger else PluginLogger
        val valuesDir = File(resDir, "values")
        
        if (!valuesDir.exists() || !valuesDir.isDirectory) {
            return
        }
        
        logger.info("开始并行处理values资源，线程数：$threadCount")
        
        // 收集需要处理的XML文件
        val xmlFiles = valuesDir.listFiles { file -> 
            file.extension.equals("xml", ignoreCase = true) && !file.name.endsWith(".bak_resreplace")
        }?.toList() ?: listOf()
        
        // 过滤出需要增量处理的文件
        val xmlFilesToProcess = ResourcePerformanceOptimizer.filterFilesForIncrementalProcessing(xmlFiles)
        
        logger.info("找到 ${xmlFilesToProcess.size} 个需要处理的values资源文件（共 ${xmlFiles.size} 个XML文件）")
        
        // 并行处理
        val count = ResourcePerformanceOptimizer.processFilesInParallel(
            files = xmlFilesToProcess,
            processor = { file ->
                processValuesResourceFile(
                    file = file,
                    finalPrefix = finalPrefix,
                    finalSuffix = finalSuffix,
                    namingStrategy = namingStrategy,
                    enableMd5 = enableMd5,
                    includeMd5InFileName = includeMd5InFileName,
                    keepOriginalName = keepOriginalName,
                    md5Length = md5Length,
                    whiteList = whiteList,
                    renameMap = renameMap,
                    valuesDir = valuesDir
                )
            },
            threadCount = threadCount
        )
        
        processedCount(count)
    }
    
    /**
     * 处理单个values资源文件（供并行处理使用）
     */
    private fun processValuesResourceFile(
        file: File,
        finalPrefix: String,
        finalSuffix: String,
        namingStrategy: ResourceNamingStrategy,
        enableMd5: Boolean,
        includeMd5InFileName: Boolean,
        keepOriginalName: Boolean,
        md5Length: Int,
        whiteList: List<String>,
        renameMap: MutableMap<String, String>,
        valuesDir: File
    ): Boolean {
        val logger = if (ResourceLogger != null) PluginLogger else PluginLogger
        
        try {
            val oldMd5 = ResourcePerformanceOptimizer.calculateMd5WithCache(file, md5Length)
            
            // 创建备份
            val backupFile = File(file.parent, file.name + ".bak_resreplace")
            if (!backupFile.exists()) file.copyTo(backupFile)
            
            // 使用缓存机制获取文件内容
            val content = ResourcePerformanceOptimizer.getFileContentWithCache(file)
            
            // 解析XML
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("*")
            var changed = false
            
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as? org.w3c.dom.Element ?: continue
                if (!node.hasAttribute("name")) continue
                
                val name = node.getAttribute("name")
                val type = node.tagName
                
                // 检查是否在白名单中
                if (isInWhiteList(name, type, whiteList)) {
                    logger.info("资源白名单跳过：$type/$name")
                    continue
                }
                
                // 检查是否已经在重命名映射中
                synchronized(renameMap) {
                    if (renameMap.containsKey(name)) {
                        // 如果已经处理过，直接应用映射
                        val newName = renameMap[name]
                        node.setAttribute("name", newName)
                        logger.info("values资源应用映射：$name -> $newName")
                        changed = true
                    } else {
                        val legalName = legalizeResourceName(name)
                        
                        // 检查是否需要重命名
                        val needRename = when (namingStrategy) {
                            ResourceNamingStrategy.PREFIX -> !name.startsWith(finalPrefix)
                            ResourceNamingStrategy.SUFFIX -> !name.endsWith(finalSuffix)
                            ResourceNamingStrategy.MIXED -> !name.startsWith(finalPrefix) || !name.endsWith(finalSuffix)
                            ResourceNamingStrategy.HASH -> true // 总是重命名
                            ResourceNamingStrategy.MODULE_MAPPING -> true // 总是重命名
                            ResourceNamingStrategy.DIRECTORY_ISOLATION -> false // 不重命名
                            ResourceNamingStrategy.SEMANTIC -> true // 总是重命名
                            ResourceNamingStrategy.VERSIONED -> true // 总是重命名
                        }
                        
                        if (needRename) {
                            // 如果只启用MD5计算，但不将MD5加入文件名，则保持原名
                            if (enableMd5 && !includeMd5InFileName) {
                                // 只修改内容的MD5值，不重命名
                                logger.info("values资源仅修改内容MD5值：$name")
                                changed = true
                            } else {
                                // 计算资源的md5散列值
                                val nameMd5 = md5(name.toByteArray()).substring(0, md5Length)
                                
                                // 根据资源类型生成前缀
                                val resourcePrefix = when (type) {
                                    "string" -> "str"
                                    "color" -> "color"
                                    "dimen" -> "dim"
                                    "style" -> "style"
                                    else -> "res"
                                }
                                
                                // 生成新的资源名称
                                val newName = when (namingStrategy) {
                                    ResourceNamingStrategy.PREFIX -> {
                                        if (enableMd5 && includeMd5InFileName) {
                                            if (keepOriginalName) {
                                                "$finalPrefix${nameMd5}_$legalName"
                                            } else {
                                                "$finalPrefix$nameMd5"
                                            }
                                        } else {
                                            "$finalPrefix$legalName"
                                        }
                                    }
                                    ResourceNamingStrategy.SUFFIX -> {
                                        if (enableMd5 && includeMd5InFileName) {
                                            if (keepOriginalName) {
                                                "$legalName${finalSuffix}_$nameMd5"
                                            } else {
                                                "res${finalSuffix}_$nameMd5"
                                            }
                                        } else {
                                            "$legalName$finalSuffix"
                                        }
                                    }
                                    ResourceNamingStrategy.MIXED -> {
                                        if (enableMd5 && includeMd5InFileName) {
                                            if (keepOriginalName) {
                                                "$finalPrefix${legalName}${finalSuffix}_$nameMd5"
                                            } else {
                                                "$finalPrefix${nameMd5}${finalSuffix}"
                                            }
                                        } else {
                                            "$finalPrefix${legalName}${finalSuffix}"
                                        }
                                    }
                                    ResourceNamingStrategy.HASH -> {
                                        "h$nameMd5"
                                    }
                                    ResourceNamingStrategy.MODULE_MAPPING -> {
                                        "m_$legalName"
                                    }
                                    ResourceNamingStrategy.DIRECTORY_ISOLATION -> {
                                        // 不重命名，保持原名
                                        name
                                    }
                                    ResourceNamingStrategy.SEMANTIC -> {
                                        "${resourcePrefix}_$legalName"
                                    }
                                    ResourceNamingStrategy.VERSIONED -> {
                                        "${legalName}_v1"
                                    }
                                }
                                
                                // 检查是否存在命名冲突
                                val hasConflict = synchronized(renameMap) {
                                    renameMap.values.contains(newName)
                                }
                                
                                if (hasConflict) {
                                    logger.warn("values资源命名冲突：$newName 已存在，跳过 $name")
                                } else {
                                    node.setAttribute("name", newName)
                                    val nameTypeText = when (namingStrategy) {
                                        ResourceNamingStrategy.PREFIX -> "前缀"
                                        ResourceNamingStrategy.SUFFIX -> "后缀"
                                        ResourceNamingStrategy.MIXED -> "混合"
                                        else -> "重命名"
                                    }
                                    logger.info("values资源自动${nameTypeText}：$name -> $newName")
                                    
                                    // 更新映射
                                    synchronized(renameMap) {
                                        renameMap[name] = newName
                                    }
                                    
                                    // 日志记录
                                    try {
                                        ResourceLogger.logRenameOperation(name, newName, "values-$type", namingStrategy)
                                    } catch (e: Exception) {
                                        // 忽略异常
                                    }
                                    
                                    changed = true
                                }
                            }
                        } else if (enableMd5) {
                            // 如果已有正确前缀/后缀但需要修改MD5值
                            logger.info("values资源仅修改内容MD5值：$name")
                            changed = true
                        }
                    }
                }
            }
            
            if (changed) {
                val transformer = TransformerFactory.newInstance().newTransformer()
                transformer.transform(DOMSource(doc), StreamResult(file))
                val newMd5 = ResourcePerformanceOptimizer.calculateMd5WithCache(file, md5Length, true)
                logger.info("values资源变更MD5: ${file.name} $oldMd5 -> $newMd5")
                return true
            }
            
            return false
        } catch (e: Exception) {
            logger.error("处理values资源文件失败: ${file.name}, ${e.message}")
            return false
        }
    }
    
    /**
     * 并行处理XML引用
     */
    private fun processXmlReferencesParallel(
        resDir: File,
        finalPrefix: String,
        finalSuffix: String,
        namingStrategy: ResourceNamingStrategy,
        enableMd5: Boolean,
        threadCount: Int,
        processedCount: (Int) -> Unit
    ) {
        val logger = if (ResourceLogger != null) PluginLogger else PluginLogger
        logger.info("开始并行处理XML引用，线程数：$threadCount")
        
        // 收集需要处理的XML文件
        val xmlFiles = resDir.walkTopDown().filter { 
            it.isFile && 
            it.extension.equals("xml", ignoreCase = true) && 
            it.name != "AndroidManifest.xml" && 
            !it.name.endsWith(".bak_resreplace") // 排除备份文件
        }.toList()
        
        // 过滤出需要增量处理的文件
        val xmlFilesToProcess = ResourcePerformanceOptimizer.filterFilesForIncrementalProcessing(xmlFiles)
        
        logger.info("找到 ${xmlFilesToProcess.size} 个需要处理的XML引用文件（共 ${xmlFiles.size} 个XML文件）")
        
        // 避免使用有问题的batchProcessXmlReferences，直接使用ExecutorService
        if (threadCount <= 1) {
            // 单线程处理
            var count = 0
            for (file in xmlFilesToProcess) {
                val changed = processXmlResourceContent(file, finalPrefix, finalSuffix, namingStrategy, enableMd5)
                if (changed) count++
            }
            processedCount(count)
        } else {
            // 多线程处理
            val executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount)
            val counter = java.util.concurrent.atomic.AtomicInteger(0)
            
            for (file in xmlFilesToProcess) {
                executor.submit {
                    try {
                        val changed = processXmlResourceContent(file, finalPrefix, finalSuffix, namingStrategy, enableMd5)
                        if (changed) counter.incrementAndGet()
                    } catch (e: Exception) {
                        logger.error("处理XML引用时出错: ${file.name}, ${e.message}")
                    }
                }
            }
            
            executor.shutdown()
            executor.awaitTermination(30, java.util.concurrent.TimeUnit.MINUTES)
            
            processedCount(counter.get())
        }
    }

    /**
     * 根据enableResourceIsolation决定是否需要重命名
     */
    private fun needRename(fileName: String, finalPrefix: String, finalSuffix: String, namingStrategy: ResourceNamingStrategy): Boolean {
        return when (namingStrategy) {
            ResourceNamingStrategy.PREFIX -> !fileName.startsWith(finalPrefix)
            ResourceNamingStrategy.SUFFIX -> {
                val dotIndex = fileName.lastIndexOf(".")
                if (dotIndex > 0) {
                    !fileName.substring(0, dotIndex).endsWith(finalSuffix)
                } else true
            }
            ResourceNamingStrategy.MIXED -> 
                !fileName.startsWith(finalPrefix) || 
                !fileName.substring(0, fileName.lastIndexOf(".")).endsWith(finalSuffix)
            ResourceNamingStrategy.HASH -> true // 总是重命名
            ResourceNamingStrategy.MODULE_MAPPING -> true // 总是重命名
            ResourceNamingStrategy.DIRECTORY_ISOLATION -> false // 不重命名
            ResourceNamingStrategy.SEMANTIC -> true // 总是重命名
            ResourceNamingStrategy.VERSIONED -> true // 总是重命名
        }
    }

    /**
     * 生成资源新名称
     */
    private fun generateNewName(
        namingStrategy: ResourceNamingStrategy,
        legalName: String,
        finalPrefix: String,
        finalSuffix: String,
        enableMd5: Boolean,
        includeMd5InFileName: Boolean,
        keepOriginalName: Boolean,
        md5Hash: String,
        extension: String,
        fileType: String = ""
    ): String {
        return when (namingStrategy) {
            ResourceNamingStrategy.PREFIX -> {
                if (enableMd5 && includeMd5InFileName) {
                    if (keepOriginalName) {
                        "$finalPrefix${md5Hash}_$legalName.$extension"
                    } else {
                        "$finalPrefix${md5Hash}.$extension"
                    }
                } else {
                    "$finalPrefix$legalName.$extension"
                }
            }
            ResourceNamingStrategy.SUFFIX -> {
                if (enableMd5 && includeMd5InFileName) {
                    if (keepOriginalName) {
                        "$legalName${finalSuffix}_$md5Hash.$extension"
                    } else {
                        "res${finalSuffix}_$md5Hash.$extension"
                    }
                } else {
                    "$legalName$finalSuffix.$extension"
                }
            }
            ResourceNamingStrategy.MIXED -> {
                if (enableMd5 && includeMd5InFileName) {
                    if (keepOriginalName) {
                        "$finalPrefix${legalName}${finalSuffix}_$md5Hash.$extension"
                    } else {
                        "$finalPrefix${md5Hash}${finalSuffix}.$extension"
                    }
                } else {
                    "$finalPrefix${legalName}${finalSuffix}.$extension"
                }
            }
            ResourceNamingStrategy.HASH -> {
                "h${md5Hash}.$extension"
            }
            ResourceNamingStrategy.MODULE_MAPPING -> {
                "m_${legalName}.$extension"
            }
            ResourceNamingStrategy.DIRECTORY_ISOLATION -> {
                // 不重命名，保持原名
                "$legalName.$extension"
            }
            ResourceNamingStrategy.SEMANTIC -> {
                val typePrefix = when {
                    fileType.startsWith("drawable") -> "img"
                    fileType.startsWith("layout") -> "layout"
                    else -> "res"
                }
                "${typePrefix}_${legalName}.$extension"
            }
            ResourceNamingStrategy.VERSIONED -> {
                "${legalName}_v1.$extension"
            }
        }
    }
} 