// 作者：Redamancy  时间：2025-07-06
package com.neil.plugin.resource

import com.neil.plugin.logger.PluginLogger
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 资源处理性能优化器
 * 提供以下性能优化功能：
 * 1. 并行处理资源文件
 * 2. MD5计算缓存
 * 3. 增量处理资源
 * 4. 文件读写优化
 * 
 * 作者：Redamancy  时间：2025-07-06
 */
object ResourcePerformanceOptimizer {
    // MD5计算缓存
    private val md5Cache = ConcurrentHashMap<String, String>()
    
    // 文件修改时间缓存，用于增量处理
    private val fileModificationCache = ConcurrentHashMap<String, Long>()
    
    // 文件内容缓存，减少重复读取
    private val fileContentCache = ConcurrentHashMap<String, String>()
    
    // 最大缓存大小
    private const val MAX_CACHE_SIZE = 1000
    
    // 默认线程池大小
    private val DEFAULT_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    
    /**
     * 并行处理资源文件
     * 
     * @param files 要处理的文件列表
     * @param processor 文件处理器函数
     * @param threadCount 线程数量，默认为可用处理器数量
     * @return 处理的文件数量
     */
    fun processFilesInParallel(
        files: List<File>,
        processor: (File) -> Boolean,
        threadCount: Int = DEFAULT_THREAD_POOL_SIZE
    ): Int {
        if (files.isEmpty()) return 0
        
        val startTime = System.currentTimeMillis()
        PluginLogger.info("开始并行处理 ${files.size} 个资源文件，线程数：$threadCount")
        
        val executor = Executors.newFixedThreadPool(threadCount)
        val processedCount = AtomicInteger(0)
        
        files.forEach { file ->
            executor.submit {
                try {
                    val isProcessed = processor(file)
                    if (isProcessed) {
                        processedCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    PluginLogger.error("处理文件 ${file.name} 时出错: ${e.message}")
                }
            }
        }
        
        executor.shutdown()
        executor.awaitTermination(30, TimeUnit.MINUTES)
        
        val totalTime = System.currentTimeMillis() - startTime
        PluginLogger.info("并行处理完成，共处理 ${processedCount.get()} 个文件，耗时 ${totalTime}ms")
        
        return processedCount.get()
    }
    
    /**
     * 计算文件MD5，使用缓存机制
     * 
     * @param file 文件对象
     * @param length MD5长度
     * @param forceRefresh 强制刷新缓存
     * @return MD5值
     */
    fun calculateMd5WithCache(file: File, length: Int = 8, forceRefresh: Boolean = false): String {
        val cacheKey = "${file.absolutePath}:${file.lastModified()}"
        
        if (!forceRefresh && md5Cache.containsKey(cacheKey)) {
            return md5Cache[cacheKey]?.substring(0, length.coerceAtMost(32)) ?: ""
        }
        
        // 计算新的MD5值
        val md5Value = ResourceTypeProcessor.calculateMd5(file, 32) // 存储完整的MD5值
        
        // 管理缓存大小
        if (md5Cache.size >= MAX_CACHE_SIZE) {
            md5Cache.keys.take(MAX_CACHE_SIZE / 10).forEach { md5Cache.remove(it) }
        }
        
        md5Cache[cacheKey] = md5Value
        return md5Value.substring(0, length.coerceAtMost(32))
    }
    
    /**
     * 检查文件是否已被修改，用于增量处理
     * 
     * @param file 文件对象
     * @return 是否被修改
     */
    fun isFileModified(file: File): Boolean {
        val cachedModTime = fileModificationCache[file.absolutePath]
        val currentModTime = file.lastModified()
        
        val isModified = cachedModTime == null || cachedModTime != currentModTime
        
        // 更新缓存
        if (isModified) {
            fileModificationCache[file.absolutePath] = currentModTime
        }
        
        return isModified
    }
    
    /**
     * 获取文件内容，使用缓存机制
     * 
     * @param file 文件对象
     * @param forceRefresh 强制刷新缓存
     * @return 文件内容
     */
    fun getFileContentWithCache(file: File, forceRefresh: Boolean = false): String {
        val cacheKey = "${file.absolutePath}:${file.lastModified()}"
        
        if (!forceRefresh && fileContentCache.containsKey(cacheKey)) {
            return fileContentCache[cacheKey] ?: ""
        }
        
        val content = file.readText()
        
        // 管理缓存大小
        if (fileContentCache.size >= MAX_CACHE_SIZE) {
            fileContentCache.keys.take(MAX_CACHE_SIZE / 10).forEach { fileContentCache.remove(it) }
        }
        
        fileContentCache[cacheKey] = content
        return content
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCaches() {
        md5Cache.clear()
        fileModificationCache.clear()
        fileContentCache.clear()
        PluginLogger.info("已清除所有资源处理缓存")
    }
    
    /**
     * 筛选需要处理的文件（增量处理）
     * 
     * @param files 文件列表
     * @return 需要处理的文件列表
     */
    fun filterFilesForIncrementalProcessing(files: List<File>): List<File> {
        return files.filter { isFileModified(it) }
    }
    
    /**
     * 优化的文件写入操作
     * 
     * @param file 目标文件
     * @param content 文件内容
     * @return 是否写入成功
     */
    fun writeFileOptimized(file: File, content: String): Boolean {
        return try {
            // 使用缓冲写入
            file.bufferedWriter().use { it.write(content) }
            
            // 更新缓存
            val cacheKey = "${file.absolutePath}:${file.lastModified()}"
            fileContentCache[cacheKey] = content
            
            true
        } catch (e: Exception) {
            PluginLogger.error("写入文件 ${file.name} 时出错: ${e.message}")
            false
        }
    }
    
    /**
     * 批量处理XML文件的引用更新
     * 
     * @param xmlFiles XML文件列表
     * @param processor XML处理函数
     * @return 处理的文件数量
     */
    fun batchProcessXmlReferences(
        xmlFiles: List<File>,
        processor: (File) -> Boolean
    ): Int {
        return processFilesInParallel(xmlFiles, processor)
    }
} 