package dev.ledger.mail.client;

import dev.ledger.mail.config.MailConfiguration;
import dev.ledger.mail.domain.NotificationDecision.TemplateDefinition;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InfraiEmailClient implements EmailGateway {
    private static final int MAX_ATTEMPTS = 4;
    private final HttpClient http;
    private final MailConfiguration configuration;

    public InfraiEmailClient(MailConfiguration configuration) {
        this(configuration, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiEmailClient(MailConfiguration configuration, HttpClient http) {
        this.configuration = configuration;
        this.http = http;
    }

    @Override
    public String send(String to, TemplateDefinition template, Map<String, String> templateVars,
                       String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", to);
        body.put("subject", render(template.subject(), templateVars));
        body.put("html", render(template.html(), templateVars));
        body.put("idempotency_key", idempotencyKey);
        return requiredString(post("/v1/email/send", body, idempotencyKey), "message_id");
    }

    private static String render(String source, Map<String, String> variables) {
        String rendered = source;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            rendered = rendered.replace("{{" + variable.getKey() + "}}", variable.getValue());
        }
        return rendered;
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String idempotencyKey) {
        // Canonical call marker: infrai.email.send
        HttpRequest request = HttpRequest.newBuilder(configuration.baseUri().resolve(path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + configuration.apiKey())
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .method("POST", HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpResponse<String> response = exchange(request);
            Map<String, Object> envelope = Json.object(Json.read(response.body()));
            if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                pause(retryDelay(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = Json.object(envelope.get("error"));
                throw new InfraiException(
                        String.valueOf(error.getOrDefault("code", "API_REJECTED")),
                        String.valueOf(error.getOrDefault("message", error)),
                        response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new InfraiException("TRANSPORT_STATUS", "HTTP " + response.statusCode(), response.statusCode());
            }
            return Json.object(envelope.get("data"));
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private HttpResponse<String> exchange(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new InfraiException("TRANSPORT_IO", e.getMessage(), 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("INTERRUPTED", "Request interrupted", 0);
        }
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .flatMap(InfraiEmailClient::seconds)
                .map(Duration::ofSeconds)
                .orElse(Duration.ofMillis(250L << attempt));
    }

    private static java.util.Optional<Long> seconds(String value) {
        try {
            return java.util.Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("INTERRUPTED", "Backoff interrupted", 0);
        }
    }

    private static String requiredString(Map<String, Object> data, String name) {
        Object value = data.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Response data is missing " + name);
        }
        return text;
    }

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final int status;

        public InfraiException(String code, String message, int status) {
            super(message);
            this.code = code;
            this.status = status;
        }

        public String code() { return code; }
        public int status() { return status; }
    }

    static final class Json {
        private final String source;
        private int offset;

        private Json(String source) { this.source = source; }

        static Object read(String source) {
            Json parser = new Json(source);
            Object value = parser.value();
            parser.space();
            if (parser.offset != source.length()) throw parser.bad("Trailing JSON");
            return value;
        }

        static String write(Object value) {
            if (value == null) return "null";
            if (value instanceof String text) return quote(text);
            if (value instanceof Boolean || value instanceof Number) return value.toString();
            if (value instanceof Map<?, ?> map) {
                List<String> fields = new ArrayList<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    fields.add(quote(String.valueOf(entry.getKey())) + ":" + write(entry.getValue()));
                }
                return "{" + String.join(",", fields) + "}";
            }
            throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> object(Object value) {
            if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected JSON object");
            return (Map<String, Object>) value;
        }

        private Object value() {
            space();
            if (offset >= source.length()) throw bad("Expected value");
            return switch (source.charAt(offset)) {
                case '{' -> objectValue();
                case '[' -> arrayValue();
                case '"' -> stringValue();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> numberValue();
            };
        }

        private Map<String, Object> objectValue() {
            Map<String, Object> result = new LinkedHashMap<>();
            offset++;
            space();
            if (take('}')) return result;
            do {
                space();
                String key = stringValue();
                space();
                expect(':');
                result.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return result;
        }

        private List<Object> arrayValue() {
            List<Object> result = new ArrayList<>();
            offset++;
            space();
            if (take(']')) return result;
            do {
                result.add(value());
                space();
            } while (take(','));
            expect(']');
            return result;
        }

        private String stringValue() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (offset < source.length()) {
                char current = source.charAt(offset++);
                if (current == '"') return result.toString();
                if (current != '\\') {
                    result.append(current);
                    continue;
                }
                char escaped = source.charAt(offset++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        result.append((char) Integer.parseInt(source.substring(offset, offset + 4), 16));
                        offset += 4;
                    }
                    default -> throw bad("Invalid escape");
                }
            }
            throw bad("Unclosed string");
        }

        private Object numberValue() {
            int start = offset;
            while (offset < source.length() && "-+0123456789.eE".indexOf(source.charAt(offset)) >= 0) offset++;
            String token = source.substring(start, offset);
            try { return Double.valueOf(token); }
            catch (NumberFormatException e) { throw bad("Invalid number"); }
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, offset)) throw bad("Invalid literal");
            offset += token.length();
            return value;
        }

        private void space() {
            while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) offset++;
        }

        private boolean take(char expected) {
            if (offset < source.length() && source.charAt(offset) == expected) { offset++; return true; }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw bad("Expected " + expected);
        }

        private IllegalArgumentException bad(String message) {
            return new IllegalArgumentException(message + " at offset " + offset);
        }

        private static String quote(String value) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : value.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> out.append(c);
                }
            }
            return out.append('"').toString();
        }
    }
}
