-- Changing a password invalidates previously issued access and refresh tokens.
ALTER TABLE users ADD COLUMN auth_version bigint NOT NULL DEFAULT 0;
