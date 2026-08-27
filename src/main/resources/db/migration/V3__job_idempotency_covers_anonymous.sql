-- Adim 3.6: the idempotency index did not cover anonymous requests.
--
-- V1 wrote it as (user_id, idempotency_key), which does exactly what Bolum
-- 30.7 asks for as long as there is a user. An anonymous job has user_id NULL,
-- and Postgres counts NULLs as distinct from each other -- so two requests
-- carrying the same key produced two rows, and the double click Bolum 30.7
-- exists to absorb went through twice. The defect was written down in EK D.6.5
-- when the anonymous flow was still a maybe; it is now a caller.
--
-- COALESCE over the two owner columns is the fix, and the cast is what makes
-- them comparable: user_id is a uuid and anon_session_id is text. One of the
-- two is always null (the JobOwner type is what guarantees it), so the
-- expression is whichever owner the row actually has.
--
-- The old index is dropped rather than left beside the new one. Two unique
-- indexes over the same intent are two places to reason about, and the second
-- would go on being satisfied by rows the first already rejected.

DROP INDEX IF EXISTS jobs_user_id_idempotency_key_idx;

CREATE UNIQUE INDEX jobs_owner_idempotency_key_idx
    ON jobs (COALESCE(user_id::text, anon_session_id), idempotency_key)
    WHERE idempotency_key IS NOT NULL;
