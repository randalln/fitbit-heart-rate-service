import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint.gradle)
}

val jvmVersion = libs.versions.jvm.get()!!

android {
    namespace = "org.noblecow.hrservice.app"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "org.noblecow.hrservice"
        minSdk = 28
        targetSdk = 36
        versionCode = 17
        versionName = "0.9.2"
    }

    signingConfigs {
        val propsFile: File = rootProject.file("keystore.properties")
        if (propsFile.exists()) {
            val keystoreProperties =
                Properties().apply {
                    this.load(FileInputStream(propsFile))
                }
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        } else {
            create("release")
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

    compileOptions {
        JavaVersion.toVersion(jvmVersion).apply {
            sourceCompatibility = this
            targetCompatibility = this
        }
    }

    packaging {
        resources {
            excludes +=
                arrayOf(
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
}

dependencies {
    implementation(project(":composeApp"))
    debugImplementation(libs.compose.ui.tooling)
}
