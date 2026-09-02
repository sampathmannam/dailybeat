import org.gradle.api.artifacts.dsl.LockMode

buildscript {
    configurations.classpath {
        resolutionStrategy.force(
            "org.bouncycastle:bcpkix-jdk18on:1.84",
            "org.bouncycastle:bcprov-jdk18on:1.84",
            "org.bouncycastle:bcutil-jdk18on:1.84",
        )
        // AGP also places Unified Test Platform libraries on its build-host
        // classpath before project configurations exist. Patch that tooling
        // graph here; these modules are never packaged into the application.
        resolutionStrategy.eachDependency {
            when (requested.group) {
                "com.google.protobuf" -> useVersion("3.25.5")
                "io.netty" -> useVersion("4.1.136.Final")
            }
        }
    }
}

plugins {
    id("com.android.application") version "8.11.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}

allprojects {
    // Android Gradle Plugin's Unified Test Platform runs on the build host and
    // is not packaged into PatrolGrid. Keep that isolated tool graph patched
    // without forcing application/runtime dependency versions.
    configurations.configureEach {
        if (name.startsWith("_internal-unified-test-platform-")) {
            resolutionStrategy.eachDependency {
                when (requested.group) {
                    "com.google.protobuf" -> useVersion("3.25.5")
                    "io.netty" -> useVersion("4.1.136.Final")
                }
            }
        }
    }

    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
    }
}
