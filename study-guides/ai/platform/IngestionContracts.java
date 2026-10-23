import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Java 17 source-file program for versioned, bounded, and atomic ingestion contracts. */
public final class IngestionContracts {
    record IngestionKey(String tenant,
                        String document,
                        long revision,
                        String parserVersion,
                        String chunkerVersion,
                        String embeddingModel) {
        IngestionKey {
            requireText(tenant, "tenant");
            requireText(document, "document");
            requireText(parserVersion, "parser version");
            requireText(chunkerVersion, "chunker version");
            requireText(embeddingModel, "embedding model");
            if (revision <= 0) throw new IllegalArgumentException("revision must be positive");
        }
    }

    enum JobState { ACCEPTED, STAGED, PUBLISHED, SUPERSEDED, QUARANTINED }

    record Job(String id, IngestionKey key, JobState state, String generation) {
        Job {
            requireText(id, "job id");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(state, "state");
        }

        Job withState(JobState next, String nextGeneration) {
            return new Job(id, key, next, nextGeneration);
        }
    }

    /** One durable job per complete idempotency tuple; publication is revision-monotonic. */
    static final class JobRegistry {
        private final Map<IngestionKey, Job> jobs = new LinkedHashMap<>();
        private final Map<String, Long> activeRevision = new HashMap<>();
        private int sequence;

        Job accept(IngestionKey key) {
            return jobs.computeIfAbsent(key,
                    ignored -> new Job("job-" + (++sequence), key, JobState.ACCEPTED, null));
        }

        Job stage(IngestionKey key, String generation) {
            requireText(generation, "generation");
            Job current = required(key);
            if (current.state() == JobState.QUARANTINED) {
                throw new IllegalStateException("quarantined input cannot be staged");
            }
            Job staged = current.withState(JobState.STAGED, generation);
            jobs.put(key, staged);
            return staged;
        }

        Job publish(IngestionKey key) {
            Job current = required(key);
            if (current.state() != JobState.STAGED) {
                throw new IllegalStateException("only a validated staging generation may publish");
            }
            String documentKey = key.tenant() + ":" + key.document();
            long published = activeRevision.getOrDefault(documentKey, 0L);
            Job decided;
            if (key.revision() < published) {
                decided = current.withState(JobState.SUPERSEDED, current.generation());
            } else {
                activeRevision.put(documentKey, key.revision());
                decided = current.withState(JobState.PUBLISHED, current.generation());
            }
            jobs.put(key, decided);
            return decided;
        }

        Job quarantine(IngestionKey key) {
            Job quarantined = required(key).withState(JobState.QUARANTINED, null);
            jobs.put(key, quarantined);
            return quarantined;
        }

        long activeRevision(String tenant, String document) {
            return activeRevision.getOrDefault(tenant + ":" + document, 0L);
        }

        private Job required(IngestionKey key) {
            Job job = jobs.get(key);
            if (job == null) throw new IllegalArgumentException("job was not accepted");
            return job;
        }
    }

    record ParseLimits(long maxCompressedBytes,
                       long maxExpandedBytes,
                       int maxPages,
                       long maxMillis) {
        ParseLimits {
            if (maxCompressedBytes <= 0 || maxExpandedBytes <= 0 || maxPages <= 0 || maxMillis <= 0) {
                throw new IllegalArgumentException("parse limits must be positive");
            }
            if (maxExpandedBytes < maxCompressedBytes) {
                throw new IllegalArgumentException("expanded limit cannot be smaller than input limit");
            }
        }

        void admit(ParseObservation input) {
            if (input.compressedBytes() > maxCompressedBytes
                    || input.expandedBytes() > maxExpandedBytes
                    || input.pages() > maxPages
                    || input.elapsedMillis() > maxMillis) {
                throw new IllegalArgumentException("parse observation exceeds a configured resource limit");
            }
        }
    }

    record ParseObservation(long compressedBytes, long expandedBytes, int pages, long elapsedMillis) {
        ParseObservation {
            if (compressedBytes < 0 || expandedBytes < 0 || pages < 0 || elapsedMillis < 0) {
                throw new IllegalArgumentException("parse observation cannot be negative");
            }
        }
    }

    record Window(int ordinal, int startInclusive, int endExclusive) {
        int size() { return endExclusive - startInclusive; }
    }

    static List<Window> windows(int tokenCount, int windowSize, int overlap) {
        if (tokenCount <= 0 || windowSize <= 0 || overlap < 0 || overlap >= windowSize) {
            throw new IllegalArgumentException("require tokens > 0 and 0 <= overlap < window");
        }
        int stride = windowSize - overlap;
        List<Window> result = new ArrayList<>();
        for (int start = 0, ordinal = 0; start < tokenCount; start += stride, ordinal++) {
            int end = Math.min(start + windowSize, tokenCount);
            result.add(new Window(ordinal, start, end));
        }
        return List.copyOf(result);
    }

    record Generation(String id,
                      long documentRevision,
                      int expectedChunks,
                      int actualChunks,
                      boolean metadataComplete,
                      boolean retrievalProbePassed) {
        Generation {
            requireText(id, "generation id");
            if (documentRevision <= 0 || expectedChunks < 0 || actualChunks < 0) {
                throw new IllegalArgumentException("invalid generation counts or revision");
            }
        }

        boolean valid() {
            return expectedChunks == actualChunks && metadataComplete && retrievalProbePassed;
        }
    }

