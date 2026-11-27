import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val appVersionCode = (project.findProperty("enziiVersionCode") as String?)
    ?.trim()
    ?.toIntOrNull()
    ?: 1
val appVersionName = (project.findProperty("enziiVersionName") as String?)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "0.1.0"

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

val envStorePath = System.getenv("ENZII_KEYSTORE_PATH")?.trim().orEmpty()
val envStorePass = System.getenv("ENZII_KEYSTORE_PASSWORD")?.trim().orEmpty()
val envKeyAlias = System.getenv("ENZII_KEY_ALIAS")?.trim().orEmpty()
val envKeyPass = System.getenv("ENZII_KEY_PASSWORD")?.trim().orEmpty()
val hasEnvKeystore = envStorePath.isNotEmpty() &&
    file(envStorePath).exists() &&
    envStorePass.isNotEmpty() &&
    envKeyAlias.isNotEmpty() &&
    envKeyPass.isNotEmpty()

val localStorePath = keystoreProperties.getProperty("storeFile")?.trim().orEmpty()
val localStorePass = keystoreProperties.getProperty("storePassword")?.trim().orEmpty()
val localKeyAlias = keystoreProperties.getProperty("keyAlias")?.trim().orEmpty()
val localKeyPass = keystoreProperties.getProperty("keyPassword")?.trim().orEmpty()
val hasLocalKeystore = keystorePropertiesFile.exists() &&
    localStorePath.isNotEmpty() &&
    rootProject.file(localStorePath).exists() &&
    localStorePass.isNotEmpty() &&
    localStorePass != "your_keystore_password_here" &&
    localKeyAlias.isNotEmpty() &&
    localKeyPass.isNotEmpty() &&
    localKeyPass != "your_key_password_here"

val hasReleaseKeystore = hasEnvKeystore || hasLocalKeystore

android {
    namespace = "com.enziidict.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.enziidict.app"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "GITHUB_OWNER", "\"vudaivlogs-hue\"")
        buildConfigField("String", "GITHUB_REPO", "\"app-h-c-ti-ng-anh-\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasEnvKeystore) {
                storeFile = file(envStorePath)
                storePassword = envStorePass
                keyAlias = envKeyAlias
                keyPassword = envKeyPass
            } else if (hasLocalKeystore) {
                storeFile = rootProject.file(localStorePath)
                storePassword = localStorePass
                keyAlias = localKeyAlias
                keyPassword = localKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.mlkit.translate)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
