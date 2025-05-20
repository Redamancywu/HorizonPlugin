// 作者：Redamancy  时间：2025-05-20
package com.neil.plugin.autoregister

/**
 * ServiceLoader - 提供类似Java SPI的便捷服务发现机制
 * 基于HorizonPlugin自动注册模块，提供自由接口访问和初始化
 * 
 * 示例用法:
 * ```kotlin
 * // 初始化模块注册表（仅创建非懒加载的模块实例）
 * ServiceLoader.init(context)
 * 
 * // 获取特定服务类型的所有实现
 * val services = ServiceLoader.load<MyService>()
 * 
 * // 获取特定服务类型的单个实现（第一个）
 * val service = ServiceLoader.loadFirst<MyService>()
 * 
 * // 获取特定类型的服务（按模块类型筛选）
 * val loggers = ServiceLoader.loadByType<Logger>("logging")
 * 
 * // 自定义初始化接口
 * interface MyInitializable {
 *     fun initialize(context: Context)
 * }
 * 
 * // 然后可以自行调用初始化
 * val service = ServiceLoader.loadFirst<MyService>()
 * if (service is MyInitializable) {
 *     service.initialize(myContext)
 * }
 * ```
 * 
 * 作者：Redamancy  时间：2025-05-20
 */
object ServiceLoader {
    // 改为public属性
    var isInitialized = false
    var context: Any? = null
    lateinit var registryClassName: String
    lateinit var generatedPackage: String
    
    /**
     * 初始化ServiceLoader
     * 
     * 注意：此方法不会自动初始化所有模块，仅创建非懒加载的模块实例
     * 对于模块的初始化逻辑，建议开发者自行定义接口并调用方法。
     * 
     * @param context 上下文对象
     * @param registryClass 注册类的名称（默认为"ModuleRegistry"）
     * @param packageName 生成的注册类的包名（默认为"com.neil.plugin.autoregister"）
     * @return 成功初始化的模块数量
     */
    @JvmOverloads
    fun init(
        context: Any, 
        registryClass: String = "ModuleRegistry",
        packageName: String = "com.neil.plugin.autoregister"
    ): Int {
        if (isInitialized) {
            println("ServiceLoader已经初始化")
            return 0
        }
        
        this.context = context
        this.registryClassName = registryClass
        this.generatedPackage = packageName
        
        try {
            // 调用ModuleRegistry.init(context)初始化注册表
            val registryClass = Class.forName("$packageName.$registryClass")
            val initMethod = registryClass.getMethod("init", Any::class.java)
            val result = initMethod.invoke(null, context) as Int
            isInitialized = true
            return result
        } catch (e: Exception) {
            println("ServiceLoader初始化失败: ${e.message}")
            e.printStackTrace()
            return 0
        }
    }
    
    /**
     * 获取特定类型服务的所有实现实例
     * @return 服务实现列表，如果未找到则返回空列表
     */
    fun <T> load(clazz: Class<T>): List<T> {
        checkInitialized()
        
        try {
            val registryClass = Class.forName("$generatedPackage.$registryClassName")
            // 使用ServiceProvider助手类
            val serviceProviderClass = registryClass.getDeclaredClasses()
                .firstOrNull { it.simpleName == "ServiceProvider" }
                ?: throw IllegalStateException("未找到ServiceProvider助手类")
                
            val getAllMethod = serviceProviderClass.getMethod("getAll", Class::class.java)
            
            // 调用方法
            val result = getAllMethod.invoke(null, clazz) as? List<*>
            return result?.filterIsInstance(clazz) ?: emptyList()
        } catch (e: Exception) {
            println("加载服务失败: ${e.message}")
            e.printStackTrace()
        }
        
        return emptyList()
    }
    
    /**
     * 获取特定类型服务的所有实现实例（使用内联reified方式）
     */
    inline fun <reified T> load(): List<T> {
        return load(T::class.java)
    }
    
    /**
     * 获取特定类型的第一个服务实现实例
     * @return 服务实例，如果未找到则返回null
     */
    fun <T> loadFirst(clazz: Class<T>): T? {
        checkInitialized()
        
        try {
            val registryClass = Class.forName("$generatedPackage.$registryClassName")
            // 使用ServiceProvider助手类
            val serviceProviderClass = registryClass.getDeclaredClasses()
                .firstOrNull { it.simpleName == "ServiceProvider" }
                ?: throw IllegalStateException("未找到ServiceProvider助手类")
                
            val getMethod = serviceProviderClass.getMethod("get", Class::class.java)
            
            // 调用方法
            val result = getMethod.invoke(null, clazz)
            return clazz.isInstance(result)?.let { clazz.cast(result) }
        } catch (e: Exception) {
            println("加载服务失败: ${e.message}")
            e.printStackTrace()
        }
        
        return null
    }
    
    /**
     * 获取特定类型的第一个服务实现实例（使用内联reified方式）
     */
    inline fun <reified T> loadFirst(): T? {
        return loadFirst(T::class.java)
    }
    
    /**
     * 按类型筛选获取服务实例
     * @param type 服务类型（对应@AutoRegisterModule注解的type参数）
     * @return 指定类型的服务实现列表
     */
    fun <T> loadByType(clazz: Class<T>, type: String): List<T> {
        checkInitialized()
        
        try {
            val registryClass = Class.forName("$generatedPackage.$registryClassName")
            // 使用ServiceProvider助手类
            val serviceProviderClass = registryClass.getDeclaredClasses()
                .firstOrNull { it.simpleName == "ServiceProvider" }
                ?: throw IllegalStateException("未找到ServiceProvider助手类")
                
            val getByTypeMethod = serviceProviderClass.getMethod("getByType", Class::class.java, String::class.java)
            
            // 调用方法
            val result = getByTypeMethod.invoke(null, clazz, type) as? List<*>
            return result?.filterIsInstance(clazz) ?: emptyList()
        } catch (e: Exception) {
            println("按类型加载服务失败: ${e.message}")
            e.printStackTrace()
        }
        
        return emptyList()
    }
    
    /**
     * 按类型筛选获取服务实例（使用内联reified方式）
     */
    inline fun <reified T> loadByType(type: String): List<T> {
        return loadByType(T::class.java, type)
    }
    
    /**
     * 按分组筛选获取服务实例
     * @param group 服务分组（对应@AutoRegisterModule注解的group参数）
     * @return 指定分组的服务实现列表
     */
    fun <T> loadByGroup(clazz: Class<T>, group: String): List<T> {
        checkInitialized()
        
        try {
            val registryClass = Class.forName("$generatedPackage.$registryClassName")
            // 使用ServiceProvider助手类
            val serviceProviderClass = registryClass.getDeclaredClasses()
                .firstOrNull { it.simpleName == "ServiceProvider" }
                ?: throw IllegalStateException("未找到ServiceProvider助手类")
                
            val getByGroupMethod = serviceProviderClass.getMethod("getByGroup", Class::class.java, String::class.java)
            
            // 调用方法
            val result = getByGroupMethod.invoke(null, clazz, group) as? List<*>
            return result?.filterIsInstance(clazz) ?: emptyList()
        } catch (e: Exception) {
            println("按分组加载服务失败: ${e.message}")
            e.printStackTrace()
        }
        
        return emptyList()
    }
    
    /**
     * 按分组筛选获取服务实例（使用内联reified方式）
     */
    inline fun <reified T> loadByGroup(group: String): List<T> {
        return loadByGroup(T::class.java, group)
    }
    
    /**
     * 检查ServiceLoader是否已初始化
     */
    fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("ServiceLoader未初始化，请先调用ServiceLoader.init(context)")
        }
    }
} 