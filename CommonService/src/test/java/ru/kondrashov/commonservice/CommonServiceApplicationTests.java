package ru.kondrashov.commonservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "common.security.users[0].username=admin",
        "common.security.users[0].password={bcrypt}$2a$10$iRqGX4LQYe4DK3Yd8Ab/keEvMTNgq4DxXr/MUNW.FLGDE8KNQyC8i",
        "common.security.users[0].roles=STAFF,ADMIN"
})
class CommonServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {

        assertThat(applicationContext).isNotNull();
    }

}
