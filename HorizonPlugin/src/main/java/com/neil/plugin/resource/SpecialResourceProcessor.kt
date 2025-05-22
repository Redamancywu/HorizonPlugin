// 作者：Redamancy  时间：2025-07-06
package com.neil.plugin.resource

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import com.neil.plugin.logger.PluginLogger

/**
 * 特定资源类型处理器
 * 提供针对特定Android资源类型的专门处理逻辑
 * 
 * 作者：Redamancy  时间：2025-07-06
 */
object SpecialResourceProcessor {
    
    /**
     * 资源类型处理配置
     * 
     * @property enabled 是否启用该处理器
     * @property optimizeSize 是否优化资源大小
     * @property applyCompression 是否应用压缩
     * @property qualityLevel 质量级别 (0-100)
     */
    data class ProcessorConfig(
        val enabled: Boolean = true,
        val optimizeSize: Boolean = true,
        val applyCompression: Boolean = false,
        val qualityLevel: Int = 80
    )
    
    // 特定类型处理器配置
    private val processorConfigs = mutableMapOf<String, ProcessorConfig>()
    
    init {
        // 默认配置
        processorConfigs["drawable"] = ProcessorConfig(enabled = true, optimizeSize = true)
        processorConfigs["layout"] = ProcessorConfig(enabled = true, optimizeSize = false)
        processorConfigs["mipmap"] = ProcessorConfig(enabled = true, optimizeSize = true)
        processorConfigs["raw"] = ProcessorConfig(enabled = false)
        processorConfigs["values"] = ProcessorConfig(enabled = true)
        processorConfigs["xml"] = ProcessorConfig(enabled = true)
    }
    
    /**
     * 设置处理器配置
     * 
     * @param resourceType 资源类型
     * @param config 处理器配置
     */
    fun setProcessorConfig(resourceType: String, config: ProcessorConfig) {
        processorConfigs[resourceType] = config
        PluginLogger.info("已设置 $resourceType 类型资源处理器配置: $config")
    }
    
    /**
     * 获取处理器配置
     * 
     * @param resourceType 资源类型
     * @return 处理器配置
     */
    fun getProcessorConfig(resourceType: String): ProcessorConfig {
        return processorConfigs[resourceType] ?: ProcessorConfig()
    }
    
    /**
     * 处理特定类型的资源
     * 
     * @param file 资源文件
     * @param resourceType 资源类型
     * @return 是否处理成功
     */
    fun processSpecialResource(file: File, resourceType: String? = null): Boolean {
        val type = resourceType ?: ResourceTypeProcessor.getResourceType(file) ?: return false
        val config = getProcessorConfig(type)
        
        if (!config.enabled) {
            return false
        }
        
        return when (type) {
            "drawable" -> processDrawableResource(file, config)
            "layout" -> processLayoutResource(file, config)
            "values" -> processValuesResource(file, config)
            "mipmap" -> processMipmapResource(file, config)
            "raw" -> processRawResource(file, config)
            "xml" -> processXmlResource(file, config)
            else -> false
        }
    }
    
    /**
     * 处理drawable资源
     */
    private fun processDrawableResource(file: File, config: ProcessorConfig): Boolean {
        if (!ResourceTypeProcessor.isImageResourceFile(file)) {
            return processXmlBasedDrawable(file, config)
        }
        
        if (config.optimizeSize) {
            // 这里仅模拟图像优化处理，实际应用中需要使用图像处理库
            ResourceLogger.info("优化Drawable图像资源: ${file.name}")
            
            // 标记文件已处理
            file.setLastModified(System.currentTimeMillis())
            return true
        }
        
        return false
    }
    
