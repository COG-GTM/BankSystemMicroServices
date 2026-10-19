package ru.kondrashov.personservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
class PersonServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;


    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

}
