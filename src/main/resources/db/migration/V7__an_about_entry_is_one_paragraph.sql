-- Bolum 20.3: an entry prints `min_atoms` of itself or none of itself. The
-- column default is two, which is a bullet-list number, and a summary is not a
-- bullet list.
--
-- A person maintaining a master CV keeps several summaries -- one written
-- towards backend work, one towards data, one towards AI -- and the importer
-- wrote a minimum of two over the entry holding them. Faz C then did exactly
-- what it was told and put two opening paragraphs on one page. Both were the
-- person's own words and the document still read as a mistake.
--
-- ProfileWriter now writes one for an About entry. This repairs the rows
-- written before it did.
--
-- Safe to apply blind, on the same reasoning as V5: `min_atoms` is reachable
-- through the API but no client has ever sent it, so every value in existence
-- is a default rather than somebody's choice. Only About is touched, and only
-- where the stored number is above one.
UPDATE entries e
SET min_atoms = 1
FROM sections s
WHERE s.id = e.section_id
  AND s.kind = 'about'
  AND e.min_atoms > 1;