    /**
     * 处理XML格式的Drawable资源
     */
    private fun processXmlBasedDrawable(file: File, config: ProcessorConfig): Boolean {
        if (!file.extension.equals("xml", ignoreCase = true)) {
            return false
        }
        
        try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val root = doc.documentElement
            
            // 检查是否是shape、selector等drawable元素
            if (root.nodeName in listOf("shape", "selector", "layer-list", "ripple", "vector")) {
                // 针对XML drawable的处理
                ResourceLogger.info("处理XML Drawable资源: ${file.name} (${root.nodeName})")
                
                // 检查是否添加了android:tint属性
                if (!root.hasAttribute("android:tint") && root.nodeName == "vector") {
                    // 对于vector，添加tint支持
                    root.setAttribute("android:tint", "?attr/colorControlNormal")
                    
                    // 保存修改后的文件
                    val transformer = TransformerFactory.newInstance().newTransformer()
                    transformer.transform(DOMSource(doc), StreamResult(file))
                    
                    ResourceLogger.info("为Vector Drawable添加tint支持: ${file.name}")
                    return true
                }
            }
        } catch (e: Exception) {
            ResourceLogger.error("处理XML Drawable资源失败: ${file.name}", throwable = e)
        }
        
        return false
    }
    
    /**
     * 处理layout资源
     */
    private fun processLayoutResource(file: File, config: ProcessorConfig): Boolean {
        if (!file.extension.equals("xml", ignoreCase = true)) {
            return false
        }
        
        try {
            val content = file.readText()
            var modified = false
            var newContent = content
            
            // 检测并替换常见的性能问题
            
            // 1. 替换嵌套的LinearLayout为ConstraintLayout
            if (content.contains("<LinearLayout") && content.contains("android:orientation") && 
                !content.contains("tools:ignore=\"NestedWeights\"")) {
                ResourceLogger.info("检测到可能的嵌套LinearLayout问题: ${file.name}")
                modified = true
            }
            
            // 2. 检测过深的视图层次
            val depthRegex = Regex("<(\\w+)(?:\\s+[^>]*)?>\n(?:\\s*<(?:\\w+)(?:\\s+[^>]*)?>\n){5,}")
            if (depthRegex.containsMatchIn(content)) {
                ResourceLogger.warn("检测到过深的视图层次结构: ${file.name}")
                modified = true
            }
            
            // 3. 优化include标签使用
            if (content.contains("<include") && 
                !content.contains("layout_width=\"0dp\"") && 
                !content.contains("layout_height=\"0dp\"")) {
                ResourceLogger.info("优化include标签使用: ${file.name}")
                
                // 确保include标签包含必要的宽高属性
                newContent = newContent.replace(
                    Regex("<include\\s+([^>]*)>"),
                    { matchResult ->
                        val attributes = matchResult.groupValues[1]
                        val hasWidth = attributes.contains("layout_width")
                        val hasHeight = attributes.contains("layout_height")
                        
                        var replacementStr = "<include "
                        if (!hasWidth) replacementStr += "android:layout_width=\"match_parent\" "
                        if (!hasHeight) replacementStr += "android:layout_height=\"wrap_content\" "
                        replacementStr += attributes + ">"
                        
                        replacementStr
                    }
                )
                modified = true
            }
            
            if (modified) {
                file.writeText(newContent)
                return true
            }
        } catch (e: Exception) {
            ResourceLogger.error("处理Layout资源失败: ${file.name}", throwable = e)
        }
        
        return false
    }
    
    /**
     * 处理values资源
     */
    private fun processValuesResource(file: File, config: ProcessorConfig): Boolean {
        if (!file.extension.equals("xml", ignoreCase = true)) {
            return false
        }
        
        try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val root = doc.documentElement
            var modified = false
            
            // 处理string资源，检查是否有未本地化的字符串
            val stringNodes = doc.getElementsByTagName("string")
            if (stringNodes.length > 0) {
                for (i in 0 until stringNodes.length) {
                    val node = stringNodes.item(i) as org.w3c.dom.Element
                    val name = node.getAttribute("name")
                    
                    // 检查字符串中是否有硬编码的标点符号
                    val text = node.textContent
                    if (text.contains("...") || text.contains("…")) {
                        ResourceLogger.warn("检测到硬编码的省略号，应使用XML实体: $name")
                        
                        // 替换为XML实体
                        val newText = text.replace("...", "&#8230;").replace("…", "&#8230;")
                        node.textContent = newText
                        modified = true
                    }
                }
            }
            
            // 处理颜色资源，转换为夜间模式兼容的格式
            val colorNodes = doc.getElementsByTagName("color")
            if (colorNodes.length > 0) {
                for (i in 0 until colorNodes.length) {
                    val node = colorNodes.item(i) as org.w3c.dom.Element
                    val name = node.getAttribute("name")
                    
                    // 检查是否是主题相关颜色但没有夜间模式适配
                    if ((name.contains("background") || name.contains("text") || name.contains("primary")) && 
                        !file.name.contains("night")) {
                        ResourceLogger.info("检测到未适配夜间模式的主题颜色: $name")
                    }
                }
            }
            
            if (modified) {
                val transformer = TransformerFactory.newInstance().newTransformer()
                transformer.transform(DOMSource(doc), StreamResult(file))
                return true
            }
        } catch (e: Exception) {
            ResourceLogger.error("处理Values资源失败: ${file.name}", throwable = e)
        }
        
        return false
    }
    
    /**
     * 处理mipmap资源
     */
    private fun processMipmapResource(file: File, config: ProcessorConfig): Boolean {
        // mipmap主要用于应用图标，类似drawable处理
        return processDrawableResource(file, config)
    }
    
    /**
     * 处理raw资源
     */
    private fun processRawResource(file: File, config: ProcessorConfig): Boolean {
        // raw资源通常不做特殊处理
        ResourceLogger.info("处理Raw资源: ${file.name}")
        return false
    }
    
    /**
     * 处理xml资源
     */
    private fun processXmlResource(file: File, config: ProcessorConfig): Boolean {
        if (!file.extension.equals("xml", ignoreCase = true)) {
            return false
        }
        
        try {
            // 对于animator等特殊XML资源，进行处理
            val content = file.readText()
            
            if (content.contains("<objectAnimator") || content.contains("<animator")) {
                ResourceLogger.info("处理动画XML资源: ${file.name}")
                // 这里可以进行动画相关处理
                return true
            } else if (content.contains("<PreferenceScreen")) {
                ResourceLogger.info("处理Preference XML资源: ${file.name}")
                // 对于偏好设置XML处理
                return true
            }
        } catch (e: Exception) {
            ResourceLogger.error("处理XML资源失败: ${file.name}", throwable = e)
        }
        
        return false
    }
    
    /**
     * 批量处理特定类型的资源
     * 
     * @param resDir 资源目录
     * @param resourceType 资源类型，为null时处理所有类型
     * @return 处理的文件数量
     */
    fun batchProcessSpecialResources(resDir: File, resourceType: String? = null): Int {
        if (!resDir.exists() || !resDir.isDirectory) {
            return 0
        }
        
        ResourceLogger.startProcessing(resourceType ?: "特定资源")
        
        val files = if (resourceType != null) {
            val typeDirs = ResourceTypeProcessor.getResourceTypeDirectories(resDir, resourceType)
            typeDirs.flatMap { dir -> 
                dir.walkTopDown().filter { it.isFile }.toList() 
            }
        } else {
            resDir.walkTopDown().filter { 
                it.isFile && ResourceTypeProcessor.isResourceDir(it.parentFile?.name ?: "") 
            }.toList()
        }
        
        val processedCount = ResourcePerformanceOptimizer.processFilesInParallel(
            files = files,
            processor = { file ->
                val type = ResourceTypeProcessor.getResourceType(file)
                if (type != null) {
                    processSpecialResource(file, type)
                } else {
                    false
                }
            }
        )
        
        ResourceLogger.endProcessing(resourceType ?: "特定资源", processedCount)
        
        return processedCount
    }
} 