CREATE TABLE advance_balance_migrations
(
    id                       UUID                        NOT NULL,

    advance_record_id        UUID                        NOT NULL,

    amount                   INTEGER                     NOT NULL,

    timestamp                TIMESTAMP WITH TIME ZONE    NOT NULL,

    CONSTRAINT pk_advance_balance_migrations PRIMARY KEY (id),
    CONSTRAINT fk_advance_balance_migrations_advance_record FOREIGN KEY (advance_record_id) REFERENCES advance_records(id)
);