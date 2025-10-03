plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
//    id("kotlin-kapt")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.quick.voice.recorder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.quick.voice.recorder"
        minSdk = 24
        targetSdk = 36
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

    buildFeatures {
        buildConfig = true
        viewBinding = true
        dataBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.activity)
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
//    implementation(libs.androidx.activity)
    implementation("androidx.activity:activity:1.12.0-alpha09")
    implementation(libs.androidx.constraintlayout)

    // Architecture Components
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.fragment.ktx)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines - CRITICAL FOR .await() TO WORK
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google Play Services
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.auth)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    // UI Components
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.lottie)

    // Image Loading
    implementation(libs.glide)
    ksp(libs.glide.compiler)

    // Data Storage
    implementation(libs.androidx.datastore.preferences)

    // Ads
//    implementation(libs.play.services.ads)

    // Responsive Design
    implementation(libs.sdp.android)
    implementation(libs.ssp.android)

    //swiperefresh
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-alpha01")

    implementation("androidx.media:media:1.6.0")
    // Permissions
    implementation(libs.akspermission.android)
}