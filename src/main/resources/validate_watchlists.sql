-- ============================================================
-- Watchlist Validation Script
-- Validates account-watchlist membership rules per user.
-- Returns all violations; empty result = ALL RULES PASS.
-- ============================================================

-- Rule 1: All accounts must be in 'All' watchlist
SELECT a.id AS account_id, a.name AS account_name, a.user_id,
       'Rule 1: MISSING FROM All WATCHLIST' AS violation
FROM accounts a
WHERE NOT EXISTS (
    SELECT 1 FROM watchlist_accounts wa
    JOIN watchlists w ON w.id = wa.watchlist_id
    WHERE wa.account_id = a.id AND w.user_id = a.user_id AND w.name = 'All'
)

UNION ALL

-- Rule 2: Liabilities accounts must not be in any watchlist other than All and Liabilities
SELECT a.id, a.name, a.user_id,
       CONCAT('Rule 2: LIABILITIES ACCOUNT IN INVALID WATCHLIST [', w_other.name, ']')
FROM accounts a
JOIN watchlist_accounts wa_liab ON wa_liab.account_id = a.id
JOIN watchlists w_liab ON w_liab.id = wa_liab.watchlist_id AND w_liab.user_id = a.user_id AND w_liab.name = 'Liabilities'
JOIN watchlist_accounts wa_other ON wa_other.account_id = a.id
JOIN watchlists w_other ON w_other.id = wa_other.watchlist_id AND w_other.user_id = a.user_id AND w_other.name NOT IN ('All', 'Liabilities')

UNION ALL

-- Rule 3: Assets accounts must also be in Assets Fixed or Assets Liquid
SELECT a.id, a.name, a.user_id,
       'Rule 3: IN ASSETS BUT NOT IN ASSETS FIXED OR ASSETS LIQUID'
FROM accounts a
JOIN watchlist_accounts wa ON wa.account_id = a.id
JOIN watchlists w ON w.id = wa.watchlist_id AND w.user_id = a.user_id AND w.name = 'Assets'
WHERE NOT EXISTS (
    SELECT 1 FROM watchlist_accounts wa2
    JOIN watchlists w2 ON w2.id = wa2.watchlist_id
    WHERE wa2.account_id = a.id AND w2.user_id = a.user_id AND w2.name IN ('Assets Fixed', 'Assets Liquid')
)

UNION ALL

-- Rule 4: Assets Fixed accounts must also be in Assets Fixed Soft or Assets Fixed Hard
SELECT a.id, a.name, a.user_id,
       'Rule 4: IN ASSETS FIXED BUT NOT IN ASSETS FIXED SOFT OR ASSETS FIXED HARD'
FROM accounts a
JOIN watchlist_accounts wa ON wa.account_id = a.id
JOIN watchlists w ON w.id = wa.watchlist_id AND w.user_id = a.user_id AND w.name = 'Assets Fixed'
WHERE NOT EXISTS (
    SELECT 1 FROM watchlist_accounts wa2
    JOIN watchlists w2 ON w2.id = wa2.watchlist_id
    WHERE wa2.account_id = a.id AND w2.user_id = a.user_id AND w2.name IN ('Assets Fixed Soft', 'Assets Fixed Hard')
)

UNION ALL

-- Rule 5: Assets Liquid accounts must also be in Assets Liquid Soft or Assets Liquid Hard
SELECT a.id, a.name, a.user_id,
       'Rule 5: IN ASSETS LIQUID BUT NOT IN ASSETS LIQUID SOFT OR ASSETS LIQUID HARD'
FROM accounts a
JOIN watchlist_accounts wa ON wa.account_id = a.id
JOIN watchlists w ON w.id = wa.watchlist_id AND w.user_id = a.user_id AND w.name = 'Assets Liquid'
WHERE NOT EXISTS (
    SELECT 1 FROM watchlist_accounts wa2
    JOIN watchlists w2 ON w2.id = wa2.watchlist_id
    WHERE wa2.account_id = a.id AND w2.user_id = a.user_id AND w2.name IN ('Assets Liquid Soft', 'Assets Liquid Hard')
)

UNION ALL

-- Rule 6: Accounts in 'Assets Fixed' or 'Assets Liquid' must also be in 'Assets'
SELECT a.id, a.name, a.user_id,
       CONCAT('Rule 6: IN ', w.name, ' BUT NOT IN ASSETS')
FROM accounts a
JOIN watchlist_accounts wa ON wa.account_id = a.id
JOIN watchlists w ON w.id = wa.watchlist_id AND w.user_id = a.user_id AND w.name IN ('Assets Fixed', 'Assets Liquid')
WHERE NOT EXISTS (
    SELECT 1 FROM watchlist_accounts wa2
    JOIN watchlists w2 ON w2.id = wa2.watchlist_id
    WHERE wa2.account_id = a.id AND w2.user_id = a.user_id AND w2.name = 'Assets'
)

