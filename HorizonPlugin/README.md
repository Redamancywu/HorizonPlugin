# HorizonSDKPlugin

## v1.0.5 更新优化

> 本次更新重构代码结构，增强代码引用分析，改进日志系统，提高插件健壮性和易用性。更多详细信息请查看 [CHANGELOG.md](CHANGELOG.md)。

### 1. 代码结构优化

- 重构插件主类，将功能拆分到专门的辅助类
- 新增 `ManifestMerger` 专门处理 Manifest 合并
- 新增 `ProguardInjector` 专门处理混淆规则注入
- 降低代码耦合度，提高可维护性和扩展性

```kotlin
// 原代码：功能都在 HorizonSDKPlugin 中
project.afterEvaluate {
    val androidExt = project.extensions.findByName("android")
    if (androidExt != null) {
        // Manifest合并代码...
    }
}

// 优化后：拆分到专门的辅助类
project.afterEvaluate {
    val androidExt = project.extensions.findByName("android")
    if (androidExt != null) {
        ManifestMerger.mergeModuleManifests(project)
    }
}
```

### 2. 代码引用分析增强

- 完善代码引用分析，支持更精确的类依赖识别
- 支持识别以下类型的引用：
  - 类型引用（变量声明、方法参数等）
  - 构造函数调用（Java和Kotlin风格）
  - 静态方法/属性调用
  - 继承和实现关系

```kotlin
// 原代码：简单识别导入
private fun extractReferences(content: String, imports: Set<String>): MutableSet<String> {
    val references = mutableSetOf<String>()
    references.addAll(imports)
    // TODO: 实现更复杂的引用分析逻辑
    return references
}

// 优化后：全面识别类引用
private fun extractReferences(content: String, imports: Set<String>): MutableSet<String> {
    val references = mutableSetOf<String>()
    references.addAll(imports)
    
    // 识别类型引用（变量声明、方法参数等）
    val typePattern = Pattern.compile(":\\s*([A-Z][\\w.]+)(?:<.*>)?")
    // 识别构造函数调用
    val constructorPattern = Pattern.compile("\\bnew\\s+([A-Z][\\w.]+)\\s*\\(")
    // 更多引用识别逻辑...
    
    return references
}
```

### 3. 日志系统增强

- 支持彩色终端输出，不同日志级别使用不同颜色
- 添加日志文件轮转功能，防止日志文件过大
- 自动创建日志目录，完善错误处理
- 提供更友好的日志配置API

```kotlin
// 日志系统使用示例
import com.neil.plugin.logger.PluginLogger
import com.neil.plugin.logger.LogLevel

// 设置日志级别
PluginLogger.logLevel = LogLevel.DEBUG

// 启用文件日志并设置轮转策略（10MB，最多5个历史文件）
PluginLogger.createDefaultLogFile(project)
PluginLogger.setRotationPolicy(10 * 1024 * 1024, 5)

// 记录日志
PluginLogger.debug("调试信息")
PluginLogger.info("普通信息")
```

### 4. 配置验证功能

- 为HorizonExtension添加配置验证功能
- 自动检测并修正无效配置
- 提供明确的错误提示信息

```kotlin
// 插件主入口自动验证配置
project.afterEvaluate {
    // 验证配置有效性
    extension.validate()
    
    // 设置日志级别
    PluginLogger.logLevel = try {
        LogLevel.valueOf(extension.logLevel.uppercase())
    } catch (e: Exception) {
        LogLevel.INFO
    }
}
```

### 5. 资源前缀模板变量增强

- 改进资源前缀模板变量处理逻辑
- 自动替换模板变量为实际值
- 支持module和flavor名称动态注入

```kotlin
horizon {
    // 生成类似 "moduleName_flavorName_" 的资源前缀
    resourcePrefixPattern = "{module}_{flavor}_"
}

// 内部实现
val prefix = if (prefixPattern != null) {
    prefixPattern
        .replace("{module}", module.name)
        .replace("{flavor}", project.findProperty("flavorName") as? String ?: "")
} else {
    namespace.split('.').last() + "_"
}
```

