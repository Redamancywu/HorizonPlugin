# HorizonSDKPlugin

> 作者：Redamancy  时间：2025-05-19

## 插件简介

HorizonSDKPlugin 是一款专为 Android SDK 多模块开发设计的通用 Gradle 插件，聚焦于自动注册、资源隔离、混淆规则自动注入、Manifest 智能合并、日志系统等核心能力，极大提升 SDK 工程的标准化、自动化和可维护性。

---

## 主要特性
- **模块自动注册**：KSP + KotlinPoet 自动扫描注解，生成注册代码。
- **混淆规则自动注入**：自动合并 proguard 规则，支持追加自定义 keep。
- **Manifest 智能合并**：自动结构化合并多模块 Manifest 片段，冲突检测与顺序可控。
- **资源隔离与自动重命名**：自动检测并重命名资源，支持前缀/MD5，防止冲突。
- **日志系统封装**：支持日志等级、格式化、可选文件输出。

---

## 注解自动注册用法

### 1. 添加注解
在需要自动注册的类上加注解：
```kotlin
import com.neil.plugin.autoregister.AutoRegisterModule

@AutoRegisterModule(desc = "用户模块", type = "user", author = "Redamancy", version = "1.0.0", group = "core")
class UserModule {
    fun init(context: Any?) {
        // 初始化逻辑
    }
}
```
- 注解参数说明：
  - `desc`：模块描述
  - `type`：模块类型
  - `author`：作者
  - `version`：版本
  - `group`：分组
- 注解可无参数，所有参数均有默认值。

### 2. 自动生成注册表
插件会自动扫描所有被 `@AutoRegisterModule` 标记的类，生成注册表（如 `ModuleRegistry`），并支持一键初始化：
```kotlin
com.neil.plugin.autoregister.ModuleRegistry.initAll(context)
```

---

## 资源隔离与自动重命名

- 插件自动检测所有 module 的 `res/` 目录，未按命名空间前缀命名的资源会被自动重命名。
- 支持通过 `gradle.properties` 配置 `enableResourceMd5=true` 启用 MD5 重命名，进一步防止冲突。
- `values` 目录下的 xml 资源项（如 `<string>`、`<color>` 等）也会自动加前缀或加md5。

---

## 混淆规则自动注入

- 插件自动合并 `proguard-rules.sdk.pro` 到主工程混淆配置。
- 可在插件 DSL 追加自定义 keep 规则。
- 保证 SDK 关键类和自动注册模块不会被混淆。

---

## Manifest 智能合并

- 各 module 可在 `src/main/manifest/fragment_模块名.xml` 维护 Manifest 片段。
- 插件自动结构化合并所有片段到主工程 `AndroidManifest.xml`，支持冲突检测与顺序控制。
- 合并时如遇同名节点，自动检测并输出详细日志。

---

## 日志系统

- 支持日志等级（DEBUG/INFO/WARN/ERROR）、格式化输出、可选输出到文件。
- 可通过代码设置日志等级和输出文件：
```kotlin
import com.neil.plugin.logger.PluginLogger
import com.neil.plugin.logger.LogLevel

PluginLogger.logLevel = LogLevel.DEBUG
PluginLogger.logToFile = true
PluginLogger.logFile = File(project.buildDir, "horizon_plugin.log")
```
- 插件和自动注册、资源处理、合并等所有流程均有详细日志输出，便于调试和追踪。

---

## 常见问题

- **Q: 如何启用资源MD5重命名？**
  - A: 在 gradle.properties 添加 `enableResourceMd5=true`，或命令行加 `-PenableResourceMd5=true`。
- **Q: 如何自定义注册类名、包名？**
  - A: 通过插件 DSL 配置 `registerClassName`、`generatedPackage`。
- **Q: Manifest 合并冲突如何处理？**
  - A: 插件会自动检测并输出详细日志，开发者可根据日志调整片段内容。

---

## 联系作者
- 作者：Redamancy
- GitHub: [https://github.com/Redamancywu/HorizonSDKPlugin](https://github.com/Redamancywu/HorizonSDKPlugin) 