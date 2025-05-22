// 作者：Redamancy  时间：2025-07-06
package com.neil.plugin.resource

import com.neil.plugin.logger.PluginLogger
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import org.json.JSONArray

/**
 * 资源处理日志记录工具
 * 提供结构化日志和详细报告
 * 
 * 作者：Redamancy  时间：2025-07-06
 */
object ResourceLogger {
    // 日志级别
    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    // 当前日志级别
    private var currentLogLevel = LogLevel.INFO
    
    // 日志缓存，用于生成报告
    private val logEntries = mutableListOf<Map<String, Any>>()
    
    // 处理统计信息
    private val stats = mutableMapOf<String, Int>().withDefault { 0 }
    
    // 处理时间记录
    private var startTime = 0L
    private var endTime = 0L
    
    /**
     * 设置日志级别
     * 
     * @param level 日志级别
     */
    fun setLogLevel(level: LogLevel) {
        currentLogLevel = level
        debug("日志级别设置为 $level")
    }
    
    /**
     * 记录调试级别日志
     * 
     * @param message 日志信息
     * @param details 详细信息
     */
    fun debug(message: String, details: Map<String, Any> = emptyMap()) {
        if (currentLogLevel.ordinal <= LogLevel.DEBUG.ordinal) {
            PluginLogger.debug(message)
            logInternal(LogLevel.DEBUG, message, details)
        }
    }
    
    /**
     * 记录信息级别日志
     * 
     * @param message 日志信息
     * @param details 详细信息
     */
    fun info(message: String, details: Map<String, Any> = emptyMap()) {
        if (currentLogLevel.ordinal <= LogLevel.INFO.ordinal) {
            PluginLogger.info(message)
            logInternal(LogLevel.INFO, message, details)
        }
    }
    
    /**
     * 记录警告级别日志
     * 
     * @param message 日志信息
     * @param details 详细信息
     */
    fun warn(message: String, details: Map<String, Any> = emptyMap()) {
        if (currentLogLevel.ordinal <= LogLevel.WARN.ordinal) {
            PluginLogger.warn(message)
            logInternal(LogLevel.WARN, message, details)
        }
    }
    
    /**
     * 记录错误级别日志
     * 
     * @param message 日志信息
     * @param details 详细信息
     * @param throwable 异常
     */
    fun error(message: String, details: Map<String, Any> = emptyMap(), throwable: Throwable? = null) {
        if (currentLogLevel.ordinal <= LogLevel.ERROR.ordinal) {
            if (throwable != null) {
                PluginLogger.error("$message: ${throwable.message}")
                throwable.printStackTrace()
            } else {
                PluginLogger.error(message)
            }
            logInternal(LogLevel.ERROR, message, details + mapOf("exception" to (throwable?.message ?: "")))
        }
    }
    
