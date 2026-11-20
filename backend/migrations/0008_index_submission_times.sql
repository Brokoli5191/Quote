-- The global daily submission limit counts all rows of the last 24 hours in both tables.
CREATE INDEX IF NOT EXISTS submissions_submitted_at_idx ON submissions(submitted_at);
CREATE INDEX IF NOT EXISTS deleted_submission_slots_submitted_at_idx ON deleted_submission_slots(submitted_at);
