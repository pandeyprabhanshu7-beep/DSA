import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** Dependency-free Java 17 contracts for recoverable ingestion and atomic publication. */
public final class RecoverableIngestionContracts {
    private RecoverableIngestionContracts() {}

    enum Stage { ACCEPTED, STORED, PARSED, EMBEDDED, VALIDATED, PUBLISHED, RETIRED, REJECTED }
    enum StartOutcome { CREATED, REPLAYED }
    enum AdvanceOutcome { ADVANCED, REPLAYED, BLOCK_ORDER, REJECTED }
    enum PublishOutcome { PUBLISHED, REPLAYED, BLOCK_NOT_READY, BLOCK_EXPECTED_ACTIVE, BLOCK_TOMBSTONE }
    enum FetchOutcome { ALLOW, DENY_TOMBSTONE, DENY_ACL, DENY_GENERATION }

    record Identity(String tenant, String sourceId, String sourceRevision,
                    String parserVersion, String chunkerVersion, String embeddingVersion) {
        Identity {
            required(tenant, "tenant");
            required(sourceId, "sourceId");
            required(sourceRevision, "sourceRevision");
            required(parserVersion, "parserVersion");
            required(chunkerVersion, "chunkerVersion");
            required(embeddingVersion, "embeddingVersion");
        }

        String digest() {
            MessageDigest sha;
            try {
                sha = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
            for (String part : List.of(tenant, sourceId, sourceRevision,
                    parserVersion, chunkerVersion, embeddingVersion)) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                sha.update((byte) (bytes.length >>> 24));
                sha.update((byte) (bytes.length >>> 16));
                sha.update((byte) (bytes.length >>> 8));
                sha.update((byte) bytes.length);
                sha.update(bytes);
            }
            return HexFormat.of().formatHex(sha.digest());
        }
    }

    record ParseLimits(long compressedBytes, long expandedBytes, int pages,
                       long cpuMillis, String mediaType) {
        ParseLimits {
            if (compressedBytes < 0 || expandedBytes < 0 || pages < 0 || cpuMillis < 0) {
                throw new IllegalArgumentException("negative parse measurement");
            }
            required(mediaType, "mediaType");
        }
    }

    record Policy(long maxCompressedBytes, long maxExpandedBytes, int maxPages,
                  long maxCpuMillis, List<String> permittedMediaTypes) {
        Policy {
            if (maxCompressedBytes < 0 || maxExpandedBytes < 0 || maxPages < 0
                    || maxCpuMillis < 0) throw new IllegalArgumentException("negative policy limit");
            permittedMediaTypes = List.copyOf(permittedMediaTypes);
        }

        boolean admits(ParseLimits actual) {
            return actual.compressedBytes() <= maxCompressedBytes
                    && actual.expandedBytes() <= maxExpandedBytes
                    && actual.pages() <= maxPages
                    && actual.cpuMillis() <= maxCpuMillis
                    && permittedMediaTypes.contains(actual.mediaType());
        }
    }

    record Generation(String id, Identity identity, Stage stage, String objectChecksum,
                      int chunkCount, int vectorCount, int dimension,
                      long aclVersion, boolean sampleRetrievalPassed) {
        Generation {
            required(id, "id");
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(stage, "stage");
            if (chunkCount < 0 || vectorCount < 0 || dimension < 0 || aclVersion < 0) {
                throw new IllegalArgumentException("negative generation field");
            }
            objectChecksum = objectChecksum == null ? "" : objectChecksum;
        }

        boolean ready() {
            return stage == Stage.VALIDATED && !objectChecksum.isBlank()
                    && chunkCount > 0 && vectorCount == chunkCount
                    && dimension > 0 && sampleRetrievalPassed;
        }
    }

    record SourceState(String tenant, String sourceId, String activeGeneration,
                       String priorGeneration, boolean tombstoned, long aclVersion) {
        SourceState {
            required(tenant, "tenant");
            required(sourceId, "sourceId");
            activeGeneration = activeGeneration == null ? "" : activeGeneration;
            priorGeneration = priorGeneration == null ? "" : priorGeneration;
            if (aclVersion < 0) throw new IllegalArgumentException("negative aclVersion");
        }
    }

    record StartResult(StartOutcome outcome, String generationId) {}
    record AdvanceResult(AdvanceOutcome outcome, Generation generation) {}
    record PublishResult(PublishOutcome outcome, SourceState source) {}

    static final class Catalog {
        private final Map<String, String> generationByIdentity = new HashMap<>();
        private final Map<String, Generation> generations = new HashMap<>();
        private final Map<String, SourceState> sources = new HashMap<>();
        private int nextGeneration = 1;

