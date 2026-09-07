-- Bolum 33.4: a summary is prose, and prose does not take a bullet.
--
-- `layout` has allowed four values since the first migration and none of them
-- described a paragraph, so an About section carried the column's default and
-- the renderer set it as a bulleted item: a marker in front of a paragraph,
-- which reads as the first of a list that never arrives. The canonical template
-- sets a summary in a label-less list, straight under the heading -- the same
-- shape the document it was read from uses.
--
-- PARAGRAPH is a fifth value rather than a reuse of `inline_list`, which is the
-- other label-less layout. An inline row is a label and the list it introduces,
-- so its first colon is set in bold; a summary opening "Backend engineer: five
-- years of ..." would have had its first words emboldened by a rule that was
-- never about it.
--
-- Geometry is unchanged, so no template version is raised and no stored cost is
-- invalidated: both layouts open an `itemize` at the same left margin, and a
-- missing bullet marker sits in the margin rather than in the text block. What
-- changes is what is printed, not how wide it is.
-- Dropped by what it says rather than by what it is called. The constraint was
-- written inline in V1, so its name is Postgres's own and a literal here would
-- be a guess: guess wrong and the DROP does nothing, the ADD leaves two checks
-- on one column, and the UPDATE below fails against the one still refusing
-- 'paragraph' -- a migration that half-applies for a reason nothing in it says.
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
    CHECK (layout IN ('bullet_list', 'entry_list', 'inline_list', 'two_column', 'paragraph'));

-- ProfileWriter now writes PARAGRAPH for ABOUT at import. This repairs the rows
-- written before it did.
--
-- Safe to apply blind, on V6's reasoning: every About section in existence
-- carries this importer's default rather than somebody's choice, and one that
-- has been changed away from `bullet_list` is left alone.
UPDATE sections
SET layout = 'paragraph'
WHERE kind = 'about'
  AND layout = 'bullet_list';
