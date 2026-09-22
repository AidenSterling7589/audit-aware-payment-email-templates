package dev.ledger.mail;

import dev.ledger.mail.client.EmailGateway;
import dev.ledger.mail.domain.NotificationDecision.TemplateDefinition;
import dev.ledger.mail.domain.PaymentEvent;
import dev.ledger.mail.domain.PaymentNotificationPolicy;
import dev.ledger.mail.service.PaymentNotificationService;

import java.util.Map;

public final class PaymentNotificationPolicyTest {
    public static void main(String[] args) {
        reviewGoesOnlyToCompliance();
        declinedPaymentSendsNothing();
        System.out.println("PaymentNotificationPolicyTest passed");
    }

    private static void reviewGoesOnlyToCompliance() {
        RecordingGateway gateway = new RecordingGateway();
        PaymentNotificationService service = service(gateway);
        PaymentNotificationService.AuditResult result = service.handle(event(PaymentEvent.Status.REVIEW_REQUIRED));

        check("risk@example.test".equals(gateway.recipient), "review recipient must be compliance");
        check(!gateway.vars.containsKey("customer_name"), "risk notice must omit customer name");
        check(gateway.sendCount == 1, "review event must make one delivery call");
        check("REVIEW_COMPLIANCE_ONLY".equals(result.decision()), "audit decision must explain routing");
    }

    private static void declinedPaymentSendsNothing() {
        RecordingGateway gateway = new RecordingGateway();
        PaymentNotificationService.AuditResult result = service(gateway).handle(event(PaymentEvent.Status.DECLINED));

        check(gateway.sendCount == 0, "declined event must not send mail");
        check("DECLINED_NO_EMAIL".equals(result.decision()), "suppression must be auditable");
    }

    private static PaymentNotificationService service(RecordingGateway gateway) {
        return new PaymentNotificationService(new PaymentNotificationPolicy("risk@example.test"), gateway);
    }

    private static PaymentEvent event(PaymentEvent.Status status) {
        return new PaymentEvent("evt-17", status, "customer@example.test", "A. Customer",
                "125.00", "USD", "pay-17");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingGateway implements EmailGateway {
        private int sendCount;
        private String recipient;
        private Map<String, String> vars = Map.of();

        @Override
        public String send(String to, TemplateDefinition template, Map<String, String> templateVars,
                           String idempotencyKey) {
            sendCount++;
            recipient = to;
            vars = templateVars;
            return "message-test";
        }
    }
}
