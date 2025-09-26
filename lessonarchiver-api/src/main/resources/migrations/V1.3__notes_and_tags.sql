CREATE TABLE IF NOT EXISTS lessonarchiver.notes (id uuid PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, updated_at TIMESTAMP NOT NULL);

CREATE TABLE IF NOT EXISTS lessonarchiver.tags (id uuid PRIMARY KEY, "name" VARCHAR(255) NOT NULL, parent_id uuid NULL);

CREATE TABLE IF NOT EXISTS lessonarchiver.tag_to_file (id uuid PRIMARY KEY, tag_id uuid NOT NULL, file_id uuid NOT NULL);

CREATE TABLE IF NOT EXISTS lessonarchiver.tag_to_note (id uuid PRIMARY KEY, tag_id uuid NOT NULL, note_id uuid NOT NULL);

ALTER TABLE lessonarchiver.tags ADD CONSTRAINT fk_tags_parent_id__id FOREIGN KEY (parent_id) REFERENCES lessonarchiver.tags(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE lessonarchiver.tag_to_file ADD CONSTRAINT fk_tag_to_file_tag_id__id FOREIGN KEY (tag_id) REFERENCES lessonarchiver.tags(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE lessonarchiver.tag_to_file ADD CONSTRAINT fk_tag_to_file_file_id__id FOREIGN KEY (file_id) REFERENCES lessonarchiver.files(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE lessonarchiver.tag_to_note ADD CONSTRAINT fk_tag_to_note_tag_id__id FOREIGN KEY (tag_id) REFERENCES lessonarchiver.tags(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE lessonarchiver.tag_to_note ADD CONSTRAINT fk_tag_to_note_note_id__id FOREIGN KEY (note_id) REFERENCES lessonarchiver.notes(id) ON DELETE RESTRICT ON UPDATE RESTRICT