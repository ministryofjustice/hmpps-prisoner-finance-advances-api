CREATE TABLE advance_write_off
(
    id                       UUID                        NOT NULL,

    advance_record_id        UUID                        NOT NULL,

    amount                   INTEGER                     NOT NULL,

    created_at               TIMESTAMP WITH TIME ZONE    NOT NULL,

    created_by               VARCHAR(255)                NOT NULL,

    CONSTRAINT pk_advance_write_off PRIMARY KEY (id),
    CONSTRAINT fk_advance_write_off_advance_record FOREIGN KEY (advance_record_id) REFERENCES advance_records(id),
    CONSTRAINT uq_advance_write_off_advance_record  UNIQUE (advance_record_id)
);