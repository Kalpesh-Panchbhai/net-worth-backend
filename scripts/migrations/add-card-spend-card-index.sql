-- Lets the add-spend form list every card number used per account: the card numbers query groups by
-- (account_id, card_last4) and takes MAX(spent_on), which this index answers on its own.
-- Also reflected in src/main/resources/schema.sql. Safe to re-run: MySQL has no ADD KEY IF NOT EXISTS,
-- so the index is only added when information_schema does not already list it.

SET @missing = (SELECT COUNT(*) = 0 FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'card_spends'
                  AND index_name = 'idx_card_spends_account_card_date');
SET @ddl = IF(@missing,
              'ALTER TABLE card_spends ADD KEY idx_card_spends_account_card_date (account_id, card_last4, spent_on)',
              'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
