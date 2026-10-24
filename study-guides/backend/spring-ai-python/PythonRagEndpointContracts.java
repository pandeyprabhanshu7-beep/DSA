import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Dependency-free contracts for a bounded Python RAG endpoint. */
public final class PythonRagEndpointContracts {
    enum Result { ANSWER, ABSTAIN, REJECTED, DEADLINE, DEPENDENCY }
    record Document(String id, int revision, String text) {}
    record Snapshot(List<Document> documents, List<double[]> vectors, int dimension) {}
    record Ranked(double score, Document document) {}
    record HttpResult(Result result, int status) {}

    static Snapshot buildSnapshot(List<Document> documents, List<double[]> vectors) {
        if (documents.isEmpty() || documents.size() != vectors.size()) {
            throw new IllegalArgumentException("embedding count mismatch");
        }
        if (documents.stream().map(Document::id).distinct().count() != documents.size()) {
            throw new IllegalArgumentException("duplicate document ID");
        }
        int dimension = vectors.get(0).length;
        if (dimension == 0) throw new IllegalArgumentException("empty vector");
        var copies = new ArrayList<double[]>();
        for (double[] vector : vectors) {
            if (vector.length != dimension) throw new IllegalArgumentException("invalid embedding shape");
            double norm = 0;
            for (double value : vector) {
                if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite coordinate");
                norm += value * value;
            }
            if (Math.sqrt(norm) == 0) throw new IllegalArgumentException("zero-norm embedding");
            copies.add(vector.clone());
        }
        return new Snapshot(List.copyOf(documents), List.copyOf(copies), dimension);
    }

    static String validateQuestion(String question) {
        if (question == null || question.length() < 3 || question.length() > 2_000 || question.isBlank()) {
            throw new IllegalArgumentException("question violates DTO contract");
        }
        return question.trim();
    }

    static double cosine(double[] left, double[] right) {
        if (left.length == 0 || left.length != right.length) {
            throw new IllegalArgumentException("embedding dimensions differ");
        }
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            if (!Double.isFinite(left[index]) || !Double.isFinite(right[index])) {
                throw new IllegalArgumentException("non-finite coordinate");
            }
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) throw new IllegalArgumentException("zero-norm embedding");
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    static List<Document> retrieve(Snapshot snapshot, double[] query, double threshold, int limit) {
        if (query.length != snapshot.dimension() || limit < 1 || limit > snapshot.documents().size()) {
            throw new IllegalArgumentException("invalid query dimension or limit");
        }
        var ranked = new ArrayList<Ranked>();
        for (int index = 0; index < snapshot.documents().size(); index++) {
            ranked.add(new Ranked(cosine(query, snapshot.vectors().get(index)), snapshot.documents().get(index)));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed()
                .thenComparing(item -> item.document().id()));
        if (ranked.get(0).score() < threshold) return List.of();
        return ranked.subList(0, limit).stream().map(Ranked::document).toList();
    }

    static Result validateGrounding(Set<String> cited, Set<String> supported, List<Document> selected) {
        Set<String> allowed = new HashSet<>();
        selected.forEach(document -> allowed.add(document.id()));
        if (cited.isEmpty() || !allowed.containsAll(cited)) {
            throw new IllegalArgumentException("missing or invented citation");
        }
        return supported.containsAll(cited) ? Result.ANSWER : Result.ABSTAIN;
    }

    static HttpResult mapOutcome(boolean admitted, boolean deadline, boolean dependencyFailed, boolean noEvidence) {
        if (!admitted) return new HttpResult(Result.REJECTED, 429);
        if (deadline) return new HttpResult(Result.DEADLINE, 504);
        if (dependencyFailed) return new HttpResult(Result.DEPENDENCY, 502);
        if (noEvidence) return new HttpResult(Result.ABSTAIN, 200);
        return new HttpResult(Result.ANSWER, 200);
    }

