package ru.kondrashov.commonservice;

import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {"app.downstream.username=test-user", "app.downstream.password=test-password"})
class CommonServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {

        assertThat(applicationContext).isNotNull();
    }

}
