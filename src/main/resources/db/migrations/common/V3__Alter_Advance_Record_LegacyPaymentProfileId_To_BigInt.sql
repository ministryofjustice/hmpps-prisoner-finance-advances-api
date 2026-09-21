ALTER TABLE advance_records
    ALTER COLUMN legacy_payment_profile_id TYPE BIGINT
        USING legacy_payment_profile_id::bigint;