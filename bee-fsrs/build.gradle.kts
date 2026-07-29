plugins {
    alias(libs.plugins.kotlin.jvm)
}

// bee-fsrs is deliberately dependency-free apart from the Kotlin stdlib. It is
// pure memory mathematics with no clock, no storage, and no logging, so that
// BeeCode and any other consumer can pin it as the same tested artifact.
// See PROVENANCE.md for its origin and extraction rules.

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    api(kotlin("stdlib"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
