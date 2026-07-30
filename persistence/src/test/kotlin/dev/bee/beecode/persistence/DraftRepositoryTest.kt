package dev.bee.beecode.persistence

import dev.bee.beecode.domain.DeviceId
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemRevisionId
import dev.bee.beecode.fsrs.FsrsDefaults
import dev.bee.beecode.fsrs.SchedulerPolicy
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val T0 = Instant.parse("2026-07-29T12:00:00Z")

class DraftRepositoryTest {
    private lateinit var database: BeeCodeDatabase
    private lateinit var drafts: DraftRepository

    @BeforeTest
    fun setUp() {
        database = BeeCodeDatabase.inMemory()
        drafts = DraftRepository(database)
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun anUnopenedProblemHasNoDraft() {
        // Null rather than a synthesized starter, so the caller can distinguish
        // "never opened" from "opened and reset".
        assertNull(drafts.draft(ProblemId("two-sum")))
    }

    @Test
    fun loadOrStartSeedsFromTheStarterSource() {
        val draft = drafts.loadOrStart(problem(), T0)
        assertEquals(problem().starterSource, draft.source)
        assertTrue(draft.isPristine)
    }

    @Test
    fun aSavedDraftSurvivesAndIncrementsItsVersion() {
        // Losing typed source is data loss, so this is the most important
        // assertion in the file.
        val edited = drafts.loadOrStart(problem(), T0).copy(source = "def two_sum(nums, target):\n    return [0, 1]\n")
        val saved = assertNotNull(drafts.save(edited, T0))

        assertEquals(1L, saved.version)
        val reloaded = assertNotNull(drafts.draft(ProblemId("two-sum")))
        assertEquals(edited.source, reloaded.source)
        assertFalse(reloaded.isPristine)
    }

    @Test
    fun aDraftSurvivesReopeningTheDatabase() {
        // Simulates a process kill: the file is all that carries the source over.
        val file = kotlin.io.path.createTempFile("beecode-draft-", ".db").toFile()
        try {
            BeeCodeDatabase.open(file.absolutePath).use { database ->
                val repository = DraftRepository(database)
                val edited = repository.loadOrStart(problem(), T0).copy(source = "half-written code")
                repository.save(edited, T0)
            }
            BeeCodeDatabase.open(file.absolutePath).use { database ->
                val reloaded = assertNotNull(DraftRepository(database).draft(ProblemId("two-sum")))
                assertEquals("half-written code", reloaded.source)
            }
        } finally {
            file.delete()
            java.io.File(file.absolutePath + "-wal").delete()
            java.io.File(file.absolutePath + "-shm").delete()
        }
    }

    @Test
    fun aStaleSaveCannotOverwriteANewerDraft() {
        // Autosave and explicit save can overlap, and an autosave triggered by an
        // earlier keystroke must not land after a later save and resurrect old
        // text.
        val base = drafts.loadOrStart(problem(), T0)
        val first = assertNotNull(drafts.save(base.copy(source = "newest"), T0))
        assertEquals(1L, first.version)

        // A save built from the pre-save snapshot: same version the first one used.
        assertNull(drafts.save(base.copy(source = "stale"), T0), "a stale save must be rejected")
        assertEquals("newest", drafts.draft(ProblemId("two-sum"))!!.source)
    }

    @Test
    fun anEditedDraftIsKeptWhenTheProblemContentChanges() {
        // Throwing away typed code because the Problem was updated would be
        // indefensible. The source is kept; the starter baseline is refreshed so
        // "reset to starter" gives the current starter.
        drafts.save(drafts.loadOrStart(problem(), T0).copy(source = "my work"), T0)

        val updated = problem().copy(
            revisionId = ProblemRevisionId("b".repeat(64)),
            starterSource = "def two_sum(nums, target):\n    # new starter\n    pass\n",
        )
        val migrated = drafts.loadOrStart(updated, T0.plusDays(1))

        assertEquals("my work", migrated.source, "the learner's code must be preserved")
        assertEquals(updated.starterSource, migrated.starterBaseline)
        assertEquals(updated.revisionId, migrated.problemRevisionId)
    }

    @Test
    fun aPristineDraftAdoptsTheNewStarterWhenContentChanges() {
        // Nothing was typed, so there is nothing to preserve and showing the stale
        // starter would just be confusing.
        drafts.save(drafts.loadOrStart(problem(), T0), T0)

        val updated = problem().copy(
            revisionId = ProblemRevisionId("c".repeat(64)),
            starterSource = "def two_sum(nums, target):\n    # improved\n    pass\n",
        )
        val migrated = drafts.loadOrStart(updated, T0.plusDays(1))

        assertEquals(updated.starterSource, migrated.source)
        assertTrue(migrated.isPristine)
    }

    @Test
    fun deletingADraftRemovesIt() {
        drafts.save(drafts.loadOrStart(problem(), T0).copy(source = "x"), T0)
        drafts.delete(ProblemId("two-sum"))
        assertNull(drafts.draft(ProblemId("two-sum")))
    }

    @Test
    fun allDraftsReturnsEveryStoredDraftForExport() {
        drafts.save(drafts.loadOrStart(problem("two-sum"), T0).copy(source = "a"), T0)
        drafts.save(drafts.loadOrStart(problem("valid-parentheses"), T0).copy(source = "b"), T0)
        assertEquals(2, drafts.allDrafts().size)
    }
}

class SettingsRepositoryTest {
    private lateinit var database: BeeCodeDatabase
    private lateinit var settings: SettingsRepository

