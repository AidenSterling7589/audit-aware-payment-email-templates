package dev.ledger.mail.client;

import dev.ledger.mail.domain.NotificationDecision.TemplateDefinition;

import java.util.Map;

public interface EmailGateway {
    String send(String to, TemplateDefinition template, Map<String, String> templateVars,
                String idempotencyKey);
}
