plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Loads, validates, and compiles one-folder Problem definitions into the runtime
// representation the clients consume.
//
// This module is used at build time by the validator and at runtime by the
// clients to read a compiled pack, so it must stay free of desktop-only APIs.

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":domain"))
    api(project(":python-api"))
    implementation(libs.snakeyaml)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnit()
    // The validator runs reference solutions through real Python, so tests need
    // the repository root to locate the content directory.
    systemProperty("beecode.repoRoot", rootProject.projectDir.absolutePath)
}