### 6. 初始化日志自动配置

- 插件初始化时自动配置日志输出
- 记录关键操作和配置信息
- 更容易排查问题和查看运行状态

```kotlin
PluginLogger.createDefaultLogFile(project)
PluginLogger.info("HorizonSDKPlugin 已应用，欢迎使用！")
PluginLogger.info("插件版本: 1.0.5")
PluginLogger.debug("配置信息: enableAutoRegister=${extension.enableAutoRegister}, " +
                    "enableCodeReferenceAnalysis=${extension.enableCodeReferenceAnalysis}")
```

## v1.0.4 更新优化

> 本次更新新增代码引用分析功能，支持依赖图生成和未使用类检测，优化代码结构和引用管理。

### 1. 代码引用分析功能

- 新增代码引用分析功能，支持分析模块内所有Java/Kotlin文件的引用关系
- 可视化生成依赖图(DOT格式)，便于团队了解代码结构和依赖关系
- 自动检测未使用的类，避免代码膨胀
- 支持白名单配置，灵活控制分析范围

### 2. DSL 配置示例

```kotlin
horizon {
    // 启用代码引用分析
    enableCodeReferenceAnalysis = true
    
    // 是否生成依赖图(DOT格式)
    generateDependencyGraph = true
    
    // 是否检测未使用的类
    detectUnusedClasses = true
    
    // 代码引用白名单配置(API接口、常量类等无引用但需保留的类)
    codeReferenceWhiteList = listOf(
        "com.example.api.*",      // API接口类
        "com.example.Constants",  // 常量类
        "com.example.AppEntry"    // 入口类
    )
    
    // 或使用白名单文件
    codeReferenceWhiteList = "config/code_whitelist.txt"
}
```

### 3. 依赖图示例

代码依赖图使用DOT格式生成，可用Graphviz等工具可视化：

```dot
digraph CodeDependencies {
  rankdir=LR;
  node [shape=box, style=filled, fillcolor=lightblue];

  "MainActivity" -> "UserViewModel";
  "UserViewModel" -> "UserRepository";
  "UserRepository" -> "ApiService";
  "ApiService" -> "NetworkModule";
}
```

生成的依赖图位于`build/reports/code-references/{模块名}_dependency_graph.dot`

### 4. 未使用类检测

自动检测模块内未被引用的类，生成报告文件，位于`build/reports/code-references/{模块名}_unused_classes.txt`

未使用类检测结果示例：
```
com.example.utils.UnusedHelper
com.example.legacy.OldFeature
com.example.temp.DebugUtils
```

## v1.0.3 更新优化

> 本次更新重点修复资源引用格式严重错误问题，增强资源白名单配置能力，提升多Flavor和CI/CD场景下的适配性。

### 1. 资源引用格式修复优化
- 修复XML中资源ID格式被破坏的问题（如`@sdkdemo_sdkdemo_id/main`而非正确的`@+id/sdkdemo_main`）
- 改用正则文本处理替代DOM解析，保留原有的`@+id/`、`@id/`等前缀格式
- 只替换资源名部分，保证格式规范性的同时保留原有标记
- 增强了资源引用检测的稳定性和准确性

#### 资源引用格式修复示例：
- 错误格式：`@sdkdemo_sdkdemo_id/main` → 修复为：`@+id/sdkdemo_main`
- 错误格式：`@module_drawable/icon` → 修复为：`@drawable/module_icon`

### 2. 资源白名单配置能力增强
- 支持在DSL中配置更灵活的白名单规则：
  ```kotlin
  horizon {
      // 支持直接List<String>配置规则
      resourceWhiteList = listOf(
          "drawable:ic_sdk_*",  // 类型:通配符格式
          "string:keep_me",     // 类型:精确名格式
          "my_third_party_*"    // 全局通配符格式
      )
      
      // 或使用文件路径(相对或绝对路径)
      resourceWhiteList = "config/sdk_whitelist.txt"
      
      // 或多文件合并(适合多模块、多团队场景)
      resourceWhiteList = listOf(
          "config/core_whitelist.txt",
          "config/module_whitelist.txt",
          "/absolute/path/to/third_party.json"
      )
  }
  ```

