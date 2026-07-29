package dev.bee.beecode.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.FileSyncStore
import dev.bee.beecode.app.ProblemCatalogue
import dev.bee.beecode.app.RunOutcome
import dev.bee.beecode.app.SyncReport
import dev.bee.beecode.app.SyncService
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ReviewRating
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Sync between two profiles on a real device, through a real file.
 *
 * `SyncServiceTest` proves the loop on the JVM. This proves it on Android, against the
 * platform's own SQLite and filesystem — which is where ADR 0003's one-persistence-impl
 * decision could bite differently from the desktop.
 *
 * Uses [FileSyncStore] rather than [DocumentSyncStore] deliberately: a document URI needs
 * a picker and a human, and what is worth checking on-device is that the *engine* works
 * against Android's storage, not that the SAF plumbing does. The two stores are
 * interchangeable by construction — both hash their contents for the token.
 */
@RunWith(AndroidJUnit4::class)
class SyncInteropTest {

    @Test
    fun twoProfilesOnDeviceConvergeThroughOneFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        System.setProperty("java.io.tmpdir", context.cacheDir.absolutePath)
        System.setProperty("org.sqlite.tmpdir", context.cacheDir.absolutePath)

        val application = context.applicationContext as BeeCodeApplication
        val catalogue: ProblemCatalogue = application.catalogue
        val shared = File(context.cacheDir, "beecode-sync-\${System.nanoTime()}.json")

        val phone = BeeCodeProfile.inMemory(catalogue = catalogue, runner = application.runner)
        val tablet = BeeCodeProfile.inMemory(catalogue = catalogue, runner = application.runner)
        try {
            // Warm the interpreter before anything timed, as elsewhere on Android.
            phone.study.runnerStatus()

            solve(phone, "two-sum")
            val first = SyncService(FileSyncStore(shared), phone).sync(Clock.System.now())
            assertTrue("first sync should complete: \$first", first is SyncReport.Completed)
            assertTrue("the shared file must exist", shared.isFile)

            val second = SyncService(FileSyncStore(shared), tablet).sync(Clock.System.now())
            assertTrue("second sync should complete: \$second", second is SyncReport.Completed)

            // The second profile received the first's work, and rebuilt its schedule by
            // replaying the merged log rather than copying a projection.
            assertEquals(1, tablet.allReviews().size)
            assertTrue(tablet.reviews.schedule(ProblemId("two-sum")) != null)
            assertEquals(emptyList<ProblemId>(), tablet.verifyScheduleIntegrity())
            // And the source came across, which is most of what a learner wants back.
            assertEquals(
                phone.reviews.selectedSources().values.single(),
                tablet.reviews.selectedSources().values.single(),
            )
        } finally {
            phone.close()
            tablet.close()
            shared.delete()
            File(shared.absolutePath + ".tmp").delete()
        }
    }

    private suspend fun solve(profile: BeeCodeProfile, problem: String) {
        val problemId = ProblemId(problem)
        profile.study.open(problemId)
        val source = """
            def two_sum(nums, target):
                seen = {}
                for index, value in enumerate(nums):
                    if target - value in seen:
                        return [seen[target - value], index]
                    seen[value] = index
                return []
        """.trimIndent()
        val run = profile.study.run(problemId, source)
        val completed = run as RunOutcome.Completed
        profile.study.finalize(problemId, completed.run.id, ReviewRating.GOOD)
    }
}
