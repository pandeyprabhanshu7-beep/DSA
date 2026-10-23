import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Executable, dependency-free contracts for the Chapter 5 PostgreSQL model.
 * This is a state-machine oracle, not a PostgreSQL or pgvector replacement.
 * Run with: java -ea PostgresRagContracts.java
 */
public final class PostgresRagContracts {
    record EmbeddingSpec(String model, int dimensions, Metric metric) {
        EmbeddingSpec {
            requireText(model, "model");
            if (dimensions <= 0) throw new IllegalArgumentException("dimensions must be positive");
            Objects.requireNonNull(metric, "metric");
        }
    }

    enum Metric { COSINE }

    record ChunkKey(String tenant, String document, long revision, int chunkNo, String generation) {
        ChunkKey {
            requireText(tenant, "tenant");
            requireText(document, "document");
            requireText(generation, "generation");
            if (revision <= 0 || chunkNo < 0) throw new IllegalArgumentException("invalid revision/chunk");
        }
    }

    record Chunk(ChunkKey key, String body, EmbeddingSpec spec, double[] embedding) {
        Chunk {
            Objects.requireNonNull(key, "key");
            requireText(body, "body");
            Objects.requireNonNull(spec, "spec");
            embedding = checkedVector(embedding, spec);
        }

        @Override public double[] embedding() { return embedding.clone(); }

        boolean samePayload(Chunk other) {
            return body.equals(other.body) && spec.equals(other.spec)
                    && java.util.Arrays.equals(embedding, other.embedding);
        }
    }

    record Principal(String tenant, Set<String> allowedDocuments) {
        Principal {
            requireText(tenant, "tenant");
            allowedDocuments = Set.copyOf(allowedDocuments);
        }
    }

    record Hit(String document, long revision, int chunkNo, double cosineDistance) {}

    static final class Store {
        private final Map<ChunkKey, Chunk> rows = new HashMap<>();
        private final Map<String, String> activeGeneration = new HashMap<>();
        private final Map<String, Long> deletedThroughRevision = new HashMap<>();

        /** Equivalent to INSERT ... ON CONFLICT after comparing immutable payloads. */
        boolean stage(Chunk chunk) {
            Chunk existing = rows.get(chunk.key());
            if (existing == null) {
                rows.put(chunk.key(), chunk);
                return true;
            }
            if (!existing.samePayload(chunk)) {
                throw new IllegalStateException("same key, different immutable payload");
            }
            return false;
        }

        /**
         * Compare-and-set publication. A real transaction should lock/update one
         * tenant pointer row and validate the staged generation before commit.
         */
        void publish(String tenant, String expectedActive, String next, int expectedChunks) {
            requireText(tenant, "tenant");
            requireText(next, "next generation");
            if (!Objects.equals(activeGeneration.get(tenant), expectedActive)) {
                throw new IllegalStateException("stale publication expectation");
            }
            List<Chunk> staged = rows.values().stream()
                    .filter(c -> c.key().tenant().equals(tenant) && c.key().generation().equals(next))
                    .toList();
            if (staged.size() != expectedChunks || staged.isEmpty()) {
                throw new IllegalStateException("staged count mismatch");
            }
            EmbeddingSpec oneSpec = staged.get(0).spec();
            if (staged.stream().anyMatch(c -> !c.spec().equals(oneSpec))) {
                throw new IllegalStateException("mixed embedding contract in one generation");
            }
            activeGeneration.put(tenant, next);
        }

        void tombstone(String tenant, String document, long throughRevision) {
            requireText(tenant, "tenant");
            requireText(document, "document");
            if (throughRevision <= 0) throw new IllegalArgumentException("revision must be positive");
            String identity = tenant + "\u0000" + document;
            deletedThroughRevision.merge(identity, throughRevision, Math::max);
        }

        List<Hit> exactCosine(Principal principal, EmbeddingSpec querySpec,
                              double[] query, int topK) {
            Objects.requireNonNull(principal, "principal");
            double[] checkedQuery = checkedVector(query, querySpec);
            if (topK <= 0 || topK > 100) throw new IllegalArgumentException("topK out of range");
            String generation = activeGeneration.get(principal.tenant());
            if (generation == null) return List.of();

            return rows.values().stream()
                    .filter(c -> c.key().tenant().equals(principal.tenant()))
                    .filter(c -> c.key().generation().equals(generation))
                    .filter(c -> principal.allowedDocuments().contains(c.key().document()))
                    .filter(c -> c.key().revision() > deletedThrough(c.key()))
                    .filter(c -> c.spec().equals(querySpec))
                    .map(c -> new Hit(c.key().document(), c.key().revision(), c.key().chunkNo(),
                            cosineDistance(checkedQuery, c.embedding())))
                    .sorted(Comparator.comparingDouble(Hit::cosineDistance)
                            .thenComparing(Hit::document)
                            .thenComparingLong(Hit::revision)
                            .thenComparingInt(Hit::chunkNo))
                    .limit(topK)
                    .toList();
        }