- 白名单文件格式增强：
  - 支持txt文件(每行一条规则)：
    ```
    # 这是注释行
    drawable:ic_sdk_*
    string:keep_me
    my_third_party_*
    ```
  - 支持json数组格式：
    ```json
    [
      "drawable:ic_sdk_*",
      "string:keep_me",
      "my_third_party_*"
    ]
    ```
  - 自动支持去重、忽略空行和注释行
  - 自动处理相对路径和绝对路径

- 白名单规则优化：
  - 支持类型:通配符格式：`drawable:ic_sdk_*` 
  - 支持类型:精确名格式：`string:keep_me`
  - 支持全局通配符格式：`my_third_party_*`
  - 规则匹配优先级：精确名 > 类型:通配符 > 全局通配符
  - 支持后配置覆盖先配置的冲突规则

### 3. 多Flavor与CI/CD场景支持增强
- 支持通过Gradle属性或环境变量动态注入白名单配置：
  ```shell
  # 命令行动态指定白名单文件
  ./gradlew assembleDev -PresourceWhiteList=flavor_dev_whitelist.txt
  
  # Gradle属性动态读取
  horizon {
      resourceWhiteList = project.findProperty("resourceWhiteList") ?: "default_whitelist.txt"
  }
  ```

- 支持flavor/module名称动态拼接到资源前缀：
  ```kotlin
  horizon {
      // 支持变量替换：{module}=模块名, {flavor}=flavor名
      resourcePrefixPattern = "{module}_{flavor}_"
  }
  ```

### 4. 日志与错误处理增强
- 为资源白名单配置增加了更详细的日志输出
- 添加了找不到白名单文件时的友好警告
- 资源处理过程输出详细的处理记录，便于排查问题

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

## 资源隔离与重命名配置详解

### 配置选项完整说明

```kotlin
horizon {
    // 是否启用资源隔离（默认false）
    enableResourceIsolation = true
    
    // 资源命名策略：PREFIX（前缀）或SUFFIX（后缀）
    resourceNamingStrategy = ResourceNamingStrategy.PREFIX
    
    // 自定义资源前缀模板，支持变量：{module}=模块名, {flavor}=flavor名称, {package}=包名最后一段
    resourcePrefixPattern = "{module}_{flavor}_"
    
    // 自定义资源后缀模板，支持变量：{module}=模块名, {flavor}=flavor名称, {package}=包名最后一段
    resourceSuffixPattern = "_{module}_{flavor}"
    
    // 是否启用资源MD5命名（默认false，通常用于解决极端冲突）
    enableResourceMd5 = false
    
    // MD5长度，仅当enableResourceMd5=true时生效
    resourceMd5Length = 8
    
    // 是否保留原始资源名（添加到资源前缀/后缀之后）
    keepOriginalName = true
    
    // 资源重命名映射表输出位置（相对项目根目录）
    resourceMapOutputPath = "build/reports/resources/resource_rename_map.json"
    
    // 是否在编译前自动清理上一次的资源重命名备份文件
    cleanResourceBackupBeforeBuild = true
    
    // 资源白名单配置，支持List<String>、单文件路径、多文件路径列表
    resourceWhiteList = listOf(
        "drawable:ic_sdk_*",       // 类型:通配符格式
        "string:keep_me",          // 类型:精确名格式
        "my_third_party_*"         // 全局通配符格式
    )
    // 或使用文件配置
    resourceWhiteList = "config/sdk_whitelist.txt"
    
    // 排除特定资源目录，不进行资源隔离处理
    excludeResourceDirs = listOf("src/main/res/raw", "src/main/assets")
    
    // 排除特定资源类型，不进行资源隔离处理
    excludeResourceTypes = listOf("raw", "font", "mipmap")
    
    // 启用跨模块资源引用解析
    enableCrossModuleResourceReference = true
    
    // 声明依赖模块（用于解析资源引用）
    dependentModules = listOf("core", "common")
    
    // 指定资源重命名映射表导入路径（用于解析其他模块的资源映射）
    resourceMapImportPaths = listOf(
        "../core/build/reports/resources/resource_rename_map.json",
        "../common/build/reports/resources/resource_rename_map.json"
    )
    
    // 自定义资源处理器类名（需实现ResourceProcessor接口）
    resourceProcessorClass = "com.example.CustomResourceProcessor"
    
    // 资源处理器参数
    resourceProcessorParams = mapOf(
        "customParam1" to "value1",
        "customParam2" to "value2"
    )
}
```

