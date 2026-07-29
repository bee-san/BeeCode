plugins {
    alias(libs.plugins.kotlin.jvm)
}

// A vendored checkout of dev.bee:bee-fsrs 0.1.0 from
// https://github.com/bee-san/bee-fsrs — see PROVENANCE.md.
//
// Do not edit the sources here. Change the engine upstream and re-vendor, or BeeCode
// silently forks the mathematics kanji_anki also depends on. FsrsProvenanceTest
// asserts this copy is the version it claims to be.
//
// Vendored rather than resolved because the package is not on Maven Central yet, and a
// composite build or submodule would break offline and fresh-clone builds. Reversible:
// fsrs-adapter is the only module that imports dev.bee.fsrs.
//
// Dependency-free apart from the Kotlin stdlib, with no clock, storage, or logging, so
// any consumer can pin it as the same tested artifact.

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
