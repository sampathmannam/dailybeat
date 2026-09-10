plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

fun quotedBuildConfig(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val supabaseUrl = providers.gradleProperty("SUPABASE_URL")
    .orElse(providers.environmentVariable("SUPABASE_URL"))
    .getOrElse("")
val supabaseAnonKey = providers.gradleProperty("SUPABASE_ANON_KEY")
    .orElse(providers.environmentVariable("SUPABASE_ANON_KEY"))
    .getOrElse("")
val debugApplicationIdSuffix = providers.gradleProperty("dailybeatDebugApplicationIdSuffix")
    .getOrElse(".qa")
require(Regex("\\.qa(?:\\.[a-zA-Z][a-zA-Z0-9_]*)*").matches(debugApplicationIdSuffix)) {
    "Debug builds must use .qa or a nested .qa.* package; production data must stay isolated."
}

android {
    namespace = "com.dailybeat.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dailybeat.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 17
        versionName = "3.8.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", quotedBuildConfig(supabaseUrl))
        buildConfigField("String", "SUPABASE_ANON_KEY", quotedBuildConfig(supabaseAnonKey))
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = System.getenv("DAILYBEAT_STORE_PASSWORD")
            keyAlias = "dailybeat"
            keyPassword = System.getenv("DAILYBEAT_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = debugApplicationIdSuffix
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }

    androidResources {
        noCompress += listOf("gguf", "bin")
    }
}

tasks.withType<Test> {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}

// The regular .qa app can contain the officer's imported PDFs. Instrumentation fixtures
// are destructive, so neither a default Gradle invocation nor a runner may target it.
val verifyDisposableTestTarget = tasks.register("verifyDisposableTestTarget") {
    doLast {
        require(debugApplicationIdSuffix == ".qa.e2eloop") {
            "Instrumentation requires -PdailybeatDebugApplicationIdSuffix=.qa.e2eloop; keep production and regular QA data intact."
        }
    }
}
tasks.configureEach {
    if (name == "connectedDebugAndroidTest" || name == "installDebugAndroidTest") {
        dependsOn(verifyDisposableTestTarget)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.maplibre.gl:android-sdk:11.8.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.02"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// aapt2 is published per platform, and it is the only platform-classified artifact in the graph.
// Regenerating gradle/verification-metadata.xml on macOS records only the osx variant, while every
// CI job runs on ubuntu-latest — so dependency verification would pass locally and fail closed on
// the runner. Running this task in the same generation pass pulls the linux jar in so its checksum
// is captured too; see docs/RELEASE.md. It contributes nothing to the APK, and
// test_gradle_supply_chain.py fails the release gate if the linux pin ever goes missing.
val aapt2LinuxForVerification: Configuration by configurations.creating
dependencies {
    aapt2LinuxForVerification("com.android.tools.build:aapt2:8.6.1-11315950:linux@jar")
}
tasks.register("resolveAapt2Linux") {
    val resolved = aapt2LinuxForVerification.incoming.artifactView { lenient(false) }.files
    doLast { resolved.forEach { println("resolved ${it.name}") } }
}
