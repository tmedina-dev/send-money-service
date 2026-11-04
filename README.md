# Send Money

Java 21 / Spring Boot application foundation for the backend coding assignment.
The wallet balance and friend list APIs are implemented. Other business endpoints are pending.

## Local development (Windows PowerShell)

Prerequisites: JDK 21, Docker Desktop running Linux containers, and Git.
Set `JAVA_HOME` to your JDK installation and include its `bin` folder in `PATH`.
Maven is downloaded automatically by the committed Maven Wrapper.

For the JDK currently installed on this machine, configure the current PowerShell
session with:

```powershell
$env:JAVA_HOME = 'C:\Program Files\BellSoft\LibericaJDK-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

On other machines, replace that path with the installed JDK 21 directory.

From this directory:

```powershell
java -version
docker version
docker compose up -d db
$env:DB_URL = 'jdbc:postgresql://localhost:5432/local_wallet'
$env:DB_USERNAME = 'send_money'
$env:DB_PASSWORD = 'local_dev_password'
$env:USER_DIRECTORY_BASE_URL = 'https://jsonplaceholder.typicode.com'
.\mvnw.cmd spring-boot:run
```

The app listens on port 8080. Wait for the `Started SendMoneyApplication` log.
The root `/` returns HTTP 404; use `/api/v1/wallet` for wallet balance requests.
PostgreSQL is available at `localhost:5432`, database `local_wallet`, with username
`send_money` and password `local_dev_password`. These are demonstration credentials.

The four application settings are required environment variables. For a locally
installed PostgreSQL instance, use your actual database credentials, for example:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/local_wallet'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = 'root'
$env:USER_DIRECTORY_BASE_URL = 'https://jsonplaceholder.typicode.com'
```

Compose reads an optional `.env` file; Spring Boot does not automatically load it.
Set the four variables in your shell or IDE run configuration before starting
Java locally. `.env.example` documents the required names. If you customize
Compose credentials using `.env.example`, set matching environment variables in
your shell or IDE when running Java locally. Changing credentials does not update
an already initialized PostgreSQL volume.

For the Dockerized app, Compose provides all four settings, using `db` as the
database hostname inside the container network. The example `DB_URL` uses
`localhost` for local Java development; Compose intentionally supplies its own URL.

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

## Wallet balance API

`GET /api/v1/wallet` returns only the user identified by the gateway's
`X-User-Id` header. There is no client-selected user ID in the URL or request body.
User IDs must be positive integers that identify a local wallet. Gateway
authentication is assumed, as required by the assignment.

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/wallet' -Headers @{ 'X-User-Id' = '1' }
```

Example HTTP 200 response:

```json
{
  "userId": 1,
  "name": "Leanne Graham",
  "balance": 10000.0,
  "currency": "PHP"
}
```

The response uses `Cache-Control: no-store`. Missing/invalid headers return HTTP
400; unknown users return HTTP 404. Handled API errors use `application/json`
with only a `detail` field; status is communicated through the HTTP status code.
Currency comes from `Money.CURRENCY`, not the DB.
Client-facing errors use generic messages (`Invalid request.`,
`Requested resource not found.`, or `Unable to process your request.`).
Specific failure reasons are logged server-side with the request ID.

The service fetches `name` from `GET /users/{id}` on JSONPlaceholder and merges
it with the locally stored balance. Profiles are not saved locally. Set
`USER_DIRECTORY_BASE_URL` to override the external service URL. Connections time
out after 3 seconds and reads after 5 seconds. External errors, missing profiles,
or invalid profile responses return HTTP 502; the service does not return an
incomplete wallet response. External HTTP I/O runs outside the database transaction.
Tests stub the directory and do not depend on live internet responses.

## Friend list API

`GET /api/v1/friends` requires a valid `X-User-Id` for an existing local wallet.
For this demo, a friend is an external directory user who has a local wallet,
excluding the caller. There is no separate friendship relationship table.
One external `GET /users` call supplies profiles, and one batched local query
checks recipient wallets. Responses expose IDs and names only, never balances
or full profiles. Profiles are not persisted. No eligible friends returns `[]`.

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/friends' -Headers @{ 'X-User-Id' = '1' }
```

Example response (abbreviated):

```json
[
  { "userId": 2, "name": "Ervin Howell" },
  { "userId": 3, "name": "Clementine Bauch" }
]
```

Missing/invalid identity returns HTTP 400, unknown callers return HTTP 404, and
external directory failures or malformed profiles return HTTP 502. All use the
generic error response. Request correlation, file logging, downstream timeouts,
and `Cache-Control: no-store` apply to this endpoint as well.

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

### Financial tables

`V1_02__money_transfers.sql` and `V1_03__daily_transfer_limits.sql` add transfer
history and daily limit configuration:

| Table                   | Purpose                                                                                                              |
| ----------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `wallets`               | One balance per user, using exact decimal amounts.                                                                   |
| `money_transfers`       | One row per completed transfer with sender, recipient, positive amount, and timestamp. Both users must have wallets. |
| `daily_transfer_limits` | One configuration per wallet, with a nonnegative daily outgoing limit. Zero prevents outgoing transfers.             |

User IDs refer directly to the external directory; names, emails, and other
profile data are not stored locally. `V1_04__remove_currency_columns.sql` removes
the redundant currency columns from all three tables without changing monetary
amounts. Earlier migrations remain unchanged for Flyway checksum compatibility.
The currency is fixed in Java as `Money.CURRENCY` (PHP); no currency conversion is
performed. Future API requests should omit currency and responses should report
`Money.CURRENCY.getCurrencyCode()`; use `BigDecimal` for Java monetary values.

The daily limit migration gives every existing wallet a **demo PHP 50,000 daily limit**, not an agreed
business requirement. New wallets must receive their limit configuration in the
same application transaction; treat a missing configuration as an error.

Daily totals should use completed outgoing transfers within a calendar day in
`Asia/Manila`, using a start-inclusive/end-exclusive timestamp range. Limit
enforcement, atomic wallet updates, and transfer creation still need service
implementation; these migrations alone do not implement money movement. The
service must set `updated_at` when modifying a limit and validate monetary scale
before persistence. Failed attempts are not stored in `money_transfers`.

Restart the application to apply pending migrations automatically. Inspect using:

```sql
SELECT * FROM wallets;
SELECT * FROM money_transfers ORDER BY created_at DESC;
SELECT * FROM daily_transfer_limits;
```

```powershell
docker compose down
```

This stops containers and preserves database data in the named volume.
`docker compose down -v` deletes that data and resets the demo on next startup.

## Next implementation steps

1. Add request correlation logging and reuse identity validation for future APIs.
2. Extend external directory integration for transaction responses.
3. Add atomic transfers, transaction history, and ownership restrictions.
4. Add concurrency and integration tests, OpenAPI documentation, and optional limits.
