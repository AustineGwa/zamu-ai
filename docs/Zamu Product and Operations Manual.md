# **Zamu Product and Operations Manual**

Oct 10, 2026 · @Austine Gwa

## **Product overview**

Zamu is an AI receptionist that runs a business's WhatsApp number around the clock. It answers questions from the business's own information, books appointments, collects M-Pesa payments and hands the chat to a person when needed. OTBLABS sets it up, runs it and improves it for a monthly fee.

This manual is the reference for the OTBLABS team. It covers what Zamu does, how a new client is onboarded, and how the service is run every day.

**Who Zamu is for:** small and mid-size businesses whose customers book or pay over WhatsApp. The first focus is private clinics, then salons and spas, then private schools.

**The four roles:**

| Role | Who | What they do in Zamu |
| :---- | :---- | :---- |
| Customer | The client's patients, guests or parents | Chat on WhatsApp, book, pay, ask questions |
| Client staff | Receptionists, front-desk team | Take over chats Zamu hands to them; confirm bookings when needed |
| Client owner | Practice owner, manager or bursar | Approves the knowledge base, reads reports, pays the subscription |
| OTBLABS operator | Our onboarding and support team | Sets up, monitors, reviews conversations, fixes answers, supports the client |

The assistant always replies under the client's own business name. Customers never see the Zamu or OTBLABS name.

## **How a conversation works**

![Zamu conversation flow: three paths, each ending in an answer, a confirmed booking or a person][image1]

Zamu conversation flow: three paths, each ending in an answer, a confirmed booking or a person

Zamu decides what each message needs. Questions it can answer from approved information get a reply; anything it can't answer, or anything sensitive, goes to a person; bookings are confirmed only once the calendar is updated and, where a deposit is required, M-Pesa confirms payment.

## **Features in detail**

Version 1 has eight features. Each one is described by what the customer sees, what the client controls, and the rules that keep it safe.

### **1\. Answers from the business's own information**

* **Customer sees:** a quick, friendly reply to questions about services, prices, hours, location, parking, insurance accepted, policies and preparation instructions.  
* **Client controls:** the knowledge base, which they approve before go-live and can ask us to change at any time.  
* **Rules:** Zamu answers only from the approved knowledge base. If the answer isn't there, it says so and offers a staff member. It never gives medical, legal or financial advice.

### **2\. Bookings**

* **Customer sees:** available slots for the service and staff member they want, then a confirmation with date, time, location and what to bring.  
* **Client controls:** working hours, services and their durations, which staff do which services, buffer time between bookings, and how far ahead customers can book.  
* **Rules:** a slot is held only once it is written to the client's calendar. Customers can reschedule or cancel by replying to the confirmation, up to the client's cut-off time.

### **3\. Reminders**

* **Customer sees:** a reminder 24 hours before the appointment, with buttons to confirm, reschedule or cancel. An optional second reminder goes out 2 hours before.  
* **Client controls:** whether reminders are on, their timing and wording.  
* **Rules:** reminders are sent as approved WhatsApp utility templates, because they can fall outside the 24-hour chat window.

### **4\. M-Pesa payments**

* **Customer sees:** an M-Pesa prompt on their phone for a deposit or fee, then a receipt message in the chat once paid.  
* **Client controls:** which services need a deposit and how much, and their own till or paybill number.  
* **Rules:** money goes straight to the client's till or paybill; OTBLABS never holds client funds. A booking marked "deposit required" is confirmed only when M-Pesa confirms the payment.

### **5\. Handover to staff**

* **Customer sees:** "I'm connecting you to our team," then a reply from a person.  
* **Client controls:** who receives handovers, their hours, and topics that always go to a person.  
* **Rules:** Zamu hands over when the customer asks for a person, the topic is sensitive (medical symptoms, complaints, refunds, emergencies), or it is not confident in its answer. Outside staff hours it takes a message and promises a reply time.

### **6\. Client dashboard**

A web dashboard where staff see every conversation live, reply to handovers, and see today's bookings and payments. Owners also see reports and settings.

### **7\. Reports**

* **Daily summary** to the owner on WhatsApp: chats handled, bookings, payments, open handovers.  
* **Weekly report** by email: totals, after-hours bookings, top questions, questions Zamu could not answer, and suggested knowledge base additions.

### **8\. Languages**

