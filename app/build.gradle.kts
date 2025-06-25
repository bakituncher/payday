plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.payday"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.payday"
        minSdk = 24
        targetSdk = 35 // compileSdk ile aynı olması en iyi pratiktir.
        versionCode = 1
        versionName = "1.0"

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
        // Projedeki tutarsızlığı gidermek için Java 17'ye güncellendi.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17" // Java sürümü ile uyumlu hale getirildi.
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation("nl.dionsegijn:konfetti-xml:2.0.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // ViewModel, LiveData ve Activity KTX
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.activity.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0") // BU SATIRI EKLEYİN

    implementation(libs.androidx.core.ktx)
    implementation("nl.dionsegijn:konfetti-xml:2.0.2")
    implementation("com.google.code.gson:gson:2.10.1")
}