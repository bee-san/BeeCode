package dev.bee.beecode.persistence

import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemRevisionId
import dev.bee.beecode.domain.SolutionDraft
import kotlinx.datetime.Instant
import java.sql.Connection

/**
 * Stores the learner's in-progress source.
 *
 * Losing typed source is treated as data loss, not an inconvenience, so drafts are
 * written eagerly rather than on close: the process can die at any moment — an
 * Android low-memory kill, a desktop crash, a power cut — and the learner should
 * find their code where they left it.
 */
class DraftRepository(private val database: BeeCodeDatabase) {

    /**
     * The learner's draft for a Problem, or null if they have not opened it.
     *
     * Returns null rather than a synthesized starter draft, so the caller can tell
     * "never opened" from "opened and reset to starter". Use [loadOrStart] when
     * that distinction does not matter.
     */
    fun draft(problemId: ProblemId): SolutionDraft? = database.read { connection ->
        readDraft(connection, problemId)
    }

    /**
     * The learner's draft, or a fresh one seeded from the Problem's starter.
     *
     * Also handles the awkward case where the Problem's content was updated after
     * the learner started: their source is kept, because throwing away typed code
     * would be indefensible, but the starter baseline is refreshed so "reset to
     * starter" gives them the *current* starter. The revision is updated so the
     * next run is judged against the content they can actually see.
     */
    fun loadOrStart(problem: ProblemDefinition, now: Instant): SolutionDraft {
        val existing = draft(problem.id)
            ?: return SolutionDraft(
                problemId = problem.id,
                problemRevisionId = problem.revisionId,
                source = problem.starterSource,
                starterBaseline = problem.starterSource,
                version = 0,
                updatedAt = now,
            )

        if (existing.problemRevisionId == problem.revisionId) return existing

        // Content changed underneath the learner.
        return if (existing.isPristine) {
            // They never edited it, so silently adopt the new starter.
            existing.copy(
                problemRevisionId = problem.revisionId,
                source = problem.starterSource,
                starterBaseline = problem.starterSource,
                updatedAt = now,
            )
        } else {
            existing.copy(
                problemRevisionId = problem.revisionId,
                starterBaseline = problem.starterSource,
                updatedAt = now,
            )
        }
    }

    /**
     * Save a draft, refusing to overwrite a newer one.
     *
     * The version check matters because saves are frequent and can overlap: an
     * autosave triggered by a keystroke can land after a later explicit save. A
     * stale write is dropped rather than applied, so the learner never sees their
     * newest edits vanish.
     *
     * @return the persisted draft with its incremented version, or null if a newer
     *   version was already stored.
     */
    fun save(draft: SolutionDraft, now: Instant): SolutionDraft? = database.transaction { connection ->
        val stored = readDraft(connection, draft.problemId)
        if (stored != null && stored.version > draft.version) return@transaction null

        // Truncated to the precision this table can store, so the draft returned
        // here is identical to the one a later read produces. Returning a
        // nanosecond-precise value that the database rounds would make the two
        // disagree for no visible reason.
        val next = draft.copy(version = draft.version + 1, updatedAt = now.truncatedToMillis())
        connection.prepareStatement(
            """
            INSERT INTO solution_draft (
                problem_id, problem_revision_id, source, starter_baseline, version, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(problem_id) DO UPDATE SET
                problem_revision_id = excluded.problem_revision_id,
                source = excluded.source,
                starter_baseline = excluded.starter_baseline,
                version = excluded.version,
                updated_at = excluded.updated_at
            WHERE solution_draft.version = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, next.problemId.value)
            statement.setString(2, next.problemRevisionId.value)
            statement.setString(3, next.source)
            statement.setString(4, next.starterBaseline)
            statement.setLong(5, next.version)
            statement.setLong(6, next.updatedAt.toEpochMilliseconds())
            statement.setLong(7, draft.version)
            statement.executeUpdate()
        }
        next
    }

    /** Delete a draft, e.g. when the learner resets a Problem completely. */
    fun delete(problemId: ProblemId) {
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM solution_draft WHERE problem_id = ?").use { statement ->
                statement.setString(1, problemId.value)
                statement.executeUpdate()
            }
        }
    }

    /** Every draft, for export. */
    fun allDrafts(): List<SolutionDraft> = database.read { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM solution_draft").use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            SolutionDraft(
                                problemId = ProblemId(rows.getString("problem_id")),
                                problemRevisionId = ProblemRevisionId(rows.getString("problem_revision_id")),
                                source = rows.getString("source"),
                                starterBaseline = rows.getString("starter_baseline"),
                                version = rows.getLong("version"),
                                updatedAt = Instant.fromEpochMilliseconds(rows.getLong("updated_at")),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun readDraft(connection: Connection, problemId: ProblemId): SolutionDraft? =
        connection.prepareStatement("SELECT * FROM solution_draft WHERE problem_id = ?").use { statement ->
            statement.setString(1, problemId.value)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return@use null
                SolutionDraft(
                    problemId = ProblemId(rows.getString("problem_id")),
                    problemRevisionId = ProblemRevisionId(rows.getString("problem_revision_id")),
                    source = rows.getString("source"),
                    starterBaseline = rows.getString("starter_baseline"),
                    version = rows.getLong("version"),
                    updatedAt = Instant.fromEpochMilliseconds(rows.getLong("updated_at")),
                )
            }
        }
}