Zamu replies in the language the customer writes in. English and Swahili are fully supported, and it understands common Sheng while replying in clear Swahili or English. The client chooses the default tone, from formal to friendly.

## **System architecture, data and security**

Zamu is one shared platform with each client kept separate. Every client has their own WhatsApp number, knowledge base, calendar connection, M-Pesa credentials and data, and no client can see another's.

### **Components**

| Component | What it does | Built with |
| :---- | :---- | :---- |
| WhatsApp gateway | Receives and sends messages through Meta's WhatsApp Cloud API; verifies Meta's webhook signatures | Meta Cloud API, webhook service |
| Conversation engine | Keeps each chat's context, decides whether to answer, book, request payment or hand over | Spring Boot application (Java) |
| AI layer | Writes replies using only passages retrieved from the client's knowledge base; a small model by default, a larger one for hard questions | AI model API with retrieval |
| Booking service | Reads free slots and writes bookings to the client's calendar | Google Calendar API |
| Payment service | Sends STK Push requests, receives M-Pesa callbacks, matches payments to bookings | M-Pesa Daraja API |
| Scheduler | Sends reminders, daily summaries and weekly reports | MySQL jobs table |
| Dashboard | Live inbox, handovers, bookings, payments, reports and settings | Spring Boot with Thymeleaf and Tailwind CSS |
| Database | Stores clients, conversations, bookings, payments and the knowledge base | MySQL |

### **Data stored per client**

| Data | Why we keep it | How long |
| :---- | :---- | :---- |
| Customer name and WhatsApp number | To reply, book and remind | While the client is active, then deleted within 30 days of offboarding |
| Conversation history | Context, review and quality checks | 12 months, unless the client asks for less |
| Bookings | Calendar sync and reports | 12 months |
| M-Pesa transaction references | Matching payments to bookings and reconciliation | 7 years, or as the client's accounting rules require |
| Knowledge base | Answering questions | While the client is active |

Zamu does not store medical histories, diagnoses or card details. If a customer volunteers sensitive health information, Zamu hands the chat to staff and doesn't repeat that information in reports. The retention periods above are proposals; confirm them with the lawyer who reviews the data processing agreement.

### **Security**

* Client secrets (WhatsApp tokens, Daraja keys, calendar access) are encrypted at rest and never shown in the dashboard after setup.  
* Dashboard logins use email plus a one-time code; each staff member has their own login, and owners can remove staff instantly.  
* Every login, export and settings change is recorded in an audit log.  
* All traffic is encrypted in transit; daily database backups are kept for 30 days and restore is tested monthly.  
* AI providers are chosen on the condition that they don't train on our data.  
* OTBLABS staff access production data only for support or review, and that access is logged.

## **Plans and limits**

Three monthly plans, each capped by conversations so WhatsApp and AI costs stay predictable. Prices are the launch prices still being tested with the first clients.

|  | Starter | Growth | Business |
| :---- | :---- | :---- | :---- |
| KES per month | 7,500 | 15,000 | 30,000 |
| Setup fee (KES) | 25,000 | 25,000 | Quoted |
| Conversations per month | 500 | 1,500 | 4,000 |
| WhatsApp numbers | 1 | 1 | Up to 3 |
| Staff logins | 2 | 5 | 15 |
| Answers and handover | Yes | Yes | Yes |
| Bookings and reminders | No | Yes | Yes |
| M-Pesa payments | No | Yes | Yes |
| Daily and weekly reports | Weekly only | Yes | Yes |
| Zoho or Odoo sync | No | No | Yes |
| Knowledge base updates included | 2 per month | 5 per month | Unlimited |
| Support hours | Weekdays 8am to 6pm | Weekdays 8am to 6pm | Every day 7am to 9pm |
| Monthly review call | No | No | Yes |

**What counts as a conversation:** all messages with one customer within 24 hours of their first message. A customer who chats in the morning and again in the afternoon is one conversation; the next day is a new one. Reminders and reports don't count.

**Going over the limit:** Zamu keeps working. Extra conversations are billed at KES 15 each, and a client who goes over two months in a row is offered the next plan. The overage rate is a proposal to test against real running costs.

## **Client onboarding**

A new client goes live five working days after the kickoff call, followed by two weeks of close monitoring. The two steps outside our control are Meta business verification and M-Pesa Daraja go-live, so both start on day 1\.

