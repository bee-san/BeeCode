package dev.bee.beecode.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import dev.bee.beecode.design.BeeCodePalette
import dev.bee.beecode.design.ThemeChoice
import kotlin.test.Test
import kotlin.test.fail

/**
 * The desktop Problem pane, measured from the pixels it actually renders.
 *
 * ## Why a pixel test, when `PaletteContrastTest` already checks contrast
 *
 * Because that test asks whether two *palette values* differ, and the defect this one
 * exists to catch is that the two values were never adjacent in the first place.
 *
 * Both clients paint inset surfaces — code blocks, examples, test output — with
 * `surface`, which reads as recessed because a `Card` fills with the lighter
 * `surfaceContainerHighest`. `PaletteContrastTest` confirms that pairing is 1.447:1 in
 * dark. On desktop it was 1.000:1: the Problem pane's left column had no fill of its
 * own, so the inset sat directly on the window's `background` — and `background` and
 * `surface` are *the same value* in both BeeCode palettes. Page colour on page colour.
 * The examples box and the statement's code blocks did not exist visually.
 *
 * Every palette-level assertion passed throughout, on both clients, correctly. Android
 * was genuinely fine, because its statement has always been inside a `Card`. Nothing
 * that reasons about colour values alone can see this class of bug, and reviewing one
 * client does not review the other.
 *
 * ## Why it can run at all
 *
 * `captureToImage` works under [runComposeUiTest] with no display: skiko renders to an
 * offscreen surface, so this needs no Xvfb and no `DISPLAY` — which is what made a
 * *measured* desktop check possible at all, where before there was only code review.
 *
 * Two things the harness has to get right, both of which cost a debugging round:
 *
 *  - The [Surface] wrapper is copied from `Main.kt`'s window, not added for tidiness.
 *    Without it nothing paints an opaque background and every captured pixel is
 *    `00000000` — a scan then reports pure alpha and looks like a capture failure.
 *  - Capture [onRoot], not the node under test. A node-scoped capture excludes its
 *    ancestors' fills, so the very background being asserted against is not in the
 *    image.
 */
@OptIn(ExperimentalTestApi::class)
class ProblemPaneContrastTest {

    @Test
    fun theStatementCardIsPaintedBehindTheStatement() {
        withProblemPane(ThemeChoice.DARK) { ui ->
            val card = BeeCodePalette.Dark.surfaceContainerHighest.rgb()
            val page = BeeCodePalette.Dark.surface.rgb()
            val pixels = ui.leftColumnPixels()

            // The card must actually be the dominant fill of the left column. When the
            // statement was not in a Card this count was zero: the column was all page
            // colour, and the inset boxes drawn on it were the same colour again.
            val cardPixels = pixels.count { it == card }
            val pagePixels = pixels.count { it == page }
            if (cardPixels <= pagePixels) {
                fail(
                    "The Problem pane's left column is mostly page colour " +
                        "(${page.hex()}: $pagePixels px) rather than card " +
                        "(${card.hex()}: $cardPixels px). The statement and its examples " +
                        "are meant to sit in a Card — without one, an inset surface " +
                        "painted `surface` lands on `background`, which is the same " +
                        "value, and renders at 1.000:1.",
                )
            }
        }
    }

