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
        whiteList: List<String> = emptyList()
    ): Int {
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
        // 统计处理的资源数量
        var processedCount = 0
        
        // 1. 文件级资源自动重命名，并处理xml内容
        resDir.walkTopDown().filter { it.isFile && it.parentFile.name != "." && it.parentFile.name != "values" && it.name != "AndroidManifest.xml" }.forEach { resFile ->
            val nameWithoutExt = resFile.nameWithoutExtension
            val type = resFile.parentFile.name
            if (isInWhiteList(nameWithoutExt, type, whiteList)) {
                PluginLogger.info("资源白名单跳过：$type/$nameWithoutExt")
                return@forEach
            }
            val legalName = legalizeResourceName(nameWithoutExt)
            
            // 检查是否需要重命名
            val needRename = when (namingStrategy) {
                ResourceNamingStrategy.PREFIX -> !resFile.name.startsWith(finalPrefix)
                ResourceNamingStrategy.SUFFIX -> {
                    val dotIndex = resFile.name.lastIndexOf(".")
                    if (dotIndex > 0) {
                        !resFile.name.substring(0, dotIndex).endsWith(finalSuffix)
                    } else true
                }
            }
            
            if (needRename) {
                val oldMd5 = md5(resFile.readBytes()).substring(0, md5Length)
                
                // 生成新文件名
                val newName = when (namingStrategy) {
                    ResourceNamingStrategy.PREFIX -> {
                        if (enableMd5) {
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
                        if (enableMd5) {
                            if (keepOriginalName) {
                                "$legalName${finalSuffix}_$oldMd5.${resFile.extension}"
                            } else {
                                "res${finalSuffix}_$oldMd5.${resFile.extension}"
                            }
                        } else {
                            "$legalName$finalSuffix.${resFile.extension}"
                        }
                    }
                }
                
                val newFile = File(resFile.parent + File.separator + newName)
                if (newFile.exists()) {
                    PluginLogger.warn("资源重命名冲突：${newFile.name} 已存在，跳过 ${resFile.name}")
                    PluginLogger.error("资源重命名冲突：${newFile.name} 已存在，跳过 ${resFile.name}")
                } else {
                    val backupFile = File(resFile.parent, resFile.name + ".bak_resreplace")
                    if (!backupFile.exists()) resFile.copyTo(backupFile)
                    val renameSuccess = resFile.renameTo(newFile)
                    val newMd5 = if (newFile.exists()) md5(newFile.readBytes()).substring(0, md5Length) else "文件不存在"
                    PluginLogger.info("资源自动重命名：${resFile.name} -> ${newFile.name}，MD5: $oldMd5 -> $newMd5")
                    PluginLogger.debug("资源自动重命名：${resFile.name} -> ${newFile.name}，MD5: $oldMd5 -> $newMd5")
                    if (!renameSuccess) {
                        PluginLogger.warn("资源重命名失败：${resFile.name} -> ${newFile.name}，请检查文件权限或是否已存在同名文件")
                        PluginLogger.error("资源重命名失败：${resFile.name} -> ${newFile.name}，请检查文件权限或是否已存在同名文件")
                    } else {
                        renameMap[nameWithoutExt] = File(newFile.parent, newFile.name).nameWithoutExtension
                        processedCount++
                    }
                }
            }
            if (resFile.extension == "xml") {
                val beforeMd5 = md5(resFile.readBytes()).substring(0, md5Length)
                val xmlChanged = processXmlResourceContent(resFile, finalPrefix, finalSuffix, namingStrategy, enableMd5)
                if (xmlChanged) {
                    processedCount++
                }
            }
        }
        // 2. values资源项自动加前缀/后缀
        val valuesDir = File(resDir, "values")
        valuesDir.listFiles { file -> file.extension == "xml" }?.forEach { xmlFile ->
            val oldMd5 = md5(xmlFile.readBytes()).substring(0, md5Length)
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
            val nodes = doc.getElementsByTagName("*")
            var changed = false
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node is org.w3c.dom.Element && node.hasAttribute("name")) {
                    val name = node.getAttribute("name")
                    val type = node.tagName
                    if (isInWhiteList(name, type, whiteList)) {
                        PluginLogger.info("资源白名单跳过：$type/$name")
                        continue
                    }
                    val legalName = legalizeResourceName(name)
                    
                    // 检查是否需要重命名
                    val needRename = when (namingStrategy) {
                        ResourceNamingStrategy.PREFIX -> !name.startsWith(finalPrefix)
                        ResourceNamingStrategy.SUFFIX -> !name.endsWith(finalSuffix)
                    }
                    
                    if (needRename) {
                        val newName = when (namingStrategy) {
                            ResourceNamingStrategy.PREFIX -> {
                                if (enableMd5) {
                                    if (keepOriginalName) {
                                        "$finalPrefix${md5(name.toByteArray()).substring(0, md5Length)}_$legalName"
                                    } else {
                                        "$finalPrefix${md5(name.toByteArray()).substring(0, md5Length)}"
                                    }
                                } else {
                                    "$finalPrefix$legalName"
                                }
                            }
                            ResourceNamingStrategy.SUFFIX -> {
                                if (enableMd5) {
                                    if (keepOriginalName) {
                                        "$legalName${finalSuffix}_${md5(name.toByteArray()).substring(0, md5Length)}"
                                    } else {
                                        "res${finalSuffix}_${md5(name.toByteArray()).substring(0, md5Length)}"
                                    }
                                } else {
                                    "$legalName$finalSuffix"
                                }
                            }
                        }
                        
                        // 检查是否存在命名冲突
                        val conflictExists = valuesDir.walkTopDown().any { 
                            it.isFile && it.extension == "xml" && it.readText().contains("name=\"$newName\"") 
                        }
                        
                        if (conflictExists) {
                            PluginLogger.warn("values资源命名冲突：$newName 已存在，跳过 $name")
                            PluginLogger.error("values资源命名冲突：$newName 已存在，跳过 $name")
                            continue
                        }
                        
                        node.setAttribute("name", newName)
                        val nameTypeText = if (namingStrategy == ResourceNamingStrategy.PREFIX) "前缀" else "后缀"
                        PluginLogger.info("values资源自动添加$nameTypeText：$name -> $newName")
                        PluginLogger.debug("values资源自动添加$nameTypeText：$name -> $newName")
                        changed = true
                        renameMap[name] = newName
                    }
                }
            }
            if (changed) {
                val transformer = TransformerFactory.newInstance().newTransformer()
                transformer.transform(DOMSource(doc), StreamResult(xmlFile))
                val newMd5 = md5(xmlFile.readBytes()).substring(0, md5Length)
                PluginLogger.info("values资源变更MD5: ${xmlFile.name} $oldMd5 -> $newMd5")
                PluginLogger.debug("values资源变更MD5: ${xmlFile.name} $oldMd5 -> $newMd5")
                processedCount++
            }
        }
        // 3. 自动同步所有xml文件中的资源引用（只替换资源名部分，保留@+id/等格式）
        val xmlFiles = resDir.walkTopDown().filter { it.isFile && it.extension == "xml" && it.name != "AndroidManifest.xml" }.toList()
        val resourceTypes = listOf("drawable", "color", "string", "id", "layout", "anim", "menu", "mipmap", "xml", "raw", "font", "styleable", "attr", "plurals", "bool", "integer", "array", "dimen", "fraction", "interpolator")
        xmlFiles.forEach { xmlFile ->
            var content = xmlFile.readText()
            var changed = false
            renameMap.forEach { (oldName, newName) ->
                resourceTypes.forEach { type ->
                    if (isInWhiteList(oldName, type, whiteList)) return@forEach
                    // 只替换@+id/oldName 或 @id/oldName 或 @type/oldName 的 oldName 部分，保留@+id/等格式
                    val regex = Regex("(@\\+?${type}/)${oldName}\\b")
                    val before = content
                    content = content.replace(regex) { matchResult ->
                        matchResult.groupValues[1] + newName
                    }
                    // Databinding表达式 @{...@type/oldName...}
                    val dbRegex = Regex("@\\{[^}]*@${type}/${oldName}[^}]*}")
                    content = content.replace(dbRegex) { it.value.replace("@${type}/${oldName}", "@${type}/${newName}") }
                    if (before != content) {
                        PluginLogger.info("xml资源引用同步：${xmlFile.name} @${type}/${oldName} -> @${type}/${newName}")
                        PluginLogger.debug("xml资源引用同步：${xmlFile.name} @${type}/${oldName} -> @${type}/${newName}")
                        changed = true
                    }
                }
            }
            if (changed) {
                xmlFile.writeText(content)
                processedCount++
            }
        }
        // 4. 导出重命名映射表
        try {
            val buildDir = File(resDir.parentFile?.parentFile, mapOutputPath.substringBeforeLast("/"))
            if (!buildDir.exists()) buildDir.mkdirs()
            val mapFile = File(resDir.parentFile?.parentFile, mapOutputPath)
            mapFile.writeText(renameMap.entries.joinToString(prefix = "{\n", postfix = "\n}", separator = ",\n") { "    \"${it.key}\": \"${it.value}\"" })
            PluginLogger.info("资源重命名映射表已导出到: ${mapFile.absolutePath}")
        } catch (e: Exception) {
            PluginLogger.warn("导出资源重命名映射表失败: ${e.message}")
            PluginLogger.error("导出资源重命名映射表失败: ${e.message}")
        }
        // 5. 自动同步所有源码中的 R.type.oldName -> R.type.newName
        try {
            val srcDirs = listOf("src${File.separator}main${File.separator}java", "src${File.separator}main${File.separator}kotlin")
            val moduleDir = resDir.parentFile?.parentFile ?: resDir.parentFile
            srcDirs.forEach { srcDirPath ->
                val srcDir = File(moduleDir, srcDirPath)
                if (srcDir.exists()) {
                    srcDir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.forEach { codeFile ->
                        val backupFile = File(codeFile.parent, codeFile.name + ".bak_resreplace")
                        if (!backupFile.exists()) codeFile.copyTo(backupFile)
                        var content = codeFile.readText()
                        var changed = false
                        renameMap.forEach { (oldName, newName) ->
                            resourceTypes.forEach { type ->
                                if (isInWhiteList(oldName, type, whiteList)) return@forEach
                                val regex = Regex("""R\.$type\.$oldName\b""")
                                val before = content
                                content = content.replace(regex, "R.$type.$newName")
                                if (before != content) {
                                    PluginLogger.info("代码资源引用同步：${codeFile.name} R.$type.$oldName -> R.$type.$newName")
                                    PluginLogger.debug("代码资源引用同步：${codeFile.name} R.$type.$oldName -> R.$type.$newName")
                                    changed = true
                                }
                            }
                        }
                        if (changed) {
                            codeFile.writeText(content)
                            processedCount++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            PluginLogger.warn("自动同步代码资源引用失败: ${e.message}")
            PluginLogger.error("自动同步代码资源引用失败: ${e.message}")
        }
        // 6. 自动同步 ViewBinding 代码中的 id 属性名（binding.oldId -> binding.newId），支持下划线转驼峰
        try {
            val idRenameMap = renameMap.filterKeys { it.isNotBlank() && it.matches(Regex("[a-zA-Z0-9_]+")) }
            fun toCamelCase(id: String): String {
                return id.split('_').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }.replaceFirstChar { it.lowercase() }
            }
            val srcDirs = listOf("src${File.separator}main${File.separator}java", "src${File.separator}main${File.separator}kotlin")
            val moduleDir = resDir.parentFile?.parentFile ?: resDir.parentFile
            srcDirs.forEach { srcDirPath ->
                val srcDir = File(moduleDir, srcDirPath)
                if (srcDir.exists()) {
                    srcDir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.forEach { codeFile ->
                        val backupFile = File(codeFile.parent, codeFile.name + ".bak_viewbindingreplace")
                        if (!backupFile.exists()) codeFile.copyTo(backupFile)
                        var content = codeFile.readText()
                        var changed = false
                        idRenameMap.forEach { (oldId, newId) ->
                            val oldProp = toCamelCase(oldId)
                            val newProp = toCamelCase(newId)
                            val regex = Regex("binding\\.$oldProp\\b")
                            val before = content
                            content = content.replace(regex, "binding.$newProp")
                            if (before != content) {
                                PluginLogger.info("ViewBinding属性同步：${codeFile.name} binding.$oldProp -> binding.$newProp")
                                PluginLogger.debug("ViewBinding属性同步：${codeFile.name} binding.$oldProp -> binding.$newProp")
                                changed = true
                            }
                        }
                        if (changed) {
                            codeFile.writeText(content)
                            processedCount++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            PluginLogger.warn("自动同步ViewBinding属性名失败: ${e.message}")
            PluginLogger.error("自动同步ViewBinding属性名失败: ${e.message}")
        }
        // 7. 资源引用统计与冗余检测（输出未被引用资源）
        try {
            val allResourceNames = renameMap.values.toSet()
            val allCodeFiles = mutableListOf<File>()
            val srcDirs = listOf("src${File.separator}main${File.separator}java", "src${File.separator}main${File.separator}kotlin")
            val moduleDir = resDir.parentFile?.parentFile ?: resDir.parentFile
            srcDirs.forEach { srcDirPath ->
                val srcDir = File(moduleDir, srcDirPath)
                if (srcDir.exists()) {
                    allCodeFiles.addAll(srcDir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") })
                }
            }
            val allXmlFiles = resDir.walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()
            val used = mutableSetOf<String>()
            allResourceNames.forEach { resName ->
                val regex = Regex("\b$resName\b")
                if (allCodeFiles.any { regex.containsMatchIn(it.readText()) } || allXmlFiles.any { regex.containsMatchIn(it.readText()) }) {
                    used.add(resName)
                }
            }
            val unused = allResourceNames - used
            if (unused.isNotEmpty()) {
                val unusedFile = File(resDir.parentFile?.parentFile, "build/unused_resources.txt")
                unusedFile.writeText(unused.joinToString("\n"))
                PluginLogger.info("未被引用的资源已输出到: ${unusedFile.absolutePath}")
            }
        } catch (e: Exception) {
            PluginLogger.warn("资源冗余检测失败: ${e.message}")
            PluginLogger.error("资源冗余检测失败: ${e.message}")
        }
        // 8. 回滚与备份机制说明
        PluginLogger.info("所有源码和xml自动替换前已自动备份为 .bak_resreplace/.bak_viewbindingreplace 文件，如需回滚可手动还原。")
        
        // 最后返回处理的资源数量
        return processedCount
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
                    }
                    
                    if (needUpdate) {
                        val newName = when (namingStrategy) {
                            ResourceNamingStrategy.PREFIX -> "$prefix$name"
                            ResourceNamingStrategy.SUFFIX -> "$name$suffix"
                        }
                        val strategyName = if (namingStrategy == ResourceNamingStrategy.PREFIX) "前缀" else "后缀"
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
     * 读取白名单配置
     * 支持 List<String>、String(文件路径)、List<String>(多文件路径)
     * 文件支持 .txt（每行一个规则）、.json（数组）
     * @param config DSL配置
     * @param projectDir 工程目录
     * @return 白名单规则列表
     * 作者：Redamancy  时间：2025-05-19
     */
    fun loadWhiteListFromConfig(config: Any?, projectDir: File): List<String> {
        fun readFile(file: File): List<String> {
            return if (file.exists()) {
                when {
                    file.extension == "json" -> {
                        // 简单json数组解析
                        val text = file.readText().trim()
                        Regex("\\[.*?\\]", RegexOption.DOT_MATCHES_ALL).find(text)?.value
                            ?.removeSurrounding("[", "]")
                            ?.split(",")
                            ?.map { it.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'") }
                            ?.filter { it.isNotEmpty() } ?: emptyList()
                    }
                    else -> file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                }
            } else {
                PluginLogger.warn("资源白名单文件不存在: ${file.absolutePath}")
                emptyList()
            }
        }
        return when (config) {
            is List<*> -> config.flatMap {
                when (it) {
                    is String -> {
                        if (it.endsWith(".txt") || it.endsWith(".json")) {
                            val fileToRead = if (File(it).isAbsolute) File(it) else File(projectDir, it)
                            readFile(fileToRead)
                        } else listOf(it)
                    }
                    else -> emptyList()
                }
            }.toSet().toList()
            is String -> {
                if (config.endsWith(".txt") || config.endsWith(".json")) {
                    val fileToRead = if (File(config).isAbsolute) File(config) else File(projectDir, config)
                    readFile(fileToRead)
                } else listOf(config)
            }
            else -> emptyList()
        }
    }
} 