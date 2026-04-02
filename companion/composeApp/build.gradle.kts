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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.aboutLibs)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.buildkonfig)
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

val jvmVersion = libs.versions.jvm.get()!!
val projectPackage = "org.noblecow.hrservice"

kotlin {
    jvmToolchain(jvmVersion.toInt())
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-Xannotation-default-target=param-property"
        )
    }

    android {
        namespace = projectPackage
        compileSdk = 36
        minSdk = 28
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(jvmVersion))
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isReturnDefaultValues = true
        }
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            packaging {
                resources.excludes.addAll(
                    setOf(
                        "/META-INF/{AL2.0,LGPL2.1}",
                        "META-INF/INDEX.LIST",
                        "META-INF/io.netty.versions.properties",
                        "META-INF/LICENSE*.md"
                    )
                )
            }
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            binaryOption("bundleId", projectPackage)
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            @Suppress("DEPRECATION")
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
        getByName("androidHostTest").dependencies {
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
        getByName("androidDeviceTest").dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.test.espresso.core)
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

dependencies {
    detektPlugins(libs.compose.rules.detekt)
    ktlintRuleset(libs.compose.rules.ktlint)
    androidRuntimeClasspath(libs.compose.ui.tooling)
}

buildkonfig {
    packageName = projectPackage

    defaultConfigs {
        buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN, "DEBUG", "false")
    }

    targetConfigs("debug") {
        create("android") {
            buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN, "DEBUG", "true")
        }
    }
}

configurations.getByName("androidHostTestImplementation") {
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
