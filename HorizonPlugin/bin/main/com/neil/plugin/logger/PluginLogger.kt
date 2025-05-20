// 作者：Redamancy  时间：2025-05-19
// 插件专用日志系统（支持日志等级、格式化、可选文件输出）
package com.neil.plugin.logger

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 日志等级
 */
enum class LogLevel(val priority: Int) {
    DEBUG(1), INFO(2), WARN(3), ERROR(4)
}

/**
 * 插件日志工具，支持日志等级、格式化输出、可选输出到文件
 * 作者：Redamancy  时间：2025-05-19
 */
object PluginLogger {
    var logLevel: LogLevel = LogLevel.INFO
    var logToFile: Boolean = false
    var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    fun debug(msg: String) = log(LogLevel.DEBUG, msg)
    fun info(msg: String) = log(LogLevel.INFO, msg)
    fun warn(msg: String) = log(LogLevel.WARN, msg)
    fun error(msg: String) = log(LogLevel.ERROR, msg)

    fun log(level: LogLevel, msg: String) {
        if (level.priority < logLevel.priority) return
        val time = dateFormat.format(Date())
        val formatted = "[HorizonPlugin][$time][${level.name}] $msg"
        println(formatted)
        if (logToFile && logFile != null) {
            logFile!!.appendText(formatted + "\n")
        }
    }
} 