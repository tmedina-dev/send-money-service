# Send Money

To run the backend and database on another device, follow [the short Docker run guide](docs/RUNNING.md).

Documentation:

- [Design choices, class diagram, and transfer sequence diagram](docs/DESIGN.md ,docs/CLASS DIAGRAM.md, docs/SEQUENCE DIAGRAM.md)
- [API specification, examples, headers, and error responses](docs/API.md)
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Run everything with Docker

Only Docker Desktop with Compose is required; no local JDK or Maven installation.
Stop the locally running app first so port 8080 is free.

```powershell
docker compose up --build
```

The Dockerfile builds the JAR from source and runs it in a Java 21 runtime image.
Compose waits for the database health check before starting the application.
Initial setup needs internet access for Maven dependencies and container images.

## Swagger API documentation

After starting the application, open http://localhost:8080/swagger-ui/index.html.
The OpenAPI JSON specification is at http://localhost:8080/v3/api-docs.

Expand **Wallet** or **Friends**, select **Try it out**, enter `X-User-Id` (for
example `1`), and click **Execute**. You can optionally supply `X-Request-Id`.
The documentation includes response schemas, example values, error responses,
and the correlation header. Only implemented `/api/v1/**` endpoints are listed.
Swagger UI and the specification themselves do not require `X-User-Id`.

## Request logging

Spring Boot automatically writes logs to `logs/app-yyyy-MM-dd.0.log` relative to the working
directory, as well as the console. No shell redirection or `Tee-Object` is needed.
Dates use `Asia/Manila`. The filename changes on the next log event after midnight
without restarting the app. Override the directory with `APP_LOG_DIR` if required.
The numeric suffix increments when a file reaches 10 MB. Logs roll daily or at
10 MB, with compressed archives retained for up to 14 days and a 100 MB archive
cap. Docker Compose stores logs in the persistent `app_logs` volume at `/app/logs`.

Find or search today's local logs in PowerShell:

```powershell
$logDate = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, 'Singapore Standard Time').ToString('yyyy-MM-dd')
Get-ChildItem "logs/app-$logDate.*.log"
Select-String -Path "logs/app-$logDate.*.log" -SimpleMatch 'postman-wallet-001'
```

For the Compose application:

```powershell
docker compose exec app sh -c 'tail -n 50 /app/logs/app-*.log'
```

Log files are excluded from Git and the Docker build context. Restart the app
after changing logging settings. `docker compose down -v` deletes the log volume
as well as the database volume.

Every API request receives an `X-Request-Id` response header. Send your own ID
(1-64 characters, starting with an alphanumeric character and containing only
letters, digits, dots, underscores, or hyphens), or let the server generate a UUID.
Missing/invalid IDs are replaced automatically.

Logs include request ID, user ID (when syntactically valid), HTTP method, path,
status, and elapsed milliseconds. Downstream directory requests forward the same
ID and log their status/timing. Search the console logs for the response's ID to
cross-reference the incoming request and its downstream call. Bodies, query
strings, authorization headers, and profile data are excluded from these logs.
Logging context is cleaned up after every request, including failures.

In Postman, optionally send `X-Request-Id: postman-wallet-001`, then inspect that
header in the response and search the application logs for `postman-wallet-001`.

## Tests

```powershell
.\mvnw.cmd verify
```

Docker must be running: the generated application context test uses an isolated
PostgreSQL Testcontainer. Docker image packaging skips tests, so run this check
separately before submission.

## Persistence and migrations

Flyway applies migrations on application startup. `V1_01__wallets.sql` creates
wallets and seeds external user IDs 1 through 10 with demo PHP balances.
Hibernate validates the schema and does not create or modify it.
