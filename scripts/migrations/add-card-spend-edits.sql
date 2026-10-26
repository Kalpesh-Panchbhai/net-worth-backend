-- Lets card spends be added and edited by hand: manual rows have no Gmail message id, and edited
-- marks an email row that was changed afterwards. The unique key on gmail_message_id still dedupes
-- emails (MySQL allows many NULLs in a unique index). Also reflected in src/main/resources/schema.sql.
-- Run this against the database before deploying the backend change.

ALTER TABLE card_spends
    MODIFY COLUMN gmail_message_id VARCHAR(64) NULL,
    ADD COLUMN edited TINYINT(1) NOT NULL DEFAULT 0 AFTER spent_on;
