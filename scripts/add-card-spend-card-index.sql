-- Lets the add-spend form list every card number used per account: the card numbers query groups by
-- (account_id, card_last4) and takes MAX(spent_on), which this index answers on its own.
-- Also reflected in src/main/resources/schema.sql. Run this against the database before deploying.

ALTER TABLE card_spends
    ADD KEY idx_card_spends_account_card_date (account_id, card_last4, spent_on);
