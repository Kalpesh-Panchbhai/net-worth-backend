package com.networth.networthbackend.mf;

import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the scheme-master sync once and prints the resolved category breakdown.
 * <p>
 * Step 1 of the MF analysis build is validation-first: the whole ranking layer sits on the
 * assumption that AMFI's ~104 category spellings collapse into the right peer groups, and the only
 * way to know is to look at the counts. Run this before building anything that reads the table.
 * <pre>
 *   mvn -q compile exec:java -Dexec.mainClass=com.networth.networthbackend.mf.MfSchemeSyncRunner
 * </pre>
 * Expect roughly 1,760 Direct+Growth schemes, of which ~570 equity, and zero unmapped categories.
 */
@Slf4j
public class MfSchemeSyncRunner {

    public static void main(String[] args) throws Exception {
        try {
            MfSchemeSyncService service = new MfSchemeSyncService();
            MfSchemeSyncService.Result result = service.refresh();

            if (result.written() == 0) {
                log.error("Nothing written — check the log above for the fetch or parse failure");
                return;
            }
            service.logCategoryBreakdown();

            if (!result.unmappedCategories().isEmpty()) {
                log.warn("Extend MfCategoryResolver for these AMFI categories: {}",
                        result.unmappedCategories());
            }
        } finally {
            Database.shutdown();
        }
    }
}
