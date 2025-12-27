import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val cargoTargets = mapOf(
    "aarch64-linux-android" to "arm64-v8a",
    "armv7-linux-androideabi" to "armeabi-v7a",
    "x86_64-linux-android" to "x86_64",
    "i686-linux-android" to "x86"
)

abstract class CargoBuildTask : DefaultTask() {
    @get:InputDirectory
    abstract val cargoProjectDir: DirectoryProperty
    
    @get:OutputDirectory
    abstract val jniLibsDir: DirectoryProperty
    
    @get:Input
    abstract val cargoPath: Property<String>
    
    @get:Input
    abstract val ndkHome: Property<String>
    
    @get:Input
    abstract val envPath: Property<String>
    
    @get:Input
    abstract val targets: MapProperty<String, String>
    
    @get:Input
    @get:Optional
    abstract val rustFlags: Property<String>
    
    @get:Inject
    abstract val execOps: ExecOperations
    
    @TaskAction
    fun build() {
        val jniLibs = jniLibsDir.get().asFile
        val cargoDir = cargoProjectDir.get().asFile
        
        // Create output directories
        targets.get().values.forEach { abi ->
            jniLibs.resolve(abi).mkdirs()
        }
        
        // Build for each target
        targets.get().forEach { (target, _) ->
            execOps.exec {
                workingDir(cargoDir)
                environment("ANDROID_NDK_HOME", ndkHome.get())
                environment("PATH", envPath.get())
                rustFlags.orNull?.takeIf { it.isNotBlank() }?.let { environment("RUSTFLAGS", it) }
                commandLine(
                    cargoPath.get(),
                    "ndk",
                    "--platform",
                    "24",
                    "--target",
                    target,
                    "-o",
                    jniLibs.absolutePath,
                    "build",
                    "--release"
                )
            }
        }
    }
}

// Read local.properties
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(FileInputStream(file))
}

val cargoBuild by tasks.registering(CargoBuildTask::class) {
    group = "build"
    description = "Build the AndroidEnhancer Rust core with cargo-ndk"
    cargoProjectDir.set(layout.projectDirectory.dir("src/main/rust"))
    jniLibsDir.set(layout.projectDirectory.dir("src/main/jniLibs"))
    
    val cargoHome = System.getenv("CARGO_HOME")
        ?: localProperties.getProperty("cargo.home")
        ?: error("CARGO_HOME not set and cargo.home not found in local.properties")
    
    cargoPath.set("$cargoHome/bin/cargo")
    
    ndkHome.set(
        System.getenv("ANDROID_NDK_HOME") ?: run {
            val sdkDir = localProperties.getProperty("sdk.dir")
                ?: System.getenv("ANDROID_HOME")
                ?: error("sdk.dir not in local.properties and ANDROID_HOME not set")
            val ndkVer = localProperties.getProperty("ndk.version")
                ?: System.getenv("NDK_VERSION")
                ?: error("ndk.version not found in local.properties and NDK_VERSION not set")
            "$sdkDir/ndk/$ndkVer"
        }
    )
    
    envPath.set("$cargoHome/bin:" + (System.getenv("PATH") ?: ""))
    
    targets.set(cargoTargets)
    
    rustFlags.set(
        System.getenv("RUSTFLAGS")
            ?: localProperties.getProperty("rust.flags")
            ?: ""
    )
}

android {
    namespace = "io.github.iamlooper.androidenhancer"
    compileSdk = 36
    (localProperties.getProperty("ndk.version") ?: System.getenv("NDK_VERSION"))?.let { ndkVersion = it }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = Properties()

            // 1. Local Development (reads from file)
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
            // 2. GitHub Actions (reads from secrets via environment variables)
            else if (System.getenv("ANDROID_KEYSTORE_PASSWORD") != null) {
                storeFile = file("keystore.jks")
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
            // 3. No signing config available
            else {
                logger.warn("WARNING: Release signing not configured. keystore.properties not found and ANDROID_KEYSTORE_PASSWORD not set.")
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.iamlooper.androidenhancer"
        minSdk = 24
        targetSdk = 36
        versionCode = 20
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21

        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.topjohnwu.libsu.core)
    implementation(libs.topjohnwu.libsu.service)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.hiddenapibypass)
}

tasks.named("preBuild").configure {
    dependsOn(cargoBuild)
}