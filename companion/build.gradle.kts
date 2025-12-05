plugins {
    alias(libs.plugins.aboutLibs) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.hotreload) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover) apply false
}

tasks.register<Exec>("buildIosIpa") {
    group = "build"
    description = "Builds an unsigned IPA for AltStore distribution"

    dependsOn(":composeApp:linkReleaseFrameworkIosArm64")

    val iosAppDir = layout.projectDirectory.dir("iosApp")
    workingDir = iosAppDir.asFile

    commandLine(
        "sh", "-c", """
            set -e
            echo "Building iOS archive..."
            xcodebuild clean archive \
                -project iosApp.xcodeproj \
                -scheme iosApp \
                -configuration Release \
                -archivePath build/iosApp.xcarchive \
                CODE_SIGN_IDENTITY="-" \
                CODE_SIGNING_REQUIRED=NO \
                CODE_SIGNING_ALLOWED=NO \
                DEVELOPMENT_TEAM=""

            echo "Creating IPA manually (AltStore will re-sign)..."
            rm -rf build/Payload
            mkdir -p build/Payload
            cp -r build/iosApp.xcarchive/Products/Applications/*.app build/Payload/
            cd build
            zip -qr iosApp.ipa Payload
            rm -rf Payload

            echo "IPA created at: iosApp/build/iosApp.ipa"
            ls -lh iosApp.ipa
        """.trimIndent()
    )

    doLast {
        val ipaFile = iosAppDir.file("build/iosApp.ipa").asFile
        if (ipaFile.exists()) {
            val sizeInMB = ipaFile.length() / (1024.0 * 1024.0)
            println("\n✅ IPA built successfully!")
            println("📦 Location: ${ipaFile.absolutePath}")
            println("📊 Size: ${"%.2f".format(sizeInMB)} MB")
            println("📊 Bytes: ${ipaFile.length()}")
            println("\nUpdate AltStore/ClassicSource.json with:")
            println("  \"size\": ${ipaFile.length()},")
            println("  \"downloadURL\": \"<your-hosted-url>/iosApp.ipa\"")
        }
    }
}
