import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

fun quotedBuildConfig(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun isHttpsUrl(value: String): Boolean = runCatching {
    URI(value).let { uri ->
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }
}.getOrDefault(false)

fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

val supabaseUrlFromEnvironment = providers.environmentVariable("SUPABASE_URL")
    .getOrElse("")
    .trim()
val supabaseAnonKeyFromEnvironment = providers.environmentVariable("SUPABASE_ANON_KEY")
    .getOrElse("")
    .trim()
val patrolGridPrivacyPolicyUrlFromEnvironment =
    providers.environmentVariable("PATROLGRID_PRIVACY_POLICY_URL")
        .getOrElse("")
        .trim()
val patrolGridReleaseCommitFromEnvironment =
    providers.environmentVariable("PATROLGRID_RELEASE_COMMIT")
        .getOrElse("")
        .trim()

// Local and QA builds keep developer-friendly Gradle-property overrides. Release
// values are declared separately below and never read from Gradle properties.
val debugSupabaseUrl = providers.gradleProperty("SUPABASE_URL")
    .orElse(providers.environmentVariable("SUPABASE_URL"))
    .getOrElse("")
    .trim()
val debugSupabaseAnonKey = providers.gradleProperty("SUPABASE_ANON_KEY")
    .orElse(providers.environmentVariable("SUPABASE_ANON_KEY"))
    .getOrElse("")
    .trim()
val openFreeMapStyleUrl = "https://tiles.openfreemap.org/styles/liberty"
val debugPatrolGridMapStyleUrlOverride = providers.gradleProperty("PATROLGRID_MAP_STYLE_URL")
    .orElse(providers.environmentVariable("PATROLGRID_MAP_STYLE_URL"))
    .getOrElse("")
    .trim()
val debugPatrolGridMapStyleUrl =
    debugPatrolGridMapStyleUrlOverride.ifBlank { openFreeMapStyleUrl }
val debugPatrolGridPrivacyPolicyUrl = providers.gradleProperty("PATROLGRID_PRIVACY_POLICY_URL")
    .orElse(providers.environmentVariable("PATROLGRID_PRIVACY_POLICY_URL"))
    .getOrElse("")
    .trim()
val releaseSupabaseUrl = supabaseUrlFromEnvironment
val releaseSupabaseAnonKey = supabaseAnonKeyFromEnvironment
val releasePatrolGridPrivacyPolicyUrl = patrolGridPrivacyPolicyUrlFromEnvironment
val releasePatrolGridMapStyleUrl = openFreeMapStyleUrl
val releasePatrolGridCommit = patrolGridReleaseCommitFromEnvironment
// PatrolGrid's approved evidence-retention policy is part of the app identity,
// not a release-time switch. Policy changes require reviewed source and a new APK.
val patrolGridRetentionDays = 365
val patrolGridApplicationId = "com.dailybeat.app.patrolgrid"
val patrolGridPrivacyPolicySource = rootProject.file("../docs/PATROLGRID_PRIVACY_POLICY.md")
val patrolGridProductionIdentitySource = rootProject.file("patrolgrid-production.properties")
val patrolGridProductionIdentity = Properties().apply {
    val identityPath = patrolGridProductionIdentitySource.toPath()
    if (Files.isRegularFile(identityPath, LinkOption.NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(identityPath)
    ) {
        patrolGridProductionIdentitySource.inputStream().use(::load)
    }
}
val expectedProductionSupabaseUrl =
    patrolGridProductionIdentity.getProperty("SUPABASE_URL", "").trim()
val expectedProductionSupabaseAnonKeySha256 =
    patrolGridProductionIdentity.getProperty("SUPABASE_ANON_KEY_SHA256", "").trim().lowercase()
val expectedPrivacyPolicyStatus =
    patrolGridProductionIdentity.getProperty("PRIVACY_POLICY_STATUS", "").trim()
val expectedPrivacyNoticeVersion =
    patrolGridProductionIdentity.getProperty("PRIVACY_NOTICE_VERSION", "")
        .trim()
        .toIntOrNull() ?: 0

android {
    namespace = "com.dailybeat.app"
    compileSdk = 36

    defaultConfig {
        // This is PatrolGrid's permanent direct-update identity. Never make it
        // deployment-configurable: a typo would create a second app and strand
        // the existing encrypted local database.
        applicationId = patrolGridApplicationId
        minSdk = 26
        targetSdk = 36
        // Version code scheme: major * 10_000 + minor * 100 + patch.
        versionCode = 10_000
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", quotedBuildConfig(debugSupabaseUrl))
        buildConfigField("String", "SUPABASE_ANON_KEY", quotedBuildConfig(debugSupabaseAnonKey))
        buildConfigField("String", "PATROLGRID_RELEASE_COMMIT", quotedBuildConfig("debug"))
        buildConfigField("String", "PATROLGRID_BACKEND_IDENTITY", quotedBuildConfig("debug"))
        buildConfigField("String", "PATROLGRID_PRIVACY_POLICY_STATUS", quotedBuildConfig("debug"))
        buildConfigField("int", "PATROLGRID_PRIVACY_NOTICE_VERSION", "3")
        manifestPlaceholders["patrolGridReleaseCommit"] = "debug"
        manifestPlaceholders["patrolGridBackendIdentity"] = "debug"
        manifestPlaceholders["patrolGridPrivacyPolicyStatus"] = "debug"
        manifestPlaceholders["patrolGridPrivacyNoticeVersion"] = "3"
        buildConfigField(
            "String",
            "PATROLGRID_MAP_STYLE_URL",
            quotedBuildConfig(debugPatrolGridMapStyleUrl),
        )
        buildConfigField(
            "String",
            "PATROLGRID_PRIVACY_POLICY_URL",
            quotedBuildConfig(debugPatrolGridPrivacyPolicyUrl),
        )
        buildConfigField("int", "PATROLGRID_RETENTION_DAYS", patrolGridRetentionDays.toString())
    }

    buildTypes {
        debug {
            // Keep PatrolGrid QA isolated from historical DailyBeat developer installs.
            applicationIdSuffix = ".qa"
        }
        release {
            // Production backend and policy values are environment-only. The map
            // style is deliberately source-pinned and has no release override.
            buildConfigField("String", "SUPABASE_URL", quotedBuildConfig(releaseSupabaseUrl))
            buildConfigField(
                "String",
                "SUPABASE_ANON_KEY",
                quotedBuildConfig(releaseSupabaseAnonKey),
            )
            buildConfigField(
                "String",
                "PATROLGRID_RELEASE_COMMIT",
                quotedBuildConfig(releasePatrolGridCommit),
            )
            buildConfigField(
                "String",
                "PATROLGRID_BACKEND_IDENTITY",
                quotedBuildConfig(expectedProductionSupabaseUrl),
            )
            buildConfigField(
                "String",
                "PATROLGRID_PRIVACY_POLICY_STATUS",
                quotedBuildConfig(expectedPrivacyPolicyStatus),
            )
            buildConfigField(
                "int",
                "PATROLGRID_PRIVACY_NOTICE_VERSION",
                expectedPrivacyNoticeVersion.toString(),
            )
            manifestPlaceholders["patrolGridReleaseCommit"] = releasePatrolGridCommit
            manifestPlaceholders["patrolGridBackendIdentity"] = expectedProductionSupabaseUrl
            manifestPlaceholders["patrolGridPrivacyPolicyStatus"] = expectedPrivacyPolicyStatus
            manifestPlaceholders["patrolGridPrivacyNoticeVersion"] =
                expectedPrivacyNoticeVersion.toString()
            buildConfigField(
                "String",
                "PATROLGRID_MAP_STYLE_URL",
                quotedBuildConfig(releasePatrolGridMapStyleUrl),
            )
            buildConfigField(
                "String",
                "PATROLGRID_PRIVACY_POLICY_URL",
                quotedBuildConfig(releasePatrolGridPrivacyPolicyUrl),
            )
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
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

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
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
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("org.maplibre.gl:android-sdk:11.8.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    constraints {
        testImplementation("org.bouncycastle:bcprov-jdk18on:1.84") {
            because("Robolectric's 1.77 transitively includes CVE-2025-14813")
        }
    }

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val validateReleaseConfiguration by tasks.registering {
    group = "verification"
    description = "Fails closed when PatrolGrid release backend or policy configuration is unsafe."
    doLast {
        val forbiddenGradleProperties = listOf(
            "SUPABASE_URL",
            "SUPABASE_ANON_KEY",
            "PATROLGRID_PRIVACY_POLICY_URL",
            "PATROLGRID_MAP_STYLE_URL",
            "PATROLGRID_RELEASE_COMMIT",
            "PATROLGRID_BACKEND_IDENTITY",
            "PATROLGRID_PRIVACY_POLICY_STATUS",
            "PATROLGRID_PRIVACY_NOTICE_VERSION",
        ).filter { providers.gradleProperty(it).isPresent }
        check(forbiddenGradleProperties.isEmpty()) {
            "Release PatrolGrid builds reject Gradle-property overrides for: " +
                forbiddenGradleProperties.joinToString(", ")
        }
        check(!providers.environmentVariable("PATROLGRID_MAP_STYLE_URL").isPresent) {
            "Release PatrolGrid builds reject PATROLGRID_MAP_STYLE_URL environment overrides."
        }
        check(!providers.environmentVariable("PATROLGRID_BACKEND_IDENTITY").isPresent) {
            "Release PatrolGrid backend identity is source-pinned and cannot be environment-overridden."
        }
        check(!providers.environmentVariable("PATROLGRID_PRIVACY_POLICY_STATUS").isPresent &&
            !providers.environmentVariable("PATROLGRID_PRIVACY_NOTICE_VERSION").isPresent
        ) {
            "Release PatrolGrid privacy approval and notice version are source-pinned and cannot be environment-overridden."
        }
        check(Regex("[0-9a-f]{40}").matches(releasePatrolGridCommit)) {
            "Release PatrolGrid builds require PATROLGRID_RELEASE_COMMIT as an exact lowercase full commit SHA."
        }
        val productionIdentityPath = patrolGridProductionIdentitySource.toPath()
        check(Files.isRegularFile(productionIdentityPath, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(productionIdentityPath) &&
            Files.size(productionIdentityPath) > 0L
        ) {
            "android/patrolgrid-production.properties must be a non-empty regular source file."
        }
        check(expectedProductionSupabaseUrl != "UNCONFIGURED" &&
            isHttpsUrl(expectedProductionSupabaseUrl) &&
            URI(expectedProductionSupabaseUrl).path.orEmpty().let { it.isEmpty() || it == "/" }
        ) {
            "A reviewed exact production Supabase origin must be source-pinned before release."
        }
        check(Regex("[0-9a-f]{64}").matches(expectedProductionSupabaseAnonKeySha256)) {
            "A reviewed production Supabase anon-key SHA-256 must be source-pinned before release."
        }
        check(expectedPrivacyPolicyStatus == "APPROVED") {
            "The source-pinned PatrolGrid privacy policy must be explicitly APPROVED before release."
        }
        check(expectedPrivacyNoticeVersion == 3) {
            "The source-pinned PatrolGrid privacy notice must be approved as version 3."
        }
        check(releaseSupabaseUrl == expectedProductionSupabaseUrl) {
            "The injected SUPABASE_URL does not match PatrolGrid's source-pinned production backend."
        }
        check(sha256Hex(releaseSupabaseAnonKey) == expectedProductionSupabaseAnonKeySha256) {
            "The injected SUPABASE_ANON_KEY does not match PatrolGrid's source-pinned key digest."
        }
        check(isHttpsUrl(releaseSupabaseUrl)) {
            "Release PatrolGrid builds require an HTTPS SUPABASE_URL."
        }
        check(releaseSupabaseAnonKey.isNotBlank()) {
            "Release PatrolGrid builds require SUPABASE_ANON_KEY."
        }
        check(releasePatrolGridMapStyleUrl == openFreeMapStyleUrl) {
            "Release PatrolGrid builds must use the source-pinned OpenFreeMap style."
        }
        check(isHttpsUrl(releasePatrolGridMapStyleUrl)) {
            "PatrolGrid's source-pinned OpenFreeMap style must use HTTPS."
        }
        check(isHttpsUrl(releasePatrolGridPrivacyPolicyUrl)) {
            "Release PatrolGrid builds require an HTTPS PATROLGRID_PRIVACY_POLICY_URL."
        }
        providers.environmentVariable("EXPECTED_PRIVACY_POLICY_URL")
            .getOrElse("")
            .trim()
            .takeIf(String::isNotBlank)
            ?.let { expectedPrivacyPolicyUrl ->
                check(releasePatrolGridPrivacyPolicyUrl == expectedPrivacyPolicyUrl) {
                    "The release privacy-policy URL does not match EXPECTED_PRIVACY_POLICY_URL."
                }
            }
        val privacyPolicyPath = patrolGridPrivacyPolicySource.toPath()
        check(Files.isRegularFile(privacyPolicyPath, LinkOption.NOFOLLOW_LINKS)) {
            "docs/PATROLGRID_PRIVACY_POLICY.md must exist as a regular, non-symlink source file."
        }
        check(!Files.isSymbolicLink(privacyPolicyPath) && Files.size(privacyPolicyPath) > 0L) {
            "docs/PATROLGRID_PRIVACY_POLICY.md must be a non-empty, non-symlink source file."
        }
        val privacyPolicyText = patrolGridPrivacyPolicySource.readText(Charsets.UTF_8)
        check("**Notice version:** 3" in privacyPolicyText &&
            "**Retention period:** 365 days" in privacyPolicyText &&
            "There is no separate PatrolGrid technical-support desk." in privacyPolicyText &&
            "subdivision supervisor through the existing official Department" in privacyPolicyText &&
            "normal chain of command" in privacyPolicyText &&
            "radio" in privacyPolicyText &&
            "official telephone" in privacyPolicyText
        ) {
            "The approved PatrolGrid policy is missing a required retention, request-route, no-desk, or emergency clause."
        }
        check("Deployment draft" !in privacyPolicyText) {
            "The PatrolGrid privacy policy is still marked as a deployment draft."
        }
        check(patrolGridApplicationId == "com.dailybeat.app.patrolgrid") {
            "Release PatrolGrid builds must use the permanent com.dailybeat.app.patrolgrid application id."
        }
    }
}

val verifyReleaseBuildConfigValues by tasks.registering {
    group = "verification"
    description = "Verifies that generated release BuildConfig contains only the intended PatrolGrid values."
    dependsOn("generateReleaseBuildConfig")
    doLast {
        val buildConfigFile = layout.buildDirectory.file(
            "generated/source/buildConfig/release/com/dailybeat/app/BuildConfig.java",
        ).get().asFile
        check(buildConfigFile.isFile) {
            "Generated release BuildConfig.java is missing."
        }
        val generatedSource = buildConfigFile.readText()

        fun verifySingleStringField(name: String, expectedValue: String) {
            val declaration =
                "public static final String $name = ${quotedBuildConfig(expectedValue)};"
            val matchingDeclarations = generatedSource.lineSequence()
                .map(String::trim)
                .filter { it.startsWith("public static final String $name = ") }
                .toList()
            check(matchingDeclarations == listOf(declaration)) {
                "Generated release BuildConfig field $name does not exactly match its intended value."
            }
        }

        verifySingleStringField("SUPABASE_URL", releaseSupabaseUrl)
        verifySingleStringField("SUPABASE_ANON_KEY", releaseSupabaseAnonKey)
        verifySingleStringField("PATROLGRID_RELEASE_COMMIT", releasePatrolGridCommit)
        verifySingleStringField("PATROLGRID_BACKEND_IDENTITY", expectedProductionSupabaseUrl)
        verifySingleStringField("PATROLGRID_PRIVACY_POLICY_STATUS", expectedPrivacyPolicyStatus)
        verifySingleStringField("PATROLGRID_PRIVACY_POLICY_URL", releasePatrolGridPrivacyPolicyUrl)
        verifySingleStringField("PATROLGRID_MAP_STYLE_URL", openFreeMapStyleUrl)
        verifySingleStringField("APPLICATION_ID", patrolGridApplicationId)
        check(
            generatedSource.lineSequence().map(String::trim).count {
                it == "public static final int PATROLGRID_RETENTION_DAYS = $patrolGridRetentionDays;"
            } == 1,
        ) {
            "Generated release BuildConfig does not contain the fixed PatrolGrid retention policy."
        }
        check(
            generatedSource.lineSequence().map(String::trim).count {
                it == "public static final int PATROLGRID_PRIVACY_NOTICE_VERSION = $expectedPrivacyNoticeVersion;"
            } == 1,
        ) {
            "Generated release BuildConfig does not contain the approved PatrolGrid privacy notice version."
        }
    }
}

val verifyUnsignedReleaseCandidate by tasks.registering {
    group = "verification"
    description = "Assembles and verifies the unsigned, minified PatrolGrid release candidate."
    dependsOn("assembleRelease")
    doLast {
        val unsignedApk = layout.buildDirectory.file(
            "outputs/apk/release/app-release-unsigned.apk",
        ).get().asFile
        val unexpectedlySignedApk = layout.buildDirectory.file(
            "outputs/apk/release/app-release.apk",
        ).get().asFile
        val releaseMapping = layout.buildDirectory.file(
            "outputs/mapping/release/mapping.txt",
        ).get().asFile

        check(unsignedApk.isFile && unsignedApk.length() > 0L) {
            "Unsigned release candidate is missing: ${unsignedApk.invariantSeparatorsPath}"
        }
        check(!unexpectedlySignedApk.exists()) {
            "Gradle unexpectedly produced a signed release APK."
        }
        check(releaseMapping.isFile && releaseMapping.length() > 0L) {
            "Minified release mapping is missing: ${releaseMapping.invariantSeparatorsPath}"
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseConfiguration)
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseBuildConfigValues)
}
