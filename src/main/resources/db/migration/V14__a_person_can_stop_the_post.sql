-- Bolum 57.7. Two columns, one for the answer and one for the way back to it.
--
-- lifecycle_emails is the preference itself: true until somebody says
-- otherwise, and read by every email the section marks as optional. The
-- deletion confirmation ignores it on purpose -- Bolum 57.4 requires telling a
-- person their data is gone, and a switch that suppressed that would be a way
-- of not telling them.
--
-- unsubscribe_token is how the preference is reachable from an inbox, where
-- there is no session and no cookie. Opaque and random rather than derived
-- from the id: a token that could be computed from something visible would let
-- anyone turn off anyone's email. It does not expire, because the email it
-- travels in does not either -- somebody can act on a message from a year ago.
--
-- A UUID rather than a signed value: it needs no secret to verify, which is one
-- fewer production key to hold, and it is revocable by writing a new one. The
-- default backfills every row that already exists.
ALTER TABLE users
    ADD COLUMN lifecycle_emails  BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN unsubscribe_token UUID    NOT NULL DEFAULT gen_random_uuid();

-- Looked up by the token alone, which is the only thing the person clicking
-- has. Unique because two accounts sharing one would make that lookup a guess.
ALTER TABLE users ADD CONSTRAINT users_unsubscribe_token_key UNIQUE (unsubscribe_token);
