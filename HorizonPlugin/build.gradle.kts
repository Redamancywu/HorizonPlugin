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
version = "1.0.7"         // 插件版本号更新

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
    // 添加org.json库，用于处理JSON文件
    implementation("org.json:json:20231013")
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

// 发布到 mavenLocal 供本地测试
// 运行: ./gradlew publishToMavenLocal
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "horizon-plugin"
            version = project.version.toString()
            
            from(components["java"])
            
            // 添加POM信息
            pom {
                name.set("HorizonSDKPlugin")
                description.set("A universal Gradle plugin for Android SDK multi-module projects")
                url.set("https://github.com/Redamancywu/HorizonPlugin")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("redamancy")
                        name.set("Redamancy")
                        email.set("redamancy@example.com")
                    }
                }
            }
        }
    }
}
