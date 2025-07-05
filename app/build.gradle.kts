plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.codenzi.payday"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codenzi.payday"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            // Derleme sırasında oluşabilecek "DuplicateFileException" hatasını önler.
            excludes.add("META-INF/DEPENDENCIES")
        }
    }

    buildTypes {
        release {
            // UYGULAMA BOYUTUNU KÜÇÜLTME VE GÜVENLİK İÇİN DÜZELTME:
            // Kod küçültme (minification) ve gizleme (obfuscation) etkinleştirildi.
            // Bu, uygulamanızın APK boyutunu düşürür ve kodunuzun okunmasını zorlaştırarak güvenliği artırır.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    // --- ÖNEMLİ: FIREBASE KÜTÜPHANELERİNİ EKLEYİN ---
    // Firebase Bill of Materials (BoM) - Bu satır, diğer Firebase kütüphanelerinin sürümlerini otomatik yönetir.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    // Firebase Crashlytics bağımlılığını ekleyin
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    // Daha iyi analiz için Analytics'i de eklemeniz önerilir.
    implementation("com.google.firebase:firebase-analytics-ktx")

    // --- TEMEL VE UI KÜTÜPHANELERİ ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // --- YAŞAM DÖNGÜSÜ (LIFECYCLE) VE VIEWMODEL ---
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")

    // --- VERİ SAKLAMA (DATA) ---
    // Room (Veritabanı)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")
    // DataStore (Basit veri saklama)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- GOOGLE ENTEGRASYONLARI ---
    // Google ile Giriş ve Drive API
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0") {
        exclude("org.apache.httpcomponents")
    }
    implementation("com.google.http-client:google-http-client-gson:1.44.2") {
        exclude("org.apache.httpcomponents")
    }

    // --- ÜÇÜNCÜ PARTİ KÜTÜPHANELER ---
    // TEKRARLANAN BAĞIMLILIKLAR TEMİZLENDİ
    implementation("nl.dionsegijn:konfetti-xml:2.0.2")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // --- TEST KÜTÜPHANELERİ ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- CORE LIBRARY DESUGARING ---
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}