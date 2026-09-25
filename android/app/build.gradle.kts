plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

// Platform-only build: ./gradlew assembleRelease -PdailybeatFoss=true
// A property preserves the existing signed release task/artifact contract for current users.
val dailybeatFoss = providers.gradleProperty("dailybeatFoss").orNull == "true"
// The store build must be reproducible without private backend settings or a signing key.
val dailybeatStore = providers.gradleProperty("dailybeatStore").orNull == "true"
val dailybeatUnsigned = providers.gradleProperty("dailybeatUnsigned").orNull == "true"
require(!dailybeatStore || dailybeatFoss) { "Store builds require -PdailybeatFoss=true." }
val fossManifest = layout.buildDirectory.file("generated/foss/AndroidManifest.xml")
val prepareFossManifest = tasks.register("prepareFossManifest") {
    inputs.file("src/main/AndroidManifest.xml")
    outputs.file(fossManifest)
    doLast {
        fossManifest.get().asFile.apply {
            parentFile.mkdirs()
            writeText(file("src/main/AndroidManifest.xml").readLines()
                .filterNot { it.contains("permission.ACTIVITY_RECOGNITION") }
                .joinToString("\n", postfix = "\n"))
        }
    }
}

// Package the exact repository GPL text into every APK instead of maintaining a second copy.
val generatedLegalResources = layout.buildDirectory.dir("generated/legal/res")
val prepareLegalResources = tasks.register<Copy>("prepareLegalResources") {
    from(rootProject.file("../LICENSE"))
    into(generatedLegalResources.map { it.dir("raw") })
    rename { "gpl_3_0.txt" }
}

fun quotedBuildConfig(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val supabaseUrl = if (dailybeatStore) "" else providers.gradleProperty("SUPABASE_URL")
    .orElse(providers.environmentVariable("SUPABASE_URL"))
    .getOrElse("")
val supabaseAnonKey = if (dailybeatStore) "" else providers.gradleProperty("SUPABASE_ANON_KEY")
    .orElse(providers.environmentVariable("SUPABASE_ANON_KEY"))
    .getOrElse("")
val debugApplicationIdSuffix = providers.gradleProperty("dailybeatDebugApplicationIdSuffix")
    .getOrElse(".qa")
require(Regex("\\.qa(?:\\.[a-zA-Z][a-zA-Z0-9_]*)*").matches(debugApplicationIdSuffix)) {
    "Debug builds must use .qa or a nested .qa.* package; production data must stay isolated."
}

android {
    namespace = "com.dailybeat.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    // Google Play encrypts this optional metadata, making it opaque to F-Droid.
    // Keep dependency auditing in the public inventory and CI instead.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    sourceSets.getByName("main") {
        java.srcDir(if (dailybeatFoss) "src/foss/java" else "src/gms/java")
        res.srcDir(generatedLegalResources)
        if (dailybeatFoss) manifest.srcFile(fossManifest)
    }

    defaultConfig {
        applicationId = "com.dailybeat.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 41
        versionName = "4.3.7"
        buildConfigField("boolean", "GOOGLE_LOCATION", (!dailybeatFoss).toString())
        buildConfigField("boolean", "STORE_DISTRIBUTION", dailybeatStore.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", quotedBuildConfig(supabaseUrl))
        buildConfigField("String", "SUPABASE_ANON_KEY", quotedBuildConfig(supabaseAnonKey))
    }

    signingConfigs {
        if (!dailybeatUnsigned) create("release") {
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
            if (!dailybeatUnsigned) signingConfig = signingConfigs.getByName("release")
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

tasks.register("verifyGoogleFreeDependencies") {
    group = "verification"
    description = "Reject Google Play Services/Firebase dependencies in the platform-only build."
    doLast {
        check(dailybeatFoss) { "Use -PdailybeatFoss=true for this verification." }
        val modules = configurations.getByName("releaseRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts.map { it.moduleVersion.id }
        val forbidden = modules.filter { it.group.startsWith("com.google.android.gms") ||
            it.group.startsWith("com.google.firebase") }
        check(forbidden.isEmpty()) { "Non-free dependencies found: $forbidden" }
        logger.lifecycle("Google-free dependency gate passed ({} resolved artifacts).", modules.size)
    }
}

// Trivy does not infer Android dependencies from build.gradle.kts. Emit the resolved release
// modules in its supported Gradle lockfile format; this inventory is NOT an input lock for Gradle.
tasks.register("exportReleaseDependencyInventory") {
    group = "verification"
    val variantName = if (dailybeatFoss) "foss" else "standard"
    val inventory = layout.buildDirectory.file("reports/dependency-inventory/$variantName.gradle.lockfile")
    outputs.file(inventory)
    outputs.upToDateWhen { false }
    doLast {
        val modules = configurations.getByName("releaseRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts.map { it.moduleVersion.id.toString() }
            .distinct().sorted()
        check(modules.isNotEmpty()) { "Refusing to emit an empty dependency inventory." }
        inventory.get().asFile.apply {
            parentFile.mkdirs()
            writeText("# Generated release runtime inventory for vulnerability scanning; not an input lock.\n" +
                modules.joinToString("\n") { "$it=releaseRuntimeClasspath" } + "\nempty=\n")
        }
        logger.lifecycle("Exported {} {} release modules for vulnerability scanning.", modules.size, variantName)
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
    if (name == "preBuild") dependsOn(prepareLegalResources)
    if (dailybeatFoss && name == "preBuild") dependsOn(prepareFossManifest)
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
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Tink references these FLOSS, compile-time annotations. GMS previously supplied them
    // incidentally; declare them explicitly so the Google-free R8 release can be built.
    compileOnly("com.google.errorprone:error_prone_annotations:2.23.0")
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

// F-Droid removes this unused file and src/gms before scanning/building. The FOSS graph
// never evaluates it, and its absence must not change the store APK.
if (!dailybeatFoss) apply(from = "gms-dependencies.gradle.kts")

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
