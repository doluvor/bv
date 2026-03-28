@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.io.FileInputStream
import java.net.URI
import java.util.Properties

plugins {
    alias(gradleLibs.plugins.android.application)
    alias(gradleLibs.plugins.compose.compiler)
    alias(gradleLibs.plugins.firebase.crashlytics)
    alias(gradleLibs.plugins.google.ksp)
    alias(gradleLibs.plugins.google.protobuf)
    alias(gradleLibs.plugins.google.services) apply false
    alias(gradleLibs.plugins.kotlin.android)
    alias(gradleLibs.plugins.kotlin.serialization)
}

if (AppConfiguration.googleServicesAvailable) {
    apply(plugin = gradleLibs.plugins.google.services.get().pluginId)
}


val signingProp = file(project.rootProject.file("signing.properties"))

android {
    signingConfigs {
        if (signingProp.exists()) {
            val properties = Properties().apply {
                load(FileInputStream(signingProp))
            }
            create("key") {
                storeFile = rootProject.file(properties.getProperty("keystore.path"))
                storePassword = properties.getProperty("keystore.pwd")
                keyAlias = properties.getProperty("keystore.alias")
                keyPassword = properties.getProperty("keystore.alias_pwd")
            }
        }
    }

    namespace = AppConfiguration.appId
    compileSdk = AppConfiguration.compileSdk

    defaultConfig {
        applicationId = AppConfiguration.appId
        minSdk = AppConfiguration.minSdk
        targetSdk = AppConfiguration.targetSdk
        versionCode = AppConfiguration.versionCode
        versionName = AppConfiguration.versionName
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("int", "VERSION_CODE", "${AppConfiguration.versionCode}")
        buildConfigField("String", "VERSION_NAME", "\"${AppConfiguration.versionName}\"")
        buildConfigField("String", "APPLICATION_ID", "\"${AppConfiguration.appId}\"")
        buildConfigField("String", "BLACKLIST_URL", "\"${AppConfiguration.blacklistUrl}\"")
    }

    flavorDimensions.add("channel")
    flavorDimensions.add("platform")

    productFlavors {
        create("lite") {
            dimension = "channel"
        }
        create("default") {
            dimension = "channel"
        }
        create("mobile") {
            dimension = "platform"
            applicationIdSuffix = ".mobile"
            versionNameSuffix = "-mobile"
        }
        create("tv") {
            dimension = "platform"
            // No suffix - keeps the original app ID
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingProp.exists()) signingConfig = signingConfigs.getByName("key")
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = AppConfiguration.googleServicesAvailable
            }
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            applicationIdSuffix = ".debug"
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
        create("r8Test") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            applicationIdSuffix = ".r8test"
            if (signingProp.exists()) signingConfig = signingConfigs.getByName("key")
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
        create("alpha") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (signingProp.exists()) signingConfigs.getByName("key") else signingConfigs.getByName("debug")
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = AppConfiguration.googleServicesAvailable
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "**/*.proto"
        }

        if (gradle.startParameter.taskNames.find { it.startsWith("assembleLite") } != null) {
            jniLibs {
                val vlcLibs = listOf("libvlc", "libc++_shared", "libvlcjni")
                val abis = listOf("x86_64", "x86", "arm64-v8a", "armeabi-v7a")
                vlcLibs.forEach { vlcLibName -> abis.forEach { abi -> excludes.add("lib/$abi/$vlcLibName.so") } }
            }
        }
    }

    /*splits {
        if (gradle.startParameter.taskNames.find { it.startsWith("assembleDefault") } != null) {
            abi {
                isEnable = true
                reset()
                include("x86_64", "x86", "arm64-v8a", "armeabi-v7a")
                isUniversalApk = true
            }
        }
    }*/

    applicationVariants.configureEach {
        val variant = this
        outputs.configureEach {
            (this as ApkVariantOutputImpl).apply {
                val abi = this.filters.find { it.filterType == "ABI" }?.identifier ?: "universal"
                outputFileName =
                    "BV_${AppConfiguration.versionCode}_${AppConfiguration.versionName}.${variant.buildType.name}_${variant.flavorName}_$abi.apk"
                versionNameOverride =
                    "${variant.versionName}.${variant.buildType.name}"
            }
        }
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_build_reports")
    stabilityConfigurationFiles.addAll(
        layout.projectDirectory.file("compose_compiler_config.conf")
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(AppConfiguration.jdk))
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    annotationProcessor(androidx.room.compiler)
    ksp(androidx.room.compiler)
    ksp(libs.koin.ksp.compiler)
    api(platform("${libs.firebase.bom.get()}"))
    api(androidx.activity.compose)
    api(androidx.core.ktx)
    api(androidx.core.splashscreen)
    api(androidx.compose.constraintlayout)
    api(androidx.compose.ui)
    api(androidx.compose.ui.util)
    api(androidx.compose.ui.tooling.preview)
    api(androidx.compose.material.icons)
    api(androidx.compose.material)
    api(androidx.compose.material3)
    api(androidx.compose.material3.adaptive)
    api(androidx.compose.material3.adaptive.layout)
    api(androidx.compose.material3.adaptive.navigation)
    api(androidx.compose.material3.adaptive.navigation.suit)
    api(androidx.compose.material3.window.size)
    api(androidx.compose.tv.foundation)
    api(androidx.compose.tv.material)
    api(androidx.datastore.typed)
    api(androidx.datastore.preferences)
    api(androidx.lifecycle.runtime.ktx)
    api(androidx.media3.common)
    api(androidx.media3.decoder)
    api(androidx.media3.exoplayer)
    api(androidx.media3.ui)
    api(androidx.navigation.compose)
    api(androidx.room.ktx)
    api(androidx.room.runtime)
    api(androidx.webkit)
    api(libs.accompanist.systemuicontroller)
    api(libs.akdanmaku)
    api(libs.coil.compose)
    api(libs.coil.core)
    api(libs.coil.gif)
    api(libs.coil.network.okhttp)
    api(libs.coil.svg)
    api(libs.firebase.analytics)
    api(libs.firebase.crashlytics)
    api(libs.geetest.sensebot)
    api(libs.koin.android)
    api(libs.koin.annotations)
    api(libs.koin.compose)
    api(libs.koin.compose.navigation)
    api(libs.kotlinx.serialization)
    api(libs.ktor.client.cio)
    api(libs.koin.core)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.client.core)
    api(libs.ktor.client.encoding)
    api(libs.ktor.client.okhttp)
    api(libs.ktor.client.serialization.kotlinx)
    api(libs.ktor.server.cio)
    api(libs.ktor.server.core)
    api(libs.logging)
    api(libs.lottie)
    api(libs.material)
    api(libs.protobuf.kotlin)
    api(libs.qrcode)
    api(libs.rememberPreference)
    api(libs.slf4j.android.mvysny)
    api(libs.zxing)
    api(project(mapOf("path" to ":bili-api")))
    api(project(mapOf("path" to ":bili-subtitle")))
    api(project(mapOf("path" to ":player")))
    api(project(mapOf("path" to ":utils")))
    api(project(mapOf("path" to ":symbols")))
    testImplementation(androidx.room.testing)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(androidx.compose.ui.test.junit4)
    debugApi(androidx.compose.ui.test.manifest)
    debugApi(androidx.compose.ui.tooling)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java")
                create("kotlin")
            }
        }
    }
}

tasks.register("downloadBlacklist") {
    val assetsDir = file("src/main/res/raw")
    val resourceUrl = AppConfiguration.blacklistUrl
    val outputFile = File(assetsDir, "blacklist.bin")

    if (outputFile.exists()) return@register

    doLast {
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }
        println("Downloading resource from $resourceUrl to ${outputFile.absolutePath}")
        URI(resourceUrl).toURL().openStream().use { input: InputStream ->
            outputFile.outputStream().use { output: java.io.FileOutputStream ->
                input.copyTo(output)
            }
            println("Download complete: ${outputFile.absolutePath}")
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("downloadBlacklist")
}

tasks.withType<Test> {
    useJUnitPlatform()
}