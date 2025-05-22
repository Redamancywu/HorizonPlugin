// 作者：Redamancy  时间：2025-06-02
package com.neil.plugin.resource

import com.neil.plugin.logger.PluginLogger
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document

/**
 * 资源回退工具类
 * 
 * 用于回退之前通过ResourceIsolationHelper执行的资源重命名和MD5修改
 * 通过读取备份文件(.bak_resreplace)将资源恢复到原始状态
 * 
 * 作者：Redamancy  时间：2025-06-02
 */
object ResourceRollbackHelper {
    
    // 用于标记回退操作完成的文件名
    private const val ROLLBACK_MARKER_FILENAME = ".resource_rollback"
    
    /**
     * 执行资源回退操作
     * 
     * @param resDir 资源目录
     * @param alsoDeleteMappingFile 是否同时删除资源映射文件
     * @param mapFilePath 资源映射文件路径
     * @param forceRollback 是否强制回退，忽略回退标记
     * @return 回退的文件数量
     */
    fun rollbackResources(
        resDir: File,
        alsoDeleteMappingFile: Boolean = true,
        mapFilePath: String = "build/reports/resources/resource_rename_map.json",
        forceRollback: Boolean = false
    ): Int {
        // 检查是否已经回退过（除非强制回退）
        val rollbackMarkerFile = File(resDir.parentFile, ROLLBACK_MARKER_FILENAME)
        if (rollbackMarkerFile.exists() && !forceRollback) {
            PluginLogger.info("资源目录 ${resDir.absolutePath} 已经执行过回退操作，跳过。使用forceRollback=true强制回退。")
            return 0
        }
        
        // 检查是否存在处理标记文件，若无则说明未执行过重命名（除非强制回退）
        val processMarkerFile = File(resDir.parentFile, ResourceIsolationHelper.PROCESS_MARKER_FILENAME)
        if (!processMarkerFile.exists() && !forceRollback) {
            PluginLogger.info("资源目录 ${resDir.absolutePath} 未执行过重命名操作，无需回退。使用forceRollback=true强制回退。")
            return 0
        }
        
        // 强制回退时，即使没有标记文件也执行回退操作，检查是否有.bak_resreplace文件
        if (forceRollback) {
            val hasBackupFiles = resDir.walkTopDown().any { it.name.endsWith(".bak_resreplace") }
            if (!hasBackupFiles) {
                PluginLogger.info("强制回退模式：资源目录 ${resDir.absolutePath} 未找到任何备份文件，无法回退。")
                return 0
            } else {
                PluginLogger.info("强制回退模式：资源目录 ${resDir.absolutePath} 找到备份文件，将执行回退操作。")
            }
        }
        
        var rollbackCount = 0
        
        try {
            // 统一回退所有资源文件（不再分类处理）
            rollbackCount = rollbackAllResources(resDir)
            
            // 删除处理标记文件
            if (processMarkerFile.exists()) {
                processMarkerFile.delete()
                PluginLogger.info("已删除资源处理标记文件: ${processMarkerFile.absolutePath}")
            }
            
            // 删除回退标记（如果是强制回退）
            if (rollbackMarkerFile.exists() && forceRollback) {
                rollbackMarkerFile.delete()
                PluginLogger.info("已删除旧的回退标记文件: ${rollbackMarkerFile.absolutePath}")
            }
            
            // 删除映射文件
            if (alsoDeleteMappingFile) {
                val mappingFile = File(resDir.parentFile.parentFile, mapFilePath)
                if (mappingFile.exists()) {
                    mappingFile.delete()
                    PluginLogger.info("已删除资源映射文件: ${mappingFile.absolutePath}")
                }
            }
            
            // 删除可能存在的临时文件和其他标记
            cleanupResourceProcessingFiles(resDir)
            
            // 创建回退标记文件
            rollbackMarkerFile.writeText("Rollback at ${java.time.LocalDateTime.now()}")
            
            PluginLogger.info("资源回退完成，共回退 $rollbackCount 个文件。")
        } catch (e: Exception) {
            PluginLogger.error("资源回退操作失败: ${e.message}")
            e.printStackTrace()
        }
        
        return rollbackCount
    }
    
