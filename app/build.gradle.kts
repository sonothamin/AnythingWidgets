plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sonothamin.anythingwidgets"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sonothamin.anythingwidgets"
        minSdk = 26
        targetSdk = 36
        // Official sonothamin.dev widget registry (must end with "/"). Serves index.json + widget specs over HTTPS.
        buildConfigField("String", "REGISTRY_URL", "\"https://sonothamin.dev/widgets/\"")
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
    }

    // The production key is optional: it is only configured when CI (or you) provides these env vars.
    val keystorePath = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        // Placeholder key committed to the repo (see signing/README.md) so CI release APKs are always signed.
        create("dev") {
            storeFile = rootProject.file("signing/dev.p12")
            storeType = "pkcs12"
            storePassword = "devkey-placeholder"
            keyAlias = "dev"
            keyPassword = "devkey-placeholder"
        }
        if (keystorePath != null && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real key when CI provides one, otherwise the placeholder.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
}
