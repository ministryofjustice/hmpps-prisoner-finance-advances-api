CREATE TABLE advance_records
(
    id                        UUID                        NOT NULL,
    legacy_payment_profile_id VARCHAR(255)                NOT NULL,
    legacy_information_number VARCHAR(255)                NOT NULL,
    prison_number             VARCHAR(255)                NOT NULL,
    prison_id                 VARCHAR(255)                NOT NULL,
    amount                    INTEGER                     NOT NULL,
    created_on                TIMESTAMP WITH TIME ZONE    NOT NULL,
    repayment_start_date      TIMESTAMP WITH TIME ZONE    NOT NULL,
    repayment_amount          INTEGER                     NOT NULL,
    reference                 VARCHAR(255)                NOT NULL,
    created_by                VARCHAR(255)                NOT NULL,
    status                    VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_advance_records PRIMARY KEY (id)
);

ALTER TABLE advance_records
    ADD CONSTRAINT uc_advance_records_legacy_payment_profile_id UNIQUE (legacy_payment_profile_id);