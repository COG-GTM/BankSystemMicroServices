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

### Database connection and TLS
`PersonService` and `AccountService` connect to PostgreSQL with `sslmode=require` by default, so the local server must have TLS enabled (`ssl = on`).

| Variable | Default | Purpose |
| -------- | ------- | ------- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/<service>-db?sslmode=${DB_SSLMODE}` | Full JDBC URL |
| `DB_SSLMODE` | `require` | `sslmode` used by the default URL |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `postgres` / `12345` | Credentials |
| `APP_DATASOURCE_ALLOW_INSECURE_TRANSPORT` | `false` | Opt out of the TLS check below |

The services refuse to start if the URL points at a non-loopback host without `sslmode=require`, `verify-ca` or `verify-full` (or `ssl=true`). For any deployment off the local machine, use `sslmode=verify-full&sslrootcert=/path/to/ca.pem`. On a local server without TLS, set `DB_SSLMODE=disable`.

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

