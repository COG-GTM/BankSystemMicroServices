package ru.kondrashov.personservice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaManagementConfigTest {

    private static final Path MAIN_RESOURCES = Paths.get("src", "main", "resources");

    @Test
    void shippedHibernateConfigDoesNotRecreateSchema() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(MAIN_RESOURCES.resolve("hibernate.properties"))) {
            properties.load(reader);
        }

        assertThat(properties.getProperty("hibernate.hbm2ddl.auto")).isIn("validate", "none");
        assertThat(properties.getProperty("hibernate.show_sql")).isNotEqualToIgnoringCase("true");
    }

    @Test
    void schemaIsManagedByFlywayMigrations() throws IOException {
        Path migrations = MAIN_RESOURCES.resolve(Paths.get("db", "migration"));

        assertThat(migrations).isDirectory();
        assertThat(Files.list(migrations).anyMatch(p -> p.getFileName().toString().startsWith("V1__"))).isTrue();
    }
}
