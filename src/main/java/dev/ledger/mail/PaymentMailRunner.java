package dev.ledger.mail;

import dev.ledger.mail.client.InfraiEmailClient;
import dev.ledger.mail.config.MailConfiguration;
import dev.ledger.mail.domain.PaymentEvent;
import dev.ledger.mail.domain.PaymentNotificationPolicy;
import dev.ledger.mail.service.PaymentNotificationService;

public final class PaymentMailRunner {
    private PaymentMailRunner() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: PaymentMailRunner <customer-email>");
        }
        MailConfiguration config = MailConfiguration.fromEnvironment();
        PaymentNotificationService service = new PaymentNotificationService(
                new PaymentNotificationPolicy(config.complianceMailbox()),
                new InfraiEmailClient(config));

        PaymentEvent event = new PaymentEvent(
                "evt-demo-2026-09-21",
                PaymentEvent.Status.CAPTURED,
                args[0],
                "Ada",
                "42.00",
                "USD",
                "pay-demo-1042");

        PaymentNotificationService.AuditResult result = service.handle(event);
        System.out.printf("event_id=%s decision=%s message_id=%s%n",
                result.eventId(), result.decision(), result.messageId());
    }
}
