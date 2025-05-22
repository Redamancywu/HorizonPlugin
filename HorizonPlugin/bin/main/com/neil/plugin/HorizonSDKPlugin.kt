// 作者：Redamancy  时间：2025-07-28
// Horizon SDK Gradle 插件入口
package com.neil.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import com.neil.plugin.logger.PluginLogger
import com.neil.plugin.logger.LogLevel
import org.w3c.dom.*
import javax.xml.parsers.DocumentBuilderFactory
import com.neil.plugin.resource.ResourceIsolationHelper
import com.neil.plugin.reference.CodeReferenceHelper
import com.neil.plugin.manifest.ManifestMerger
import com.neil.plugin.proguard.ProguardInjector
import com.neil.plugin.resource.ResourceReferenceUpdater
import java.io.File
import com.neil.plugin.resource.ResourceNamingStrategy
import com.neil.plugin.resource.ResourceRollbackHelper
import kotlin.system.measureTimeMillis

/**
 * 插件入口，自动注册Horizon SDK相关功能
 * 作者：Redamancy  时间：2025-07-28
 */
class HorizonSDKPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 记录插件执行时间
        val totalTime = measureTimeMillis {
            // 注册DSL扩展
            val extension = project.extensions.create("horizon", HorizonExtension::class.java)
            
            // 日志等级设置移动到 afterEvaluate，确保能读取到 DSL 配置
            project.afterEvaluate {
                // 验证配置有效性
                extension.validate()
                
                // 额外验证资源回退功能配置
                val rollbackConfigValid = ResourceRollbackHelper.validateRollbackConfig(
                    extension.enableResourceRollback,
                    extension.enableResourceIsolation,
                    extension.enableResourceMd5
                )
                
                if (!rollbackConfigValid) {
                    PluginLogger.warn("资源回退功能配置无效，已自动调整配置")
                    if (extension.enableResourceRollback) {
                        // 如果启用了回退功能，则禁用重命名和MD5功能
                        extension.enableResourceIsolation = false
                        extension.enableResourceMd5 = false
                    }
                }
                
                // 设置日志级别
                PluginLogger.logLevel = try {
                    LogLevel.valueOf(extension.logLevel.uppercase())
                } catch (e: Exception) {
                    PluginLogger.warn("无效的日志级别: ${extension.logLevel}，已使用默认值 INFO")
                    LogLevel.INFO
                }
                
                // 设置日志文件
                PluginLogger.createDefaultLogFile(project)
                
                PluginLogger.info("HorizonSDKPlugin 已应用，欢迎使用！")
                PluginLogger.info("插件版本: 1.0.9")
                PluginLogger.debug("配置信息: enableAutoRegister=${extension.enableAutoRegister}, " +
                                "enableCodeReferenceAnalysis=${extension.enableCodeReferenceAnalysis}")
            }
            
            // 配置自动注册
            setupAutoRegister(project, extension)
            
            // 自动注入SDK混淆规则
            setupProguardRules(project, extension)
    
            // 智能结构化合并所有模块 Manifest 片段
            setupManifestMerge(project, extension)
    
            // 资源处理逻辑 - 先检查是否需要回退
            project.afterEvaluate {
                if (extension.enableResourceRollback) {
                    handleResourceRollback(project, extension)
                } else {
                    // 自动检测所有子module资源命名空间与前缀合规性，并自动重命名和前缀处理
                    setupResourceIsolation(project, extension)
                }
            }
            
            // 代码引用分析功能
            setupCodeReferenceAnalysis(project, extension)
            
            // 资源引用自动更新功能
            setupResourceReferenceUpdate(project, extension)
        }
        
        PluginLogger.info("HorizonSDKPlugin 执行完成，总耗时: ${totalTime}ms")
    }
    
    /**
     * 配置自动注册功能
     */
    private fun setupAutoRegister(project: Project, extension: HorizonExtension) {
        if (extension.enableAutoRegister) {
            // 同步DSL配置到project properties，供HorizonServiceLoader和其他机制使用
            project.extensions.extraProperties["horizon.modulePackages"] = extension.modulePackages.joinToString(",")
            project.extensions.extraProperties["horizon.excludePackages"] = extension.excludePackages.joinToString(",")

            // 为所有KSP任务动态配置arg参数，传递模块包名和排除包名
            project.afterEvaluate {
                project.tasks.matching { it.name.startsWith("ksp") }.configureEach { task ->
                    task.extensions.extraProperties["kspArgs"] = mapOf(
                        "horizon.modulePackages" to extension.modulePackages.joinToString(","),
                        "horizon.excludePackages" to extension.excludePackages.joinToString(",")
                    )
                }
            }
            
            PluginLogger.info("自动注册功能已配置，将使用HorizonServiceLoader机制")
        }
    }
    
    /**
     * 配置混淆规则注入
     */
    private fun setupProguardRules(project: Project, extension: HorizonExtension) {
        project.afterEvaluate {
            val androidExt = project.extensions.findByName("android")
            if (androidExt != null) {
                try {
                    ProguardInjector.injectProguardRules(project, extension)
                } catch (e: Exception) {
                    PluginLogger.error("注入混淆规则时发生错误: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                PluginLogger.warn("未找到Android扩展，跳过混淆规则注入")
            }
        }
    }
    
    /**
     * 配置Manifest合并
     */
    private fun setupManifestMerge(project: Project, extension: HorizonExtension) {
        project.afterEvaluate {
            val androidExt = project.extensions.findByName("android")
            if (androidExt != null) {
                try {
                    ManifestMerger.mergeModuleManifests(project)
                } catch (e: Exception) {
                    PluginLogger.error("合并Manifest时发生错误: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                PluginLogger.warn("未找到Android扩展，跳过Manifest合并")
            }
        }
    }
    
    /**
     * 处理资源回退逻辑
     */
    private fun handleResourceRollback(project: Project, extension: HorizonExtension) {
        PluginLogger.info("开始执行资源回退功能")
        
        // 确保资源隔离和MD5修改功能不会同时启用
        if (extension.enableResourceIsolation || extension.enableResourceMd5) {
            PluginLogger.warn("资源回退功能启用时，资源隔离和MD5修改功能必须关闭，已自动禁用")
            // 在回退功能运行时，确保这些功能是关闭的
            extension.enableResourceIsolation = false
            extension.enableResourceMd5 = false
        }
        
        // 执行资源回退
        val allModules = project.rootProject.subprojects.filter { it != project }
        var totalRollbackCount = 0
        var successModuleCount = 0
        var skippedModuleCount = 0
        var errorModuleCount = 0
        
        allModules.forEach { module ->
            val resDir = module.file("src/main/res")
            if (resDir.exists() && resDir.isDirectory) {
                try {
                    // 检查是否需要强制回退
                    val forceRollback = extension.extraArgs["force_rollback"] as? Boolean ?: false
                    
                    if (ResourceRollbackHelper.canRollback(resDir, forceRollback)) {
                        val rollbackCount = ResourceRollbackHelper.rollbackResources(
                            resDir = resDir,
                            alsoDeleteMappingFile = extension.deleteResourceMappingAfterRollback,
                            mapFilePath = extension.resourceMapOutputPath,
                            forceRollback = forceRollback
                        )
                        totalRollbackCount += rollbackCount
                        
                        if (rollbackCount > 0) {
                            PluginLogger.info("模块 ${module.name} 资源回退完成，共回退 $rollbackCount 个资源文件")
                            successModuleCount++
                        } else {
                            PluginLogger.info("模块 ${module.name} 资源回退完成，但没有找到需要回退的资源")
                            skippedModuleCount++
                        }
                    } else {
                        PluginLogger.info("模块 ${module.name} 不需要执行资源回退操作")
                        skippedModuleCount++
                    }
                } catch (e: Exception) {
                    PluginLogger.error("模块 ${module.name} 资源回退失败: ${e.message}")
                    e.printStackTrace()
                    errorModuleCount++
                }
            }
        }
        
        PluginLogger.info("资源回退功能执行完成，共回退 $totalRollbackCount 个资源文件，" +
                        "$successModuleCount 个模块成功，$skippedModuleCount 个模块跳过，$errorModuleCount 个模块失败")
    }
    
    /**
     * 配置资源隔离功能
     */
    private fun setupResourceIsolation(project: Project, extension: HorizonExtension) {
        // 只有在明确开启资源隔离功能时才执行
        if (extension.enableResourceIsolation) {
            PluginLogger.info("开始执行资源隔离功能")
            
            val enableResourceMd5 = extension.enableResourceMd5
            PluginLogger.info("资源MD5计算：" + (if (enableResourceMd5) "已启用" else "已禁用"))
            
            val includeMd5InFileName = extension.includeMd5InFileName
            PluginLogger.info("资源MD5添加到文件名：" + (if (enableResourceMd5 && includeMd5InFileName) "是" else "否"))
            
            val namingStrategy = extension.resourceNamingStrategy
            PluginLogger.info("资源命名策略：" + (if (namingStrategy == ResourceNamingStrategy.PREFIX) "前缀模式" else "后缀模式"))
            
            // 记录是否强制重新处理资源
            val forceReprocessText = if (extension.forceReprocessResources) "是" else "否"
            PluginLogger.info("强制重新处理资源：$forceReprocessText")
            
            val allModules = project.rootProject.subprojects.filter { it != project }
            var successCount = 0
            var skippedCount = 0
            var errorCount = 0
            
            allModules.forEach { module ->
                try {
                    val buildGradle = module.file("build.gradle")
                    val buildGradleKts = module.file("build.gradle.kts")
                    var namespace: String? = null
                    
                    // 尝试从build.gradle获取namespace
                    if (buildGradle.exists()) {
                        val text = buildGradle.readText()
                        namespace = Regex("""namespace ['"]([\w.]+)['"]""").find(text)?.groupValues?.getOrNull(1)
                    } 
                    // 如果build.gradle不存在或未找到namespace，则尝试从build.gradle.kts获取
                    else if (buildGradleKts.exists()) {
                        val text = buildGradleKts.readText()
                        namespace = Regex("""namespace ?= ?['"]([\w.]+)['"]""").find(text)?.groupValues?.getOrNull(1)
                    }
                    
                    if (namespace != null) {
                        // 处理资源前缀/后缀模式
                        val namingStrategy = extension.resourceNamingStrategy
                        val resDir = module.file("src/main/res")
                        
                        if (resDir.exists() && resDir.isDirectory) {
                            val isolationResult = ResourceIsolationHelper.processResDir(
                                resDir = resDir, 
                                prefix = namespace,
                                suffix = "",
                                enableMd5 = extension.enableResourceMd5,
                                moduleName = module.name,
                                flavorName = project.findProperty("flavorName") as? String ?: "",
                                resourcePrefixPattern = extension.resourcePrefixPattern,
                                resourceSuffixPattern = extension.resourceSuffixPattern,
                                namingStrategy = namingStrategy,
                                keepOriginalName = extension.keepOriginalName,
                                md5Length = extension.resourceMd5Length,
                                mapOutputPath = extension.resourceMapOutputPath,
                                whiteList = ResourceIsolationHelper.loadWhiteListFromConfig(
                                    extension.resourceWhiteList, module.projectDir
                                ),
                                forceReprocess = extension.forceReprocessResources ?: false,
                                includeMd5InFileName = extension.includeMd5InFileName
                            )
                            
                            // 检查处理结果
                            if (isolationResult > 0) {
                                PluginLogger.info("模块 ${module.name} 资源隔离完成，处理了 ${isolationResult} 个资源")
                                successCount++
                            } else if (isolationResult == 0) {
                                PluginLogger.info("模块 ${module.name} 资源隔离完成，没有需要处理的资源")
                                skippedCount++
                            } else {
                                PluginLogger.warn("模块 ${module.name} 资源隔离失败")
                                errorCount++
                            }
                        } else {
                            PluginLogger.info("模块 ${module.name} 没有res目录，跳过资源隔离")
                            skippedCount++
                        }
                    } else {
                        PluginLogger.warn("模块 ${module.name} 未找到namespace配置，跳过资源隔离")
                        skippedCount++
                    }
                } catch (e: Exception) {
                    PluginLogger.error("处理模块 ${module.name} 资源时发生异常: ${e.message}")
                    e.printStackTrace()
                    errorCount++
                }
            }
            
            PluginLogger.info("资源隔离功能执行完成：$successCount 个模块成功，$skippedCount 个模块跳过，$errorCount 个模块失败")
        }
    }
    
    /**
     * 配置代码引用分析功能
     */
    private fun setupCodeReferenceAnalysis(project: Project, extension: HorizonExtension) {
        project.afterEvaluate {
            // 如果启用了代码引用分析功能
            if (extension.enableCodeReferenceAnalysis) {
                try {
                    PluginLogger.info("开始执行代码引用分析")
                    
                    // 配置代码引用分析任务
                    val taskName = "analyzeCodeReferences"
                    project.tasks.register(taskName) { task ->
                        task.doLast {
                            // 收集所有子模块的源代码目录
                            val allModules = project.rootProject.subprojects.filter { it != project }
                            val sourceDirs = mutableListOf<File>()
                            
                            allModules.forEach { module ->
                                val srcDir = module.file("src/main/java")
                                if (srcDir.exists()) {
                                    sourceDirs.add(srcDir)
                                }
                                
                                val kotlinDir = module.file("src/main/kotlin")
                                if (kotlinDir.exists()) {
                                    sourceDirs.add(kotlinDir)
                                }
                            }
                            
                            // 执行代码引用分析
                            allModules.forEach { module ->
                                try {
                                    val srcDirs = listOf(
                                        module.file("src/main/java"),
                                        module.file("src/main/kotlin")
                                    ).filter { it.exists() && it.isDirectory }
                                    
                                    if (srcDirs.isNotEmpty()) {
                                        // 加载白名单配置
                                        val whiteList = CodeReferenceHelper.loadCodeReferenceWhiteList(
                                            extension.codeReferenceWhiteList,
                                            module.projectDir
                                        )
                                        
                                        srcDirs.forEach { srcDir ->
                                            val referenceMap = CodeReferenceHelper.analyzeCodeReferences(
                                                srcDir,
                                                module.name,
                                                whiteList
                                            )
                                            
                                            if (extension.generateDependencyGraph && referenceMap.isNotEmpty()) {
                                                val graphFile = File(
                                                    module.buildDir,
                                                    "reports/code-references/${module.name}_dependency_graph.dot"
                                                )
                                                graphFile.parentFile.mkdirs()
                                                CodeReferenceHelper.generateDependencyGraph(
                                                    referenceMap,
                                                    graphFile,
                                                    simplifyNames = true
                                                )
                                            }
                                            
                                            if (extension.detectUnusedClasses && referenceMap.isNotEmpty()) {
                                                val unusedClasses = CodeReferenceHelper.findUnusedClasses(
                                                    referenceMap,
                                                    whiteList
                                                )
                                                if (unusedClasses.isNotEmpty()) {
                                                    val unusedFile = File(
                                                        module.buildDir,
                                                        "reports/code-references/${module.name}_unused_classes.txt"
                                                    )
                                                    unusedFile.parentFile.mkdirs()
                                                    unusedFile.writeText(unusedClasses.joinToString("\n"))
                                                    PluginLogger.info("模块 ${module.name} 发现 ${unusedClasses.size} 个未使用的类，详见: ${unusedFile.absolutePath}")
                                                } else {
                                                    PluginLogger.info("模块 ${module.name} 未发现未使用的类，代码引用良好")
                                                }
                                            }
                                        }
                                    } else {
                                        PluginLogger.warn("模块 ${module.name} 未找到源代码目录，跳过代码引用分析")
                                    }
                                } catch (e: Exception) {
                                    PluginLogger.warn("模块 ${module.name} 代码引用分析失败: ${e.message}")
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    
                    // 自动执行分析任务
                    project.afterEvaluate {
                        try {
                            // 手动配置任务运行
                            val task = project.tasks.findByName(taskName)
                            if (task != null) {
                                project.gradle.buildFinished {
                                    PluginLogger.info("执行代码引用分析任务")
                                }
                            }
                        } catch (e: Exception) {
                            PluginLogger.error("自动执行任务失败: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    PluginLogger.error("代码引用分析失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * 配置资源引用自动更新功能
     */
    private fun setupResourceReferenceUpdate(project: Project, extension: HorizonExtension) {
        project.afterEvaluate {
            // 如果启用了资源引用自动更新
            if (extension.enableResourceReferenceUpdate) {
                try {
                    PluginLogger.info("开始执行资源引用自动更新")
                    
                    // 注册资源引用更新任务
                    val taskName = "updateResourceReferences"
                    project.tasks.register(taskName) { task ->
                        task.doLast {
                            // 收集所有子模块的源代码目录和资源目录
                            val allModules = project.rootProject.subprojects.filter { it != project }
                            val sourceAndResDirs = mutableListOf<Pair<File, File>>()
                            
                            allModules.forEach { module ->
                                val srcDir = module.file("src/main/java")
                                val kotlinDir = module.file("src/main/kotlin")
                                val resDir = module.file("src/main/res")
                                
                                if (srcDir.exists() && resDir.exists()) {
                                    sourceAndResDirs.add(Pair(srcDir, resDir))
                                }
                                
                                if (kotlinDir.exists() && resDir.exists()) {
                                    sourceAndResDirs.add(Pair(kotlinDir, resDir))
                                }
                            }
                            
                            // 更新资源引用
                            var updateCount = 0
                            
                            // 更新当前项目的资源引用
                            val appPrefix = if (extension.resourcePrefixPattern != null) {
                                extension.resourcePrefixPattern!!
                                    .replace("{module}", project.name)
                                    .replace("{flavor}", project.findProperty("flavorName") as? String ?: "")
                            } else {
                                val namespace = project.findProperty("android.namespace") as? String
                                if (namespace != null) {
                                    namespace.split('.').last() + "_"
                                } else {
                                    project.name + "_"
                                }
                            }
                            
                            updateCount += ResourceReferenceUpdater.updateResourceReferences(
                                project,
                                project.name,
                                appPrefix,
                                extension.resourceReferenceDryRun,
                                loadWhiteList(extension.resourceReferenceWhiteList),
                                extension.forceUpdatePrefixedResources
                            )
                            
                            // 更新每个模块的资源引用
                            allModules.forEach { module ->
                                val buildGradle = module.file("build.gradle")
                                val buildGradleKts = module.file("build.gradle.kts")
                                var namespace: String? = null
                                
                                if (buildGradle.exists()) {
                                    val text = buildGradle.readText()
                                    namespace = Regex("""namespace ['"]([\w.]+)['"]""").find(text)?.groupValues?.getOrNull(1)
                                } else if (buildGradleKts.exists()) {
                                    val text = buildGradleKts.readText()
                                    namespace = Regex("""namespace ?= ?['"]([\w.]+)['"]""").find(text)?.groupValues?.getOrNull(1)
                                }
                                
                                if (namespace != null) {
                                    // 处理资源前缀模式
                                    val prefixPattern = extension.resourcePrefixPattern
                                    val modulePrefix = if (prefixPattern != null) {
                                        prefixPattern
                                            .replace("{module}", module.name)
                                            .replace("{flavor}", project.findProperty("flavorName") as? String ?: "")
                                    } else {
                                        namespace.split('.').last() + "_"
                                    }
                                    
                                    updateCount += ResourceReferenceUpdater.updateResourceReferences(
                                        project,
                                        module.name,
                                        modulePrefix,
                                        extension.resourceReferenceDryRun,
                                        loadWhiteList(extension.resourceReferenceWhiteList),
                                        extension.forceUpdatePrefixedResources
                                    )
                                }
                            }
                            
                            PluginLogger.info("资源引用更新完成，共更新 $updateCount 处引用")
                        }
                    }
                    
                    // 自动执行更新任务
                    project.afterEvaluate {
                        try {
                            // 手动配置任务运行
                            val task = project.tasks.findByName(taskName)
                            if (task != null) {
                                project.gradle.buildFinished {
                                    PluginLogger.info("执行资源引用更新任务")
                                }
                            }
                        } catch (e: Exception) {
                            PluginLogger.error("自动执行任务失败: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    PluginLogger.error("资源引用自动更新失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 从配置加载白名单
     */
    private fun loadWhiteList(whiteListConfig: Any?): Set<String> {
        if (whiteListConfig == null) return emptySet()
        
        val whiteList = mutableSetOf<String>()
        
        when (whiteListConfig) {
            is List<*> -> {
                whiteListConfig.filterIsInstance<String>().forEach { 
                    if (it.contains(":")) {
                        whiteList.add(it) 
                    } else {
                        whiteList.add("*:$it")
                    }
                }
            }
            is String -> {
                if (whiteListConfig.contains(":")) {
                    whiteList.add(whiteListConfig)
                } else {
                    whiteList.add("*:$whiteListConfig")
                }
            }
        }
        
        return whiteList
    }
}