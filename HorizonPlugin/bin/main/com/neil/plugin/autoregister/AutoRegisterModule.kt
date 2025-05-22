// 作者：Redamancy  时间：2025-06-02
package com.neil.plugin.autoregister

/**
 * 自动注册模块注解，用于标记需要自动注册的模块类
 *
 * 使用示例:
 * ```kotlin
 * @AutoRegisterModule(
 *     desc = "用户模块",
 *     type = "user",
 *     author = "Redamancy",
 *     version = "1.0.0",
 *     priority = 10
 * )
 * class UserModule {
 *     // 模块实现...
 * }
 * ```
 * 
 * 作者：Redamancy  时间：2025-06-02
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoRegisterModule(
    /**
     * 模块描述
     */
    val desc: String = "",
    
    /**
     * 模块类型，用于分类和按类型查找
     */
    val type: String = "default",
    
    /**
     * 模块作者
     */
    val author: String = "",
    
    /**
     * 模块版本
     */
    val version: String = "1.0.0",
    
    /**
     * 模块分组，用于按分组查找
     */
    val group: String = "default",
    
    /**
     * 是否懒加载，true表示仅在需要时初始化，false表示启动时初始化
     */
    val lazy: Boolean = false,
    
    /**
     * 优先级，数值越大优先级越高
     * 影响loadFirst()返回顺序和load()等方法返回的列表顺序
     */
    val priority: Int = 0
)