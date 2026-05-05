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
- Java 8
- Spring Boot
- PostgreSQL
- Hibernate
- jUnit
- Log4j2
- Thymeleaf
- Swagger 2
- Lombok

## How to run
```sh
To launch classes Application.java in servecies customerService, accountService and commonService
```
Services commonService, accountService and customerService start on localhost ports 8080, 8081 and 8082 respectively.
Postgre database initialized with some sample user.

## Development Setup

The project supports two ways of running locally: an in-memory **H2 dev profile** (no external database required) and a **PostgreSQL** setup via docker-compose.

### Option 1: Run with the dev profile (H2, no external DB)

Each service has an `application-dev.yml` that switches the datasource to an in-memory H2 database. Use the `dev` profile to run any service:

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

When the dev profile is active, `PersonService` and `AccountService` automatically load seed data from `data-dev.sql` so you can interact with the APIs immediately.

The H2 web console is available while a service is running:
- PersonService: http://localhost:8082/h2-console (JDBC URL: `jdbc:h2:mem:person-service-db-dev`)
- AccountService: http://localhost:8081/h2-console (JDBC URL: `jdbc:h2:mem:account-service-db-dev`)

Use username `sa` and an empty password.

#### Seed data (dev profile)

The seed data uses fixed UUIDs so AccountService records reference PersonService records consistently:

| Person ID | Name | Accounts | Bills |
| --- | --- | --- | --- |
| `11111111-1111-1111-1111-111111111111` | John Doe | John Checking, John Savings | USD 1500 (overdraft), USD 5000 |
| `22222222-2222-2222-2222-222222222222` | Jane Smith | Jane Checking | EUR 2500 (overdraft) |
| `33333333-3333-3333-3333-333333333333` | Bob Johnson | Bob Checking | USD 800 |

Total: 3 people, 4 accounts, 4 bills.

### Option 2: Run with PostgreSQL via docker-compose

A `docker-compose.yml` at the repo root starts a single PostgreSQL 13 container that hosts both `person-service-db` and `account-service-db` (plus their `*-test` counterparts), created by `init-db.sh` on first boot.

```sh
docker-compose up -d
```

Then run each service without the dev profile:

```sh
mvn spring-boot:run
```

The default PostgreSQL credentials match the docker-compose container (`postgres` / `12345`). To point at a different database, override via environment variables:

```sh
DB_URL=jdbc:postgresql://localhost/person-service-db \
DB_USERNAME=postgres \
DB_PASSWORD=12345 \
mvn spring-boot:run
```

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

