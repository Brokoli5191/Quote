-- Quote language as chosen in the app; rows from older app versions are English.
ALTER TABLE submissions ADD COLUMN language TEXT NOT NULL DEFAULT 'en'
    CHECK (language IN ('en', 'de'));
-- Note shown to the submitter in the app. reviewer_note stays private to moderators.
ALTER TABLE submissions ADD COLUMN review_note TEXT
    CHECK (review_note IS NULL OR length(review_note) <= 500);

ALTER TABLE community_quotes ADD COLUMN language TEXT NOT NULL DEFAULT 'en'
    CHECK (language IN ('en', 'de'));
-- Optional moderator translation into the other supported language.
ALTER TABLE community_quotes ADD COLUMN translation TEXT
    CHECK (translation IS NULL OR length(translation) BETWEEN 3 AND 500);
-- Letters-and-digits comparison key used to reject submissions of already published quotes.
-- Existing rows are filled in by the scheduled maintenance job.
ALTER TABLE community_quotes ADD COLUMN normalized_text TEXT;

CREATE INDEX community_quotes_normalized_text_idx
    ON community_quotes(normalized_text);

-- Content hashes of submissions a moderator deleted permanently, so the same text
-- cannot simply be submitted again.
CREATE TABLE blocked_content_hashes (
    content_hash TEXT PRIMARY KEY,
    blocked_at INTEGER NOT NULL DEFAULT (unixepoch())
);
