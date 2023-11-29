plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.looper.android_enhancer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.looper.android_enhancer"
        minSdk = 27
        targetSdk = 34
        versionCode = 10
        versionName = "1.1.0"       
    }
    
    buildFeatures {
        buildConfig = true       
    }    
    
    buildTypes {
        getByName("release") {
            // Enables code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            // Includes the default ProGuard rules files.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }    
    
    sourceSets {
        getByName("main") {
            // Specify the directories containing native libraries. (.so files)
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
    
    packaging {
        jniLibs {
            // Include compiled native libraries (.so) using legacy packaging.
            useLegacyPackaging = true
        }
    }    
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.preference)
    
    implementation(libs.material)
        
    implementation(libs.topjohnwu.libsu.core)

    implementation(libs.looper.utils.android.support)
}