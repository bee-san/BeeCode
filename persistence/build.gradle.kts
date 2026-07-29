plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Local SQLite persistence. SQLite is the authority for all study state: drafts,
// reviews, schedules, and achievements. Nothing here requires a network.
//
// The JDBC driver is used on both desktop and Android in this first slice, so a
// single implementation and a single migration path serve both. If Android later
// needs its own driver, it goes behind the same repository contract rather than
// duplicating the schema.

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":domain"))
    api(project(":fsrs-adapter"))
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnit()
}
