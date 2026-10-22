package com.networth.networthbackend.gmail;

import java.sql.SQLException;
import java.util.Optional;

/** Decides which account/holding a matched email updates. */
public interface AccountResolver {

    /**
     * @param label               shown in logs and Slack, e.g. the account name
     * @param investedEqualsValue true where the account records invested = value on every row
     *                            (credit cards); otherwise invested is carried forward
     */
    record Target(long accountId, long holdingId, String label, boolean investedEqualsValue) {
    }

    /** Empty when the email cannot be tied to exactly one account. */
    Optional<Target> resolve(GmailMessage message) throws SQLException;
}
