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
    const val VERSION: Int = 4

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

        // ---- Version 2: fractional intervals, for FSRS-7 -------------------
        //
        // FSRS-7 schedules in fractional days: a same-day review can legitimately
        // be due in ten minutes (0.00694 days). These columns were INTEGER, which
        // does not merely lose precision — SQLite would coerce every sub-day
        // interval toward zero, so the exact case the algorithm exists to handle
        // would be the case that broke.
        //
        // `fsrs_elapsed_days` widens for the same reason on the input side: an
        // elapsed time floored to whole days feeds FSRS-7 an FSRS-6-shaped
        // question and gets back FSRS-6-shaped behaviour.
        //
        // Widening INTEGER to REAL is value-preserving in both directions that
        // matter here: existing whole-day values read back identically, and
        // SQLite's dynamic typing means already-stored integers need no rewrite.
        // Done with ALTER TABLE ... RENAME plus a copy rather than in place
        // because SQLite cannot change a column's declared type, and the copy is
        // also what lets the NOT NULL constraints carry over.
        listOf(
            "ALTER TABLE problem_schedule RENAME TO problem_schedule_v1",
            """
            CREATE TABLE problem_schedule (
                problem_id        TEXT    NOT NULL PRIMARY KEY,
                stability         REAL    NOT NULL,
                difficulty        REAL    NOT NULL,
                due_at            INTEGER NOT NULL,
                last_reviewed_at  INTEGER NOT NULL,
                interval_days     REAL    NOT NULL,
                review_count      INTEGER NOT NULL,
                lapse_count       INTEGER NOT NULL,
                version           INTEGER NOT NULL,
                updated_at        INTEGER NOT NULL
            )
            """,
            """
            INSERT INTO problem_schedule (
                problem_id, stability, difficulty, due_at, last_reviewed_at,
                interval_days, review_count, lapse_count, version, updated_at
            )
            SELECT
                problem_id, stability, difficulty, due_at, last_reviewed_at,
                CAST(interval_days AS REAL), review_count, lapse_count, version, updated_at
            FROM problem_schedule_v1
            """,
            "DROP TABLE problem_schedule_v1",
            // Recreated because dropping the old table takes its index with it.
            "CREATE INDEX idx_problem_schedule_due ON problem_schedule (due_at)",

            // The review log is append-only, but that constrains INSERT and DELETE
            // of *rows*; widening a column's type preserves every recorded fact,
            // and each row keeps naming the algorithm that produced it.
            "ALTER TABLE problem_review RENAME TO problem_review_v1",
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
                selected_source        TEXT    NOT NULL,
                local_date             TEXT    NOT NULL,
                local_hour             INTEGER NOT NULL,
                streak_zone_id         TEXT    NOT NULL,
                fsrs_algorithm_id      TEXT    NOT NULL,
                fsrs_engine_version    TEXT    NOT NULL,
                fsrs_parameters_hash   TEXT    NOT NULL,
                fsrs_prev_state_hash   TEXT    NOT NULL,
                fsrs_prev_stability    REAL,
                fsrs_prev_difficulty   REAL,
                fsrs_elapsed_days      REAL    NOT NULL,
                fsrs_rating_value      INTEGER NOT NULL,
                fsrs_desired_retention REAL    NOT NULL,
                fsrs_max_interval_days REAL    NOT NULL,
                fsrs_next_stability    REAL    NOT NULL,
                fsrs_next_difficulty   REAL    NOT NULL,
                fsrs_next_interval     REAL    NOT NULL,
                fsrs_retrievability    REAL    NOT NULL,
                fsrs_due_at            INTEGER NOT NULL
            )
            """,
            """
            INSERT INTO problem_review (
                review_session_id, event_id, problem_id, problem_revision_id,
                execution_run_id, outcome, rating, aided, counts_as_solved,
                finalized_at, device_id, selected_source, local_date, local_hour,
                streak_zone_id, fsrs_algorithm_id, fsrs_engine_version,
                fsrs_parameters_hash, fsrs_prev_state_hash, fsrs_prev_stability,
                fsrs_prev_difficulty, fsrs_elapsed_days, fsrs_rating_value,
                fsrs_desired_retention, fsrs_max_interval_days, fsrs_next_stability,
                fsrs_next_difficulty, fsrs_next_interval, fsrs_retrievability,
                fsrs_due_at
            )
            SELECT
                review_session_id, event_id, problem_id, problem_revision_id,
                execution_run_id, outcome, rating, aided, counts_as_solved,
                finalized_at, device_id, selected_source, local_date, local_hour,
                streak_zone_id, fsrs_algorithm_id, fsrs_engine_version,
                fsrs_parameters_hash, fsrs_prev_state_hash, fsrs_prev_stability,
                fsrs_prev_difficulty, CAST(fsrs_elapsed_days AS REAL), fsrs_rating_value,
                fsrs_desired_retention, CAST(fsrs_max_interval_days AS REAL), fsrs_next_stability,
                fsrs_next_difficulty, CAST(fsrs_next_interval AS REAL), fsrs_retrievability,
                fsrs_due_at
            FROM problem_review_v1
            """,
            "DROP TABLE problem_review_v1",
            "CREATE INDEX idx_problem_review_problem ON problem_review (problem_id, finalized_at)",
            "CREATE INDEX idx_problem_review_finalized ON problem_review (finalized_at)",
            "CREATE INDEX idx_problem_review_solved_date ON problem_review (counts_as_solved, local_date)",
        ),

        // ---- Version 3: the Leaderboard activity outbox ---------------------
        //
        // LDB-007 calls the outbox "durable", and until now it was a list in
        // memory: correct in every state transition and lost on every process
        // death. "App restart preserves pending events" is one of its acceptance
        // criteria, so the queue has to live here.
        //
        // Keyed by `event_id` rather than an autoincrement rowid, because that id
        // is the idempotency key end to end — the review's own session id, minted
        // on-device. A surrogate key would let one review enqueue twice after a
        // restart, which is precisely the double-count the whole design exists to
        // prevent (ADR 0002 property 1).
        //
        // Deliberately *not* joined to `problem_review`. A rejected event must
        // survive as the record of a server decision even if its Problem later
        // leaves the pack, and a foreign key would make deleting a pack delete
        // that history.
        listOf(
            """
            CREATE TABLE activity_outbox (
                event_id        TEXT    NOT NULL PRIMARY KEY,
                problem_id      TEXT    NOT NULL,
                occurred_at     INTEGER NOT NULL,
                local_date      TEXT    NOT NULL,
                counts_as_solved INTEGER NOT NULL,
                state           TEXT    NOT NULL,
                attempts        INTEGER NOT NULL,
                next_attempt_at INTEGER NOT NULL,
                last_reason     TEXT
            )
            """,
            // The batch query's exact shape: pending rows whose backoff has
            // elapsed, oldest first.
            "CREATE INDEX idx_activity_outbox_ready ON activity_outbox (state, next_attempt_at, occurred_at)",
        ),

        // ---- Version 4: the topic schedule ----------------------------------
        //
        // The card the learner actually studies. A learner does not forget
        // *two-sum*; they forget dynamic programming, so FSRS's memory state
        // belongs on the technique and the Problem is the exercise that rehearses
        // it. With the card here, "frequently forgets DP" needs no weakness
        // heuristic: lapses lower DP's stability, FSRS shortens DP's interval, and
        // DP comes back sooner.
        //
        // Keyed by the topic slug, which is content-derived and therefore stable
        // across devices without coordination (ADR 0002 property 1). No
        // autoincrement, for the same reason nothing else here has one.
        //
        // A projection, like `problem_schedule`: folded from the append-only review
        // log crossed with the pack's current topic tags. That is what keeps it out
        // of the sync payload entirely — it is rebuilt after a merge rather than
        // merged, so no format version moves.
        //
        // Deliberately *not* joined to anything. `problem_review` rows outlive the
        // Problems they name, and a topic must keep its history when a Problem
        // leaves the pack — the same reasoning `activity_outbox` above records.
        listOf(
            """
            CREATE TABLE topic_schedule (
                topic             TEXT    NOT NULL PRIMARY KEY,
                stability         REAL    NOT NULL,
                difficulty        REAL    NOT NULL,
                due_at            INTEGER NOT NULL,
                last_reviewed_at  INTEGER NOT NULL,
                -- REAL from the outset. `problem_schedule` declared this INTEGER in
                -- version 1 and version 2 had to rename-copy-drop the whole table to
                -- widen it, because SQLite cannot alter a column's declared type.
                -- FSRS-7 schedules fractional days; there is no reason to repeat that.
                interval_days     REAL    NOT NULL,
                review_count      INTEGER NOT NULL,
                lapse_count       INTEGER NOT NULL,
                version           INTEGER NOT NULL,
                updated_at        INTEGER NOT NULL
            )
            """,
            // The topic queue's only query shape: due soonest first.
            "CREATE INDEX idx_topic_schedule_due ON topic_schedule (due_at)",
        ),
    )

    init {
        require(MIGRATIONS.size == VERSION) {
            "Schema.VERSION ($VERSION) must equal the number of migrations (${MIGRATIONS.size})"
        }
    }
}
