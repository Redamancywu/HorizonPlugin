// 作者：Redamancy  时间：2025-05-21
// 代码引用处理工具类
package com.neil.plugin.reference

import com.neil.plugin.logger.PluginLogger
import java.io.File
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONException

/**
 * 代码引用处理工具类
 * 支持：
 * 1. 识别和处理代码中的引用关系
 * 2. 分析代码依赖图
 * 3. 引用计数和清理
 * 作者：Redamancy  时间：2025-05-21
 */
object CodeReferenceHelper {
    
    /**
     * 加载白名单配置
     * 支持：
     * 1. List<String>直接配置
     * 2. 单个文件路径(String)
     * 3. 多个文件路径(List<String>)
     * @param whiteListConfig 白名单配置
     * @param projectDir 项目目录，用于解析相对路径
     * @return 白名单规则列表
     */
    fun loadCodeReferenceWhiteList(whiteListConfig: Any?, projectDir: File): List<String> {
        if (whiteListConfig == null) return emptyList()
        
        val whiteList = mutableListOf<String>()
        
        try {
            when (whiteListConfig) {
                is List<*> -> {
                    whiteListConfig.forEach { item ->
                        when (item) {
                            is String -> {
                                if (item.endsWith(".txt") || item.endsWith(".json")) {
                                    // 文件路径
                                    val file = File(if (File(item).isAbsolute) item else projectDir.absolutePath + File.separator + item)
                                    if (file.exists()) {
                                        whiteList.addAll(readWhiteListFile(file))
                                    } else {
                                        PluginLogger.warn("代码引用白名单文件不存在: ${file.absolutePath}")
                                    }
                                } else {
                                    // 直接规则
                                    whiteList.add(item)
                                }
                            }
                            else -> PluginLogger.warn("不支持的白名单配置类型: $item")
                        }
                    }
                }
                is String -> {
                    if (whiteListConfig.endsWith(".txt") || whiteListConfig.endsWith(".json")) {
                        // 文件路径
                        val file = File(if (File(whiteListConfig).isAbsolute) whiteListConfig else projectDir.absolutePath + File.separator + whiteListConfig)
                        if (file.exists()) {
                            whiteList.addAll(readWhiteListFile(file))
                        } else {
                            PluginLogger.warn("代码引用白名单文件不存在: ${file.absolutePath}")
                        }
                    } else {
                        // 直接规则
                        whiteList.add(whiteListConfig)
                    }
                }
                else -> PluginLogger.warn("不支持的白名单配置类型: $whiteListConfig")
            }
        } catch (e: Exception) {
            PluginLogger.error("加载代码引用白名单失败: ${e.message}")
            e.printStackTrace()
        }
        
        // 去重和日志
        val uniqueList = whiteList.filter { it.isNotBlank() }.distinct()
        if (uniqueList.isNotEmpty()) {
            PluginLogger.info("加载代码引用白名单: ${uniqueList.size}条规则")
            uniqueList.forEach { PluginLogger.debug(" - $it") }
        }
        
        return uniqueList
    }
    
