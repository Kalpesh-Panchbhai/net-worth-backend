package com.networth.networthbackend.mf;

import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a user's mutual-fund holdings to AMFI schemes so the analyser's precomputed metrics can
 * be shown against what they actually hold.
 * <p>
 * Holdings store the fund's display name (from the broker sync), not its scheme code, so the match
 * is by name against the canonical Direct+Growth {@code mf_schemes} rows. On this data every held
 * fund matched exactly; a fund whose name does not match is simply omitted rather than guessed.
 */
@Slf4j
public class MfPortfolioRepository {

    /** One held fund, aggregated across the user's accounts, with its latest cumulative units/cost. */
    public record HeldScheme(int schemeCode, String name, String amc, String assetClass, String subCategory,
                             BigDecimal units, BigDecimal invested) {
    }

    /**
     * The user's MF holdings mapped to schemes, summed per scheme. {@code units} is the latest
     * cumulative units held and {@code invested} the latest cumulative cost, taken from each
     * holding's most recent transaction.
     */
    public List<HeldScheme> heldSchemes(long userId) throws SQLException {
        String sql = """
                SELECT s.scheme_code, s.name, s.amc, s.asset_class, s.sub_category,
                       SUM(lt.units) AS units, SUM(lt.invested) AS invested
                FROM accounts a
                JOIN holdings h ON h.account_id = a.id AND h.symbol <> '__ACCT__'
                JOIN mf_schemes s ON LOWER(s.name) = LOWER(h.name)
                     AND s.plan = 'DIRECT' AND s.`option` = 'GROWTH' AND s.is_live = 1
                JOIN (
                    SELECT t1.holding_id, t1.value AS units, t1.invested
                    FROM transactions t1
                    JOIN (SELECT holding_id, MAX(txn_date) AS md FROM transactions GROUP BY holding_id) t2
                      ON t1.holding_id = t2.holding_id AND t1.txn_date = t2.md
                ) lt ON lt.holding_id = h.id
                WHERE a.user_id = ?
                GROUP BY s.scheme_code, s.name, s.amc, s.asset_class, s.sub_category
                """;
        List<HeldScheme> out = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new HeldScheme(rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7)));
                }
            }
        }
        return out;
    }
}
