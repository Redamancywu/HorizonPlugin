# HorizonSDKPlugin

## v1.0.2 更新优化

> 本次更新重点提升了资源隔离的灵活性、白名单配置能力和插件健壮性，适配更复杂的多模块/多团队协作场景。

### 1. 资源白名单 DSL 配置能力增强
- 支持在 DSL（horizon { ... }）中通过 `resourceWhiteList` 配置资源白名单：
  - 可直接写 List<String>，如：
    ```kotlin
    resourceWhiteList = listOf("drawable:ic_sdk_*", "string:keep_me", "my_third_party_*")
    ```
  - 支持指定白名单文件路径（.txt/.json），如：
    ```kotlin
    resourceWhiteList = "sdk_whitelist.txt"
    ```
  - 支持多文件合并，便于主工程和各模块分别维护：
    ```kotlin
    resourceWhiteList = listOf("sdk_whitelist.txt", "module_whitelist.txt")
    ```
  - 文件支持 .txt（每行一个规则）、.json（数组），自动去重、去空行。
- 白名单规则支持类型:通配符、通配符、精确名等多种写法，兼容第三方SDK、特殊资源名等场景。

#### 资源白名单文件示例
- sdk_whitelist.txt：
  ```
  drawable:ic_sdk_*
  string:keep_me
  my_third_party_*
  layout:activity_external
  ```
- module_whitelist.json：
  ```json
  ["color:brand_*", "string:external_*", "thirdparty_*"]
  ```

#### 资源白名单规则说明
- `drawable:ic_sdk_*`  表示跳过所有以 ic_sdk_ 开头的 drawable 资源
- `string:keep_me`    表示跳过名为 keep_me 的 string 资源
- `my_third_party_*`  表示跳过所有以 my_third_party_ 开头的任意类型资源
- `layout:activity_external` 表示跳过指定 layout 文件

---

## 详细用法说明

### 1. 插件 DSL 配置示例
```kotlin
horizon {
    enableAutoRegister = true
    modulePackages = mutableListOf("com.example.module1", "com.example.module2")
    excludePackages = mutableListOf("com.example.module1.internal")
    registerClassName = "ModuleRegistry"
    outputDir = "build/generated/horizon"
    logLevel = "INFO"
    generatedPackage = "com.neil.plugin.autoregister"
    proguardRules = mutableListOf("-keep class com.example.** { *; }")
    // 多 flavor 场景下可动态拼接前缀
    resourcePrefixPattern = "{module}_{flavor}_"
    // 动态合并主工程和各 flavor/module 的白名单
    resourceWhiteList = listOf("sdk_whitelist.txt", "module_whitelist.txt", "flavor_dev_whitelist.txt")
}
```

### 2. 多 flavor 场景下的资源隔离与自动检测
- 插件会自动检测当前构建的 flavor（如 dev、prod、release 等），并将 flavor 名拼接到资源前缀，实现 flavor 级资源隔离。
- 支持多渠道包、灰度/正式环境等多 flavor 并存的复杂场景，无需手动指定 flavor，插件自动感知。
- 支持 AGP 官方 flavor 体系，兼容主流多渠道构建流程。

### 3. flavor 变量自动注入到 KSP
- 插件会在 afterEvaluate 阶段自动将当前 flavor、module、前缀等变量通过 KSP 参数注入，保证 KSP 处理器可感知 flavor 环境。
- 典型注入参数：
  - `horizon.flavorName` 当前 flavor 名称
  - `horizon.moduleName` 当前 module 名称
  - `horizon.resourcePrefix` 当前资源前缀（已拼接 flavor）
- KSP 处理器可通过 `environment.options["horizon.flavorName"]` 等方式读取，便于生成 flavor 相关代码或资源。

### 4. 白名单规则的正则优先级说明
- 白名单规则支持类型:通配符、通配符、精确名，内部优先级如下：
  1. 精确名（如 `string:keep_me`）优先级最高，完全匹配直接跳过
  2. 类型:通配符（如 `drawable:ic_sdk_*`）优先于全局通配符
  3. 全局通配符（如 `my_third_party_*`）优先级最低，适配所有类型
- 多条规则命中时，优先级高的先生效，后配置的规则可覆盖前面规则
- 建议将最严格、最具体的规则放在前面，通配符放后面

### 5. 白名单动态合并与优先级规则
- 支持在 DSL 中配置多个白名单文件，插件会自动读取并合并所有规则，去重、去空行。
- 白名单优先级：**后配置的文件/规则优先级更高**，如有冲突则后者覆盖前者。
- 支持主工程、各 module、各 flavor 分别维护独立白名单，最终动态合并为一份全局白名单。
- 典型合并顺序建议：主工程白名单 < module 白名单 < flavor 白名单。

