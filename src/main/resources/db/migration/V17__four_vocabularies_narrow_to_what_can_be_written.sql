-- Four closed vocabularies, and the values in them nothing can produce.
--
-- V16 did this for atom_variants.created_by and recorded the rule it was
-- applying: the owner of a closed vocabulary is the migration, and a value the
-- wire cannot carry is a branch the frontend writes for nothing. The sixth
-- audit asked the same question of every other vocabulary in the schema and
-- found four more values in the same state -- three of them published.
--
--   generations.status = 'failed'   Never written. selection_state is NOT NULL,
--                                   so a run that fails before selection has no
--                                   row to write at all and the failure lives on
--                                   the job. GenerationStatus said so in its own
--                                   javadoc -- "Reserved. Nothing writes it
--                                   today" -- while the API schema published it.
--
--   jobs.status = 'cancelled'       Job.cancel() existed and no production code
--                                   called it; the only caller was a test of the
--                                   method itself. Nothing in the resource map
--                                   cancels a job, so a client could reach the
--                                   state only by being told about it.
--
--   jobs.type = 'email'             Priority 80 in 30.3, no handler, nothing
--                                   enqueuing one. Mail goes out on a
--                                   post-commit event instead, which is the
--                                   fifth audit's doing. A queued row of this
--                                   type would have failed on arrival --
--                                   JobWorker refuses what it has no handler for
--                                   and says so, which is the right behaviour and
--                                   the wrong thing to need.
--
--   sections.layout = 'two_column'  Worse than the other three, because it is an
--                                   input. The API accepted it, the CHECK
--                                   allowed it, and LatexDocumentRenderer fell
--                                   through to the entry list on purpose: all
--                                   three templates are single-column, and 33.5
--                                   records the reason as ATS extraction. So a
--                                   person could set it, be told nothing, and get
--                                   a different layout -- the silently bad result
--                                   principle 4 exists to forbid.
--
-- jobs.type gains a constraint rather than losing a value, because it never had
-- one: V1 named the six in a comment, and a comment refuses nothing.

-- ── generations.status ──
--
-- Written inline in V1 without a CHECK at all, so there is nothing to drop.
ALTER TABLE generations
    ADD CONSTRAINT generations_status_check
    CHECK (status IN ('completed', 'superseded'));

-- ── jobs.status ──
--
-- Dropped by what it says rather than by what it is called, V9's reasoning:
-- the constraint was written inline in V1 and its name is Postgres's own.
DO $$
DECLARE existing TEXT;
BEGIN
    FOR existing IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'jobs'::REGCLASS
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%queued%'
    LOOP
        EXECUTE format('ALTER TABLE jobs DROP CONSTRAINT %I', existing);
    END LOOP;
END $$;

ALTER TABLE jobs
    ADD CONSTRAINT jobs_status_check
    CHECK (status IN ('queued', 'running', 'completed', 'failed'));

-- ── jobs.type ──
ALTER TABLE jobs
    ADD CONSTRAINT jobs_type_check
    CHECK (type IN ('generation', 'profile_extract', 'measurement', 'translation', 'embedding'));

-- ── sections.layout ──
--
-- Repaired before it is narrowed, and this is the one of the four that can
-- actually have rows: the value was reachable through PATCH /profile/sections.
-- They become what the renderer was already printing them as, so no page
-- changes and no measured cost is invalidated -- the row is only stopping
-- claiming something the document never did.
UPDATE sections SET layout = 'entry_list' WHERE layout = 'two_column';

DO $$
DECLARE existing TEXT;
BEGIN
    FOR existing IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'sections'::REGCLASS
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%bullet_list%'
    LOOP
        EXECUTE format('ALTER TABLE sections DROP CONSTRAINT %I', existing);
    END LOOP;
END $$;

ALTER TABLE sections
    ADD CONSTRAINT sections_layout_check
    CHECK (layout IN ('bullet_list', 'entry_list', 'inline_list', 'paragraph'));
