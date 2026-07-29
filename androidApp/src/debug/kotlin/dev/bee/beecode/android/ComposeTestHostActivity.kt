package dev.bee.beecode.android

import androidx.activity.ComponentActivity

/**
 * An empty Activity for Compose UI tests to host content in.
 *
 * Exists in the `debug` source set, so it is part of the **app** APK rather than the
 * test APK. That distinction is the whole reason this class exists: the debug build
 * type carries `applicationIdSuffix = ".debug"`, so the app is
 * `dev.bee.beecode.debug` while the test APK is `dev.bee.beecode.debug.test`. The
 * host activity that `ui-test-manifest` contributes lands in the test package, and
 * launching it from instrumentation targeting the app package fails with
 * "Intent in process ... resolved to different process".
 *
 * Declaring our own host in the app package removes the ambiguity. It ships only in
 * debug builds, never in a release APK.
 */
class ComposeTestHostActivity : ComponentActivity()