UNION ALL

-- Rule 7: Accounts in 'Assets Fixed Soft' or 'Assets Fixed Hard' must also be in 'Assets Fixed'
SELECT a.id, a.name, a.user_id,
       CONCAT('Rule 7: IN ', w.name, ' BUT NOT IN ASSETS FIXED')
FROM accounts a
JOIN watchlist_accounts wa ON wa.account_id = a.id
JOIN watchlists w ON w.id = wa.watchlist_id AND w.user_id = a.user_id AND w.name IN ('Assets Fixed Soft', 'Assets Fixed Hard')
WHERE NOT EXISTS (
    SELECT 1 FROM watchlist_accounts wa2
    JOIN watchlists w2 ON w2.id = wa2.watchlist_id
    WHERE wa2.account_id = a.id AND w2.user_id = a.user_id AND w2.name = 'Assets Fixed'
)

UNION ALL

-- Rule 8: Accounts in 'Assets Liquid Soft' or 'Assets Liquid Hard' must also be in 'Assets Liquid'
SELECT a.id, a.name, a.user_id,
       CONCAT('Rule 8: IN ', w.name, ' BUT NOT IN ASSETS LIQUID')
FROM accounts a
JOIN watchlist_accounts wa ON wa.account_id = a.id
JOIN watchlists w ON w.id = wa.watchlist_id AND w.user_id = a.user_id AND w.name IN ('Assets Liquid Soft', 'Assets Liquid Hard')
WHERE NOT EXISTS (
    SELECT 1 FROM watchlist_accounts wa2
    JOIN watchlists w2 ON w2.id = wa2.watchlist_id
    WHERE wa2.account_id = a.id AND w2.user_id = a.user_id AND w2.name = 'Assets Liquid'
)

UNION ALL

-- Rule 9: Account must not be in both Assets Fixed and Assets Liquid
SELECT a.id, a.name, a.user_id,
       'Rule 9: IN BOTH ASSETS FIXED AND ASSETS LIQUID'
FROM accounts a
JOIN watchlist_accounts wa1 ON wa1.account_id = a.id
JOIN watchlists w1 ON w1.id = wa1.watchlist_id AND w1.user_id = a.user_id AND w1.name = 'Assets Fixed'
JOIN watchlist_accounts wa2 ON wa2.account_id = a.id
JOIN watchlists w2 ON w2.id = wa2.watchlist_id AND w2.user_id = a.user_id AND w2.name = 'Assets Liquid'

UNION ALL

-- Rule 10: Account must not be in both Assets Fixed Soft and Assets Fixed Hard
SELECT a.id, a.name, a.user_id,
       'Rule 10: IN BOTH ASSETS FIXED SOFT AND ASSETS FIXED HARD'
FROM accounts a
JOIN watchlist_accounts wa1 ON wa1.account_id = a.id
JOIN watchlists w1 ON w1.id = wa1.watchlist_id AND w1.user_id = a.user_id AND w1.name = 'Assets Fixed Soft'
JOIN watchlist_accounts wa2 ON wa2.account_id = a.id
JOIN watchlists w2 ON w2.id = wa2.watchlist_id AND w2.user_id = a.user_id AND w2.name = 'Assets Fixed Hard'

UNION ALL

-- Rule 11: Account must not be in both Assets Liquid Soft and Assets Liquid Hard
SELECT a.id, a.name, a.user_id,
       'Rule 11: IN BOTH ASSETS LIQUID SOFT AND ASSETS LIQUID HARD'
FROM accounts a
JOIN watchlist_accounts wa1 ON wa1.account_id = a.id
JOIN watchlists w1 ON w1.id = wa1.watchlist_id AND w1.user_id = a.user_id AND w1.name = 'Assets Liquid Soft'
JOIN watchlist_accounts wa2 ON wa2.account_id = a.id
JOIN watchlists w2 ON w2.id = wa2.watchlist_id AND w2.user_id = a.user_id AND w2.name = 'Assets Liquid Hard'

UNION ALL

-- Rule 12: Every account must be in either Assets or Liabilities
SELECT a.id, a.name, a.user_id,
       'Rule 12: NOT IN ASSETS OR LIABILITIES'
FROM accounts a
WHERE NOT EXISTS (
    SELECT 1 FROM watchlist_accounts wa
    JOIN watchlists w ON w.id = wa.watchlist_id
    WHERE wa.account_id = a.id AND w.user_id = a.user_id AND w.name = 'Assets'
)
AND NOT EXISTS (
    SELECT 1 FROM watchlist_accounts wa
    JOIN watchlists w ON w.id = wa.watchlist_id
    WHERE wa.account_id = a.id AND w.user_id = a.user_id AND w.name = 'Liabilities'
);
