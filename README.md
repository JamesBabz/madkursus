# madkursus

Minimal Spring Boot application using Java 21, PostgreSQL, Spring Data JPA, Flyway,
Spring Web, Validation, Thymeleaf, and Gradle.

## Prerequisites

- Java 21
- Docker with Docker Compose

The Gradle wrapper is included, so a separate Gradle installation is not required.

## Start PostgreSQL

```shell
docker compose up -d postgres
```

The database is exposed on `localhost:5432`. Its data is retained in the named
Docker volume `postgres_data`.

## Run the tests

On macOS/Linux:

```shell
./gradlew test
```

On Windows:

```powershell
.\gradlew.bat test
```

## Start the application

With PostgreSQL running, use:

```shell
./gradlew bootRun
```

On Windows, use `.\gradlew.bat bootRun`. Then open
<http://localhost:8080/api/health>; it returns `{"status":"UP"}`.

The default database connection can be overridden with the `DB_URL`,
`DB_USERNAME`, and `DB_PASSWORD` environment variables. The HTTP port can be
overridden with `SERVER_PORT`.

Stop PostgreSQL with `docker compose down`. Add `-v` only when you also want to
delete the persisted database volume.
