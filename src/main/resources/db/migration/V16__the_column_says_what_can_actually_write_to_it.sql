-- atom_variants.created_by has carried a comment naming four authors since V1
-- and has only ever held two.
--
-- 'llm_extract' had no writer because extraction keeps the person's own
-- sentences and ProfileWriter marks them 'user' on purpose. 'llm_rewrite' had
-- none because a Faz D rewrite is not a variant: it lives in
-- generations.rewritten_content, which is the whole subject of V11. Both were
-- in the Java enum and in the published API schema, so the frontend could
-- reasonably write a branch for a value nothing would ever send it.
--
-- EK D records that the owner of a closed vocabulary is the migration -- an
-- unknown value is supposed to fail loudly rather than be read as something
-- else -- and this column never had the constraint that would do it. It has
-- one now, and it names the two that are real.
--
-- No rows are repaired first because none can exist: two writers in the whole
-- codebase, one per value. The constraint is validated against what is there,
-- so a row proving otherwise would stop this migration rather than be
-- silently kept.
ALTER TABLE atom_variants
    ADD CONSTRAINT atom_variants_created_by_check
    CHECK (created_by IN ('user', 'llm_translate'));
