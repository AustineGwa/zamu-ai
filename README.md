# Zamu

Multi-tenant WhatsApp AI receptionist for Kenyan businesses, run by the Zamu team.
The source of truth for what to build is in `docs/` (Software Requirements Specification first).

Stack: Java 21, Spring Boot 4.1.1, Maven, Thymeleaf + Tailwind CSS, Spring Data JDBC / `JdbcClient`
with explicit SQL, MySQL 8.4 + Flyway, Spring Security with email one-time codes, Spring Session JDBC,
Testcontainers. No JPA, no Redis.

## Status

**Milestone 1, foundations** is in place:

| Area | What exists |
| --- | --- |
| `shared` | UUIDv7 ids, UTC/Nairobi time helpers, AES-256-GCM secret encryption, HMAC code hashing, TOTP, MySQL job queue and runner, SMTP email, log masking, request IDs, security config, error pages, layout |
| `tenants` | Admin tenant list and create; setup wizard steps *profile* and *tone* (other steps show which milestone builds them) |
| `users` | Email one-time-code login; authenticator-app second factor for Zamu team roles; owner staff management (`/app/users`); wizard *staff* step; first-admin bootstrap |
| `audit` | Append-only audit log writes (logins, tenant, user and sign-up changes) |
| `leads` | Public website: landing page at `/`, sign-up form at `/signup` (lead capture with honeypot and rate limit), email to the team, *Sign-ups* list in the admin console |

## Profiles

| Profile | When | Database | Email | Secrets |
| --- | --- | --- | --- | --- |
| `h2` (default) | Local development; any run with no profile set | In-memory H2, empty on every start, demo data added | Printed in the console | Built-in public dev keys |
| `mysql` | Staging and production (and local MySQL if you want it) | MySQL 8.4 | SMTP | Environment variables (`.env.example`) |

The app refuses to start with both, or with neither (for example a mistyped `SPRING_PROFILES_ACTIVE=prod`).
The automated integration tests always run on MySQL (`mysql,test`), as the specification requires.

## Run locally (h2, nothing to install)

Run `ai.zamu.ZamuApplication` from the IDE with no profile set, or:

```powershell
.\mvnw.cmd spring-boot:run
```

Open http://localhost:8080 for the public home page. Use **Sign in** with `owner@demo.local` or
`staff@demo.local` ("Demo Clinic" is created on startup); the login code is **printed in the console**.
**Sign up** submissions appear under *Sign-ups* in the admin console, and their notification email is
printed in the console too. `admin@zamu.local` opens the admin
console, after you set up an authenticator app.

When running from the IDE, the stylesheet comes from `src/main/resources/static/css/app.css`. After changing
Tailwind classes, rebuild it with `.\mvnw.cmd generate-resources` (or `npm run watch` in `src/main/frontend`).

Every migration in `db/migration` (MySQL, the source of truth) needs an H2 twin with the same file name in
`db/migration-h2`; `H2MigrationsMirrorTest` fails the build if one is missing.

## Run with MySQL (production setup, or locally with Docker)

```powershell
docker compose up -d   # MySQL 8.4 and Mailpit (inbox at http://localhost:8025); skip in production

function New-Key { $b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b) }
$env:SPRING_PROFILES_ACTIVE = "mysql"
$env:ZAMU_ENCRYPTION_KEY = New-Key       # keep it: it decrypts stored secrets
$env:ZAMU_CODE_HASH_KEY = New-Key
$env:ZAMU_BOOTSTRAP_ADMIN_EMAIL = "you@zamu.co.ke"
# Local Mailpit only (production uses its SMTP provider's values):
$env:ZAMU_SMTP_HOST = "localhost"; $env:ZAMU_SMTP_PORT = "1025"; $env:ZAMU_SMTP_AUTH = "false"; $env:ZAMU_SMTP_STARTTLS = "false"

.\mvnw.cmd spring-boot:run
```

In the IDE, set the same variables in the run configuration, and set the active profile to `mysql`.
macOS/Linux: use `openssl rand -base64 32` for the keys and `./mvnw`.

## Tests

```powershell
.\mvnw.cmd verify          # unit tests, then *IT integration tests on MySQL 8.4 in Testcontainers
.\mvnw.cmd test            # unit tests, @WebMvcTest slices and h2 smoke tests (no Docker needed)
```

Add `-Dskip.frontend=true` to skip the Tailwind build when only Java changed.

Tests are named after requirement IDs where one applies (for example `NFR_09_ownerOfTenantA_cannotDisableAUserOfTenantB`).

## Rules that keep the code honest

* **Tenant isolation (NFR-09).** Every repository method on a tenant-owned table takes a `TenantId`. Controllers
  get it by declaring a `TenantId` parameter (resolved from the session) or, on admin routes, from
  `AdminTenantScope`. A method that genuinely can't take one (login by email, admin listings) is annotated
  `@CrossTenant("why")`. `RepositoryTenantGuardTest` fails the build otherwise, and `TenantIsolationIT`
  holds the route-level checks: add one for every new route.
* **Packages.** One package per feature; classes package-private by default; features talk through public
  services and events, never each other's repositories.
* **Migrations.** Never edit one that has run. Add `V<n>__<feature>_<change>.sql`.
* **Jobs.** Slow work goes through `JobQueue.enqueue(...)`; handlers implement `JobHandler` and must be
  idempotent. Same `lockKey` means one at a time, in order (MSG-05).
* **Logs.** No personal data or secrets; use `Masking.phone(...)` / `Masking.email(...)` when you must.
