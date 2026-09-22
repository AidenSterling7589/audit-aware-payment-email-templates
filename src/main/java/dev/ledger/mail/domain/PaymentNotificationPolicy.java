package dev.ledger.mail.domain;

import dev.ledger.mail.domain.NotificationDecision.Send;
import dev.ledger.mail.domain.NotificationDecision.Suppress;
import dev.ledger.mail.domain.NotificationDecision.TemplateDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PaymentNotificationPolicy {
    private static final TemplateDefinition RECEIPT = new TemplateDefinition(
            "payment-receipt-v1",
            "Payment received: {{payment_reference}}",
            "<p>Hello {{customer_name}},</p><p>We received {{amount}} {{currency}}.</p>"
                    + "<p>Reference: {{payment_reference}}</p>");

    private static final TemplateDefinition RISK_REVIEW = new TemplateDefinition(
            "payment-risk-review-v1",
            "Payment review required: {{payment_reference}}",
            "<p>Payment {{payment_reference}} requires manual review.</p>"
                    + "<p>Event: {{event_id}}</p>");

    private final String complianceMailbox;

    public PaymentNotificationPolicy(String complianceMailbox) {
        this.complianceMailbox = complianceMailbox;
    }

    public NotificationDecision decide(PaymentEvent event) {
        return switch (event.status()) {
            case CAPTURED -> new Send(
                    "CAPTURED_CUSTOMER_RECEIPT",
                    event.customerEmail(),
                    RECEIPT,
                    orderedVars(event, true));
            case REVIEW_REQUIRED -> new Send(
                    "REVIEW_COMPLIANCE_ONLY",
                    complianceMailbox,
                    RISK_REVIEW,
                    orderedVars(event, false));
            case DECLINED -> new Suppress("DECLINED_NO_EMAIL");
        };
    }

    private static Map<String, String> orderedVars(PaymentEvent event, boolean includeCustomer) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("event_id", event.eventId());
        vars.put("payment_reference", event.paymentReference());
        vars.put("amount", event.amount());
        vars.put("currency", event.currency());
        if (includeCustomer) {
            vars.put("customer_name", event.customerName());
        }
        return Map.copyOf(vars);
    }
}
