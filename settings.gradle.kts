pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Provisions the JDK 21 toolchain when the build machine has no matching JDK
    // (e.g. when only the IDE's bundled JBR is installed).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "repo-lens"

include(":core")
include(":plugin")
