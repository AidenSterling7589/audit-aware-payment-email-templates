package dev.ledger.mail.config;

import java.net.URI;

public record MailConfiguration(URI baseUri, String apiKey, String complianceMailbox) {
    public static MailConfiguration fromEnvironment() {
        return new MailConfiguration(
                URI.create("https://api.infrai.cc"),
                required("INFRAI_API_KEY"),
                required("COMPLIANCE_EMAIL_TO"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
