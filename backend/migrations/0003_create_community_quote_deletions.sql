CREATE TABLE community_quote_deletions (
    id TEXT PRIMARY KEY,
    revision INTEGER NOT NULL UNIQUE,
    deleted_at INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX community_quote_deletions_revision_idx
    ON community_quote_deletions(revision);
