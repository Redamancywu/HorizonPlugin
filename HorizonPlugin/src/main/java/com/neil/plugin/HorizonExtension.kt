// 作者：Redamancy  时间：2025-05-23
// Horizon插件DSL扩展
package com.neil.plugin

import com.neil.plugin.logger.LogLevel
import com.neil.plugin.logger.PluginLogger
import com.neil.plugin.resource.ResourceNamingStrategy
import org.gradle.api.Project
import java.io.File

/**
 * Horizon插件DSL扩展，支持自动注册等功能配置
 * 作者：Redamancy  时间：2025-05-23
 */
open class HorizonExtension {
    /** 是否启用自动注册，默认true */
    var enableAutoRegister: Boolean = true

    /** 需要扫描的模块包名列表 */
    var modulePackages: MutableList<String> = mutableListOf()

    /** 需要排除的包名列表 */
    var excludePackages: MutableList<String> = mutableListOf()

    /** 生成的注册类名，默认 ModuleRegistry */
    var registerClassName: String = "ModuleRegistry"

    /** 生成代码输出目录（可选，默认 build/generated/horizon） */
    var outputDir: String = "build/generated/horizon"

    /** 日志级别，默认INFO，可选DEBUG/INFO/WARN/ERROR */
    var logLevel: String = "INFO"

    /** 额外参数，便于后续扩展 */
    var extraArgs: MutableMap<String, Any> = mutableMapOf()

    /** 生成注册类的包名，默认 com.neil.plugin.autoregister */
    var generatedPackage: String = "com.neil.plugin.autoregister"

    /** 追加自定义混淆规则 */
    var proguardRules: MutableList<String> = mutableListOf()

    /**
     * 资源白名单配置
     * 支持：直接 List<String>，或指定文件路径（String/多个文件路径 List<String>）
     * 文件支持 .txt（每行一个规则）、.json（数组）等，便于团队协作和版本管理
     * 示例：resourceWhiteList = listOf("drawable:ic_sdk_*", "string:keep_me", "my_third_party_*")
     *      resourceWhiteList = "sdk_whitelist.txt"
     *      resourceWhiteList = listOf("sdk_whitelist.txt", "module_whitelist.txt")
     * 作者：Redamancy  时间：2025-05-23
     */
    var resourceWhiteList: Any? = null
    
    /**
     * 资源前缀模式
     * 支持：{module}=模块名, {flavor}=flavor名
     * 示例："{module}_{flavor}_"
     * 作者：Redamancy  时间：2025-05-23
     */
    var resourcePrefixPattern: String? = null
    
    /**
     * 资源后缀模式
     * 支持：{module}=模块名, {flavor}=flavor名
     * 示例："_{module}_{flavor}"
     * 作者：Redamancy  时间：2025-05-27
     */
    var resourceSuffixPattern: String? = null
    
    /**
     * 资源命名策略
     * PREFIX: 前缀模式，在资源名前添加前缀
     * SUFFIX: 后缀模式，在资源名后添加后缀
     * 默认为PREFIX
     * 作者：Redamancy  时间：2025-05-27
     */
    var resourceNamingStrategy: ResourceNamingStrategy = ResourceNamingStrategy.PREFIX
    
    /**
     * 是否保留原始资源名
     * true: 保留原始资源名，在前缀/后缀之后保留原名
     * false: 不保留原始资源名，使用MD5替代
     * 默认为true
     * 作者：Redamancy  时间：2025-05-27
     */
    var keepOriginalName: Boolean = true
    
    /**
     * MD5长度，用于生成MD5资源名称时指定长度
     * 默认为8
     * 作者：Redamancy  时间：2025-05-27
     */
    var resourceMd5Length: Int = 8
    
    /**
     * 资源映射表输出路径
     * 默认为"build/reports/resources/resource_rename_map.json"
     * 作者：Redamancy  时间：2025-05-27
     */
    var resourceMapOutputPath: String = "build/reports/resources/resource_rename_map.json"
    
    /**
     * 是否启用代码引用分析，默认false
     * 开启后会分析所有模块的代码引用关系，生成依赖图和未使用类报告
     * 作者：Redamancy  时间：2025-05-23
     */
    var enableCodeReferenceAnalysis: Boolean = false
    
    /**
     * 是否生成依赖图，默认true
     * 生成的依赖图为DOT格式，可使用Graphviz等工具可视化
     * 作者：Redamancy  时间：2025-05-23
     */
    var generateDependencyGraph: Boolean = true
    
    /**
     * 是否检测未使用的类，默认true
     * 开启后会生成未使用类报告，帮助清理冗余代码
     * 作者：Redamancy  时间：2025-05-23
     */
    var detectUnusedClasses: Boolean = true
    
    /**
     * 代码引用分析白名单
     * 支持：直接 List<String>，或指定文件路径（String/多个文件路径 List<String>）
     * 白名单中的类即使未被引用也不会被标记为未使用
     * 示例：codeReferenceWhiteList = listOf("com.example.ApiService", "com.example.util.*")
     * 作者：Redamancy  时间：2025-05-23
     */
    var codeReferenceWhiteList: Any? = null
    
