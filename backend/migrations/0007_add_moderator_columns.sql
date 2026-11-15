-- Moderator (Access email) who approved or rejected a submission. reviewer_note now holds
-- only the private note; older rows carry the moderator inside it.
ALTER TABLE submissions ADD COLUMN reviewed_by TEXT;
-- Moderator who last edited, unpublished or republished a community quote.
ALTER TABLE community_quotes ADD COLUMN updated_by TEXT;