### 1. 资源隔离策略配置

资源隔离是HorizonSDKPlugin的核心功能之一，通过自定义前缀或后缀，确保各模块资源互不冲突。以下是完整的配置选项：

```kotlin
horizon {
    // 启用资源隔离（默认为true）
    enableResourceIsolation = true
    
    // 资源命名策略：PREFIX（前缀）或SUFFIX（后缀）
    resourceNamingStrategy = ResourceNamingStrategy.PREFIX
    
    // 自定义资源前缀/后缀模板
    // 支持变量：{module}=模块名, {flavor}=flavor名称, {package}=包名最后一段
    resourcePrefixPattern = "{module}_{flavor}_"
    resourceSuffixPattern = "_{module}_{flavor}"
    
    // 是否启用资源MD5命名（默认false, 通常仅用于解决极端冲突）
    enableResourceMd5 = false
    
    // MD5长度，仅当enableResourceMd5=true时生效
    resourceMd5Length = 8
    
    // 是否保留原始资源名（添加到资源前缀/后缀之后）
    keepOriginalName = true
    
    // 资源重命名映射表输出位置（相对项目根目录）
    resourceMapOutputPath = "build/reports/resources/resource_rename_map.json"
    
    // 是否在编译前自动清理上一次的资源重命名备份文件
    cleanResourceBackupBeforeBuild = true
}
```

### 2. 命令行动态配置

除了在build.gradle中配置外，还支持通过命令行参数动态指定：

```shell
# 指定资源命名策略为后缀模式
./gradlew assembleDebug -PresourceNamingStrategy=SUFFIX

# 指定自定义资源前缀
./gradlew assembleDebug -PresourcePrefixPattern="custom_"

# 启用MD5资源命名
./gradlew assembleDebug -PenableResourceMd5=true -PresourceMd5Length=6
```

### 3. 不同命名策略示例

#### 前缀模式(PREFIX)
```kotlin
horizon {
    resourceNamingStrategy = ResourceNamingStrategy.PREFIX
    resourcePrefixPattern = "demo_"
}
```
效果：
- `layout/activity_main.xml` → `layout/demo_activity_main.xml`
- `<string name="app_name">` → `<string name="demo_app_name">`
- `R.drawable.icon` → `R.drawable.demo_icon`

#### 后缀模式(SUFFIX)
```kotlin
horizon {
    resourceNamingStrategy = ResourceNamingStrategy.SUFFIX
    resourceSuffixPattern = "_demo"
}
```
效果：
- `layout/activity_main.xml` → `layout/activity_main_demo.xml`
- `<string name="app_name">` → `<string name="app_name_demo">`
- `R.drawable.icon` → `R.drawable.icon_demo`

#### MD5模式示例
```kotlin
horizon {
    enableResourceMd5 = true
    resourceMd5Length = 6
    keepOriginalName = true
}
```
效果：
- `layout/activity_main.xml` → `layout/md5a7c3b9_activity_main.xml`
- `<string name="app_name">` → `<string name="md5f8e4d2_app_name">`

### 4. 模板变量详解

资源隔离支持灵活的模板变量，可在前缀/后缀中动态替换：

