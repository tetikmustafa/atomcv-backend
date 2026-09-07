-- Bolum 20.2: a summary sits straight under its section heading, the way every
-- CV's does. Extraction has to put every atom somewhere and the shape it is
-- given has only entries, so it invented a title for the one it made -- a real
-- import produced `Professional Summary`, and the renderer printed it as a
-- heading above the paragraph.
--
-- The document it was read from has no such line. So the page carried a heading
-- nobody wrote and paid an entry heading's 21 pt for it, and it was the one
-- string on a produced CV that could not be traced to the source.
--
-- ProfileWriter now writes an About section's atoms at section level. This
-- moves the rows written before it did, and takes the invented entries with
-- them.
--
-- Order is rebuilt across the entries the atoms arrived in: each entry numbered
-- its own from zero, so a profile whose About had two entries would otherwise
-- keep two atoms at position 0 and leave the section's order to whatever the
-- database returned.
WITH renumbered AS (
    SELECT a.id,
           (ROW_NUMBER() OVER (
               PARTITION BY a.section_id
               ORDER BY e.display_order, a.display_order, a.id
           ) - 1)::SMALLINT AS position
    FROM atoms a
    JOIN entries e ON e.id = a.entry_id
    JOIN sections s ON s.id = a.section_id
    WHERE s.kind = 'about'
)
UPDATE atoms a
SET entry_id = NULL,
    display_order = renumbered.position
FROM renumbered
WHERE renumbered.id = a.id;

-- The entries they hung off carry an invented title and nothing else now.
DELETE FROM entries e
USING sections s
WHERE s.id = e.section_id
  AND s.kind = 'about'
  AND NOT EXISTS (SELECT 1 FROM atoms a WHERE a.entry_id = e.id);
