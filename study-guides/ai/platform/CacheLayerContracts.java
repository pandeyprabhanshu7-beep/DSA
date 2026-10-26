import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Random;

/** Dependency-free Java 17 contracts for AI-platform cache identity and reuse. */
public final class CacheLayerContracts {
    private CacheLayerContracts() {}

    record EmbeddingKey(String contentHash, String embeddingModel,
                        String preprocessingRevision) {}

    record RetrievalKey(String tenant, String subject, long aclEpoch,
                        long corpusGeneration, String queryHash, String filterHash,
                        String retrievalRevision) {}

    record AnswerKey(String tenant, String subject, long aclEpoch,
                     long corpusGeneration, String evidenceHash, String promptRevision,
                     String modelRevision, String optionsHash, String requestHash) {}

    record PrefixKey(String modelRevision, String tokenizerRevision,
                     String adapterRevision, String cacheSalt, String tokenHash,
                     String runtimeRevision) {}

    record SemanticCandidate(String tenant, long aclEpoch, long corpusGeneration,
                             String environment, String product, double distance,
                             boolean personalized, boolean timeSensitive,
                             boolean mutationDependent) {}

    static EmbeddingKey embeddingKey(String content, String model,
                                     String preprocessingRevision) {
        return new EmbeddingKey(
                hashFramed(normalizeContent(content)),
                required(model, "embeddingModel"),
                required(preprocessingRevision, "preprocessingRevision"));
    }

    static RetrievalKey retrievalKey(String verifiedTenant, String requestedTenant,
                                     String subject, long aclEpoch, long corpusGeneration,
                                     String query, String filters, String retrievalRevision) {
        String tenant = required(verifiedTenant, "verifiedTenant");
        if (!tenant.equals(required(requestedTenant, "requestedTenant"))) {
            throw new SecurityException("requested tenant is not the verified tenant");
        }
        requireNonNegative(aclEpoch, "aclEpoch");
        requireNonNegative(corpusGeneration, "corpusGeneration");
        return new RetrievalKey(tenant, required(subject, "subject"), aclEpoch,
                corpusGeneration, hashFramed(normalizeQuery(query)),
                hashFramed(required(filters, "filters")),
                required(retrievalRevision, "retrievalRevision"));
    }

    static AnswerKey answerKey(RetrievalKey retrieval, String evidenceHash,
                               String promptRevision, String modelRevision,
                               String options, String request) {
        Objects.requireNonNull(retrieval, "retrieval");
        return new AnswerKey(retrieval.tenant(), retrieval.subject(), retrieval.aclEpoch(),
                retrieval.corpusGeneration(), required(evidenceHash, "evidenceHash"),
                required(promptRevision, "promptRevision"),
                required(modelRevision, "modelRevision"),
                hashFramed(required(options, "options")),
                hashFramed(normalizeQuery(request)));
    }

    static PrefixKey prefixKey(String modelRevision, String tokenizerRevision,
                               String adapterRevision, String cacheSalt,
                               int[] exactTokens, String runtimeRevision) {
        Objects.requireNonNull(exactTokens, "exactTokens");
        if (exactTokens.length == 0) throw new IllegalArgumentException("tokens are empty");
        return new PrefixKey(required(modelRevision, "modelRevision"),
                required(tokenizerRevision, "tokenizerRevision"),
                required(adapterRevision, "adapterRevision"),
                required(cacheSalt, "cacheSalt"), hashTokens(exactTokens),
                required(runtimeRevision, "runtimeRevision"));
    }

    static boolean retrievalHitIsCurrent(RetrievalKey stored, RetrievalKey current) {
        return Objects.requireNonNull(stored, "stored").equals(
                Objects.requireNonNull(current, "current"));
    }

    static boolean publishAnswer(long activeGeneration, AnswerKey candidate) {
        requireNonNegative(activeGeneration, "activeGeneration");
        return Objects.requireNonNull(candidate, "candidate").corpusGeneration()
                == activeGeneration;
    }

    static boolean semanticAnswerReuse(SemanticCandidate candidate, String tenant,
                                       long aclEpoch, long corpusGeneration,
                                       String environment, String product,
                                       double maximumDistance) {
        Objects.requireNonNull(candidate, "candidate");
        if (!Double.isFinite(maximumDistance) || maximumDistance < 0.0) {
            throw new IllegalArgumentException("maximumDistance must be finite and nonnegative");
        }
        return !candidate.personalized()
                && !candidate.timeSensitive()
                && !candidate.mutationDependent()
                && candidate.tenant().equals(required(tenant, "tenant"))
                && candidate.aclEpoch() == aclEpoch
                && candidate.corpusGeneration() == corpusGeneration
                && candidate.environment().equals(required(environment, "environment"))
                && candidate.product().equals(required(product, "product"))
                && Double.isFinite(candidate.distance())
                && candidate.distance() >= 0.0
                && candidate.distance() <= maximumDistance;
    }