    record ActiveView(String generation, long documentRevision, long authorizationEpoch) { }

    /** Readers observe one coherent generation; deletion and ACL changes advance freshness state. */
    static final class Catalog {
        private final Map<String, Generation> staging = new HashMap<>();
        private final Map<String, Long> tombstones = new HashMap<>();
        private String activeGeneration;
        private long activeRevision;
        private long authorizationEpoch;

        void stage(Generation generation) {
            staging.put(generation.id(), generation);
        }

        ActiveView publish(String generationId) {
            Generation candidate = staging.get(generationId);
            if (candidate == null || !candidate.valid()) {
                throw new IllegalStateException("generation is missing or failed validation");
            }
            if (candidate.documentRevision() < activeRevision) {
                throw new IllegalStateException("stale generation cannot replace a newer revision");
            }
            activeGeneration = candidate.id();
            activeRevision = candidate.documentRevision();
            return view();
        }

        void delete(String document, long revision) {
            requireText(document, "document");
            tombstones.merge(document, revision, Math::max);
            authorizationEpoch++;
        }

        void revokeAcl() {
            authorizationEpoch++;
        }

        boolean mayServe(String document, long revision, long requestEpoch) {
            long deletedThrough = tombstones.getOrDefault(document, 0L);
            return activeGeneration != null
                    && revision > deletedThrough
                    && requestEpoch == authorizationEpoch;
        }

        ActiveView view() {
            return new ActiveView(activeGeneration, activeRevision, authorizationEpoch);
        }
    }

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
    }

    static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void expectThrows(Class<? extends Throwable> type, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (type.isInstance(actual)) return;
            throw new AssertionError(message + ": wrong exception " + actual, actual);
        }
        throw new AssertionError(message + ": no exception");
    }

    public static void main(String[] args) {
        int groups = 0;

        var registry = new JobRegistry();
        var revision7 = new IngestionKey("acme", "payments", 7,
                "tika-4.1", "heading-v3", "embed-v5");
        Job first = registry.accept(revision7);
        Job retry = registry.accept(revision7);
        expect(first.id().equals(retry.id()), "same idempotency tuple must return the same job");
        groups++;

        registry.stage(revision7, "g17");
        expect(registry.publish(revision7).state() == JobState.PUBLISHED,
                "validated revision 7 should publish");
        var revision8 = new IngestionKey("acme", "payments", 8,
                "tika-4.1", "heading-v3", "embed-v5");
        registry.accept(revision8);
        registry.stage(revision8, "g18");
        registry.publish(revision8);
        expect(registry.activeRevision("acme", "payments") == 8,
                "newer revision must become active");
        groups++;

        var delayedRevision6 = new IngestionKey("acme", "payments", 6,
                "tika-4.1", "heading-v3", "embed-v5");
        registry.accept(delayedRevision6);
        registry.stage(delayedRevision6, "g16");
        expect(registry.publish(delayedRevision6).state() == JobState.SUPERSEDED,
                "delayed old work must not overwrite the newer active revision");
        expect(registry.activeRevision("acme", "payments") == 8,
                "stale publication must leave the active revision unchanged");
        groups++;

        var limits = new ParseLimits(10_000_000, 100_000_000, 500, 30_000);
        limits.admit(new ParseObservation(2_000_000, 18_000_000, 42, 1_900));
        expectThrows(IllegalArgumentException.class,
                () -> limits.admit(new ParseObservation(2_000_000, 120_000_000, 42, 1_900)),
                "expanded-byte bomb must be rejected");
        groups += 2;

        List<Window> chunks = windows(4_000, 600, 100);
        expect(chunks.size() == 8, "4000/600/100 should produce eight windows");
        expect(chunks.get(0).equals(new Window(0, 0, 600)), "first window mismatch");
        expect(chunks.get(7).equals(new Window(7, 3_500, 4_000)), "last window should be 500 tokens");
        groups++;

        var catalog = new Catalog();
        catalog.stage(new Generation("g18", 8, 8, 7, true, true));
        expectThrows(IllegalStateException.class, () -> catalog.publish("g18"),
                "count mismatch must block publication");
        catalog.stage(new Generation("g18", 8, 8, 8, true, true));
        ActiveView published = catalog.publish("g18");
        expect("g18".equals(published.generation()) && published.documentRevision() == 8,
                "validated generation must switch atomically");
        groups += 2;

        long beforeRevocation = catalog.view().authorizationEpoch();
        expect(catalog.mayServe("payments", 8, beforeRevocation),
                "fresh authorized revision should be readable");
        catalog.revokeAcl();
        expect(!catalog.mayServe("payments", 8, beforeRevocation),
                "stale authorization epoch must not survive ACL revocation");
        long afterRevocation = catalog.view().authorizationEpoch();
        catalog.delete("payments", 8);
        expect(!catalog.mayServe("payments", 8, afterRevocation + 1),
                "tombstone must block deleted revision even with a fresh epoch");
        groups += 3;

        registry.quarantine(revision7);
        expectThrows(IllegalStateException.class, () -> registry.stage(revision7, "g19"),
                "quarantined input must not reenter publication without a new decision");
        groups++;

        System.out.println("IngestionContracts: " + groups + " scenario groups passed");
    }
}