        StartResult start(Identity identity) {
            Objects.requireNonNull(identity, "identity");
            String key = identity.digest();
            String prior = generationByIdentity.get(key);
            if (prior != null) return new StartResult(StartOutcome.REPLAYED, prior);
            String id = "g-" + nextGeneration++;
            generationByIdentity.put(key, id);
            generations.put(id, new Generation(id, identity, Stage.ACCEPTED,
                    "", 0, 0, 0, 0, false));
            sources.putIfAbsent(sourceKey(identity.tenant(), identity.sourceId()),
                    new SourceState(identity.tenant(), identity.sourceId(), "", "", false, 0));
            return new StartResult(StartOutcome.CREATED, id);
        }

        Generation generation(String id) { return generations.get(id); }

        AdvanceResult advance(String id, Stage target, String checksum, int chunks,
                              int vectors, int dimension, long aclVersion,
                              boolean sampleRetrievalPassed) {
            Generation current = requireGeneration(id);
            if (current.stage() == Stage.REJECTED) {
                return new AdvanceResult(AdvanceOutcome.REJECTED, current);
            }
            if (current.stage() == target) {
                return new AdvanceResult(AdvanceOutcome.REPLAYED, current);
            }
            if (!nextStage(current.stage(), target)) {
                return new AdvanceResult(AdvanceOutcome.BLOCK_ORDER, current);
            }
            Generation next = new Generation(id, current.identity(), target,
                    checksum, chunks, vectors, dimension, aclVersion, sampleRetrievalPassed);
            generations.put(id, next);
            return new AdvanceResult(AdvanceOutcome.ADVANCED, next);
        }

        Generation reject(String id) {
            Generation current = requireGeneration(id);
            Generation rejected = new Generation(id, current.identity(), Stage.REJECTED,
                    current.objectChecksum(), current.chunkCount(), current.vectorCount(),
                    current.dimension(), current.aclVersion(), current.sampleRetrievalPassed());
            generations.put(id, rejected);
            return rejected;
        }

        PublishResult publish(String id, String expectedActive) {
            Generation candidate = requireGeneration(id);
            String key = sourceKey(candidate.identity().tenant(), candidate.identity().sourceId());
            SourceState source = sources.get(key);
            if (source.tombstoned()) return new PublishResult(PublishOutcome.BLOCK_TOMBSTONE, source);
            if (source.activeGeneration().equals(id)) {
                return new PublishResult(PublishOutcome.REPLAYED, source);
            }
            if (!candidate.ready()) return new PublishResult(PublishOutcome.BLOCK_NOT_READY, source);
            String expected = expectedActive == null ? "" : expectedActive;
            if (!source.activeGeneration().equals(expected)) {
                return new PublishResult(PublishOutcome.BLOCK_EXPECTED_ACTIVE, source);
            }
            SourceState next = new SourceState(source.tenant(), source.sourceId(), id,
                    source.activeGeneration(), false, candidate.aclVersion());
            sources.put(key, next);
            generations.put(id, copyStage(candidate, Stage.PUBLISHED));
            return new PublishResult(PublishOutcome.PUBLISHED, next);
        }

        SourceState tombstone(String tenant, String sourceId) {
            String key = sourceKey(tenant, sourceId);
            SourceState source = Objects.requireNonNull(sources.get(key), "unknown source");
            SourceState next = new SourceState(tenant, sourceId, source.activeGeneration(),
                    source.priorGeneration(), true, source.aclVersion() + 1);
            sources.put(key, next);
            return next;
        }

        SourceState updateAcl(String tenant, String sourceId, long newVersion) {
            String key = sourceKey(tenant, sourceId);
            SourceState source = Objects.requireNonNull(sources.get(key), "unknown source");
            if (newVersion <= source.aclVersion()) throw new IllegalArgumentException("ACL must advance");
            SourceState next = new SourceState(tenant, sourceId, source.activeGeneration(),
                    source.priorGeneration(), source.tombstoned(), newVersion);
            sources.put(key, next);
            return next;
        }

        FetchOutcome fetch(String tenant, String sourceId, String candidateGeneration,
                           long candidateAclVersion) {
            SourceState source = sources.get(sourceKey(tenant, sourceId));
            if (source == null || source.tombstoned()) return FetchOutcome.DENY_TOMBSTONE;
            if (!source.activeGeneration().equals(candidateGeneration)) return FetchOutcome.DENY_GENERATION;
            if (source.aclVersion() != candidateAclVersion) return FetchOutcome.DENY_ACL;
            return FetchOutcome.ALLOW;
        }

        SourceState source(String tenant, String sourceId) {
            return sources.get(sourceKey(tenant, sourceId));
        }

        private Generation requireGeneration(String id) {
            return Objects.requireNonNull(generations.get(id), "unknown generation");
        }
    }