### **Before kickoff**

* ☐ Client signs the service agreement and the data processing agreement  
* ☐ Setup fee invoiced and paid (waived for launch-offer clients)  
* ☐ Client receives the intake questionnaire (appendix) and the documents list  
* ☐ Kickoff call booked within 3 working days of signing

**Documents the client provides:**

* Business registration certificate and KRA PIN, for Meta verification  
* Access to their Meta Business account, or permission for us to create one in their name  
* The phone number Zamu will use, and whether it is currently on the WhatsApp Business app  
* M-Pesa till or paybill number and the details of whoever administers it on the M-Pesa business portal  
* Their ODPC registration certificate, if they have one (clinics and schools must register)  
* Logo, price list, service menu, opening hours and any existing FAQ or policy documents

### **The five days**

| Day | Step | Owner | Done when |
| :---- | :---- | :---- | :---- |
| 1 | Kickoff call (60 min): walk through the questionnaire, agree services, deposits, handover staff and tone | OTBLABS onboarding lead \+ client owner | Questionnaire complete; handover staff named |
| 1 | Start Meta business verification and WhatsApp number registration | Client, guided by OTBLABS | Verification submitted |
| 1 | Start M-Pesa Daraja production go-live for the client's till or paybill | Client's M-Pesa admin, guided by OTBLABS | Go-live request submitted |
| 2 | Write the knowledge base from the questionnaire and documents | OTBLABS | First draft ready for client review |
| 2 | Set up services, durations, staff, working hours and calendar connection | OTBLABS | Test booking appears in the client's calendar |
| 3 | Connect the WhatsApp number; submit reminder and receipt templates to Meta for approval | OTBLABS | Number live on the test account; templates submitted |
| 3 | Connect M-Pesa: test in the Daraja sandbox, then production when go-live is approved | OTBLABS | A KES 1 test payment confirmed end to end |
| 4 | Run the test script (testing section); fix answers | OTBLABS | All test cases pass |
| 4 | Client reviews the knowledge base and tries Zamu on a test phone | Client owner | Written approval of the knowledge base |
| 4 | Train staff on the dashboard and handovers (45 min) | OTBLABS \+ client staff | Every staff member has logged in and taken over a test chat |
| 5 | Go live; watch every conversation for the first day | OTBLABS | Go-live checklist signed off |
| 5 | Client tells customers: WhatsApp status, posters at reception, a broadcast to past customers who opted in | Client | Announcement sent |

### **After go-live**

* **Days 6 to 15 (close monitoring):** review every conversation daily, fix wrong answers the same day, and send the client a short daily note.  
* **Day 15 check-in call (30 min):** review results, agree changes, confirm the client is happy to move to normal operations.  
* **Day 30:** first monthly report; ask launch-offer clients for their testimonial.

**If Meta verification or Daraja go-live is delayed:** go live on time for answers and handover only, then switch on bookings and payments the day approval arrives. Tell the client on day 3 if either looks likely to slip.

**If the number is already on the WhatsApp Business app:** check with Meta whether it can be connected to the Cloud API while staying on the app, or move it fully to the Cloud API. Agree this with the client at kickoff, because moving the number changes how their staff use WhatsApp.

## **Building the knowledge base**

The knowledge base is the only source Zamu answers from, so its quality decides the quality of every reply. Write it as short, separate entries, each answering one question, and get the client's written approval before go-live.

### **What to collect**

| Area | Examples |
| :---- | :---- |
| Business basics | Name as customers know it, branches, address with landmarks, Google Maps link, parking, opening hours, public holiday hours |
| Services | Every service with a plain description, duration, price or price range, who performs it, and preparation instructions |
| Payments | Accepted methods, deposit rules, insurance and SHA (formerly NHIF) cover accepted (clinics), fee structures and deadlines (schools) |
| Policies | Cancellation and lateness, refunds, children and companions, COVID or hygiene rules, privacy |
| Staff | Names and roles customers may ask for, and their days (only what the client agrees to share) |
| Escalation | Topics that always go to a person, emergency instructions, after-hours contact |
| Tone | Formal or friendly, preferred greeting, words to avoid, how to address customers |
| Top questions | The 20 questions staff are asked most, collected from recent WhatsApp chats |

### **Writing rules**

