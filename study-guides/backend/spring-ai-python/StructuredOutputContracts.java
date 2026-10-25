import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dependency-free contracts for strict structured-output acceptance. */
public final class StructuredOutputContracts {
    private static final Set<String> FIELDS = Set.of("service", "severity", "summary", "evidence_ids");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final int MAX_BYTES = 2_048;
    private static final int MAX_SUMMARY = 240;
    private static final int MAX_EVIDENCE = 8;

    enum Status { ACCEPTED, SYNTAX_INVALID, SCHEMA_INVALID, BUSINESS_INVALID, EXHAUSTED }

    record IncidentSummary(String service, String severity, String summary, List<String> evidenceIds) {
        IncidentSummary {
            evidenceIds = List.copyOf(evidenceIds);
        }
    }

    record Outcome(Status status, String reason, int attempts, IncidentSummary value) {}

    static Outcome parseAndValidate(String raw, Set<String> allowedServices, Set<String> allowedEvidence) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return new Outcome(Status.SYNTAX_INVALID, "response exceeds byte limit", 1, null);
        }

        final Map<String, Object> object;
        try {
            object = new StrictParser(raw).parseObjectDocument();
        } catch (SchemaFailure failure) {
            return new Outcome(Status.SCHEMA_INVALID, failure.getMessage(), 1, null);
        } catch (RuntimeException failure) {
            return new Outcome(Status.SYNTAX_INVALID, failure.getMessage(), 1, null);
        }

        if (!object.keySet().equals(FIELDS)) {
            return new Outcome(Status.SCHEMA_INVALID, "required/unknown fields", 1, null);
        }
        if (!(object.get("service") instanceof String service)
                || !(object.get("severity") instanceof String severity)
                || !(object.get("summary") instanceof String summary)) {
            return new Outcome(Status.SCHEMA_INVALID, "string field type", 1, null);
        }
        if (!(object.get("evidence_ids") instanceof List<?> rawEvidence)
                || rawEvidence.stream().anyMatch(item -> !(item instanceof String))) {
            return new Outcome(Status.SCHEMA_INVALID, "evidence_ids must be strings", 1, null);
        }
        List<String> evidence = rawEvidence.stream().map(String.class::cast).toList();

        if (service.isEmpty() || service.length() > 64 || !SEVERITIES.contains(severity)) {
            return new Outcome(Status.SCHEMA_INVALID, "service/severity constraint", 1, null);
        }
        if (summary.isEmpty() || summary.length() > MAX_SUMMARY || !summary.equals(summary.strip())) {
            return new Outcome(Status.SCHEMA_INVALID, "summary constraint", 1, null);
        }
        if (evidence.isEmpty() || evidence.size() > MAX_EVIDENCE
                || new HashSet<>(evidence).size() != evidence.size()) {
            return new Outcome(Status.SCHEMA_INVALID, "evidence cardinality/uniqueness", 1, null);
        }
        if (evidence.stream().anyMatch(id -> id.isEmpty() || id.length() > 64)) {
            return new Outcome(Status.SCHEMA_INVALID, "evidence ID constraint", 1, null);
        }

        IncidentSummary candidate = new IncidentSummary(service, severity, summary, evidence);
        if (!allowedServices.contains(service)) {
            return new Outcome(Status.BUSINESS_INVALID, "service outside trusted scope", 1, null);
        }
        if (!allowedEvidence.containsAll(evidence)) {
            return new Outcome(Status.BUSINESS_INVALID, "evidence outside supplied set", 1, null);
        }
        return new Outcome(Status.ACCEPTED, "accepted", 1, candidate);
    }

    static Outcome validateWithRepair(
            List<String> candidates,
            Set<String> services,
            Set<String> evidence,
            int maxAttempts,
            int budgetMs,
            int attemptCostMs,
            int reserveMs) {
        if (maxAttempts < 1 || budgetMs < 0 || attemptCostMs < 1 || reserveMs < 0) {
            throw new IllegalArgumentException("invalid repair policy");
        }
        Outcome last = null;
        int used = 0;
        for (String candidate : candidates) {
            if (used >= maxAttempts || budgetMs - used * attemptCostMs < attemptCostMs + reserveMs) break;
            used++;
            last = parseAndValidate(candidate, services, evidence);
            if (last.status() == Status.ACCEPTED) {
                return new Outcome(last.status(), last.reason(), used, last.value());
            }
        }
        return new Outcome(Status.EXHAUSTED,
                last == null ? "no valid fully revalidated output" : last.reason(), used, null);
    }

    public static void main(String[] args) {
        Set<String> services = Set.of("payments");
        Set<String> evidence = Set.of("evt-17", "log-22");
        String valid = json("payments", "HIGH", "Payment errors rose after deploy.", List.of("evt-17", "log-22"));
        int scenarios = 0;

        expect(Status.ACCEPTED, valid, services, evidence); scenarios++;
        expect(Status.SYNTAX_INVALID, "{", services, evidence); scenarios++;
        expect(Status.SYNTAX_INVALID, valid.substring(0, valid.length() - 1) + ",\"service\":\"x\"}", services, evidence); scenarios++;
        expect(Status.SYNTAX_INVALID, "x".repeat(MAX_BYTES + 1), services, evidence); scenarios++;
        expect(Status.SYNTAX_INVALID, valid + " trailing", services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, "[]", services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, "{}", services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, valid.substring(0, valid.length() - 1) + ",\"auto_resolve\":true}", services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, json("", "HIGH", "x", List.of("evt-17")), services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, json("x".repeat(65), "HIGH", "x", List.of("evt-17")), services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, json("payments", "URGENT", "x", List.of("evt-17")), services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, json("payments", "HIGH", "", List.of("evt-17")), services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, json("payments", "HIGH", " x", List.of("evt-17")), services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, json("payments", "HIGH", "x".repeat(241), List.of("evt-17")), services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, json("payments", "HIGH", "x", List.of()), services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, json("payments", "HIGH", "x", List.of("evt-17", "evt-17")), services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, json("payments", "HIGH", "x", List.of("0","1","2","3","4","5","6","7","8")), services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, json("payments", "HIGH", "x", List.of("")), services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, json("payments", "HIGH", "x", List.of("x".repeat(65))), services, evidence); scenarios++;
        expect(Status.BUSINESS_INVALID, json("orders", "HIGH", "x", List.of("evt-17")), services, evidence); scenarios++;
        expect(Status.BUSINESS_INVALID, json("payments", "HIGH", "x", List.of("foreign")), services, evidence); scenarios++;
        expect(Status.ACCEPTED, json("payments", "LOW", "x", List.of("evt-17")), services, evidence); scenarios++;
        expect(Status.ACCEPTED, json("payments", "MEDIUM", "x", List.of("evt-17")), services, evidence); scenarios++;
        expect(Status.ACCEPTED, json("payments", "CRITICAL", "x", List.of("evt-17")), services, evidence); scenarios++;
        expectRepair(Status.ACCEPTED, List.of("{", valid), services, evidence, 2, 60, 2); scenarios++;
        expectRepair(Status.EXHAUSTED, List.of("{", valid), services, evidence, 1, 60, 1); scenarios++;
        expectRepair(Status.EXHAUSTED, List.of("{", valid), services, evidence, 2, 25, 0); scenarios++;
        expectRepair(Status.EXHAUSTED, List.of(), services, evidence, 2, 60, 0); scenarios++;
        expectRepair(Status.EXHAUSTED, List.of(json("orders", "HIGH", "x", List.of("evt-17"))), services, evidence, 2, 60, 1); scenarios++;
        expectPolicyError(0, 60); scenarios++;
        expectPolicyError(2, -1); scenarios++;
        expect(Status.SCHEMA_INVALID, "{\"service\":1,\"severity\":\"HIGH\",\"summary\":\"x\",\"evidence_ids\":[\"evt-17\"]}", services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, "{\"service\":\"payments\",\"severity\":\"HIGH\",\"summary\":\"x\",\"evidence_ids\":\"evt-17\"}", services, evidence); scenarios++;
        expect(Status.SCHEMA_INVALID, "{\"service\":\"payments\",\"severity\":\"HIGH\",\"summary\":\"x\",\"evidence_ids\":[1]}", services, evidence); scenarios++;
        expect(Status.ACCEPTED, json("payments", "HIGH", "x", List.of("log-22", "evt-17")), services, evidence); scenarios++;
        expect(Status.BUSINESS_INVALID, json("payments", "HIGH", "x", List.of("evt-17", "foreign")), services, evidence); scenarios++;

        long state = 0x5EEDL;
        int accepted = 0;
        for (int i = 0; i < 10_000; i++) {
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            String service = ((state >>> 16) & 1L) == 1L ? "payments" : "orders";
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            String cited = ((state >>> 8) % 3L) == 0L ? "foreign" : "evt-17";
            Outcome outcome = parseAndValidate(json(service, "HIGH", "x", List.of(cited)), services, evidence);
            boolean expected = service.equals("payments") && cited.equals("evt-17");
            check((outcome.status() == Status.ACCEPTED) == expected, "seeded acceptance mismatch");
            if (expected) accepted++;
        }

        System.out.println("PARITY|ACCEPTED|SYNTAX_INVALID|SCHEMA_INVALID|BUSINESS_INVALID|EXHAUSTED|" + accepted);
        System.out.println("Java structured-output contracts passed: " + scenarios + " scenario groups + 10000 seeded trials");
    }

    private static void expect(Status expected, String raw, Set<String> services, Set<String> evidence) {
        check(parseAndValidate(raw, services, evidence).status() == expected, "expected " + expected);
    }

    private static void expectRepair(Status expected, List<String> raws, Set<String> services,
            Set<String> evidence, int max, int budget, int attempts) {
        Outcome outcome = validateWithRepair(raws, services, evidence, max, budget, 20, 10);
        check(outcome.status() == expected && outcome.attempts() == attempts, "repair outcome");
    }

    private static void expectPolicyError(int max, int budget) {
        try {
            validateWithRepair(List.of(), Set.of("payments"), Set.of("evt-17"), max, budget, 20, 10);
            throw new AssertionError("invalid policy accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static String json(String service, String severity, String summary, List<String> evidence) {
        return "{\"service\":\"" + escape(service) + "\",\"severity\":\"" + escape(severity)
                + "\",\"summary\":\"" + escape(summary) + "\",\"evidence_ids\":["
                + evidence.stream().map(value -> "\"" + escape(value) + "\"").reduce((a, b) -> a + "," + b).orElse("")
                + "]}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class SchemaFailure extends RuntimeException {
        SchemaFailure(String message) { super(message); }
    }

    /** Strict parser for this intentionally flat teaching schema. */
    private static final class StrictParser {
        private final String text;
        private int at;

        StrictParser(String text) { this.text = text; }

        Map<String, Object> parseObjectDocument() {
            whitespace();
            if (peek('[')) throw new SchemaFailure("root must be an object");
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (take('}')) {
                finish();
                return result;
            }
            while (true) {
                String key = string();
                if (result.containsKey(key)) fail("duplicate field: " + key);
                whitespace();
                expect(':');
                result.put(key, value());
                whitespace();
                if (take('}')) break;
                expect(',');
            }
            finish();
            return result;
        }

        private Object value() {
            whitespace();
            if (peek('"')) return string();
            if (peek('[')) return array();
            int start = at;
            while (at < text.length() && ",}] \t\r\n".indexOf(text.charAt(at)) < 0) at++;
            if (start == at) fail("value expected");
            String token = text.substring(start, at);
            if (token.equals("true")) return Boolean.TRUE;
            if (token.equals("false")) return Boolean.FALSE;
            if (token.equals("null")) return null;
            try {
                return token.contains(".") || token.contains("e") || token.contains("E")
                        ? Double.valueOf(token) : Long.valueOf(token);
            } catch (NumberFormatException failure) {
                fail("invalid value");
                return null;
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> result = new ArrayList<>();
            whitespace();
            if (take(']')) return result;
            while (true) {
                result.add(value());
                whitespace();
                if (take(']')) return result;
                expect(',');
            }
        }

        private String string() {
            whitespace();
            expect('"');
            StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char ch = text.charAt(at++);
                if (ch == '"') return out.toString();
                if (ch < 0x20) fail("control character in string");
                if (ch != '\\') {
                    out.append(ch);
                    continue;
                }
                if (at >= text.length()) fail("incomplete escape");
                char escaped = text.charAt(at++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (at + 4 > text.length()) fail("incomplete unicode escape");
                        try {
                            out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                        } catch (NumberFormatException failure) {
                            fail("invalid unicode escape");
                        }
                        at += 4;
                    }
                    default -> fail("invalid escape");
                }
            }
            fail("unterminated string");
            return null;
        }

        private void finish() {
            whitespace();
            if (at != text.length()) fail("trailing content");
        }

        private void whitespace() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
        }

        private boolean peek(char wanted) {
            whitespace();
            return at < text.length() && text.charAt(at) == wanted;
        }

        private boolean take(char wanted) {
            whitespace();
            if (at < text.length() && text.charAt(at) == wanted) {
                at++;
                return true;
            }
            return false;
        }

        private void expect(char wanted) {
            if (!take(wanted)) fail("expected " + wanted);
        }

        private void fail(String message) {
            throw new IllegalArgumentException(message + " at " + at);
        }
    }
}