    /**
     * 回退所有资源文件
     * 统一处理所有带有.bak_resreplace后缀的文件，不管它们在哪个资源目录中
     * 
     * @param dir 资源目录
     * @return 回退的文件数量
     */
    private fun rollbackAllResources(dir: File): Int {
        var count = 0
        
        // 查找所有备份文件（包括所有资源类型目录）
        val backupFiles = dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".bak_resreplace") }
            .toList()
        
        PluginLogger.info("在目录 ${dir.name} 及其子目录中找到 ${backupFiles.size} 个备份文件")
        
        // 按照资源类型分组输出（仅用于日志记录）
        val backupsByType = backupFiles.groupBy { 
            val parent = it.parentFile.name
            if (parent.contains("-")) parent.substringBefore("-") else parent
        }
        
        backupsByType.forEach { (type, files) ->
            PluginLogger.info("* $type 类型资源: ${files.size}个文件")
        }
        
        // 处理所有备份文件
        backupFiles.forEach { backupFile ->
            try {
                // 获取原始文件名
                val originalFileName = backupFile.name.substringBeforeLast(".bak_resreplace")
                val originalFilePath = backupFile.parent + File.separator + originalFileName
                val originalFile = File(originalFilePath)
                
                // 记录资源类型（用于日志）
                val resourceType = backupFile.parentFile.name
                
                // 检查当前目录中是否有同名文件
                if (originalFile.exists()) {
                    // 如果存在同名文件，先删除
                    originalFile.delete()
                }
                
                // 将备份文件恢复为原始文件
                backupFile.copyTo(originalFile, overwrite = true)
                
                // 删除备份文件
                backupFile.delete()
                
                PluginLogger.info("已回退[$resourceType]资源: $originalFileName")
                count++
            } catch (e: Exception) {
                PluginLogger.error("回退文件 ${backupFile.name} 失败: ${e.message}")
            }
        }
        
        return count
    }
    
    /**
     * 检查是否可以执行回退操作
     * 
     * @param resDir 资源目录
     * @param ignoreMarkers 是否忽略标记文件的检查
     * @return 是否可以回退
     */
    fun canRollback(resDir: File, ignoreMarkers: Boolean = false): Boolean {
        // 如果忽略标记文件检查，则检查是否有.bak_resreplace文件决定是否可以回退
        if (ignoreMarkers) {
            val hasBackupFiles = resDir.walkTopDown().any { it.name.endsWith(".bak_resreplace") }
            return hasBackupFiles
        }
        
        val processMarkerFile = File(resDir.parentFile, ResourceIsolationHelper.PROCESS_MARKER_FILENAME)
        val rollbackMarkerFile = File(resDir.parentFile, ROLLBACK_MARKER_FILENAME)
        
        // 如果已经执行过处理且未执行过回退，则可以回退
        return processMarkerFile.exists() && !rollbackMarkerFile.exists()
    }
    
    /**
     * 清理回退标记，允许再次执行回退操作
     * 
     * @param resDir 资源目录
     */
    fun clearRollbackMarker(resDir: File) {
        val rollbackMarkerFile = File(resDir.parentFile, ROLLBACK_MARKER_FILENAME)
        if (rollbackMarkerFile.exists()) {
            rollbackMarkerFile.delete()
            PluginLogger.info("已清除资源回退标记，可以再次执行回退操作。")
        }
    }
    
    /**
     * 检查资源隔离和回退功能的配置是否有效
     * 
     * @param enableResourceRollback 是否启用资源回退
     * @param enableResourceIsolation 是否启用资源隔离
     * @param enableResourceMd5 是否启用资源MD5修改
     * @return 配置是否有效
     */
    fun validateRollbackConfig(
        enableResourceRollback: Boolean,
        enableResourceIsolation: Boolean,
        enableResourceMd5: Boolean
    ): Boolean {
        // 回退和修改功能不能同时启用
        if (enableResourceRollback && (enableResourceIsolation || enableResourceMd5)) {
            PluginLogger.error("资源回退功能与资源隔离/MD5修改功能不能同时启用")
            PluginLogger.error("请关闭资源隔离和MD5修改功能后再启用资源回退功能")
            return false
        }
        
        // 配置有效
        return true
    }
    
    /**
     * 清理资源处理过程中可能产生的临时文件和标记
     */
    private fun cleanupResourceProcessingFiles(resDir: File) {
        try {
            // 清理临时hash标记文件
            val tempHashFiles = resDir.walkTopDown()
                .filter { it.name.endsWith(".md5hash") }
                .toList()
            
            if (tempHashFiles.isNotEmpty()) {
                tempHashFiles.forEach { it.delete() }
                PluginLogger.info("清理临时hash标记文件: ${tempHashFiles.size}个")
            }
            
            // 清理临时生成的资源文件
            val tempResFiles = resDir.walkTopDown()
                .filter { it.name.endsWith(".tmp_res") }
                .toList()
                
            if (tempResFiles.isNotEmpty()) {
                tempResFiles.forEach { it.delete() }
                PluginLogger.info("清理临时资源文件: ${tempResFiles.size}个") 
            }
        } catch (e: Exception) {
            PluginLogger.warn("清理临时文件时出错: ${e.message}")
        }
    }
} 