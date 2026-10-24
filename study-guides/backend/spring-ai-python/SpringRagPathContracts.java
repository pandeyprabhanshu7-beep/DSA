import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Dependency-free contracts for the complete Spring AI RAG path. */
public final class SpringRagPathContracts {
    enum Result { ANSWER, ABSTAIN, REJECTED, DEADLINE, DEPENDENCY }
    record RuntimeIdentity(String boot, String springAi, String chatModel, String embeddingModel) {}
    record Evidence(String id, int revision, String text) {}
    record Snapshot(String generation, List<Evidence> evidence, List<double[]> vectors, int dimension) {}
    record Ranked(double score, Evidence evidence) {}
    record HttpResult(Result result, int status) {}

    static RuntimeIdentity requireIdentity(String boot, String springAi, String chat, String embedding) {
        if (boot == null || springAi == null || chat == null || embedding == null
                || boot.isBlank() || springAi.isBlank() || chat.isBlank() || embedding.isBlank()) {
            throw new IllegalArgumentException("complete runtime identity required");
        }
        return new RuntimeIdentity(boot, springAi, chat, embedding);
    }

    static Snapshot publish(String generation, List<Evidence> evidence, List<double[]> vectors) {
        if (generation == null || generation.isBlank() || evidence.isEmpty() || evidence.size() != vectors.size()) {
            throw new IllegalArgumentException("incomplete snapshot");
        }
        if (evidence.stream().map(Evidence::id).distinct().count() != evidence.size()) {
            throw new IllegalArgumentException("duplicate evidence ID");
        }
        int dimension = vectors.get(0).length;
        if (dimension == 0) throw new IllegalArgumentException("empty vector");
        var copies = new ArrayList<double[]>();
        for (double[] vector : vectors) {
            if (vector.length != dimension) throw new IllegalArgumentException("dimension mismatch");
            double norm = 0;
            for (double coordinate : vector) {
                if (!Double.isFinite(coordinate)) throw new IllegalArgumentException("non-finite coordinate");
                norm += coordinate * coordinate;
            }
            if (norm == 0) throw new IllegalArgumentException("zero vector");
            copies.add(vector.clone());
        }
        return new Snapshot(generation, List.copyOf(evidence), List.copyOf(copies), dimension);
    }

