package dev.ledger.mail.service;

import dev.ledger.mail.client.EmailGateway;
import dev.ledger.mail.domain.NotificationDecision;
import dev.ledger.mail.domain.NotificationDecision.Send;
import dev.ledger.mail.domain.NotificationDecision.Suppress;
import dev.ledger.mail.domain.PaymentEvent;
import dev.ledger.mail.domain.PaymentNotificationPolicy;

public final class PaymentNotificationService {
    private final PaymentNotificationPolicy policy;
    private final EmailGateway email;

    public PaymentNotificationService(PaymentNotificationPolicy policy, EmailGateway email) {
        this.policy = policy;
        this.email = email;
    }

    public AuditResult handle(PaymentEvent event) {
        NotificationDecision decision = policy.decide(event);
        if (decision instanceof Suppress suppressed) {
            return new AuditResult(event.eventId(), suppressed.auditReason(), null);
        }

        Send send = (Send) decision;
        String messageId = email.send(
                send.recipient(),
                send.template(),
                send.templateVars(),
                event.eventId() + ":send");
        return new AuditResult(event.eventId(), send.auditReason(), messageId);
    }

    public record AuditResult(String eventId, String decision, String messageId) {}
}