    static boolean prefixCanReuse(PrefixKey stored, PrefixKey requested) {
        return Objects.requireNonNull(stored, "stored").equals(
                Objects.requireNonNull(requested, "requested"));
    }

    static String hashFramed(String... fields) {
        MessageDigest digest = sha256();
        for (String field : fields) {
            byte[] bytes = required(field, "hash field").getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String hashTokens(int[] tokens) {
        MessageDigest digest = sha256();
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(tokens.length).array());
        for (int token : tokens) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(token).array());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java 17 must provide SHA-256", impossible);
        }
    }

    private static String normalizeContent(String value) {
        return Normalizer.normalize(required(value, "content"), Normalizer.Form.NFC)
                .replace("\r\n", "\n").strip();
    }

    private static String normalizeQuery(String value) {
        return Normalizer.normalize(required(value, "query"), Normalizer.Form.NFC)
                .strip().replaceAll("\\s+", " ");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is blank");
        return value;
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " is negative");
    }

    private static int scenarios;

    private static void group(Runnable check) {
        check.run();
        scenarios++;
    }

    private static void truth(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) return;
            throw new AssertionError("expected " + expected + ", got " + actual, actual);
        }
        throw new AssertionError("expected " + expected.getSimpleName());
    }

    public static void main(String[] args) {
        group(() -> truth(embeddingKey(" café\r\n", "embed@1", "nfc@1")
                .equals(embeddingKey("cafe\u0301\n", "embed@1", "nfc@1")), "NFC/newline normalization"));
        group(() -> truth(!embeddingKey("text", "embed@1", "prep@1")
                .equals(embeddingKey("text", "embed@2", "prep@1")), "model fence"));
        group(() -> truth(!embeddingKey("text", "embed@1", "prep@1")
                .equals(embeddingKey("text", "embed@1", "prep@2")), "preprocessing fence"));
        group(() -> truth(!hashFramed("ab", "c").equals(hashFramed("a", "bc")), "framing"));

        RetrievalKey r7 = retrievalKey("tenant-a", "tenant-a", "alice", 12, 42,
                " reset   password ", "product=pay", "hybrid@3");
        group(() -> truth(r7.equals(retrievalKey("tenant-a", "tenant-a", "alice", 12, 42,
                "reset password", "product=pay", "hybrid@3")), "query normalization"));
        group(() -> rejects(SecurityException.class, () -> retrievalKey("tenant-a", "tenant-b",
                "alice", 12, 42, "q", "f", "r")));
        group(() -> truth(!retrievalHitIsCurrent(r7, retrievalKey("tenant-a", "tenant-a", "alice",
                13, 42, "reset password", "product=pay", "hybrid@3")), "ACL epoch"));
        group(() -> truth(!retrievalHitIsCurrent(r7, retrievalKey("tenant-a", "tenant-a", "alice",
                12, 43, "reset password", "product=pay", "hybrid@3")), "corpus generation"));
        group(() -> truth(!retrievalHitIsCurrent(r7, retrievalKey("tenant-a", "tenant-a", "bob",
                12, 42, "reset password", "product=pay", "hybrid@3")), "subject scope"));
        group(() -> truth(!retrievalHitIsCurrent(r7, retrievalKey("tenant-a", "tenant-a", "alice",
                12, 42, "reset password", "product=fraud", "hybrid@3")), "filter fence"));

        AnswerKey a42 = answerKey(r7, "evidence:7", "prompt@5", "model@9",
                "temperature=0", "How do I reset a password?");
        group(() -> truth(publishAnswer(42, a42), "current answer publication"));
        group(() -> truth(!publishAnswer(43, a42), "stale answer resurrection fence"));
        group(() -> truth(!a42.equals(answerKey(r7, "evidence:8", "prompt@5", "model@9",
                "temperature=0", "How do I reset a password?")), "evidence fence"));
        group(() -> truth(!a42.equals(answerKey(r7, "evidence:7", "prompt@6", "model@9",
                "temperature=0", "How do I reset a password?")), "prompt fence"));
        group(() -> truth(!a42.equals(answerKey(r7, "evidence:7", "prompt@5", "model@10",
                "temperature=0", "How do I reset a password?")), "model fence"));

        SemanticCandidate safe = new SemanticCandidate("tenant-a", 12, 42,
                "staging", "payments", 0.04, false, false, false);
        group(() -> truth(semanticAnswerReuse(safe, "tenant-a", 12, 42,
                "staging", "payments", 0.05), "bounded semantic reuse"));
        group(() -> truth(!semanticAnswerReuse(safe, "tenant-a", 12, 42,
                "production", "payments", 0.05), "environment mismatch"));
        group(() -> truth(!semanticAnswerReuse(new SemanticCandidate("tenant-a", 12, 42,
                "staging", "payments", 0.04, false, true, false), "tenant-a", 12, 42,
                "staging", "payments", 0.05), "time-sensitive denial"));
        group(() -> truth(!semanticAnswerReuse(new SemanticCandidate("tenant-a", 12, 42,
                "staging", "payments", 0.04, false, false, true), "tenant-a", 12, 42,
                "staging", "payments", 0.05), "mutation denial"));
        group(() -> truth(!semanticAnswerReuse(safe, "tenant-a", 12, 42,
                "staging", "payments", 0.03), "distance gate"));

        PrefixKey prefix = prefixKey("model@9", "tok@3", "base", "tenant-a",
                new int[]{101, 202, 303}, "runtime@4");
        group(() -> truth(prefixCanReuse(prefix, prefixKey("model@9", "tok@3", "base",
                "tenant-a", new int[]{101, 202, 303}, "runtime@4")), "exact prefix"));
        group(() -> truth(!prefixCanReuse(prefix, prefixKey("model@9", "tok@3", "base",
                "tenant-b", new int[]{101, 202, 303}, "runtime@4")), "salt isolation"));
        group(() -> truth(!prefixCanReuse(prefix, prefixKey("model@9", "tok@4", "base",
                "tenant-a", new int[]{101, 202, 303}, "runtime@4")), "tokenizer fence"));
        group(() -> truth(!prefixCanReuse(prefix, prefixKey("model@9", "tok@3", "base",
                "tenant-a", new int[]{101, 202, 304}, "runtime@4")), "exact token fence"));
        group(() -> truth(!prefixCanReuse(prefix, prefixKey("model@9", "tok@3", "lora@2",
                "tenant-a", new int[]{101, 202, 303}, "runtime@4")), "adapter fence"));
        group(() -> rejects(IllegalArgumentException.class, () -> prefixKey("m", "t", "a",
                "s", new int[0], "r")));
        group(() -> rejects(IllegalArgumentException.class, () -> semanticAnswerReuse(safe,
                "tenant-a", 12, 42, "staging", "payments", Double.NaN)));
        group(() -> rejects(IllegalArgumentException.class, () -> retrievalKey("t", "t", "s",
                -1, 1, "q", "f", "r")));

        Random random = new Random(0xCACE16L);
        int trials = 10_000;
        for (int i = 0; i < trials; i++) {
            long acl = random.nextInt(8), generation = random.nextInt(8);
            String tenant = "t" + random.nextInt(4);
            RetrievalKey stored = retrievalKey(tenant, tenant, "s", acl,
                    generation, "q" + i, "f0", "r1");
            boolean mutate = random.nextBoolean();
            RetrievalKey current = mutate
                    ? retrievalKey(stored.tenant(), stored.tenant(), stored.subject(), acl + 1,
                    generation, "q" + i, "f0", "r1")
                    : retrievalKey(stored.tenant(), stored.tenant(), stored.subject(), acl,
                    generation, "q" + i, "f0", "r1");
            boolean oracle = !mutate;
            truth(retrievalHitIsCurrent(stored, current) == oracle, "retrieval oracle " + i);

            double distance = random.nextDouble() * 0.1;
            boolean timeSensitive = random.nextInt(7) == 0;
            SemanticCandidate candidate = new SemanticCandidate("t0", acl, generation,
                    "prod", "pay", distance, false, timeSensitive, false);
            boolean expected = !timeSensitive && distance <= 0.05;
            truth(semanticAnswerReuse(candidate, "t0", acl, generation,
                    "prod", "pay", 0.05) == expected, "semantic oracle " + i);
        }

        System.out.println("Cache layer contracts passed: " + scenarios
                + " scenario groups + " + trials + " seeded dual-oracle trials");
        System.out.println("LAYERS=EMBEDDING_RETRIEVAL_ANSWER_PREFIX|SCOPE=TENANT_ACL_GENERATION"
                + "|SEMANTIC=ENTITY_AND_RISK_GATED|PUBLICATION=GENERATION_FENCED"
                + "|PREFIX=EXACT_TOKENS_AND_RUNTIME");
    }
}
