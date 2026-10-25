# Running the legacy stack locally

This runs the current, unmodified stack: Java 8, Spring Boot 2.4.4, Maven, PostgreSQL.

| Service | Port | Database |
|---|---|---|
| CommonService (Thymeleaf UI) | 8080 | none (calls the other two over HTTP) |
| AccountService | 8081 | `account-service-db` |
| PersonService | 8082 | `person-service-db` |

## Prerequisites

- JDK 8 (`JAVA_HOME` pointing at it). Newer JDKs are not part of the legacy baseline.
- Maven 3.6+ (no Maven wrapper is committed).
- Docker with the Compose plugin.
- Free ports 5432, 8080, 8081, 8082.

## 1. Start PostgreSQL

```sh
docker compose -f docker-compose.legacy.yml up -d
docker compose -f docker-compose.legacy.yml ps   # wait for "healthy"
```

The container uses `postgres` / `12345` (the credentials hard-coded in each `application.yml`) and, on first
start, `docker/postgres-init/01-create-databases.sql` creates four databases:
`account-service-db`, `person-service-db` and the test databases `account-service-db-test`,
`person-service-db-test`.

The init script only runs on an empty data directory. The compose file uses no named volume, so
`docker compose -f docker-compose.legacy.yml down` followed by `up -d` gives a clean database.

## 2. Start the services

One terminal per service (any order; CommonService only calls the others on request):

```sh
export JAVA_HOME=/path/to/jdk8
(cd PersonService  && mvn spring-boot:run)
(cd AccountService && mvn spring-boot:run)
(cd CommonService  && mvn spring-boot:run)
```

Check:

```sh
curl -s localhost:8082/v1/people                                   # PersonService REST
curl -s -o /dev/null -w '%{http_code}\n' localhost:8081/v2/api-docs # AccountService Swagger 2 JSON
open http://localhost:8080/common/v1/info/people                    # UI people list
```

Swagger UI: http://localhost:8081/swagger-ui.html and http://localhost:8082/swagger-ui.html.

## 3. Load sample data

There is no seed script (despite the README's note about a sample user), so the database starts empty.
Create people through the UI (**Create person** on the people page) or REST:

```sh
curl -s -XPOST localhost:8082/v1/people -H 'Content-Type: application/json' \
  -d '{"birthdate":"1985-03-14","email":"ana.garcia@example.com","firstName":"Ana","lastName":"Garcia","phoneNumber":"+1 234 567 89 01"}'
```

## Data is wiped on every start

`AccountService/src/main/resources/hibernate.properties` and
`PersonService/src/main/resources/hibernate.properties` set `hibernate.hbm2ddl.auto = create`: Hibernate
drops and recreates the schema each time a service starts, so **all data in that service's database is lost
on restart**. This is legacy behaviour and is intentionally left unchanged here (the target stack replaces it
with Flyway). The test resources use `create-drop` against the `*-db-test` databases.

## 4. Run the tests

With the Postgres container running (integration tests use the `test` profile and the `*-db-test`
databases):

```sh
export JAVA_HOME=/path/to/jdk8
(cd PersonService  && mvn test)
(cd AccountService && mvn test)
(cd CommonService  && mvn test)
```

Baseline on the unmodified code (JDK 1.8.0_504, Maven 3.9.9, postgres:13):

| Service | Tests | Pass | Fail/Error | Result |
|---|---|---|---|---|
| PersonService | 6 | 6 | 0 | BUILD SUCCESS |
| AccountService | 20 | 14 | 6 | BUILD FAILURE |
| CommonService | 1 | 1 | 0 | BUILD SUCCESS |

The 6 AccountService errors are all in the `@WebMvcTest` slice tests
(`controllers/AccountsControllerTest` and `controllers/BillsControllerTest`, 3 each). The controllers take an
`org.apache.logging.log4j.Logger` constructor argument that is provided by an `@Bean` in
`config/ApplicationSpringConfig`, which the MVC slice does not load, so the context fails with
`NoSuchBeanDefinitionException: No qualifying bean of type 'org.apache.logging.log4j.Logger'`. This is a
pre-existing test defect, not an environment problem; it is left as-is in this baseline.

## Stop

```sh
# Ctrl-C each mvn spring-boot:run
docker compose -f docker-compose.legacy.yml down
```
