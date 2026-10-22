package com.networth.networthbackend.gmail;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Always resolves to the one active account of the given type with exactly this name, and its
 * aggregate holding. For several cards that share one account (e.g. ICICI cards with a combined limit).
 * Zero or several matches resolve to nothing rather than guessing.
 */
@Slf4j
public class NameResolver implements AccountResolver {

    private static final String SQL = "SELECT a.id, a.name, h.id FROM accounts a"
            + " JOIN holdings h ON h.account_id = a.id AND h.symbol = ?"
            + " WHERE a.is_active = 1 AND a.type = ? AND TRIM(a.name) = ?";

    private final String accountType;
    private final String name;

    /**
     * @param accountType {@code accounts.type} to search, e.g. CREDIT_CARD
     * @param name        the account's exact name
     */
    public NameResolver(String accountType, String name) {
        this.accountType = accountType;
        this.name = name;
    }

    @Override
    public Optional<Target> resolve(GmailMessage message, BankBalanceParser.ParsedBalance parsed) throws SQLException {
        // Only cards record invested = value on every row.
        boolean investedEqualsValue = Last4Resolver.TYPE_CREDIT_CARD.equals(accountType);

        List<Target> found = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {
            ps.setString(1, Constants.Accounts.AGG_HOLDING_SYMBOL);
            ps.setString(2, accountType);
            ps.setString(3, name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) found.add(new Target(rs.getLong(1), rs.getLong(3), rs.getString(2), investedEqualsValue));
            }
        }
        if (found.size() != 1) {
            log.warn("{} named '{}': expected exactly one account, found {}", accountType, name, found.size());
            return Optional.empty();
        }
        return Optional.of(found.get(0));
    }
}
