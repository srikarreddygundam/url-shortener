-- Nullable on purpose: existing rows get NULL = never expires, so the change
-- is invisible to every link created before this migration.
ALTER TABLE links ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
