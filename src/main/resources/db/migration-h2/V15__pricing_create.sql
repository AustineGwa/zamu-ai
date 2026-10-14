-- H2 twin of db/migration/V15__pricing_create.sql, for the local h2 profile only.
CREATE TABLE pricing_plans (
    id            BINARY(16)    NOT NULL,
    code          VARCHAR(32)   NOT NULL,
    name          VARCHAR(60)   NOT NULL,
    tagline       VARCHAR(200)  NOT NULL,
    audience      VARCHAR(200)  NOT NULL,
    monthly_price DECIMAL(12,2) NOT NULL,
    currency      CHAR(3)       NOT NULL,
    price_note    VARCHAR(120)  NOT NULL,
    trial_days    INT           NOT NULL,
    highlights    CLOB          NOT NULL,
    cta_label     VARCHAR(60)   NOT NULL,
    badge         VARCHAR(60)   NULL,
    featured      BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order    INT           NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP(3)  NOT NULL,
    updated_at    TIMESTAMP(3)  NOT NULL,
    CONSTRAINT pk_pricing_plans PRIMARY KEY (id),
    CONSTRAINT uk_pricing_plans_code UNIQUE (code)
);

CREATE TABLE pricing_features (
    id          BINARY(16)   NOT NULL,
    group_name  VARCHAR(80)  NOT NULL,
    label       VARCHAR(160) NOT NULL,
    sort_order  INT          NOT NULL,
    CONSTRAINT pk_pricing_features PRIMARY KEY (id)
);

CREATE TABLE pricing_plan_features (
    plan_id     BINARY(16)  NOT NULL,
    feature_id  BINARY(16)  NOT NULL,
    feature_value VARCHAR(80) NOT NULL,
    CONSTRAINT pk_pricing_plan_features PRIMARY KEY (plan_id, feature_id),
    CONSTRAINT fk_pricing_plan_features_plan FOREIGN KEY (plan_id) REFERENCES pricing_plans (id),
    CONSTRAINT fk_pricing_plan_features_feature FOREIGN KEY (feature_id) REFERENCES pricing_features (id)
);

CREATE TABLE pricing_faqs (
    id          BINARY(16)   NOT NULL,
    question    VARCHAR(200) NOT NULL,
    answer      CLOB         NOT NULL,
    sort_order  INT          NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_pricing_faqs PRIMARY KEY (id)
);

INSERT INTO pricing_plans (id, code, name, tagline, audience, monthly_price, currency, price_note, trial_days,
                           highlights, cta_label, badge, featured, sort_order, active, created_at, updated_at) VALUES
