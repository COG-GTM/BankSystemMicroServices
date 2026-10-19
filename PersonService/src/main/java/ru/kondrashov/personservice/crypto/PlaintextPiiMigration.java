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
import java.util.stream.Collectors;

/**
 * One-off migration for databases created before field-level encryption: widens the PII columns to text
 * (birthdate was a date column) in one short schema transaction, then encrypts any value not yet in the
 * {@code v1:} format in independently committed batches of locked rows. Idempotent; rows still in plaintext
 * fail closed on read until their batch commits.
 * Enable with {@code person.pii.migrate-plaintext=true} (PERSON_PII_MIGRATE_PLAINTEXT) for one deployment.
 */
@Component
@ConditionalOnProperty(name = "person.pii.migrate-plaintext", havingValue = "true")
public class PlaintextPiiMigration implements ApplicationRunner {

    static final List<String> PII_COLUMNS =
            Arrays.asList("first_name", "last_name", "birthdate", "phone_number", "email");
    static final int BATCH_SIZE = 500;

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
        widenColumnsToText();
        String needsEncryption = PII_COLUMNS.stream()
                .map(column -> column + " NOT LIKE '" + PiiCipher.VERSION_PREFIX + "%'")
                .collect(Collectors.joining(" OR ", "(", ")"));
        String select = "SELECT id, " + String.join(", ", PII_COLUMNS) + " FROM public.people WHERE " + needsEncryption;
        int count = 0;
        Object lastId = null;
        while (true) {
            Object afterId = lastId;
            BatchResult batch = transactionTemplate.execute(status -> encryptBatch(select, afterId));
            if (batch == null || batch.lastId == null) {
                return count;
            }
            count += batch.encrypted;
            lastId = batch.lastId;
        }
    }

    private void widenColumnsToText() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = 'people' AND data_type <> 'text'", String.class);
        List<String> alterations = PII_COLUMNS.stream()
                .filter(columns::contains)
                .map(column -> "ALTER COLUMN " + column + " TYPE text USING " + column + "::text")
                .collect(Collectors.toList());
        if (!alterations.isEmpty()) {
            transactionTemplate.execute(status -> {
                jdbcTemplate.execute("ALTER TABLE public.people " + String.join(", ", alterations));
                return null;
            });
        }
    }

    private BatchResult encryptBatch(String select, Object afterId) {
        String batchSelect = select + (afterId == null ? "" : " AND id > ?")
                + " ORDER BY id LIMIT " + BATCH_SIZE + " FOR UPDATE";
        List<Map<String, Object>> rows = afterId == null
                ? jdbcTemplate.queryForList(batchSelect)
                : jdbcTemplate.queryForList(batchSelect, afterId);
        Object lastId = null;
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
            lastId = row.get("id");
            values.add(lastId);
            jdbcTemplate.update("UPDATE public.people SET " + String.join(", ", assignments)
                    + " WHERE id = ?", values.toArray());
        }
        return new BatchResult(rows.size(), lastId);
    }

    private static final class BatchResult {
        private final int encrypted;
        private final Object lastId;

        private BatchResult(int encrypted, Object lastId) {
            this.encrypted = encrypted;
            this.lastId = lastId;
        }
    }
}
