package dev.bee.beecode.persistence

/**
 * The local SQLite schema and its migrations.
 *
 * Design rules, all of which exist to serve either recovery or the sync model in
 * ADR 0002:
 *
 * - **No autoincrement identities on syncable rows.** Every primary key is a
 *   content ID or a device-generated opaque ID, so two devices working offline
 *   cannot mint colliding keys.
 * - **`updated_at` on every mutable row.** Per-entity merge is last-write-wins
 *   over this column; a mutable row without it could only be clobbered.
 * - **The review log is append-only.** `problem_review` has no UPDATE path.
 *   Merging two devices' review histories is a set union keyed by
 *   `review_session_id`, which is always correct and needs no timestamp
 *   comparison.
 * - **Instants are stored as epoch milliseconds (INTEGER).** Not text: integers
 *   sort and compare correctly in SQL, which is what makes the due queue a cheap
 *   indexed scan. Local dates are stored separately where a rule is defined in
 *   local time.
 * - **Every schedule transition is fully recorded.** Enough to rebuild
 *   operational state by folding recorded outputs, with no historical engine
 *   binary present.
 */
internal object Schema {
    /**
     * Current schema version.
     *
     * Bump this and append to [MIGRATIONS] for any schema change. Never edit an
     * existing migration: an installed client has already run it, and changing it
     * would leave the two databases silently different.
     */
    const val VERSION: Int = 1

