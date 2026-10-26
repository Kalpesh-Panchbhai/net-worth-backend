package com.networth.networthbackend.cardspends;

import com.networth.networthbackend.common.JdbcRepository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Individual card spend rows for listing and hand edits. A row without a Gmail message id was entered
 * by hand; an email row changed afterwards is flagged {@code edited}.
 */
public class CardSpendEntryRepository extends JdbcRepository<CardSpendEntryRepository.CardSpend> {

    public record CardSpend(long id, long accountId, String accountName, BigDecimal amount, String cardLast4,
                            LocalDate spentOn, boolean fromEmail, boolean edited) {
    }

    private static final String SELECT = "SELECT s.id, s.account_id, a.name, s.amount, s.card_last4, s.spent_on,"
            + " s.gmail_message_id IS NOT NULL, s.edited"
            + " FROM card_spends s JOIN accounts a ON a.id = s.account_id";

    private static CardSpend map(java.sql.ResultSet rs) throws SQLException {
        return new CardSpend(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getBigDecimal(4), rs.getString(5),
                rs.getDate(6).toLocalDate(), rs.getBoolean(7), rs.getBoolean(8));
    }

    /** The user's spends between the two dates (both inclusive), newest first; one account when given. */
    public List<CardSpend> listForUser(long userId, Long accountId, LocalDate from, LocalDate to) throws SQLException {
        return queryList(SELECT + " WHERE a.user_id = ? AND s.spent_on BETWEEN ? AND ?"
                        + (accountId != null ? " AND s.account_id = ?" : "")
                        + " ORDER BY s.spent_on DESC, s.id DESC",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setDate(2, Date.valueOf(from));
                    ps.setDate(3, Date.valueOf(to));
                    if (accountId != null) ps.setLong(4, accountId);
                },
                CardSpendEntryRepository::map);
    }

    public Optional<CardSpend> findById(long id) throws SQLException {
        return queryOne(SELECT + " WHERE s.id = ?", ps -> ps.setLong(1, id), CardSpendEntryRepository::map);
    }

    /** Stores a spend entered by hand and returns its id. */
    public long create(long accountId, BigDecimal amount, String cardLast4, LocalDate spentOn) throws SQLException {
        return executeAndReturnGeneratedId("INSERT INTO card_spends (account_id, amount, card_last4, spent_on) VALUES (?, ?, ?, ?)",
                ps -> {
                    ps.setLong(1, accountId);
                    ps.setBigDecimal(2, amount);
                    setLast4(ps, 3, cardLast4);
                    ps.setDate(4, Date.valueOf(spentOn));
                });
    }

    /** Overwrites a spend; an email row is flagged edited so a hand change stays visible. */
    public int update(long id, long accountId, BigDecimal amount, String cardLast4, LocalDate spentOn) throws SQLException {
        return execute("UPDATE card_spends SET account_id = ?, amount = ?, card_last4 = ?, spent_on = ?,"
                        + " edited = (gmail_message_id IS NOT NULL) WHERE id = ?",
                ps -> {
                    ps.setLong(1, accountId);
                    ps.setBigDecimal(2, amount);
                    setLast4(ps, 3, cardLast4);
                    ps.setDate(4, Date.valueOf(spentOn));
                    ps.setLong(5, id);
                });
    }

    public int deleteById(long id) throws SQLException {
        return execute("DELETE FROM card_spends WHERE id = ?", ps -> ps.setLong(1, id));
    }

    private static void setLast4(java.sql.PreparedStatement ps, int index, String cardLast4) throws SQLException {
        if (cardLast4 == null) ps.setNull(index, Types.CHAR);
        else ps.setString(index, cardLast4);
    }
}
