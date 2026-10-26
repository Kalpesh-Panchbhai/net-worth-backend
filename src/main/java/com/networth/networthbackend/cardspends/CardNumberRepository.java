package com.networth.networthbackend.cardspends;

import com.networth.networthbackend.common.JdbcRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * The card numbers (last 4 digits) seen on a user's card spends, per account. Served from
 * {@code idx_card_spends_account_card_date}, so the spend rows themselves are never read.
 */
public class CardNumberRepository extends JdbcRepository<CardNumberRepository.CardNumber> {

    public record CardNumber(long accountId, String cardLast4, LocalDate lastUsed) {
    }

    /** Each account's card numbers, most recently used first. */
    public List<CardNumber> listForUser(long userId) throws SQLException {
        return queryList("SELECT s.account_id, s.card_last4, MAX(s.spent_on)"
                        + " FROM accounts a JOIN card_spends s ON s.account_id = a.id"
                        + " WHERE a.user_id = ? AND s.card_last4 IS NOT NULL"
                        + " GROUP BY s.account_id, s.card_last4"
                        + " ORDER BY s.account_id, MAX(s.spent_on) DESC",
                ps -> ps.setLong(1, userId),
                rs -> new CardNumber(rs.getLong(1), rs.getString(2), rs.getDate(3).toLocalDate()));
    }
}