| 变量 | 说明 | 示例 |
| --- | --- | --- |
| `{module}` | 当前模块名称 | 模块为"payment"时替换为"payment" |
| `{flavor}` | 当前flavor名称 | flavor为"dev"时替换为"dev" |
| `{package}` | 包名最后一段 | 包名为"com.example.demo"时替换为"demo" |
| `{variant}` | 构建变体名称 | 变体为"devDebug"时替换为"devdebug" |
| `{buildType}` | 构建类型 | 构建类型为"debug"时替换为"debug" |

模板变量组合示例：
```kotlin
resourcePrefixPattern = "{module}_{flavor}_"
```
效果：模块名为"payment"，flavor为"dev"时，资源前缀为"payment_dev_"

### 5. 高级配置：跨模块资源隔离与引用

当多个模块共享资源时，可以使用以下配置确保正确引用：

```kotlin
horizon {
    // 启用跨模块资源引用解析
    enableCrossModuleResourceReference = true
    
    // 声明依赖模块（用于解析资源引用）
    dependentModules = listOf("core", "common")
    
    // 指定资源重命名映射表导入路径（用于解析其他模块的资源映射）
    resourceMapImportPaths = listOf(
        "../core/build/reports/resources/resource_rename_map.json",
        "../common/build/reports/resources/resource_rename_map.json"
    )
}
```

### 6. 自定义资源处理器配置

高级用户可以自定义资源处理逻辑：

```kotlin
horizon {
    // 自定义资源处理器类名（需实现ResourceProcessor接口）
    resourceProcessorClass = "com.example.CustomResourceProcessor"
    
    // 资源处理器参数
    resourceProcessorParams = mapOf(
        "customParam1" to "value1",
        "customParam2" to "value2"
    )
}
```

### 7. 资源隔离的工作流程

1. **资源扫描**：插件扫描所有module的res目录
2. **前缀/后缀生成**：根据配置生成资源前缀或后缀
3. **资源重命名**：对不符合命名规则的资源进行重命名
4. **引用同步**：自动同步XML和代码中的资源引用
5. **映射表生成**：生成资源重命名映射表，便于追踪和调试
6. **备份原文件**：所有修改前会自动备份原文件，便于回滚

### 8. 排除特定资源

除使用白名单外，还可以通过以下配置排除特定资源：

```kotlin
horizon {
    // 排除特定资源目录
    excludeResourceDirs = listOf("src/main/res/raw", "src/main/assets")
    
    // 排除特定资源类型
    excludeResourceTypes = listOf("raw", "font", "mipmap")
}
```

### 9. 兼容性与注意事项

- 资源隔离功能兼容Android Gradle Plugin 7.0+
- 启用MD5命名时，请确保项目中没有硬编码的资源名引用
- 使用后缀策略时，ViewBinding属性名可能出现变化，请谨慎使用
- 大型项目首次启用资源隔离时，建议先备份项目并在非生产环境测试
- 如遇特殊资源命名冲突，可组合使用前缀/后缀和MD5策略

---

## 完整配置示例

以下是HorizonSDKPlugin所有功能的完整配置示例：

