// 作者：Redamancy  时间：2025-05-20
package com.neil.plugin.autoregister

/**
 * 标记需要自动注册的模块类
 * 支持无参数和有参数两种写法
 * @param desc 模块描述，默认空字符串
 * @param type 模块类型，默认"default"
 * @param author 作者，默认空字符串
 * @param version 版本，默认"1.0.0"
 * @param group 分组，默认空字符串
 * @param lazy 是否懒加载，默认false（即立即加载）
 * 作者：Redamancy  时间：2025-05-20
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoRegisterModule(
    val desc: String = "",
    val type: String = "default",
    val author: String = "",
    val version: String = "1.0.0",
    val group: String = "",
    val lazy: Boolean = false
)