# Zamu

Multi-tenant WhatsApp AI receptionist for Kenyan businesses, run by the Zamu team. Voice calls are proposed
for after version 1 (`docs/voice-channel-proposal.md`).
The source of truth for what to build is in `docs/` (Software Requirements Specification first).

Stack: Java 21, Spring Boot 4.1.1, Maven, Thymeleaf + Tailwind CSS, Spring Data JDBC / `JdbcClient`
with explicit SQL, MySQL 8.4 + Flyway, Spring Security with email one-time codes, Spring Session JDBC,
Testcontainers. No JPA, no Redis.

## Status

Milestones 1 to 5 and 7 are in place: foundations, WhatsApp and inbox, **AI answers from an approved knowledge
base** (Spring AI, Claude by default), the **pilot go-live checklist**, **bookings with Google Calendar and
reminders**, and **reports and hardening** (weekly owner report, usage and margin, monitoring and alerts, review
queue, change requests, data export and deletion, retention, audit log search). M-Pesa (6), acceptance (8) and
voice on Asterisk with a Safaricom SIP trunk (9) follow.

| Area | What exists |
| --- | --- |
| `shared` | UUIDv7 ids, UTC/Nairobi time helpers, AES-256-GCM secret encryption, HMAC code hashing, TOTP, MySQL job queue and runner, SMTP email, log masking, request IDs, security config, error pages, layout |
| `tenants` | Admin tenant list and create; setup wizard steps *profile* and *tone* (other steps show which milestone builds them) |
| `users` | Email one-time-code login; authenticator-app second factor for Zamu team roles; owner staff management (`/app/users`); wizard *staff* step; first-admin bootstrap |
| `audit` | Append-only audit log writes (logins, tenant, user and sign-up changes); search for the admin console |
| `leads` | Public website: landing page at `/`, sign-up form at `/signup` (lead capture with honeypot and rate limit), email to the team, *Sign-ups* list in the admin console |
| `whatsapp` | Signed webhook (`/webhooks/whatsapp`), routing by phone number ID, de-duplication, jobs per customer, Cloud API sending with retries, delivery statuses, message templates and Meta review status, wizard *WhatsApp* step, local customer simulator (`/dev/whatsapp`) |
| `conversations` | Channel-neutral conversation engine (greeting, 24-hour window, staff-only mode, "person", "stop", non-text), live inbox and conversation pages with staff replies and templates, block/unblock |
| `handovers` | Handover requests, accept / hand back / close, after-hours message, 30-minute escalation email to owners, browser alert and sound |
| `bookings` | Opening and handover hours and closed days; services, staff and rooms, booking rules (wizard *Services* step); Google Calendar by OAuth (wizard *Calendar* step); free slots; booking, rescheduling and cancelling by WhatsApp list messages; deposits waiting for payment; reminders; calendar sync; staff *Today* and *Bookings* pages |
| `reports` | Daily usage counters (billable conversations, messages, AI cost, WhatsApp template messages); owner *Reports* page with CSV and the Monday email; admin *Usage* page with running cost, margin and CSV; running-cost alert |
| `ops` | Admin *Health* (job queue, dead jobs with *Retry*, webhook backlog, failed sends, number quality, disconnected calendars), *Review* queue, *Audit log*; operator alert emails; Micrometer gauges |
| `privacy` | Per-client *Privacy and data* step: find a customer by phone, download their data (JSON), delete them; retention settings; offboarding; nightly retention run |
| `tenants` | Also: owner *Settings* page with staff-only mode and holding reply; owner change requests (`/app/requests`, "suggest a change" on the knowledge base and report) and the admin *Requests* queue |
| `knowledge` | Knowledge base per tenant (draft, approved, archived; English and Swahili answers), chunking, optional embeddings, in-memory retrieval (keyword plus vector score), owner read-only view (`/app/knowledge`) |
| `ai` | Answer pipeline on Spring AI: emergency words and "always hand over" topics, retrieval, small model then large model, JSON output, guardrails (numbers only from the knowledge base, no leaked instructions, length), confidence threshold, token and cost recording; evaluation set with starter traps and test runs |
| `golive` | Go-live checklist per tenant, *Go live* and *Pause Zamu* |
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

## AI answers (Spring AI)

Zamu calls models through [Spring AI](https://docs.spring.io/spring-ai/reference/) 2.0, so the provider is a
setting, not code. Claude (Anthropic) is the default.

| Variable | Default | Meaning |
| --- | --- | --- |
| `ANTHROPIC_API_KEY` | (none) | Required with the mysql profile while the provider is `anthropic` |
| `ZAMU_AI_CHAT_PROVIDER` | `anthropic` (mysql), `none` (h2) | `anthropic`, `openai`, or `none` for the offline stand-in |
| `ZAMU_AI_MODEL_SMALL` | `claude-haiku-5-5` | First attempt for every answer (AI-09) |
| `ZAMU_AI_MODEL_LARGE` | `claude-sonnet-5-5` | One retry when the small model's output is invalid or fails a guardrail |
| `ZAMU_AI_EMBEDDING_PROVIDER` | `none` | `openai` adds vector search on top of keyword search (needs `OPENAI_API_KEY`) |
| `ZAMU_AI_EMBEDDING_MODEL` | `text-embedding-3-small` | Embedding model when enabled |

**Switching provider:** set `ZAMU_AI_CHAT_PROVIDER=openai`, `OPENAI_API_KEY`, and the two model names
(for example `gpt-5-mini` and `gpt-5`). Another Spring AI provider needs its starter added to `pom.xml` and its
`spring.ai.<provider>` properties; nothing in Zamu's code changes. A backup provider for AI-10 is described in
`application.yml` (`zamu.ai.backup-provider`). Prices per million tokens for the cost report (AI-11) go under
`zamu.ai.prices`; models without a price are recorded at zero cost.

Without a provider (h2 profile, tests) an **offline stand-in** answers with the best-matching approved entry
word for word, through the same guardrails, so the demo clinic answers "How much is a consultation?" locally.
The go-live checklist refuses to go live on the stand-in.

**Getting a tenant ready for the pilot** (admin console, setup wizard):

1. *Tone and safety*: confidence threshold, emergency number, topics that always go to a person.
2. *Knowledge*: enter the client's top questions and answers, then approve each one. Only approved entries are
   used; editing an approved entry re-indexes it, archiving removes it immediately.
3. *Tests*: add at least 40 questions (real ones with the facts the answer must include, plus the 10 starter
   traps), run them, and fix the knowledge base until a run reaches 95% with no trap answered.
4. *Go-live*: every required check green, then **Go live**. **Pause Zamu** stops replies at any time.

Model names and prices change: confirm the model IDs in Anthropic's (or the other provider's) current model list
and the Spring AI property names against the Spring AI version in `pom.xml` before go-live.

