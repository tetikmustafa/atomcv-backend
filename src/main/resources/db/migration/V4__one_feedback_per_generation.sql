-- Bolum 13's generation_feedback carries no key beyond its id, so nothing
-- stops one person leaving four verdicts on one document. The screen is a
-- pair of thumbs: pressing the other one is changing your mind, not adding a
-- second opinion, and a rate that counted both would be measuring clicks.
--
-- user_id is nullable in that table and Postgres treats NULLs as distinct, so
-- this constrains accounts only. Nothing writes anonymous feedback today —
-- the generation handler refuses an ownerless job — and when Bolum 9's
-- anonymous generation lands, its own subject will need its own answer here.

CREATE UNIQUE INDEX generation_feedback_one_per_user
    ON generation_feedback (generation_id, user_id);
