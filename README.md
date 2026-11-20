# Coupon service

A REST API for creating country-restricted coupons and redeeming them. No authentication is required. Each supplied `userId` can redeem a coupon once, and each coupon has a total usage limit.

## Run locally

You need JDK 25, Maven, and a running PostgreSQL instance. Create a database and a user that can create tables in its schema. Liquibase creates the application tables on startup; it does not create the database itself.

For a local PostgreSQL instance, you can optionally use Docker:

```powershell
docker run --name empik-postgres -d -p 127.0.0.1:5435:5432 -e POSTGRES_DB=empik -e POSTGRES_USER=empik -e POSTGRES_PASSWORD= postgres:17.7
```

Set the connection settings and your [IPinfo Lite](https://ipinfo.io/lite) token in the same shell you use to launch the app:

```powershell
$env:DB_PASSWORD = 'local-dev-password' # Use your database password
$env:IPINFO_TOKEN = '<your IPinfo Lite token>'
mvn package
java -jar .\target\empik-1.0-SNAPSHOT.jar
```

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5435` | PostgreSQL port; set to `5432` if your instance uses the standard port |
| `DB_NAME` | `empik` | Existing database name |
| `DB_USER` | `empik` | PostgreSQL username |
| `DB_PASSWORD` | Empty | PostgreSQL password |
| `IPINFO_TOKEN` | Empty | Required for redemption lookups; without it, a valid lookup request returns `503` |
| `SERVER_ADDRESS` | `127.0.0.1` | Address the app binds to |
| `SERVER_PORT` | `8080` | HTTP port |
| `API_BASE_PATH` | `/empik` | Prefix for all endpoints |
| `CLIENT_IP_HEADER` | `X-Forwarded-For` | Header used to determine the client's country |

The default base URL is `http://127.0.0.1:8080/empik`. For local testing, you can supply the client IP header yourself. In a deployment, only trust this header behind a reverse proxy that overwrites incoming values and prevents clients from reaching the app directly.

IPinfo requests use a direct network connection. If redemption returns `503` and logs show a connection timeout, check that the application can reach `api.ipinfo.io` on port 443 from its runtime environment. Insomnia may use a different network or proxy configuration. The connect, response and per-attempt lookup timeouts can be adjusted with `IPINFO_CONNECT_TIMEOUT_MS` (default `1000`), `IPINFO_RESPONSE_TIMEOUT_MS` (default `2000`) and `IPINFO_LOOKUP_TIMEOUT_MS` (default `3000`).

IPinfo lookup failures are logged without the client IP or token. Failed provider requests include a reason, attempt count and duration. Retry details are available at DEBUG level for `pl.bienkowski.empik.core.geolocation.IpGeolocationResolver`. Database readiness logs only failure and recovery transitions, not every probe.

## API

Paths below include the default `/empik` prefix. Send JSON POST bodies with `Content-Type: application/json`.

### `POST /empik/coupons` — Create a coupon

```json
{
  "code": "SALE5",
  "countryCode": "PL",
  "maxUsages": 2
}
```

`code` must be nonblank and at least 5 characters long. Codes are case-sensitive and unique per country. `countryCode` must be an officially assigned uppercase ISO 3166-1 alpha-2 code. `maxUsages` must be a positive integer.

**`201 Created`** returns the coupon, with `currentUsages` initially set to `0`:

```json
{
  "code": "SALE5",
  "countryCode": "PL",
  "maxUsages": 2,
  "currentUsages": 0,
  "createdAt": "2026-11-19T14:48:00Z"
}
```

| Status | When |
| --- | --- |
| `400 Bad Request` | The request body is missing, malformed, or fails validation, including an invalid country code. |
| `409 Conflict` | `Coupon code already exists for this country` |
| `500 Internal Server Error` | An unexpected error occurs. |

### `POST /empik/coupons/redeem` — Redeem a coupon

```json
{
  "code": "SALE5",
  "userId": "customer-123"
}
```

Use the case-sensitive `code` supplied at creation. If the same code exists in multiple countries, the coupon for the resolved client country is redeemed. `userId` must be nonblank and at most 255 characters. Send exactly one IPv4 or IPv6 literal in `X-Forwarded-For` (or the configured header); repeated header values and comma-separated lists are not accepted. To test a successful redemption, use a public IP that IPinfo resolves to the coupon's country.

| Status | Response |
| --- | --- |
| `204 No Content` | Coupon redeemed. No response body. |
| `400 Bad Request` | The request body is missing, malformed, or fails validation. |
| `403 Forbidden` | `Client country unavailable` if the header is missing or invalid, or the IP does not resolve to a valid country. `Country not allowed` if the resolved country does not match the coupon. |
| `404 Not Found` | `Coupon not found` |
| `409 Conflict` | `Coupon already redeemed by this user` or `Coupon usage limit reached` |
| `503 Service Unavailable` | `IP country lookup is unavailable` |
| `500 Internal Server Error` | An unexpected error occurs. |

### Health endpoints

Neither endpoint takes a request body. Both return an empty response body for the statuses below.

| Endpoint | `200 OK` | `503 Service Unavailable` |
| --- | --- | --- |
| `GET /empik/health/liveness` | The app reports that it is live. | The app reports that it is not live. |
| `GET /empik/health/readiness` | The app is accepting traffic and PostgreSQL responds to `SELECT 1`. | The app is not accepting traffic or the PostgreSQL check fails or times out. |

### Coupon error body

Coupon errors use `application/problem+json`. For example:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Coupon already redeemed by this user",
  "instance": "/empik/coupons/redeem"
}
```