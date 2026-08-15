import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

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
        // the separate Community (IC) artifact is no longer published. Pinned to the
        // 2026.1.5 patch (build 261.27258): current marketplace plugins for the 261
        // line, the Go plugin included, require a newer build than the initial
        // 2026.1 release provides. The baseline stays 2026.1 / since-build 261.
        intellijIdea("2026.1.5")
        // UAST ships inside the Java plugin and its types extend Java PSI, so the Java
        // plugin is needed to compile the structure provider. At runtime it stays an
        // optional dependency (see repo-lens-uast.xml): Repo Lens Core must still load
        // in IDEs without Java.
        bundledPlugin("com.intellij.java")
        // Go PSI for the Go structure provider. Marketplace plugin (not bundled);
        // at runtime it stays an optional dependency like the UAST provider.
        plugin("org.jetbrains.plugins.go", "261.26222.22")
        // JS/TS PSI for the JavaScript structure provider; bundled in the unified
        // distribution and likewise optional at runtime.
        bundledPlugin("JavaScript")
        // Test scope only: Repo Lens references no Kotlin API, and the bundled Kotlin
        // plugin carries newer Kotlin metadata than this module compiles against, so it
        // must not reach the compile classpath. The tests need it loaded to prove that
        // Kotlin structure extraction works through the same UAST path as Java.
        testBundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

tasks.named<RunIdeTask>("runIde") {
    // Repo Lens is not unload-safe: its class loader cannot be released, so when a
    // rebuild replaces the jars under a running sandbox the IDE's hot-swap fails with
    // "plugin is still loaded". Restarting the sandbox is the supported way to pick up
    // changes, so do not let it try.
    systemProperty("idea.auto.reload.plugins", "false")
}

tasks.compileTestKotlin {
    // The bundled Kotlin plugin carries Kotlin 2.4 metadata while this module compiles
    // with 2.2. Tests only need that plugin on the classpath so Kotlin files parse; no
    // Kotlin plugin API is referenced, so skipping the check affects nothing we call.
    compilerOptions.freeCompilerArgs.add("-Xskip-metadata-version-check")
}

tasks.test {
    // The unified IntelliJ IDEA distribution bundles language plugins whose listeners
    // fail to initialize in the headless test harness, so tests load only what they
    // need: this plugin plus Java, which supplies UAST for the structure provider.
    systemProperty(
        "idea.load.plugins.id",
        "com.kanicream.repolens,com.intellij.java,org.jetbrains.kotlin,org.jetbrains.plugins.go,JavaScript",
    )
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
