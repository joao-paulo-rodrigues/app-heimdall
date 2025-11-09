plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // kapt temporariamente desabilitado devido a incompatibilidade com Java 21
    // id("kotlin-kapt")
    // Hilt temporariamente desabilitado
    // id("dagger.hilt.android.plugin")
}

android {
    namespace = "com.heimdall.device"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.heimdall.device"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // MQTT - HiveMQ Client (mesmo do projeto base)
    implementation("com.hivemq:hivemq-mqtt-client:1.3.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    
    // JSON
    implementation("org.json:json:20231013")
    
    // Java 8+ API desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // Hilt DI - temporariamente desabilitado
    // implementation("com.google.dagger:hilt-android:2.48")
    // kapt("com.google.dagger:hilt-compiler:2.48")
    
    // Room Database - Temporariamente desabilitado devido a incompatibilidade kapt com Java 21
    // Para reativar: usar Java 11 ou migrar para KSP
    // implementation("androidx.room:room-runtime:2.6.1")
    // implementation("androidx.room:room-ktx:2.6.1")
    // kapt("androidx.room:room-compiler:2.6.1")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    
    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