1. One entry per question, in plain language, under 80 words.  
2. Write facts, not marketing. "A filling costs KES 4,500 to 8,000 depending on size" beats "affordable fillings".  
3. Give ranges and conditions when prices vary, and say who confirms the final price.  
4. Include Swahili versions for the 20 most common questions.  
5. Never include clinical advice. For symptoms, the entry says to book or call, and gives the emergency instruction.  
6. Date every entry with a price or a policy, so stale entries are easy to spot.  
7. Mark any entry the client hasn't confirmed as "draft". Draft entries are never used in live replies.

### **Keeping it current**

* Ask the client to send changes (prices, hours, holidays, new services) to the support WhatsApp group.  
* Apply plan-included updates within one working day.  
* Each weekly review adds new entries for questions Zamu could not answer.  
* Every quarter, send the client the full knowledge base to re-confirm.

## **Testing and go-live checklist**

No client goes live until every test below passes on their real number and the client owner has signed off. Run the tests from two phones: one Safaricom line for payments, one other network line.

### **Test script**

| \# | Test | Pass when |
| :---- | :---- | :---- |
| 1 | Ask the 20 top questions in English | Every answer matches the knowledge base |
| 2 | Ask 10 of them in Swahili and 5 in Sheng | Replies are correct and in the customer's language |
| 3 | Ask something not in the knowledge base | Zamu says it doesn't know and offers a staff member; nothing is invented |
| 4 | Describe a medical symptom or emergency | Zamu gives the emergency instruction and hands over immediately; no advice |
| 5 | Ask for a person | Handover reaches the dashboard and the named staff member is notified |
| 6 | Book, reschedule and cancel an appointment | The calendar shows each change within a minute |
| 7 | Try to book a taken slot or outside hours | Zamu offers the nearest free slots instead |
| 8 | Book a service that needs a deposit and pay KES 1 | STK prompt arrives, payment confirms, receipt message sent, booking confirmed |
| 9 | Cancel the M-Pesa prompt, or let it time out | Booking stays unconfirmed; Zamu offers to resend the prompt |
| 10 | Trigger a reminder for a booking | Reminder template arrives with confirm, reschedule and cancel options |
| 11 | Send a message after staff hours asking for a person | Zamu takes a message and states when the team will reply |
| 12 | Send an image, voice note and sticker | Zamu replies politely and offers a staff member if it can't read the content |
| 13 | Send abusive or spam messages | Zamu stays polite, stops engaging after two messages and flags the chat |

### **Go-live sign-off**

* ☐ All 13 tests passed and recorded  
* ☐ Knowledge base approved in writing by the client owner  
* ☐ Meta business verification approved, display name approved  
* ☐ Reminder and receipt templates approved by Meta  
* ☐ M-Pesa production credentials live and a real payment confirmed  
* ☐ Every staff member has a dashboard login and has handled a test handover  
* ☐ Daily summary recipient and weekly report email confirmed  
* ☐ Client ODPC registration checked and the data processing agreement signed  
* ☐ Monitoring alerts switched on for this client  
* ☐ Client announcement ready: WhatsApp status, reception poster, opt-in broadcast

## **Daily and weekly operations**

One OTBLABS operator can run about 25 clients on these routines once each client is past close monitoring. That figure is an estimate to check against real workload after the first ten clients.

### **Every day (about 20 minutes per 10 clients)**

* ☐ 8:00am: check the monitoring board. All numbers connected, no failed webhooks, M-Pesa callbacks arriving, AI provider responding.  
* ☐ Check overnight handovers that no staff member has picked up, and nudge the client's staff on the support group.  
* ☐ Review every conversation flagged by Zamu as low confidence, abusive or failed payment.  
* ☐ Fix any wrong answer the same day: correct the knowledge base entry, then re-test that question.  
* ☐ Confirm daily summaries went out at 7:00pm.

### **Every week (about 30 minutes per client)**

* ☐ Read a sample of 20 conversations per client, plus all handovers.  
* ☐ Score them: correct, partly correct, wrong, should have handed over. Target 95% correct or better.  
* ☐ Add knowledge base entries for questions Zamu couldn't answer, with the client's approval.  
* ☐ Check the weekly report before it goes out on Monday at 8:00am.  
* ☐ Compare running cost (WhatsApp plus AI) with the client's fee and flag any client above 40% of their fee.

