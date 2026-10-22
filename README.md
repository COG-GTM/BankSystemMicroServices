# Bank System
![](https://img.shields.io/badge/Code-Java-informational?style=flat&logo=Java&logoColor=white&color=4AB197)
![](https://img.shields.io/badge/Code-SpringBoot-informational?style=flat&logo=Spring&logoColor=white&color=4AB197)
![](https://img.shields.io/badge/Code-Hibernate-informational?style=flat&logo=Hibernate&logoColor=white&color=4AB197)
![](https://img.shields.io/badge/Code-Maven-informational?style=flat&logo=Maven&logoColor=white&color=4AB197)
![](https://img.shields.io/badge/Code-PostgreSQL-informational?style=flat&logo=PostgreSQL&logoColor=white&color=4AB197)
![](https://img.shields.io/badge/Code-Thymeleaf-informational?style=flat&logo=Thymeleaf&logoColor=white&color=4AB197)
![](https://img.shields.io/badge/Code-Lombok-informational?style=flat&logo=Lombok&logoColor=white&color=4AB197)
![](https://img.shields.io/badge/Test-JUnit-informational?style=flat&logo=jUnit&logoColor=white&color=4AB197)
![](https://img.shields.io/badge/Log-Log4j2-informational?style=flat&logo=Log4j2&logoColor=white&color=4AB197)
<br>
This is a prototype of a banking system, which demonstrates [Microservice Architecture Pattern](http://martinfowler.com/microservices/) using Spring Boot.
## Technologies
- Java 17
- Spring Boot 3.5
- Spring Security (HTTP Basic)
- PostgreSQL
- Hibernate
- jUnit
- Log4j2
- Thymeleaf
- springdoc-openapi (OpenAPI 3 / Swagger UI)
- Lombok

## How to run
Requirements: JDK 17+, Maven 3.6.3+, PostgreSQL with the `account-service-db` and `person-service-db` databases.

Export the required environment variables, then launch the `*Application.java` classes of PersonService, AccountService and CommonService (or `mvn spring-boot:run` in each module):
```sh
export DB_USERNAME=<postgres user>
export DB_PASSWORD=<postgres password>
export API_USERNAME=<REST API user>
export API_PASSWORD=<REST API password>
```
Services commonService, accountService and personService start on localhost ports 8080, 8081 and 8082 respectively.

### Environment variables
No credentials are stored in the repository; they are resolved from the environment at startup. A missing required variable makes the service fail to start.

| Variable | Used by | Required | Description |
| -------- | ------- | -------- | ----------- |
| `DB_USERNAME` | AccountService, PersonService | yes | PostgreSQL user |
| `DB_PASSWORD` | AccountService, PersonService | yes | PostgreSQL password |
| `DB_URL` | AccountService, PersonService | no | JDBC URL. Defaults to `jdbc:postgresql://localhost:5432/account-service-db` / `.../person-service-db` |
| `API_USERNAME` | AccountService, PersonService, CommonService | yes | User accepted by the REST API (HTTP Basic). CommonService sends it to the downstream services |
| `API_PASSWORD` | AccountService, PersonService, CommonService | yes | Password for `API_USERNAME` |
| `TEST_DB_URL` | AccountService, PersonService tests | no | JDBC URL of the test database. Defaults to `jdbc:postgresql://localhost:5432/<service>-db-test` |

### Security
The REST APIs of AccountService and PersonService are protected with **HTTP Basic authentication** (Spring Security, stateless, CSRF disabled because no browser session/cookies are used). Every `/v1/**` endpoint requires the credentials configured through `API_USERNAME` / `API_PASSWORD`; unauthenticated requests get `401 Unauthorized`.

```sh
curl -u "$API_USERNAME:$API_PASSWORD" http://localhost:8082/v1/people
```

The password is hashed in memory with the delegating (bcrypt) `PasswordEncoder`. The API documentation (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`) and the `/v1` redirect are public; Swagger UI exposes an **Authorize** button to send Basic credentials when trying out endpoints. Use HTTPS in front of the services in any non-local environment, since Basic credentials are only base64-encoded.

CommonService (the Thymeleaf UI) calls the downstream services through a `RestTemplate` configured with the same `API_USERNAME` / `API_PASSWORD`.

### API documentation
- AccountService: http://localhost:8081/swagger-ui.html (OpenAPI JSON at `/v3/api-docs`)
- PersonService: http://localhost:8082/swagger-ui.html (OpenAPI JSON at `/v3/api-docs`)

### Build and tests
```sh
cd AccountService && mvn clean verify
```
Integration tests need a reachable PostgreSQL (`account-service-db-test` / `person-service-db-test`, override with `TEST_DB_URL`) and `DB_USERNAME` / `DB_PASSWORD` exported.

## Functional Services
BankSystem was decomposed into three core microservices. All of them are independently deployable applications, organized around certain business domains.

### People Service
Contains the general logic of working and validating person.

| HTTP METHOD | PATH | USAGE |
| -----------| ------ | ------ |
| GET | /v1/people | get all people | 
| GET | /v1/people/{id} | get person by id | 
| POST | /v1/people| create new person | 
| PUT | /v1/people/{id}| update person info | 
| DELETE | /v1/people/{id} | delete person by id | 

#### Sample JSON for People Service
#### Create a person :
```sh
{
    "birthdate": "2021-04-20",
    "email": "mail@mail.ru",
    "firstName": "Bill",
    "lastName": "Johnson",
    "phoneNumber": "+1 234 567 89 01"
} 
```
### Account Service
Contains the general logic of working and validating account, bill and transactions.

| HTTP METHOD | PATH | USAGE |
| -----------| ------ | ------ |
| GET | /v1/people/{personId}/accounts | get all accounts by personId | 
| GET | /v1/people/{personId}/accounts/{accountId}/bills | get all bills by accountId | 
| GET | /v1/people/{personId}/accounts/{accountId}/bills/{id}/transactions | get all transactions by bill | 
| POST | /v1/people/{personId}/accounts | create account for exact person | 
| POST | /v1/people/{personId}/accounts/{accountId}/bills | create bill for exact account | 
| PUT | /v1/people/{personId}/accounts/{id} | update account by id |
| PUT | /v1/people/{personId}/accounts/{accountId}/bills/{id} | update bill by id | 
| PUT | /v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments | commit payment by bill | 
| PUT | /v1/people/{personId}/accounts/{accountId}/bills/{billId}/adjustments | commit adjustment by bill | 
| PUT | /v1/people/{personId}/accounts/{accountId}/bills/{sourceId}/transfer/{beneficiaryBillId} | commit transfer between two bills | 
| DELETE | /v1/people/{personId}/accounts/{id} | delete account by id |
| DELETE | /v1/people/{personId}/accounts/{accountId}/bills/{id} | delete bill by id |

#### Sample JSON for Account Service
##### Create an account:
```sh
{
  "creationDate": "2021-04-20",
  "name": "Salary",
  "personId": "c5a6757f-fec4-41f3-a6a6-eb03d70c0d6f"
} 
```
##### Create a bill:
```sh
{
  "amount": 10,
  "currency": "USD",
  "isOverdraft": false,
  "account": {
    "creationDate": "2021-04-20",
    "id": "58612097-bc0d-4de3-b32b-2221c81ec8e1",
    "name": "Salary",
    "personId": "c5a6757f-fec4-41f3-a6a6-eb03d70c0d6f"
  }
} 
```
##### Commit a payment:
```sh
{
  "amount": 700,
  "currency": "RUB",
  "message": "Payment for a purchase in the OK store in the amount of 700 RUB",
  "time": "2021-04-20T12:27:59.873Z",
  "bill": {
      "account": {
          "id": "58612097-bc0d-4de3-b32b-2221c81ec8e1",
          "name": "Salary",
          "creationDate": "2021-04-20",
          "personId": "c5a6757f-fec4-41f3-a6a6-eb03d70c0d6f"
      },
    "amount": 10,
    "currency": "USD",
    "isOverdraft": false
  }
}
```
##### Commit an adjustment:
```sh
{
  "amount": 10,
  "currency": "USD",
  "message": "Adjustment of the account in the amount of 10 USD",
  "time": "2021-04-20T12:27:59.873Z",
  "bill": {
      "account": {
          "id": "58612097-bc0d-4de3-b32b-2221c81ec8e1",
          "name": "Salary",
          "creationDate": "2021-04-20",
          "personId": "c5a6757f-fec4-41f3-a6a6-eb03d70c0d6f"
      },
    "amount": 0,
    "currency": "USD",
    "isOverdraft": false
  }
}
```
##### Commit a transfer:
```sh
{
  "amount": 10,
  "time": "2021-04-20T12:27:59.880Z",
  "currency": "USD",
  "message": "Transfer of funds between accounts",
  "sourceBill": {
    "account": {
      "creationDate": "2021-04-20",
      "id": "58612097-bc0d-4de3-b32b-2221c81ec8e1",
      "name": "Salary",
      "personId": "c5a6757f-fec4-41f3-a6a6-eb03d70c0d6f"
    },
    "amount": 10,
    "currency": "USD",
    "id": "987ae068-7960-4119-bc51-914e2e0c71a0",
    "isOverdraft": false
  },
  "beneficiaryBill": {
    "account": {
      "creationDate": "2021-04-20",
      "id": "58612097-bc0d-4de3-b32b-2221c81ec8e1",
      "name": "Salary",
      "personId": "c5a6757f-fec4-41f3-a6a6-eb03d70c0d6f"
    },
    "amount": 0,
    "currency": "RUB",
    "id": "2b10fd68-f8fd-4cdb-b9ad-eae860828c5c",
    "isOverdraft": true
  }
}
```
### Common Service
Provides a single access point for the client and implements a visual interface.