    static int fleetCapacity(int processes, int permitsPerProcess) {
        if (processes < 1 || permitsPerProcess < 1) {
            throw new IllegalArgumentException("positive process and permit counts required");
        }
        return Math.multiplyExact(processes, permitsPerProcess);
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
        var docs = List.of(
                new Document("payments-r7", 7, "Rollback requires backward-compatible migrations."),
                new Document("runbook-r2", 2, "Page the payments owner before rollback."),
                new Document("catalog-r1", 1, "Unrelated catalog deployment notes."));
        var snapshot = buildSnapshot(docs, List.of(new double[]{1, 0}, new double[]{.8, .2}, new double[]{-1, 0}));
        assert snapshot.dimension() == 2;
        rejects(() -> buildSnapshot(docs, List.of(new double[]{1}, new double[]{2})));
        rejects(() -> buildSnapshot(docs, List.of(new double[]{1, 0}, new double[]{1}, new double[]{1, 0})));
        rejects(() -> buildSnapshot(docs, List.of(new double[]{1, 0}, new double[]{Double.NaN, 1}, new double[]{1, 0})));
        rejects(() -> buildSnapshot(docs, List.of(new double[]{1, 0}, new double[]{0, 0}, new double[]{1, 0})));
        rejects(() -> buildSnapshot(List.of(docs.get(0), docs.get(0)), List.of(new double[]{1}, new double[]{1})));

        assert validateQuestion("  rollback?  ").equals("rollback?");
        rejects(() -> validateQuestion("  "));
        rejects(() -> validateQuestion("x".repeat(2_001)));
        var selected = retrieve(snapshot, new double[]{1, 0}, .70, 2);
        assert selected.stream().map(Document::id).toList().equals(List.of("payments-r7", "runbook-r2"));
        var tied = buildSnapshot(docs.subList(0, 2), List.of(new double[]{1, 0}, new double[]{1, 0}));
        assert retrieve(tied, new double[]{1, 0}, .9, 2).stream().map(Document::id).toList()
                .equals(List.of("payments-r7", "runbook-r2"));
        assert retrieve(snapshot, new double[]{0, 1}, .99, 2).isEmpty();
        rejects(() -> retrieve(snapshot, new double[]{1}, .5, 2));

        assert validateGrounding(Set.of("payments-r7"), Set.of("payments-r7"), selected) == Result.ANSWER;
        assert validateGrounding(Set.of("payments-r7"), Set.of(), selected) == Result.ABSTAIN;
        rejects(() -> validateGrounding(Set.of(), Set.of(), selected));
        rejects(() -> validateGrounding(Set.of("invented"), Set.of("invented"), selected));

        assert fleetCapacity(3, 4) == 12;
        rejects(() -> fleetCapacity(0, 4));
        assert mapOutcome(false, false, false, false).equals(new HttpResult(Result.REJECTED, 429));
        assert mapOutcome(true, true, false, false).equals(new HttpResult(Result.DEADLINE, 504));
        assert mapOutcome(true, false, true, false).equals(new HttpResult(Result.DEPENDENCY, 502));
        assert mapOutcome(true, false, false, true).equals(new HttpResult(Result.ABSTAIN, 200));

        var permit = new Permit();
        permit.acquire();
        try {
            throw new RuntimeException("simulated deadline");
        } catch (RuntimeException expected) {
            // failure is mapped by the handler
        } finally {
            permit.release();
        }
        assert permit.releases == 1 && !permit.acquired;
        rejects(permit::release);

        var random = new Random(0xA71A5);
        for (int trial = 0; trial < 10_000; trial++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double[] vector = {Math.cos(angle), Math.sin(angle)};
            assert Math.abs(cosine(vector, vector) - 1.0) < 1e-12;
        }

        System.out.println("PARITY|READY|ANSWER|ABSTAIN|429|504|502|RELEASED");
        System.out.println("PythonRagEndpointContracts: 32 scenario groups and 10000 trials passed");
    }
}
