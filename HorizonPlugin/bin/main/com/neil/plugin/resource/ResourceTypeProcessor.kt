// 作者：Redamancy  时间：2025-06-02
package com.neil.plugin.resource

import com.neil.plugin.logger.PluginLogger
import java.io.File
import java.security.MessageDigest

/**
 * 资源类型处理器
 * 针对不同类型的Android资源文件提供专门的处理方法
 * 支持所有标准的Android资源目录类型
 * 
 * 支持的资源类型包括：
 * - drawable: 图片资源
 * - layout: 布局文件
 * - values: 值资源
 * - mipmap: 启动图标
 * - anim: 动画资源
 * - animator: 属性动画
 * - color: 颜色状态列表
 * - menu: 菜单资源
 * - raw: 原始资源
 * - xml: XML配置资源
 * 
 * 作者：Redamancy  时间：2025-06-02
 */
object ResourceTypeProcessor {
    
    // 所有标准的Android资源目录类型
    val STANDARD_RESOURCE_TYPES = listOf(
        "drawable", "layout", "values", "mipmap", 
        "anim", "animator", "color", "menu", 
        "raw", "xml", "font", "interpolator", 
        "transition"
    )
    
    // 支持限定符的资源目录类型
    val QUALIFIER_SUPPORTED_TYPES = listOf(
        "drawable", "layout", "values", "mipmap", 
        "anim", "animator", "color", "menu",
        "font", "interpolator", "transition"
    )
    
    // 不支持限定符的资源目录类型
    val QUALIFIER_NOT_SUPPORTED_TYPES = listOf(
        "raw", "xml"
    )
    
    /**
     * 判断目录是否为Android资源目录
     * 
     * @param dirName 目录名称
     * @return 是否为资源目录
     */
    fun isResourceDir(dirName: String): Boolean {
        // 检查是否为标准资源目录或带限定符的资源目录
        return STANDARD_RESOURCE_TYPES.contains(dirName) || 
               STANDARD_RESOURCE_TYPES.any { 
                   dirName.startsWith("$it-") && QUALIFIER_SUPPORTED_TYPES.contains(it) 
               }
    }
    
    /**
     * 从资源目录名称中提取基本类型
     * 例如：values-zh-rCN -> values
     * 
     * @param dirName 目录名称
     * @return 资源基本类型
     */
    fun extractBaseResourceType(dirName: String): String {
        return if (dirName.contains("-")) {
            dirName.substringBefore("-")
        } else {
            dirName
        }
    }
    
    /**
     * 判断文件是否为XML资源文件
     * 
     * @param file 文件对象
     * @return 是否为XML资源文件
     */
    fun isXmlResourceFile(file: File): Boolean {
        return file.extension.equals("xml", ignoreCase = true)
    }
    
    /**
     * 判断文件是否为图像资源文件
     * 
     * @param file 文件对象
     * @return 是否为图像资源文件
     */
    fun isImageResourceFile(file: File): Boolean {
        val imageExtensions = listOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
        return imageExtensions.contains(file.extension.lowercase())
    }
    
    /**
     * 计算资源文件的MD5值
     * 
     * @param file 文件对象
     * @param length MD5值的长度
     * @return MD5值的十六进制字符串表示
     */
    fun calculateMd5(file: File, length: Int = 8): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(file.readBytes())
        val fullMd5 = digest.joinToString("") { "%02x".format(it) }
        return fullMd5.substring(0, length.coerceAtMost(fullMd5.length))
    }
    
    /**
     * 创建资源文件的备份
     * 
     * @param file 要备份的文件
     * @return 是否成功创建备份
     */
    fun createBackup(file: File): Boolean {
        try {
            val backupFile = File(file.parent, file.name + ".bak_resreplace")
            if (!backupFile.exists()) {
                file.copyTo(backupFile)
                return true
            }
            return backupFile.exists()
        } catch (e: Exception) {
            PluginLogger.error("创建文件备份失败: ${file.name}, ${e.message}")
            return false
        }
    }
    
    /**
     * 恢复资源文件的备份
     * 
     * @param backupFile 备份文件
     * @return 是否成功恢复备份
     */
    fun restoreBackup(backupFile: File): Boolean {
        try {
            if (!backupFile.exists() || !backupFile.name.endsWith(".bak_resreplace")) {
                return false
            }
            
            val originalFileName = backupFile.name.substringBeforeLast(".bak_resreplace")
            val originalFile = File(backupFile.parent, originalFileName)
            
            if (originalFile.exists()) {
                originalFile.delete()
            }
            
            backupFile.copyTo(originalFile, overwrite = true)
            backupFile.delete()
            
            return true
        } catch (e: Exception) {
            PluginLogger.error("恢复文件备份失败: ${backupFile.name}, ${e.message}")
            return false
        }
    }
    
    /**
     * 列出资源目录中的所有资源目录类型
     * 
     * @param resDir 资源根目录
     * @return 资源类型及对应的目录列表
     */
    fun listResourceTypes(resDir: File): Map<String, List<File>> {
        if (!resDir.exists() || !resDir.isDirectory) {
            return emptyMap()
        }
        
        return resDir.listFiles { file -> file.isDirectory }
            ?.filter { isResourceDir(it.name) }
            ?.groupBy { extractBaseResourceType(it.name) }
            ?: emptyMap()
    }
    
    /**
     * 获取特定资源类型的所有目录
     * 包括带限定符的目录
     * 
     * @param resDir 资源根目录
     * @param resourceType 资源类型
     * @return 匹配的资源目录列表
     */
    fun getResourceTypeDirectories(resDir: File, resourceType: String): List<File> {
        if (!resDir.exists() || !resDir.isDirectory) {
            return emptyList()
        }
        
        return resDir.listFiles { file -> 
            file.isDirectory && (file.name == resourceType || file.name.startsWith("$resourceType-"))
        }?.toList() ?: emptyList()
    }
    
    /**
     * 获取资源文件的资源类型
     * 
     * @param file 资源文件
     * @return 资源类型
     */
    fun getResourceType(file: File): String? {
        if (!file.exists() || !file.isFile) {
            return null
        }
        
        val parentDir = file.parentFile
        return if (parentDir != null && isResourceDir(parentDir.name)) {
            extractBaseResourceType(parentDir.name)
        } else {
            null
        }
    }
} 