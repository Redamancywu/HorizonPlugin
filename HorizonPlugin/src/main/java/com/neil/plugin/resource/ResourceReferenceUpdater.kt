// 作者：Redamancy  时间：2025-05-20
package com.neil.plugin.resource

import com.neil.plugin.logger.PluginLogger
import org.gradle.api.Project
import java.io.File
import java.util.regex.Pattern
import java.io.IOException

/**
 * 资源引用自动更新工具
 * 负责分析和更新Java/Kotlin源代码中的资源引用
 * 作者：Redamancy  时间：2025-05-20
 */
class ResourceReferenceUpdater {
    companion object {
        // 资源引用模式：R.xxx.yyy 匹配
        private val RESOURCE_PATTERN = Pattern.compile(
            "R\\.(id|layout|drawable|string|color|dimen|raw|menu|anim|animator|xml|plurals|array|font|navigation|style|styleable|bool|integer|interpolator|transition|mipmap)\\.(\\w+)"
        )
        
        // 特殊方法调用中的资源引用模式
        private val METHOD_RESOURCE_PATTERNS = listOf(
            // findViewById(R.id.xxx)
            Pattern.compile("findViewById\\(\\s*R\\.(id)\\.(\\w+)\\s*\\)"),
            // setContentView(R.layout.xxx)
            Pattern.compile("setContentView\\(\\s*R\\.(layout)\\.(\\w+)\\s*\\)"),
            // inflate(R.layout.xxx, ...)
            Pattern.compile("inflate\\(\\s*R\\.(layout)\\.(\\w+)\\s*,"),
            // getIdentifier("xxx", "layout", ...)
            Pattern.compile("getIdentifier\\(\\s*[\"'](\\w+)[\"']\\s*,\\s*[\"'](id|layout|drawable|string|color|dimen|raw|menu|anim|animator|xml|plurals|array)[\"']")
        )
        
        /**
         * 更新指定模块中的资源引用
         * @param project Gradle项目
         * @param moduleName 模块名称
         * @param resourcePrefix 资源前缀
         * @param dryRun 是否为试运行模式（不实际修改文件）
         * @param whiteList 不需要更新的资源引用白名单
         * @param forceUpdatePrefixed 是否强制更新已有前缀的资源
         * @return 修改的文件数量
         */
        fun updateResourceReferences(
            project: Project,
            moduleName: String,
            resourcePrefix: String,
            dryRun: Boolean = false,
            whiteList: Set<String> = emptySet(),
            forceUpdatePrefixed: Boolean = false
        ): Int {
            if (resourcePrefix.isEmpty()) {
                PluginLogger.warn("模块 $moduleName 资源前缀为空，跳过资源引用更新")
                return 0
            }
            
            // 记录开始时间
            val startTime = System.currentTimeMillis()
            
            val srcDirs = listOf(
                project.file("src/main/java"),
                project.file("src/main/kotlin")
            ).filter { it.exists() && it.isDirectory }
            
            if (srcDirs.isEmpty()) {
                PluginLogger.warn("模块 $moduleName 未找到源代码目录，跳过资源引用更新")
                return 0
            }
            
            var updatedFiles = 0
            var totalResourceReferences = 0
            var updatedResourceReferences = 0
            
            srcDirs.forEach { srcDir ->
                val result = processSourceDirectory(
                    srcDir, 
                    resourcePrefix, 
                    dryRun,
                    whiteList,
                    forceUpdatePrefixed
                )
                updatedFiles += result.first
                totalResourceReferences += result.second
                updatedResourceReferences += result.third
            }
            
            val elapsedTime = System.currentTimeMillis() - startTime
            
            if (updatedFiles > 0) {
                val actionWord = if (dryRun) "需要更新" else "已更新"
                PluginLogger.info("模块 $moduleName 资源引用$actionWord：共扫描 $totalResourceReferences 个引用，$actionWord $updatedResourceReferences 个，涉及 $updatedFiles 个文件（耗时 ${elapsedTime}ms）")
            } else if (totalResourceReferences > 0) {
                PluginLogger.info("模块 $moduleName 共扫描 $totalResourceReferences 个资源引用，全部符合规范，无需更新（耗时 ${elapsedTime}ms）")
            } else {
                PluginLogger.info("模块 $moduleName 未发现资源引用（耗时 ${elapsedTime}ms）")
            }
            
            return updatedFiles
        }
        
        /**
         * 处理源代码目录
         * @return Triple(更新的文件数, 总引用数, 更新的引用数)
         */
        private fun processSourceDirectory(
            sourceDir: File,
            resourcePrefix: String,
            dryRun: Boolean,
            whiteList: Set<String>,
            forceUpdatePrefixed: Boolean
        ): Triple<Int, Int, Int> {
            var updatedFiles = 0
            var totalReferences = 0
            var updatedReferences = 0
            
            sourceDir.walkTopDown()
                .filter { it.isFile && (it.extension == "java" || it.extension == "kt") }
                .forEach { file ->
                    try {
                        val content = file.readText()
                        
                        // 先用主要正则表达式处理标准的R.xxx.yyy形式
                        val result = updateResourceReferencesInFile(
                            content, 
                            resourcePrefix,
                            whiteList,
                            forceUpdatePrefixed
                        )
                        
                        totalReferences += result.second
                        
                        // 然后处理特殊的方法调用形式
                        val methodResult = updateMethodResourceReferencesInFile(
                            result.first,
                            resourcePrefix,
                            whiteList,
                            forceUpdatePrefixed
                        )
                        
                        totalReferences += methodResult.second
                        val totalUpdatedReferences = result.third + methodResult.third
                        
                        if (totalUpdatedReferences > 0) {
                            if (!dryRun) {
                                file.writeText(methodResult.first)
                                PluginLogger.debug("更新文件 ${file.absolutePath} 中的资源引用：${totalUpdatedReferences}个")
                            } else {
                                PluginLogger.debug("试运行：需要更新文件 ${file.absolutePath} 中的资源引用：${totalUpdatedReferences}个")
                            }
                            updatedFiles++
                            updatedReferences += totalUpdatedReferences
                        }
                    } catch (e: IOException) {
                        PluginLogger.error("处理文件 ${file.absolutePath} 时出错: ${e.message}")
                    }
                }
            
            return Triple(updatedFiles, totalReferences, updatedReferences)
        }
        
        /**
         * 更新文件内容中的资源引用
         * @return Triple(更新后的内容, 总引用数, 更新的引用数)
         */
        private fun updateResourceReferencesInFile(
            content: String, 
            resourcePrefix: String,
            whiteList: Set<String>,
            forceUpdatePrefixed: Boolean
        ): Triple<String, Int, Int> {
            val matcher = RESOURCE_PATTERN.matcher(content)
            val buffer = StringBuffer()
            var totalCount = 0
            var updatedCount = 0
            
            while (matcher.find()) {
                totalCount++
                val resType = matcher.group(1)
                val resName = matcher.group(2)
                val originalRef = matcher.group(0)
                
                // 检查是否在白名单中
                val resKey = "$resType:$resName"
                if (whiteList.contains(resKey) || whiteList.contains("*:$resName") || 
                    whiteList.contains("$resType:*") || whiteList.contains(resName)) {
                    matcher.appendReplacement(buffer, originalRef)
                    PluginLogger.debug("白名单跳过: $originalRef (匹配规则: $resKey)")
                    continue
                }
                
                // 如果资源名已经有前缀且不强制更新，跳过
                if (resName.startsWith(resourcePrefix) && !forceUpdatePrefixed) {
                    matcher.appendReplacement(buffer, originalRef)
                    continue
                }
                
                // 如果已有其他前缀且不强制更新，跳过
                if (resName.contains("_") && !forceUpdatePrefixed) {
                    val prefix = resName.substringBefore("_") + "_"
                    if (prefix != resourcePrefix) {
                        matcher.appendReplacement(buffer, originalRef)
                        PluginLogger.debug("保留已有前缀: $originalRef (前缀: $prefix)")
                        continue
                    }
                }
                
                // 如果是强制更新且已有前缀，移除现有前缀
                val nameWithoutPrefix = if (forceUpdatePrefixed && resName.contains("_")) {
                    resName.substringAfter("_")
                } else {
                    resName
                }
                
                // 构建新的资源引用
                val newResRef = "R.$resType.${resourcePrefix}$nameWithoutPrefix"
                matcher.appendReplacement(buffer, newResRef)
                updatedCount++
                
                PluginLogger.debug("替换资源引用: $originalRef -> $newResRef")
            }
            
            matcher.appendTail(buffer)
            return Triple(buffer.toString(), totalCount, updatedCount)
        }
        
        /**
         * 更新方法调用中的资源引用
         * @return Triple(更新后的内容, 总引用数, 更新的引用数)
         */
        private fun updateMethodResourceReferencesInFile(
            content: String,
            resourcePrefix: String,
            whiteList: Set<String>,
            forceUpdatePrefixed: Boolean
        ): Triple<String, Int, Int> {
            var resultContent = content
            var totalCount = 0
            var updatedCount = 0
            
            METHOD_RESOURCE_PATTERNS.forEach { pattern ->
                val matcher = pattern.matcher(resultContent)
                val buffer = StringBuffer()
                
                while (matcher.find()) {
                    totalCount++
                    
                    // 处理不同类型的方法调用模式
                    if (pattern.pattern().startsWith("getIdentifier")) {
                        // 特殊处理getIdentifier("name", "type", ...)
                        val resName = matcher.group(1)
                        val resType = matcher.group(2)
                        val originalRef = matcher.group(0)
                        
                        // 白名单检查和前缀处理与普通资源引用相同
                        val resKey = "$resType:$resName"
                        if (whiteList.contains(resKey) || whiteList.contains("*:$resName") || 
                            whiteList.contains("$resType:*") || whiteList.contains(resName)) {
                            matcher.appendReplacement(buffer, originalRef)
                            continue
                        }
                        
                        if (resName.startsWith(resourcePrefix) && !forceUpdatePrefixed) {
                            matcher.appendReplacement(buffer, originalRef)
                            continue
                        }
                        
                        // 如果已有其他前缀且不强制更新，跳过
                        if (resName.contains("_") && !forceUpdatePrefixed) {
                            val prefix = resName.substringBefore("_") + "_"
                            if (prefix != resourcePrefix) {
                                matcher.appendReplacement(buffer, originalRef)
                                continue
                            }
                        }
                        
                        // 处理资源名称
                        val nameWithoutPrefix = if (forceUpdatePrefixed && resName.contains("_")) {
                            resName.substringAfter("_")
                        } else {
                            resName
                        }
                        
                        // 构建新的引用格式
                        val newName = "${resourcePrefix}$nameWithoutPrefix"
                        val newRef = originalRef.replace("\"$resName\"", "\"$newName\"")
                        matcher.appendReplacement(buffer, newRef)
                        updatedCount++
                        
                        PluginLogger.debug("替换getIdentifier引用: $originalRef -> $newRef")
                    } else {
                        // 标准方法调用模式 (findViewById, setContentView等)
                        val resType = matcher.group(1)
                        val resName = matcher.group(2)
                        val originalRef = matcher.group(0)
                        
                        // 白名单检查
                        val resKey = "$resType:$resName"
                        if (whiteList.contains(resKey) || whiteList.contains("*:$resName") || 
                            whiteList.contains("$resType:*") || whiteList.contains(resName)) {
                            matcher.appendReplacement(buffer, originalRef)
                            continue
                        }
                        
                        // 前缀检查
                        if (resName.startsWith(resourcePrefix) && !forceUpdatePrefixed) {
                            matcher.appendReplacement(buffer, originalRef)
                            continue
                        }
                        
                        // 如果已有其他前缀且不强制更新，跳过
                        if (resName.contains("_") && !forceUpdatePrefixed) {
                            val prefix = resName.substringBefore("_") + "_"
                            if (prefix != resourcePrefix) {
                                matcher.appendReplacement(buffer, originalRef)
                                continue
                            }
                        }
                        
                        // 处理资源名
                        val nameWithoutPrefix = if (forceUpdatePrefixed && resName.contains("_")) {
                            resName.substringAfter("_")
                        } else {
                            resName
                        }
                        
                        // 构建新的引用
                        val newResRef = "R.$resType.${resourcePrefix}$nameWithoutPrefix"
                        val newRef = originalRef.replace("R.$resType.$resName", newResRef)
                        matcher.appendReplacement(buffer, newRef)
                        updatedCount++
                        
                        PluginLogger.debug("替换方法调用中的资源引用: $originalRef -> $newRef")
                    }
                }
                
                matcher.appendTail(buffer)
                resultContent = buffer.toString()
            }
            
            return Triple(resultContent, totalCount, updatedCount)
        }
        
        /**
         * 加载资源引用白名单
         */
        fun loadWhiteList(project: Project, whiteListConfig: Any?): Set<String> {
            if (whiteListConfig == null) {
                return emptySet()
            }
            
            val result = mutableSetOf<String>()
            
            when (whiteListConfig) {
                is String -> {
                    val file = if (File(whiteListConfig).isAbsolute) {
                        File(whiteListConfig)
                    } else {
                        project.file(whiteListConfig)
                    }
                    
                    if (file.exists()) {
                        file.readLines().forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                result.add(trimmed)
                            }
                        }
                        PluginLogger.info("从文件加载资源引用白名单: ${file.absolutePath}, ${result.size}条规则")
                    } else {
                        PluginLogger.warn("资源引用白名单文件不存在: $whiteListConfig")
                    }
                }
                is List<*> -> {
                    whiteListConfig.forEach { item ->
                        when (item) {
                            is String -> {
                                if (File(item).exists()) {
                                    val file = project.file(item)
                                    file.readLines().forEach { line ->
                                        val trimmed = line.trim()
                                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                            result.add(trimmed)
                                        }
                                    }
                                    PluginLogger.info("从文件加载资源引用白名单: ${file.absolutePath}, ${result.size}条规则")
                                } else {
                                    result.add(item)
                                }
                            }
                        }
                    }
                }
            }
            
            return result
        }
        
        /**
         * 更新项目中所有模块的资源引用
         */
        fun updateAllModulesResourceReferences(
            project: Project,
            modulePrefixMap: Map<String, String>,
            dryRun: Boolean = false,
            whiteList: Set<String> = emptySet(),
            forceUpdatePrefixed: Boolean = false
        ): Int {
            var totalUpdated = 0
            
            modulePrefixMap.forEach { (moduleName, prefix) ->
                val moduleProject = project.rootProject.findProject(":$moduleName")
                if (moduleProject != null) {
                    val updated = updateResourceReferences(
                        moduleProject,
                        moduleName,
                        prefix,
                        dryRun,
                        whiteList,
                        forceUpdatePrefixed
                    )
                    totalUpdated += updated
                } else {
                    PluginLogger.warn("未找到模块: $moduleName")
                }
            }
            
            return totalUpdated
        }
    }
} 