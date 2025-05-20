// 作者：Redamancy  时间：2025-05-23
// Manifest片段智能合并工具
package com.neil.plugin.manifest

import com.neil.plugin.logger.PluginLogger
import org.gradle.api.Project
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Manifest片段智能合并工具
 * 支持：
 * 1. 合并所有模块Manifest片段
 * 2. 冲突检测和处理
 * 3. 结构化合并保证顺序和关系
 * 作者：Redamancy  时间：2025-05-23
 */
object ManifestMerger {
    
    /**
     * 合并所有模块的Manifest片段到主工程AndroidManifest.xml
     * @param project 主工程
     */
    fun mergeModuleManifests(project: Project) {
        PluginLogger.info("智能结构化合并所有模块 Manifest 片段")
        val mainManifest = project.file("src/main/AndroidManifest.xml")
        if (!mainManifest.exists()) {
            PluginLogger.warn("主工程AndroidManifest.xml不存在，无法进行合并")
            return
        }
        
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val mainDoc = factory.newDocumentBuilder().parse(mainManifest)
            val appNode = mainDoc.getElementsByTagName("application").item(0) as? Element
            
            if (appNode == null) {
                PluginLogger.warn("主工程AndroidManifest.xml中未找到application节点，无法进行合并")
                return
            }
            
            val allModules = project.rootProject.subprojects.filter { it != project }.sortedBy { it.name }
            var mergeCount = 0
            var skipCount = 0
            
            // 按模块名称排序，确保合并顺序一致
            allModules.forEach { module ->
                val fragment = module.file("src/main/manifest/fragment_${module.name}.xml")
                if (fragment.exists()) {
                    mergeCount += mergeModuleManifest(module, fragment, mainDoc, appNode)
                }
            }
            
            // 保存合并后的mainManifest
            val transformer = TransformerFactory.newInstance().newTransformer()
            val source = DOMSource(mainDoc)
            val result = StreamResult(mainManifest)
            transformer.transform(source, result)
            
            PluginLogger.info("Manifest结构化合并完成: 合并了${mergeCount}个节点，跳过了${skipCount}个冲突节点")
        } catch (e: Exception) {
            PluginLogger.error("Manifest合并失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 合并单个模块的Manifest片段
     * @param module 模块
     * @param fragment 片段文件
     * @param mainDoc 主文档
     * @param appNode 主文档application节点
     * @return 合并的节点数
     */
    private fun mergeModuleManifest(
        module: Project, 
        fragment: File, 
        mainDoc: org.w3c.dom.Document, 
        appNode: Element
    ): Int {
        var mergeCount = 0
        var skipCount = 0
        
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val fragDoc = factory.newDocumentBuilder().parse(fragment)
            val fragAppNode = fragDoc.getElementsByTagName("application").item(0) as? Element
            
            if (fragAppNode == null) {
                PluginLogger.warn("模块 ${module.name} 的Manifest片段中未找到application节点，跳过")
                return 0
            }
            
            val fragChildren = fragAppNode.childNodes
            for (i in 0 until fragChildren.length) {
                val node = fragChildren.item(i)
                if (node is Element) {
                    val nameAttr = node.getAttribute("android:name")
                    val exists = (0 until appNode.childNodes.length).any { j ->
                        val child = appNode.childNodes.item(j)
                        child is Element && child.tagName == node.tagName && 
                        child.getAttribute("android:name") == nameAttr
                    }
                    
                    if (exists) {
                        PluginLogger.warn("Manifest合并冲突: ${node.tagName} $nameAttr 已存在，已跳过")
                        skipCount++
                    } else {
                        appNode.appendChild(mainDoc.importNode(node, true))
                        PluginLogger.info("已合并模块 ${module.name} 的节点: ${node.tagName} $nameAttr")
                        mergeCount++
                    }
                }
            }
            
            return mergeCount
        } catch (e: Exception) {
            PluginLogger.error("合并模块 ${module.name} 的Manifest失败: ${e.message}")
            e.printStackTrace()
            return 0
        }
    }
} 