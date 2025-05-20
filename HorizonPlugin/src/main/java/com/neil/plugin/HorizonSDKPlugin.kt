// 作者：Redamancy  时间：2025-05-19
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

/**
 * 插件入口，自动注册Horizon SDK相关功能
 * 作者：Redamancy  时间：2025-05-23
 */
class HorizonSDKPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 注册DSL扩展
        val extension = project.extensions.create("horizon", HorizonExtension::class.java)
        
        // 日志等级设置移动到 afterEvaluate，确保能读取到 DSL 配置
        project.afterEvaluate {
            // 验证配置有效性
            extension.validate()
            
            // 设置日志级别
            PluginLogger.logLevel = try {
                LogLevel.valueOf(extension.logLevel.uppercase())
            } catch (e: Exception) {
                LogLevel.INFO
            }
            
            // 设置日志文件
            PluginLogger.createDefaultLogFile(project)
            
            PluginLogger.info("HorizonSDKPlugin 已应用，欢迎使用！")
            PluginLogger.info("插件版本: 1.0.7")
            PluginLogger.debug("配置信息: enableAutoRegister=${extension.enableAutoRegister}, " +
                               "enableCodeReferenceAnalysis=${extension.enableCodeReferenceAnalysis}")
        }
        
        // 配置自动注册
        setupAutoRegister(project, extension)
        
        // 自动注入SDK混淆规则
        project.afterEvaluate {
            val androidExt = project.extensions.findByName("android")
            if (androidExt != null) {
                ProguardInjector.injectProguardRules(project, extension)
            }
        }

        // 智能结构化合并所有模块 Manifest 片段
        project.afterEvaluate {
            val androidExt = project.extensions.findByName("android")
            if (androidExt != null) {
                ManifestMerger.mergeModuleManifests(project)
            }
        }

        // 自动检测所有子module资源命名空间与前缀合规性，并自动重命名和前缀处理
        setupResourceIsolation(project, extension)
        
        // 代码引用分析功能
        setupCodeReferenceAnalysis(project, extension)
        
        // 资源引用自动更新功能
        setupResourceReferenceUpdate(project, extension)
    }
    
    /**
     * 配置自动注册功能
     */
    private fun setupAutoRegister(project: Project, extension: HorizonExtension) {
        if (extension.enableAutoRegister) {
            // 同步DSL配置到project properties，供KSP参数读取
            project.extensions.extraProperties["horizon.modulePackages"] = extension.modulePackages.joinToString(",")
            project.extensions.extraProperties["horizon.excludePackages"] = extension.excludePackages.joinToString(",")
            project.extensions.extraProperties["horizon.registerClassName"] = extension.registerClassName
            project.extensions.extraProperties["horizon.outputDir"] = extension.outputDir
            project.extensions.extraProperties["horizon.generatedPackage"] = extension.generatedPackage

            // 为所有KSP任务动态配置arg参数，实现DSL配置与KSP参数联动
            project.afterEvaluate {
                project.tasks.matching { it.name.startsWith("ksp") }.configureEach { task ->
                    task.extensions.extraProperties["kspArgs"] = mapOf(
                        "horizon.modulePackages" to extension.modulePackages.joinToString(","),
                        "horizon.excludePackages" to extension.excludePackages.joinToString(","),
                        "horizon.registerClassName" to extension.registerClassName,
                        "horizon.outputDir" to extension.outputDir,
                        "horizon.generatedPackage" to extension.generatedPackage
                    )
                }
            }
        }
    }
    
    /**
     * 配置资源隔离功能
     */
    private fun setupResourceIsolation(project: Project, extension: HorizonExtension) {
        project.afterEvaluate {
            // 只有在明确开启资源隔离功能时才执行
            if (extension.enableResourceIsolation) {
                PluginLogger.info("开始执行资源隔离功能")
                
                // 使用扩展配置中的MD5设置，不再从project属性获取
                val enableResourceMd5 = extension.enableResourceMd5
                val md5StatusText = if (enableResourceMd5) "已启用" else "未启用"
                PluginLogger.info("资源MD5计算：$md5StatusText")
                
                val allModules = project.rootProject.subprojects.filter { it != project }
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
                        val prefix = if (prefixPattern != null) {
                            prefixPattern
                                .replace("{module}", module.name)
                                .replace("{flavor}", project.findProperty("flavorName") as? String ?: "")
                        } else {
                            namespace.split('.').last() + "_"
                        }
                        
                        val resDir = module.file("src/main/res")
                        if (resDir.exists() && resDir.isDirectory) {
                            // 读取白名单配置
                            val whiteList = ResourceIsolationHelper.loadWhiteListFromConfig(
                                extension.resourceWhiteList, module.projectDir
                            )
                            if (whiteList.isNotEmpty()) {
                                PluginLogger.info("模块 ${module.name} 使用资源白名单：${whiteList.size}条规则")
                            }
                            
                            // 处理资源目录，进行资源隔离和重命名
                            val processedCount = ResourceIsolationHelper.processResDir(
                                resDir, 
                                prefix, 
                                enableResourceMd5, 
                                moduleName = module.name,
                                whiteList = whiteList
                            )
                            
                            if (processedCount > 0) {
                                PluginLogger.info("模块 ${module.name} 资源隔离处理完成，共处理 $processedCount 个资源文件")
                            } else {
                                PluginLogger.info("模块 ${module.name} 资源已符合隔离规范，无需处理")
                            }
                        }
                    } else {
                        PluginLogger.warn("资源隔离检测：未检测到模块${module.name}的namespace，建议在build.gradle中声明namespace！")
                    }
                }
                
                PluginLogger.info("资源隔离功能执行完成")
            }
        }
    }
    
    /**
     * 配置代码引用分析功能
     */
    private fun setupCodeReferenceAnalysis(project: Project, extension: HorizonExtension) {
        project.afterEvaluate {
            if (extension.enableCodeReferenceAnalysis) {
                PluginLogger.info("开始代码引用分析")
                val allModules = project.rootProject.subprojects.filter { it != project }
                
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
                PluginLogger.info("代码引用分析完成")
            }
        }
    }
    
    /**
     * 配置资源引用自动更新功能
     */
    private fun setupResourceReferenceUpdate(project: Project, extension: HorizonExtension) {
        project.afterEvaluate {
            if (extension.enableResourceReferenceUpdate) {
                PluginLogger.info("开始资源引用自动更新")
                
                // 加载白名单
                val whiteList = ResourceReferenceUpdater.loadWhiteList(project, extension.resourceReferenceWhiteList)
                
                val allModules = project.rootProject.subprojects.filter { it != project }
                val modulePrefixMap = mutableMapOf<String, String>()
                
                // 收集所有模块的资源前缀
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
                        val prefix = if (prefixPattern != null) {
                            prefixPattern
                                .replace("{module}", module.name)
                                .replace("{flavor}", project.findProperty("flavorName") as? String ?: "")
                        } else {
                            namespace.split('.').last() + "_"
                        }
                        
                        modulePrefixMap[module.name] = prefix
                    }
                }
                
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
                
                val dryRunMode = extension.resourceReferenceDryRun
                val dryRunMsg = if (dryRunMode) "(试运行模式)" else ""
                PluginLogger.info("资源引用自动更新$dryRunMsg - 当前项目前缀：$appPrefix")
                
                val updatedFiles = ResourceReferenceUpdater.updateResourceReferences(
                    project,
                    project.name,
                    appPrefix,
                    dryRunMode,
                    whiteList,
                    extension.forceUpdatePrefixedResources
                )
                
                // 更新所有模块的资源引用
                PluginLogger.info("资源引用自动更新$dryRunMsg - 将更新 ${modulePrefixMap.size} 个子模块")
                
                val totalUpdated = updatedFiles + ResourceReferenceUpdater.updateAllModulesResourceReferences(
                    project,
                    modulePrefixMap,
                    dryRunMode,
                    whiteList,
                    extension.forceUpdatePrefixedResources
                )
                
                if (dryRunMode) {
                    PluginLogger.info("资源引用自动更新(试运行模式)完成，共发现 $totalUpdated 个需要更新的文件")
                    PluginLogger.info("试运行模式不会实际修改文件，如需应用更改，请设置 resourceReferenceDryRun = false")
                } else {
                    PluginLogger.info("资源引用自动更新完成，共更新 $totalUpdated 个文件")
                }
            }
        }
    }
}