plugins {
    kotlin("jvm") version "2.2.21" apply false
    id("org.jetbrains.intellij.platform") version "2.18.1" apply false
}

allprojects {
    group = "com.kanicream.repolens"
    // Release ordinal (SemVer-style). Independent of docs/milestones vX.Y, which are
    // theme names - the pull-forward broke any 1:1 mapping (see milestones/README.md).
    // 0.1.0 = core workflow; 0.2.0 = language providers + capability view + hardening.
    version = "0.2.0"
}
