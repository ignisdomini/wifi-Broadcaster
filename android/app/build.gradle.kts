import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Данные ключа лежат в keystore.properties рядом с проектом и в репозиторий
 * не попадают. Если файла нет — release собирается неподписанным, чтобы
 * сборка не падала у того, у кого ключа и не должно быть.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val hasSigningKey = keystoreProperties.getProperty("storeFile") != null &&
    rootProject.file(keystoreProperties.getProperty("storeFile", "")).exists()

android {
    namespace = "ru.radioinformator.efir"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.radioinformator.efir"
        minSdk = 21
        targetSdk = 34
        versionCode = 13
        versionName = "2.5"

        // Адрес сети по умолчанию. Пользователь может сменить его в настройках
        // приложения; короткие ссылки собираются из сохранённого значения.
        buildConfigField("String", "DEFAULT_SITE_URL", "\"https://radioinformator.ru\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v1 нужен для Android 6 и старше, v2/v3 — для новых.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // 10.0.2.2 — «localhost компьютера» с точки зрения эмулятора.
            // Обычный HTTP для него разрешён в network_security_config.
            buildConfigField("String", "DEFAULT_SITE_URL", "\"http://10.0.2.2:8831\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // X25519 и ChaCha20-Poly1305: в стандартном Android X25519 появился только
    // с API 33, а у нас minSdk 21. Берём низкоуровневые примитивы Tink.
    implementation("com.google.crypto.tink:tink-android:1.15.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Чистая JVM-сборка Tink — чтобы схему шифрования можно было прогнать
    // обычным unit-тестом, без устройства и эмулятора.
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.crypto.tink:tink:1.15.0")
}
