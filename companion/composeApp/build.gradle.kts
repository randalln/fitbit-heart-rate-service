/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("UnstableApiUsage")

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.aboutLibs)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hotreload)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint.gradle)
    alias(libs.plugins.metro)
}

ktlint {
    version.set("1.8.0")
    verbose.set(true)
    outputToConsole.set(true)
    coloredOutput.set(true)
    filter {
        exclude("**/generated/**")
    }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
keystoreProperties.load(FileInputStream(keystorePropertiesFile))

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            binaryOption("bundleId", "org.noblecow.hrservice")
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(compose.material3)
            implementation(libs.material.icons.core)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
            api(libs.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.kermit)
            implementation(libs.metrox.viewmodel.compose)
            implementation(libs.navigation.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kermit.test)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.activity.compose)
            implementation(libs.activity.ktx)
            implementation(libs.androidx.compose.material3)
            implementation(libs.material)
            implementation(libs.work.runtime.ktx)

            // Third-party libraries
            implementation(libs.blessed.kotlin)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.server.call.logging)
            implementation(libs.ktor.server.netty)
            implementation(libs.logback.android)
            implementation(libs.metrox.android)
        }
        androidUnitTest.dependencies {
            implementation(libs.androidx.compose.ui.tooling)
            implementation(libs.androidx.test.runner)
            implementation(libs.junit)
            implementation(libs.mockk.android)
            implementation(libs.mockk.agent)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.server.test.host)
            implementation(libs.logback.classic)
            implementation(libs.turbine)
        }
        androidInstrumentedTest.dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.test.rules)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.ktor.server.cio)
        }
        iosTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kermit.test)
            implementation(libs.turbine)
        }
    }
}

android {
    namespace = "org.noblecow.hrservice"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "org.noblecow.hrservice"
        minSdk = 28
        targetSdk = 36
        versionCode = 16
        versionName = "0.9.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        buildConfig = true
    }
    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs["release"]
        }
    }
    packaging {
        resources {
            excludes += arrayOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
    testOptions {
        animationsDisabled = true
        packaging {
            resources.excludes.add("META-INF/LICENSE*.md")
        }
        unitTests.isReturnDefaultValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            allWarningsAsErrors = true
            freeCompilerArgs = listOf(
                "-Xwhen-guards",
                "-Xannotation-default-target=param-property",
                "-Xexpect-actual-classes"
            )
        }
    }
}

dependencies {
    detektPlugins(libs.compose.rules.detekt)
    ktlintRuleset(libs.compose.rules.ktlint)
    debugImplementation(libs.compose.ui.tooling)
}

configurations.testImplementation {
    exclude(module = "logback-android")
}

aboutLibraries {
    export {
        // Define the output path for manual generation
        // Adjust the path based on your project structure (e.g., composeResources, Android res/raw)
        outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json")
        // Optionally specify the variant for export
        // variant = "release"
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/config.yml")
}

tasks.withType<Detekt>().configureEach {
    buildUponDefaultConfig = true
    exclude("**/generated/**")
    config.setFrom("$rootDir/config/detekt/config.yml")
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    exclude("**/generated/**")
}

tasks.register<Detekt>("detektCommonMain") {
    description = "Run detekt on commonMain (KMP)"
    group = "verification"
    setSource(files("src/commonMain/kotlin"))
}

tasks.matching { it.name == "detektAndroidDebug" || it.name == "detektIosArm64Main" }.configureEach {
    dependsOn("detektCommonMain")
}
