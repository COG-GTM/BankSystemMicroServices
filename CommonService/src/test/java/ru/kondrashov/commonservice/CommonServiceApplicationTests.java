package ru.kondrashov.commonservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "person-service.client.password=service-password-0001",
        "common-service.security.users[0].username=operator",
        "common-service.security.users[0].password=operator-password-0001"
})
class CommonServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {

        assertThat(applicationContext).isNotNull();
    }

}
