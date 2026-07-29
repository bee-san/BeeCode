plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The domain is pure: models, value types, and state machines. It must not
// import Compose, Android, SQL, HTTP, or any Python-provider class, and it must
// not read a clock or the filesystem. Time and identity arrive as inputs so
// every rule here is deterministically testable.

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(libs.kotlinx.datetime)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
