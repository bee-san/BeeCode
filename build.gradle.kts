// Every plugin is resolved once here and applied per module. Chaquopy puts the
// Kotlin plugin on the build classpath itself, so a module that also declared a
// version for it would fail with "already on the classpath with an unknown
// version". Declaring them all here removes that class of conflict.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.chaquopy) apply false
}

// Every module targets the same JVM bytecode level so desktop and Android
// agree on semantics. Android's desugaring is configured per-module.
subprojects {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = libs.versions.jvmTarget.get()
        targetCompatibility = libs.versions.jvmTarget.get()
    }
    tasks.withType<Test>().configureEach {
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
