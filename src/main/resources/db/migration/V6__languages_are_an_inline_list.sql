-- Bolum 33.4: a language is a label and a level -- "Turkish: Native" -- which
-- is the row a skills matrix is made of, not an entry with a heading.
--
-- The importer wrote BULLET_LIST over every section but skills, so a real
-- profile's Languages section printed the label twice: an entry heading reading
-- `English` with a bullet under it reading `English: B2`. It cost 112 pt of a
-- 708 pt page for what an inline list prints in 45 -- an entry heading and an
-- itemize per language, both of which the inline renderer discards.
--
-- ProfileWriter now writes INLINE_LIST for LANGUAGES at import. This repairs
-- the rows written before it did.
--
-- Safe to apply blind. `layout` is exposed read-only: it appears in
-- SectionResponse and nowhere in a request body, so no value in existence is
-- somebody's choice -- every one is this importer's default. A section already
-- carrying INLINE_LIST is left as it is.
--
-- Nothing needs to move: the renderer flattens an inline list's entries into
-- one block and reads the atoms through them, so the entries stay where they
-- are and stop being printed as headings. Selection stops charging for those
-- headings by the same route.
UPDATE sections
SET layout = 'inline_list'
WHERE kind = 'languages'
  AND layout <> 'inline_list';
