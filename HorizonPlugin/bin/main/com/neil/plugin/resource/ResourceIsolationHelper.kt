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
 * 1. 文件级资源自动重命名（加前缀/加md5）
 * 2. values资源项自动加前缀
 * @param prefix 资源前缀
 * @param enableMd5 是否启用md5重命名
 * 作者：Redamancy  时间：2025-05-19
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

    fun processResDir(
        resDir: File,
        prefix: String,
        enableMd5: Boolean = false,
        moduleName: String? = null,
        flavorName: String? = null,
        resourcePrefixPattern: String? = null,
        whiteList: List<String> = emptyList()
    ) {
        val finalPrefix = resourcePrefixPattern
            ?.replace("{module}", moduleName ?: "")
            ?.replace("{flavor}", flavorName ?: "")
            ?: prefix
        val renameMap = mutableMapOf<String, String>()
        // 1. 文件级资源自动重命名，并处理xml内容
        resDir.walkTopDown().filter { it.isFile && it.parentFile.name != "." && it.parentFile.name != "values" && it.name != "AndroidManifest.xml" }.forEach { resFile ->
            val nameWithoutExt = resFile.nameWithoutExtension
            val type = resFile.parentFile.name
            if (isInWhiteList(nameWithoutExt, type, whiteList)) {
                PluginLogger.info("资源白名单跳过：$type/$nameWithoutExt")
                return@forEach
            }
            val legalName = legalizeResourceName(nameWithoutExt)
            if (!resFile.name.startsWith(finalPrefix)) {
                val oldMd5 = md5(resFile.readBytes())
                val newName = if (enableMd5) finalPrefix + oldMd5 + "_" + legalName + "." + resFile.extension else finalPrefix + legalName + "." + resFile.extension
                val newFile = File(resFile.parent + File.separator + newName)
                if (newFile.exists()) {
                    PluginLogger.warn("资源重命名冲突：${newFile.name} 已存在，跳过 ${resFile.name}")
                    PluginLogger.error("资源重命名冲突：${newFile.name} 已存在，跳过 ${resFile.name}")
                } else {
                    val backupFile = File(resFile.parent, resFile.name + ".bak_resreplace")
                    if (!backupFile.exists()) resFile.copyTo(backupFile)
                    val renameSuccess = resFile.renameTo(newFile)
                    val newMd5 = if (newFile.exists()) md5(newFile.readBytes()) else "文件不存在"
                    PluginLogger.info("资源自动重命名：${resFile.name} -> ${newFile.name}，MD5: $oldMd5 -> $newMd5")
                    PluginLogger.debug("资源自动重命名：${resFile.name} -> ${newFile.name}，MD5: $oldMd5 -> $newMd5")
                    if (!renameSuccess) {
                        PluginLogger.warn("资源重命名失败：${resFile.name} -> ${newFile.name}，请检查文件权限或是否已存在同名文件")
                        PluginLogger.error("资源重命名失败：${resFile.name} -> ${newFile.name}，请检查文件权限或是否已存在同名文件")
                    } else {
                        renameMap[nameWithoutExt] = File(newFile.parent, newFile.name).nameWithoutExtension
                    }
                }
            }
            if (resFile.extension == "xml") {
                processXmlResourceContent(resFile, finalPrefix, enableMd5)
            }
        }
        // 2. values资源项自动加前缀（保持原有）
        val valuesDir = File(resDir, "values")
        valuesDir.listFiles { file -> file.extension == "xml" }?.forEach { xmlFile ->
            val oldMd5 = md5(xmlFile.readBytes())
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
                    val newName = if (enableMd5) finalPrefix + md5(name.toByteArray()) + "_" + legalName else finalPrefix + legalName
                    if (!name.startsWith(finalPrefix) && valuesDir.walkTopDown().any { it.isFile && it.extension == "xml" && it.readText().contains("name=\"$newName\"") }) {
                        PluginLogger.warn("values资源命名冲突：$newName 已存在，跳过 $name")
                        PluginLogger.error("values资源命名冲突：$newName 已存在，跳过 $name")
                        continue
                    }
                    if (!name.startsWith(finalPrefix)) {
                        node.setAttribute("name", newName)
                        PluginLogger.info("values资源自动加前缀：$name -> $newName")
                        PluginLogger.debug("values资源自动加前缀：$name -> $newName")
                        changed = true
                        renameMap[name] = newName
                    }
                }
            }
            if (changed) {
                val transformer = TransformerFactory.newInstance().newTransformer()
                transformer.transform(DOMSource(doc), StreamResult(xmlFile))
                val newMd5 = md5(xmlFile.readBytes())
                PluginLogger.info("values资源变更MD5: ${xmlFile.name} $oldMd5 -> $newMd5")
                PluginLogger.debug("values资源变更MD5: ${xmlFile.name} $oldMd5 -> $newMd5")
            }
        }
        // 3. 自动同步所有xml文件中的资源引用（保留@+id/xxx格式不变，正则优化，支持Databinding表达式）
        val xmlFiles = resDir.walkTopDown().filter { it.isFile && it.extension == "xml" && it.name != "AndroidManifest.xml" }.toList()
        val resourceTypes = listOf("drawable", "color", "string", "id", "layout", "anim", "menu", "mipmap", "xml", "raw", "font", "styleable", "attr", "plurals", "bool", "integer", "array", "dimen", "fraction", "interpolator")
        xmlFiles.forEach { xmlFile ->
            var content = xmlFile.readText()
            var changed = false
            renameMap.forEach { (oldName, newName) ->
                resourceTypes.forEach { type ->
                    if (isInWhiteList(oldName, type, whiteList)) return@forEach
                    // 替换@type/oldName为@type/newName，但不替换@+id/oldName，支持边界和引号
                    val regex = Regex("@(?!!\+id/)$type/$oldName(?=[\b"'\s<])")
                    val before = content
                    content = content.replace(regex) { matchResult ->
                        "@${type}/$newName"
                    }
                    // Databinding表达式 @{...@string/oldName...}
                    val dbRegex = Regex("@\{[^}]*@$type/$oldName[^}]*}")
                    content = content.replace(dbRegex) { it.value.replace("@$type/$oldName", "@$type/$newName") }
                    if (before != content) {
                        PluginLogger.info("xml资源引用同步：${xmlFile.name} @${type}/$oldName -> @${type}/$newName")
                        PluginLogger.debug("xml资源引用同步：${xmlFile.name} @${type}/$oldName -> @${type}/$newName")
                        changed = true
                    }
                }
            }
            if (changed) {
                xmlFile.writeText(content)
            }
        }
        // 4. 导出重命名映射表到 build/resource_rename_map.json
        try {
            val buildDir = File(resDir.parentFile?.parentFile, "build")
            if (!buildDir.exists()) buildDir.mkdirs()
            val mapFile = File(buildDir, "resource_rename_map.json")
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
    }

    private fun md5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // 新增：处理xml内容中的资源名
    private fun processXmlResourceContent(xmlFile: File, prefix: String, enableMd5: Boolean) {
        try {
            val oldMd5 = md5(xmlFile.readBytes())
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
            val nodes = doc.getElementsByTagName("*")
            var changed = false
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node is org.w3c.dom.Element) {
                    // 处理android:id、style、name等属性
                    val attrs = listOf("android:id", "style", "name", "color", "drawable", "attr", "string")
                    for (attr in attrs) {
                        if (node.hasAttribute(attr)) {
                            val value = node.getAttribute(attr)
                            if (!value.startsWith(prefix) && value.startsWith("@")) {
                                val newValue = "@" + prefix + value.substring(1)
                                node.setAttribute(attr, newValue)
                                PluginLogger.info("xml资源内容自动加前缀：$value -> $newValue")
                                PluginLogger.debug("xml资源内容自动加前缀：$value -> $newValue")
                                changed = true
                            }
                        }
                    }
                }
            }
            if (changed) {
                val transformer = TransformerFactory.newInstance().newTransformer()
                transformer.transform(DOMSource(doc), StreamResult(xmlFile))
                val newMd5 = md5(xmlFile.readBytes())
                PluginLogger.info("xml资源内容变更MD5: ${xmlFile.name} $oldMd5 -> $newMd5")
                PluginLogger.debug("xml资源内容变更MD5: ${xmlFile.name} $oldMd5 -> $newMd5")
            }
        } catch (e: Exception) {
            PluginLogger.warn("处理xml资源内容时出错：${xmlFile.name}，原因：${e.message}")
            PluginLogger.error("处理xml资源内容时出错：${xmlFile.name}，原因：${e.message}")
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
                        Regex("\\[.*?]", RegexOption.DOT_MATCHES_ALL).find(text)?.value
                            ?.removeSurrounding("[", "]")\
                            ?.split(",")\
                            ?.map { it.trim().removeSurrounding("\"", "\"") }\
                            ?.filter { it.isNotEmpty() } ?: emptyList()
                    }\n                    else -> file.readLines().map { it.trim() }.filter { it.isNotEmpty() }\n                }\n            } else emptyList()\n        }\n        return when (config) {\n            is List<*> -> config.flatMap {\n                if (it is String && (it.endsWith(".txt") || it.endsWith(".json"))) {\n                    readFile(File(if (File(it).isAbsolute) it else File(projectDir, it).absolutePath))\n                } else if (it is String) listOf(it) else emptyList()\n            }.toSet().toList()\n            is String -> {\n                if (config.endsWith(".txt") || config.endsWith(".json")) {\n                    readFile(File(if (File(config).isAbsolute) config else File(projectDir, config).absolutePath))\n                } else listOf(config)\n            }\n            else -> emptyList()\n        }\n    }
} 