// 作者：Redamancy  时间：2025-07-11
package com.neil.plugin.autoregister

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * HorizonServiceLoader - 提供自动模块发现和管理的服务加载器
 * 
 * 示例用法:
 * ```kotlin
 * // 初始化模块（仅创建非懒加载的模块实例）
 * HorizonServiceLoader.init(context)
 * 
 * // 获取特定服务类型的所有实现
 * val services = HorizonServiceLoader.load<MyService>()
 * 
 * // 获取特定服务类型的单个实现（第一个）
 * val service = HorizonServiceLoader.loadFirst<MyService>()
 * 
 * // 获取特定类型的服务（按模块类型筛选）
 * val loggers = HorizonServiceLoader.loadByType<Logger>("logging")
 * ```
 * 
 * 作者：Redamancy  时间：2025-07-11
 */
object HorizonServiceLoader {
    /**
     * 模块状态枚举
     */
    enum class ModuleState {
        PENDING,    // 待处理
        INITIALIZING, // 初始化中
        INITIALIZED,  // 已初始化
        FAILED       // 初始化失败
    }
    
    /**
     * 模块信息数据类
     */
    data class ModuleInfo(
        val className: String,      // 类名
        val desc: String,           // 描述
        val type: String,           // 类型
        val author: String,         // 作者
        val version: String,        // 版本
        val group: String,          // 分组
        val lazy: Boolean,          // 是否懒加载
        val interfaces: List<String>, // 实现的接口列表
        val priority: Int = 0       // 优先级，数值越大优先级越高
    ) {
        var state: ModuleState = ModuleState.PENDING
        var instance: Any? = null
        var error: String? = null
        var initTimeMs: Long = 0
        
        // 依赖关系跟踪，用于检测循环依赖
        val initializingDependents = mutableSetOf<String>()
        
        // 初始化完成标记，用于线程同步
        var initLatch: CountDownLatch? = null
    }
    
    // 模块缓存
    private val modules = mutableListOf<ModuleInfo>()
    
    // 类缓存，避免重复反射加载
    private val classCache = ConcurrentHashMap<String, Class<*>>()
    
    // 接口关系缓存，避免重复检查
    private val interfaceRelationCache = ConcurrentHashMap<Pair<String, String>, Boolean>()
    
    // 接口继承关系缓存，优化类型兼容性检测
    private val interfaceHierarchyCache = ConcurrentHashMap<String, Set<String>>()
    
    // 线程锁映射，用于确保线程安全
    private val moduleLocks = ConcurrentHashMap<String, ReentrantReadWriteLock>()
    
    // 初始化状态
    var isInitialized = false
    var context: Any? = null
    
    // 保存扫描的包名和排除包名
    private var scanPackages = mutableListOf<String>()
    private var excludePackages = mutableListOf<String>()
    
    // 扫描结果缓存
    private val scanResultsCache = ConcurrentHashMap<String, Set<Class<*>>>()
    
    /**
     * 初始化服务加载器
     * 
     * @param context 上下文对象
     * @param packageNames 要扫描的包名列表（可选）
     * @param excludePackageNames 要排除的包名列表（可选）
     * @return 成功初始化的模块数量
     */
    @JvmOverloads
    fun init(
        context: Any, 
        packageNames: List<String>? = null,
        excludePackageNames: List<String>? = null
    ): Int {
        if (isInitialized) {
            log("HorizonServiceLoader已经初始化")
            return 0
        }
        
        this.context = context
        
        // 设置扫描包名
        packageNames?.let { scanPackages.addAll(it) }
        excludePackageNames?.let { excludePackages.addAll(it) }
        
        // 从系统属性中读取包名
        System.getProperty("horizon.modulePackages")?.split(",")?.filter { it.isNotBlank() }?.let {
            scanPackages.addAll(it)
        }
        System.getProperty("horizon.excludePackages")?.split(",")?.filter { it.isNotBlank() }?.let {
            excludePackages.addAll(it)
        }
        
        // 扫描并加载模块
        scanModules()
        
        // 按优先级排序模块
        modules.sortByDescending { it.priority }
        
        // 初始化非懒加载模块
        val eagerModules = modules.filter { !it.lazy }
        log("初始化：非懒加载模块 ${eagerModules.size}个，总共模块 ${modules.size}个")
        
        var successCount = 0
        eagerModules.forEach { moduleInfo ->
            try {
                ensureModuleInstance(moduleInfo)
                if (moduleInfo.state == ModuleState.INITIALIZED) successCount++
            } catch (e: Exception) {
                log("初始化模块${moduleInfo.className}失败: ${e.message}")
            }
        }
        
        // 清理临时状态
        modules.forEach { it.initializingDependents.clear() }
        
        isInitialized = true
        return successCount
    }
    