```kotlin
// 在app/build.gradle.kts或app/build.gradle中添加以下配置
plugins {
    id("com.neil.horizonsdk") version "1.0.5"
}

horizon {
    //========== 基础配置 ==========
    // 日志级别，可选值：DEBUG、INFO、WARN、ERROR（默认INFO）
    logLevel = "INFO"
    
    //========== 模块自动注册配置 ==========
    // 是否启用自动注册功能（默认true）
    enableAutoRegister = true
    
    // 需要扫描的模块包名列表
    modulePackages = mutableListOf(
        "com.example.module1",
        "com.example.module2"
    )
    
    // A需要排除的包名列表
    excludePackages = mutableListOf(
        "com.example.module1.internal"
    )
    
    // 生成的注册类名（默认ModuleRegistry）
    registerClassName = "SDKModuleRegistry"
    
    // 生成代码输出目录（默认build/generated/horizon）
    outputDir = "build/generated/horizon"
    
    // 生成注册类的包名（默认com.neil.plugin.autoregister）
    generatedPackage = "com.example.sdk.registry"
    
    //========== 混淆规则配置 ==========
    // 追加自定义混淆规则
    proguardRules = mutableListOf(
        "-keep class com.example.api.** { *; }",
        "-keep class com.example.core.** { *; }"
    )
    
    //========== 资源隔离配置 ==========
    // 是否启用资源隔离（默认false）
    enableResourceIsolation = true
    
    // 资源命名策略：PREFIX（前缀）或SUFFIX（后缀）
    resourceNamingStrategy = ResourceNamingStrategy.PREFIX
    
    // 自定义资源前缀模板，支持变量替换
    resourcePrefixPattern = "{module}_{flavor}_"
    
    // 自定义资源后缀模板，支持变量替换
    resourceSuffixPattern = "_{module}_{flavor}"
    
    // 是否启用资源MD5命名（默认false）
    enableResourceMd5 = false
    
    // MD5长度，仅当enableResourceMd5=true时生效
    resourceMd5Length = 8
    
    // 是否保留原始资源名
    keepOriginalName = true
    
    // 资源白名单配置
    resourceWhiteList = listOf(
        "drawable:ic_sdk_*",
        "string:keep_me",
        "my_third_party_*"
    )
    
    // 排除特定资源目录
    excludeResourceDirs = listOf(
        "src/main/res/raw", 
        "src/main/assets"
    )
    
    // 排除特定资源类型
    excludeResourceTypes = listOf(
        "raw", 
        "font", 
        "mipmap"
    )
    
    //========== 代码引用分析配置 ==========
    // 是否启用代码引用分析（默认false）
    enableCodeReferenceAnalysis = true
    
    // 是否生成依赖图（默认true，依赖enableCodeReferenceAnalysis=true）
    generateDependencyGraph = true
    
    // 是否检测未使用的类（默认true，依赖enableCodeReferenceAnalysis=true）
    detectUnusedClasses = true
    
    // 代码引用白名单（API接口、常量类等无引用但需保留的类）
    codeReferenceWhiteList = listOf(
        "com.example.api.*",
        "com.example.Constants",
        "com.example.AppEntry"
    )
    
    //========== 资源引用自动更新配置 ==========
    // 是否启用资源引用自动更新（默认false）
    enableResourceReferenceUpdate = false
    
    // 资源引用更新白名单配置
    resourceReferenceWhiteList = listOf(
        "com.example.thirdparty.*"
    )
    
    // 是否为试运行模式（不实际修改文件，仅输出日志）
    resourceReferenceDryRun = true
    
    // 是否强制更新已有前缀的资源引用
    forceUpdatePrefixedResources = false
    
    //========== 跨模块资源引用配置 ==========
    // 启用跨模块资源引用解析
    enableCrossModuleResourceReference = true
    
    // 声明依赖模块
    dependentModules = listOf("core", "common")
    
    // 指定资源重命名映射表导入路径
    resourceMapImportPaths = listOf(
        "../core/build/reports/resources/resource_rename_map.json",
        "../common/build/reports/resources/resource_rename_map.json"
    )
    
    //========== 高级自定义配置 ==========
    // 自定义资源处理器类名
    resourceProcessorClass = "com.example.CustomResourceProcessor"
    
    // 资源处理器参数
    resourceProcessorParams = mapOf(
        "customParam1" to "value1",
        "customParam2" to "value2"
    )
    
    // 额外参数，便于后续扩展
    extraArgs = mutableMapOf(
        "customSetting" to true,
        "anotherSetting" to "value"
    )
}
```

上述配置示例涵盖了HorizonSDKPlugin的所有功能选项，您可以根据自己的需求选择性配置。

---

## 常见场景配置示例

### 1. 仅使用自动注册功能

如果您只需要使用模块自动注册功能，不需要资源隔离等其他功能：

```kotlin
horizon {
    // 启用自动注册
    enableAutoRegister = true
    
    // 需要扫描的模块包名列表
    modulePackages = mutableListOf("com.example.module1", "com.example.module2")
    
    // 生成的注册类名
    registerClassName = "ModuleRegistry"
    
    // 生成类的包名
    generatedPackage = "com.example.registry"
}
```