    /**
     * 是否启用资源引用自动更新
     * 默认关闭，需要手动开启该功能
     * 作者：Redamancy  时间：2025-05-23
     */
    var enableResourceReferenceUpdate: Boolean = false
    
    /**
     * 资源引用更新白名单配置文件路径
     * 支持：直接 List<String>，或指定文件路径（String/多个文件路径 List<String>）
     * 白名单中的资源引用不会被自动更新
     * 作者：Redamancy  时间：2025-05-23
     */
    var resourceReferenceWhiteList: Any? = null
    
    /**
     * 资源引用更新是否为试运行模式（不实际修改文件）
     * 设置为true时，不会实际修改源文件，只会输出日志
     * 作者：Redamancy  时间：2025-05-23
     */
    var resourceReferenceDryRun: Boolean = false
    
    /**
     * 是否强制更新已有前缀的资源引用
     * 默认false，即如果资源引用已经有前缀（即使与预期不同），也不会更新
     * 作者：Redamancy  时间：2025-05-23
     */
    var forceUpdatePrefixedResources: Boolean = false
    
    /**
     * 是否启用资源隔离功能
     * 默认关闭，需要手动开启该功能
     * 开启后会自动检测所有子模块资源命名空间与前缀合规性，并自动重命名资源
     * 作者：Redamancy  时间：2025-05-23
     */
    var enableResourceIsolation: Boolean = false
    
    /**
     * 是否启用资源MD5计算
     * 默认关闭，需要手动开启该功能
     * 开启后会为资源文件名添加MD5值，防止资源冲突
     * 作者：Redamancy  时间：2025-05-23
     */
    var enableResourceMd5: Boolean = false
    
    /**
     * 验证配置有效性
     * @return 是否有效
     */
    fun validate(): Boolean {
        var isValid = true
        
        // 验证日志级别
        try {
            LogLevel.valueOf(logLevel.uppercase())
        } catch (e: Exception) {
            PluginLogger.warn("无效的日志级别: $logLevel，已自动设置为INFO")
            logLevel = "INFO"
            isValid = false
        }
        
        // 验证注册类名
        if (registerClassName.isBlank()) {
            PluginLogger.warn("注册类名不能为空，已自动设置为ModuleRegistry")
            registerClassName = "ModuleRegistry"
            isValid = false
        }
        
        // 验证生成包名
        if (generatedPackage.isBlank()) {
            PluginLogger.warn("生成包名不能为空，已自动设置为com.neil.plugin.autoregister")
            generatedPackage = "com.neil.plugin.autoregister"
            isValid = false
        }
        
        // 验证资源前缀模式
        if (resourcePrefixPattern?.isEmpty() == true) {
            resourcePrefixPattern = null
        }
        
        // 验证资源后缀模式
        if (resourceSuffixPattern?.isEmpty() == true) {
            resourceSuffixPattern = null
        }
        
        // 验证资源命名策略
        if (resourceNamingStrategy == ResourceNamingStrategy.PREFIX && resourcePrefixPattern == null) {
            PluginLogger.warn("警告: 已设置前缀命名策略，但未设置资源前缀模式(resourcePrefixPattern)，将使用模块包名的最后一部分作为前缀")
        }
        
        if (resourceNamingStrategy == ResourceNamingStrategy.SUFFIX && resourceSuffixPattern == null) {
            PluginLogger.warn("警告: 已设置后缀命名策略，但未设置资源后缀模式(resourceSuffixPattern)，将使用'_模块名'作为默认后缀")
        }
        
        // 验证是否启用代码引用分析
        if (generateDependencyGraph && !enableCodeReferenceAnalysis) {
            generateDependencyGraph = false
        }
        
        // 验证是否检测未使用的类
        if (detectUnusedClasses && !enableCodeReferenceAnalysis) {
            detectUnusedClasses = false
        }
        
        // 验证是否启用资源引用自动更新
        if (enableResourceReferenceUpdate && resourcePrefixPattern == null) {
            PluginLogger.warn("警告: 已启用资源引用自动更新，但未设置资源前缀模式(resourcePrefixPattern)，这可能导致无法正确更新资源引用")
        }
        
        // 验证是否启用资源隔离
        if (enableResourceIsolation && resourcePrefixPattern == null) {
            PluginLogger.warn("警告: 已启用资源隔离，但未设置资源前缀模式(resourcePrefixPattern)，将使用模块包名的最后一部分作为前缀")
        }
        
        return isValid
    }
    
    /**
     * 将白名单配置从字符串转换为文件路径
     * @param project 当前工程
     * @param configValue 字符串配置值
     * @return 文件对象
     */
    fun resolveWhiteListFile(project: Project, configValue: String): File {
        return if (File(configValue).isAbsolute) {
            File(configValue)
        } else {
            project.file(configValue)
        }
    }
} 