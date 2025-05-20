// 作者：Redamancy  时间：2025-05-19
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


/**
 * KSP处理器：扫描@AutoRegisterModule并生成注册代码，支持注解参数收集
 * 作者：Redamancy  时间：2025-05-19
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
        PluginLogger.info("KSP参数：modulePackages=$modulePackages, excludePackages=$excludePackages, registerClassName=$registerClassName, outputDir=$outputDir, generatedPackage=$generatedPackage")
        val annotationName = "com.neil.plugin.autoregister.AutoRegisterModule"
        val symbols = resolver.getSymbolsWithAnnotation(annotationName)
        val modules = symbols.filterIsInstance<KSClassDeclaration>()
            .filter { clazz ->
                val pkg = clazz.packageName.asString()
                (modulePackages.isEmpty() || modulePackages.any { pkg.startsWith(it) }) &&
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
                PluginLogger.info("发现模块: $qualifiedName, desc=$desc, type=$type, author=$author, version=$version, group=$group")
                ModuleInfo(qualifiedName, desc, type, author, version, group)
            } catch (e: Exception) {
                PluginLogger.warn("解析@AutoRegisterModule失败: ${clazz.simpleName.asString()}，原因: ${e.message}")
                null
            }
        }.toList()

        if (modules.isNotEmpty()) {
            PluginLogger.info("正在生成自动注册类: $registerClassName, 共${modules.size}个模块")
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
                        .build()
                )
                .addProperty(PropertySpec.builder("className", String::class).initializer("className").build())
                .addProperty(PropertySpec.builder("desc", String::class).initializer("desc").build())
                .addProperty(PropertySpec.builder("type", String::class).initializer("type").build())
                .addProperty(PropertySpec.builder("author", String::class).initializer("author").build())
                .addProperty(PropertySpec.builder("version", String::class).initializer("version").build())
                .addProperty(PropertySpec.builder("group", String::class).initializer("group").build())
                .build()
            // 生成object和模块列表
            val registry = TypeSpec.objectBuilder(registerClassName)
                .addType(moduleInfoClass)
                .addProperty(
                    PropertySpec.builder("modules",
                        List::class.asClassName().parameterizedBy(ClassName("com.neil.plugin.autoregister", "ModuleRegistry.ModuleInfo")))
                        .initializer("listOf(${modules.joinToString { "ModuleInfo(\"${it.className}\", \"${it.desc}\", \"${it.type}\", \"${it.author}\", \"${it.version}\", \"${it.group}\")" }})")
                        .build()
                )
                .addFunction(
                    FunSpec.builder("initAll")
                        .addParameter("context", ANY.copy(nullable = true))
                        .addCode(modules.joinToString("\n") { "    // 初始化模块: ${it.className}\n    // 可调用 ${it.className}().init(context)" })
                        .build()
                )
                .build()
            val fileSpec = FileSpec.builder(generatedPackage, registerClassName)
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

    data class ModuleInfo(val className: String, val desc: String, val type: String, val author: String, val version: String, val group: String)
}

class HorizonModuleProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return HorizonModuleProcessor(environment)
    }
} 