### 2. 仅使用资源隔离功能

如果您只需要使用资源隔离功能，确保各模块资源不冲突：

```kotlin
horizon {
    // 启用资源隔离
    enableResourceIsolation = true
    
    // 使用前缀策略
    resourceNamingStrategy = ResourceNamingStrategy.PREFIX
    
    // 自定义前缀为模块名+下划线
    resourcePrefixPattern = "{module}_"
    
    // 常用第三方SDK资源白名单
    resourceWhiteList = listOf(
        "androidx_*",
        "google_*",
        "material_*"
    )
}
```

### 3. 多flavor项目资源隔离配置

如果您的项目有多个flavor，需要针对不同flavor隔离资源：

```kotlin
horizon {
    // 启用资源隔离
    enableResourceIsolation = true
    
    // 使用前缀策略
    resourceNamingStrategy = ResourceNamingStrategy.PREFIX
    
    // 自定义前缀包含模块名和flavor
    resourcePrefixPattern = "{module}_{flavor}_"
    
    // 资源白名单使用不同flavor的配置文件
    resourceWhiteList = listOf(
        "config/common_whitelist.txt",
        "config/${project.findProperty("flavorName")}_whitelist.txt"
    )
}
```

### 4. 完整SDK开发配置

多模块SDK开发的推荐配置，包含自动注册、资源隔离、代码引用分析等功能：

```kotlin
horizon {
    // 基础配置
    logLevel = "INFO"
    
    // 模块自动注册
    enableAutoRegister = true
    modulePackages = mutableListOf("com.example.module1", "com.example.module2")
    registerClassName = "SDKRegistry"
    generatedPackage = "com.example.sdk.registry"
    
    // 混淆规则
    proguardRules = mutableListOf("-keep class com.example.api.** { *; }")
    
    // 资源隔离
    enableResourceIsolation = true
    resourceNamingStrategy = ResourceNamingStrategy.PREFIX
    resourcePrefixPattern = "sdk_{module}_"
    resourceWhiteList = "config/sdk_whitelist.txt"
    
    // 代码引用分析
    enableCodeReferenceAnalysis = true
    generateDependencyGraph = true
    detectUnusedClasses = true
    
    // 资源引用自动更新（初次配置使用试运行模式）
    enableResourceReferenceUpdate = true
    resourceReferenceDryRun = true
}
```

### 5. CI/CD环境动态配置

在CI/CD环境中，可以通过Gradle属性动态指定配置：

```kotlin
horizon {
    // 从Gradle属性读取配置，适合CI/CD环境
    logLevel = project.findProperty("horizon.logLevel") ?: "INFO"
    
    // 资源隔离
    enableResourceIsolation = (project.findProperty("horizon.enableResourceIsolation") ?: "true").toBoolean()
    resourcePrefixPattern = project.findProperty("horizon.resourcePrefix") ?: "{module}_"
    
    // 从环境特定文件加载白名单
    resourceWhiteList = project.findProperty("horizon.whiteListFile") ?: "config/default_whitelist.txt"
    
    // CI环境中始终使用试运行模式，避免意外修改
    resourceReferenceDryRun = !project.hasProperty("horizon.applyChanges")
}
```

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
  - A: 在 gradle.properties 添加 `enableResourceMd5=true`，或命令行加 `-PenableResourceMd5=true`，或在 DSL 中设置 `enableResourceMd5 = true`。
- **Q: 如何自定义资源前缀/后缀？**
  - A: 在 horizon DSL 中设置 `resourcePrefixPattern` 或 `resourceSuffixPattern`，支持静态文本和动态变量，如 `"{module}_{flavor}_"`。
- **Q: 如何切换资源命名策略（前缀/后缀）？**
  - A: 在 horizon DSL 中设置 `resourceNamingStrategy = ResourceNamingStrategy.PREFIX` 或 `ResourceNamingStrategy.SUFFIX`。
- **Q: 资源命名支持哪些模板变量？**
  - A: 支持 {module}、{flavor}、{package}、{variant}、{buildType} 等变量，详见"模板变量详解"。
