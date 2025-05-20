// 作者：Redamancy  时间：2025-05-23
// 混淆规则自动注入工具
package com.neil.plugin.proguard

import com.neil.plugin.logger.PluginLogger
import com.neil.plugin.HorizonExtension
import org.gradle.api.Project
import java.io.File

/**
 * 混淆规则自动注入工具
 * 支持：
 * 1. 自动注入内置混淆规则
 * 2. 追加DSL配置的自定义规则
 * 3. 注入到所有buildType
 * 作者：Redamancy  时间：2025-05-23
 */
object ProguardInjector {
    
    /**
     * 注入混淆规则到项目
     * @param project 主工程
     * @param extension 插件DSL扩展
     */
    fun injectProguardRules(project: Project, extension: HorizonExtension) {
        PluginLogger.info("自动注入SDK混淆规则")
        
        try {
            // 1. 拷贝插件内置 proguard 文件到主工程 build 目录
            val sdkProguardDir = project.file("${project.buildDir}/proguard")
            val sdkProguardFile = project.file("${sdkProguardDir}/proguard-rules.sdk.pro")
            sdkProguardDir.mkdirs()
            
            val pluginProFile = project.rootProject.file("HorizonPlugin/proguard/proguard-rules.sdk.pro")
            if (pluginProFile.exists()) {
                sdkProguardFile.writeText(pluginProFile.readText())
                PluginLogger.debug("拷贝混淆规则: ${pluginProFile.absolutePath} -> ${sdkProguardFile.absolutePath}")
            } else {
                PluginLogger.warn("内置混淆规则文件不存在: ${pluginProFile.absolutePath}")
                // 创建一个空的混淆文件
                sdkProguardFile.writeText("# Horizon SDK 自动生成的混淆规则\n")
            }
            
            // 2. 追加 DSL 配置的自定义规则
            if (extension.proguardRules.isNotEmpty()) {
                val customRules = "\n# 自定义混淆规则\n" + extension.proguardRules.joinToString("\n")
                sdkProguardFile.appendText(customRules)
                PluginLogger.info("追加 ${extension.proguardRules.size} 条自定义混淆规则")
            }
            
            // 3. 注入到所有 buildType
            val androidExt = project.extensions.findByName("android") as? com.android.build.gradle.BaseExtension
            androidExt?.buildTypes?.all { buildType ->
                buildType.proguardFile(sdkProguardFile)
                PluginLogger.debug("为 buildType ${buildType.name} 注入混淆规则")
            }
            
            PluginLogger.info("混淆规则注入完成: ${sdkProguardFile.absolutePath}")
        } catch (e: Exception) {
            PluginLogger.error("注入混淆规则失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 获取内置混淆规则文件
     * 如果不存在则返回空字符串
     */
    fun getDefaultProguardRules(project: Project): String {
        val pluginProFile = project.rootProject.file("HorizonPlugin/proguard/proguard-rules.sdk.pro")
        return if (pluginProFile.exists()) {
            pluginProFile.readText()
        } else {
            ""
        }
    }
    
    /**
     * 生成混淆规则文件
     * @param outputFile 输出文件
     * @param customRules 自定义规则列表
     * @param defaultRules 默认规则
     */
    fun generateProguardFile(outputFile: File, customRules: List<String>, defaultRules: String = "") {
        try {
            val sb = StringBuilder()
            
            // 添加默认规则
            if (defaultRules.isNotEmpty()) {
                sb.append(defaultRules)
                sb.append("\n\n")
            }
            
            // 添加自定义规则
            if (customRules.isNotEmpty()) {
                sb.append("# 自定义混淆规则\n")
                customRules.forEach { rule ->
                    sb.append(rule)
                    sb.append("\n")
                }
            }
            
            // 写入文件
            outputFile.parentFile.mkdirs()
            outputFile.writeText(sb.toString())
            
            PluginLogger.info("生成混淆规则文件: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            PluginLogger.error("生成混淆规则文件失败: ${e.message}")
            e.printStackTrace()
        }
    }
} 