    /**
     * The examples box specifically, in both schemes.
     *
     * Anchored on the "Input:" line's own bounds rather than scanning the whole column,
     * and that is the point of the test rather than an implementation detail. The first
     * version asked whether a card→inset boundary existed *anywhere* in the pane, and a
     * mutation repainting the examples box with the card's own colour passed it — the
     * statement's code block still supplied a boundary elsewhere, so "found somewhere"
     * proved nothing about the box being examined.
     *
     * Asserted as adjacency rather than as a ratio between two palette fields: the
     * question is whether this box's fill actually differs from what is behind it, and a
     * ratio between the two palette values was already true while the bug shipped.
     */
    @Test
    fun theExamplesBoxIsInsetAgainstTheCard() {
        listOf(
            ThemeChoice.DARK to BeeCodePalette.Dark,
            ThemeChoice.LIGHT to BeeCodePalette.Light,
        ).forEach { (choice, palette) ->
            withProblemPane(choice) { ui ->
                val card = palette.surfaceContainerHighest.rgb()
                val inset = palette.surface.rgb()
                val image = ui.onRoot().captureToImage()
                val px = IntArray(image.width * image.height)
                image.readPixels(px)
                fun at(x: Int, y: Int) = px[y * image.width + x] and RGB

                // The first worked example's "Input:" line. Its left edge sits inside
                // the box's 10dp padding, so scanning up from it crosses the box's own
                // fill and then the card behind it.
                val bounds = ui.onAllNodesWithText("Input:", substring = true)
                    .onFirst()
                    .getUnclippedBoundsInRoot()
                val density = 1f
                val x = ((bounds.left.value + 4) * density).toInt()
                val top = (bounds.top.value * density).toInt()

                val fill = at(x, top + 1)
                if (fill != inset) {
                    fail(
                        "The $choice examples box is filled ${fill.hex()} where the inset " +
                            "surface ${inset.hex()} was expected. If it is ${card.hex()} " +
                            "the box has been painted the same colour as the Card behind " +
                            "it and has no visible extent.",
                    )
                }

                // And the card is genuinely behind it: walk up from the box's top edge
                // until the colour stops being the inset fill.
                var y = top
                while (y > COLUMN_TOP && at(x, y) == inset) y--
                val behind = at(x, y)
                if (behind != card) {
                    fail(
                        "Above the $choice examples box the pixel is ${behind.hex()}, not " +
                            "the Card's ${card.hex()}. The box is meant to be inset into a " +
                            "Card; if what is behind it is the page colour ${inset.hex()} " +
                            "then the statement lost its Card and the box renders at " +
                            "1.000:1 — the original defect.",
                    )
                }
            }
        }
    }

    private fun withProblemPane(choice: ThemeChoice, body: (ComposeUiTest) -> Unit) {
        val catalogue = ProblemCatalogue.fromResource(PACK_RESOURCE)
        val profile = BeeCodeProfile.inMemory(
            catalogue = catalogue,
            runner = ScriptedPythonRunner(),
        )
        try {
            runComposeUiTest {
                setContent {
                    // Copied from Main.kt's BeeCodeWindow. See this class's KDoc: without
                    // the Surface there is no opaque fill and every pixel is 00000000.
                    BeeCodeTheme(choice = choice) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            DesktopApp(profile)
                        }
                    }
                }
                // Two Sum has both a fenced code block in its statement and worked
                // examples, so one Problem exercises both inset sites.
                onNodeWithTag(QUEUE_LIST_TAG).performScrollToNode(hasText(TWO_SUM_TITLE))
                onAllNodesWithText(TWO_SUM_TITLE).onFirst().performClick()
                waitForIdle()
                body(this)
            }
        } finally {
            profile.close()
        }
    }

    private companion object {
        const val TWO_SUM_TITLE = "Two Sum"

        /** Opaque-alpha mask: compare fills by RGB, since every palette value is opaque. */
        const val RGB = 0xFFFFFF

        /**
         * Horizontal span sampled inside the left statement pane.
         *
         * The pane is `weight(0.42f)` of a 1024px-wide capture, so it ends near x=430.
         * Sampling stops well short of that to stay clear of the divider and the
         * editor beyond it.
         */
        val LEFT_COLUMN = 40 until 380

        /** Below the header row and its divider. */
        const val COLUMN_TOP = 100

        fun Long.rgb(): Int = (this and RGB.toLong()).toInt()

        fun Int.hex(): String = "#%06X".format(toLong())

        fun ComposeUiTest.leftColumnPixels(): List<Int> {
            val image = onRoot().captureToImage()
            val px = IntArray(image.width * image.height)
            image.readPixels(px)
            return buildList {
                for (y in COLUMN_TOP until image.height) {
                    for (x in LEFT_COLUMN) add(px[y * image.width + x] and RGB)
                }
            }
        }
    }
}
