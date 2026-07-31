package dev.bee.beecode.design

import dev.bee.beecode.persistence.BeeCodeDatabase
import dev.bee.beecode.persistence.SettingsRepository
import kotlinx.datetime.Instant
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Theme selection is two independent settings, and both survive a relaunch.
 *
 * ## Why two settings rather than one list
 *
 * The obvious design is one flat list — System, Honey dark, Honey light, High contrast
 * dark… — and it quietly removes a capability: picking a family would mean giving up
 * following the OS, because "follow the OS" is not a member of a list of concrete
 * schemes. Splitting them keeps every combination reachable, and these tests pin that
 * property rather than the implementation that currently provides it.
 *
 * ## Why persistence is exercised through a real database
 *
 * The two accessors do not just serialise an enum: they *clear* the key on the default
 * value, so "never chose" and "chose the default" cannot become two distinguishable
 * states. That behaviour only exists across a write and a re-read, and asserting it
 * against an in-memory map would be asserting the map.
 */
class ThemeSelectionTest {

    private lateinit var databaseFile: File
    private lateinit var database: BeeCodeDatabase

    @BeforeTest
    fun setUp() {
        databaseFile = kotlin.io.path.createTempFile("beecode-theme-", ".db").toFile()
        databaseFile.delete()
        database = BeeCodeDatabase.open(databaseFile.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        database.close()
        databaseFile.delete()
        File(databaseFile.absolutePath + "-wal").delete()
        File(databaseFile.absolutePath + "-shm").delete()
    }

    @Test
    fun aFreshProfileFollowsTheSystemInTheDefaultFamily() {
        assertEquals(ThemeChoice.SYSTEM, settings().themeChoice())
        assertEquals(ThemeFamily.Default, settings().themeFamily())
        assertEquals(ThemeFamily.HONEY, ThemeFamily.Default, "Honey is BeeCode's own palette")
    }

    @Test
    fun aChosenFamilySurvivesARelaunch() {
        settings().setThemeFamily(ThemeFamily.SLATE, NOW)
        // A second repository over the same file: what the next launch constructs.
        assertEquals(ThemeFamily.SLATE, settings().themeFamily())
    }

    @Test
    fun choosingTheDefaultFamilyClearsTheKeyRatherThanStoringItsName() {
        val settings = settings()
        settings.setThemeFamily(ThemeFamily.HIGH_CONTRAST, NOW)
        assertEquals("HIGH_CONTRAST", settings.appThemeFamily())

        settings.setThemeFamily(ThemeFamily.Default, NOW)
        assertNull(
            settings.appThemeFamily(),
            "the default must clear the key — otherwise 'never chose' and 'chose Honey' " +
                "are two states that can disagree, and a future change to Default would " +
                "silently not apply to anyone who had ever opened Settings",
        )
        assertEquals(ThemeFamily.Default, settings.themeFamily())
    }

    @Test
    fun anUnrecognisedStoredFamilyFallsBackRatherThanThrowing() {
        val settings = settings()
        // What a profile written by a newer BeeCode looks like to an older one, after a
        // sync or a restored backup. Throwing here would make the app unopenable over a
        // preference, which is the worst possible trade for a colour.
        settings.setAppThemeFamily("AURORA", NOW)
        assertEquals(ThemeFamily.Default, settings.themeFamily())
    }

    @Test
    fun theFamilyAndTheModeAreIndependent() {
        val settings = settings()
        settings.setThemeChoice(ThemeChoice.LIGHT, NOW)
        settings.setThemeFamily(ThemeFamily.SLATE, NOW)

        // Neither write clobbered the other. They are separate keys precisely so that
        // choosing a family does not mean re-choosing a mode.
        assertEquals(ThemeChoice.LIGHT, settings.themeChoice())
        assertEquals(ThemeFamily.SLATE, settings.themeFamily())
    }

    @Test
    fun everyFamilyStillHonoursTheModeIncludingFollowTheSystem() {
        // The capability the one-flat-list design would have removed, asserted directly:
        // for every family, SYSTEM tracks the OS signal, and DARK/LIGHT override it.
        for (family in ThemeFamily.entries) {
            assertSame(
                family.dark,
                resolvePalette(family, ThemeChoice.SYSTEM, systemIsDark = true),
                "${family.label}: SYSTEM must follow a dark OS",
            )
            assertSame(
                family.light,
                resolvePalette(family, ThemeChoice.SYSTEM, systemIsDark = false),
                "${family.label}: SYSTEM must follow a light OS",
            )
            // An OS that will not say — Linux desktop, where skiko reports UNKNOWN.
            // Dark, not light: BeeCode is used at 5am, and a white flash is the wrong
            // way to resolve an ambiguity.
            assertSame(
                family.dark,
                resolvePalette(family, ThemeChoice.SYSTEM, systemIsDark = null),
                "${family.label}: an unknown OS signal must resolve to dark",
            )
            // And an explicit choice ignores the OS entirely, in both directions.
            assertSame(family.light, resolvePalette(family, ThemeChoice.LIGHT, systemIsDark = true))
            assertSame(family.dark, resolvePalette(family, ThemeChoice.DARK, systemIsDark = false))
        }
    }

    @Test
    fun everyFamilyDescribesItselfForTheSettingsRow() {
        // The label and description are what a learner reads in the picker, and an empty
        // one renders as a radio button with nothing beside it. Distinctness matters too:
        // two families with the same label are two identical rows.
        val labels = ThemeFamily.entries.map { it.label }
        assertEquals(labels.size, labels.distinct().size, "family labels must be distinct")
        for (family in ThemeFamily.entries) {
            assertNotEquals("", family.label.trim(), "$family has no label")
            assertNotEquals("", family.description.trim(), "${family.label} has no description")
        }
    }

    @Test
    fun aStoredFamilyIsReadCaseInsensitivelyAndIgnoringSurroundingSpace() {
        val settings = settings()
        // Not decoration: this value travels through a JSON export and a sync snapshot,
        // and a whitespace difference reverting a learner's theme is the kind of defect
        // nobody reports because it looks like they misremembered.
        settings.setAppThemeFamily("  slate\n", NOW)
        assertEquals(ThemeFamily.SLATE, settings.themeFamily())
    }

    private fun settings(): SettingsRepository = SettingsRepository(database)

    private companion object {
        /** Any fixed instant. These assertions are about values, not about time. */
        val NOW: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
    }
}
