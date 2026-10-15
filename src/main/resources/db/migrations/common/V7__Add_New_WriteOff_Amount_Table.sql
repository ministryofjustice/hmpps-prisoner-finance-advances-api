CREATE TABLE advance_write_off
(
    id                       UUID                        NOT NULL,

    advance_record_id        UUID                        NOT NULL,

    amount                   INTEGER                     NOT NULL,

    created_at               TIMESTAMP WITH TIME ZONE    NOT NULL,

    created_by               VARCHAR(255)                NOT NULL,

    CONSTRAINT pk_advance_record_payments PRIMARY KEY (id),
    CONSTRAINT fk_advance_record_payments_advance_record FOREIGN KEY (advance_record_id) REFERENCES advance_records(id)
);