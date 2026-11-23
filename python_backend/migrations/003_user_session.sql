-- Persistent session support (access token + rotating refresh token).
-- Base.metadata.create_all() only creates missing tables, so run this manually
-- against an existing database.

CREATE TABLE IF NOT EXISTS "USER_SESSION" (
    "ID"                 SERIAL PRIMARY KEY,
    "LOGIN_ID"           INTEGER NOT NULL REFERENCES "LOGIN_MASTER"("ID"),
    "REFRESH_TOKEN_HASH" VARCHAR(64) NOT NULL UNIQUE,
    "DEVICE_ID"          VARCHAR(128),
    "USER_AGENT"         VARCHAR(256),
    "IP_HASH"            VARCHAR(64),
    "CREATED_AT"         TIMESTAMP NOT NULL DEFAULT NOW(),
    "LAST_USED_AT"       TIMESTAMP,
    "EXPIRES_AT"         TIMESTAMP NOT NULL,
    "REVOKED_AT"         TIMESTAMP,
    "ROTATED_FROM"       INTEGER
);

CREATE INDEX IF NOT EXISTS "ix_USER_SESSION_LOGIN_ID"
    ON "USER_SESSION" ("LOGIN_ID");

CREATE INDEX IF NOT EXISTS "ix_USER_SESSION_REFRESH_TOKEN_HASH"
    ON "USER_SESSION" ("REFRESH_TOKEN_HASH");

-- Optional: keep only the most recent sessions per user.
-- DELETE FROM "USER_SESSION"
-- WHERE "REVOKED_AT" IS NOT NULL
--   AND "REVOKED_AT" < NOW() - INTERVAL '90 days';