    /**
     * 读取白名单文件内容
     */
    private fun readWhiteListFile(file: File): List<String> {
        if (!file.exists()) return emptyList()
        
        val rules = mutableListOf<String>()
        
        try {
            when {
                file.extension == "json" -> {
                    // JSON数组格式
                    val content = file.readText()
                    try {
                        val jsonArray = JSONArray(content)
                        for (i in 0 until jsonArray.length()) {
                            val rule = jsonArray.getString(i)
                            if (rule.isNotBlank()) rules.add(rule)
                        }
                    } catch (e: JSONException) {
                        PluginLogger.warn("解析JSON白名单文件失败: ${file.absolutePath}, ${e.message}")
                    }
                }
                else -> {
                    // 文本格式，每行一条规则
                    file.readLines().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                            rules.add(trimmed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            PluginLogger.warn("读取白名单文件失败: ${file.absolutePath}, ${e.message}")
        }
        
        return rules
    }
    
    /**
     * 检查类名是否在白名单中
     */
    fun isInCodeReferenceWhiteList(className: String, whiteList: List<String>): Boolean {
        return whiteList.any { rule ->
            if (rule.contains("*")) {
                val pattern = rule.replace(".", "\\.").replace("*", ".*")
                className.matches(Regex(pattern))
            } else {
                className == rule
            }
        }
    }
    
    /**
     * 分析Kotlin/Java文件中的代码引用关系
     * @param srcDir 源代码目录
     * @param moduleName 模块名称
     * @param whiteList 白名单规则
     * @return 代码引用关系图
     */
    fun analyzeCodeReferences(
        srcDir: File,
        moduleName: String,
        whiteList: List<String> = emptyList()
    ): Map<String, Set<String>> {
        val referenceMap = mutableMapOf<String, MutableSet<String>>()
        
        if (!srcDir.exists() || !srcDir.isDirectory) {
            PluginLogger.warn("源代码目录不存在: ${srcDir.absolutePath}")
            return referenceMap
        }
        
        try {
            // 搜集所有Kotlin和Java文件
            val sourceFiles = srcDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .toList()
                
            PluginLogger.info("分析模块 $moduleName 的代码引用关系，共 ${sourceFiles.size} 个源文件")
            
            // 分析每个文件的引用关系
            sourceFiles.forEach { file ->
                val className = extractClassName(file)
                if (className.isNotEmpty()) {
                    val content = file.readText()
                    val imports = extractImports(content)
                    val references = extractReferences(content, imports)
                    
                    referenceMap[className] = references
                    PluginLogger.debug("类 $className 引用了 ${references.size} 个其他类")
                }
            }
            
            // 输出引用统计
            val buildDir = File(srcDir.parentFile?.parentFile, "build")
            if (!buildDir.exists()) buildDir.mkdirs()
            
            val reportFile = File(buildDir, "reports/code-references/${moduleName}_code_references.json")
            reportFile.parentFile.mkdirs()
            reportFile.writeText(formatReferenceMap(referenceMap))
            PluginLogger.info("代码引用分析报告已生成: ${reportFile.absolutePath}")
            
            return referenceMap
        } catch (e: Exception) {
            PluginLogger.error("分析代码引用关系失败: ${e.message}")
            e.printStackTrace()
            return referenceMap
        }
    }
    
    /**
     * 从文件路径提取类名
     */
    private fun extractClassName(file: File): String {
        try {
            val content = file.readText()
            val packagePattern = Pattern.compile("package\\s+([\\w.]+)")
            val packageMatcher = packagePattern.matcher(content)
            
            val packageName = if (packageMatcher.find()) {
                packageMatcher.group(1)
            } else {
                ""
            }
            
            val classPattern = Pattern.compile("(class|interface|object|enum)\\s+(\\w+)")
            val classMatcher = classPattern.matcher(content)
            
            return if (classMatcher.find()) {
                if (packageName.isEmpty()) classMatcher.group(2) else "$packageName.${classMatcher.group(2)}"
            } else {
                ""
            }
        } catch (e: Exception) {
            PluginLogger.warn("提取类名失败: ${file.absolutePath}, ${e.message}")
            return ""
        }
    }
    
    /**
     * 提取导入语句
     */
    private fun extractImports(content: String): Set<String> {
        val imports = mutableSetOf<String>()
        val importPattern = Pattern.compile("import\\s+([\\w.]+)(?:\\s+as\\s+\\w+)?")
        val matcher = importPattern.matcher(content)
        
        while (matcher.find()) {
            imports.add(matcher.group(1))
        }
        
        return imports
    }
    
    /**
     * 提取代码引用关系
     */
    private fun extractReferences(content: String, imports: Set<String>): MutableSet<String> {
        val references = mutableSetOf<String>()
        
        // 添加导入的类
        references.addAll(imports)
        
        // 分析内联引用
        // 1. 提取类型引用（如变量声明、方法参数等）
        val typePattern = Pattern.compile(":\\s*([A-Z][\\w.]+)(?:<.*>)?")
        val typeMatcher = typePattern.matcher(content)
        while (typeMatcher.find()) {
            val type = typeMatcher.group(1)
            if (!type.startsWith("kotlin.") && !type.startsWith("java.lang.")) {
                // 检查是否是完全限定名
                if (type.contains(".")) {
                    references.add(type)
                } else {
                    // 尝试通过导入找到完全限定名
                    imports.find { it.endsWith(".$type") }?.let {
                        references.add(it)
                    }
                }
            }
        }
        
        // 2. 提取构造函数调用
        val constructorPattern = Pattern.compile("\\bnew\\s+([A-Z][\\w.]+)\\s*\\(")
        val constructorMatcher = constructorPattern.matcher(content)
        while (constructorMatcher.find()) {
            val className = constructorMatcher.group(1)
            if (!className.startsWith("kotlin.") && !className.startsWith("java.lang.")) {
                if (className.contains(".")) {
                    references.add(className)
                } else {
                    imports.find { it.endsWith(".$className") }?.let {
                        references.add(it)
                    }
                }
            }
        }
        
        // 3. 提取Kotlin构造函数调用（不带new）
        val kotlinConstructorPattern = Pattern.compile("\\b([A-Z][\\w.]+)\\s*\\(")
        val kotlinConstructorMatcher = kotlinConstructorPattern.matcher(content)
        while (kotlinConstructorMatcher.find()) {
            val className = kotlinConstructorMatcher.group(1)
            // 避免误匹配方法调用
            if (!className.startsWith("kotlin.") && !className.startsWith("java.lang.") && isLikelyClass(className)) {
                if (className.contains(".")) {
                    references.add(className)
                } else {
                    imports.find { it.endsWith(".$className") }?.let {
                        references.add(it)
                    }
                }
            }
        }
        
        // 4. 提取静态方法/属性调用
        val staticPattern = Pattern.compile("\\b([A-Z][\\w.]+)\\.\\w+")
        val staticMatcher = staticPattern.matcher(content)
        while (staticMatcher.find()) {
            val className = staticMatcher.group(1)
            if (!className.startsWith("kotlin.") && !className.startsWith("java.lang.") && 
                !className.equals("R", ignoreCase = true)) { // 排除R资源引用
                if (className.contains(".")) {
                    references.add(className)
                } else {
                    imports.find { it.endsWith(".$className") }?.let {
                        references.add(it)
                    }
                }
            }
        }
        
        // 5. 提取继承和实现
        val extendsPattern = Pattern.compile("(extends|implements|:)\\s+([A-Z][\\w.,<>\\s]+)")
        val extendsMatcher = extendsPattern.matcher(content)
        while (extendsMatcher.find()) {
            val inheritanceList = extendsMatcher.group(2)
            // 分割多个继承/实现
            inheritanceList.split(",").forEach { item ->
                val className = item.trim().split("<")[0].trim() // 移除泛型部分
                if (!className.startsWith("kotlin.") && !className.startsWith("java.lang.")) {
                    if (className.contains(".")) {
                        references.add(className)
                    } else {
                        imports.find { it.endsWith(".$className") }?.let {
                            references.add(it)
                        }
                    }
                }
            }
        }
        
        return references
    }
    
    /**
     * 判断标识符是否可能是类名
     * 大写字母开头，非关键字
     */
    private fun isLikelyClass(identifier: String): Boolean {
        val firstChar = identifier.firstOrNull() ?: return false
        if (!firstChar.isUpperCase()) return false
        
        // Kotlin关键字列表
        val keywords = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", 
            "in", "interface", "is", "null", "object", "package", "return", "super", "this", 
            "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while"
        )
        
        return !keywords.contains(identifier.lowercase())
    }
    
    /**
     * 格式化引用关系图为JSON
     */
    private fun formatReferenceMap(referenceMap: Map<String, Set<String>>): String {
        val sb = StringBuilder("{\n")
        
        referenceMap.entries.forEachIndexed { index, (className, references) ->
            sb.append("  \"$className\": [")
            if (references.isNotEmpty()) {
                sb.append("\n")
                references.forEachIndexed { refIndex, ref ->
                    sb.append("    \"$ref\"")
                    if (refIndex < references.size - 1) {
                        sb.append(",")
                    }
                    sb.append("\n")
                }
                sb.append("  ]")
            } else {
                sb.append("]")
            }
            
            if (index < referenceMap.size - 1) {
                sb.append(",")
            }
            sb.append("\n")
        }
        
        sb.append("}")
        return sb.toString()
    }
    
    /**
     * 查找未使用的类
     * @param referenceMap 引用关系图
     * @param whiteList 白名单规则
     * @return 未被引用的类列表
     */
    fun findUnusedClasses(
        referenceMap: Map<String, Set<String>>,
        whiteList: List<String> = emptyList()
    ): Set<String> {
        val allClasses = referenceMap.keys.toSet()
        val referencedClasses = mutableSetOf<String>()
        
        // 收集所有被引用的类
        referenceMap.values.forEach { references ->
            referencedClasses.addAll(references.filter { it in allClasses })
        }
        
        // 过滤掉白名单中的类
        val unusedClasses = allClasses - referencedClasses
        return unusedClasses.filter { className ->
            !isInCodeReferenceWhiteList(className, whiteList)
        }.toSet()
    }
    
    /**
     * 生成依赖图可视化文件(DOT格式，可用Graphviz渲染)
     */
    fun generateDependencyGraph(
        referenceMap: Map<String, Set<String>>,
        outputFile: File,
        simplifyNames: Boolean = true
    ) {
        try {
            val dot = StringBuilder("digraph CodeDependencies {\n")
            dot.append("  rankdir=LR;\n")
            dot.append("  node [shape=box, style=filled, fillcolor=lightblue];\n\n")
            
            referenceMap.forEach { (className, references) ->
                val shortClassName = if (simplifyNames) {
                    className.substringAfterLast('.')
                } else {
                    className
                }
                
                references.filter { it in referenceMap.keys }.forEach { ref ->
                    val shortRef = if (simplifyNames) {
                        ref.substringAfterLast('.')
                    } else {
                        ref
                    }
                    
                    dot.append("  \"$shortClassName\" -> \"$shortRef\";\n")
                }
            }
            
            dot.append("}")
            outputFile.parentFile.mkdirs()
            outputFile.writeText(dot.toString())
            PluginLogger.info("依赖图已生成: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            PluginLogger.error("生成依赖图失败: ${e.message}")
        }
    }
} 