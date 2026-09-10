-- Bolum 26.4. The header block was one measured number for every profile,
-- calibrated for a name and two centred lines. A real header wraps: a contact
-- line with six fields and a headline that runs long take three lines, and the
-- page was charged for two. Measured against the compiler on 2026-09-10 that
-- was 16.3 pt short on senior_backend_tr -- a bullet and a half, always in the
-- direction that overflows.
--
-- So the header is measured like any other piece of text now, and what it
-- measured is kept here. Keyed by geometry and language together: the same
-- header wraps differently at another margin, and the contact labels are
-- translated, so "E-posta" and "Email" are not the same width.
--
-- Cleared when the header's own text changes, the way an atom variant's costs
-- are cleared when its wording does.
ALTER TABLE profiles
    ADD COLUMN header_costs JSONB NOT NULL DEFAULT '{}'::jsonb;