- **Q: 不同模块使用不同资源命名策略可以吗？**
  - A: 可以，每个模块可以在各自的 build.gradle 中独立配置不同的命名策略。
- **Q: 如何排除特定类型资源的重命名？**
  - A: 使用 `excludeResourceTypes = listOf("raw", "font")` 或通过白名单规则排除。
- **Q: 如何禁用特定模块的资源隔离？**
  - A: 在模块的 horizon DSL 中设置 `enableResourceIsolation = false`。
- **Q: 资源重命名后，如何找到原始资源名？**
  - A: 查看生成的资源映射文件 `build/reports/resources/resource_rename_map.json`。
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
- **Q: 日志文件在哪里？**
  - A: 默认在项目 build/logs 目录下的 horizon_plugin.log 文件中。
- **Q: 如何设置日志文件轮转？**
  - A: 使用 `PluginLogger.setRotationPolicy(maxSize, maxCount)` 设置大小限制和历史文件数量。
- **Q: 日志文件过大怎么办？**
  - A: v1.0.5 版本已添加日志轮转功能，可以自动管理日志文件大小。
- **Q: 代码引用分析支持哪些类型的引用？**
  - A: v1.0.5 支持类型引用、构造函数调用、静态方法/属性调用、继承和实现关系等。
- **Q: 配置验证功能是做什么的？**
  - A: 自动检查配置项是否有效，并自动修正无效的配置，提供明确的错误提示。

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
// 初始化所有模块，返回成功初始化的模块数量
val successCount = com.neil.plugin.autoregister.ModuleRegistry.initAll(context)
```

### 3. 模块懒加载和初始化控制
支持按需初始化特定类型或分组的模块：

```kotlin
// 按类型初始化模块
val count1 = ModuleRegistry.initModulesByType("user", context)

// 按分组初始化模块
val count2 = ModuleRegistry.initModulesByGroup("core", context)

// 初始化特定模块列表
val userModules = ModuleRegistry.getModulesByType("user")
val count3 = ModuleRegistry.initModules(userModules, context)
```

### 4. 模块状态查询
提供模块状态查询功能，方便了解各模块初始化情况：

```kotlin
// 查询已初始化的模块
val initializedModules = ModuleRegistry.getInitializedModules()

// 查询初始化失败的模块
val failedModules = ModuleRegistry.getFailedModules()

// 检查模块状态
val userModules = ModuleRegistry.getModulesByType("user")
userModules.forEach { moduleInfo ->
    println("模块: ${moduleInfo.className}")
    println("- 状态: ${moduleInfo.state}") // PENDING/INITIALIZING/INITIALIZED/FAILED
    println("- 耗时: ${moduleInfo.initTimeMs}ms")
    if (moduleInfo.state == ModuleRegistry.ModuleState.FAILED) {
        println("- 错误: ${moduleInfo.error}")
    }
}
```

### 5. 模块实例获取
初始化后的模块实例可以直接获取，无需重复创建：

```kotlin
// 获取已初始化的模块实例
val userModule = ModuleRegistry.getInstance<UserModule>("com.example.UserModule")
userModule?.someMethod() // 直接使用模块实例
```

### 6. 初始化过程说明
- 支持懒加载，避免不必要的初始化
- 自动处理依赖关系，避免重复初始化
- 线程安全，适用于并发场景
- 详细记录每个模块的初始化状态、耗时和错误信息
- 支持插件日志系统集成，方便调试和追踪
- `initAll`和`initModulesByXxx`方法返回成功初始化的模块数量

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
- 邮箱：redamancy.wu@example.com
- 版本更新日志：[CHANGELOG.md](CHANGELOG.md)
- 问题反馈：请在GitHub提交Issue或Pull Request

## 贡献指南

欢迎为HorizonSDKPlugin做出贡献！

1. Fork项目
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 打开Pull Request

## 开源许可

HorizonSDKPlugin使用MIT许可证。详见 [LICENSE](LICENSE) 文件。 