## Bookings and Google Calendar

Customers book by WhatsApp: "I'd like to book a check-up" brings a list of the nearest five free times; tapping
one confirms it (or, for services with a deposit, holds it for staff to collect payment until M-Pesa arrives in
Milestone 6). The confirmation has *Reschedule* and *Cancel* buttons, and customers can also type 1 or 2.
A reminder goes out 24 hours before (and optionally 2 hours before).

Free times come from opening hours, closed days, each service's duration and gap, the staff or rooms that
deliver it, Zamu's own bookings and, when connected, the Google Calendar's busy times. The calendar is checked
again just before a time is booked, so appointments staff add by hand are never double-booked.

**Google Calendar setup** (once per Zamu installation):

1. In Google Cloud, create an OAuth client of type *Web application* and enable the Google Calendar API.
2. Add the redirect URI `{ZAMU_BASE_URL}/admin/calendar/callback`.
3. Set `ZAMU_GOOGLE_CLIENT_ID` and `ZAMU_GOOGLE_CLIENT_SECRET`.
4. Per client: setup wizard, **Calendar** step, *Connect Google Calendar*, signed in as the account that holds
   the bookings calendar. Zamu asks for calendar events and free/busy only.

Without these, bookings still work from opening hours and Zamu's own bookings. Google requires app verification
for these scopes before many outside accounts can connect: start that early.

**Reminders outside the 24-hour window** need an approved template named `booking_reminder` per language, with
body parameters {{1}} business name and {{2}} day and time, for example: *Reminder: your appointment at {{1}} is
{{2}}. Reply 1 to confirm, 2 to reschedule or 3 to cancel.*

Try it locally: the demo clinic has two dentists, a consultation (no deposit) and a check-up (KES 500 deposit).
In `/dev/whatsapp` send "I want to book a consultation", then tap a time under *Zamu's latest options*.

## Reports, monitoring and privacy

**Owners** see *Reports* (`/app/reports`): conversations (and how many came after hours), the share handed to
staff and the median first staff reply, bookings by service, no-shows, the questions Zamu answered most, and the
questions it couldn't answer, each with a *Suggest answer* box that becomes a change request. *Download CSV*
exports the week. Owners get the same summary by email every Monday from 8:00am in the business's timezone,
once per week even with several instances.

**Zamu admins** see *Usage* (`/admin/usage`): per client and month, billable conversations against the plan limit
and overage, AI cost, WhatsApp template messages, running cost in KES and its share of the plan fee, with CSV for
invoicing. The rates are settings, because Meta's and the AI provider's prices change:

| Variable | Default | Meaning |
| --- | --- | --- |
| `ZAMU_BILLING_USD_TO_KES` | 129.00 | Exchange rate for running cost |
| `ZAMU_BILLING_WHATSAPP_MESSAGE_USD` | 0.0100 | Placeholder price per template message; set it from Meta's current rate card for Kenya |

When a client's running cost passes 40% of their plan fee, the Zamu team gets one email that month.

**Operators** have *Health* (`/admin/health`), *Review* (`/admin/review`: unsure answers, errors, payment issues,
sensitive chats without their text, failed sends) and *Requests*. Every minute Zamu checks the job queue (more
than 100 due, or the oldest waiting over 2 minutes), webhooks waiting over 5 minutes, and jobs that failed for
good, and emails the Zamu team (at most every 30 minutes per problem). The gauges `zamu.jobs.due` and
`zamu.jobs.dead` are registered with Micrometer for whichever metrics backend the host adds.

**Privacy** (setup wizard, *Privacy and data* step, Zamu team only): find a customer by phone number, download
everything Zamu holds about them as JSON, or delete them and their conversations, handovers and bookings
(typing the number again to confirm). Payment records and the audit log are kept. Conversations with no message
for 12 months (adjustable per client) are deleted nightly at 02:23. Offboarding (after *Pause Zamu*) stops Zamu
for good, and the client's data is deleted 30 days later (adjustable), keeping usage, audit and payment records.
Finished background jobs are deleted after 30 days.

**Audit log** (`/admin/audit`, admins): filter by client, action and dates; CSV export. Exports are audited.

Database backups are the host's job (NFR-15): daily encrypted MySQL backups kept 30 days, point-in-time recovery
where the host offers it, and a restore tested every month.

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
