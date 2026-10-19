package ru.kondrashov.personservice.crypto;

import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * One-off migration for databases created before field-level encryption: widens the PII columns to text
 * (birthdate was a date column) and encrypts any value not yet in the {@code v1:} format. Idempotent.
 * Enable with {@code person.pii.migrate-plaintext=true} (PERSON_PII_MIGRATE_PLAINTEXT) for one deployment.
 */
@Component
@ConditionalOnProperty(name = "person.pii.migrate-plaintext", havingValue = "true")
public class PlaintextPiiMigration implements ApplicationRunner {

    static final List<String> PII_COLUMNS =
            Arrays.asList("first_name", "last_name", "birthdate", "phone_number", "email");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final PiiCipher cipher;
    private final Logger logger;

    public PlaintextPiiMigration(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
                                 PiiCipher cipher, Logger logger) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.cipher = cipher;
        this.logger = logger;
    }

    @Override
    public void run(ApplicationArguments args) {
        int migrated = migrate();
        logger.info("Plaintext PII migration finished: " + migrated + " people encrypted");
    }

    public int migrate() {
        Integer migrated = transactionTemplate.execute(status -> {
            for (String column : PII_COLUMNS) {
                jdbcTemplate.execute("ALTER TABLE public.people ALTER COLUMN " + column
                        + " TYPE text USING " + column + "::text");
            }
            int count = 0;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, " + String.join(", ", PII_COLUMNS) + " FROM public.people");
            for (Map<String, Object> row : rows) {
                List<String> assignments = new ArrayList<>();
                List<Object> values = new ArrayList<>();
                for (String column : PII_COLUMNS) {
                    Object value = row.get(column);
                    if (value != null && !value.toString().startsWith(PiiCipher.VERSION_PREFIX)) {
                        assignments.add(column + " = ?");
                        values.add(cipher.encrypt(value.toString()));
                    }
                }
                if (!assignments.isEmpty()) {
                    values.add(row.get("id"));
                    jdbcTemplate.update("UPDATE public.people SET " + String.join(", ", assignments)
                            + " WHERE id = ?", values.toArray());
                    count++;
                }
            }
            return count;
        });
        return migrated == null ? 0 : migrated;
    }
}
