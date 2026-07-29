plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Application services shared by desktop and Android: the study loop, statistics,
// achievements, and the profile that wires them together.
//
// Plain Kotlin/JVM rather than a Compose module. Both clients run on the JVM, and
// keeping the application layer UI-free means the whole study loop is testable
// without a UI toolkit — which is how the fail-then-fix journey gets an automated
// test rather than a manual one.

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":domain"))
    api(project(":persistence"))
    api(project(":python-api"))
    api(project(":content-tools"))
    api(project(":fsrs-adapter"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnit()
    systemProperty("beecode.repoRoot", rootProject.projectDir.absolutePath)
}
