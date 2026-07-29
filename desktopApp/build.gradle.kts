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

    // Compose UI tests for the desktop client. These need no display: Compose
    // Desktop's `runComposeUiTest` composes and lays out headlessly on the JVM, so
    // unlike the Android instrumented tests there is no emulator in the way.
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

/**
 * Normalise a version for installer formats that reject a zero major.
 *
 * macOS requires `CFBundleShortVersionString` to start at 1, and jpackage enforces
 * it — `0.1.0` fails outright. Rather than inflate BeeCode's real version to satisfy
 * a packaging rule, the *installer* version is normalised and the application keeps
 * its own; the release tag and the About screen still say 0.1.0.
 *
 * Any pre-1.0 version maps to a flat `1.0.0` rather than substituting the major.
 * Substituting would turn 0.1.0 into "1.1.0", which reads as a released 1.1 and is
 * worse than obviously-a-placeholder.
 */
fun macPackageVersion(version: String): String {
    val major = version.substringBefore('.').toIntOrNull() ?: 0
    return if (major >= 1) version else "1.0.0"
}

compose.desktop {
    application {
        mainClass = "dev.bee.beecode.desktop.MainKt"

        nativeDistributions {
            // jpackage can only build for the OS it runs on, so the format set is
            // chosen per host rather than declared once. A release therefore needs a
            // macOS runner to produce a .dmg — it cannot be cross-built from Linux.
            val os = System.getProperty("os.name").orEmpty().lowercase()
            when {
                os.contains("mac") -> targetFormats(TargetFormat.Dmg)
                os.contains("win") -> targetFormats(TargetFormat.Msi)
                // Deb needs dpkg, which is absent on some build hosts; AppImage does
                // not, so listing both means a Linux build still produces something.
                else -> targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            }

            packageName = "BeeCode"
            packageVersion = libs.versions.appVersionName.get()
            description = "Offline spaced-repetition practice for algorithm Problems"
            // macOS and Windows installer formats reject a zero major version —
            // jpackage fails with "'0.1.0' is not a valid version" — so the native
            // package version is normalised below while the app keeps its real one.
            vendor = "bee-san"
            licenseFile.set(rootProject.file("LICENSE").takeIf { it.exists() })

            linux {
                menuGroup = "Development"
                appCategory = "Development"
            }

            macOS {
                // A stable reverse-DNS identifier, so macOS treats an upgrade as the
                // same app rather than a second copy.
                bundleID = "dev.bee.beecode"
                packageName = "BeeCode"
                appCategory = "public.app-category.developer-tools"
                // CFBundleShortVersionString must have a major of at least 1 on
                // macOS. 0.1.0 is rejected outright by jpackage, so the DMG carries
                // 1.0.0 while the app's own version stays 0.1.0 — which is what the
                // release tag and the About screen show.
                packageVersion = macPackageVersion(libs.versions.appVersionName.get())
                dmgPackageVersion = macPackageVersion(libs.versions.appVersionName.get())
                // Unsigned and un-notarized: there is no Apple Developer certificate
                // for this project. Gatekeeper will refuse the first launch, and the
                // release notes tell the user how to open it anyway. Claiming to sign
                // without a certificate would just fail the build.
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
