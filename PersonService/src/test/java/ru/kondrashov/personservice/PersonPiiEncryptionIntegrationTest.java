package ru.kondrashov.personservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.repositories.PeopleRepository;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
class PersonPiiEncryptionIntegrationTest {

    @Autowired
    private PeopleRepository peopleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
}
