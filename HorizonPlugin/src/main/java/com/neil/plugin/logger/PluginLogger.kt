// 作者：Redamancy  时间：2025-05-23
// 插件专用日志系统（支持日志等级、格式化、可选文件输出）
package com.neil.plugin.logger

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.io.FileWriter
import java.io.PrintWriter

/**
 * 日志等级
 */
enum class LogLevel(val priority: Int) {
    DEBUG(1), INFO(2), WARN(3), ERROR(4)
}

/**
 * 插件日志工具，支持日志等级、格式化输出、可选输出到文件
 * 支持：
 * 1. 日志等级控制
 * 2. 时间戳和格式化
 * 3. 自动文件输出
 * 4. 彩色终端输出
 * 作者：Redamancy  时间：2025-05-23
 */
object PluginLogger {
    var logLevel: LogLevel = LogLevel.INFO
    var logToFile: Boolean = false
    var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    private var fileWriter: PrintWriter? = null
    
    // 终端颜色代码
    private const val RESET = "\u001B[0m"
    private const val RED = "\u001B[31m"
    private const val YELLOW = "\u001B[33m"
    private const val GREEN = "\u001B[32m"
    private const val BLUE = "\u001B[34m"
    
    fun debug(msg: String) = log(LogLevel.DEBUG, msg)
    fun info(msg: String) = log(LogLevel.INFO, msg)
    fun warn(msg: String) = log(LogLevel.WARN, msg)
    fun error(msg: String) = log(LogLevel.ERROR, msg)

    fun log(level: LogLevel, msg: String) {
        if (level.priority < logLevel.priority) return
        val time = dateFormat.format(Date())
        val levelName = when(level) {
            LogLevel.DEBUG -> "${BLUE}DEBUG${RESET}"
            LogLevel.INFO -> "${GREEN}INFO${RESET}"
            LogLevel.WARN -> "${YELLOW}WARN${RESET}"
            LogLevel.ERROR -> "${RED}ERROR${RESET}"
        }
        
        val plainLevelName = level.name
        val plainFormatted = "[HorizonPlugin][$time][$plainLevelName] $msg"
        val colorFormatted = "[HorizonPlugin][$time][$levelName] $msg"
        
        // 控制台彩色输出
        println(colorFormatted)
        
        // 文件输出（无颜色）
        if (logToFile) {
            ensureFileWriter()
            fileWriter?.println(plainFormatted)
            fileWriter?.flush()
        }
    }
    
    /**
     * 确保文件写入器已初始化
     */
    private fun ensureFileWriter() {
        if (fileWriter == null && logFile != null) {
            try {
                logFile!!.parentFile?.mkdirs()
                fileWriter = PrintWriter(FileWriter(logFile!!, true), true)
            } catch (e: Exception) {
                println("${RED}[HorizonPlugin] 无法创建日志文件: ${e.message}${RESET}")
                logToFile = false
            }
        }
    }
    
    /**
     * 设置输出到文件
     * @param file 日志文件
     * @param append 是否追加写入，默认true
     */
    fun setLogFile(file: File, append: Boolean = true) {
        closeFileWriter()
        logFile = file
        logToFile = true
        try {
            file.parentFile?.mkdirs()
            fileWriter = PrintWriter(FileWriter(file, append), true)
            info("日志文件已设置为：${file.absolutePath}")
        } catch (e: Exception) {
            println("${RED}[HorizonPlugin] 无法创建日志文件: ${e.message}${RESET}")
            logToFile = false
        }
    }
    
    /**
     * 关闭文件写入器
     */
    fun closeFileWriter() {
        fileWriter?.close()
        fileWriter = null
    }
    
    /**
     * 创建默认日志文件（在build目录下）
     * @param project 工程对象，用于获取build目录
     * @param fileName 文件名，默认为horizon_plugin.log
     */
    fun createDefaultLogFile(project: org.gradle.api.Project, fileName: String = "horizon_plugin.log") {
        val file = File(project.buildDir, "logs/$fileName")
        setLogFile(file)
    }
} 