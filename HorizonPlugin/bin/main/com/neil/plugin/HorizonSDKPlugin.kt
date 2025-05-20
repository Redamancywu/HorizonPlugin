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

/**
 * 插件入口，自动注册Horizon SDK相关功能
 * 作者：Redamancy  时间：2025-05-19
 */
class HorizonSDKPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 注册DSL扩展
        val extension = project.extensions.create("horizon", HorizonExtension::class.java)
        // 设置日志等级
        PluginLogger.logLevel = try {
            LogLevel.valueOf(extension.logLevel.uppercase())
        } catch (e: Exception) {
            LogLevel.INFO
        }
        PluginLogger.info("HorizonSDKPlugin 已应用，欢迎使用！")
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

        // 自动注入SDK混淆规则
        project.afterEvaluate {
            val androidExt = project.extensions.findByName("android")
            if (androidExt != null) {
                PluginLogger.info("自动注入SDK混淆规则")
                // 1. 拷贝插件内置 proguard 文件到主工程 build 目录
                val sdkProguardDir = project.file("${'$'}{project.buildDir}/proguard")
                val sdkProguardFile = project.file("${'$'}{sdkProguardDir}/proguard-rules.sdk.pro")
                sdkProguardDir.mkdirs()
                val pluginProFile = project.rootProject.file("HorizonPlugin/proguard/proguard-rules.sdk.pro")
                if (pluginProFile.exists()) {
                    sdkProguardFile.writeText(pluginProFile.readText())
                }
                // 2. 追加 DSL 配置的自定义规则
                if (extension.proguardRules.isNotEmpty()) {
                    sdkProguardFile.appendText("\n" + extension.proguardRules.joinToString("\n"))
                }
                // 3. 注入到所有 buildType
                (androidExt as? com.android.build.gradle.BaseExtension)?.buildTypes?.all {
                    it.proguardFile(sdkProguardFile)
                }
            }
        }

        // 智能结构化合并所有模块 Manifest 片段
        project.afterEvaluate {
            val androidExt = project.extensions.findByName("android")
            if (androidExt != null) {
                PluginLogger.info("智能结构化合并所有模块 Manifest 片段")
                val mainManifest = project.file("src/main/AndroidManifest.xml")
                if (!mainManifest.exists()) return@afterEvaluate
                val factory = DocumentBuilderFactory.newInstance()
                val mainDoc = factory.newDocumentBuilder().parse(mainManifest)
                val appNode = mainDoc.getElementsByTagName("application").item(0) as? Element ?: return@afterEvaluate
                val allModules = project.rootProject.subprojects.filter { it != project }.sortedBy { it.name }
                allModules.forEach { module ->
                    val fragment = module.file("src/main/manifest/fragment_${'$'}{module.name}.xml")
                    if (fragment.exists()) {
                        val fragDoc = factory.newDocumentBuilder().parse(fragment)
                        val fragAppNode = fragDoc.getElementsByTagName("application").item(0) as? Element ?: return@forEach
                        val fragChildren = fragAppNode.childNodes
                        for (i in 0 until fragChildren.length) {
                            val node = fragChildren.item(i)
                            if (node is Element) {
                                val nameAttr = node.getAttribute("android:name")
                                val exists = (0 until appNode.childNodes.length).any { j ->
                                    val child = appNode.childNodes.item(j)
                                    child is Element && child.tagName == node.tagName && child.getAttribute("android:name") == nameAttr
                                }
                                if (exists) {
                                    PluginLogger.warn("Manifest合并冲突: ${node.tagName} $nameAttr 已存在，已跳过")
                                } else {
                                    appNode.appendChild(mainDoc.importNode(node, true))
                                    PluginLogger.info("已合并模块 ${module.name} 的节点: ${node.tagName} $nameAttr")
                                }
                            }
                        }
                    }
                }
                // 保存合并后的mainManifest
                val transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer()
                val source = javax.xml.transform.dom.DOMSource(mainDoc)
                val result = javax.xml.transform.stream.StreamResult(mainManifest)
                transformer.transform(source, result)
                PluginLogger.info("Manifest结构化合并完成")
            }
        }

        // 自动检测所有子module资源命名空间与前缀合规性，并自动重命名和前缀处理
        project.afterEvaluate {
            val enableResourceMd5 = (project.findProperty("enableResourceMd5") as? String)?.toBoolean() ?: false
            val allModules = project.rootProject.subprojects.filter { it != project }
            allModules.forEach { module ->
                val buildGradle = module.file("build.gradle")
                val buildGradleKts = module.file("build.gradle.kts")
                var namespace: String? = null
                if (buildGradle.exists()) {
                    val text = buildGradle.readText()
                    namespace = Regex("namespace ['\"]([\w.]+)['\"]").find(text)?.groupValues?.getOrNull(1)
                } else if (buildGradleKts.exists()) {
                    val text = buildGradleKts.readText()
                    namespace = Regex("namespace ?= ?['\"]([\w.]+)['\"]").find(text)?.groupValues?.getOrNull(1)
                }
                if (namespace != null) {
                    val prefix = namespace.split('.').last() + "_"
                    val resDir = module.file("src/main/res")
                    if (resDir.exists() && resDir.isDirectory) {
                        ResourceIsolationHelper.processResDir(resDir, prefix, enableResourceMd5)
                    }
                } else {
                    PluginLogger.warn("资源隔离检测：未检测到模块${module.name}的namespace，建议在build.gradle中声明namespace！")
                }
            }
        }
        // TODO: 后续在此注册各类扩展与功能
    }
}