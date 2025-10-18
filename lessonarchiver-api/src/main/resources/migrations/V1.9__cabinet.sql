CREATE TABLE IF NOT EXISTS lessonarchiver.cabinet (id uuid PRIMARY KEY, owner_id uuid NOT NULL, "name" TEXT NOT NULL, description TEXT NULL, parent_id uuid NULL, created_at TIMESTAMP NOT NULL);

CREATE TABLE IF NOT EXISTS lessonarchiver.cabinet_materials (id uuid PRIMARY KEY, cabinet_id uuid NOT NULL, "order" INT NOT NULL, file_id uuid NULL, note_id uuid NULL);

CREATE TABLE IF NOT EXISTS lessonarchiver.file_grants (id uuid PRIMARY KEY, owner_id uuid NOT NULL, file_id uuid NOT NULL, created_at TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL, CONSTRAINT fk_file_grants_owner_id__id FOREIGN KEY (owner_id) REFERENCES lessonarchiver.users(id) ON DELETE RESTRICT ON UPDATE RESTRICT, CONSTRAINT fk_file_grants_file_id__id FOREIGN KEY (file_id) REFERENCES lessonarchiver.files(id) ON DELETE RESTRICT ON UPDATE RESTRICT);

ALTER TABLE lessonarchiver.cabinet ADD CONSTRAINT fk_cabinet_owner_id__id FOREIGN KEY (owner_id) REFERENCES lessonarchiver.users(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE lessonarchiver.cabinet ADD CONSTRAINT fk_cabinet_parent_id__id FOREIGN KEY (parent_id) REFERENCES lessonarchiver.cabinet(id) ON DELETE CASCADE ON UPDATE RESTRICT;

ALTER TABLE lessonarchiver.cabinet_materials ADD CONSTRAINT fk_cabinet_materials_cabinet_id__id FOREIGN KEY (cabinet_id) REFERENCES lessonarchiver.cabinet(id) ON DELETE CASCADE ON UPDATE RESTRICT;

ALTER TABLE lessonarchiver.cabinet_materials ADD CONSTRAINT fk_cabinet_materials_file_id__id FOREIGN KEY (file_id) REFERENCES lessonarchiver.files(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE lessonarchiver.cabinet_materials ADD CONSTRAINT fk_cabinet_materials_note_id__id FOREIGN KEY (note_id) REFERENCES lessonarchiver.notes(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE lessonarchiver.tags DROP COLUMN parent_id;

ALTER TABLE lessonarchiver.tags ADD CONSTRAINT tags_owner_id_name_unique UNIQUE (owner_id, "name");

ALTER TABLE lessonarchiver.cabinet ADD CONSTRAINT cabinet_owner_id_name_parent_id_unique UNIQUE NULLS NOT DISTINCT (owner_id, "name", parent_id);

ALTER TABLE lessonarchiver.files ADD pinned BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE lessonarchiver.notes ADD pinned BOOLEAN DEFAULT FALSE NOT NULL