    /**
     * 扫描并查找带有@AutoRegisterModule注解的类
     */
    private fun scanModules() {
        val classLoader = Thread.currentThread().contextClassLoader ?: this.javaClass.classLoader
        
        try {
            // 检查缓存是否存在，提高性能
            val cacheKey = "annotatedClasses_${AutoRegisterModule::class.java.name}"
            var annotatedClasses = scanResultsCache[cacheKey]
            
            if (annotatedClasses == null) {
                // 缓存未命中，执行扫描
                val reflections = Reflection(classLoader)
                annotatedClasses = reflections.getTypesAnnotatedWith(AutoRegisterModule::class.java)
                
                // 保存到缓存
                scanResultsCache[cacheKey] = annotatedClasses
            }
            
            log("找到${annotatedClasses.size}个带@AutoRegisterModule注解的类")
            
            modules.clear()
            annotatedClasses.forEach { clazz ->
                val packageName = clazz.packageName
                
                // 过滤包名
                val shouldInclude = if (scanPackages.isEmpty()) {
                    !excludePackages.any { packageName.startsWith(it) }
                } else {
                    scanPackages.any { packageName.startsWith(it) } && 
                    !excludePackages.any { packageName.startsWith(it) }
                }
                
                if (shouldInclude) {
                    try {
                        val annotation = clazz.getAnnotation(AutoRegisterModule::class.java)
                        
                        // 获取所有接口，包括父接口
                        val allInterfaces = getAllInterfaces(clazz)
                        
                        val moduleInfo = ModuleInfo(
                            className = clazz.name,
                            desc = annotation.desc,
                            type = annotation.type,
                            author = annotation.author,
                            version = annotation.version,
                            group = annotation.group,
                            lazy = annotation.lazy,
                            interfaces = allInterfaces.map { it.name },
                            priority = annotation.priority
                        )
                        
                        // 初始化同步信号量
                        moduleInfo.initLatch = CountDownLatch(1)
                        
                        modules.add(moduleInfo)
                        log("添加模块: ${moduleInfo.className}, 类型: ${moduleInfo.type}, 优先级: ${moduleInfo.priority}")
                        
                        // 缓存接口继承层次结构
                        allInterfaces.forEach { iface ->
                            cacheInterfaceHierarchy(iface)
                        }
                    } catch (e: Exception) {
                        log("处理模块${clazz.name}时出错: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            log("扫描模块时出错: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 获取类的所有接口，包括父接口
     */
    private fun getAllInterfaces(clazz: Class<*>): Set<Class<*>> {
        val interfaces = mutableSetOf<Class<*>>()
        collectInterfaces(clazz, interfaces)
        return interfaces
    }
    
    /**
     * 递归收集接口
     */
    private fun collectInterfaces(clazz: Class<*>, interfaces: MutableSet<Class<*>>) {
        // 添加直接实现的接口
        clazz.interfaces.forEach { iface ->
            interfaces.add(iface)
            // 递归添加父接口
            collectInterfaces(iface, interfaces)
        }
        
        // 处理父类
        val superClass = clazz.superclass
        if (superClass != null && superClass != Any::class.java) {
            collectInterfaces(superClass, interfaces)
        }
    }
    
    /**
     * 缓存接口的继承层次结构
     * 新增方法，用于优化接口兼容性检查
     */
    private fun cacheInterfaceHierarchy(interfaceClass: Class<*>) {
        val interfaceName = interfaceClass.name
        if (interfaceHierarchyCache.containsKey(interfaceName)) {
            return // 已缓存，跳过
        }
        
        val superInterfaces = mutableSetOf<String>()
        // 添加自身
        superInterfaces.add(interfaceName)
        
        // 递归添加所有父接口
        collectInterfaceHierarchy(interfaceClass, superInterfaces)
        
        // 保存到缓存
        interfaceHierarchyCache[interfaceName] = superInterfaces
    }
    
    /**
     * 递归收集接口的父接口
     * 新增方法，用于构建接口层次结构缓存
     */
    private fun collectInterfaceHierarchy(interfaceClass: Class<*>, hierarchy: MutableSet<String>) {
        interfaceClass.interfaces.forEach { parentInterface ->
            hierarchy.add(parentInterface.name)
            collectInterfaceHierarchy(parentInterface, hierarchy)
        }
    }
    
    /**
     * 确保模块实例已创建，包含循环依赖检测
     */
    private fun ensureModuleInstance(moduleInfo: ModuleInfo, dependentPath: Set<String> = emptySet()): Boolean {
        // 检测循环依赖
        if (moduleInfo.className in dependentPath) {
            val cycle = dependentPath.joinToString(" -> ") + " -> ${moduleInfo.className}"
            val error = "检测到模块循环依赖: $cycle"
            log(error)
            moduleInfo.state = ModuleState.FAILED
            moduleInfo.error = error
            return false
        }
        
        if (moduleInfo.state == ModuleState.INITIALIZING) {
            log("模块 ${moduleInfo.className} 正在被其他线程初始化，等待完成")
            
            // 使用CountDownLatch等待初始化完成，最多等待10秒
            try {
                moduleInfo.initLatch?.await(10, TimeUnit.SECONDS)
            } catch (e: Exception) {
                log("等待模块 ${moduleInfo.className} 初始化超时")
            }
            
            return moduleInfo.state == ModuleState.INITIALIZED
        }
        
        if (moduleInfo.state == ModuleState.INITIALIZED) {
            log("模块 ${moduleInfo.className} 已经初始化，跳过")
            return true
        }
        
        // 获取模块锁，确保线程安全
        val moduleLock = getModuleLock(moduleInfo.className)
        
        return moduleLock.write {
            // 二次检查，可能在获取锁的过程中被其他线程初始化
            if (moduleInfo.state == ModuleState.INITIALIZED) return@write true
            
            // 更新初始化状态
            moduleInfo.state = ModuleState.INITIALIZING
            
            // 更新依赖路径，用于循环依赖检测
            val newDependentPath = dependentPath + moduleInfo.className
            
            // 将当前模块标记为正在初始化
            moduleInfo.initializingDependents.addAll(dependentPath)
            
            val moduleStartTime = System.currentTimeMillis()
            
            try {
                log("正在创建模块实例: ${moduleInfo.className} (${moduleInfo.desc})")
                
                // 从缓存获取Class对象
                val moduleClass = getClass(moduleInfo.className)
                
                // 创建实例
                val moduleInstance = moduleClass.getDeclaredConstructor().newInstance()
                moduleInfo.instance = moduleInstance
                moduleInfo.state = ModuleState.INITIALIZED
                val moduleEndTime = System.currentTimeMillis()
                moduleInfo.initTimeMs = moduleEndTime - moduleStartTime
                log("模块 ${moduleInfo.className} 实例创建成功，耗时${moduleInfo.initTimeMs}ms")
                
                // 通知等待的线程初始化完成
                moduleInfo.initLatch?.countDown()
                
                true
            } catch (e: Exception) {
                moduleInfo.state = ModuleState.FAILED
                moduleInfo.error = e.message ?: "Unknown error"
                log("模块 ${moduleInfo.className} 实例创建失败: ${moduleInfo.error}")
                e.printStackTrace()
                
                // 通知等待的线程初始化失败
                moduleInfo.initLatch?.countDown()
                
                false
            }
        }
    }
    
    /**
     * 获取模块锁
     */
    private fun getModuleLock(className: String): ReentrantReadWriteLock {
        return moduleLocks.computeIfAbsent(className) { ReentrantReadWriteLock() }
    }
    
    /**
     * 从缓存获取Class对象，提高反射性能
     */
    private fun getClass(className: String): Class<*> {
        return classCache.computeIfAbsent(className) {
            Class.forName(className)
        }
    }
    
    /**
     * 检查模块是否与指定接口兼容
     * 优化版本：使用缓存避免重复检查，并考虑继承关系
     */
    private fun isCompatibleWith(moduleInfo: ModuleInfo, interfaceClass: Class<*>): Boolean {
        // 生成缓存键
        val cacheKey = Pair(moduleInfo.className, interfaceClass.name)
        
        // 检查缓存
        val cachedResult = interfaceRelationCache[cacheKey]
        if (cachedResult != null) {
            return cachedResult
        }
        
        // 获取目标接口的所有父接口
        val targetInterfaceHierarchy = interfaceHierarchyCache[interfaceClass.name]
            ?: setOf(interfaceClass.name).also {
                // 接口不在缓存中，立即缓存
                cacheInterfaceHierarchy(interfaceClass)
                // 重新获取完整的层次结构
                return isCompatibleWith(moduleInfo, interfaceClass)
            }
        
        // 检查模块的接口是否有任何一个在目标接口的层次结构中
        val result = moduleInfo.interfaces.any { implementedInterface ->
            // 检查直接匹配
            if (implementedInterface == interfaceClass.name) {
                return@any true
            }
            
            // 检查实现接口是否是目标接口的子类型
            val implementedInterfaceHierarchy = interfaceHierarchyCache[implementedInterface]
            if (implementedInterfaceHierarchy != null) {
                // 如果实现接口的层次结构包含目标接口，则兼容
                return@any targetInterfaceHierarchy.any { it in implementedInterfaceHierarchy }
            }
            
            // 缓存中没有，回退到标准检查
            try {
                val implClass = getClass(implementedInterface)
                interfaceClass.isAssignableFrom(implClass)
            } catch (e: Exception) {
                false
            }
        }
        
        // 保存到缓存
        interfaceRelationCache[cacheKey] = result
        
        return result
    }
    
    /**
     * 获取特定类型服务的所有实现实例
     * @return 服务实现列表，如果未找到则返回空列表
     */
    fun <T> load(clazz: Class<T>): List<T> {
        checkInitialized()
        
        val compatibleModules = modules.filter { moduleInfo -> 
            isCompatibleWith(moduleInfo, clazz)
        }
        
        if (compatibleModules.isEmpty()) {
            log("警告: 未找到实现接口 ${clazz.name} 的服务模块")
            return emptyList()
        }
        
        // 确保所有模块都已初始化
        val initializedModules = mutableListOf<ModuleInfo>()
        compatibleModules.forEach { moduleInfo ->
            if (moduleInfo.state == ModuleState.PENDING || moduleInfo.state == ModuleState.FAILED) {
                if (ensureModuleInstance(moduleInfo)) {
                    initializedModules.add(moduleInfo)
                }
            } else if (moduleInfo.state == ModuleState.INITIALIZED) {
                initializedModules.add(moduleInfo)
            }
        }
        
        @Suppress("UNCHECKED_CAST")
        return initializedModules.mapNotNull { it.instance as? T }
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
        
        // 注意：过滤后按优先级排序，确保返回优先级最高的实现
        val compatibleModules = modules
            .filter { moduleInfo -> isCompatibleWith(moduleInfo, clazz) }
            .sortedByDescending { it.priority }
        
        if (compatibleModules.isEmpty()) {
            log("警告: 未找到实现接口 ${clazz.name} 的服务模块")
            return null
        }
        
        val moduleInfo = compatibleModules.first()
        if (moduleInfo.state == ModuleState.PENDING || moduleInfo.state == ModuleState.FAILED) {
            if (!ensureModuleInstance(moduleInfo)) {
                return null
            }
        }
        
        @Suppress("UNCHECKED_CAST")
        return moduleInfo.instance as? T
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
        
        val filteredModules = modules
            .filter { it.type == type }
            .filter { moduleInfo -> isCompatibleWith(moduleInfo, clazz) }
            .sortedByDescending { it.priority } // 按优先级排序
            
        if (filteredModules.isEmpty()) {
            log("警告: 未找到类型为 $type 且实现接口 ${clazz.name} 的服务模块")
            return emptyList()
        }
        
        // 确保所有模块都已初始化
        val initializedModules = mutableListOf<ModuleInfo>()
        filteredModules.forEach { moduleInfo ->
            if (moduleInfo.state == ModuleState.PENDING || moduleInfo.state == ModuleState.FAILED) {
                if (ensureModuleInstance(moduleInfo)) {
                    initializedModules.add(moduleInfo)
                }
            } else if (moduleInfo.state == ModuleState.INITIALIZED) {
                initializedModules.add(moduleInfo)
            }
        }
        
        @Suppress("UNCHECKED_CAST")
        return initializedModules.mapNotNull { it.instance as? T }
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
        
        val filteredModules = modules
            .filter { it.group == group }
            .filter { moduleInfo -> isCompatibleWith(moduleInfo, clazz) }
            .sortedByDescending { it.priority } // 按优先级排序
            
        if (filteredModules.isEmpty()) {
            log("警告: 未找到分组为 $group 且实现接口 ${clazz.name} 的服务模块")
            return emptyList()
        }
        
        // 确保所有模块都已初始化
        val initializedModules = mutableListOf<ModuleInfo>()
        filteredModules.forEach { moduleInfo ->
            if (moduleInfo.state == ModuleState.PENDING || moduleInfo.state == ModuleState.FAILED) {
                if (ensureModuleInstance(moduleInfo)) {
                    initializedModules.add(moduleInfo)
                }
            } else if (moduleInfo.state == ModuleState.INITIALIZED) {
                initializedModules.add(moduleInfo)
            }
        }
        
        @Suppress("UNCHECKED_CAST")
        return initializedModules.mapNotNull { it.instance as? T }
    }
    
    /**
     * 按分组筛选获取服务实例（使用内联reified方式）
     */
    inline fun <reified T> loadByGroup(group: String): List<T> {
        return loadByGroup(T::class.java, group)
    }
    
    /**
     * 获取模块实例
     * @param className 模块完整类名
     * @return 模块实例，如果未找到则返回null
     */
    fun <T> getInstance(className: String): T? {
        checkInitialized()
        
        val moduleInfo = modules.find { it.className == className }
        if (moduleInfo != null) {
            if (moduleInfo.state == ModuleState.PENDING || moduleInfo.state == ModuleState.FAILED) {
                if (!ensureModuleInstance(moduleInfo)) {
                    return null
                }
            }
            
            if (moduleInfo.state == ModuleState.INITIALIZED && moduleInfo.instance != null) {
                @Suppress("UNCHECKED_CAST")
                return moduleInfo.instance as? T
            }
        }
        
        return null
    }
    
    /**
     * 获取所有模块信息
     */
    fun getAllModules(): List<ModuleInfo> {
        return modules.toList()
    }
    
    /**
     * 按类型获取模块信息
     */
    fun getModulesByType(type: String): List<ModuleInfo> {
        return modules.filter { it.type == type }
    }
    
    /**
     * 按分组获取模块信息
     */
    fun getModulesByGroup(group: String): List<ModuleInfo> {
        return modules.filter { it.group == group }
    }
    
    /**
     * 获取已初始化的模块信息
     */
    fun getInitializedModules(): List<ModuleInfo> {
        return modules.filter { it.state == ModuleState.INITIALIZED }
    }
    
    /**
     * 获取初始化失败的模块信息
     */
    fun getFailedModules(): List<ModuleInfo> {
        return modules.filter { it.state == ModuleState.FAILED }
    }
    
    /**
     * 清理缓存，释放内存
     */
    fun clearCaches() {
        classCache.clear()
        interfaceRelationCache.clear()
        interfaceHierarchyCache.clear()
        scanResultsCache.clear()
    }
    
    /**
     * 检查是否已初始化
     */
    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("HorizonServiceLoader尚未初始化，请先调用init()方法")
        }
    }
    
    /**
     * 记录日志
     */
    private fun log(message: String) {
        println("[HorizonServiceLoader] $message")
        try {
            // 尝试使用插件的日志系统（如果可用）
            val loggerClass = Class.forName("com.neil.plugin.logger.PluginLogger")
            val infoMethod = loggerClass.getMethod("info", String::class.java)
            infoMethod.invoke(null, message)
        } catch (e: Exception) {
            // 忽略错误，已经通过println输出
        }
    }
    
    /**
     * 简单的Reflection工具类，用于扫描带有指定注解的类
     * 优化版本：更高效的类扫描算法
     */
    private class Reflection(private val classLoader: ClassLoader) {
        // 缓存已扫描过的目录/JAR文件，避免重复扫描
        private val scannedPaths = mutableSetOf<String>()
        
        fun getTypesAnnotatedWith(annotation: Class<out Annotation>): Set<Class<*>> {
            val result = mutableSetOf<Class<*>>()
            
            try {
                // 使用并发处理提高性能
                val classPath = System.getProperty("java.class.path")
                val pathSeparator = System.getProperty("path.separator")
                val classPathEntries = classPath.split(pathSeparator)
                    .filter { it.isNotBlank() }
                    .map { File(it) }
                    .filter { it.exists() }
                
                // 并行扫描提高性能
                classPathEntries.parallelStream().forEach { entry ->
                    // 跳过已扫描的路径
                    val path = entry.absolutePath
                    if (path !in scannedPaths) {
                        scannedPaths.add(path)
                        
                        when {
                            entry.isDirectory -> scanDirectory(entry, "", result, annotation)
                            entry.name.endsWith(".jar") || entry.name.endsWith(".zip") -> scanJarFile(entry, result, annotation)
                        }
                    }
                }
            } catch (e: Exception) {
                println("扫描类型失败: ${e.message}")
                e.printStackTrace()
            }
            
            return result
        }
        
        private fun scanDirectory(
            directory: File,
            packageName: String,
            result: MutableSet<Class<*>>,
            annotation: Class<out Annotation>
        ) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanDirectory(
                        file,
                        if (packageName.isEmpty()) file.name else "$packageName.${file.name}",
                        result,
                        annotation
                    )
                } else if (file.name.endsWith(".class")) {
                    val className = if (packageName.isEmpty()) {
                        file.name.substringBeforeLast(".class")
                    } else {
                        "$packageName.${file.name.substringBeforeLast(".class")}"
                    }
                    
                    try {
                        val clazz = Class.forName(className, false, classLoader)
                        if (clazz.isAnnotationPresent(annotation)) {
                            synchronized(result) {
                                result.add(clazz)
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略类加载错误
                    }
                }
            }
        }
        
        private fun scanJarFile(
            jarFile: File,
            result: MutableSet<Class<*>>,
            annotation: Class<out Annotation>
        ) {
            try {
                java.util.jar.JarFile(jarFile).use { jar ->
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory && entry.name.endsWith(".class")) {
                            val className = entry.name
                                .replace('/', '.')
                                .substringBeforeLast(".class")
                            
                            try {
                                val clazz = Class.forName(className, false, classLoader)
                                if (clazz.isAnnotationPresent(annotation)) {
                                    synchronized(result) {
                                        result.add(clazz)
                                    }
                                }
                            } catch (e: Exception) {
                                // 忽略类加载错误
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("扫描JAR文件失败: ${e.message}")
            }
        }
    }
} 