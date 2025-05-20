// 作者：Redamancy  时间：2025-05-20
package com.neil.plugin.autoregister.processor
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.writeTo
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.jvm.*
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.neil.plugin.logger.PluginLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * KSP处理器：扫描@AutoRegisterModule并生成注册代码，支持注解参数收集
 * 作者：Redamancy  时间：2025-05-20
 */
class HorizonModuleProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        // 读取DSL参数
        val modulePackages = environment.options["horizon.modulePackages"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val excludePackages = environment.options["horizon.excludePackages"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val registerClassName = environment.options["horizon.registerClassName"] ?: "ModuleRegistry"
        val outputDir = environment.options["horizon.outputDir"] ?: "build/generated/horizon"
        val generatedPackage = environment.options["horizon.generatedPackage"] ?: "com.neil.plugin.autoregister"
        val baseModelClass = environment.options["horizon.baseModelClass"] ?: "com.neil.horizon.core.BaseModel"
        val autoInitEnabled = environment.options["horizon.autoInitEnabled"]?.toBoolean() ?: true
        
        PluginLogger.info("KSP参数：modulePackages=$modulePackages, excludePackages=$excludePackages, registerClassName=$registerClassName, outputDir=$outputDir, generatedPackage=$generatedPackage, baseModelClass=$baseModelClass, autoInitEnabled=$autoInitEnabled")
        val annotationName = "com.neil.plugin.autoregister.AutoRegisterModule"
        val symbols = resolver.getSymbolsWithAnnotation(annotationName)

        // 自动收集包名逻辑
        val scanPackages: Set<String> = if (modulePackages.isEmpty()) {
            val detected = symbols.filterIsInstance<KSClassDeclaration>()
                .map { it.packageName.asString() }
                .toSet()
            if (detected.isNotEmpty()) {
                PluginLogger.warn("[HorizonPlugin] 未配置 modulePackages，自动扫描到以下包名：")
                detected.forEach { PluginLogger.warn(" - $it") }
                // 打印所有被注解的实现类
                val detectedClasses = symbols.filterIsInstance<KSClassDeclaration>()
                    .mapNotNull { it.qualifiedName?.asString() }
                    .toSet()
                PluginLogger.warn("[HorizonPlugin] 自动扫描到以下被@AutoRegisterModule标记的实现类：")
                detectedClasses.forEach { PluginLogger.warn("   -> $it") }
                PluginLogger.warn("建议将上述包名补充到 horizon DSL 的 modulePackages 配置中以提升性能和可控性。")
            }
            detected
        } else {
            modulePackages.toSet()
        }

        val modules = symbols.filterIsInstance<KSClassDeclaration>()
            .filter { clazz ->
                val pkg = clazz.packageName.asString()
                (scanPackages.isEmpty() || scanPackages.any { pkg.startsWith(it) }) &&
                (excludePackages.isEmpty() || excludePackages.none { pkg.startsWith(it) })
            }
            .mapNotNull { clazz ->
            try {
                val annotation = clazz.annotations.firstOrNull { it.shortName.asString() == "AutoRegisterModule" }
                val desc = annotation?.arguments?.find { it.name?.asString() == "desc" }?.value as? String ?: ""
                val type = annotation?.arguments?.find { it.name?.asString() == "type" }?.value as? String ?: "default"
                val author = annotation?.arguments?.find { it.name?.asString() == "author" }?.value as? String ?: ""
                val version = annotation?.arguments?.find { it.name?.asString() == "version" }?.value as? String ?: "1.0.0"
                val group = annotation?.arguments?.find { it.name?.asString() == "group" }?.value as? String ?: ""
                val qualifiedName = clazz.qualifiedName?.asString() ?: return@mapNotNull null
                val lazy = annotation?.arguments?.find { it.name?.asString() == "lazy" }?.value as? Boolean ?: false
                
                // 收集模块实现的接口
                val interfaces = mutableListOf<String>()
                clazz.superTypes.forEach { superType ->
                    val superTypeName = superType.resolve().declaration.qualifiedName?.asString() ?: return@forEach
                    if (superTypeName != "kotlin.Any" && superTypeName != baseModelClass) {
                        interfaces.add(superTypeName)
                    }
                }
                
                PluginLogger.info("发现模块: $qualifiedName, desc=$desc, type=$type, author=$author, version=$version, group=$group, lazy=$lazy, 实现接口: ${interfaces.joinToString()}")
                ModuleInfo(qualifiedName, desc, type, author, version, group, lazy, interfaces)
            } catch (e: Exception) {
                PluginLogger.warn("解析@AutoRegisterModule失败: ${clazz.simpleName.asString()}，原因: ${e.message}")
                null
            }
        }.toList()

        if (modules.isNotEmpty()) {
            PluginLogger.info("正在生成自动注册类: $registerClassName, 共${modules.size}个模块")
            
            // 生成ModuleState枚举类
            val moduleStateEnum = TypeSpec.enumBuilder("ModuleState")
                .addEnumConstant("PENDING")
                .addEnumConstant("INITIALIZING")
                .addEnumConstant("INITIALIZED")
                .addEnumConstant("FAILED")
                .build()
            
            // 生成InstantiationPolicy枚举类
            val instantiationPolicyEnum = TypeSpec.enumBuilder("InstantiationPolicy")
                .addEnumConstant("EAGER")   // 立即创建实例
                .addEnumConstant("LAZY")    // 延迟到首次使用时创建
                .build()
            
            // 生成数据类
            val moduleInfoClass = TypeSpec.classBuilder("ModuleInfo")
                .addModifiers(KModifier.DATA)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("className", String::class)
                        .addParameter("desc", String::class)
                        .addParameter("type", String::class)
                        .addParameter("author", String::class)
                        .addParameter("version", String::class)
                        .addParameter("group", String::class)
                        .addParameter("lazy", Boolean::class)
                        .addParameter(
                            "interfaces", 
                            List::class.asClassName().parameterizedBy(String::class.asClassName())
                        )
                        .build()
                )
                .addProperty(PropertySpec.builder("className", String::class).initializer("className").build())
                .addProperty(PropertySpec.builder("desc", String::class).initializer("desc").build())
                .addProperty(PropertySpec.builder("type", String::class).initializer("type").build())
                .addProperty(PropertySpec.builder("author", String::class).initializer("author").build())
                .addProperty(PropertySpec.builder("version", String::class).initializer("version").build())
                .addProperty(PropertySpec.builder("group", String::class).initializer("group").build())
                .addProperty(PropertySpec.builder("lazy", Boolean::class).initializer("lazy").build())
                .addProperty(
                    PropertySpec.builder(
                        "interfaces", 
                        List::class.asClassName().parameterizedBy(String::class.asClassName())
                    )
                    .initializer("interfaces")
                    .build()
                )
                .addProperty(PropertySpec.builder("state", ClassName(generatedPackage, "$registerClassName.ModuleState"))
                    .mutable(true)
                    .initializer("ModuleState.PENDING")
                    .build()
                )
                .addProperty(PropertySpec.builder("instance", ANY.copy(nullable = true))
                    .mutable(true)
                    .initializer("null")
                    .build()
                )
                .addProperty(PropertySpec.builder("error", String::class.asTypeName().copy(nullable = true))
                    .mutable(true)
                    .initializer("null")
                    .build()
                )
                .addProperty(PropertySpec.builder("initTimeMs", LONG)
                    .mutable(true)
                    .initializer("0L")
                    .build()
                )
                .addProperty(PropertySpec.builder("instantiationPolicy", ClassName(generatedPackage, "$registerClassName.InstantiationPolicy"))
                    .initializer("if (lazy) InstantiationPolicy.LAZY else InstantiationPolicy.EAGER")
                    .build()
                )
                .build()
            
            // 生成ServiceProvider帮助类 - 增强的泛型支持和错误处理
            val serviceProviderClass = TypeSpec.objectBuilder("ServiceProvider")
                .addKdoc(
                    """
                    提供类型安全的服务访问接口，类似Java的ServiceLoader
                    使用示例:
                    ```
                    // 获取实现MyService接口的第一个服务
                    val service = ServiceProvider.get<MyService>()
                    
                    // 获取所有实现MyService接口的服务
                    val allServices = ServiceProvider.getAll<MyService>()
                    
                    // 按类型获取服务
                    val loggers = ServiceProvider.getByType<Logger>("logging")
                    ```
                    """.trimIndent()
                )
                .addFunction(
                    FunSpec.builder("get")
                        .addModifiers(KModifier.INLINE)
                        .addTypeVariable(TypeVariableName("reified T"))
                        .returns(TypeVariableName("T").copy(nullable = true))
                        .addKdoc("获取实现指定接口T的第一个服务实例，如果有多个实现则返回第一个。\n@return 服务实例，如果未找到则返回null")
                        .addCode(CodeBlock.builder()
                            .addStatement("val modules = getModulesByInterface(T::class.java)")
                            .beginControlFlow("if (modules.isEmpty())")
                            .addStatement("log(\"警告: 未找到实现接口 \${T::class.java.name} 的服务模块\")")
                            .addStatement("return null")
                            .endControlFlow()
                            .add("\n")
                            .addStatement("val moduleInfo = modules.first()")
                            .beginControlFlow("if (moduleInfo.state == ModuleState.PENDING || moduleInfo.state == ModuleState.FAILED)")
                            .addStatement("// 懒加载模式，首次使用时创建实例")
                            .addStatement("ensureModuleInstance(moduleInfo)")
                            .endControlFlow()
                            .add("\n")
                            .addStatement("@Suppress(\"UNCHECKED_CAST\")")
                            .addStatement("return moduleInfo.instance as? T")
                            .build()
                        )
                        .build()
                )
                .addFunction(
                    FunSpec.builder("getAll")
                        .addModifiers(KModifier.INLINE)
                        .addTypeVariable(TypeVariableName("reified T"))
                        .returns(List::class.asClassName().parameterizedBy(TypeVariableName("T")))
                        .addKdoc("获取所有实现指定接口T的服务实例\n@return 服务实例列表，如果未找到则返回空列表")
                        .addCode(CodeBlock.builder()
                            .addStatement("val modules = getModulesByInterface(T::class.java)")
                            .beginControlFlow("if (modules.isEmpty())")
                            .addStatement("log(\"警告: 未找到实现接口 \${T::class.java.name} 的服务模块\")")
                            .addStatement("return emptyList()")
                            .endControlFlow()
                            .add("\n")
                            .addStatement("// 确保所有模块都已初始化")
                            .addStatement("modules.forEach { moduleInfo ->")
                            .addStatement("    if (moduleInfo.state == ModuleState.PENDING || moduleInfo.state == ModuleState.FAILED) {")
                            .addStatement("        ensureModuleInstance(moduleInfo)")
                            .addStatement("    }")
                            .addStatement("}")
                            .add("\n")
                            .addStatement("@Suppress(\"UNCHECKED_CAST\")")
                            .addStatement("return modules.mapNotNull { it.instance as? T }")
                            .build()
                        )
                        .build()
                )
                .addFunction(
                    FunSpec.builder("getByType")
                        .addModifiers(KModifier.INLINE)
                        .addTypeVariable(TypeVariableName("reified T"))
                        .addParameter("type", String::class)
                        .returns(List::class.asClassName().parameterizedBy(TypeVariableName("T")))
                        .addKdoc("获取特定类型并实现指定接口T的所有服务实例\n@param type 模块类型\n@return 服务实例列表")
                        .addCode(CodeBlock.builder()
                            .addStatement("val modules = getModulesByType(type).filter { moduleInfo ->")
                            .addStatement("    isCompatibleWith(moduleInfo, T::class.java)")
                            .addStatement("}")
                            .add("\n")
                            .beginControlFlow("if (modules.isEmpty())")
                            .addStatement("log(\"警告: 未找到类型为 \$type 且实现接口 \${T::class.java.name} 的服务模块\")")
                            .addStatement("return emptyList()")
                            .endControlFlow()
                            .add("\n")
                            .addStatement("// 确保所有模块都已初始化")
                            .addStatement("modules.forEach { moduleInfo ->")
                            .addStatement("    if (moduleInfo.state == ModuleState.PENDING || moduleInfo.state == ModuleState.FAILED) {")
                            .addStatement("        ensureModuleInstance(moduleInfo)")
                            .addStatement("    }")
                            .addStatement("}")
                            .add("\n")
                            .addStatement("@Suppress(\"UNCHECKED_CAST\")")
                            .addStatement("return modules.mapNotNull { it.instance as? T }")
                            .build()
                        )
                        .build()
                )
                .addFunction(
                    FunSpec.builder("getByGroup")
                        .addModifiers(KModifier.INLINE)
                        .addTypeVariable(TypeVariableName("reified T"))
                        .addParameter("group", String::class)
                        .returns(List::class.asClassName().parameterizedBy(TypeVariableName("T")))
                        .addKdoc("获取特定分组并实现指定接口T的所有服务实例\n@param group 模块分组\n@return 服务实例列表")
                        .addCode(CodeBlock.builder()
                            .addStatement("val modules = getModulesByGroup(group).filter { moduleInfo ->")
                            .addStatement("    isCompatibleWith(moduleInfo, T::class.java)")
                            .addStatement("}")
                            .add("\n")
                            .beginControlFlow("if (modules.isEmpty())")
                            .addStatement("log(\"警告: 未找到分组为 \$group 且实现接口 \${T::class.java.name} 的服务模块\")")
                            .addStatement("return emptyList()")
                            .endControlFlow()
                            .add("\n")
                            .addStatement("// 确保所有模块都已初始化")
                            .addStatement("modules.forEach { moduleInfo ->")
                            .addStatement("    if (moduleInfo.state == ModuleState.PENDING || moduleInfo.state == ModuleState.FAILED) {")
                            .addStatement("        ensureModuleInstance(moduleInfo)")
                            .addStatement("    }")
                            .addStatement("}")
                            .add("\n")
                            .addStatement("@Suppress(\"UNCHECKED_CAST\")")
                            .addStatement("return modules.mapNotNull { it.instance as? T }")
                            .build()
                        )
                        .build()
                )
                .build()
            
            // 实例管理与创建方法代码
            val ensureModuleInstanceCode = CodeBlock.builder()
                .beginControlFlow("if (moduleInfo.state == ModuleState.INITIALIZING)")
                .addStatement("    log(\"模块 \${moduleInfo.className} 正在被其他线程初始化，等待完成\")")
                .addStatement("    // 等待其他线程完成初始化")
                .addStatement("    while (moduleInfo.state == ModuleState.INITIALIZING) {")
                .addStatement("        Thread.sleep(10)")
                .addStatement("    }")
                .addStatement("    return")
                .endControlFlow()
                .add("\n")
                .beginControlFlow("if (moduleInfo.state == ModuleState.INITIALIZED)")
                .addStatement("    log(\"模块 \${moduleInfo.className} 已经初始化，跳过\")")
                .addStatement("    return")
                .endControlFlow()
                .add("\n")
                .addStatement("// 获取模块锁，确保线程安全")
                .addStatement("val moduleLock = getModuleLock(moduleInfo.className)")
                .add("\n")
                .beginControlFlow("moduleLock.write {")
                .addStatement("    // 二次检查，可能在获取锁的过程中被其他线程初始化")
                .beginControlFlow("    if (moduleInfo.state == ModuleState.INITIALIZED)")
                .addStatement("        return")
                .endControlFlow()
                .add("\n")
                .addStatement("    moduleInfo.state = ModuleState.INITIALIZING")
                .addStatement("    val moduleStartTime = System.currentTimeMillis()")
                .add("\n")
                .beginControlFlow("    try")
                .addStatement("        log(\"正在创建模块实例: \${moduleInfo.className} (\${moduleInfo.desc})\")")
                .addStatement("        val moduleClass = Class.forName(moduleInfo.className)")
                .addStatement("        val moduleInstance = moduleClass.getDeclaredConstructor().newInstance()")
                .addStatement("        moduleInfo.instance = moduleInstance")
                .addStatement("        moduleInfo.state = ModuleState.INITIALIZED")
                .addStatement("        val moduleEndTime = System.currentTimeMillis()")
                .addStatement("        moduleInfo.initTimeMs = moduleEndTime - moduleStartTime")
                .addStatement("        log(\"模块 \${moduleInfo.className} 实例创建成功，耗时\${moduleInfo.initTimeMs}ms\")")
                .endControlFlow()
                .beginControlFlow("    catch (e: Exception)")
                .addStatement("        moduleInfo.state = ModuleState.FAILED")
                .addStatement("        moduleInfo.error = e.message ?: \"Unknown error\"")
                .addStatement("        log(\"模块 \${moduleInfo.className} 实例创建失败: \${moduleInfo.error}\")")
                .addStatement("        e.printStackTrace()")
                .endControlFlow()
                .endControlFlow()
                .build()
            
            // 类型兼容性检查方法
            val isCompatibleWithCode = CodeBlock.builder()
                .addStatement("return moduleInfo.interfaces.any { interfaceName ->")
                .addStatement("    interfaceName == interfaceClass.name ||")
                .addStatement("    try {")
                .addStatement("        val theInterface = Class.forName(interfaceName)")
                .addStatement("        interfaceClass.isAssignableFrom(theInterface)")
                .addStatement("    } catch (e: Exception) {")
                .addStatement("        false")
                .addStatement("    }")
                .addStatement("}")
                .build()
            
            // 生成getModulesByInterface方法代码
            val getModulesByInterfaceCode = CodeBlock.builder()
                .addStatement("return modules.filter { moduleInfo ->")
                .addStatement("    isCompatibleWith(moduleInfo, interfaceClass)")
                .addStatement("}")
                .build()
                
            // 生成日志方法
            val logMethodCode = CodeBlock.builder()
                .addStatement("println(\"[HorizonModuleRegistry] \$message\")")
                .addStatement("try {")
                .addStatement("    // 尝试使用插件的日志系统（如果可用）")
                .addStatement("    val loggerClass = Class.forName(\"com.neil.plugin.logger.PluginLogger\")")
                .addStatement("    val infoMethod = loggerClass.getMethod(\"info\", String::class.java)")
                .addStatement("    infoMethod.invoke(null, message)")
                .addStatement("} catch (e: Exception) {")
                .addStatement("    // 忽略错误，已经通过println输出")
                .addStatement("}")
                .build()
                
            // 生成object和模块列表
            val registry = TypeSpec.objectBuilder(registerClassName)
                .addKdoc(
                    """
                    自动注册的模块注册表，提供类似Java ServiceLoader的功能。
                    支持通过接口类型获取实现，支持懒加载和自定义初始化。
                    
                    基本用法:
                    1. 标记类型: `@AutoRegisterModule`
                    2. 获取实例: `${registerClassName}.ServiceProvider.get<YourInterface>()`
                    
                    作者：Redamancy  时间：2025-05-20
                    """.trimIndent()
                )
                .addType(moduleStateEnum)
                .addType(instantiationPolicyEnum)
                .addType(moduleInfoClass)
                .addType(serviceProviderClass)
                .addProperty(
                    PropertySpec.builder("modules",
                        List::class.asClassName().parameterizedBy(ClassName(generatedPackage, "$registerClassName.ModuleInfo")))
                        .initializer(
                            "listOf(${
                                modules.joinToString { 
                                    "ModuleInfo(\"${it.className}\", \"${it.desc}\", \"${it.type}\", \"${it.author}\", \"${it.version}\", \"${it.group}\", ${it.lazy}, listOf(${it.interfaces.joinToString { "\"$it\"" }}))" 
                                }
                            })"
                        )
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("moduleLocks", 
                        ClassName("java.util.concurrent", "ConcurrentHashMap")
                            .parameterizedBy(
                                String::class.asClassName(),
                                ClassName("java.util.concurrent.locks", "ReentrantReadWriteLock")
                            )
                    )
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("ConcurrentHashMap<String, ReentrantReadWriteLock>()")
                    .build()
                )
                .addProperty(
                    PropertySpec.builder("isInitialized", Boolean::class)
                        .mutable(true)
                        .addModifiers(KModifier.PRIVATE)
                        .initializer("false")
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("initContext", ANY.copy(nullable = true))
                        .mutable(true)
                        .addModifiers(KModifier.PRIVATE)
                        .initializer("null")
                        .build()
                )
                .addFunction(
                    FunSpec.builder("getModuleLock")
                        .addModifiers(KModifier.PRIVATE)
                        .addParameter("className", String::class)
                        .returns(ClassName("java.util.concurrent.locks", "ReentrantReadWriteLock"))
                        .addCode(
                            CodeBlock.builder()
                                .addStatement("return moduleLocks.computeIfAbsent(className) { ReentrantReadWriteLock() }")
                                .build()
                        )
                        .build()
                )
                .addFunction(
                    FunSpec.builder("init")
                        .addParameter("context", ANY.copy(nullable = true))
                        .returns(INT)
                        .addKdoc(
                            """
                            初始化模块注册表
                            注意：此方法不会自动初始化所有模块，仅设置上下文并初始化非懒加载的模块
                            @param context 初始化上下文对象
                            @return 成功初始化的模块数量
                            """.trimIndent()
                        )
                        .addCode(
                            CodeBlock.builder()
                                .addStatement("isInitialized = true")
                                .addStatement("initContext = context")
                                .addStatement("// 仅初始化非懒加载模块")
                                .addStatement("val eagerModules = modules.filter { !it.lazy }")
                                .addStatement("log(\"初始化：非懒加载模块 \${eagerModules.size}个，总共模块 \${modules.size}个\")")
                                .addStatement("var successCount = 0")
                                .add("\n")
                                .addStatement("eagerModules.forEach { moduleInfo ->")
                                .addStatement("    ensureModuleInstance(moduleInfo)")
                                .addStatement("    if (moduleInfo.state == ModuleState.INITIALIZED) successCount++")
                                .addStatement("}")
                                .add("\n")
                                .addStatement("return successCount")
                                .build()
                        )
                        .build()
                )
                .addFunction(
                    FunSpec.builder("ensureModuleInstance")
                        .addModifiers(KModifier.PRIVATE)
                        .addParameter("moduleInfo", ClassName(generatedPackage, "$registerClassName.ModuleInfo"))
                        .addCode(ensureModuleInstanceCode)
                        .build()
                )
                .addFunction(
                    FunSpec.builder("isCompatibleWith")
                        .addModifiers(KModifier.PRIVATE)
                        .addParameter("moduleInfo", ClassName(generatedPackage, "$registerClassName.ModuleInfo"))
                        .addParameter("interfaceClass", Class::class.asClassName().parameterizedBy(STAR))
                        .returns(Boolean::class)
                        .addCode(isCompatibleWithCode)
                        .build()
                )
                .addFunction(
                    FunSpec.builder("getModulesByType")
                        .addParameter("type", String::class)
                        .returns(List::class.asClassName().parameterizedBy(ClassName(generatedPackage, "$registerClassName.ModuleInfo")))
                        .addCode("return modules.filter { it.type == type }")
                        .build()
                )
                .addFunction(
                    FunSpec.builder("getModulesByGroup")
                        .addParameter("group", String::class)
                        .returns(List::class.asClassName().parameterizedBy(ClassName(generatedPackage, "$registerClassName.ModuleInfo")))
                        .addCode("return modules.filter { it.group == group }")
                        .build()
                )
                .addFunction(
                    FunSpec.builder("getModulesByInterface")
                        .addParameter("interfaceClass", Class::class.asClassName().parameterizedBy(STAR))
                        .returns(List::class.asClassName().parameterizedBy(ClassName(generatedPackage, "$registerClassName.ModuleInfo")))
                        .addCode(getModulesByInterfaceCode)
                        .build()
                )
                .addFunction(
                    FunSpec.builder("getInitializedModules")
                        .returns(List::class.asClassName().parameterizedBy(ClassName(generatedPackage, "$registerClassName.ModuleInfo")))
                        .addCode("return modules.filter { it.state == ModuleState.INITIALIZED }")
                        .build()
                )
                .addFunction(
                    FunSpec.builder("getFailedModules")
                        .returns(List::class.asClassName().parameterizedBy(ClassName(generatedPackage, "$registerClassName.ModuleInfo")))
                        .addCode("return modules.filter { it.state == ModuleState.FAILED }")
                        .build()
                )
                .addFunction(
                    FunSpec.builder("getInstance")
                        .addTypeVariable(TypeVariableName("T"))
                        .addParameter("className", String::class)
                        .returns(TypeVariableName("T").copy(nullable = true))
                        .addCode(
                            CodeBlock.builder()
                                .addStatement("val module = modules.find { it.className == className }")
                                .beginControlFlow("if (module != null)")
                                .beginControlFlow("    if (module.state == ModuleState.PENDING || module.state == ModuleState.FAILED)")
                                .addStatement("        ensureModuleInstance(module)")
                                .endControlFlow()
                                .beginControlFlow("    if (module.state == ModuleState.INITIALIZED && module.instance != null)")
                                .addStatement("        @Suppress(\"UNCHECKED_CAST\")")
                                .addStatement("        return module.instance as T")
                                .endControlFlow()
                                .endControlFlow()
                                .addStatement("return null")
                                .build()
                        )
                        .build()
                )
                .addFunction(
                    FunSpec.builder("log")
                        .addModifiers(KModifier.PRIVATE)
                        .addParameter("message", String::class)
                        .addCode(logMethodCode)
                        .build()
                )
                .build()
                
            val fileSpec = FileSpec.builder(generatedPackage, registerClassName)
                .addImport("kotlin.reflect", "*")
                .addImport("java.util.concurrent", "ConcurrentHashMap")
                .addImport("java.util.concurrent.locks", "ReentrantReadWriteLock")
                .addImport("kotlin.concurrent", "read", "write")
                .addType(registry)
                .build()
                
            val codeGenerator = environment.codeGenerator
            fileSpec.writeTo(codeGenerator, Dependencies(false))
            PluginLogger.info("自动注册类生成完成: $registerClassName")
        } else {
            PluginLogger.warn("未发现@AutoRegisterModule标记的类，本次不生成注册代码")
        }
        return emptyList()
    }

    data class ModuleInfo(
        val className: String, 
        val desc: String, 
        val type: String, 
        val author: String, 
        val version: String, 
        val group: String,
        val lazy: Boolean = false,
        val interfaces: List<String> = emptyList()
    )
}

class HorizonModuleProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return HorizonModuleProcessor(environment)
    }
} 