(X'01a13935f58070018000000000000001', 'starter', 'Starter', 'Your first AI receptionist. Every WhatsApp message answered, day and night.', 'For small shops, solo practitioners and new businesses.', 3500.00, 'KES', 'per month', 7, 'AI receptionist on your own WhatsApp Business number, 24 hours a day
Replies in English and Swahili, in your business''s tone
Answers questions on prices, hours, location and services
Hands the chat to you the moment a customer asks for a person
Live inbox on your phone or laptop to reply yourself', 'Start your 1-week free trial', NULL, FALSE, 10, TRUE, '2026-10-14 07:00:00.000', '2026-10-14 07:00:00.000'),
(X'01a13935f58070028000000000000002', 'growth', 'Growth', 'One assistant for your WhatsApp and your phone line.', 'For busy front desks that take calls as well as chats.', 20000.00, 'KES', 'per month', 14, 'Everything in Starter
AI voice receptionist answers calls to your business line
Call summaries saved next to your WhatsApp chats
Calls transferred to your team when a caller needs a person
Shared inbox for a small team (limited staff logins)', 'Start your 2-week free trial', 'Popular', FALSE, 20, TRUE, '2026-10-14 07:00:00.000', '2026-10-14 07:00:00.000'),
(X'01a13935f58070038000000000000003', 'business', 'Business', 'Every Zamu feature, for teams that book, bill and grow.', 'For clinics, salons, schools and service businesses running at full speed.', 75000.00, 'KES', 'per month', 30, 'Everything in Growth, for your whole team
Bookings, reschedules and reminders synced to your calendar
M-Pesa deposits paid straight to your till or paybill
Handover hours, escalation alerts and an audit log of staff actions
Daily and weekly performance reports
Priority onboarding and support', 'Start your 1-month free trial', 'Everything included', TRUE, 30, TRUE, '2026-10-14 07:00:00.000', '2026-10-14 07:00:00.000'),
(X'01a13935f58070048000000000000004', 'enterprise', 'Enterprise', 'Zamu on your own infrastructure, with corporate-grade privacy.', 'For hospitals, banks, insurers and groups with strict data rules.', 120000.00, 'KES', 'per month', 30, 'Everything in Business
Installed on your own servers or private cloud (on-premises)
Customer conversations and records stay inside your network
Data processing agreement under Kenya''s Data Protection Act, 2019
Security review with your IT team before go-live
Named account manager and an agreed service level', 'Book your 1-month pilot', NULL, FALSE, 40, TRUE, '2026-10-14 07:00:00.000', '2026-10-14 07:00:00.000');

INSERT INTO pricing_features (id, group_name, label, sort_order) VALUES
(X'01a13935f58070058000000000000005', 'Channels', 'WhatsApp AI receptionist, 24 hours a day', 10),
(X'01a13935f58070068000000000000006', 'Channels', 'Voice receptionist on your business phone line', 20),
(X'01a13935f58070078000000000000007', 'Channels', 'English and Swahili', 30),
(X'01a13935f58070088000000000000008', 'Your team', 'Live shared inbox with handover to staff', 40),
(X'01a13935f58070098000000000000009', 'Your team', 'Staff logins', 50),
(X'01a13935f580700a800000000000000a', 'Your team', 'Handover hours and escalation alerts', 60),
(X'01a13935f580700b800000000000000b', 'Bookings and payments', 'Bookings, reschedules and reminders', 70),
(X'01a13935f580700c800000000000000c', 'Bookings and payments', 'Calendar sync', 80),
(X'01a13935f580700d800000000000000d', 'Bookings and payments', 'M-Pesa deposits to your till or paybill', 90),
(X'01a13935f580700e800000000000000e', 'Reports', 'Weekly summary', 100),
(X'01a13935f580700f800000000000000f', 'Reports', 'Daily reports and conversation insights', 110),
(X'01a13935f58070108000000000000010', 'Security and hosting', 'Hosting', 120),
(X'01a13935f58070118000000000000011', 'Security and hosting', 'Data stays inside your network', 130),
(X'01a13935f58070128000000000000012', 'Security and hosting', 'Audit log of staff actions', 140),
(X'01a13935f58070138000000000000013', 'Security and hosting', 'Data processing agreement', 150),
(X'01a13935f58070148000000000000014', 'Support', 'Onboarding', 160),
(X'01a13935f58070158000000000000015', 'Support', 'Support', 170);

INSERT INTO pricing_plan_features (plan_id, feature_id, feature_value) VALUES
(X'01a13935f58070018000000000000001', X'01a13935f58070058000000000000005', 'yes'),
(X'01a13935f58070028000000000000002', X'01a13935f58070058000000000000005', 'yes'),
(X'01a13935f58070038000000000000003', X'01a13935f58070058000000000000005', 'yes'),
(X'01a13935f58070048000000000000004', X'01a13935f58070058000000000000005', 'yes'),
(X'01a13935f58070018000000000000001', X'01a13935f58070068000000000000006', 'no'),
(X'01a13935f58070028000000000000002', X'01a13935f58070068000000000000006', 'yes'),
(X'01a13935f58070038000000000000003', X'01a13935f58070068000000000000006', 'yes'),
(X'01a13935f58070048000000000000004', X'01a13935f58070068000000000000006', 'yes'),
(X'01a13935f58070018000000000000001', X'01a13935f58070078000000000000007', 'yes'),
(X'01a13935f58070028000000000000002', X'01a13935f58070078000000000000007', 'yes'),
(X'01a13935f58070038000000000000003', X'01a13935f58070078000000000000007', 'yes'),
(X'01a13935f58070048000000000000004', X'01a13935f58070078000000000000007', 'yes'),
(X'01a13935f58070018000000000000001', X'01a13935f58070088000000000000008', 'yes'),
(X'01a13935f58070028000000000000002', X'01a13935f58070088000000000000008', 'yes'),
(X'01a13935f58070038000000000000003', X'01a13935f58070088000000000000008', 'yes'),
(X'01a13935f58070048000000000000004', X'01a13935f58070088000000000000008', 'yes'),
(X'01a13935f58070018000000000000001', X'01a13935f58070098000000000000009', 'Limited'),
(X'01a13935f58070028000000000000002', X'01a13935f58070098000000000000009', 'Limited'),
(X'01a13935f58070038000000000000003', X'01a13935f58070098000000000000009', 'Whole team'),
(X'01a13935f58070048000000000000004', X'01a13935f58070098000000000000009', 'Whole team'),
(X'01a13935f58070018000000000000001', X'01a13935f580700a800000000000000a', 'no'),
(X'01a13935f58070028000000000000002', X'01a13935f580700a800000000000000a', 'yes'),
(X'01a13935f58070038000000000000003', X'01a13935f580700a800000000000000a', 'yes'),
(X'01a13935f58070048000000000000004', X'01a13935f580700a800000000000000a', 'yes'),
(X'01a13935f58070018000000000000001', X'01a13935f580700b800000000000000b', 'no'),
(X'01a13935f58070028000000000000002', X'01a13935f580700b800000000000000b', 'no'),
(X'01a13935f58070038000000000000003', X'01a13935f580700b800000000000000b', 'yes'),
(X'01a13935f58070048000000000000004', X'01a13935f580700b800000000000000b', 'yes'),
(X'01a13935f58070018000000000000001', X'01a13935f580700c800000000000000c', 'no'),
(X'01a13935f58070028000000000000002', X'01a13935f580700c800000000000000c', 'no'),
(X'01a13935f58070038000000000000003', X'01a13935f580700c800000000000000c', 'yes'),
(X'01a13935f58070048000000000000004', X'01a13935f580700c800000000000000c', 'yes'),
(X'01a13935f58070018000000000000001', X'01a13935f580700d800000000000000d', 'no'),
(X'01a13935f58070028000000000000002', X'01a13935f580700d800000000000000d', 'no'),
(X'01a13935f58070038000000000000003', X'01a13935f580700d800000000000000d', 'yes'),
(X'01a13935f58070048000000000000004', X'01a13935f580700d800000000000000d', 'yes'),
(X'01a13935f58070018000000000000001', X'01a13935f580700e800000000000000e', 'yes'),
(X'01a13935f58070028000000000000002', X'01a13935f580700e800000000000000e', 'yes'),
(X'01a13935f58070038000000000000003', X'01a13935f580700e800000000000000e', 'yes'),
(X'01a13935f58070048000000000000004', X'01a13935f580700e800000000000000e', 'yes'),
(X'01a13935f58070018000000000000001', X'01a13935f580700f800000000000000f', 'no'),
(X'01a13935f58070028000000000000002', X'01a13935f580700f800000000000000f', 'no'),
(X'01a13935f58070038000000000000003', X'01a13935f580700f800000000000000f', 'yes'),
(X'01a13935f58070048000000000000004', X'01a13935f580700f800000000000000f', 'yes'),
(X'01a13935f58070018000000000000001', X'01a13935f58070108000000000000010', 'Zamu cloud'),
(X'01a13935f58070028000000000000002', X'01a13935f58070108000000000000010', 'Zamu cloud'),
(X'01a13935f58070038000000000000003', X'01a13935f58070108000000000000010', 'Zamu cloud'),
(X'01a13935f58070048000000000000004', X'01a13935f58070108000000000000010', 'Your servers or private cloud'),
(X'01a13935f58070018000000000000001', X'01a13935f58070118000000000000011', 'no'),
(X'01a13935f58070028000000000000002', X'01a13935f58070118000000000000011', 'no'),
(X'01a13935f58070038000000000000003', X'01a13935f58070118000000000000011', 'no'),
(X'01a13935f58070048000000000000004', X'01a13935f58070118000000000000011', 'yes'),
(X'01a13935f58070018000000000000001', X'01a13935f58070128000000000000012', 'no'),
(X'01a13935f58070028000000000000002', X'01a13935f58070128000000000000012', 'no'),
(X'01a13935f58070038000000000000003', X'01a13935f58070128000000000000012', 'yes'),
(X'01a13935f58070048000000000000004', X'01a13935f58070128000000000000012', 'yes'),
(X'01a13935f58070018000000000000001', X'01a13935f58070138000000000000013', 'Standard terms'),
(X'01a13935f58070028000000000000002', X'01a13935f58070138000000000000013', 'Standard terms'),
(X'01a13935f58070038000000000000003', X'01a13935f58070138000000000000013', 'Standard terms'),
(X'01a13935f58070048000000000000004', X'01a13935f58070138000000000000013', 'Custom agreement'),
(X'01a13935f58070018000000000000001', X'01a13935f58070148000000000000014', 'Guided setup'),
(X'01a13935f58070028000000000000002', X'01a13935f58070148000000000000014', 'Guided setup'),
(X'01a13935f58070038000000000000003', X'01a13935f58070148000000000000014', 'Priority onboarding'),
(X'01a13935f58070048000000000000004', X'01a13935f58070148000000000000014', 'Dedicated onboarding team'),
(X'01a13935f58070018000000000000001', X'01a13935f58070158000000000000015', 'WhatsApp and email'),
(X'01a13935f58070028000000000000002', X'01a13935f58070158000000000000015', 'WhatsApp and email'),
(X'01a13935f58070038000000000000003', X'01a13935f58070158000000000000015', 'Priority'),
(X'01a13935f58070048000000000000004', X'01a13935f58070158000000000000015', 'Named account manager and service level');

INSERT INTO pricing_faqs (id, question, answer, sort_order, active) VALUES
(X'01a13935f58070168000000000000016', 'How does the free trial work?', 'Sign up and our team sets Zamu up for you with your prices, hours and services. Your trial starts the day Zamu goes live, so you get the full trial period with real customers. No payment is needed to start.', 10, TRUE),
(X'01a13935f58070178000000000000017', 'What happens when the trial ends?', 'We share a summary of everything Zamu handled for you. If you continue, your plan starts at the monthly price shown. If not, we switch Zamu off and your number works exactly as it did before.', 20, TRUE),
(X'01a13935f58070188000000000000018', 'Can I change plans later?', 'Yes. Move up or down whenever your business needs it; the new price applies from your next month.', 30, TRUE),
(X'01a13935f58070198000000000000019', 'Which WhatsApp number does Zamu use?', 'Your own business number, connected through Meta''s official WhatsApp Business Platform. Customers keep messaging the number they already know.', 40, TRUE),
(X'01a13935f580701a800000000000001a', 'When is the voice receptionist available?', 'Voice is rolling out in phases to Growth, Business and Enterprise customers. Your onboarding team confirms the date for your line and connects Zamu to your existing phone system.', 50, TRUE),
(X'01a13935f580701b800000000000001b', 'How is our customer data protected?', 'Connections are encrypted, access keys are stored encrypted, and each business''s data is kept separate from every other. We process personal data under Kenya''s Data Protection Act, 2019. Enterprise keeps everything on your own infrastructure.', 60, TRUE),
(X'01a13935f580701c800000000000001c', 'What does on-premises hosting involve?', 'We install and run Zamu inside your data centre or private cloud, so conversations never leave your network. During a one-month pilot we agree the setup, security review and support terms with your IT team.', 70, TRUE);
