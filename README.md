# madkursus

Minimal Spring Boot application using Java 21, PostgreSQL, Spring Data JPA, Flyway,
Spring Web, Validation, Thymeleaf, and Gradle.

The source code follows a ports-and-adapters structure:

- `inbound/rest` contains generated API delegates and REST-boundary mappers.
- `service/applications` contains application use cases.
- `service/models` contains framework-independent models.
- `service/ports` defines persistence boundaries.
- `outbound` contains port adapters, JPA entities, repositories, and mappers.

## REST API

Products are available at `/v1/products`, and inventory items are available at
`/v1/inventory`. Both resources support create, list, get by ID, patch, and
delete operations. Request validation errors return HTTP 400, and unknown IDs
return HTTP 404.

The REST contract is defined in `src/main/resources/openapi/madkursus-api.yaml`.
Gradle validates it and generates Spring delegate interfaces, controllers, and
DTOs under `build/generated/openapi`; generated Java is not committed.

OpenAPI documentation is generated at <http://localhost:8080/v3/api-docs>.
Swagger UI is available at <http://localhost:8080/swagger-ui.html> while the
application is running.

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

## Deploy with Docker Compose on Ubuntu

The production image is built in Docker with the included Gradle wrapper and a
Java 21 build image. Neither Gradle nor Java needs to be installed on the
server. The runtime image uses Java 21, runs as an unprivileged user, and listens
on port 8080.

Install Docker Engine and the Docker Compose plugin on the Ubuntu server, then
clone or copy this repository to the server. From the repository directory:

```shell
cp .env.example .env
nano .env
```

Set strong, matching database credentials in `.env`. `DB_URL` must use the
Compose service name and production database name:

```dotenv
DB_URL=jdbc:postgresql://postgres:5432/madkursus_prod
```

If the PostgreSQL volume already exists, the `POSTGRES_*` initialization values
do not create another database or change existing credentials. Create the
production database once, without deleting or renaming any development database:

```shell
docker compose up -d postgres
docker compose exec postgres sh -c 'createdb -U "$POSTGRES_USER" madkursus_prod'
```

Skip the `createdb` command if `madkursus_prod` already exists. Starting the
application then builds the image, waits for PostgreSQL to become healthy, and
runs the existing Flyway migrations against `madkursus_prod`:

```shell
docker compose up -d --build madkursus
docker compose logs -f madkursus
```

Set `POSTGRES_BIND_ADDRESS` in `.env` to the Ubuntu server's Tailscale IPv4
address (for example, an address in Tailscale's `100.64.0.0/10` range). This
makes PostgreSQL reachable from the Windows development PC over Tailscale
without binding it exclusively to localhost. Ensure the server firewall permits
TCP port 5432 only on the Tailscale interface or from the required Tailscale
device.

Open `http://SERVER_IP:8080/` to test the application. The application port is
published as 8080 for initial testing. Production application traffic reaches
PostgreSQL through the private Compose network as `postgres:5432`; it does not
use the host's Tailscale address. Port 5432 must not be forwarded by the router
or otherwise exposed to the public internet.

Useful deployment commands:

```shell
docker compose ps
curl --fail http://localhost:8080/
docker compose pull postgres
docker compose up -d --build
docker compose down
```

Do not use `docker compose down -v` unless you intentionally want to delete the
PostgreSQL volume and all databases stored in it.
