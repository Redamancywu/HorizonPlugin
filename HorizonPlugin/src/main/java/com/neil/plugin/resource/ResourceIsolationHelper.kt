// 作者：Redamancy  时间：2025-05-19
// 资源隔离与自动重命名工具类
package com.neil.plugin.resource

import com.neil.plugin.logger.PluginLogger
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * 资源隔离与自动重命名工具
 * 支持：
 * 1. 文件级资源自动重命名（加前缀/加md5）
 * 2. values资源项自动加前缀
 * @param prefix 资源前缀
 * @param enableMd5 是否启用md5重命名
 * 作者：Redamancy  时间：2025-05-19
 */
object ResourceIsolationHelper {
    fun processResDir(resDir: File, prefix: String, enableMd5: Boolean = false) {
        if (!resDir.exists() || !resDir.isDirectory) return
        // 1. 文件级资源自动重命名
        resDir.walkTopDown().filter { it.isFile && it.parentFile.name != "." && it.parentFile.name != "values" }.forEach { resFile ->
            if (!resFile.name.startsWith(prefix)) {
                val newName = if (enableMd5) prefix + md5(resFile.readBytes()) + "_" + resFile.name else prefix + resFile.name
                val newFile = File(resFile.parent, newName)
                resFile.renameTo(newFile)
                PluginLogger.info("资源自动重命名：${resFile.name} -> ${newFile.name}")
            }
        }
        // 2. values资源项自动加前缀
        val valuesDir = File(resDir, "values")
        valuesDir.listFiles { file -> file.extension == "xml" }?.forEach { xmlFile ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
            val nodes = doc.getElementsByTagName("*")
            var changed = false
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node is org.w3c.dom.Element && node.hasAttribute("name")) {
                    val name = node.getAttribute("name")
                    if (!name.startsWith(prefix)) {
                        val newName = if (enableMd5) prefix + md5(name.toByteArray()) + "_" + name else prefix + name
                        node.setAttribute("name", newName)
                        PluginLogger.info("values资源自动加前缀：$name -> $newName")
                        changed = true
                    }
                }
            }
            if (changed) {
                val transformer = TransformerFactory.newInstance().newTransformer()
                transformer.transform(DOMSource(doc), StreamResult(xmlFile))
            }
        }
    }

    private fun md5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
} 