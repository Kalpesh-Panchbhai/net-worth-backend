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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the account from its last 4 digits: those the parser read from the content if any, else the
 * ones in the subject ("... card XXXX-2293 is here"). Picks the one active account of the given type
 * whose name ends with those digits ("Axis Bank #2293"), and its aggregate holding.
 * Zero or several matches resolve to nothing rather than guessing.
 */
@Slf4j
public class Last4Resolver implements AccountResolver {

    private static final Pattern LAST4 = Pattern.compile("X{2,}[-\\s]?(\\d{4})\\b", Pattern.CASE_INSENSITIVE);

    public static final String TYPE_CREDIT_CARD = "CREDIT_CARD";

    private static final String SQL = "SELECT a.id, a.name, h.id FROM accounts a"
            + " JOIN holdings h ON h.account_id = a.id AND h.symbol = ?"
            + " WHERE a.is_active = 1 AND a.type = ? AND TRIM(a.name) LIKE ?";

    private final String accountType;

    /** @param accountType {@code accounts.type} to search, e.g. CREDIT_CARD */
    public Last4Resolver(String accountType) {
        this.accountType = accountType;
    }

    @Override
    public Optional<Target> resolve(GmailMessage message, BankBalanceParser.ParsedBalance parsed) throws SQLException {
        String last4 = parsed.accountLast4();
        if (last4 == null) {
            Matcher m = message.subject() == null ? null : LAST4.matcher(message.subject());
            if (m == null || !m.find()) return Optional.empty();
            last4 = m.group(1);
        }
        // Only cards record invested = value on every row.
        boolean investedEqualsValue = TYPE_CREDIT_CARD.equals(accountType);

        List<Target> found = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {
            ps.setString(1, Constants.Accounts.AGG_HOLDING_SYMBOL);
            ps.setString(2, accountType);
            ps.setString(3, "%" + last4);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) found.add(new Target(rs.getLong(1), rs.getLong(3), rs.getString(2), investedEqualsValue));
            }
        }
        if (found.size() != 1) {
            log.warn("{} ending {}: expected exactly one account, found {}", accountType, last4, found.size());
            return Optional.empty();
        }
        return Optional.of(found.get(0));
    }
}