### 6. CI 场景下的白名单/前缀动态注入与多环境切换最佳实践
- 支持通过 Gradle 属性或环境变量在 CI/CD 流程中动态注入白名单和前缀配置。
- 推荐做法：
  1. 在 CI 脚本中根据分支/环境动态指定白名单和前缀：
     ```shell
     # dev 环境
     ./gradlew assembleDev -PresourceWhiteList=flavor_dev_whitelist.txt -PresourcePrefixPattern=dev_
     # prod 环境
     ./gradlew assembleProd -PresourceWhiteList=flavor_prod_whitelist.txt -PresourcePrefixPattern=prod_
     ```
  2. 在 `build.gradle` 中读取并适配：
     ```kotlin
     horizon {
         resourceWhiteList = project.findProperty("resourceWhiteList") ?: listOf("sdk_whitelist.txt")
         resourcePrefixPattern = project.findProperty("resourcePrefixPattern") ?: "{module}_{flavor}_"
     }
     ```
  3. 支持多环境、多分支、灰度/正式等多场景灵活切换，无需改动主干代码。
- 建议将各环境白名单文件纳入版本管理，便于团队协作和审计。

### 7. 资源隔离与自动重命名流程
- 插件会自动检测每个 module 的 namespace，生成资源前缀。
- 遍历 res 目录，未按前缀命名的资源自动重命名（支持 MD5 前缀）。
- values 资源项自动加前缀或 MD5。
- 自动同步 layout、drawable、anim、color、xml 等目录下 xml 文件内容中的资源名。
- 自动同步所有 xml 文件和 Java/Kotlin 源码中的资源引用（如 R.drawable.xxx、binding.xxx），支持 ViewBinding 属性名同步（下划线转驼峰）。
- 支持 Databinding 表达式的资源同步。
- 自动导出重命名映射表（resource_rename_map.json），便于后续批量替换和溯源。
- 自动检测未被引用的资源并输出 unused_resources.txt。
- 所有源码和 xml 自动替换前自动备份，便于回滚。

### 8. Manifest 智能合并
- 各 module 可在 `src/main/manifest/fragment_模块名.xml` 维护 Manifest 片段。
- 插件自动结构化合并所有片段到主工程 `AndroidManifest.xml`，支持冲突检测与顺序控制。
- 合并时如遇同名节点，自动检测并输出详细日志。

### 9. 混淆规则自动注入
- 插件自动合并 `proguard-rules.sdk.pro` 到主工程混淆配置。
- 可在插件 DSL 追加自定义 keep 规则。
- 保证 SDK 关键类和自动注册模块不会被混淆。

### 10. 日志系统
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

## FAQ 常见问题

- **Q: 如何配置资源白名单？**
  - A: 推荐在 horizon DSL 中通过 resourceWhiteList 配置，支持直接 List、单文件、多文件，支持 .txt/.json 格式，详见上方示例。
- **Q: 白名单规则支持哪些写法？**
  - A: 支持类型:通配符、通配符、精确名，详见"资源白名单规则说明"。
- **Q: 白名单文件路径是相对哪里？**
  - A: 路径相对当前 module 的 projectDir，也支持绝对路径。
- **Q: 资源隔离/重命名会影响第三方 SDK 吗？**
  - A: 不会，所有第三方/特殊资源可通过白名单跳过，保证安全。
- **Q: 如何回滚资源自动重命名/同步？**
  - A: 所有源码和 xml 自动替换前会自动备份（.bak_resreplace/.bak_viewbindingreplace），如需回滚可手动还原。
- **Q: 如何启用资源MD5重命名？**
  - A: 在 gradle.properties 添加 `enableResourceMd5=true`，或命令行加 `-PenableResourceMd5=true`。
- **Q: 如何自定义注册类名、包名？**
  - A: 通过插件 DSL 配置 `registerClassName`、`generatedPackage`。
- **Q: Manifest 合并冲突如何处理？**
  - A: 插件会自动检测并输出详细日志，开发者可根据日志调整片段内容。
- **Q: 资源重命名映射表和未被引用资源清单在哪里？**
  - A: 默认导出到各 module 的 build 目录下（resource_rename_map.json、unused_resources.txt）。
- **Q: 支持哪些 Android Gradle Plugin 版本？**
  - A: 推荐 AGP 8.0 及以上，兼容主流多模块项目。
- **Q: flavor 是如何自动检测的？**
  - A: 插件会自动读取当前构建的 flavor 名称，无需手动指定，支持 AGP 官方 flavor 体系。
- **Q: flavor 变量如何传递给 KSP？**
  - A: 插件会自动注入 flavor、module、前缀等参数到 KSP，KSP 处理器可直接读取。
- **Q: 白名单规则优先级如何？**
  - A: 精确名 > 类型:通配符 > 全局通配符，后配置的规则优先级更高。
- **Q: CI/CD 下如何动态切换白名单和前缀？**
  - A: 可通过 Gradle 属性或环境变量注入，详见上方 CI 场景最佳实践。

---

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

## 联系作者
- 作者：Redamancy
- GitHub: [https://github.com/Redamancywu/HorizonSDKPlugin](https://github.com/Redamancywu/HorizonSDKPlugin) 