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

import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint.gradle)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutLibs)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/config.yml")
}

ktlint {
    version.set("1.7.1")
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

ksp {
    arg("KOIN_CONFIG_CHECK", "true")
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
    compilerOptions {
        allWarningsAsErrors = true
    }
    jvm() // https://youtrack.jetbrains.com/issue/KT-52664/Multiplatform-projects-with-a-single-target
    androidTarget {
        /*
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
         */
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)

        dependencies {
            debugImplementation(libs.androidx.compose.ui.tooling)
            debugImplementation(libs.androidx.test.runner)

            testImplementation(libs.junit)
            testImplementation(libs.mockk.android)
            testImplementation(libs.mockk.agent)
            testImplementation(libs.kotlinx.coroutines.test)
            testImplementation(libs.logback.classic)
            testImplementation(libs.turbine)

            androidTestImplementation(platform(libs.androidx.compose.bom))
            androidTestImplementation(libs.androidx.compose.ui.test)
            androidTestImplementation(libs.androidx.compose.ui.test.junit4)
            androidTestImplementation(libs.androidx.test.rules)
            debugImplementation(libs.androidx.compose.ui.test.manifest)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            api(libs.koin.annotations)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.activity.compose)
            implementation(libs.activity.ktx)
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.ui.tooling.preview)
            implementation(libs.material)
            implementation(libs.navigation.compose)
            implementation(libs.navigation.ui.ktx)
            implementation(libs.work.runtime.ktx)
            implementation(libs.androidx.compose.ui.test)

            // Third-party libraries
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.blessed.kotlin)
            implementation(libs.timber)
            implementation(libs.ktor.client.android)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.serialization.kotlinx)
            implementation(libs.ktor.server.call.logging)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.logback.android)
            implementation(libs.slf4j.api)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
            implementation(libs.koin.androidx.workmanager)
        }

        jvmMain.dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            api(libs.androidx.compose.runtime)
        }
    }

    // KSP Common sourceSet
    sourceSets.named("commonMain").configure {
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    }
}

android {
    namespace = "org.noblecow.hrservice"
    compileSdk = 36
    ndkVersion = "29.0.14033849"

    defaultConfig {
        applicationId = "org.noblecow.hrservice"
        minSdk = 28
        targetSdk = 36
        versionCode = 14
        versionName = "0.8.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            // jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors = true
            freeCompilerArgs = listOf(
                "-Xwhen-guards"
            )
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.koin.ksp.compiler)
    add("kspAndroid", libs.koin.ksp.compiler)
    detektPlugins(libs.compose.rules.detekt)
    ktlintRuleset(libs.compose.rules.ktlint)
}

configurations.testImplementation {
    exclude(module = "logback-android")
}

// Trigger Common Metadata Generation from Native tasks
tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}
tasks.matching {
    it.name.startsWith("runKtlintCheckOverCommonMainSourceSet") &&
        it.name != "kspCommonMainKotlinMetadata"
}.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude("**/generated/**")
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    exclude("**/generated/**")
}
