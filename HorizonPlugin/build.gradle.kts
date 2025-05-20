// 作者：Redamancy  时间：2025-05-19
// KSP + KotlinPoet 配置
plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.ksp)
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.2.1"
}
group = "com.neil.plugin" // 组织ID
version = "1.0.2"         // 插件版本号

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}
dependencies {
    implementation(gradleApi())

    implementation(libs.ksp)
    implementation("com.squareup:kotlinpoet:2.2.0")
    implementation("com.squareup:kotlinpoet-ksp:2.2.0")
    // 作者：Redamancy  时间：2025-05-19
    // 依赖 AGP 以支持 android/buildTypes/proguardFile 相关 API
    implementation("com.android.tools.build:gradle:8.1.0")
}

// Gradle Plugin Portal 发布配置
// pluginBundle 块已废弃，元数据直接写在 gradlePlugin.plugins.create 内

gradlePlugin {
    plugins {
        create("HorizonPlugin") {
            id = "com.neil.horizon"
            displayName = "Horizon SDK Plugin"
            description = "A universal Gradle plugin for Android SDK multi-module projects, supporting auto-registration, resource isolation, proguard, manifest merge, and more."
            implementationClass = "com.neil.plugin.HorizonSDKPlugin"
            tags.set(listOf("android", "sdk", "ksp", "auto-register"))
            website.set("https://github.com/Redamancywu/HorizonPlugin")
            vcsUrl.set("https://github.com/Redamancywu/HorizonPlugin.git")
        }
    }
}
