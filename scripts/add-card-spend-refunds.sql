-- Refunds for card spends: a refund is its own row with a negative amount, linked to the purchase it
-- refunds. Deleting the purchase deletes its refunds. Also reflected in src/main/resources/schema.sql.
-- Run this against the database before deploying the backend change.

ALTER TABLE card_spends
    ADD COLUMN refund_of BIGINT UNSIGNED NULL AFTER edited,
    ADD CONSTRAINT fk_card_spends_refund_of
        FOREIGN KEY (refund_of) REFERENCES card_spends (id)
            ON DELETE CASCADE;