    /**
     * 内部日志记录方法
     */
    private fun logInternal(level: LogLevel, message: String, details: Map<String, Any>) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val entry = mapOf(
            "timestamp" to timestamp,
            "level" to level.name,
            "message" to message,
            "details" to details
        )
        logEntries.add(entry)
    }
    
    /**
     * 开始记录处理时间
     * 
     * @param resourceType 资源类型
     */
    fun startProcessing(resourceType: String = "all") {
        startTime = System.currentTimeMillis()
        info("开始处理 $resourceType 类型资源")
    }
    
    /**
     * 结束记录处理时间
     * 
     * @param resourceType 资源类型
     * @param count 处理数量
     */
    fun endProcessing(resourceType: String = "all", count: Int = 0) {
        endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        stats[resourceType] = stats.getValue(resourceType) + count
        stats["total"] = stats.getValue("total") + count
        
        info("处理 $resourceType 类型资源完成，共处理 $count 个，耗时 ${duration}ms", 
            mapOf("resourceType" to resourceType, "count" to count, "duration" to duration))
    }
    
    /**
     * 记录文件处理操作
     * 
     * @param operationType 操作类型
     * @param file 文件对象
     * @param details 操作详情
     */
    fun logFileOperation(operationType: String, file: File, details: Map<String, Any> = emptyMap()) {
        val fileInfo = mapOf(
            "name" to file.name,
            "path" to file.absolutePath,
            "size" to file.length(),
            "type" to file.extension
        )
        
        info("[$operationType] ${file.name}", mapOf("file" to fileInfo) + details)
        
        // 更新统计信息
        val resourceType = ResourceTypeProcessor.getResourceType(file) ?: "unknown"
        stats[resourceType] = stats.getValue(resourceType) + 1
    }
    
    /**
     * 记录命名操作
     * 
     * @param originalName 原始名称
     * @param newName 新名称
     * @param resourceType 资源类型
     * @param strategy 命名策略
     */
    fun logRenameOperation(
        originalName: String, 
        newName: String, 
        resourceType: String,
        strategy: ResourceNamingStrategy
    ) {
        info("重命名资源：[$resourceType] $originalName -> $newName (策略: ${strategy.name})", 
            mapOf(
                "originalName" to originalName,
                "newName" to newName,
                "resourceType" to resourceType,
                "strategy" to strategy.name
            )
        )
        
        // 更新统计信息
        stats[resourceType] = stats.getValue(resourceType) + 1
        stats["renamed"] = stats.getValue("renamed") + 1
    }
    
    /**
     * 获取统计信息
     * 
     * @return 统计信息
     */
    fun getStats(): Map<String, Int> {
        return stats.toMap()
    }
    
    /**
     * 生成处理报告
     * 
     * @param outputPath 报告输出路径
     * @param format 报告格式 (json/html)
     * @return 报告文件对象
     */
    fun generateReport(outputPath: String, format: String = "json"): File {
        val reportFile = File(outputPath)
        reportFile.parentFile?.mkdirs()
        
        when (format.lowercase()) {
            "json" -> generateJsonReport(reportFile)
            "html" -> generateHtmlReport(reportFile)
            else -> generateJsonReport(reportFile)
        }
        
        info("资源处理报告已生成: ${reportFile.absolutePath}")
        return reportFile
    }
    
    /**
     * 生成JSON格式报告
     */
    private fun generateJsonReport(file: File) {
        val totalTime = endTime - startTime
        
        val report = JSONObject().apply {
            put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            put("duration", totalTime)
            put("stats", JSONObject(stats))
            
            val logsArray = JSONArray()
            logEntries.forEach { entry ->
                logsArray.put(JSONObject(entry))
            }
            put("logs", logsArray)
        }
        
        file.writeText(report.toString(2))
    }
    
    /**
     * 生成HTML格式报告
     */
    private fun generateHtmlReport(file: File) {
        val totalTime = endTime - startTime
        
        val statsHtml = stats.entries.joinToString("") { (type, count) ->
            "<tr><td>$type</td><td>$count</td></tr>"
        }
        
        val logsHtml = logEntries.joinToString("") { entry ->
            val level = entry["level"] as String
            val message = entry["message"] as String
            val timestamp = entry["timestamp"] as String
            
            val levelClass = when (level) {
                "ERROR" -> "error"
                "WARN" -> "warning"
                "INFO" -> "info"
                else -> "debug"
            }
            
            """
            <tr class="$levelClass">
                <td>$timestamp</td>
                <td>$level</td>
                <td>$message</td>
            </tr>
            """
        }
        
        val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>资源处理报告</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; }
                table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #f2f2f2; }
                .error { background-color: #ffeeee; }
                .warning { background-color: #ffffee; }
                .info { background-color: #eeffee; }
                .debug { background-color: #eeeeff; }
                h1, h2 { color: #333; }
                .summary { display: flex; justify-content: space-between; }
                .summary-card { border: 1px solid #ddd; padding: 10px; border-radius: 5px; width: 30%; }
            </style>
        </head>
        <body>
            <h1>资源处理报告</h1>
            <p>生成时间: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}</p>
            
            <div class="summary">
                <div class="summary-card">
                    <h3>总处理时间</h3>
                    <p>${totalTime}ms</p>
                </div>
                <div class="summary-card">
                    <h3>处理文件数量</h3>
                    <p>${stats["total"] ?: 0}</p>
                </div>
                <div class="summary-card">
                    <h3>重命名数量</h3>
                    <p>${stats["renamed"] ?: 0}</p>
                </div>
            </div>
            
            <h2>统计信息</h2>
            <table>
                <tr>
                    <th>资源类型</th>
                    <th>处理数量</th>
                </tr>
                $statsHtml
            </table>
            
            <h2>处理日志</h2>
            <table>
                <tr>
                    <th>时间</th>
                    <th>级别</th>
                    <th>信息</th>
                </tr>
                $logsHtml
            </table>
        </body>
        </html>
        """
        
        file.writeText(html)
    }
    
    /**
     * 清除日志
     */
    fun clear() {
        logEntries.clear()
        stats.clear()
        startTime = 0
        endTime = 0
    }
} 