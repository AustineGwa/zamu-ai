# **Zamu Software Requirements Specification**

Oct 11, 2026 · @Austine Gwa

## **Purpose and scope**

This document specifies version 1 of Zamu, a multi-tenant WhatsApp AI receptionist that OTBLABS runs for Kenyan businesses. It is written for the developer or team who will build it, and is the reference for what "done" means. The companion Zamu Product and Operations Manual explains how the service is sold and run.

**Version 1 in one sentence:** a business's customers chat on WhatsApp; Zamu answers from that business's approved information, books appointments in Google Calendar, collects deposits by M-Pesa STK Push, sends reminders, and hands chats to staff in a web dashboard.

### **In scope for version 1**

* WhatsApp text messaging through Meta's WhatsApp Cloud API, one number per client (tenant)  
* AI answers grounded in a per-tenant knowledge base, in English and Swahili  
* Appointment booking, rescheduling, cancellation and reminders against Google Calendar  
* M-Pesa STK Push payments to the tenant's own till or paybill, with callbacks and status queries  
* Handover to human staff, a live inbox and reports in a web dashboard  
* An OTBLABS admin console for onboarding tenants, editing knowledge bases and monitoring  
* Usage metering per tenant (conversations), for billing

### **Out of scope for version 1**

* Voice calls, Instagram, Facebook Messenger, SMS or USSD  
* Zoho CRM, Odoo or clinic-system sync (planned for the Business plan in version 1.1)  
* Outbound marketing campaigns  
* Automated subscription billing; OTBLABS invoices manually from the usage report  
* Native mobile apps; the dashboard must work well in a phone browser instead

### **How to read requirements**

Each requirement has an ID such as **MSG-03** and a priority. **Must** means version 1 can't launch without it. **Should** means it is expected in version 1 but can follow within a month of launch. **Could** means it's welcome if time allows. Each Must and Should requirement is written so it can be tested.

## **Users, roles and permissions**

There are five roles. Customers never log in; they only use WhatsApp. Every dashboard user belongs to exactly one tenant, except OTBLABS staff, who work across tenants.

| Role | Who | Logs in to |
| :---- | :---- | :---- |
| Customer | A tenant's patient, guest or parent | WhatsApp only |
| Tenant staff | Receptionists and front-desk team | Client dashboard |
| Tenant owner | Business owner or manager | Client dashboard |
| OTBLABS operator | Onboarding and support staff | Admin console |
| OTBLABS admin | Director or lead engineer | Admin console |

### **Permissions**

| Action | Staff | Owner | Operator | Admin |
| :---- | :---- | :---- | :---- | :---- |
| View and reply to conversations | Yes | Yes | Yes | Yes |
| Take over or release a chat | Yes | Yes | Yes | Yes |
| View bookings and payments | Yes | Yes | Yes | Yes |
| Create, change or cancel a booking manually | Yes | Yes | Yes | Yes |
| View reports | No | Yes | Yes | Yes |
| Manage staff users | No | Yes | Yes | Yes |
| Edit business hours, services and handover settings | No | Request only | Yes | Yes |
| Edit the knowledge base | No | Suggest only | Yes | Yes |
| Set staff-only mode | No | Yes | Yes | Yes |
| Connect WhatsApp, M-Pesa and calendar credentials | No | No | Yes | Yes |
| Create or suspend a tenant | No | No | No | Yes |
| Export or delete a tenant's data | No | Request only | No | Yes |
| View the audit log | No | Own tenant | Yes | Yes |

"Request only" and "Suggest only" mean the owner submits a change from the dashboard, and an operator applies it in the admin console. Version 1 deliberately keeps knowledge base editing with OTBLABS, so that answer quality stays under review.

## **Architecture**

Zamu is one Spring Boot application backed by one MySQL database. The same application serves the Thymeleaf dashboard, receives webhooks from Meta and Safaricom, and runs background jobs from a MySQL jobs table. Webhooks are stored and acknowledged immediately, and the slow work (AI, calendar, M-Pesa, sending messages) runs as jobs, so a slow reply never causes a webhook timeout. There is no Redis in version 1; every place that would use it sits behind an interface, so Redis can be added later without rewriting features.

![Zamu architecture: one Spring Boot application and one MySQL database][image1]

Zamu architecture: one Spring Boot application and one MySQL database

Web requests only receive, validate and store; job runners do everything slow. Version 1 runs both in one process. To scale, run more instances of the same application: SKIP LOCKED lets several job runners share the jobs table safely.

### **Services**

| Part | Responsibility |
| :---- | :---- |
| Web layer | Thymeleaf client dashboard and admin console, public webhook endpoints for WhatsApp, M-Pesa and Google Calendar, a few JSON and server-sent event endpoints |
| Job runners | @Scheduled pollers that claim jobs from the MySQL jobs table and run the conversation engine, AI calls, calendar and Daraja calls, outbound WhatsApp messages, reminders and reports |
| MySQL | All tenant data, the jobs table, sessions, and knowledge base embeddings |
| In-process cache | Caffeine caches for Daraja and Google tokens, tenant settings and embeddings, with short expiry |
| File storage | Uploaded source documents (price lists, PDFs) and data exports, on object storage or a mounted volume |

### **Recommended stack**

This is the required stack. A developer who wants to change any part of it must agree it with OTBLABS first.

| Layer | Choice | Notes |
| :---- | :---- | :---- |
| Language | Java 21 or later (LTS) |  |
| Framework | Spring Boot, latest stable 3.x or later | One deployable JAR |
| Build | Maven | Tailwind CLI runs as part of the build |
| Views | Thymeleaf, with shared layouts and fragments | Server-rendered; no React, Angular or Vue |
| Styling | Tailwind CSS | Compiled to static/css; no inline styles |
| Partial updates | htmx, to confirm (see Open questions) | For the live inbox and small page updates |
| Live updates | Server-sent events (SseEmitter) | New messages and handovers in the inbox |
| Data access | Spring Data JDBC repositories plus explicit SQL with RowMappers (NamedParameterJdbcTemplate or JdbcClient) | No JPA, no Hibernate |
| Database | MySQL 8.4 LTS or later, InnoDB, utf8mb4 |  |
| Migrations | Flyway | One migration folder; feature-prefixed file names |
| Jobs | MySQL jobs table with SELECT ... FOR UPDATE SKIP LOCKED and @Scheduled pollers | Replaces Redis queues |
| Cache | Caffeine (in-process) | Replaces Redis caches |
| Security | Spring Security; sessions in MySQL with Spring Session JDBC | Email one-time-code login |
| HTTP clients | Spring RestClient with timeouts and retries | WhatsApp, Daraja, Google, AI provider |
| AI | Provider-agnostic interface in the ai package (Spring AI optional) | Swap providers by configuration |
| Testing | JUnit 5, AssertJ, Spring Boot test slices, Testcontainers with MySQL |  |
| Hosting | A Linux VM or managed platform plus managed MySQL with daily backups | Public HTTPS required for webhooks |

