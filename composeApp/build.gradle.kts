import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    // iOS and WasmJS targets are unused stubs (only desktop/JVM and Android are actively
    // developed) and their absence of java.time.* support broke commonMain IDE resolution.
    // Re-enable if those targets are picked back up.
    // listOf(
    //     iosArm64(),
    //     iosSimulatorArm64()
    // ).forEach { iosTarget ->
    //     iosTarget.binaries.framework {
    //         baseName = "ComposeApp"
    //         isStatic = true
    //     }
    // }

    jvm()

    // js target is an unused stub (only desktop/JVM and Android are actively developed).
    // js {
    //     browser()
    //     binaries.executable()
    // }

    // @OptIn(ExperimentalWasmDsl::class)
    // wasmJs {
    //     browser()
    //     binaries.executable()
    // }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.cio.mp)
            implementation(libs.androidx.core.ktx)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)
            implementation(libs.ktor.client.content.negotiation.mp)
            implementation(libs.ktor.serialization.kotlinx.json.mp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.cio)
            implementation(libs.openpdf)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Feeds RESERVEO_VERSION_CODE/NAME into the running JVM app — desktop has no BuildConfig
// equivalent, so AppRoot.kt reads this file off the classpath at startup instead.
val generateJvmVersionInfo = tasks.register("generateJvmVersionInfo") {
    val outputDir = layout.buildDirectory.dir("generated/jvmVersion")
    val versionCode = project.findProperty("RESERVEO_VERSION_CODE") as String
    val versionName = project.findProperty("RESERVEO_VERSION_NAME") as String
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.apply { mkdirs() }
        File(dir, "version.properties").writeText("code=$versionCode\nname=$versionName\n")
    }
}

kotlin.sourceSets.getByName("jvmMain") {
    resources.srcDir(generateJvmVersionInfo)
}

// Release signing key — never committed. See RELEASING.md for how to generate one.
// Falls back to the debug keystore (with a warning) when absent, so local
// `assembleRelease` builds still work without extra setup.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "org.julsz.smnt"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.julsz.smnt"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = (project.findProperty("RESERVEO_VERSION_CODE") as String).toInt()
        versionName = project.findProperty("RESERVEO_VERSION_NAME") as String
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("keystore.properties not found — release build signed with the debug keystore. " +
                    "In-place updates over a properly-signed install will NOT work. See RELEASING.md.")
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

compose.desktop {
    application {
        mainClass = "org.julsz.smnt.MainKt"

        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8",
            "-Dstdout.encoding=UTF-8",
            "-Dstderr.encoding=UTF-8",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Reserveo"
            packageVersion = project.findProperty("RESERVEO_VERSION_NAME") as String
            description = "Reserveo — hotel management system"
            vendor = "julsz"

            windows {
                menuGroup = "Reserveo"
                shortcut = true
                // Fixed identity so future versions upgrade the same install
                upgradeUuid = "0f3d8a5e-9c41-4a2b-b7e6-5d1c8a0b3f74"
            }
            linux {
                packageName = "reserveo"
            }
            macOS {
                bundleID = "org.julsz.smnt"
            }
        }
    }
}