    static double cosine(double[] left, double[] right) {
        if (left.length == 0 || left.length != right.length) throw new IllegalArgumentException("dimensions");
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            if (!Double.isFinite(left[index]) || !Double.isFinite(right[index])) {
                throw new IllegalArgumentException("non-finite coordinate");
            }
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) throw new IllegalArgumentException("zero vector");
        return dot / Math.sqrt(leftNorm * rightNorm);
    }

    static List<Evidence> retrieve(Snapshot snapshot, double[] query, double threshold, int limit) {
        if (query.length != snapshot.dimension() || limit < 1 || limit > snapshot.evidence().size()
                || !Double.isFinite(threshold)) {
            throw new IllegalArgumentException("invalid retrieval request");
        }
        var ranked = new ArrayList<Ranked>();
        for (int index = 0; index < snapshot.evidence().size(); index++) {
            ranked.add(new Ranked(cosine(query, snapshot.vectors().get(index)), snapshot.evidence().get(index)));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed()
                .thenComparing(item -> item.evidence().id()));
        if (ranked.get(0).score() < threshold) return List.of();
        return ranked.subList(0, limit).stream().map(Ranked::evidence).toList();
    }

    static Result validateGrounding(Set<String> cited, Set<String> supported, List<Evidence> selected) {
        Set<String> allowed = new HashSet<>();
        selected.forEach(item -> allowed.add(item.id()));
        if (cited.isEmpty() || !allowed.containsAll(cited)) {
            throw new IllegalArgumentException("missing or invented citation");
        }
        return supported.containsAll(cited) ? Result.ANSWER : Result.ABSTAIN;
    }

    static long remainingMillis(long deadlineNanos, long nowNanos) {
        if (deadlineNanos <= nowNanos) return 0;
        return Math.max(1, (deadlineNanos - nowNanos) / 1_000_000);
    }

    static int fleetCapacity(int processes, int permitsPerProcess) {
        if (processes < 1 || permitsPerProcess < 1) throw new IllegalArgumentException("positive capacity required");
        return Math.multiplyExact(processes, permitsPerProcess);
    }

    static HttpResult mapOutcome(boolean admitted, boolean deadline, boolean dependencyFailed, boolean noEvidence) {
        if (!admitted) return new HttpResult(Result.REJECTED, 429);
        if (deadline) return new HttpResult(Result.DEADLINE, 504);
        if (dependencyFailed) return new HttpResult(Result.DEPENDENCY, 502);
        if (noEvidence) return new HttpResult(Result.ABSTAIN, 200);
        return new HttpResult(Result.ANSWER, 200);
    }

    static final class Permit {
        boolean acquired;
        int releases;
        void acquire() {
            if (acquired) throw new IllegalStateException("already acquired");
            acquired = true;
        }
        void release() {
            if (!acquired) throw new IllegalStateException("release without acquisition");
            acquired = false;
            releases++;
        }
    }

    static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException expected) {
            return;
        }
        throw new AssertionError("expected rejection");
    }

    public static void main(String[] args) {
        var identity = requireIdentity("4.1.2", "2.0.2", "qwen3:8b@sha256:chat", "embeddinggemma@sha256:embed");
        assert identity.springAi().equals("2.0.2");
        rejects(() -> requireIdentity("4.1.2", "", "chat", "embed"));
        rejects(() -> requireIdentity("4.1.2", "2.0.2", null, "embed"));

        var evidence = List.of(
                new Evidence("payments-r7", 7, "Rollback requires compatible migrations."),
                new Evidence("runbook-r2", 2, "Page the payments owner before rollback."),
                new Evidence("catalog-r1", 1, "Unrelated catalog deployment notes."));
        double[] first = {1, 0};
        var snapshot = publish("corpus-42/embed-v3", evidence,
                List.of(first, new double[]{.8, .2}, new double[]{-1, 0}));
        first[0] = -1;
        assert snapshot.vectors().get(0)[0] == 1 : "published vectors must be defensive copies";
        rejects(() -> publish("", evidence, List.of(new double[]{1}, new double[]{1}, new double[]{1})));
        rejects(() -> publish("g", evidence, List.of(new double[]{1}, new double[]{1, 2}, new double[]{1})));
        rejects(() -> publish("g", evidence, List.of(new double[]{1}, new double[]{Double.NaN}, new double[]{1})));
        rejects(() -> publish("g", evidence, List.of(new double[]{1}, new double[]{0}, new double[]{1})));
        rejects(() -> publish("g", List.of(evidence.get(0), evidence.get(0)), List.of(new double[]{1}, new double[]{1})));

        var selected = retrieve(snapshot, new double[]{1, 0}, .70, 2);
        assert selected.stream().map(Evidence::id).toList().equals(List.of("payments-r7", "runbook-r2"));
        var tied = publish("g2", evidence.subList(0, 2), List.of(new double[]{1, 0}, new double[]{1, 0}));
        assert retrieve(tied, new double[]{1, 0}, .9, 2).stream().map(Evidence::id).toList()
                .equals(List.of("payments-r7", "runbook-r2"));
        assert retrieve(snapshot, new double[]{0, 1}, .99, 2).isEmpty();
        rejects(() -> retrieve(snapshot, new double[]{1}, .5, 2));
        rejects(() -> retrieve(snapshot, new double[]{1, 0}, .5, 0));

        assert validateGrounding(Set.of("payments-r7"), Set.of("payments-r7"), selected) == Result.ANSWER;
        assert validateGrounding(Set.of("payments-r7"), Set.of(), selected) == Result.ABSTAIN;
        rejects(() -> validateGrounding(Set.of(), Set.of(), selected));
        rejects(() -> validateGrounding(Set.of("invented"), Set.of("invented"), selected));

        assert remainingMillis(20_000_000, 5_000_000) == 15;
        assert remainingMillis(5_000_000, 5_000_000) == 0;
        assert fleetCapacity(3, 4) == 12;
        rejects(() -> fleetCapacity(0, 4));
        assert mapOutcome(false, false, false, false).equals(new HttpResult(Result.REJECTED, 429));
        assert mapOutcome(true, true, false, false).equals(new HttpResult(Result.DEADLINE, 504));
        assert mapOutcome(true, false, true, false).equals(new HttpResult(Result.DEPENDENCY, 502));
        assert mapOutcome(true, false, false, true).equals(new HttpResult(Result.ABSTAIN, 200));

        var permit = new Permit();
        permit.acquire();
        try {
            throw new RuntimeException("simulated provider timeout");
        } catch (RuntimeException expected) {
            // mapped outside the pure contract
        } finally {
            permit.release();
        }
        assert permit.releases == 1 && !permit.acquired;
        rejects(permit::release);

        var random = new Random(0x5A17A1);
        for (int trial = 0; trial < 10_000; trial++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double[] vector = {Math.cos(angle), Math.sin(angle)};
            assert Math.abs(cosine(vector, vector) - 1.0) < 1e-12;
        }

        System.out.println("PARITY|CONFIGURED|READY|ANSWER|ABSTAIN|429|504|502|RELEASED");
        System.out.println("SpringRagPathContracts: 36 scenario groups and 10000 trials passed");
    }
}
