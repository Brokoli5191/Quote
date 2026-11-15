-- Rate-limit slots of submissions that were withdrawn or deleted within 24 hours of being
-- sent. They keep counting toward the 24-hour limits, so a withdrawal does not free a slot.
-- The daily maintenance job removes rows once they are older than 24 hours.
CREATE TABLE deleted_submission_slots (
    installation_hash TEXT NOT NULL,
    ip_hash TEXT NOT NULL,
    submitted_at INTEGER NOT NULL
);

CREATE INDEX deleted_submission_slots_installation_idx
    ON deleted_submission_slots(installation_hash, submitted_at);
CREATE INDEX deleted_submission_slots_ip_idx
    ON deleted_submission_slots(ip_hash, submitted_at);