### **Every month**

* ☐ Send the monthly report: conversations, after-hours bookings, payments collected, handover rate, top questions.  
* ☐ Review clients near or over their conversation limit and offer an upgrade where it fits.  
* ☐ Business plan clients: hold the monthly review call.  
* ☐ Check for WhatsApp, M-Pesa or AI provider changes that affect costs or features.  
* ☐ Test restoring a database backup.

### **Every quarter**

* ☐ Send each client the full knowledge base to re-confirm.  
* ☐ Review margins per plan and adjust prices or limits if needed.  
* ☐ Review staff access: remove leavers from both OTBLABS and client accounts.

## **Support and incidents**

Clients reach support through one WhatsApp group per client, which includes the owner, the OTBLABS operator and a backup operator. Urgent issues can also be raised by phone.

### **Priority levels**

| Priority | Example | First response | Fix or workaround |
| :---- | :---- | :---- | :---- |
| P1, service down | Zamu not replying at all; payments failing for everyone | 30 minutes, any time | 4 hours |
| P2, feature broken | Bookings not reaching the calendar; reminders not sending | 2 support hours | 1 working day |
| P3, wrong answer | Zamu gave an outdated price or wrong instruction | 4 support hours | Same working day |
| P4, change request | New service, new price, tone change | 1 working day | Within plan allowance, 1 working day |

Support hours follow the client's plan. P1 is covered at any time on every plan. These targets are proposals; put the final ones in the service agreement.

### **When something breaks (P1 and P2)**

1. **Acknowledge** in the client's support group within the response time, saying what is affected.  
2. **Protect customers:** if Zamu can't reply properly, switch the client to "staff only" mode, so every message goes straight to the dashboard with an automatic "our team will reply shortly".  
3. **Find the cause** using the monitoring board: WhatsApp connection, M-Pesa callbacks, AI provider, calendar access, or our own service.  
4. **Fix or work around** it, then test with a real message and, for payments, a KES 1 transaction.  
5. **Update the client** at least every hour on a P1 until it is fixed.  
6. **Follow up** within 2 working days with a short written note: what happened, who was affected, what changed so it doesn't happen again.

### **Common outside causes**

* **Meta:** WhatsApp outages, a template rejected, a number restricted for policy reasons, or the business verification lapsing.  
* **Safaricom:** Daraja downtime or delayed callbacks. Zamu keeps the booking as "awaiting payment" and checks the transaction status again before giving up.  
* **AI provider:** an outage or slow responses. Zamu switches to a backup model or to staff-only mode.  
* **Client side:** calendar access revoked, a password changed, staff not picking up handovers.

## **Billing, renewals, upgrades and offboarding**

Clients pay monthly in advance by M-Pesa paybill or bank transfer, and every invoice is a KRA eTIMS invoice. Confirm the VAT treatment with your accountant before the first invoice.

### **Billing cycle**

| When | What happens |
| :---- | :---- |
| 25th of the month | Invoice for next month sent by email and WhatsApp, including any overage from this month |
| 1st | Payment due |
| 7th | Friendly reminder if unpaid |
| 14th | Final notice: service pauses on the 21st if still unpaid |
| 21st | Service moves to staff-only mode: customers still reach the client's staff, but Zamu stops answering, booking and taking payments |
| On payment | Full service restored within 1 working hour |

Never switch a number off completely for non-payment. The client's customers must still be able to reach a person.

### **Contract terms**

* Launch-offer clients: first month at half price, then a 3-month minimum.  
* Standard clients: month to month after setup, with 30 days' notice to cancel.  
* Annual prepayment: two months free, as a 12-month commitment.

### **Upgrades and downgrades**

* **Upgrade:** takes effect immediately; the difference for the current month is pro-rated on the next invoice.  
* **Downgrade:** takes effect at the start of the next month. Features the lower plan lacks, such as payments, are switched off then, and the client is warned 7 days before.

### **Offboarding**

1. Confirm the end date in writing and agree what the client keeps.  
2. Export and send the client their data: conversation history, bookings, payment references and the knowledge base, as CSV and PDF.  
3. Hand the WhatsApp number back. Remove it from our Meta app so the client can use it elsewhere or on the WhatsApp Business app.  
4. Delete the client's M-Pesa credentials and calendar access the same day.  
5. Delete the client's remaining data within 30 days, except payment references kept for accounting, and confirm the deletion in writing.  
6. Ask for 10 minutes of feedback on why they are leaving.

