-- Bolum 9: an anonymous person's profile becomes a row like anybody else's,
-- and is deleted when their session ends.
--
-- It was a Redis document, and the reason to move it is not storage: it is that
-- everything an anonymous person is now allowed to do -- edit the profile,
-- generate a CV against a posting -- is the same code path an account uses, and
-- a second store means a second implementation of every step. There already was
-- one, and it had already drifted: `EphemeralProfileWriter` reimplemented the
-- import writer and its own comment records what that cost -- "it differing
-- here is how it came to print a Languages heading twice and a summary under a
-- title nobody wrote". The scoped repositories underneath are addressed by
-- `ProfileRef`, and `ProfileRef.ephemeral(session)` already existed, so the
-- rows below are reached by exactly the guard an account's rows are.
--
-- `user_id` becomes nullable because an anonymous profile has no user. The
-- UNIQUE stays and keeps meaning what it meant: Postgres counts NULLs as
-- distinct in a unique index, so one profile per account still holds while any
-- number of anonymous sessions coexist.
ALTER TABLE profiles ALTER COLUMN user_id DROP NOT NULL;

-- When the session ends, so does this. NULL for an account's profile, which is
-- kept until the account is deleted (Bolum 57.4).
ALTER TABLE profiles ADD COLUMN expires_at TIMESTAMPTZ;

-- The invariant, and it is worth a constraint rather than a convention: a
-- profile has an owner or an expiry, never neither and never both.
--
-- Neither would be a row nothing can reach and nothing will ever remove -- the
-- anonymous equivalent of a leak. Both would be an account's profile with a
-- deletion date on it, which the sweep below would take. It also makes signing
-- up atomic in the only sense that matters: the UPDATE that sets `user_id` has
-- to clear `expires_at` in the same statement, or the database refuses it.
ALTER TABLE profiles
    ADD CONSTRAINT profiles_owner_xor_expiry
    CHECK ((user_id IS NULL) <> (expires_at IS NULL));

-- For the sweep, which asks one question every few minutes. Partial, because
-- the rows it never wants are the overwhelming majority: an account's profile
-- has no expiry and belongs in no index built for expiring things.
CREATE INDEX idx_profiles_expires_at ON profiles (expires_at)
    WHERE expires_at IS NOT NULL;
