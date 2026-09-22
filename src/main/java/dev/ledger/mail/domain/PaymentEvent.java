package dev.ledger.mail.domain;

import java.util.Objects;

public record PaymentEvent(
        String eventId,
        Status status,
        String customerEmail,
        String customerName,
        String amount,
        String currency,
        String paymentReference) {

    public PaymentEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(customerEmail);
        Objects.requireNonNull(customerName);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(paymentReference);
    }

    public enum Status {
        CAPTURED,
        REVIEW_REQUIRED,
        DECLINED
    }
}
