CREATE TABLE advance_record_payments
(
    id                       UUID                        NOT NULL,

    advance_record_id        UUID                        NOT NULL,

    transaction_id           UUID                        NOT NULL,

    posting_type             VARCHAR(2)                  NOT NULL,

    amount                   INTEGER                     NOT NULL,

    timestamp                TIMESTAMP WITH TIME ZONE    NOT NULL,

    created_by               VARCHAR(255)                NOT NULL,

    CONSTRAINT pk_advance_record_payments PRIMARY KEY (id),
    CONSTRAINT fk_advance_record_payments_advance_record FOREIGN KEY (advance_record_id) REFERENCES advance_records(id),
    CONSTRAINT uc_advance_record_payments_transaction_id UNIQUE (transaction_id)
);