    static boolean embeddingCompatible(Generation left, Generation right) {
        return left.identity().embeddingVersion().equals(right.identity().embeddingVersion())
                && left.dimension() == right.dimension();
    }

    static boolean mayPromoteReembedding(Generation oldGeneration,
                                         Generation candidate, boolean fixedEvaluationPassed) {
        return candidate.ready() && fixedEvaluationPassed
                && !oldGeneration.identity().embeddingVersion()
                .equals(candidate.identity().embeddingVersion());
    }

    static List<String> chunkIds(Identity identity, List<String> structuralPaths) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < structuralPaths.size(); i++) {
            ids.add(identity.sourceRevision() + ":" + structuralPaths.get(i) + ":" + i);
        }
        return List.copyOf(ids);
    }

    private static Generation copyStage(Generation value, Stage stage) {
        return new Generation(value.id(), value.identity(), stage, value.objectChecksum(),
                value.chunkCount(), value.vectorCount(), value.dimension(),
                value.aclVersion(), value.sampleRetrievalPassed());
    }

    private static boolean nextStage(Stage from, Stage to) {
        return (from == Stage.ACCEPTED && to == Stage.STORED)
                || (from == Stage.STORED && to == Stage.PARSED)
                || (from == Stage.PARSED && to == Stage.EMBEDDED)
                || (from == Stage.EMBEDDED && to == Stage.VALIDATED);
    }

    private static String sourceKey(String tenant, String sourceId) {
        return required(tenant, "tenant") + "\u0000" + required(sourceId, "sourceId");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is blank");
        return value;
    }

    private static int scenarios;
    private static void group(Runnable check) { check.run(); scenarios++; }
    private static void truth(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Generation makeReady(Catalog catalog, Identity identity, int dimension) {
        String id = catalog.start(identity).generationId();
        truth(catalog.advance(id, Stage.STORED, "sha256:abc", 0, 0, 0, 0, false).outcome()
                == AdvanceOutcome.ADVANCED, "stored");
        truth(catalog.advance(id, Stage.PARSED, "sha256:abc", 3, 0, 0, 0, false).outcome()
                == AdvanceOutcome.ADVANCED, "parsed");
        truth(catalog.advance(id, Stage.EMBEDDED, "sha256:abc", 3, 3, dimension, 0, false).outcome()
                == AdvanceOutcome.ADVANCED, "embedded");
        truth(catalog.advance(id, Stage.VALIDATED, "sha256:abc", 3, 3, dimension, 0, true).outcome()
                == AdvanceOutcome.ADVANCED, "validated");
        return catalog.generation(id);
    }

    public static void main(String[] args) {
        Identity v1 = new Identity("tenant-a", "handbook", "rev-7",
                "tika@4", "structure@2", "embed-a@1");
        Catalog catalog = new Catalog();
        StartResult first = catalog.start(v1);
        group(() -> truth(first.outcome() == StartOutcome.CREATED, "created"));
        group(() -> truth(catalog.start(v1).outcome() == StartOutcome.REPLAYED, "retry replay"));
        group(() -> truth(catalog.start(v1).generationId().equals(first.generationId()), "same job"));
        group(() -> truth(!v1.digest().equals(new Identity("tenant-a", "handbook", "rev-8",
                "tika@4", "structure@2", "embed-a@1").digest()), "revision identity"));
        group(() -> truth(!v1.digest().equals(new Identity("tenant-a", "handbook", "rev-7",
                "tika@4", "structure@2", "embed-b@1").digest()), "embedding identity"));

        Policy policy = new Policy(20_000_000, 100_000_000, 500, 30_000,
                List.of("application/pdf", "text/plain"));
        group(() -> truth(policy.admits(new ParseLimits(1_000_000, 8_000_000, 20,
                2_000, "application/pdf")), "normal upload"));
        group(() -> truth(!policy.admits(new ParseLimits(1_000_000, 900_000_000, 20,
                2_000, "application/pdf")), "expanded archive bound"));
        group(() -> truth(!policy.admits(new ParseLimits(1_000, 2_000, 1,
                50, "application/x-executable")), "media allowlist"));

        group(() -> truth(catalog.advance(first.generationId(), Stage.PARSED, "sha", 3,
                0, 0, 0, false).outcome() == AdvanceOutcome.BLOCK_ORDER, "no skipped stage"));
        catalog.advance(first.generationId(), Stage.STORED, "sha256:abc", 0, 0, 0, 0, false);
        group(() -> truth(catalog.advance(first.generationId(), Stage.STORED, "different", 9,
                9, 9, 9, true).outcome() == AdvanceOutcome.REPLAYED, "stage replay immutable"));
        catalog.advance(first.generationId(), Stage.PARSED, "sha256:abc", 3, 0, 0, 0, false);
        group(() -> truth(catalog.publish(first.generationId(), "").outcome()
                == PublishOutcome.BLOCK_NOT_READY, "partial invisible"));
        catalog.advance(first.generationId(), Stage.EMBEDDED, "sha256:abc", 3, 2, 1536, 0, false);
        catalog.advance(first.generationId(), Stage.VALIDATED, "sha256:abc", 3, 2, 1536, 0, true);
        group(() -> truth(catalog.publish(first.generationId(), "").outcome()
                == PublishOutcome.BLOCK_NOT_READY, "count mismatch"));

        Catalog publication = new Catalog();
        Generation ready1 = makeReady(publication, v1, 1536);
        PublishResult published1 = publication.publish(ready1.id(), "");
        group(() -> truth(published1.outcome() == PublishOutcome.PUBLISHED, "first publish"));
        group(() -> truth(publication.publish(ready1.id(), "wrong").outcome()
                == PublishOutcome.REPLAYED, "publish replay"));
        group(() -> truth(publication.fetch("tenant-a", "handbook", ready1.id(), 0)
                == FetchOutcome.ALLOW, "active fetch"));

        Identity v2Identity = new Identity("tenant-a", "handbook", "rev-8",
                "tika@4", "structure@2", "embed-b@1");
        Generation ready2 = makeReady(publication, v2Identity, 1536);
        group(() -> truth(publication.publish(ready2.id(), "wrong-generation").outcome()
                == PublishOutcome.BLOCK_EXPECTED_ACTIVE, "CAS fence"));
        PublishResult published2 = publication.publish(ready2.id(), ready1.id());
        group(() -> truth(published2.outcome() == PublishOutcome.PUBLISHED, "generation swap"));
        group(() -> truth(published2.source().priorGeneration().equals(ready1.id()), "rollback retained"));
        group(() -> truth(publication.fetch("tenant-a", "handbook", ready1.id(), 0)
                == FetchOutcome.DENY_GENERATION, "old candidate denied"));
        group(() -> truth(!embeddingCompatible(ready1, ready2), "dimension not compatibility"));
        group(() -> truth(mayPromoteReembedding(ready1, ready2, true), "evaluated reembedding"));
        group(() -> truth(!mayPromoteReembedding(ready1, ready2, false), "evaluation gate"));

        SourceState acl = publication.updateAcl("tenant-a", "handbook", 4);
        group(() -> truth(acl.aclVersion() == 4, "ACL advances"));
        group(() -> truth(publication.fetch("tenant-a", "handbook", ready2.id(), 0)
                == FetchOutcome.DENY_ACL, "stale ACL denied at use"));
        group(() -> truth(publication.fetch("tenant-a", "handbook", ready2.id(), 4)
                == FetchOutcome.ALLOW, "current ACL admitted"));
        publication.tombstone("tenant-a", "handbook");
        group(() -> truth(publication.fetch("tenant-a", "handbook", ready2.id(), 4)
                == FetchOutcome.DENY_TOMBSTONE, "tombstone immediate"));

        List<String> paths = List.of("h1/introduction", "h2/retry", "h2/publication");
        group(() -> truth(chunkIds(v1, paths).equals(chunkIds(v1, paths)), "stable chunk IDs"));
        group(() -> truth(chunkIds(v1, paths).size() == 3, "one ID per chunk"));

        Random random = new Random(18_2026L);
        for (int trial = 0; trial < 10_000; trial++) {
            long maxCompressed = 1 + random.nextInt(1_000_000);
            long maxExpanded = 1 + random.nextInt(5_000_000);
            int maxPages = 1 + random.nextInt(500);
            long maxCpu = 1 + random.nextInt(50_000);
            Policy p = new Policy(maxCompressed, maxExpanded, maxPages, maxCpu,
                    List.of("application/pdf"));
            ParseLimits a = new ParseLimits(random.nextInt(1_500_000),
                    random.nextInt(7_000_000), random.nextInt(700),
                    random.nextInt(70_000), random.nextBoolean() ? "application/pdf" : "text/html");
            boolean oracle = a.compressedBytes() <= maxCompressed
                    && a.expandedBytes() <= maxExpanded && a.pages() <= maxPages
                    && a.cpuMillis() <= maxCpu && a.mediaType().equals("application/pdf");
            truth(p.admits(a) == oracle, "policy oracle trial " + trial);
        }

        System.out.println("PASS " + scenarios + " scenario groups; 10000 seeded trials");
        System.out.println("PARITY|IDENTITY=FULL_REVISION|EXECUTION=AT_LEAST_ONCE|STAGES=RESUMABLE"
                + "|PUBLICATION=GENERATION_CAS|DELETE=TOMBSTONE|ACL=CHECK_AT_USE"
                + "|EMBEDDING=SPACE_VERSIONED|READY=" + scenarios);
    }
}
