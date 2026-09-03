-- Bolum 20.2, constraint 4: an entry shows `min_atoms` bullets or none of
-- itself. The column defaults to 2 and the importer left the default in place,
-- so every entry extraction gave a single bullet -- a language, a degree, a
-- Tech Stack category -- carried a minimum it could never reach. Faz C did
-- exactly what it was told and dropped those entries whole, which is how a real
-- profile lost its Tech Stack, Languages and Education sections at once.
--
-- ProfileWriter now writes a reachable minimum at import. This repairs the rows
-- written before it did.
--
-- Safe to apply blind: `min_atoms` is reachable through the API but no client
-- has ever sent it -- it appears in atomcv-frontend only in mocks and in the
-- generated type definitions, never in a component -- so every value in
-- existence is this column's own default rather than somebody's choice. An
-- entry with no atoms is left alone at 0 by the same arithmetic; it reaches the
-- page by its heading and is exempt from the minimum anyway.
UPDATE entries e
SET min_atoms = counted.atom_count
FROM (
    SELECT en.id AS entry_id, COUNT(a.id)::SMALLINT AS atom_count
    FROM entries en
    LEFT JOIN atoms a ON a.entry_id = en.id
    GROUP BY en.id
) AS counted
WHERE e.id = counted.entry_id
  AND e.min_atoms > counted.atom_count;
