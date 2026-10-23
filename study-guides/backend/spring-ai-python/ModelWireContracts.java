import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Dependency-free transport, retry, validation, and capacity contracts for one model call. */
public final class ModelWireContracts {
    private static final long MAX_BODY_BYTES = 1_048_576;
    private static final Set<String> JSON_TYPES = Set.of("application/json", "application/problem+json");

    enum Stage { BEFORE_SEND, RESPONSE, READ_TIMEOUT_AFTER_SEND }
    enum Decision { RETRY, DO_NOT_RETRY, FAIL }
    enum Outcome { ACCEPTED, INCOMPLETE }

    record Budget(long totalMs, long poolMs, long connectMs, long writeMs, long readMs) {
        Budget {
            if (totalMs <= 0 || poolMs <= 0 || connectMs <= 0 || writeMs <= 0 || readMs <= 0) {
                throw new IllegalArgumentException("all timeout budgets must be positive");
            }
            long phases = Math.addExact(Math.addExact(poolMs, connectMs), Math.addExact(writeMs, readMs));
            if (phases > totalMs) throw new IllegalArgumentException("phase budgets exceed total deadline");
        }
    }

    record RequestSpec(URI baseUri, String model, int maxOutputTokens, boolean hasTools, String requestId) {
        RequestSpec {
            validateBaseUri(baseUri);
            if (model == null || model.isBlank()) throw new IllegalArgumentException("model is required");
            if (maxOutputTokens < 1 || maxOutputTokens > 4096) {
                throw new IllegalArgumentException("max output tokens must be in [1, 4096]");
            }
            if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{8,64}")) {
                throw new IllegalArgumentException("stable request ID is required");
            }
        }
    }

    record Attempt(Stage stage, int status, long retryAfterMs, boolean bytesSent) {}
    record RetryResult(Decision decision, long delayMs, String reason) {}

    record ProviderResponse(
            int status,
            String contentType,
            long bodyBytes,
            boolean done,
            String doneReason,
            String content,
            int promptTokens,
            int outputTokens,
            long totalDurationNanos,
            String model) {}

    record Validated(Outcome outcome, int totalTokens, long estimatedMicrodollars) {}

    static void validateBaseUri(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("base URI must be an origin without credentials, query, or fragment");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean loopback = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
        if (!(scheme.equals("https") || (scheme.equals("http") && loopback))) {
            throw new IllegalArgumentException("remote model endpoints require HTTPS");
        }
    }

    static RetryResult retry(Attempt observation, int attempt, int maxAttempts,
                             boolean pureInference, boolean allowUnknownOutcomeReplay) {
        if (attempt < 1 || maxAttempts < 1) throw new IllegalArgumentException("attempt counts must be positive");
        if (attempt >= maxAttempts) return new RetryResult(Decision.DO_NOT_RETRY, 0, "attempt cap reached");

        if (observation.stage() == Stage.BEFORE_SEND && !observation.bytesSent()) {
            return new RetryResult(Decision.RETRY, backoff(attempt, observation.retryAfterMs()), "request not sent");
        }
        if (observation.stage() == Stage.RESPONSE
                && (observation.status() == 429 || observation.status() == 503)) {
            if (!pureInference) return new RetryResult(Decision.DO_NOT_RETRY, 0, "operation may have effects");
            return new RetryResult(Decision.RETRY, backoff(attempt, observation.retryAfterMs()), "transient response");
        }
        if (observation.stage() == Stage.READ_TIMEOUT_AFTER_SEND) {
            if (pureInference && allowUnknownOutcomeReplay) {
                return new RetryResult(Decision.RETRY, backoff(attempt, observation.retryAfterMs()),
                        "explicitly accepted duplicate cost and nondeterminism");
            }
            return new RetryResult(Decision.DO_NOT_RETRY, 0, "outcome unknown after send");
        }
        return new RetryResult(Decision.FAIL, 0, "non-retryable response");
    }

    private static long backoff(int attempt, long retryAfterMs) {
        if (retryAfterMs < 0) throw new IllegalArgumentException("Retry-After cannot be negative");
        long exponential = Math.min(8_000L, 250L << Math.min(attempt - 1, 5));
        return Math.max(retryAfterMs, exponential); // caller adds bounded jitter before sleeping
    }

    static Validated validate(ProviderResponse response, String expectedModel,
                              long inputRatePerMillion, long outputRatePerMillion) {
        if (response.status() != 200) throw new IllegalArgumentException("expected HTTP 200");
        String mediaType = response.contentType() == null
                ? "" : response.contentType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!JSON_TYPES.contains(mediaType)) throw new IllegalArgumentException("JSON content type required");
        if (response.bodyBytes() < 1 || response.bodyBytes() > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("response body size outside bounds");
        }
        if (!response.done()) throw new IllegalArgumentException("complete response required");
        if (response.content() == null || response.content().isBlank()) {
            throw new IllegalArgumentException("non-empty model content required");
        }
        if (!expectedModel.equals(response.model())) throw new IllegalArgumentException("unexpected model identity");
        if (response.promptTokens() < 0 || response.outputTokens() < 0 || response.totalDurationNanos() < 0) {
            throw new IllegalArgumentException("usage and duration must be nonnegative");
        }
        if (inputRatePerMillion < 0 || outputRatePerMillion < 0) {
            throw new IllegalArgumentException("rates must be nonnegative");
        }
        Outcome outcome = switch (response.doneReason()) {
            case "stop" -> Outcome.ACCEPTED;
            case "length" -> Outcome.INCOMPLETE;
            default -> throw new IllegalArgumentException("unknown completion reason");
        };
        long inputCost = ceilPerMillion(response.promptTokens(), inputRatePerMillion);
        long outputCost = ceilPerMillion(response.outputTokens(), outputRatePerMillion);
        return new Validated(outcome, Math.addExact(response.promptTokens(), response.outputTokens()),
                Math.addExact(inputCost, outputCost));
    }

    private static long ceilPerMillion(long tokens, long rate) {
        long numerator = Math.addExact(Math.multiplyExact(tokens, rate), 999_999L);
        return numerator / 1_000_000L;
    }

    static int requiredInFlight(double requestsPerSecond, double latencySeconds) {
        if (!Double.isFinite(requestsPerSecond) || !Double.isFinite(latencySeconds)
                || requestsPerSecond <= 0 || latencySeconds <= 0) {
            throw new IllegalArgumentException("positive finite rate and latency required");
        }
        double required = Math.ceil(requestsPerSecond * latencySeconds);
        if (required > Integer.MAX_VALUE) throw new IllegalArgumentException("capacity exceeds integer range");
        return (int) required;
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | ArithmeticException expected) {
            return;
        }
        throw new AssertionError("expected rejection");
    }

    public static void main(String[] args) {
        var budget = new Budget(20_000, 250, 2_000, 1_000, 16_750);
        assert budget.readMs() == 16_750;
        var request = new RequestSpec(URI.create("http://127.0.0.1:11434"), "qwen3:8b", 120, false,
                "req_01HZX9A2");
        assert request.baseUri().getPort() == 11434;
        new RequestSpec(URI.create("https://models.example.com"), "qwen3:8b", 120, false, "req_01HZX9A3");
        rejects(() -> new Budget(19_999, 250, 2_000, 1_000, 16_750));
        rejects(() -> new Budget(20_000, 0, 2_000, 1_000, 17_000));
        rejects(() -> new RequestSpec(URI.create("http://models.example.com"), "qwen3:8b", 120, false,
                "req_01HZX9A4"));
        rejects(() -> new RequestSpec(URI.create("https://user@models.example.com"), "qwen3:8b", 120,
                false, "req_01HZX9A5"));
        rejects(() -> new RequestSpec(URI.create("https://models.example.com?token=x"), "qwen3:8b", 120,
                false, "req_01HZX9A6"));
        rejects(() -> new RequestSpec(URI.create("https://models.example.com"), " ", 120, false,
                "req_01HZX9A7"));

        var beforeSend = retry(new Attempt(Stage.BEFORE_SEND, 0, 0, false), 1, 3, false, false);
        assert beforeSend.equals(new RetryResult(Decision.RETRY, 250, "request not sent"));
        var throttled = retry(new Attempt(Stage.RESPONSE, 429, 1_000, true), 1, 3, true, false);
        assert throttled.equals(new RetryResult(Decision.RETRY, 1_000, "transient response"));
        assert retry(new Attempt(Stage.RESPONSE, 503, 0, true), 1, 3, false, false).decision()
                == Decision.DO_NOT_RETRY;
        assert retry(new Attempt(Stage.READ_TIMEOUT_AFTER_SEND, 0, 0, true), 1, 3, true, false).decision()
                == Decision.DO_NOT_RETRY;
        assert retry(new Attempt(Stage.READ_TIMEOUT_AFTER_SEND, 0, 0, true), 1, 3, true, true).decision()
                == Decision.RETRY;
        assert retry(new Attempt(Stage.RESPONSE, 500, 0, true), 1, 3, true, true).decision() == Decision.FAIL;
        assert retry(new Attempt(Stage.RESPONSE, 429, 5_000, true), 3, 3, true, true).decision()
                == Decision.DO_NOT_RETRY;
        rejects(() -> retry(new Attempt(Stage.RESPONSE, 429, -1, true), 1, 3, true, true));

        var acceptedResponse = new ProviderResponse(200, "application/json; charset=utf-8", 640, true, "stop",
                "billing", 900, 100, 1_850_000_000L, "qwen3:8b");
        var accepted = validate(acceptedResponse, "qwen3:8b", 2_000_000, 8_000_000);
        assert accepted.equals(new Validated(Outcome.ACCEPTED, 1_000, 2_600));
        var incomplete = validate(new ProviderResponse(200, "application/json", 640, true, "length",
                        "partial", 900, 120, 1_900_000_000L, "qwen3:8b"),
                "qwen3:8b", 2_000_000, 8_000_000);
        assert incomplete.outcome() == Outcome.INCOMPLETE;
        rejects(() -> validate(new ProviderResponse(500, "application/json", 10, true, "stop", "x", 1, 1, 1,
                "qwen3:8b"), "qwen3:8b", 1, 1));
        rejects(() -> validate(new ProviderResponse(200, "text/html", 10, true, "stop", "x", 1, 1, 1,
                "qwen3:8b"), "qwen3:8b", 1, 1));
        rejects(() -> validate(new ProviderResponse(200, "application/json", MAX_BODY_BYTES + 1, true, "stop", "x",
                1, 1, 1, "qwen3:8b"), "qwen3:8b", 1, 1));
        rejects(() -> validate(new ProviderResponse(200, "application/json", 10, false, "stop", "x", 1, 1, 1,
                "qwen3:8b"), "qwen3:8b", 1, 1));
        rejects(() -> validate(new ProviderResponse(200, "application/json", 10, true, "stop", " ", 1, 1, 1,
                "qwen3:8b"), "qwen3:8b", 1, 1));
        rejects(() -> validate(new ProviderResponse(200, "application/json", 10, true, "stop", "x", 1, 1, 1,
                "other"), "qwen3:8b", 1, 1));
        rejects(() -> validate(new ProviderResponse(200, "application/json", 10, true, "safety", "x", 1, 1, 1,
                "qwen3:8b"), "qwen3:8b", 1, 1));
        rejects(() -> validate(new ProviderResponse(200, "application/json", 10, true, "stop", "x", -1, 1, 1,
                "qwen3:8b"), "qwen3:8b", 1, 1));

        int inFlight = requiredInFlight(50, 8);
        assert inFlight == 400;
        assert requiredInFlight(0.1, 0.1) == 1;
        rejects(() -> requiredInFlight(0, 8));
        rejects(() -> requiredInFlight(Double.NaN, 8));

        System.out.println("PARITY|RETRY_1000|ACCEPTED|INCOMPLETE|400|2600");
        System.out.println("ModelWireContracts: 30 scenario groups passed");
    }
}
