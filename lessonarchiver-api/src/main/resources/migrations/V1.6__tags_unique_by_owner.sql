ALTER TABLE lessonarchiver.tags ADD CONSTRAINT tags_owner_id_name_parent_id_unique UNIQUE NULLS NOT DISTINCT (owner_id, "name", parent_id);

ALTER TABLE IF EXISTS lessonarchiver.tags DROP CONSTRAINT IF EXISTS tags_name_parent_id_unique