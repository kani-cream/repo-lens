import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))

    intellijPlatform {
        // Since 2025.3 (253) IntelliJ IDEA ships as a single unified distribution;
        // the separate Community (IC) artifact is no longer published.
        intellijIdea("2026.1")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // The unified IntelliJ IDEA distribution bundles language plugins whose listeners
    // fail to initialize in the headless test harness. Repo Lens Core only needs the
    // platform, so tests load just this plugin.
    systemProperty("idea.load.plugins.id", "com.kanicream.repolens")
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "262.*"
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}
