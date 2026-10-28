plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.austland.aos.wallet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.austland.aos.wallet"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Default gateway for the deployed AOS prototype. Changeable in-app under Settings.
        buildConfigField("String", "DEFAULT_GATEWAY_URL", "\"https://feeless-coin.vercel.app\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Encrypted on-disk storage for the private key (Android Keystore backed).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Background 24h mining claim + lightweight periodic node sync, survives app close.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Pure-Java Ed25519 (no provider registration needed; works on every API level this app supports).
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
