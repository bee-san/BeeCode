plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.chaquopy)
}

// The Android client. Owns the entry point, lifecycle, storage locations, and the
// Chaquopy-backed Python runner. Everything else — the study loop, FSRS,
// persistence, content — comes from the shared modules, so Android and desktop
// cannot disagree about what a review means.

android {
    namespace = "dev.bee.beecode"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.bee.beecode"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = libs.versions.appVersionCode.get().toInt()
        versionName = libs.versions.appVersionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // The two ABIs the plan requires evidence on: x86_64 for the emulator,
            // arm64-v8a for a physical device. Restricting the set keeps the APK
            // from carrying two more CPython builds nobody will run.
            abiFilters += listOf("x86_64", "arm64-v8a")
        }
    }

    /**
     * Release signing.
     *
     * An installable APK must be signed, and an *unsigned* release APK cannot be
     * installed at all. There is no published keystore for this project yet, so the
     * build looks for one and falls back to the debug key when it is absent.
     *
     * That fallback is honest but consequential: an APK signed with a locally
     * generated key cannot be upgraded in place by one signed with a different key,
     * so a learner would have to uninstall before updating. The release notes say so.
     * A real signing key belongs in repository secrets before any wide distribution.
     */
    val keystoreFile = rootProject.file("release.keystore")
    val keystorePassword: String? = System.getenv("BEECODE_KEYSTORE_PASSWORD")

    signingConfigs {
        if (keystoreFile.exists() && keystorePassword != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = System.getenv("BEECODE_KEY_ALIAS") ?: "beecode"
                keyPassword = System.getenv("BEECODE_KEY_PASSWORD") ?: keystorePassword
            }
        }
    }

    buildTypes {
        release {
            // Off deliberately. The Chaquopy runner reaches Python by name and the
            // JDBC driver loads classes reflectively, so proving a shrinker's keep
            // rules are complete is real work — and getting it wrong produces an app
            // that fails only at run time, on a learner's device.
            isMinifyEnabled = false

            signingConfig = signingConfigs.findByName("release")
                // Without a keystore, sign with the debug key so the APK installs.
                // Verified in the release workflow, which fails if the output is
                // unsigned rather than shipping something that cannot be opened.
                ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the real resources and manifest, not the stubbed
            // android.jar, to inflate a Compose host activity.
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
            // The compiled Problem pack, generated into the APK's assets by the
            // :bundleProblemPack task below.
            assets.srcDirs(layout.buildDirectory.dir("generated/beecode-assets"))
            // SQLite's native library, extracted by :extractSqliteNatives. See that
            // task for why it cannot simply ride along inside the jar.
            jniLibs.srcDirs(layout.buildDirectory.dir("generated/beecode-jniLibs"))
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/kotlin")
        }
        // `src/testDebug`, not `src/test`. The Robolectric UI tests host content in
        // ComposeTestHostActivity, which lives in `src/debug` so it never ships in a
        // release APK. AGP compiles `src/test` into *every* unit-test variant, so
        // putting them there breaks :compileReleaseUnitTestKotlin with an unresolved
        // reference — the debug-only variant directory is what scopes them correctly.
        getByName("testDebug") {
            java.srcDirs("src/testDebug/kotlin")
        }
    }

    packaging {
        resources {
            // The shared modules and their dependencies each ship licence and
            // metadata files that collide when merged.
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
            )
        }
    }

    lint {
        abortOnError = false
    }
}

chaquopy {
    defaultConfig {
        // Pinned deliberately. The Python version decides what a learner's code can
        // do, so it is part of BeeCode's behaviour contract and must not drift with
        // the toolchain.
        version = "3.12"
    }
    // No pip block: the harness uses only the standard library, and installing
    // packages into learner Python is explicitly post-v1.
}

