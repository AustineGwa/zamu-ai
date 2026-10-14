# Zamu

Multi-tenant WhatsApp AI receptionist for Kenyan businesses, run by the Zamu team. Voice calls are proposed
for after version 1 (`docs/voice-channel-proposal.md`).
The source of truth for what to build is in `docs/` (Software Requirements Specification first).

Stack: Java 21, Spring Boot 4.1.1, Maven, Thymeleaf + Tailwind CSS, Spring Data JDBC / `JdbcClient`
with explicit SQL, MySQL 8.4 + Flyway, Spring Security with email one-time codes, Spring Session JDBC,
Testcontainers. No JPA, no Redis.

## Status

**Milestone 1, foundations** and **Milestone 2, WhatsApp and inbox** are in place. The AI step arrives in
Milestone 3: until then Zamu greets, handles "person", "stop" and photos, and hands every question to staff
with the "not sure" reply (AI-03).

| Area | What exists |
| --- | --- |
| `shared` | UUIDv7 ids, UTC/Nairobi time helpers, AES-256-GCM secret encryption, HMAC code hashing, TOTP, MySQL job queue and runner, SMTP email, log masking, request IDs, security config, error pages, layout |
| `tenants` | Admin tenant list and create; setup wizard steps *profile* and *tone* (other steps show which milestone builds them) |
| `users` | Email one-time-code login; authenticator-app second factor for Zamu team roles; owner staff management (`/app/users`); wizard *staff* step; first-admin bootstrap |
| `audit` | Append-only audit log writes (logins, tenant, user and sign-up changes) |
| `leads` | Public website: landing page at `/`, sign-up form at `/signup` (lead capture with honeypot and rate limit), email to the team, *Sign-ups* list in the admin console |
| `whatsapp` | Signed webhook (`/webhooks/whatsapp`), routing by phone number ID, de-duplication, jobs per customer, Cloud API sending with retries, delivery statuses, message templates and Meta review status, wizard *WhatsApp* step, local customer simulator (`/dev/whatsapp`) |
| `conversations` | Channel-neutral conversation engine (greeting, 24-hour window, staff-only mode, "person", "stop", non-text), live inbox and conversation pages with staff replies and templates, block/unblock |
| `handovers` | Handover requests, accept / hand back / close, after-hours message, 30-minute escalation email to owners, browser alert and sound |
| `bookings` | Opening and handover hours (wizard *Hours* step); bookings themselves arrive in Milestone 5 |
| `reports` | Daily usage counters (billable conversations, messages in and out) |
| `tenants` | Also: owner *Settings* page with staff-only mode and holding reply |
| `pricing` | Public pricing page (`/pricing`) with plan cards, a feature comparison and questions, all read from the database; plan cards on the home page; *Pricing* editor in the admin console; the chosen plan is carried to the sign-up form |

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
printed in the console too.

To try the inbox, sign in as the owner or staff, open http://localhost:8080/dev/whatsapp and write as a customer
(try "Habari", "person" or "stop"). Nothing is sent to Meta locally; Zamu's replies appear in the inbox, which
updates live. Click **Turn on alerts** in the header for handover notifications. `admin@zamu.local` opens the admin
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
$env:ZAMU_WHATSAPP_APP_SECRET = "..."      # Meta app secret: without it every webhook is rejected
$env:ZAMU_WHATSAPP_VERIFY_TOKEN = "..."    # any long random string; enter the same in Meta
# Local Mailpit only (production uses its SMTP provider's values):
$env:ZAMU_SMTP_HOST = "localhost"; $env:ZAMU_SMTP_PORT = "1025"; $env:ZAMU_SMTP_AUTH = "false"; $env:ZAMU_SMTP_STARTTLS = "false"

.\mvnw.cmd spring-boot:run
```

In the IDE, set the same variables in the run configuration, and set the active profile to `mysql`.
macOS/Linux: use `openssl rand -base64 32` for the keys and `./mvnw`.

## Connecting a real WhatsApp number

1. In Meta Business Manager, create the app and a system user with a permanent token for the business's
   WhatsApp Business Account.
2. In Zamu's admin console, open the tenant's setup wizard, **WhatsApp** step, and enter the WABA ID, phone
   number ID, display number and token (stored encrypted).
3. In the Meta app's WhatsApp configuration, set the callback URL shown on that page and the verify token, and
   subscribe to `messages` and `message_template_status_update`. Zamu needs to be reachable over HTTPS.
4. Add templates under **Manage templates** and submit them for Meta's review.

The Graph API version is pinned in `application.yml` (`zamu.whatsapp.graph-version`); confirm it and the request
shapes in `RestClientWhatsAppApi` against Meta's current documentation before go-live.

## Deploy with Docker (Render or any container host)

The `Dockerfile` builds everything from source, including the Tailwind stylesheet, so the generated `app.css` does
not need to be committed. The image listens on `$PORT` (Render sets it; 8080 otherwise) and its health check is
`/actuator/health`.

```powershell
docker build -t zamu .
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=h2 zamu        # throwaway demo with in-memory data
```

### Render

1. Push the repository to GitHub (including `Dockerfile`, `.dockerignore` and `render.yaml`).
2. In Render: **New > Blueprint**, choose the repository. `render.yaml` creates a Docker web service in Frankfurt.
   Or create a **Web Service** by hand, choose **Docker**, and set the health check path to `/actuator/health`.
3. The first deploy uses `SPRING_PROFILES_ACTIVE=h2`: an in-memory database with the demo clinic. It is wiped on
   every deploy, restart and (on the free plan) after 15 minutes without visitors. Sign in as `owner@demo.local`;
   the login code is printed in the Render **Logs**. Good for a test, never for real customers.
4. For real use, switch to `SPRING_PROFILES_ACTIVE=mysql` and set:
   * `ZAMU_DB_URL`, `ZAMU_DB_USERNAME`, `ZAMU_DB_PASSWORD`: Render has no managed MySQL, so use an external MySQL
     8.4 server (any provider), for example
     `jdbc:mysql://HOST:PORT/zamu?sslMode=REQUIRED&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8`.
   * `ZAMU_ENCRYPTION_KEY`, `ZAMU_CODE_HASH_KEY`: the Blueprint generates them. Never change the encryption key
     after data is stored.
   * `ZAMU_SMTP_*` and `ZAMU_MAIL_FROM`: login codes are sent by email. **Render's free plan blocks outbound
     ports 25, 465 and 587**, so either use a paid instance or an email provider that accepts SMTP on port 2525
     (`ZAMU_SMTP_PORT=2525`).
   * `ZAMU_BOOTSTRAP_ADMIN_EMAIL`, and the WhatsApp variables when you connect Meta.
   `ZAMU_BASE_URL` defaults to Render's own address (`RENDER_EXTERNAL_URL`); set it if you add a custom domain.
5. The free plan (512 MB) runs Zamu; the JVM is limited to 75% of the container's memory. Free instances sleep
   when idle, which also pauses background jobs and WhatsApp replies, so use a paid instance for a pilot.

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
