-- ══════════════════════════════════════════════════════════
-- V2__narrow_oauth_providers.sql
--
-- LinkedIn is no longer a sign-in provider (Bolum 40.6). It was the only one
-- of the three that cannot be registered at all without a verified company
-- page, and the sign-in it buys is the one Google and GitHub already give.
--
-- A new migration and not an edit to V1: applied migrations are never
-- modified. And a migration rather than nothing, because the CHECK is where
-- the database states the closed set — leaving a value the application can no
-- longer produce is how a schema starts disagreeing with the code that owns
-- it, and reads later as "we support LinkedIn, where is the adapter".
--
-- No data to move: nothing writes this table yet. OAuth lands in the next
-- slice of Adim 3.3.
-- ══════════════════════════════════════════════════════════

ALTER TABLE oauth_identities
    DROP CONSTRAINT oauth_identities_provider_check;

ALTER TABLE oauth_identities
    ADD CONSTRAINT oauth_identities_provider_check
    CHECK (provider IN ('google', 'github'));
