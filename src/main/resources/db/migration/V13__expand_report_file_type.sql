-- The DOCX MIME type
-- application/vnd.openxmlformats-officedocument.wordprocessingml.document
-- is longer than the original VARCHAR(50), so valid DOCX report uploads could not be saved.
ALTER TABLE reports ALTER COLUMN file_type TYPE VARCHAR(255);
