CREATE TABLE sync_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    revision INTEGER NOT NULL DEFAULT 0
);

INSERT INTO sync_state (id, revision) VALUES (1, 0);

CREATE TABLE community_quotes (
    id TEXT PRIMARY KEY,
    submission_id TEXT NOT NULL UNIQUE REFERENCES submissions(id),
    quote_text TEXT NOT NULL CHECK (length(quote_text) BETWEEN 3 AND 500),
    author TEXT NOT NULL CHECK (length(author) <= 100),
    category TEXT NOT NULL CHECK (length(category) BETWEEN 1 AND 50),
    tags_json TEXT NOT NULL DEFAULT '[]' CHECK (json_valid(tags_json)),
    revision INTEGER NOT NULL UNIQUE,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    published_at INTEGER NOT NULL DEFAULT (unixepoch()),
    updated_at INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX community_quotes_active_revision_idx
    ON community_quotes(active, revision);