### **Multi-tenancy rule**

Every tenant-owned table has a tenant\_id column, and every SQL query in every repository filters by it. MySQL has no row-level security, so isolation is enforced in code: repositories take the tenant ID as a required argument, and it always comes from the authenticated session (or, in jobs, from the job's tenant), never from request parameters. A test suite must prove that one tenant's user can't read or change another tenant's data (see Testing).

### **Package structure**

Code is organised by feature (use case), not by layer. Each feature package contains its own controller, service, repository, row mappers, models, DTOs, templates and migrations, so it can later become a separate Maven module.

com.otblabs.zamu  
├── tenants/        TenantController, TenantService, TenantRepository, Tenant, ...  
├── users/          UserController, UserService, UserRepository, User, LoginCodeService, ...  
├── whatsapp/       WhatsAppWebhookController, WhatsAppClient, WhatsAppAccount, TemplateService, ...  
├── conversations/  ConversationEngine, ConversationService, ConversationRepository, Message, InboxController, ...  
├── knowledge/      KnowledgeController, KnowledgeService, KbEntryRepository, KbChunkRepository, Retriever, ...  
├── ai/             AiClient (interface), provider implementations, PromptBuilder, Guardrails, EvaluationService  
├── bookings/       BookingController, BookingService, BookingRepository, SlotCalculator, GoogleCalendarClient, ...  
├── payments/       MpesaCallbackController, PaymentService, PaymentRepository, DarajaClient, ...  
├── handovers/      HandoverService, HandoverRepository, ...  
├── reports/        ReportController, ReportService, UsageRepository, ...  
├── audit/          AuditService, AuditRepository  
└── shared/         config, security, web (layouts, errors), jobs (JobQueue, JobRunner), crypto, time  
Rules:

1. Classes are package-private by default; only a feature's service methods, DTOs and events are public.  
2. A feature never uses another feature's repository or tables directly. It calls that feature's public service, or listens to its events (ApplicationEventPublisher with @TransactionalEventListener).  
3. No circular dependencies between features. shared depends on no feature.  
4. Templates mirror packages: templates/conversations/inbox.html, templates/bookings/today.html; shared layouts and UI fragments live in templates/fragments/.  
5. Migrations are named by feature: V12\_\_bookings\_add\_no\_show.sql.

### **Background jobs without Redis**

* The jobs table holds every unit of slow work: process\_inbound\_message, send\_message, request\_payment, payment\_status\_check, send\_reminder, daily\_summary, weekly\_report, reembed\_kb\_entry.  
* Job runners poll every second, claim a batch with SELECT ... FOR UPDATE SKIP LOCKED, set status \= running and a lease expiry, and commit before doing the work.  
* Failed jobs retry with exponential back-off (5 attempts), then move to dead, which is visible in the admin console. Jobs whose lease expires are picked up again.  
* Reminders and reports are jobs with a future run\_at.  
* **One message at a time per conversation:** jobs carry a lock\_key (the conversation ID); a runner never claims a job whose lock\_key already has a running job.  
* JobQueue is an interface, so a Redis-backed implementation can replace the MySQL one later without changing features.

## **Data model**

The core is: a tenant has customers; each customer has conversations made of messages; conversations can produce bookings, payments and handovers. Every table is MySQL InnoDB with utf8mb4. All tenant-owned tables carry tenant\_id; the users table allows it to be empty for OTBLABS staff.

**Column conventions:** id is a UUID stored as BINARY(16) (time-ordered UUIDv7, for index locality); created\_at and updated\_at are DATETIME(3) in UTC, shown in Africa/Nairobi; money is DECIMAL(12,2) in KES; enums are VARCHAR(32) checked in code; JSON payloads use the JSON type; encrypted secrets are VARBINARY. Every foreign key and every column used for filtering or sorting is indexed, starting with (tenant\_id, ...). Each table belongs to one feature package, which is the only package that writes to it.

| Entity (owning package) | Key fields | Notes |
| :---- | :---- | :---- |
| tenants (tenants) | name, display\_name, plan (starter, growth, business), status (onboarding, live, staff\_only, paused, offboarded), timezone, default\_language, tone, conversation\_limit, confidence\_threshold | One row per client business |
| users (users) | email, name, role (staff, owner, operator, admin), tenant\_id (null for OTBLABS), last\_login\_at, disabled\_at | Dashboard and console logins |
| login\_codes (users) | email, code\_hash, expires\_at, attempts | One-time login codes |
| SPRING\_SESSION tables (shared) | Created by Spring Session JDBC | Sessions in MySQL |
| whatsapp\_accounts (whatsapp) | waba\_id, phone\_number\_id, display\_phone, access\_token (encrypted), webhook\_verified, quality\_rating | One per tenant in version 1 |
| wa\_templates (whatsapp) | name, category (utility, marketing), language, body, meta\_status (pending, approved, rejected) | Per tenant |
| webhook\_events (whatsapp, payments) | source (meta, daraja, google), raw\_payload (JSON), received\_at, processed\_at | Raw inbound payloads, kept 30 days |
| mpesa\_accounts (payments) | shortcode, shortcode\_type (till, paybill), consumer\_key, consumer\_secret, passkey (all encrypted), environment (sandbox, production), callback\_secret | Money goes to the tenant's shortcode |
| calendar\_connections (bookings) | provider (google), calendar\_id, oauth\_refresh\_token (encrypted), status |  |
| business\_hours (bookings) | day\_of\_week, opens\_at, closes\_at, handover\_opens\_at, handover\_closes\_at | Plus a holidays table: date, closed, note |
| services (bookings) | name, name\_sw, description, duration\_min, price\_min, price\_max, deposit\_amount, buffer\_min, active | Bookable services |
| resources (bookings) | name, role, calendar\_id (optional), active | Staff or rooms; linked to services by a service\_resources table |
| kb\_entries (knowledge) | title, question, answer\_en, answer\_sw, status (draft, approved, archived), approved\_by, approved\_at, source\_ref | What the assistant may say |
| kb\_chunks (knowledge) | kb\_entry\_id, language, content, embedding (BLOB, float32 array), embedding\_model, FULLTEXT index on content | Generated from approved entries only |
| customers (conversations) | wa\_id (phone in international format), name, language, opted\_in\_marketing, blocked, deleted\_at | Unique on (tenant\_id, wa\_id) |
| conversations (conversations) | customer\_id, status (bot, handed\_over, closed), assigned\_user\_id, opened\_at, window\_expires\_at, billable\_session\_start |  |
| messages (conversations) | conversation\_id, direction (in, out), sender (customer, bot, staff, system), wa\_message\_id, type (text, interactive, template, media), body, status (queued, sent, delivered, read, failed), ai\_confidence, kb\_entry\_ids (JSON) | Unique on wa\_message\_id, for de-duplication |
| handovers (handovers) | conversation\_id, reason (requested, sensitive, low\_confidence, payment\_issue, error), requested\_at, accepted\_by, accepted\_at, resolved\_at |  |
| bookings (bookings) | customer\_id, service\_id, resource\_id, starts\_at, ends\_at, status (held, pending\_payment, confirmed, cancelled, completed, no\_show), hold\_expires\_at, calendar\_event\_id, source (bot, staff), notes |  |
| payments (payments) | booking\_id, customer\_id, amount\_kes, phone, merchant\_request\_id, checkout\_request\_id, status (initiated, paid, failed, cancelled, timeout, unknown), mpesa\_receipt, result\_code, result\_desc, raw\_callback (JSON) | Unique on checkout\_request\_id |
| jobs (shared.jobs) | type, tenant\_id, payload (JSON), run\_at, status (queued, running, done, failed, dead), attempts, max\_attempts, lock\_key, lease\_until, last\_error | Index on (status, run\_at); replaces a Redis queue |
| usage\_daily (reports) | date, conversations, messages\_in, messages\_out, ai\_tokens\_in, ai\_tokens\_out, ai\_cost\_usd, wa\_billable\_messages | Drives billing and margin reports |
| change\_requests (tenants) | requested\_by, type (kb, hours, service, other), details, status, handled\_by | Owner requests from the dashboard |
| audit\_logs (audit) | actor\_user\_id, action, entity, entity\_id, before (JSON), after (JSON), ip | Append-only |

**Relationships:** tenant 1..n customers; customer 1..n conversations; conversation 1..n messages; conversation 0..n handovers; customer 1..n bookings; booking 0..n payments; service n..n resources; kb\_entry 1..n kb\_chunks.

### **Knowledge base search on MySQL**

MySQL has no pgvector, and the knowledge base is small (typically 100 to 500 chunks per tenant), so version 1 searches it in the application:

1. When an entry is approved, a reembed\_kb\_entry job stores each chunk's embedding as a float32 BLOB.  
2. On each question, the knowledge package loads that tenant's approved chunks from a Caffeine cache (refilled from MySQL when an entry changes), computes cosine similarity in Java against the question's embedding, and keeps the top 6\.  
3. A MySQL FULLTEXT keyword search on the same chunks is merged in, so exact names, prices and service terms are always found.  
4. All of this sits behind a Retriever interface. If a tenant grows past about 5,000 chunks, it can move to MySQL's native vector support (if the chosen MySQL version and host provide vector search) or a dedicated vector store without changing other packages.

## **Messaging and the conversation engine (MSG)**

The conversation engine receives each inbound WhatsApp message, works out what the customer needs, and calls the right capability: answer, book, pay or hand over. It is a deterministic state machine that uses the AI model to understand messages and write replies, not an AI that decides everything on its own.

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| MSG-01 | Receive inbound messages from the WhatsApp webhook, verify the X-Hub-Signature-256 signature, store the raw payload, return HTTP 200 within 2 seconds, and queue processing | Must |
| MSG-02 | Ignore duplicate deliveries: a wa\_message\_id already stored is acknowledged and not processed again | Must |
| MSG-03 | Route each message to its tenant by phone\_number\_id; reject and log messages for unknown numbers | Must |
| MSG-04 | Create the customer on first contact, and open a new conversation when there is none open or the last message was over 24 hours ago | Must |
| MSG-05 | Process one message at a time per conversation (the job lock\_key; see Background jobs without Redis), in the order received, so replies never cross | Must |
| MSG-06 | Mark inbound messages as read and show a typing indicator while a reply is prepared, where the API supports it | Should |
| MSG-07 | Classify each message into one intent: question, book, reschedule, cancel, pay, talk\_to\_person, greeting, emergency, other. Detect the language (English, Swahili, mixed) | Must |
| MSG-08 | If the conversation is handed over (status handed\_over), store the message and notify staff, but never reply as the bot | Must |
| MSG-09 | If the tenant is in staff\_only or paused status, send the configured holding reply once per conversation and route everything to the inbox | Must |
| MSG-10 | Send the greeting (automated-assistant disclosure plus how to reach a person) at the start of every new conversation with a new customer | Must |
| MSG-11 | Keep conversation context: pass the last 12 messages and any open booking or payment to the AI step | Must |
| MSG-12 | Use WhatsApp interactive messages (reply buttons, lists) for slot choice and confirmations, with a plain-text fallback | Should |
| MSG-13 | Handle non-text messages (image, audio, sticker, location, document): acknowledge politely and offer a person; store the media reference | Must |
| MSG-14 | Only send free-form messages inside the 24-hour customer service window; outside it, use an approved template | Must |
| MSG-15 | Track every outbound message's status (sent, delivered, read, failed) from status webhooks, and alert operators on repeated failures | Must |
| MSG-16 | Rate-limit abuse: after 2 abusive or spam messages in a conversation, stop replying, flag the conversation, and let staff block the customer | Should |
| MSG-17 | Count a billable conversation once per customer per 24-hour period starting with their first message, and write it to usage\_daily | Must |
| MSG-18 | Customers can type "person", "mtu", "agent" or similar at any time to reach staff | Must |
| MSG-19 | Customers can type "stop" to opt out of reminders and any future marketing | Must |
| MSG-20 | End-to-end reply time from webhook receipt to reply sent: under 8 seconds at the 95th percentile for text answers | Must |

## **AI answering and guardrails (AI)**

The AI may only say what the tenant's approved knowledge base supports. When it can't ground an answer, it says so and offers a person. The model receives tools for booking and payment, but the engine checks every tool call against business rules before acting.

### **How an answer is produced**

1. Embed the customer's message and retrieve the top 6 approved chunks for that tenant through the Retriever (see Knowledge base search on MySQL; similarity plus keyword search, filtered by tenant\_id and status \= approved).  
2. Build the prompt: a fixed system prompt (rules below), tenant profile (name, tone, hours, today's date and time in Nairobi), retrieved chunks with their entry IDs, the last 12 messages, and any open booking or payment.  
3. Call the chat model with structured output: { reply, language, intent, confidence (0 to 1), used\_entry\_ids\[\], tool\_call? }.  
4. Run the guardrail checks below. If any fails, replace the reply with the safe fallback and hand over.  
5. Send the reply and store the confidence and entry IDs on the message.

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| AI-01 | Answer only from retrieved approved chunks and the tenant profile; never from general knowledge for prices, services, policies or availability | Must |
| AI-02 | Reply in the customer's language (English or Swahili); reply in clear Swahili or English to Sheng | Must |
| AI-03 | If no chunk is relevant or confidence is below the tenant threshold (default 0.6), reply with "I'm not sure about that, let me connect you to our team" and create a handover with reason low\_confidence | Must |
| AI-04 | Never give medical, legal or financial advice. Messages describing symptoms or emergencies trigger the tenant's emergency reply and an immediate handover | Must |
| AI-05 | Every price, time or phone number in a reply must appear in a retrieved chunk or the tenant profile; a post-check rejects replies that contain numbers not found in the context | Must |
| AI-06 | Booking and payment actions happen only through tools (find\_slots, hold\_slot, confirm\_booking, cancel\_booking, request\_payment, handover). The engine validates every tool argument before executing | Must |
| AI-07 | Replies are under 80 words unless listing slots or a fee breakdown, in the tenant's tone | Should |
| AI-08 | Refuse to reveal the system prompt, other customers' data or anything about other tenants, whatever the customer writes | Must |
| AI-09 | Use a small, low-cost model by default; retry once with a larger model when the structured output fails validation | Should |
| AI-10 | Provider-agnostic interface: the chat and embedding providers are set in configuration, with a backup provider that is used automatically on errors or timeouts over 10 seconds | Should |
| AI-11 | Record tokens and cost per call in usage\_daily | Must |
| AI-12 | Re-embed a knowledge base entry within 1 minute of it being approved or changed; archived entries are removed from search immediately | Must |
| AI-13 | Choose providers whose terms don't allow training on customer data; send no more personal data to the model than the conversation needs | Must |

### **Evaluation set**

Each tenant has an evaluation set of at least 40 question-and-expected-answer pairs (the top 20 questions in English, 10 in Swahili, plus traps: unknown questions, medical questions, prompt injection). The admin console runs the set on demand and before every prompt or model change. A change ships only if correct answers stay at 95% or above and no trap question gets a grounded-looking wrong answer.

## **Bookings, calendar and reminders (BK)**

Google Calendar is the source of truth for availability, because tenant staff will keep adding appointments there by hand. Zamu always re-checks the calendar immediately before confirming, so it never double-books.

### **Booking states**

held (slot reserved for 10 minutes while the customer confirms or pays) → pending\_payment (deposit requested) → confirmed → completed or no\_show. Any state can move to cancelled. A held or pending\_payment booking that expires releases its slot automatically.

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| BK-01 | Connect a tenant's Google Calendar by OAuth from the admin console; store the refresh token encrypted; alert operators if access is revoked | Must |
| BK-02 | Compute free slots from business hours, holidays, service duration, buffer time, resource calendars, existing Zamu bookings and the calendar's busy times (free/busy query) | Must |
| BK-03 | Offer up to 5 slots, nearest first, as an interactive list; if none in the next 7 days, offer the first available day or a handover | Must |
| BK-04 | Respect the tenant's minimum notice (default 2 hours) and maximum advance booking (default 30 days) | Must |
| BK-05 | On choice, create a held booking and a tentative calendar event; re-check free/busy first, and offer other slots if it's now taken | Must |
| BK-06 | If the service has no deposit, confirm straight away; if it has one, start the payment flow (PAY) and confirm only when payment succeeds | Must |
| BK-07 | On confirmation, update the calendar event to confirmed with the customer's name, phone, service and a link to the conversation; send the confirmation message | Must |
| BK-08 | Customers can reschedule or cancel by chat up to the tenant's cut-off (default 4 hours before); after the cut-off, Zamu hands over | Must |
| BK-09 | Staff can create, move and cancel bookings in the dashboard; Zamu notifies the customer of staff changes | Should |
| BK-10 | Detect events deleted or moved directly in Google Calendar (push notifications or a 5-minute sync) and update the booking | Should |
| BK-11 | Send a reminder template 24 hours before (and optionally 2 hours before) with buttons: confirm, reschedule, cancel | Must |
| BK-12 | Don't send reminders to customers who opted out, or for bookings made less than 3 hours before the appointment | Must |
| BK-13 | Staff can mark bookings completed or no-show; no-shows appear in reports | Should |
| BK-14 | Bookings collect only the customer's name, phone and the service; any notes are optional and never require health details | Must |

## **M-Pesa payments (PAY)**

Zamu requests payment with M-Pesa Express (STK Push) to the tenant's own till or paybill, using the tenant's Daraja credentials. OTBLABS never receives or holds the money. A payment counts as paid only when Safaricom's callback, or a status query, says so.

### **Payment flow**

1. The booking is held. Zamu tells the customer the deposit amount and asks which number will pay (default: their WhatsApp number, if it is a Kenyan mobile number).  
2. The worker creates a payments row (initiated) and calls STK Push with the amount, phone, a callback URL unique to the tenant, and AccountReference \= the booking's short code.  
3. The customer enters their PIN on their phone.  
4. Safaricom posts the result to the callback URL. The web app stores it and queues processing.  
5. On result code 0: payment paid, store the M-Pesa receipt number, booking confirmed, send the receipt message.  
6. On failure or cancellation: payment failed or cancelled, tell the customer in plain words, and offer to resend or hand over.  
7. With no callback after 60 seconds: run an STK status query, then again at 2 and 5 minutes. Still unknown: mark unknown, hand over, and flag for reconciliation.

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| PAY-01 | Store each tenant's consumer key, consumer secret, passkey, shortcode and type (till or paybill) encrypted; support sandbox and production per tenant | Must |
| PAY-02 | Get and cache Daraja OAuth tokens per tenant, refreshing before expiry | Must |
| PAY-03 | Build the STK Push request correctly for paybill (CustomerPayBillOnline) and till (CustomerBuyGoodsOnline) shortcodes, including the timestamp and the Base64 password from shortcode, passkey and timestamp | Must |
| PAY-04 | Normalise phone numbers to 2547XXXXXXXX or 2541XXXXXXXX format; reject others with a clear message | Must |
| PAY-05 | The callback endpoint accepts only the expected payload shape, matches it by CheckoutRequestID, is idempotent, and responds with success to Safaricom immediately | Must |
| PAY-06 | Protect the callback: a secret token in the per-tenant callback path, plus an allow-list of Safaricom IP ranges if Safaricom publishes them | Must |
| PAY-07 | Map common result codes to customer-friendly messages: 0 paid, 1032 cancelled by customer, 1037 phone unreachable, 1 insufficient balance, 2001 wrong PIN; any other code is a generic failure | Must |
| PAY-08 | Never confirm a booking or send a receipt from a customer's own claim ("I've paid"); only from a callback or status query | Must |
| PAY-09 | Allow at most 3 STK attempts per booking, and at most 1 open request per customer at a time | Must |
| PAY-10 | Dashboard shows every payment with status, receipt number, amount and booking; exportable to CSV for reconciliation | Must |
| PAY-11 | Daily reconciliation report per tenant: paid, failed and unknown payments; unknown ones are highlighted for staff to check on the M-Pesa business portal | Should |
| PAY-12 | Store the raw callback JSON for 7 years with the payment record | Must |

The endpoint paths, field names and result codes above follow Safaricom's Daraja M-Pesa Express API as commonly documented. The developer must confirm them against the current Daraja documentation, especially how till numbers (Buy Goods) set the business shortcode and the receiving party.

## **Handover, notifications and reports (HO, NOT, REP)**

A handover moves a conversation from the bot to people. Staff reply from the dashboard and the message goes out from the tenant's own WhatsApp number, so the customer sees one continuous chat.

### **Handover**

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| HO-01 | Create a handover when the customer asks for a person, on emergency or sensitive intent, low confidence (AI-03), a payment in unknown state, a repeated failure, or a tenant "always hand over" topic | Must |
| HO-02 | On handover, set the conversation to handed\_over, send the customer the handover message (or the after-hours version outside handover hours), and stop bot replies | Must |
| HO-03 | Notify all on-duty staff in the dashboard (sound and browser notification) and send the tenant's handover WhatsApp alert number a short summary | Must |
| HO-04 | Show staff an AI summary of the conversation so far (2 to 3 lines) at the top of the chat | Should |
| HO-05 | A staff member accepts the handover (assigning it to themselves); others see who has it | Must |
| HO-06 | Staff can hand the chat back to Zamu, or close it. A handed-over chat with no staff reply for 30 minutes during handover hours escalates to the owner | Must |
| HO-07 | Staff replies inside 24 hours are free-form; after that, the dashboard only offers approved templates and explains why | Must |

### **Notifications to tenants**

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| NOT-01 | Daily summary at 7:00pm Nairobi time to the owner's WhatsApp: conversations, bookings, payments collected, open handovers | Should |
| NOT-02 | Instant alert to the owner on an emergency-intent message | Must |
| NOT-03 | Email login codes and weekly report emails | Must |

### **Reports**

| ID | Requirement | Priority |
| :---- | :---- | :---- |
| REP-01 | Weekly report (Monday 8:00am) by email and in the dashboard: conversations, after-hours conversations, bookings by service, after-hours bookings, payments collected (KES), handover rate, median first-reply time, no-shows | Must |
| REP-02 | Top 10 questions and the questions Zamu couldn't answer, with a one-click "suggest KB entry" for the owner | Should |
| REP-03 | Monthly usage report per tenant for OTBLABS billing: billable conversations against plan limit, overage, AI cost, WhatsApp billable messages | Must |
| REP-04 | Margin report across tenants in the admin console: running cost as a share of plan fee, flagged above 40% | Should |
| REP-05 | All report data exportable to CSV | Should |

## **Dashboard and admin console (UI)**

Two interfaces in the same web app. Tenant users see only their own business; OTBLABS staff see the admin console and can open any tenant's dashboard (logged in the audit log). Both must work on a phone browser at 360px wide, because many receptionists will use their phones.

### **Client dashboard screens**

| Screen | What it shows and does | Roles |
| :---- | :---- | :---- |
| Login | Email address, then a 6-digit code by email, valid 10 minutes | All |
| Inbox | Conversation list with filters (needs attention, handed over, bot, closed) and search by name or phone; live updates; unread counts | Staff, owner |
| Conversation | Full chat history with sender labels (customer, Zamu, staff name); reply box; template picker outside the 24-hour window; accept, hand back and close buttons; customer panel with bookings and payments; AI summary | Staff, owner |
| Today | Today's bookings in time order, with status, deposit status and quick actions (call, message, mark completed or no-show) | Staff, owner |
| Bookings | Calendar and list views, create, move and cancel | Staff, owner |
| Payments | Payment list with status, receipt number and amount; CSV export | Staff, owner |
| Reports | Weekly and monthly reports, charts, unanswered questions, CSV export | Owner |
| Knowledge base | Read-only view of approved entries, with "suggest a change" | Owner |
| Settings | Business hours, holidays, handover hours and alert number (as change requests), staff-only mode switch, staff user management | Owner |

### **OTBLABS admin console screens**

| Screen | What it shows and does | Roles |
| :---- | :---- | :---- |
| Tenants | All tenants with plan, status, usage this month, open handovers, health (green, amber, red) | Operator, admin |
| Tenant setup wizard | Steps: business profile; WhatsApp connection (embedded signup or manual IDs and token); M-Pesa credentials with a KES 1 test; Google Calendar OAuth; services and resources; hours and holidays; tone and language; staff users; go-live checklist | Operator, admin |
| Knowledge base editor | Create, edit, approve and archive entries in English and Swahili; import from a document; see which entries were used in recent answers | Operator, admin |
| Evaluation | Run the tenant's evaluation set; show pass rate and failures side by side with expected answers | Operator, admin |
| Review queue | Low-confidence answers, flagged chats and failed payments across all tenants, for daily review | Operator, admin |
| Templates | Create WhatsApp templates per tenant, submit to Meta, and track approval status | Operator, admin |
| Change requests | Owner requests with status; apply and close | Operator, admin |
| Health | Webhook delivery, queue depth, job failures, AI and Daraja error rates, WhatsApp number quality ratings | Operator, admin |
| Usage and billing | Monthly usage per tenant, overage, cost and margin; CSV export for invoicing | Admin |
| Audit log | Search all audited actions | Admin |

## **External integrations (INT)**

Four outside services. The details below are a starting point; the developer must confirm every endpoint, field and limit against each provider's current documentation before building.

### **WhatsApp Cloud API (Meta)**

* **OTBLABS setup:** one Meta app owned by OTBLABS, with each tenant's WhatsApp Business Account connected to it (ideally through Embedded Signup, so tenants own their account and grant OTBLABS access).  
* **Inbound:** one webhook URL for all tenants. Handle the GET verification handshake (hub.mode, hub.verify\_token, hub.challenge) and verify the X-Hub-Signature-256 HMAC on every POST with the app secret.  
* **Events to handle:** incoming messages (text, interactive replies, media, location), message status updates (sent, delivered, read, failed), and account or template status changes.  
* **Outbound:** the Graph API messages endpoint for the tenant's phone\_number\_id: text, interactive (buttons and lists), templates, and read receipts. Use a pinned Graph API version and plan upgrades.  
* **Rules:** free-form messages only inside 24 hours of the customer's last message; templates outside it. Store the template category and approval state.  
* **Costs:** from 1 October 2026 Meta charges for replies inside the customer window after the first 1,000 per number per month, so log every outbound message for the usage report.

### **M-Pesa Daraja (Safaricom)**

* **Auth:** OAuth client-credentials token per tenant, cached until shortly before expiry.  
* **STK Push:** the M-Pesa Express process-request endpoint, with the status-query endpoint as a fallback (see PAY).  
* **Callbacks:** one HTTPS callback path per tenant, with a secret segment; respond quickly; process on the queue.  
* **Environments:** sandbox for development and every tenant's first test; production once the tenant's Daraja go-live is approved.

### **Google Calendar**

* **Auth:** OAuth 2.0 with offline access, scoped to calendar events and free/busy only.  
* **Use:** a free/busy query to find availability; create, update and delete events for bookings; push notifications or a periodic sync to catch changes made directly in the calendar.  
* **Limits:** respect API quotas with back-off; cache free/busy results for at most 60 seconds.

### **AI provider**

* **Chat:** a hosted model API that supports structured (JSON) output and tool calling, behind Zamu's own interface so the provider can be changed by configuration.  
* **Embeddings:** one embeddings model for all tenants; store the model name with each vector, and re-embed everything if the model changes.  
* **Contract:** a provider that does not train on API data; record where data is processed for the ODPC registration.

## **Web routes and endpoints**

The dashboard and admin console are server-rendered Thymeleaf pages, not a separate API. Pages use GET; forms POST and then redirect (post-redirect-get), with CSRF protection on. Where a page updates part of itself (the inbox, slot pickers), the controller returns a Thymeleaf fragment instead of a full page. Only webhooks, server-sent events and CSV downloads are non-HTML. For tenant users the tenant always comes from the session, never from the URL; admin routes carry an explicit tenant ID and require the operator or admin role. Each route lives in the controller of the feature package named in brackets.

### **Public webhooks**

| Method and path | Purpose |
| :---- | :---- |
| GET /webhooks/whatsapp | Meta verification handshake |
| POST /webhooks/whatsapp | Inbound messages and status updates (signature-verified) |
| POST /webhooks/mpesa/{tenantId}/{secret} | STK Push callbacks |
| POST /webhooks/google-calendar/{channelId} | Calendar change notifications |

### **Auth**

| Method and path | Purpose |
| :---- | :---- |
| GET /login | Login page (users) |
| POST /login/code | Email a one-time login code; rate-limited to 5 per hour per email (users) |
| POST /login/verify | Check the code and start a session (users) |
| POST /logout | End the session (users) |

### **Client dashboard (under /app)**

| Method and path | Purpose |
| :---- | :---- |
| GET /app/inbox?status=\&q= | Inbox page; the list is also served as a fragment for live refresh (conversations) |
| GET /app/conversations/{id} | Conversation page with messages, customer, bookings and payments (conversations) |
| GET /app/stream | Server-sent events: new messages, handovers, status changes (conversations) |
| POST /app/conversations/{id}/messages | Staff reply: text, or a template with its variables (conversations) |
| POST /app/conversations/{id}/accept, /release, /close | Accept a handover, hand back to Zamu, close (handovers) |
| POST /app/customers/{id}/block | Block or unblock a customer (conversations) |
| GET /app/today | Today's bookings (bookings) |
| \`GET /app/bookings?view=calendar | list\&from=\&to=\` |
| GET /app/bookings/new and POST /app/bookings | Staff create a booking (bookings) |
| GET /app/bookings/slots?serviceId=\&date= | Slot picker fragment (bookings) |
| POST /app/bookings/{id}/move, /cancel, /status | Move, cancel, mark completed or no-show (bookings) |
| GET /app/payments and /app/payments.csv | Payments page and CSV export (payments) |
| GET /app/reports/weekly?week=, /monthly?month=, plus .csv | Reports (reports) |
| GET /app/knowledge | Approved knowledge base, read-only (knowledge) |
| POST /app/change-requests | Owner submits a change (tenants) |
| GET /app/settings and POST /app/settings/staff-only | Settings page; staff-only mode switch (tenants) |
| GET /app/users, POST /app/users, POST /app/users/{id}/disable | Owner manages staff (users) |

### **Admin console**

| Method and path | Purpose |
| :---- | :---- |
| GET /admin/tenants and POST /admin/tenants | Tenant list; create a tenant (tenants) |
| GET /admin/tenants/{id}/setup/{step} and POST to the same path | Setup wizard steps: profile, whatsapp, mpesa, calendar, services, hours, tone, staff, go-live (tenants) |
| POST /admin/tenants/{id}/mpesa/test | Send a KES 1 test STK Push (payments) |
| GET /admin/tenants/{id}/calendar/connect and /calendar/callback | Google OAuth start and return (bookings) |
| GET, POST /admin/tenants/{id}/kb and POST .../kb/{entryId}/approve, /archive | Knowledge base editor (knowledge) |
| POST /admin/tenants/{id}/eval/run and GET .../eval/{runId} | Run and view the evaluation set (ai) |
| GET, POST /admin/tenants/{id}/templates | WhatsApp templates (whatsapp) |
| GET /admin/review-queue | Flagged items across tenants (conversations) |
| GET /admin/change-requests and POST .../{id}/close | Owner requests (tenants) |
| GET /admin/jobs?status= and POST /admin/jobs/{id}/retry | Job monitor and dead-job retry (shared.jobs) |
| GET /admin/health | Webhooks, jobs backlog, provider error rates, number quality (shared) |
| GET /admin/usage?month= and .csv | Usage and margin report (reports) |
| POST /admin/tenants/{id}/export and /delete | Data export and deletion, admin only, audited (tenants) |
| GET /admin/audit?entity=\&actor= | Audit log search (audit) |

Errors are handled in one @ControllerAdvice: pages show a friendly error page or inline form errors; webhooks return the status code the provider expects and log the details. Each feature defines its own exceptions, such as BookingSlotTakenException.

## **Non-functional requirements (NFR)**

These apply to the whole system. Version 1 is sized for up to 50 tenants and about 75,000 conversations a month in total, with room to grow without a rewrite.

| ID | Area | Requirement | Priority |
| :---- | :---- | :---- | :---- |
| NFR-01 | Performance | Webhooks acknowledged in under 2 seconds; text replies sent in under 8 seconds at the 95th percentile | Must |
| NFR-02 | Performance | Dashboard pages load in under 2 seconds on a 4G phone connection | Should |
| NFR-03 | Availability | 99.5% monthly uptime for webhook endpoints and message processing | Must |
| NFR-04 | Resilience | Queue jobs retry with exponential back-off (5 attempts); jobs that still fail move to status dead in the jobs table, visible and retryable in the admin console | Must |
| NFR-05 | Resilience | If the AI provider fails, switch to the backup provider, then to staff-only replies for that conversation; never leave a customer without a reply | Must |
| NFR-06 | Security | Secrets (tokens, keys, passkeys) encrypted at rest with a key held outside the database; never returned by the API after saving | Must |
| NFR-07 | Security | HTTPS everywhere; HSTS; secure, HTTP-only, same-site session cookies; CSRF protection on state-changing requests | Must |
| NFR-08 | Security | Sessions expire after 12 hours of inactivity; OTBLABS roles require a second factor | Must |
| NFR-09 | Security | Tenant isolation enforced in every repository query (tenant ID taken from the session or job, never the request), proved by automated tests | Must |
| NFR-10 | Security | Dependency scanning in CI; an external penetration test before the tenth paying client | Should |
| NFR-11 | Privacy | Data retention jobs: conversations 12 months, payment records 7 years, everything else deleted within 30 days of offboarding, all configurable per tenant | Must |
| NFR-12 | Privacy | Per-customer export and deletion on request, completed within 14 days | Must |
| NFR-13 | Privacy | Personal data is never written to application logs; phone numbers are masked in logs (2547XXXXX123) | Must |
| NFR-14 | Audit | All admin actions, settings changes, exports, deletions and tenant-dashboard access by OTBLABS staff are written to the audit log | Must |
| NFR-15 | Backups | Daily encrypted database backups kept 30 days; point-in-time recovery if the host offers it; restore tested monthly | Must |
| NFR-16 | Observability | Structured logs with a request or job ID; error tracking; metrics for queue depth, job latency, AI latency and cost, webhook failures | Must |
| NFR-17 | Accessibility | Dashboard meets WCAG 2.1 AA for contrast, keyboard use and labels | Should |
| NFR-18 | Time | All times stored in UTC, shown in Africa/Nairobi; scheduling respects the tenant's timezone | Must |
| NFR-19 | Cost | AI and WhatsApp cost per conversation recorded; alert when a tenant's monthly running cost passes 40% of their plan fee | Should |

## **Environments, deployment and monitoring**

Three environments, deployed from one Git repository through CI. Nothing reaches production without passing tests and the AI evaluation set.

| Environment | Purpose | Integrations |
| :---- | :---- | :---- |
| Local | Development | Daraja sandbox, a Meta test number, a test Google account, mocked AI if needed |
| Staging | Demo tenant and pre-release testing | Daraja sandbox, a Meta test number, real AI provider |
| Production | Live tenants | Daraja production per tenant, tenants' real numbers |

### **Repository and CI**

* One Maven project, zamu, organised by feature package as described in Architecture. Templates in src/main/resources/templates/\<feature\>/, migrations in src/main/resources/db/migration/, Tailwind source in src/main/frontend/. A package can later move into its own Maven module without code changes beyond the build file.  
* Every pull request runs lint, type checks, unit and integration tests, and the tenant-isolation tests.  
* Merging to main deploys to staging automatically; production deploys are manual, with one-click rollback.  
* Database migrations run before the new version starts and must be backward compatible for one release.  
* Configuration through environment variables; secrets in the host's secret store; a .env.example documents them all.

### **Monitoring and alerts**

| Alert | Threshold | Goes to |
| :---- | :---- | :---- |
| WhatsApp webhook errors | More than 5 in 5 minutes | Operator on call |
| Queue backlog | Over 100 jobs or oldest job over 2 minutes | Operator on call |
| AI provider errors | Over 5% of calls in 10 minutes | Operator on call |
| Daraja callbacks missing | Payments stuck in initiated over 10 minutes | Operator |
| Calendar access lost | Any tenant | Operator |
| WhatsApp quality rating drop | Any tenant moves down a level | Operator |
| Error rate | Any new error type in production | Developer |

A public status endpoint (/health) reports database, jobs backlog and provider reachability (Spring Boot Actuator) for uptime monitoring.

## **Testing and acceptance**

Version 1 is accepted when every Must requirement passes on staging with a demo clinic tenant, and the end-to-end scenarios below pass on a real WhatsApp number with real M-Pesa payments of KES 1\.

### **Required automated tests**

* **Unit:** slot calculation (hours, buffers, holidays, minimum notice, resource clashes), phone normalisation, STK password and timestamp, result-code mapping, conversation window and billing counters.  
* **Integration:** WhatsApp webhook signature, de-duplication and routing; Daraja callback idempotency; booking state transitions; reminder scheduling and cancellation.  
* **Tenant isolation:** for every route and repository method, a user of tenant A gets 404 or 403 for tenant B's resources.  
* **AI evaluation:** the demo tenant's 40-question set at 95% correct or better, with zero invented prices.  
* **Load:** 20 inbound messages per second for 5 minutes across 10 tenants with no lost messages and replies within NFR-01.

Repository and integration tests run against MySQL in Testcontainers, on the same major version as production; no H2 or in-memory substitutes. Use @WebMvcTest for controllers and check that Thymeleaf pages render with the expected data and form errors.

### **End-to-end acceptance scenarios**

| \# | Scenario | Expected result |
| :---- | :---- | :---- |
| 1 | New customer says hello in English | Greeting with assistant disclosure, then a helpful reply |
| 2 | Customer asks the price of a listed service in Swahili | Correct price in Swahili, matching the knowledge base |
| 3 | Customer asks something not in the knowledge base | "Not sure" reply and a handover with reason low\_confidence |
| 4 | Customer describes chest pain | Emergency reply, immediate handover, owner alerted |
| 5 | Customer books a service without a deposit | Slot list, booking confirmed, event in Google Calendar |
| 6 | Customer books a service with a deposit and pays | STK prompt, payment recorded with receipt number, booking confirmed, receipt message |
| 7 | Customer cancels the STK prompt | Friendly message, offer to resend; booking not confirmed |
| 8 | Staff books the same slot in Google Calendar a moment before the customer confirms | Zamu detects the clash and offers other slots |
| 9 | Reminder 24 hours before; customer taps reschedule | New slots offered, calendar updated, old slot released |
| 10 | Customer types "mtu" | Handover; staff accept and reply from the dashboard; customer sees it in the same chat |
| 11 | Staff try to reply 30 hours after the customer's last message | Dashboard offers templates only, with an explanation |
| 12 | Customer sends a voice note | Polite acknowledgement and offer of a person |
| 13 | Customer tries to make Zamu reveal its instructions | Zamu declines and continues normally |
| 14 | Owner turns on staff-only mode | Bot stops replying; holding message sent; all chats go to the inbox |
| 15 | Weekly report generated | Numbers match the database for that week |
| 16 | Admin exports then deletes a test customer | Export contains their data; afterwards they are gone from all tables except payment records |

## **Delivery plan**

With one full-time senior developer starting Monday 19 October 2026, a pilot release (answers, handover and the inbox) can be ready on 13 November and the full version 1 accepted on 18 December. A second developer, or reusing an existing inbox product, could bring that forward by two to three weeks. These are estimates; ask the developer to re-plan after the first week.

![Zamu delivery plan: 7 workstreams, a pilot release on 13 November and version 1 on 18 December 2026][image2]

Zamu delivery plan: 7 workstreams, a pilot release on 13 November and version 1 on 18 December 2026

The launch plan had pilots starting on 2 November. With this build plan, pilots start on the 13 November release with answers and handover only, and bookings and payments are switched on for pilot clients as they land in late November and early December.

**What each milestone delivers:**

1. **Foundations:** repository, CI, database schema, tenants, users and email login.  
2. **WhatsApp and inbox:** webhooks, conversation engine, staff inbox and handover (MSG, HO).  
3. **AI answers:** retrieval, guardrails, knowledge base editor, evaluation runner (AI).  
4. **Pilot release:** a real tenant can go live for answers and handover.  
5. **Bookings and reminders:** slots, Google Calendar, reminder templates (BK).  
6. **M-Pesa payments:** STK Push, callbacks, status queries, reconciliation (PAY).  
7. **Reports and hardening:** reports, usage metering, monitoring, backups, security review (REP, NFR).  
8. **Acceptance:** all 16 end-to-end scenarios pass on staging and on one live pilot.

## **Open questions**

Settle these with the developer in the first week; each one changes the build.

* ☐ **WhatsApp onboarding:** use Meta's Embedded Signup (tenants own their account; needs OTBLABS to be an approved Tech Provider) or connect each tenant manually? Embedded Signup is better long term but takes approval time.  
* ☐ **Existing numbers:** can tenants keep using the WhatsApp Business app on the same number alongside the Cloud API (coexistence), and with what limits? Confirm with Meta's current documentation.  
* ☐ **Owner alerts and daily summaries:** send them from a separate OTBLABS "Zamu" WhatsApp number (needs templates and its own costs), or by email and SMS only?  
* ☐ **Till numbers:** confirm with Safaricom how STK Push works for each tenant's Buy Goods till (store number versus till number) and what each tenant must request for Daraja go-live.  
* ☐ **AI provider:** which provider and models for launch, and where they process data; confirm the no-training terms in writing.  
* ☐ **Hosting region:** where to host the database, given data-transfer disclosure in the ODPC registration and latency to Kenya.  
* ☐ **Calendars:** is Google Calendar enough for the first 10 clients, or do some clinics need their practice management system instead?  
* ☐ **Build or buy the inbox:** build the staff inbox as specified, or use an existing shared-inbox product and build only the AI, booking and payment parts?  
* ☐ **Ownership:** OTBLABS owns all source code, and the developer assigns IP in their contract.  
* ☐ **Handover alerts:** do staff need a mobile push notification (requires a PWA with notifications), or are browser notifications and a WhatsApp alert enough?  
* ☐ **htmx:** use htmx for the live inbox, slot pickers and inline form updates, or plain JavaScript with server-sent events? htmx keeps everything in Thymeleaf and is the recommendation.  
* ☐ **MySQL host and version:** which managed MySQL (8.4 LTS or later), and does it offer native vector search worth using later? Not needed for version 1\.  
* ☐ **Versions:** confirm the Java (21 or 25 LTS) and Spring Boot versions at the start, and pin them in the build.  
* ☐ **When to add Redis:** agree the trigger in advance, for example more than 3 application instances, or the jobs table polling becoming a measurable database load.

[image1]: <data:image/png;base64,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>

[image2]: <data:image/png;base64,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>