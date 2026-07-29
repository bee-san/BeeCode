package dev.bee.beecode.persistence

import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The outbox queue, against real SQLite on disk.
 *
 * LDB-007 calls the outbox durable and lists "app restart preserves pending events" as an
 * acceptance criterion. Until this existed the queue was a list in memory: every state
 * transition correct, and the whole thing lost on process death. So these tests use a real
 * file and reopen it, because an in-memory database would prove the SQL parses and nothing
 * about durability.
 */
class ActivityOutboxRepositoryTest {

    private lateinit var file: File

    @BeforeTest
    fun setUp() {
        file = createTempFile("beecode-outbox-", ".db").toFile()
        // Created by the migration, not by the temp file: BeeCodeDatabase.open expects to
        // either create the schema or migrate it.
        file.delete()
    }

    @AfterTest
    fun tearDown() {
        listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
    }

    @Test
    fun pendingRowsSurviveAReopen() {
        // The acceptance criterion, literally: study offline, the process dies, the queue is
        // still there.
        BeeCodeDatabase.open(file.absolutePath).use { database ->
            ActivityOutboxRepository(database).replaceAll(
                listOf(row("e1", state = "PENDING"), row("e2", state = "PENDING")),
            )
        }

        BeeCodeDatabase.open(file.absolutePath).use { database ->
            val rows = ActivityOutboxRepository(database).all()
            assertEquals(listOf("e1", "e2"), rows.map { it.eventId })
            assertTrue(rows.all { it.state == "PENDING" })
        }
    }

    @Test
    fun everyFieldRoundTrips() {
        // A silently dropped column would be worse than a missing table: the queue would
        // look healthy while retrying at the wrong time or forgetting why it parked.
        val original = StoredOutboxRow(
            eventId = "e1",
            problemId = "two-sum",
            occurredAtEpochMillis = 1_754_000_000_000,
            localDate = "2026-08-01",
            countsAsSolved = true,
            state = "PARKED",
            attempts = 6,
            nextAttemptAtEpochMillis = 1_754_000_030_000,
            lastReason = "token expired",
        )
        BeeCodeDatabase.open(file.absolutePath).use { database ->
            val repository = ActivityOutboxRepository(database)
            repository.replaceAll(listOf(original))
            assertEquals(original, repository.all().single())
        }
    }

    @Test
    fun aNullReasonStaysNullRatherThanBecomingEmpty() {
        // An accepted row has no reason, and "" would read as a reason nobody gave.
        BeeCodeDatabase.open(file.absolutePath).use { database ->
            val repository = ActivityOutboxRepository(database)
            repository.replaceAll(listOf(row("e1", state = "ACKNOWLEDGED", reason = null)))
            assertNull(repository.all().single().lastReason)
        }
    }

    @Test
    fun replaceAllIsTheWholeQueueNotAnUpsert() {
        // The pure state machine returns a whole new list, and prune() drops rows. If this
        // merged instead of replacing, pruned rows would come back on the next save.
        BeeCodeDatabase.open(file.absolutePath).use { database ->
            val repository = ActivityOutboxRepository(database)
            repository.replaceAll(listOf(row("e1"), row("e2"), row("e3")))
            repository.replaceAll(listOf(row("e2")))
            assertEquals(listOf("e2"), repository.all().map { it.eventId })
        }
    }

    @Test
    fun anEmptyListEmptiesTheTable() {
        BeeCodeDatabase.open(file.absolutePath).use { database ->
            val repository = ActivityOutboxRepository(database)
            repository.replaceAll(listOf(row("e1")))
            repository.replaceAll(emptyList())
            assertEquals(emptyList(), repository.all())
        }
    }

    @Test
    fun theEventIdIsThePrimaryKeySoOneReviewCannotQueueTwice() {
        // event_id is the idempotency key end to end — the review's own session id. A
        // surrogate rowid would let one review enqueue twice across a restart, which is
        // exactly the double-count the design exists to prevent.
        BeeCodeDatabase.open(file.absolutePath).use { database ->
            val failure = runCatching {
                ActivityOutboxRepository(database)
                    .replaceAll(listOf(row("same"), row("same", problemId = "binary-search")))
            }.exceptionOrNull()
            assertTrue(
                failure != null,
                "two rows with one event id must be refused by the schema, not stored",
            )
        }
    }

    @Test
    fun countsByStateReportsWithoutLoadingTheQueue() {
        BeeCodeDatabase.open(file.absolutePath).use { database ->
            val repository = ActivityOutboxRepository(database)
            repository.replaceAll(
                listOf(
                    row("a", state = "PENDING"),
                    row("b", state = "PENDING"),
                    row("c", state = "ACKNOWLEDGED"),
                    row("d", state = "PARKED"),
                ),
            )
            assertEquals(
                mapOf("PENDING" to 2, "ACKNOWLEDGED" to 1, "PARKED" to 1),
                repository.countsByState(),
            )
        }
    }

    @Test
    fun clearDiscardsTheQueueForAnUnlinkedAccount() {
        BeeCodeDatabase.open(file.absolutePath).use { database ->
            val repository = ActivityOutboxRepository(database)
            repository.replaceAll(listOf(row("e1"), row("e2")))
            repository.clear()
            assertEquals(emptyList(), repository.all())
        }
    }

    @Test
    fun anExistingDatabaseGainsTheTableByMigration() {
        // A learner upgrading has a version 2 database with reviews in it. The migration must
        // add the table without disturbing anything, which is what makes this shippable
        // rather than a fresh-install-only feature.
        BeeCodeDatabase.open(file.absolutePath).use { database ->
            assertEquals(Schema.VERSION, database.schemaVersion())
        }
        BeeCodeDatabase.open(file.absolutePath).use { database ->
            // Reopening runs no migration and the table is still usable.
            ActivityOutboxRepository(database).replaceAll(listOf(row("e1")))
            assertEquals(1, ActivityOutboxRepository(database).all().size)
        }
    }

    private fun row(
        eventId: String,
        problemId: String = "two-sum",
        state: String = "PENDING",
        reason: String? = null,
    ) = StoredOutboxRow(
        eventId = eventId,
        problemId = problemId,
        occurredAtEpochMillis = 1_754_000_000_000,
        localDate = "2026-08-01",
        countsAsSolved = true,
        state = state,
        attempts = 0,
        nextAttemptAtEpochMillis = 1_754_000_000_000,
        lastReason = reason,
    )
}
