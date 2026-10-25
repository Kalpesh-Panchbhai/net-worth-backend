package com.networth.networthbackend.cardspends;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.gmail.BankBalanceParser;
import com.networth.networthbackend.gmail.Last4Resolver;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Credit card purchases read from bank alert emails, one row per email. Keyed by the Gmail message id,
 * so a redelivered or backfilled email is stored once.
 */
public class CardSpendRepository extends JdbcRepository<CardSpendRepository.CardTotal> {

    private static final int MERCHANT_MAX = 150;

    /**
     * One credit card account of the user with its spend in a date range and its latest balance
     * (the used limit, negative).
     */
    public record CardTotal(long accountId, String name, BigDecimal spent, BigDecimal balance) {
    }

    /** Stores the purchase unless this message was already recorded. */
    public void insertIgnore(long accountId, String gmailMessageId, LocalDate spentOn, BankBalanceParser.Spend spend) throws SQLException {
        String merchant = spend.merchant();
        if (merchant != null && merchant.length() > MERCHANT_MAX) merchant = merchant.substring(0, MERCHANT_MAX);
        String m = merchant;
        execute("INSERT IGNORE INTO card_spends (account_id, gmail_message_id, amount, merchant, card_last4, spent_on)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setLong(1, accountId);
                    ps.setString(2, gmailMessageId);
                    ps.setBigDecimal(3, spend.amount());
                    ps.setString(4, m);
                    ps.setString(5, spend.cardLast4());
                    ps.setDate(6, Date.valueOf(spentOn));
                });
    }

    /** Total spent on one account between the two dates, both inclusive. */
    public BigDecimal sumBetween(long accountId, LocalDate from, LocalDate to) throws SQLException {
        return queryOne("SELECT COALESCE(SUM(amount), 0) FROM card_spends WHERE account_id = ? AND spent_on BETWEEN ? AND ?",
                ps -> {
                    ps.setLong(1, accountId);
                    ps.setDate(2, Date.valueOf(from));
                    ps.setDate(3, Date.valueOf(to));
                },
                rs -> new CardTotal(accountId, null, rs.getBigDecimal(1), null))
                .map(CardTotal::spent).orElse(BigDecimal.ZERO);
    }

    /**
     * Every active credit card account of the user with what was spent between the two dates (both
     * inclusive) and the latest recorded balance of its aggregate holding (0 when none yet).
     */
    public List<CardTotal> totalsForUser(long userId, LocalDate from, LocalDate to) throws SQLException {
        return queryList("SELECT a.id, a.name,"
                        + " (SELECT COALESCE(SUM(s.amount), 0) FROM card_spends s"
                        + "   WHERE s.account_id = a.id AND s.spent_on BETWEEN ? AND ?) AS spent,"
                        + " COALESCE((SELECT t.value FROM transactions t"
                        + "   WHERE t.holding_id = h.id ORDER BY t.txn_date DESC LIMIT 1), 0) AS balance"
                        + " FROM accounts a"
                        + " JOIN holdings h ON h.account_id = a.id AND h.symbol = ?"
                        + " WHERE a.user_id = ? AND a.type = ? AND a.is_active = 1"
                        + " ORDER BY a.name",
                ps -> {
                    ps.setDate(1, Date.valueOf(from));
                    ps.setDate(2, Date.valueOf(to));
                    ps.setString(3, Constants.Accounts.AGG_HOLDING_SYMBOL);
                    ps.setLong(4, userId);
                    ps.setString(5, Last4Resolver.TYPE_CREDIT_CARD);
                },
                rs -> new CardTotal(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4)));
    }
}
