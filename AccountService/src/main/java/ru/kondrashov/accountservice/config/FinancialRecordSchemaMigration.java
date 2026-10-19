package ru.kondrashov.accountservice.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Applies db/retain_financial_records.sql after Hibernate has updated the schema, replacing cascading
 * foreign keys left over from earlier schema generations so financial history cannot be cascade-deleted.
 */
@Component
public class FinancialRecordSchemaMigration implements ApplicationRunner {

    private static final Logger LOGGER = LogManager.getLogger(FinancialRecordSchemaMigration.class);
    private static final String SCRIPT = "db/retain_financial_records.sql";

    private final JdbcTemplate jdbcTemplate;

    public FinancialRecordSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        jdbcTemplate.execute(StreamUtils.copyToString(new ClassPathResource(SCRIPT).getInputStream(), StandardCharsets.UTF_8));
        LOGGER.info("Applied {}", SCRIPT);
    }
}
