package com.networth.networthbackend.gmail;

import java.sql.SQLException;
import java.util.Optional;

/** Decides which account/holding a matched email updates. */
public interface AccountResolver {

    /** @param label shown in logs and Slack, e.g. the account name */
    record Target(long accountId, long holdingId, String label) {
    }

    /** Empty when the email cannot be tied to exactly one account. */
    Optional<Target> resolve(GmailMessage message) throws SQLException;
}