    @BeforeTest
    fun setUp() {
        database = BeeCodeDatabase.inMemory()
        settings = SettingsRepository(database)
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun theDeviceIdIsGeneratedOnceAndThenStable() {
        // ADR 0002 property 4. Two calls must not mint two identities, or a future
        // sync could not tell which writes were its own.
        var generated = 0
        val generate = { generated++; DeviceId("device-generated-$generated") }

        val first = settings.deviceId(generate, T0)
        val second = settings.deviceId(generate, T0.plusDays(1))

        assertEquals(first, second)
        assertEquals(1, generated, "the identity must be generated exactly once")
    }

    @Test
    fun theStreakZoneRoundTripsAndDefaultsToTheSystemZone() {
        // Stored rather than read from the system each time, so a learner who
        // travels does not have their streak history recomputed in a new zone.
        assertEquals(TimeZone.currentSystemDefault(), settings.streakZone())

        settings.setStreakZone(TimeZone.of("Europe/London"), T0)
        assertEquals(TimeZone.of("Europe/London"), settings.streakZone())
    }

    @Test
    fun anUnparseableStreakZoneFallsBackRatherThanThrowing() {
        // A corrupted setting must not make the app unopenable.
        settings.put(SettingsRepository.KEY_STREAK_ZONE, "Not/AZone", T0)
        assertEquals(TimeZone.currentSystemDefault(), settings.streakZone())
    }

    @Test
    fun theSchedulerPolicyRoundTripsIncludingParameters() {
        // A real parameter vector, nudged, rather than a synthetic ramp: FSRS-7
        // validates against upstream's clipper bounds, so an arbitrary ramp is
        // rejected at construction and would test nothing about round-tripping.
        val custom = SchedulerPolicy(
            desiredRetention = 0.85,
            maximumIntervalDays = 3_650.0,
            parameters = FsrsDefaults.parameters().also { it[4] = it[4] - 0.5 },
        )
        settings.setSchedulerPolicy(custom, T0)
        assertEquals(custom, settings.schedulerPolicy())
    }

    @Test
    fun theSchedulerPolicyDefaultsWhenUnset() {
        assertEquals(SchedulerPolicy.DEFAULT, settings.schedulerPolicy())
    }

    @Test
    fun aCorruptedPolicyValueFallsBackToTheDefault() {
        settings.put(SettingsRepository.KEY_DESIRED_RETENTION, "not-a-number", T0)
        assertEquals(SchedulerPolicy.DEFAULT_DESIRED_RETENTION, settings.schedulerPolicy().desiredRetention)

        // A parameter list of the wrong length is ignored rather than accepted.
        settings.put(SettingsRepository.KEY_FSRS_PARAMETERS, "1.0,2.0,3.0", T0)
        assertNull(settings.schedulerPolicy().parameters)
    }

    @Test
    fun anOutOfRangeRetentionFallsBackInsteadOfCrashing() {
        // SchedulerPolicy rejects retention outside (0, 1). Reading a bad stored
        // value must degrade to the default, not throw on app launch.
        settings.put(SettingsRepository.KEY_DESIRED_RETENTION, "1.5", T0)
        assertEquals(SchedulerPolicy.DEFAULT, settings.schedulerPolicy())
    }

    @Test
    fun theDailyReviewLimitRoundTripsAndClears() {
        assertNull(settings.dailyReviewLimit())
        settings.setDailyReviewLimit(20, T0)
        assertEquals(20, settings.dailyReviewLimit())
        settings.setDailyReviewLimit(null, T0)
        assertNull(settings.dailyReviewLimit())
    }

    @Test
    fun aNonPositiveDailyLimitIsTreatedAsNoLimit() {
        settings.put(SettingsRepository.KEY_DAILY_REVIEW_LIMIT, "0", T0)
        assertNull(settings.dailyReviewLimit())
    }

    @Test
    fun progressVisibilityDefaultsToEnabledAndRoundTrips() {
        assertTrue(settings.showProgress())

        settings.setShowProgress(false, T0)
        assertFalse(settings.showProgress())

        settings.setShowProgress(true, T0.plusDays(1))
        assertTrue(settings.showProgress())
    }

    @Test
    fun motivationVisibilityDefaultsToEnabledAndRoundTrips() {
        assertTrue(settings.showStreaksAndAchievements())

        settings.setShowStreaksAndAchievements(false, T0)
        assertFalse(settings.showStreaksAndAchievements())

        settings.setShowStreaksAndAchievements(true, T0.plusDays(1))
        assertTrue(settings.showStreaksAndAchievements())
    }

    @Test
    fun malformedVisibilitySettingsFailOpen() {
        settings.put(SettingsRepository.KEY_SHOW_PROGRESS, "sometimes", T0)
        settings.put(SettingsRepository.KEY_SHOW_STREAKS_AND_ACHIEVEMENTS, "0", T0)

        assertTrue(settings.showProgress())
        assertTrue(settings.showStreaksAndAchievements())
    }

    @Test
    fun thePythonExecutablePathRoundTripsAndClears() {
        assertNull(settings.pythonExecutable())
        settings.setPythonExecutable("/usr/local/bin/python3.12", T0)
        assertEquals("/usr/local/bin/python3.12", settings.pythonExecutable())
        settings.setPythonExecutable("   ", T0)
        assertNull(settings.pythonExecutable(), "a blank path must clear rather than store whitespace")
    }

    @Test
    fun writingASettingTwiceUpdatesRatherThanDuplicating() {
        settings.put("k", "first", T0)
        settings.put("k", "second", T0.plusDays(1))
        assertEquals("second", settings.get("k"))
        assertEquals(1, settings.all().size)
    }
}
