plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.codenzi.payday"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codenzi.payday"
        minSdk = 24
        targetSdk = 35 // compileSdk ile aynı olması en iyi pratiktir.
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
        }
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
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3") // veya lifecycle-runtime-ktx ile aynı sürüm
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // ===== GOOGLE DRIVE ENTEGRASYONU İÇİN YENİ EKLENENLER =====
    // Google ile Giriş Yapma (Authentication)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Google Drive API'ı için Gerekli Kütüphaneler
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0") {
        exclude("org.apache.httpcomponents")
    }
    implementation("com.google.http-client:google-http-client-gson:1.44.2") {
        exclude("org.apache.httpcomponents")
    }
    // =========================================================

    implementation(libs.androidx.core.ktx)
    implementation("nl.dionsegijn:konfetti-xml:2.0.2")
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

}