    /**
     * Migrations, indexed by the version they produce.
     *
     * `MIGRATIONS[0]` creates version 1 from an empty database.
     */
    val MIGRATIONS: List<List<String>> = listOf(
        // ---- Version 1: the complete local study schema -------------------
        listOf(
            // Profile-scoped settings, including the device identity reserved by
            // ADR 0002 property 4 for a sync model that does not exist yet.
            """
            CREATE TABLE settings (
                key         TEXT    NOT NULL PRIMARY KEY,
                value       TEXT    NOT NULL,
                updated_at  INTEGER NOT NULL
            )
            """,

            // The learner's in-progress source for a Problem.
            //
            // One row per Problem, mutable, so it carries both updated_at (for
            // merge) and version (so a slow autosave cannot clobber a newer
            // edit). Losing typed source is treated as data loss, so this is
            // written eagerly rather than on close.
            """
            CREATE TABLE solution_draft (
                problem_id           TEXT    NOT NULL PRIMARY KEY,
                problem_revision_id  TEXT    NOT NULL,
                source               TEXT    NOT NULL,
                starter_baseline     TEXT    NOT NULL,
                version              INTEGER NOT NULL,
                updated_at           INTEGER NOT NULL
            )
            """,

            // Materialized FSRS state, one row per Problem.
            //
            // `version` is the optimistic-concurrency counter that makes
            // finalization exactly-once: a review commits only if the stored
            // version still matches what it read. A counter, not a timestamp,
            // because counters cannot tie and clocks can move backwards.
            """
            CREATE TABLE problem_schedule (
                problem_id        TEXT    NOT NULL PRIMARY KEY,
                stability         REAL    NOT NULL,
                difficulty        REAL    NOT NULL,
                due_at            INTEGER NOT NULL,
                last_reviewed_at  INTEGER NOT NULL,
                interval_days     INTEGER NOT NULL,
                review_count      INTEGER NOT NULL,
                lapse_count       INTEGER NOT NULL,
                version           INTEGER NOT NULL,
                updated_at        INTEGER NOT NULL
            )
            """,
            // The due queue's only query shape: due soonest first.
            "CREATE INDEX idx_problem_schedule_due ON problem_schedule (due_at)",

            // The append-only review log. No UPDATE, no DELETE.
            //
            // Keyed by review_session_id rather than a surrogate, which is what
            // makes finalization idempotent: a retried finalize collides with the
            // primary key and returns the existing row instead of scheduling the
            // Problem twice.
            //
            // The fsrs_* columns are a complete audit of the transition. They are
            // deliberately redundant with problem_schedule so operational state
            // can be rebuilt by folding outputs.
            """
            CREATE TABLE problem_review (
                review_session_id      TEXT    NOT NULL PRIMARY KEY,
                event_id               TEXT    NOT NULL UNIQUE,
                problem_id             TEXT    NOT NULL,
                problem_revision_id    TEXT    NOT NULL,
                execution_run_id       TEXT    NOT NULL,
                outcome                TEXT    NOT NULL,
                rating                 TEXT    NOT NULL,
                aided                  INTEGER NOT NULL,
                counts_as_solved       INTEGER NOT NULL,
                finalized_at           INTEGER NOT NULL,
                device_id              TEXT    NOT NULL,
                -- The source that actually produced the selected result. Kept so
                -- history can show what the learner wrote, and so a restored
                -- backup is not just a set of numbers.
                selected_source        TEXT    NOT NULL,
                -- Local date and hour of finalization, derived once at write time
                -- in the profile's zone. Stored rather than recomputed because
                -- the 5am Club and streaks are defined in local dates, and a
                -- later timezone change must not silently rewrite history.
                local_date             TEXT    NOT NULL,
                local_hour             INTEGER NOT NULL,
                streak_zone_id         TEXT    NOT NULL,
                fsrs_algorithm_id      TEXT    NOT NULL,
                fsrs_engine_version    TEXT    NOT NULL,
                fsrs_parameters_hash   TEXT    NOT NULL,
                fsrs_prev_state_hash   TEXT    NOT NULL,
                fsrs_prev_stability    REAL,
                fsrs_prev_difficulty   REAL,
                fsrs_elapsed_days      INTEGER NOT NULL,
                fsrs_rating_value      INTEGER NOT NULL,
                fsrs_desired_retention REAL    NOT NULL,
                fsrs_max_interval_days INTEGER NOT NULL,
                fsrs_next_stability    REAL    NOT NULL,
                fsrs_next_difficulty   REAL    NOT NULL,
                fsrs_next_interval     INTEGER NOT NULL,
                fsrs_retrievability    REAL    NOT NULL,
                fsrs_due_at            INTEGER NOT NULL
            )
            """,
            "CREATE INDEX idx_problem_review_problem ON problem_review (problem_id, finalized_at)",
            "CREATE INDEX idx_problem_review_finalized ON problem_review (finalized_at)",
            // Backs the 5am Club and streak queries, which scan by local date
            // among solved reviews only.
            "CREATE INDEX idx_problem_review_solved_date ON problem_review (counts_as_solved, local_date)",

            // Immutable, idempotent achievement awards.
            //
            // Keyed by definition ID because an achievement is earned once. The
            // projection that writes these is idempotent and re-runnable, so a
            // crash between the review commit and the award cannot lose it.
            """
            CREATE TABLE achievement_award (
                achievement_id  TEXT    NOT NULL PRIMARY KEY,
                awarded_at      INTEGER NOT NULL,
                -- The local date that completed the achievement, for display.
                local_date      TEXT    NOT NULL,
                -- Free-form JSON detail, e.g. which dates formed a streak.
                detail_json     TEXT,
                updated_at      INTEGER NOT NULL
            )
            """,

            // Progress toward not-yet-earned achievements.
            //
            // Separate from awards because progress is *derived* and rebuildable,
            // while an award is a fact. A broken or unknown reducer may discard
            // progress; it must never discard an award, and must never block
            // study.
            """
            CREATE TABLE achievement_progress (
                achievement_id  TEXT    NOT NULL PRIMARY KEY,
                progress_json   TEXT    NOT NULL,
                updated_at      INTEGER NOT NULL
            )
            """,

            // The cursor marking how far achievement projection has consumed the
            // review log.
            //
            // Projection runs after the review commit, in its own transaction, so
            // a reducer failure cannot roll back or block a review. This cursor is
            // what lets it catch up after a restart.
            """
            CREATE TABLE projection_cursor (
                name             TEXT    NOT NULL PRIMARY KEY,
                last_finalized_at INTEGER NOT NULL,
                last_session_id  TEXT    NOT NULL,
                updated_at       INTEGER NOT NULL
            )
            """,
        ),
    )

    init {
        require(MIGRATIONS.size == VERSION) {
            "Schema.VERSION ($VERSION) must equal the number of migrations (${MIGRATIONS.size})"
        }
    }
}
