-- SHA-256 of the comparison key (letters and digits only) of texts a moderator deleted
-- permanently. Unlike blocked_content_hashes, which covers the exact text and author pair,
-- they ignore the author, case and punctuation, so the same text is not accepted again.
CREATE TABLE blocked_text_hashes (
    text_hash TEXT PRIMARY KEY,
    blocked_at INTEGER NOT NULL DEFAULT (unixepoch())
);
