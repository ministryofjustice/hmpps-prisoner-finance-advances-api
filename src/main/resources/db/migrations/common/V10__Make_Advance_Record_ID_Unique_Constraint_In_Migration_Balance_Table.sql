ALTER TABLE advance_balance_migrations
    ADD CONSTRAINT uc_advance_balance_migrations_advance_record_id UNIQUE (advance_record_id);