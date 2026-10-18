-- Invite codes are valid for 7 days. A member who opens the households page after that gets a new code.
-- Codes that exist today get a full 7 days from now.
ALTER TABLE households
    ADD COLUMN invite_code_expires_at TIMESTAMP;

UPDATE households
SET invite_code_expires_at = now() + INTERVAL '7 days';

ALTER TABLE households
    ALTER COLUMN invite_code_expires_at SET NOT NULL;
