CREATE TABLE submissions (
    id TEXT PRIMARY KEY,
    quote_text TEXT NOT NULL CHECK (length(quote_text) BETWEEN 3 AND 500),
    normalized_text TEXT NOT NULL,
    author TEXT NOT NULL CHECK (length(author) <= 100),
    category TEXT NOT NULL CHECK (length(category) BETWEEN 1 AND 50),
    tags_json TEXT NOT NULL DEFAULT '[]' CHECK (json_valid(tags_json)),
    status TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'approved', 'rejected', 'duplicate')),
    content_hash TEXT NOT NULL UNIQUE,
    installation_hash TEXT NOT NULL,
    ip_hash TEXT NOT NULL,
    app_version TEXT CHECK (app_version IS NULL OR length(app_version) <= 30),
    submitted_at INTEGER NOT NULL DEFAULT (unixepoch()),
    reviewed_at INTEGER,
    reviewer_note TEXT CHECK (reviewer_note IS NULL OR length(reviewer_note) <= 500)
);

CREATE INDEX submissions_status_submitted_at_idx
    ON submissions(status, submitted_at DESC);
CREATE INDEX submissions_installation_submitted_at_idx
    ON submissions(installation_hash, submitted_at DESC);
CREATE INDEX submissions_ip_submitted_at_idx
    ON submissions(ip_hash, submitted_at DESC);
