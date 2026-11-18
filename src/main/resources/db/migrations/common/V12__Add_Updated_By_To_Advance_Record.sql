ALTER TABLE advance_records
    add updated_by VARCHAR(255)
;

COMMENT ON COLUMN advance_records.updated_by IS 'Nullable updated by audit column. [Sensitivity: NONE]';

ALTER TABLE advances_aud
    add updated_by VARCHAR(255)
;
COMMENT ON COLUMN advances_aud.updated_by IS 'Nullable updated by audit column. [Sensitivity: NONE]';

