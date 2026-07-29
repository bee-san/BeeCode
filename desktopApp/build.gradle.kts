import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
}

// The desktop client. Owns the window, the profile location, and the
// process-based Python runner. Everything else comes from the shared modules, so
// desktop and Android cannot disagree about what a review means.

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    // kotlinx-datetime is used directly by this module (Clock.System in Settings)
    // and must be a declared dependency rather than inherited transitively.
    implementation(libs.kotlinx.datetime)

    // The compiled pack reaches the classpath through the main source set's
    // generated resources directory, declared below. It is deliberately *not* a
    // `runtimeOnly(files(...))` dependency: that put the directory on the runtime
    // classpath in place of the module's transitive dependencies, and the app failed
    // at run time with NoClassDefFoundError for kotlinx-datetime.
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "dev.bee.beecode.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "BeeCode"
            packageVersion = libs.versions.appVersionName.get()
            description = "Offline spaced-repetition practice for algorithm Problems"
            vendor = "bee-san"
            licenseFile.set(rootProject.file("LICENSE").takeIf { it.exists() })

            linux {
                menuGroup = "Development"
                appCategory = "Development"
            }

            modules(
                // sqlite-jdbc needs the JDBC API, which is not in java.base.
                "java.sql",
                // Chosen by the JVM's own logging config on some distributions.
                "java.naming",
            )
        }
    }
}

/**
 * Compile the Problem pack onto the runtime classpath.
 *
 * Same source and same compiler the Android build uses, so both clients ship
 * byte-identical content. A dedicated resolvable configuration rather than reaching
 * into `:content-tools`'s own source sets, which is unsafe under Gradle's project
 * locking.
 */
val packCompiler: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    packCompiler(project(":content-tools"))
}

val bundlePack by tasks.registering(JavaExec::class) {
    group = "beecode"
    description = "Compiles content/packs/core onto the desktop runtime classpath."

    val packSource = rootProject.layout.projectDirectory.dir("content/packs/core")
    val outputFile = layout.buildDirectory.file(
        "generated/resources/dev/bee/beecode/problems.json",
    )

    inputs.dir(packSource).withPropertyName("packSource")
    outputs.file(outputFile).withPropertyName("packJson")

    classpath = packCompiler
    mainClass.set("dev.bee.beecode.content.PackCompilerKt")
    argumentProviders.add {
        listOf(packSource.asFile.absolutePath, outputFile.get().asFile.absolutePath)
    }
}

tasks.named("processResources") { dependsOn(bundlePack) }

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("generated/resources"))
    }
}

tasks.test {
    useJUnit()
    dependsOn(bundlePack)
}