## **Data protection and compliance**

Under Kenya's Data Protection Act, 2019, each client is the data controller for their customers' data and OTBLABS is the data processor. Healthcare providers and educational institutions must register with the ODPC whatever their size ([Bowmans](https://bowmanslaw.com/insights/kenya-data-protection-getting-ready-for-registration/)). OTBLABS should register as a processor before taking paid clients. This section describes practice, not legal advice; have a lawyer review the agreements.

### **Standing obligations**

* ☐ OTBLABS registered with the ODPC as a data processor, with the certificate renewed every two years  
* ☐ A signed data processing agreement with every client before go-live  
* ☐ A list of sub-processors (Meta, the AI provider, hosting, email) and the countries they process data in, shared with clients and kept current  
* ☐ A privacy notice the client links from their WhatsApp profile, explaining that an automated assistant replies  
* ☐ Staff trained on data handling during onboarding to OTBLABS

### **In the product**

* Zamu's first message to a new customer says it is an automated assistant and that the customer can ask for a person at any time.  
* Marketing messages go only to customers who opted in, and every one includes a way to opt out.  
* Customers can ask Zamu to delete their data. The request goes to the client and OTBLABS, and is completed within 14 days.  
* Sensitive health details are not written into reports, summaries or the knowledge base.

### **If a data breach happens**

1. Contain it: revoke the exposed keys or access immediately.  
2. Tell the affected client within 24 hours, with what is known.  
3. Support the client, as controller, to notify the ODPC. The Act requires notifying the Data Commissioner within 72 hours of becoming aware of a breach; confirm the exact rules with your lawyer.  
4. Record what happened, the data involved, the people affected and the fix, in the incident log.

### **Meta and WhatsApp policies**

* Use only templates Meta has approved, in the right category (utility for reminders and receipts, marketing for promotions).  
* Don't message customers who haven't contacted the business or opted in.  
* Watch each number's quality rating weekly. A falling rating means customers are blocking or reporting messages; pause campaigns and investigate.

## **Troubleshooting**

Check the monitoring board first: most problems show up there as a red connection before a client notices.

| Problem | Likely cause | Fix |
| :---- | :---- | :---- |
| Zamu isn't replying to anyone | WhatsApp token expired, webhook failing, or Meta outage | Check the gateway status; renew the token; check Meta's status page; switch to staff-only mode if it will take long |
| Replies are slow (over 30 seconds) | AI provider slow or overloaded | Switch the client to the backup model; check the provider's status |
| Zamu gave a wrong answer | Missing or outdated knowledge base entry | Correct the entry, re-test the question, tell the client what changed |
| Zamu made something up | An entry is vague, or the confidence threshold is too low | Rewrite the entry with exact facts; raise the handover threshold for that client |
| STK prompt doesn't arrive | Wrong till or paybill, Daraja downtime, or the customer's phone is off | Check the Daraja response code; ask the customer to retry; confirm the till details |
| Customer paid but booking not confirmed | Callback delayed or missed | Run a transaction status query; confirm manually; message the customer |
| Booking missing from the calendar | Calendar access revoked or wrong calendar selected | Ask the client to reconnect the calendar; re-sync the day's bookings |
| Double booking | Staff booked directly in the calendar without blocking the slot | Show the client how to block slots; Zamu re-checks the calendar before confirming |
| Reminders not sending | Template paused or rejected by Meta | Check template status; resubmit with clearer utility wording |
| Handovers not picked up | Staff not logged in or notifications off | Check staff notification settings; agree a backup person |
| Number quality rating dropping | Too many unwanted messages, or customers reporting | Pause campaigns; review recent outbound messages; remove non-opted-in contacts |
| Client over their conversation limit | Busy period, or spam chats | Check for spam; block abusers; offer the next plan if it is real demand |
| Customer writes in a language Zamu handles poorly | Rare language or heavy Sheng | Zamu replies in English or Swahili and offers a staff member |

## **Roles and responsibilities**

OTBLABS runs the system; the client owns their information, their customers and their money. Two OTBLABS roles can be the same person while the client count is small.

| Task | OTBLABS onboarding lead | OTBLABS operator | Client owner | Client staff |
| :---- | :---- | :---- | :---- | :---- |
| Sign agreement and data processing agreement | Prepares |  | Signs |  |
| Meta verification and number registration | Guides |  | Provides documents and access |  |
| M-Pesa Daraja go-live | Guides |  | Provides till admin access |  |
| Write the knowledge base | Writes | Maintains | Approves | Suggests questions |
| Configure services, calendar, payments | Sets up | Maintains | Approves |  |
| Testing and go-live sign-off | Runs tests |  | Signs off | Tests handover |
| Reply to handovers |  |  |  | Replies |
| Daily monitoring and weekly review |  | Does |  |  |
| Report price, hours or service changes |  | Applies | Sends | Sends |
| Monthly report and review call |  | Prepares and presents | Attends |  |
| Billing and payment | Sets up | Chases | Pays |  |
| Incidents |  | Leads | Is kept informed | Uses staff-only mode |
| Data deletion requests |  | Completes | Approves as controller | Passes on |

**Escalation inside OTBLABS:** operator, then the onboarding lead, then the OTBLABS director. A P1 that is still unfixed after 2 hours goes straight to the director.

## **Appendix**

### **A. Client intake questionnaire**

Send this before the kickoff call and complete it together on the call.

1. **Business:** registered name, the name customers use, branches, physical address with landmarks, Google Maps link, parking.  
2. **Hours:** opening hours per day, public holiday hours, and the hours staff can take handovers.  
3. **Services:** list every service with its description, duration, price or price range, and which staff member does it.  
4. **Bookings:** how far ahead customers can book, minimum notice, buffer time between appointments, cancellation cut-off.  
5. **Payments:** which services need a deposit and how much; till or paybill number; who administers it on the M-Pesa business portal.  
6. **Insurance and schemes (clinics):** insurers and SHA cover accepted, and what the customer must bring.  
7. **Fees (schools):** fee structure, deadlines, how parents get a balance, admissions steps and requirements.  
8. **Policies:** cancellation, lateness, refunds, children and companions, privacy.  
9. **Top 20 questions** customers ask today, with the answers staff give.  
10. **Always hand over:** topics that must always go to a person.  
11. **Emergencies:** what to tell a customer who describes an emergency, and the number to call.  
12. **Tone:** formal or friendly, greeting, words to avoid, English or Swahili first.  
13. **People:** names, roles and phone numbers of staff who will handle handovers; who receives the daily summary and weekly report.  
14. **Current setup:** the WhatsApp number to use, whether it is on the WhatsApp Business app, and the calendar the business uses.  
15. **Compliance:** ODPC registration status and the privacy notice link, if any.

### **B. Standard messages**

Adapt these to each client's tone. Template messages must be approved by Meta before use.

**Greeting, first message to a new customer**

*Hello, welcome to \[Business name\]. I'm the automated assistant and I can answer questions, book appointments and take payments. Type "person" at any time to talk to our team.*

**Booking confirmation**

*You're booked: \[Service\] with \[Staff\] on \[Day, date\] at \[Time\], at \[Branch\]. Reply 1 to reschedule or 2 to cancel.*

**Reminder (utility template)**

*Reminder: your appointment at \[Business name\] is tomorrow, \[date\] at \[time\]. Reply 1 to confirm, 2 to reschedule or 3 to cancel.*

**Payment request**

*A deposit of KES \[amount\] secures your booking. I've sent an M-Pesa request to \[phone\]; please enter your PIN to confirm.*

**Payment received (utility template)**

*Payment received: KES \[amount\], M-Pesa ref \[code\]. Your booking for \[date\] at \[time\] is confirmed.*

**Handover**

*I'm connecting you to our team now. Someone will reply shortly.*

**After-hours handover**

*Our team is away right now and will reply by \[time\] on \[day\]. Leave your message here and we'll get back to you.*

**Emergency**

*If this is an emergency, please call \[emergency number\] or go to the nearest hospital now. I'm alerting our team as well.*

**Swahili greeting**

*Habari, karibu \[Business name\]. Mimi ni msaidizi wa kiotomatiki. Naweza kujibu maswali, kupanga miadi na kupokea malipo. Andika "mtu" wakati wowote kuongea na timu yetu.*

[image1]: <data:image/png;base64,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>