// 作者：Redamancy  时间：2025-05-19
// Horizon插件DSL扩展
package com.neil.plugin

/**
 * Horizon插件DSL扩展，支持自动注册等功能配置
 * 作者：Redamancy  时间：2025-05-19
 */
open class HorizonExtension {
    /** 是否启用自动注册，默认true */
    var enableAutoRegister: Boolean = true

    /** 需要扫描的模块包名列表 */
    var modulePackages: MutableList<String> = mutableListOf()

    /** 需要排除的包名列表 */
    var excludePackages: MutableList<String> = mutableListOf()

    /** 生成的注册类名，默认 ModuleRegistry */
    var registerClassName: String = "ModuleRegistry"

    /** 生成代码输出目录（可选，默认 build/generated/horizon） */
    var outputDir: String = "build/generated/horizon"

    /** 日志级别，默认INFO，可选DEBUG/INFO/WARN/ERROR */
    var logLevel: String = "INFO"

    /** 额外参数，便于后续扩展 */
    var extraArgs: MutableMap<String, Any> = mutableMapOf()

    /** 生成注册类的包名，默认 com.neil.plugin.autoregister */
    var generatedPackage: String = "com.neil.plugin.autoregister"

    /** 追加自定义混淆规则 */
    var proguardRules: MutableList<String> = mutableListOf()

    /**\n     * 资源白名单配置\n     * 支持：直接 List<String>，或指定文件路径（String/多个文件路径 List<String>）\n     * 文件支持 .txt（每行一个规则）、.json（数组）等，便于团队协作和版本管理\n     * 示例：resourceWhiteList = listOf("drawable:ic_sdk_*", "string:keep_me", "my_third_party_*")\n     *      resourceWhiteList = "sdk_whitelist.txt"\n     *      resourceWhiteList = listOf("sdk_whitelist.txt", "module_whitelist.txt")\n     * 作者：Redamancy  时间：2025-05-19\n     */\n    var resourceWhiteList: Any? = null
} 