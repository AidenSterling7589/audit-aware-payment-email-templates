package dev.ledger.mail.domain;

import java.util.Map;

public sealed interface NotificationDecision {
    record Send(String auditReason, String recipient, TemplateDefinition template,
                Map<String, String> templateVars) implements NotificationDecision {}

    record Suppress(String auditReason) implements NotificationDecision {}

    record TemplateDefinition(String key, String subject, String html) {}
}
