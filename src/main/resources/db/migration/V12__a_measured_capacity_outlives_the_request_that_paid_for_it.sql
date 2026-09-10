-- Bolum 33.1's layer B: what a page of a given geometry holds, measured once.
--
-- Until now there were two capacities and both were constants in
-- `TemplateRegistry`, because there were two templates at two fixed settings
-- and somebody measured them by hand. A slider ends that: a person moving the
-- font size asks for a geometry nobody has ever compiled, and the answer costs
-- a LaTeX run to produce.
--
-- **A capacity belongs to a customization, not to a person.** Two people at
-- 9.5pt on a 0.6in margin are asking the same question and the answer is the
-- same seventeen numbers, so this table has no `user_id` and no `profile_id`
-- and is not reached through a scoped repository (absolute rule 3 is about
-- user data; this is arithmetic about a page). It also means a popular setting
-- is compiled once for everybody rather than once per account.
--
-- The key is `TemplateCustomization.costKey()`, which carries the template,
-- its version and the four knobs that move a box -- and deliberately not the
-- accent colour, which moves none (layer A, "no re-measurement"). Raising a
-- template version changes every key under it, which is how a preamble change
-- invalidates these rather than leaving them to describe a document that no
-- longer exists.
--
-- `fixed_costs` is JSONB rather than seventeen columns: the set of pieces of
-- furniture is the renderer's business and has grown twice already
-- (SECTION_LIST_CLOSE, INLINE_ROW). A column per piece would have meant a
-- migration each time, and nothing here is ever queried by one of them -- the
-- whole row is read at once or not at all.
CREATE TABLE template_capacities (
    cost_key                TEXT PRIMARY KEY,
    page_text_height_pt     DOUBLE PRECISION NOT NULL,
    text_width_pt           DOUBLE PRECISION NOT NULL,
    baseline_skip_pt        DOUBLE PRECISION NOT NULL,
    item_baseline_skip_pt   DOUBLE PRECISION NOT NULL,
    fixed_costs             JSONB NOT NULL,
    measured_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Nothing is seeded. Classic and compact keep their measured numbers in
-- `TemplateRegistry`, where a person can read them next to the preamble they
-- describe and `LatexCalibrationIT` can re-derive them on every run; copying
-- them here would make two sources of one truth, and the one in the database
-- would be the one nobody checks.
