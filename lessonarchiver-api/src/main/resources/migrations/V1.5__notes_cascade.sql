ALTER TABLE lessonarchiver.tags DROP CONSTRAINT fk_tags_parent_id__id;

ALTER TABLE lessonarchiver.tags ADD CONSTRAINT fk_tags_parent_id__id FOREIGN KEY (parent_id) REFERENCES lessonarchiver.tags(id) ON DELETE CASCADE ON UPDATE RESTRICT;

ALTER TABLE lessonarchiver.tags ADD CONSTRAINT tags_name_parent_id_unique UNIQUE ("name", parent_id)