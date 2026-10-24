import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Dependency-free contracts for generation-safe, scoped durable retrieval. */
public final class DurableRetrievalContracts {
    enum State { BUILDING, READY, ACTIVE, RETIRED }
    record Generation(String id, long version, State state, int dimensions, long expectedChunks) {}
    record Chunk(String tenant, String generation, String id, int revision, double[] vector) {}
    record Ranked(String id, double score) {}
    record QueryPlan(String sql, List<Object> parameters, int statementTimeoutMs) {}

    static int scenarioChecks;

    static void check(boolean condition) {
        if (!condition) throw new AssertionError("scenario check failed");
        scenarioChecks++;
    }

    static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException expected) {
            scenarioChecks++;
            return;
        }
        throw new AssertionError("expected rejection");
    }

    static Generation promote(Generation current, Generation candidate, long actualChunks) {
        if (candidate.state() != State.READY || candidate.version() <= current.version()
                || candidate.dimensions() < 1 || actualChunks != candidate.expectedChunks()) {
            throw new IllegalArgumentException("candidate is not promotable");
        }
        return new Generation(candidate.id(), candidate.version(), State.ACTIVE,
                candidate.dimensions(), candidate.expectedChunks());
    }

    static String vectorLiteral(double[] vector, int dimensions) {
        if (vector.length != dimensions || dimensions < 1) throw new IllegalArgumentException("dimensions");
        boolean nonZero = false;
        var parts = new ArrayList<String>();
        for (double value : vector) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite coordinate");
            nonZero |= value != 0.0;
            parts.add(String.format(Locale.ROOT, "%.9g", value));
        }
        if (!nonZero) throw new IllegalArgumentException("zero vector");
        return "[" + String.join(",", parts) + "]";
    }

    static QueryPlan buildPlan(double[] vector, int dimensions, String trustedTenant,
                               String activeGeneration, int limit, int statementTimeoutMs) {
        if (trustedTenant == null || trustedTenant.isBlank()
                || activeGeneration == null || activeGeneration.isBlank()
                || limit < 1 || limit > 50 || statementTimeoutMs < 1 || statementTimeoutMs > 5_000) {
            throw new IllegalArgumentException("invalid query bounds");
        }
        String sql = "SELECT id, revision, embedding <=> ?::vector AS distance "
                + "FROM rag_chunk WHERE tenant_id = ? AND generation = ? "
                + "ORDER BY distance, id, revision LIMIT ?";
        return new QueryPlan(sql,
                List.of(vectorLiteral(vector, dimensions), trustedTenant, activeGeneration, limit),
                statementTimeoutMs);
    }

    static double cosine(double[] left, double[] right) {
        if (left.length == 0 || left.length != right.length) throw new IllegalArgumentException("dimensions");
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            if (!Double.isFinite(left[i]) || !Double.isFinite(right[i])) {
                throw new IllegalArgumentException("non-finite coordinate");
            }
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) throw new IllegalArgumentException("zero vector");
        return dot / Math.sqrt(leftNorm * rightNorm);
    }

    static List<Ranked> exactSearch(List<Chunk> rows, double[] query, String trustedTenant,
                                    String activeGeneration, int limit) {
        if (trustedTenant == null || activeGeneration == null || limit < 1 || limit > 50) {
            throw new IllegalArgumentException("invalid search");
        }
        var ranked = new ArrayList<Ranked>();
        for (Chunk row : rows) {
            if (row.tenant().equals(trustedTenant) && row.generation().equals(activeGeneration)) {
                ranked.add(new Ranked(row.id(), cosine(query, row.vector())));
            }
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed().thenComparing(Ranked::id));
        return List.copyOf(ranked.subList(0, Math.min(limit, ranked.size())));
    }

    static double recallAtK(List<Ranked> exact, List<String> approximate, int k) {
        if (k < 1 || exact.isEmpty()) throw new IllegalArgumentException("baseline required");
        int denominator = Math.min(k, exact.size());
        Set<String> expected = new HashSet<>();
        exact.stream().limit(denominator).forEach(item -> expected.add(item.id()));
        Set<String> distinctApproximate = new HashSet<>(approximate.subList(0, Math.min(k, approximate.size())));
        distinctApproximate.retainAll(expected);
        return distinctApproximate.size() / (double) denominator;
    }

    static boolean annGate(List<Ranked> exact, List<String> approximate, int k,
                           double minimumRecall, int minimumRows) {
        if (!(minimumRecall >= 0 && minimumRecall <= 1) || minimumRows < 1 || minimumRows > k) {
            throw new IllegalArgumentException("invalid gate");
        }
        return approximate.size() >= minimumRows && recallAtK(exact, approximate, k) >= minimumRecall;
    }

    static int statementBudget(long deadlineNanos, long nowNanos, int configuredCapMs) {
        if (configuredCapMs < 1 || deadlineNanos <= nowNanos) throw new IllegalArgumentException("no budget");
        long remainingMs = Math.max(1, (deadlineNanos - nowNanos) / 1_000_000);
        return (int) Math.min(configuredCapMs, Math.min(Integer.MAX_VALUE, remainingMs));
    }

    static final class Lease {
        boolean held;
        int releases;

        void acquire() {
            if (held) throw new IllegalStateException("already held");
            held = true;
        }

        void release() {
            if (!held) throw new IllegalStateException("release without acquisition");
            held = false;
            releases++;
        }
    }

    static List<Ranked> independentOracle(List<Chunk> rows, double[] query, String tenant,
                                          String generation, int limit) {
        var copy = new ArrayList<Ranked>();
        for (Chunk row : rows) {
            if (!tenant.equals(row.tenant()) || !generation.equals(row.generation())) continue;
            double dot = 0, q2 = 0, v2 = 0;
            for (int i = 0; i < query.length; i++) {
                dot += query[i] * row.vector()[i];
                q2 += query[i] * query[i];
                v2 += row.vector()[i] * row.vector()[i];
            }
            copy.add(new Ranked(row.id(), dot / Math.sqrt(q2 * v2)));
        }
        copy.sort((a, b) -> {
            int score = Double.compare(b.score(), a.score());
            return score != 0 ? score : a.id().compareTo(b.id());
        });
        return List.copyOf(copy.subList(0, Math.min(limit, copy.size())));
    }

    public static void main(String[] args) {
        var active = new Generation("corpus-42", 42, State.ACTIVE, 3, 3);
        var ready = new Generation("corpus-43", 43, State.READY, 3, 3);
        var promoted = promote(active, ready, 3);
        check(promoted.id().equals("corpus-43"));
        check(promoted.state() == State.ACTIVE);
        rejects(() -> promote(active, new Generation("partial", 43, State.BUILDING, 3, 3), 3));
        rejects(() -> promote(active, ready, 2));
        rejects(() -> promote(active, new Generation("old", 42, State.READY, 3, 3), 3));

        check(vectorLiteral(new double[]{1, .25, -2}, 3).startsWith("["));
        rejects(() -> vectorLiteral(new double[]{1, 2}, 3));
        rejects(() -> vectorLiteral(new double[]{0, 0, 0}, 3));
        rejects(() -> vectorLiteral(new double[]{1, Double.NaN, 2}, 3));

        String hostile = "payments' OR TRUE --";
        var plan = buildPlan(new double[]{1, 0, 0}, 3, hostile, "corpus-43", 5, 250);
        check(!plan.sql().contains(hostile));
        check(plan.parameters().get(1).equals(hostile));
        check(plan.parameters().equals(List.of("[1.00000000,0.00000000,0.00000000]", hostile, "corpus-43", 5)));
        check(plan.statementTimeoutMs() == 250);
        rejects(() -> buildPlan(new double[]{1, 0, 0}, 3, "payments", "corpus-43", 0, 250));
        rejects(() -> buildPlan(new double[]{1, 0, 0}, 3, "payments", "corpus-43", 5, 0));

        var rows = List.of(
                new Chunk("payments", "corpus-43", "payments-r8", 8, new double[]{1, 0, 0}),
                new Chunk("payments", "corpus-43", "runbook-r3", 3, new double[]{.9, .1, 0}),
                new Chunk("payments", "corpus-42", "old-r7", 7, new double[]{1, 0, 0}),
                new Chunk("catalog", "corpus-43", "catalog-secret", 9, new double[]{1, 0, 0}));
        var exact = exactSearch(rows, new double[]{1, 0, 0}, "payments", "corpus-43", 2);
        check(exact.stream().map(Ranked::id).toList().equals(List.of("payments-r8", "runbook-r3")));
        check(exact.stream().noneMatch(item -> item.id().equals("catalog-secret")));
        check(exactSearch(rows, new double[]{1, 0, 0}, "unknown", "corpus-43", 2).isEmpty());
        rejects(() -> exactSearch(rows, new double[]{1, 0}, "payments", "corpus-43", 2));

        var ties = List.of(
                new Chunk("payments", "corpus-43", "b", 1, new double[]{1, 0, 0}),
                new Chunk("payments", "corpus-43", "a", 1, new double[]{1, 0, 0}));
        check(exactSearch(ties, new double[]{1, 0, 0}, "payments", "corpus-43", 2)
                .stream().map(Ranked::id).toList().equals(List.of("a", "b")));

        check(recallAtK(exact, List.of("payments-r8", "runbook-r3"), 2) == 1.0);
        check(recallAtK(exact, List.of("payments-r8"), 2) == .5);
        check(annGate(exact, List.of("payments-r8", "runbook-r3"), 2, .95, 2));
        check(!annGate(exact, List.of("payments-r8"), 2, .95, 2));
        rejects(() -> annGate(exact, List.of(), 2, 1.1, 1));

        check(statementBudget(1_000_000_000, 100_000_000, 250) == 250);
        check(statementBudget(200_000_000, 100_000_000, 250) == 100);
        rejects(() -> statementBudget(100, 100, 250));
        rejects(() -> statementBudget(200, 100, 0));

        var lease = new Lease();
        lease.acquire();
        check(lease.held);
        lease.release();
        check(!lease.held && lease.releases == 1);
        rejects(lease::release);
        lease.acquire();
        try {
            throw new RuntimeException("simulated statement timeout");
        } catch (RuntimeException expected) {
            check(lease.held);
        } finally {
            lease.release();
        }
        check(!lease.held && lease.releases == 2);

        var random = new Random(0xD00AB1E);
        for (int trial = 0; trial < 10_000; trial++) {
            double[] query = {random.nextDouble() + .01, random.nextDouble() + .01, random.nextDouble() + .01};
            var sample = new ArrayList<Chunk>();
            for (int i = 0; i < 8; i++) {
                sample.add(new Chunk("payments", "corpus-43", "p-" + i, 1,
                        new double[]{random.nextDouble() + .01, random.nextDouble() + .01, random.nextDouble() + .01}));
                sample.add(new Chunk("catalog", "corpus-43", "secret-" + i, 1, query.clone()));
            }
            var actual = exactSearch(sample, query, "payments", "corpus-43", 4);
            var oracle = independentOracle(sample, query, "payments", "corpus-43", 4);
            if (!actual.stream().map(Ranked::id).toList().equals(oracle.stream().map(Ranked::id).toList())) {
                throw new AssertionError("oracle mismatch");
            }
            if (actual.stream().anyMatch(item -> item.id().startsWith("secret-"))) {
                throw new AssertionError("scope leak");
            }
        }

        System.out.println("PARITY|GEN=corpus-43|SCOPE=payments|TOP=payments-r8|RECALL=1.000|POOL=RELEASED|TIMEOUT=250");
        System.out.println("DurableRetrievalContracts: " + scenarioChecks + " scenario groups and 10000 trials passed");
    }
}
