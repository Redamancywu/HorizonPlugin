// 作者：Redamancy  时间：2025-05-23
// Horizon插件DSL扩展
package com.neil.plugin

import com.neil.plugin.logger.LogLevel
import com.neil.plugin.logger.PluginLogger
import com.neil.plugin.resource.ResourceNamingStrategy
import com.neil.plugin.resource.SpecialResourceProcessor
import org.gradle.api.Project
import org.gradle.api.Action
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

    /** 日志级别，默认INFO，可选DEBUG/INFO/WARN/ERROR */
    var logLevel: String = "INFO"

    /** 额外参数，便于后续扩展 */
    var extraArgs: MutableMap<String, Any> = mutableMapOf()

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
     * 是否启用资源回退功能
     * 默认为false，设置为true时会回退之前的资源重命名和MD5修改
     * 注意：enableResourceRollback=true时，enableResourceIsolation和enableResourceMd5必须为false
     * 回退功能会恢复之前通过ResourceIsolationHelper执行的资源重命名和MD5修改的原始状态
     * 作者：Redamancy  时间：2025-06-02
     */
    var enableResourceRollback: Boolean = false
    
    /**
     * 资源回退后是否同时删除映射文件
     * 默认为true
     * 当设置为false时，保留映射文件以便后续参考
     * 作者：Redamancy  时间：2025-06-02
     */
    var deleteResourceMappingAfterRollback: Boolean = true
    
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
     * 启用后会自动检测并更新代码中的资源引用，包括:
     * - Java/Kotlin源码中的R.xxx.yyy引用
     * - findViewById(R.id.xxx)等方法调用中的资源引用
     * - setContentView(R.layout.xxx)布局引用
     * - getIdentifier("name", "type", ...)引用
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
     * 开启后会为资源内容计算MD5值，防止资源冲突
     * 作者：Redamancy  时间：2025-05-23
     */
    var enableResourceMd5: Boolean = false
    
    /**
     * 是否将MD5值加入到文件名中
     * 默认为true，需要同时设置enableResourceMd5为true才有效
     * 设置为false时，只修改资源内容的MD5值，不修改文件名
     * 作者：Redamancy  时间：2025-05-23
     */
    var includeMd5InFileName: Boolean = true
    
    /**
     * 是否强制重新处理资源，即使已经处理过
     * 默认为false，避免每次构建都重命名资源
     */
    var forceReprocessResources: Boolean = false
    
    /**
     * 是否启用并行处理资源
     * 默认为true，利用多线程并行处理资源文件以提高性能
     * 作者：Redamancy  时间：2025-07-06
     */
    var enableParallelProcessing: Boolean = true
    
    /**
     * 处理线程数
     * 默认为系统可用处理器数量
     * 作者：Redamancy  时间：2025-07-06
     */
    var processorThreadCount: Int = Runtime.getRuntime().availableProcessors()
    
    /**
     * 是否生成资源处理报告
     * 默认为false，设置为true时将生成详细的资源处理报告
     * 作者：Redamancy  时间：2025-07-06
     */
    var generateResourceReport: Boolean = false
    
    /**
     * 资源处理报告格式
     * 可选值: json, html
     * 默认为json
     * 作者：Redamancy  时间：2025-07-06
     */
    var resourceReportFormat: String = "json"
    
    /**
     * 是否处理特定资源类型
     * 默认为false，设置为true时将对不同类型的资源提供特殊处理
     * 作者：Redamancy  时间：2025-07-06
     */
    var processSpecialResources: Boolean = false
    
    /**
     * 特定资源处理配置
     * 用于配置针对不同资源类型的处理规则
     */
    private val specialResourceConfigs = mutableMapOf<String, SpecialResourceProcessor.ProcessorConfig>()
    
    /**
     * 配置特定资源类型处理器
     * 
     * @param resourceType 资源类型
     * @param action 配置操作
     */
    fun configureResourceType(resourceType: String, action: Action<SpecialResourceTypeConfig>) {
        val config = SpecialResourceTypeConfig()
        action.execute(config)
        specialResourceConfigs[resourceType] = SpecialResourceProcessor.ProcessorConfig(
            enabled = config.enabled,
            optimizeSize = config.optimizeSize,
            applyCompression = config.applyCompression,
            qualityLevel = config.qualityLevel
        )
    }
    
    /**
     * 获取特定资源类型处理器配置
     * 
     * @return 特定资源类型处理器配置
     */
    fun getSpecialResourceConfigs(): Map<String, SpecialResourceProcessor.ProcessorConfig> {
        return specialResourceConfigs.toMap()
    }
    
    /**
     * 特定资源类型配置类
     */
    class SpecialResourceTypeConfig {
        /** 是否启用该处理器 */
        var enabled: Boolean = true
        
        /** 是否优化资源大小 */
        var optimizeSize: Boolean = true
        
        /** 是否应用压缩 */
        var applyCompression: Boolean = false
        
        /** 质量级别 (0-100) */
        var qualityLevel: Int = 80
    }
    
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
        
        // 验证资源隔离和回退功能互斥性
        if (enableResourceIsolation && enableResourceRollback) {
            PluginLogger.warn("资源隔离(enableResourceIsolation)和资源回退(enableResourceRollback)功能不能同时启用，已自动禁用资源隔离功能")
            enableResourceIsolation = false
            isValid = false
        }
        
        // 验证资源回退功能与资源MD5修改功能互斥性
        if (enableResourceRollback && enableResourceMd5) {
            PluginLogger.warn("资源回退(enableResourceRollback)和资源MD5修改(enableResourceMd5)功能不能同时启用，已自动禁用资源MD5修改功能")
            enableResourceMd5 = false
            isValid = false
        }
        
        // 验证资源命名策略
        try {
            if (resourceNamingStrategy !is ResourceNamingStrategy) {
                resourceNamingStrategy = ResourceNamingStrategy.fromString(resourceNamingStrategy.toString())
            }
        } catch (e: Exception) {
            PluginLogger.warn("无效的资源命名策略: $resourceNamingStrategy，已自动设置为PREFIX")
            resourceNamingStrategy = ResourceNamingStrategy.PREFIX
            isValid = false
        }
        
        // 验证处理线程数
        if (processorThreadCount < 1) {
            PluginLogger.warn("处理线程数必须大于0，已自动设置为CPU核心数: ${Runtime.getRuntime().availableProcessors()}")
            processorThreadCount = Runtime.getRuntime().availableProcessors()
            isValid = false
        }
        
        // 验证资源报告格式
        if (resourceReportFormat !in listOf("json", "html")) {
            PluginLogger.warn("无效的资源报告格式: $resourceReportFormat，已自动设置为json")
            resourceReportFormat = "json"
            isValid = false
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