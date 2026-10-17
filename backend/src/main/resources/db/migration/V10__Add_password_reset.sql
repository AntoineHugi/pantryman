-- "Forgot password": one pending reset per user. Only a SHA-256 hash of the emailed token is stored,
-- so someone reading the database can't use it to reset passwords.
ALTER TABLE users
    ADD COLUMN password_reset_token_hash VARCHAR(64),
    ADD COLUMN password_reset_expires_at TIMESTAMP;
