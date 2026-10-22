import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// ============================================================================
// Чтение секретов подписи: НИКОГДА не хардкодим пароли в этом файле!
// Приоритет источников:
//   1) переменные окружения (CI/CD: KEYSTORE_PASSWORD, KEY_ALIAS и т.д.)
//   2) local.properties (локальная машина; файл НЕ должен попадать в git)
// Если значение не найдено — возвращаем null, а задача validateReleaseSigning
// (см. конец файла) даст понятную ошибку ещё до начала сборки.
// ============================================================================
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }      // сначала окружение
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() } // fallback на local.properties

// Путь к keystore тоже настраиваемый: по умолчанию app/autosense-release.keystore
val keystoreFile = file(secret("KEYSTORE_FILE") ?: "autosense-release.keystore")
val keystorePassword = secret("KEYSTORE_PASSWORD")
val keyAlias = secret("KEY_ALIAS")
val keyPassword = secret("KEY_PASSWORD") ?: keystorePassword // если ключевой пароль отдельный — задайте KEY_PASSWORD

android {
    namespace = "com.mamblxon.autosense"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mamblxon.autosense"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Release-подпись: создаём конфиг ТОЛЬКО если все секреты и сам keystore на месте.
        // Иначе release APK останется неподписанным (assembleRelease сообщит об этом),
        // а ./gradlew validateReleaseSigning заранее объяснит, что именно отсутствует.
        create("release") {
            if (keystoreFile.exists() && keystorePassword != null && keyAlias != null) {
                storeFile = keystoreFile
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword ?: keystorePassword
            } else {
                logger.warn(
                    "⚠️ Release-keystore не настроен полностью " +
                        "(keystore=${keystoreFile.exists()}, " +
                        "KEYSTORE_PASSWORD=${keystorePassword != null}, KEY_ALIAS=${keyAlias != null}). " +
                        "Release будет собран без подписи. Запустите './gradlew validateReleaseSigning' для деталей."
                )
            }
        }
        // Debug-конфиг не трогаем: AGP автоматически подставляет Android debug keystore
        // (~/.android/debug.keystore, alias=AndroidDebugKey) — см. buildTypes.debug ниже.
    }

    buildTypes {
        // Debug: без обфускации/минификации, с отладочной подписью по умолчанию (debuggable=true)
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            // signingConfig не указываем намеренно: AGP сам применяет дебаг-подпись (debug.keystore)
        }
        release {
            isMinifyEnabled = true          // ProGuard/R8-обфускация по proguard-rules.pro
            isShrinkResources = true        // вырезание неиспользуемых ресурсов (требует minify)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
        viewBinding = true
    }

    // Room: экспорт схем БД (обязателен для миграционных тестов и контроля схемы в git)
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    // Lifecycle / ViewModel / Coroutines
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // WorkManager (фоновые задачи с гарантией выполнения)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    // --- Локальные unit-тесты (запускаются на JVM, без эмулятора) ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    // Ассерты в unit-тестах берутся из kotlin.test (assertEquals/assertNotNull/assertTrue)
    testImplementation(kotlin("test"))

    // --- Instrumented-тесты (запускаются на устройстве/эмуляторе) ---
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

// ============================================================================
// Guard-задача: проверяет готовность release-подписи ДО начала сборки.
// Использование:
//   ./gradlew validateReleaseSigning          — просто проверка
//   ./gradlew assembleRelease validateReleaseSigning  — проверка + сборка
// (порядок в командной строке = порядок выполнения; либо добавьте dependsOn
//  на конкретный VariantTask, если нужен жёсткий hook на каждую release-задачу)
// ============================================================================
tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Проверяет наличие release keystore и паролей подписи (env / local.properties)."

    doLast {
        val problems = mutableListOf<String>()

        if (!keystoreFile.exists()) {
            problems += "Keystore-файл не найден: ${keystoreFile.absolutePath}\n" +
                "    Создайте его командой:\n" +
                "    keytool -genkeypair -v -storetype PKCS12 " +
                "-keystore app/autosense-release.keystore -alias autosense-key " +
                "-keyalg RSA -keysize 2048 -validity 10000"
        }
        if (keystorePassword.isNullOrBlank()) {
            problems += "Не задан KEYSTORE_PASSWORD. Добавьте его в local.properties " +
                "(строка 'KEYSTORE_PASSWORD=...') или экспортируйте в окружение: export KEYSTORE_PASSWORD='...'"
        }
        if (keyAlias.isNullOrBlank()) {
            problems += "Не задан KEY_ALIAS. Ожидаемое значение: autosense-key " +
                "(добавьте 'KEY_ALIAS=autosense-key' в local.properties)"
        }
        if (keyPassword.isNullOrBlank() && keystorePassword.isNullOrBlank()) {
            problems += "Не задан KEY_PASSWORD (или KEYSTORE_PASSWORD как fallback) — " +
                "Gradle не сможет открыть ключ внутри keystore."
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "\n\n❌ Release-подпись не настроена. Проблемы:\n" +
                    problems.mapIndexed { i, p -> "  ${i + 1}. $p" }.joinToString("\n") +
                    "\n\nПосле исправления повторите: ./gradlew assembleRelease"
            )
        }

        logger.lifecycle("✅ Release signing OK: keystore=${keystoreFile.name}, alias=$keyAlias")
    }
}
