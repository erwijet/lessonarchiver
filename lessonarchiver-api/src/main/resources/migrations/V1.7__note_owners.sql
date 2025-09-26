ALTER TABLE lessonarchiver.notes ADD owner_id uuid NOT NULL;

ALTER TABLE lessonarchiver.notes ADD CONSTRAINT fk_notes_owner_id__id FOREIGN KEY (owner_id) REFERENCES lessonarchiver.users(id) ON DELETE RESTRICT ON UPDATE RESTRICT