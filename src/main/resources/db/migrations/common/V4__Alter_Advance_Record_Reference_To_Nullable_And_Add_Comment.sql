ALTER TABLE advance_records
    ALTER COLUMN reference DROP NOT NULL,
    ADD COLUMN comment VARCHAR(255);