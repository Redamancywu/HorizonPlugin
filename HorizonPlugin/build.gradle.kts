// 作者：Redamancy  时间：2025-05-19
// KSP + KotlinPoet 配置
plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.ksp)
    `maven-publish`
}

group = "com.neil.plugin" // 组织ID
version = "1.0.0"         // 插件版本号

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "horizon-plugin"
            version = project.version.toString()
        }
    }
    repositories {
        mavenLocal() // 支持本地发布
        maven { url = uri("https://jitpack.io") } // 支持JitPack远程发布
    }
}

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
}
