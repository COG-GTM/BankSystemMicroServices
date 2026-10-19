package ru.kondrashov.personservice;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import ru.kondrashov.personservice.crypto.PiiCipher;
import ru.kondrashov.personservice.crypto.PlaintextPiiMigration;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.repositories.PeopleRepository;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
class PersonPiiEncryptionIntegrationTest {

    @Autowired
    private PeopleRepository peopleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PiiCipher cipher;

    @Autowired
    private Logger logger;

    @Test
    void personPiiIsEncryptedAtRestAndDecryptedOnRead() {
        LocalDate birthdate = LocalDate.of(1966, 6, 30);
        Person saved = peopleRepository.save(
                new Person(null, "Mike", "Tyson", birthdate, "+7 1234859520", "miketyson@mail.com"));

        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT first_name, last_name, birthdate, phone_number, email FROM public.people WHERE id = ?",
                    saved.getId());

            assertThat(row.values()).allSatisfy(value -> assertThat(value.toString()).startsWith("v1:"));
            assertThat(row.toString())
                    .doesNotContain("Mike")
                    .doesNotContain("Tyson")
                    .doesNotContain("1966")
                    .doesNotContain("1234859520")
                    .doesNotContain("miketyson");

            Person loaded = peopleRepository.findById(saved.getId()).orElseThrow(AssertionError::new);
            assertThat(loaded.getFirstName()).isEqualTo("Mike");
            assertThat(loaded.getLastName()).isEqualTo("Tyson");
            assertThat(loaded.getBirthdate()).isEqualTo(birthdate);
            assertThat(loaded.getPhoneNumber()).isEqualTo("+7 1234859520");
            assertThat(loaded.getEmail()).isEqualTo("miketyson@mail.com");
            assertThat(loaded.toString()).doesNotContain("Tyson").doesNotContain("miketyson");
        } finally {
            peopleRepository.deleteById(saved.getId());
        }
    }

    @Test
    void plaintextMigrationEncryptsLegacyRowsIdempotently() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO public.people (id, first_name, last_name, birthdate, phone_number, email) "
                + "VALUES (?, 'Bill', 'Johnson', '2000-12-26', '+7 123 258 54 85', 'BillJohnson@mail.com')", id);
        PlaintextPiiMigration migration = new PlaintextPiiMigration(jdbcTemplate, transactionTemplate, cipher, logger);

        try {
            assertThat(migration.migrate()).isEqualTo(1);

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT first_name, last_name, birthdate, phone_number, email FROM public.people WHERE id = ?", id);
            assertThat(row.values()).allSatisfy(value -> assertThat(value.toString()).startsWith("v1:"));

            Person loaded = peopleRepository.findById(id).orElseThrow(AssertionError::new);
            assertThat(loaded.getFirstName()).isEqualTo("Bill");
            assertThat(loaded.getLastName()).isEqualTo("Johnson");
            assertThat(loaded.getBirthdate()).isEqualTo(LocalDate.of(2000, 12, 26));
            assertThat(loaded.getPhoneNumber()).isEqualTo("+7 123 258 54 85");
            assertThat(loaded.getEmail()).isEqualTo("BillJohnson@mail.com");

            assertThat(migration.migrate()).isZero();
            assertThat(jdbcTemplate.queryForMap(
                    "SELECT first_name, last_name, birthdate, phone_number, email FROM public.people WHERE id = ?", id))
                    .isEqualTo(row);
        } finally {
            jdbcTemplate.update("DELETE FROM public.people WHERE id = ?", id);
        }
    }

    @Test
    void longValuesFitEncryptedColumns() {
        String longEmail = repeat('a', 240) + "@mail.com";
        String longPhone = repeat('9', 1000);
        Person saved = peopleRepository.save(
                new Person(null, "Mike", "Tyson", LocalDate.of(1966, 6, 30), longPhone, longEmail));
        try {
            Person loaded = peopleRepository.findById(saved.getId()).orElseThrow(AssertionError::new);
            assertThat(loaded.getEmail()).isEqualTo(longEmail);
            assertThat(loaded.getPhoneNumber()).isEqualTo(longPhone);
        } finally {
            peopleRepository.deleteById(saved.getId());
        }
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }
}
