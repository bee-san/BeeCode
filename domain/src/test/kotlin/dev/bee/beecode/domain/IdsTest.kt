package dev.bee.beecode.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Identity validation.
 *
 * These rules matter more than they look: a ProblemId becomes a directory name
 * and a sync key, so accepting `../` or an empty string would be a path bug and a
 * merge bug at once.
 */
class IdsTest {
    @Test
    fun problemIdAcceptsContentSlugs() {
        assertEquals("two-sum", ProblemId("two-sum").value)
        assertEquals("longest-substring-2", ProblemId("longest-substring-2").value)
    }

    @Test
    fun problemIdRejectsPathTraversalAndSeparators() {
        for (bad in listOf("../etc", "a/b", "a\\b", ".", "..")) {
            assertFailsWith<IllegalArgumentException>("must reject '$bad'") { ProblemId(bad) }
        }
    }

    @Test
    fun problemIdRejectsUppercaseAndWhitespace() {
        for (bad in listOf("TwoSum", "two sum", "two_sum", "two.sum", "twö-sum")) {
            assertFailsWith<IllegalArgumentException>("must reject '$bad'") { ProblemId(bad) }
        }
    }

    @Test
    fun problemIdRejectsUglyHyphenation() {
        for (bad in listOf("-two", "two-", "two--sum")) {
            assertFailsWith<IllegalArgumentException>("must reject '$bad'") { ProblemId(bad) }
        }
    }

    @Test
    fun problemIdRejectsEmptyAndOverlongValues() {
        assertFailsWith<IllegalArgumentException> { ProblemId("") }
        assertFailsWith<IllegalArgumentException> { ProblemId("a".repeat(ProblemId.MAX_LENGTH + 1)) }
        assertEquals(ProblemId.MAX_LENGTH, ProblemId("a".repeat(ProblemId.MAX_LENGTH)).value.length)
    }

    @Test
    fun revisionIdRequiresAFullLowercaseHexHash() {
        assertEquals(64, ProblemRevisionId("0123456789abcdef".repeat(4)).value.length)
        assertFailsWith<IllegalArgumentException> { ProblemRevisionId("abc") }
        assertFailsWith<IllegalArgumentException> { ProblemRevisionId("A".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { ProblemRevisionId("g".repeat(64)) }
    }

    @Test
    fun opaqueIdsAcceptUuidsAndRejectUnsafeCharacters() {
        // UUIDv4 is the production shape; counters are the test shape.
        ExecutionRunId("3f2504e0-4f89-41d3-9a0c-0305e82c3301")
        ReviewSessionId("session_1")
        DeviceId("device-1")
        DomainEventId("evt-1")
        for (bad in listOf("", "has space", "has/slash", "has:colon", "a".repeat(65))) {
            assertFailsWith<IllegalArgumentException>("must reject '$bad'") { ExecutionRunId(bad) }
        }
    }
}
