ALTER TABLE lessonarchiver.tags ADD owner_id uuid NOT NULL;

ALTER TABLE lessonarchiver.tags ADD CONSTRAINT fk_tags_owner_id__id FOREIGN KEY (owner_id) REFERENCES lessonarchiver.users(id) ON DELETE RESTRICT ON UPDATE RESTRICT