dependencies {
    implementation(project(":shared"))

    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation("androidx.compose.ui:ui-tooling")
    // Robolectric needs the test manifest's host activity on the *unit* test
    // classpath too; createAndroidComposeRule launches it there just as it does
    // on a device.
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Local JVM Compose UI tests. These exist because the instrumented UI tests
    // require an emulator that accepts injected touch input, and neither this dev
    // host (no /dev/kvm, so only non-rendering ATD images boot) nor CI's -no-window
    // emulator provides one. Robolectric runs the same assertions against a real
    // composed tree on the JVM, so the UI is actually verified somewhere.
    testImplementation(libs.robolectric)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation(platform("androidx.compose:compose-bom:2025.09.01"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Compose UI tests assert the real composed tree, which on a headless test
    // device is stronger evidence than a screenshot: an ATD image renders no
    // pixels at all, but the semantics tree is fully present.
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // The empty host Activity createComposeRule launches. It must be merged into the
    // *test* APK's manifest, not the app's, or the rule fails with "Unable to
    // resolve activity for ComponentActivity".
    androidTestImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.09.01"))
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

/**
 * Compile the Problem pack into the APK's assets.
 *
 * The client ships *data*: the pack is compiled from the authoring directories at
 * build time so the app never parses YAML or touches `reference.py`. Wiring it as
 * a real task with declared inputs and outputs means editing a Problem rebuilds
 * the pack, and nothing else does.
 */
// A dedicated configuration rather than reaching into :content-tools's own
// sourceSets. Cross-project classpath access is unsafe under Gradle's isolated
// project locking and fails outright on 9.x.
val packCompiler: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    packCompiler(project(":content-tools"))
}

val bundleProblemPack by tasks.registering(JavaExec::class) {
    group = "beecode"
    description = "Compiles content/packs/core into the APK assets."

    val packSource = rootProject.layout.projectDirectory.dir("content/packs/core")
    val outputFile = layout.buildDirectory.file("generated/beecode-assets/problems.json")

    inputs.dir(packSource).withPropertyName("packSource")
    outputs.file(outputFile).withPropertyName("packJson")

    classpath = packCompiler
    mainClass.set("dev.bee.beecode.content.PackCompilerKt")
    argumentProviders.add {
        listOf(packSource.asFile.absolutePath, outputFile.get().asFile.absolutePath)
    }
}

/**
 * Extract SQLite's Android native libraries into `jniLibs`.
 *
 * sqlite-jdbc bundles native libraries inside its own jar under
 * `org/sqlite/native/Linux-Android/<abi>/`. On desktop the driver extracts them to
 * a temp directory at runtime; on Android that does not work, and the reason is
 * worth recording because the failure is a bare `UnsatisfiedLinkError`:
 *
 * - AGP strips `.so` files out of merged java resources, because native libraries
 *   are supposed to travel in `lib/<abi>/`. So the files silently never reach the
 *   APK at all — verified by unzipping it and finding only the Mac and Windows
 *   binaries, which AGP left alone precisely because they are not `.so`.
 * - Even if they did survive, Android's linker will not `dlopen` a path inside an
 *   APK, and extracting to a writable directory then loading by absolute path is
 *   not what the driver does on this platform.
 *
 * Copying them to `lib/<abi>/` at build time means `System.loadLibrary` finds them
 * the ordinary way, with no runtime extraction and no writable-directory
 * dependency. Only the two ABIs BeeCode ships are copied.
 */
val extractSqliteNatives by tasks.registering(Copy::class) {
    group = "beecode"
    description = "Copies sqlite-jdbc's Android native libraries into jniLibs."

    val sqliteJar = configurations.detachedConfiguration(
        dependencies.create(libs.sqlite.jdbc.get().toString()),
    ).apply { isTransitive = false }

    from(sqliteJar.map { zipTree(it) }) {
        include("org/sqlite/native/Linux-Android/x86_64/**")
        include("org/sqlite/native/Linux-Android/aarch64/**")
        eachFile {
            // org/sqlite/native/Linux-Android/aarch64/libsqlitejdbc.so
            //   -> arm64-v8a/libsqlitejdbc.so
            // Chaquopy and AGP both expect Android's ABI names, which differ from
            // the ones sqlite-jdbc uses for the same architectures.
            val abi = when (relativePath.segments.getOrNull(4)) {
                "x86_64" -> "x86_64"
                "aarch64" -> "arm64-v8a"
                else -> return@eachFile
            }
            relativePath = RelativePath(true, abi, name)
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/beecode-jniLibs"))
}

// Matched by name for the same reason as the native-lib wiring below: these are AGP
// internals, and pointing a source set at a generated directory does not by itself
// establish the task dependency Gradle requires. Lint's model writer reads the asset
// directory too, so `./gradlew build` fails without this even though `assembleDebug`
// succeeds.
tasks.withType<com.android.build.gradle.tasks.MergeSourceSetFolders>().configureEach {
    dependsOn(bundleProblemPack)
}

tasks.matching { it.name.startsWith("generate") && it.name.contains("LintReportModel") }
    .configureEach { dependsOn(bundleProblemPack, extractSqliteNatives) }

tasks.matching { it.name.startsWith("generate") && it.name.contains("LintVitalReportModel") }
    .configureEach { dependsOn(bundleProblemPack, extractSqliteNatives) }

tasks.matching { it.name.startsWith("lint") }
    .configureEach { dependsOn(bundleProblemPack, extractSqliteNatives) }

// Matched by name rather than by type: the merge tasks are AGP internals that are
// not on the buildscript classpath, and pointing a source set at a generated
// directory does not by itself establish the task dependency Gradle requires.
// Both the folder merge and the native-lib merge consume this output.
tasks.matching {
    it.name.startsWith("merge") &&
        (it.name.contains("JniLibFolders") || it.name.contains("NativeLibs"))
}.configureEach { dependsOn(extractSqliteNatives) }
