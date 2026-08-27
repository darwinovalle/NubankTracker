import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tracker.nubank"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tracker.nubank"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

// Some machines have a JRE as the default JVM (no javac). When that's the case, point
// the Java toolchain at a full JDK by setting `nubank.javaToolchain` (machine-local,
// e.g. in ~/.gradle/gradle.properties). Absent on normal machines → no override.
providers.gradleProperty("nubank.javaToolchain")
    .map(String::toInt)
    .orNull
    ?.let { version ->
        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(version)
            }
        }
    }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Google Sheets API - versiones corregidas
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-sheets:v4-rev20240319-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")
    implementation("com.google.http-client:google-http-client-gson:1.44.1")
    // Provides AndroidHttp.newCompatibleTransport() (not pulled in by google-api-client-android)
    implementation("com.google.http-client:google-http-client-android:1.44.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // OAuth Google sign-in (AccountManager-based, no google-services.json needed)
    implementation(libs.play.services.auth)

    // lifecycleScope for MainActivity
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Unit tests
    testImplementation(libs.junit)
}