        String active(String tenant) { return activeGeneration.get(tenant); }

        private long deletedThrough(ChunkKey key) {
            return deletedThroughRevision.getOrDefault(key.tenant() + "\u0000" + key.document(), 0L);
        }
    }

    private static double cosineDistance(double[] a, double[] b) {
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            aa += a[i] * a[i];
            bb += b[i] * b[i];
        }
        return 1.0 - dot / (Math.sqrt(aa) * Math.sqrt(bb));
    }

    private static double[] checkedVector(double[] input, EmbeddingSpec spec) {
        Objects.requireNonNull(input, "embedding");
        if (input.length != spec.dimensions()) throw new IllegalArgumentException("dimension mismatch");
        double norm = 0;
        for (double value : input) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite embedding");
            norm += value * value;
        }
        if (!Double.isFinite(norm) || norm == 0) throw new IllegalArgumentException("zero/overflow vector");
        return input.clone();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is blank");
    }

    private static Chunk chunk(String tenant, String document, long revision, int chunkNo,
                               String generation, String body, EmbeddingSpec spec, double... vector) {
        return new Chunk(new ChunkKey(tenant, document, revision, chunkNo, generation),
                body, spec, vector);
    }

    private static void expectFailure(Runnable action, String expectedMessage) {
        try {
            action.run();
            throw new AssertionError("expected failure: " + expectedMessage);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            assert expected.getMessage().contains(expectedMessage) : expected.getMessage();
        }
    }

    public static void main(String[] args) {
        EmbeddingSpec ada = new EmbeddingSpec("embed-a@2026-10", 3, Metric.COSINE);
        EmbeddingSpec next = new EmbeddingSpec("embed-b@2026-10", 2, Metric.COSINE);
        Store store = new Store();

        // Example 1: an exact retry is a no-op; key reuse with changed bytes fails closed.
        Chunk retry = chunk("acme", "runbook", 7, 0, "g17", "rollback safely", ada, 1, 0, 0);
        assert store.stage(retry);
        assert !store.stage(retry);
        expectFailure(() -> store.stage(chunk("acme", "runbook", 7, 0, "g17",
                "changed bytes", ada, 1, 0, 0)), "different immutable payload");

        // Example 2: incomplete staging cannot publish, and a stale writer cannot overwrite g18.
        expectFailure(() -> store.publish("acme", null, "g17", 2), "count mismatch");
        store.stage(chunk("acme", "faq", 3, 0, "g17", "invoice help", ada, .8, .2, 0));
        store.publish("acme", null, "g17", 2);
        store.stage(chunk("acme", "runbook", 8, 0, "g18", "new rollback", next, 1, 0));
        store.stage(chunk("acme", "secret", 1, 0, "g18", "private", next, 0, 1));
        store.publish("acme", "g17", "g18", 2);
        expectFailure(() -> store.publish("acme", "g17", "g18", 2), "stale publication");
        assert store.active("acme").equals("g18");

        // Example 3: active generation, embedding contract, tenant, and ACL all precede scoring.
        store.stage(chunk("beta", "runbook", 9, 0, "g18", "other tenant", next, 1, 0));
        store.publish("beta", null, "g18", 1);
        Principal reader = new Principal("acme", Set.of("runbook"));
        List<Hit> hits = store.exactCosine(reader, next, new double[] {1, 0}, 5);
        assert hits.size() == 1 && hits.get(0).document().equals("runbook");
        expectFailure(() -> store.exactCosine(reader, ada, new double[] {1, 0}, 5), "dimension mismatch");
        expectFailure(() -> store.exactCosine(reader, next, new double[] {1, 0}, 101), "topK");

        // Example 4: a monotonic tombstone outranks active rows and repeated/older deletes.
        store.tombstone("acme", "runbook", 8);
        store.tombstone("acme", "runbook", 7);
        assert store.exactCosine(reader, next, new double[] {1, 0}, 5).isEmpty();

        // Input validation is part of the query/write contract.
        expectFailure(() -> store.exactCosine(reader, next, new double[] {0, 0}, 5), "zero");
        System.out.println("PostgresRagContracts: all scenarios passed");
    }
}
