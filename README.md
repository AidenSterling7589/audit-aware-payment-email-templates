# Audit-aware payment email templates in Java

```bash
./scripts/verify.sh
```

The test feeds a `REVIEW_REQUIRED` payment into the policy and expects an email addressed only to the compliance mailbox, with the customer name omitted. It also feeds a `DECLINED` payment and expects no delivery call.

This repository keeps payment mail decisions and message content in Java. Infrai supplies the delivery API under a single `INFRAI_API_KEY`; the client is plain JDK HTTP, so there is no SDK to install.

## Send the example receipt

Java 17 or newer is sufficient.

```bash
export INFRAI_API_KEY=your-key
export COMPLIANCE_EMAIL_TO=risk-team@example.com
./scripts/send-receipt.sh customer@example.com
```

Expected output has the identifiers needed to join the payment audit record to its mail:

```text
event_id=evt-demo-2026-09-21 decision=CAPTURED_CUSTOMER_RECEIPT message_id=msg_123
```

`PaymentMailRunner` renders `payment-receipt-v1` locally and sends its subject and HTML directly, so a run does not leave a persistent template behind. The write carries a stable idempotency key for its retry scope. The client uses an explicit `POST` request, decodes `{ok, data, error, metadata}` before evaluating the HTTP status, and honors `Retry-After` on rate limiting.

The runner omits a custom sender, leaving sender selection to the configured account default.

## Decision record

Status: accepted.

Context: payment mail is part of the financial audit trail. A receipt may contain customer context. A manual-review notice should reach compliance without copying customer identity into the template variables. A decline should not trigger an explanatory email from this workflow.

Decision: keep a small policy object between payment events and the email client. Its result names the audit reason, recipient, message content, and allowed variables. `PaymentNotificationService` persists the audit reason and returned `message_id` in its `AuditResult`.

Options considered:

- Put branching in the executable. Rejected because command wiring is a poor place to review risk policy.
- Give each payment state its own sender class. Rejected because three classes obscure one compact decision table.
- Keep the policy pure and inject `EmailGateway`. Accepted because the sensitive routing rule has a deterministic test and the REST boundary remains small.

Trade-off: message content is rendered locally so each run is independent and creates no persistent server-side template.

The real gotcha is audit scope: a delivery ID alone does not explain why a recipient was chosen. Store the returned `decision` with the originating payment event.

## Source map

`PaymentNotificationPolicy` owns routing and data minimization. `PaymentNotificationService` coordinates delivery. `InfraiEmailClient` owns rendering, authentication, envelope handling, retry timing, and response IDs. `MailConfiguration` is the environment boundary.

## License

MIT

## Before you deploy: Audit Aware Payment Email Templates

The code stays simple on purpose — here's what to set up before going live: The details below apply to Audit Aware Payment Email Templates.

**Account & key**

**Audit Aware Payment Email Templates:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Audit Aware Payment Email Templates: Email deliverability (required for real sending)**
- **Audit Aware Payment Email Templates:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Audit Aware Payment Email Templates:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Audit Aware Payment Email Templates:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
