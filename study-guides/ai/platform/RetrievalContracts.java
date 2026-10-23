import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Java 17 source-file program for inspectable retrieval, fusion, and reranking contracts. */
public final class RetrievalContracts {
    record TextDocument(String id, String tenant, String body, double[] vector) {
        TextDocument {
            requireText(id, "document id");
            requireText(tenant, "tenant");
            requireText(body, "body");
            vector = vector.clone();
            validateVector(vector);
        }

        @Override public double[] vector() { return vector.clone(); }
    }

    record Ranked(String id, double score) { }

    static List<String> tokens(String text) {
        requireText(text, "text");
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_]+"))
                .filter(token -> !token.isBlank())
                .toList();
    }

    /**
     * Small, inspectable BM25 reference. Production search must use the same analyzer and
     * field statistics as the serving index; this method is an executable teaching oracle.
     */
    static List<Ranked> bm25(List<TextDocument> documents, String query, double k1, double b) {
        if (documents.isEmpty()) throw new IllegalArgumentException("documents required");
        if (!Double.isFinite(k1) || k1 < 0 || !Double.isFinite(b) || b < 0 || b > 1) {
            throw new IllegalArgumentException("require finite k1 >= 0 and 0 <= b <= 1");
        }
        Set<String> queryTerms = new LinkedHashSet<>(tokens(query));
        if (queryTerms.isEmpty()) throw new IllegalArgumentException("query terms required");

        List<List<String>> corpusTokens = documents.stream().map(d -> tokens(d.body())).toList();
        double averageLength = corpusTokens.stream().mapToInt(List::size).average().orElseThrow();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String term : queryTerms) {
            int df = 0;
            for (List<String> documentTokens : corpusTokens) {
                if (documentTokens.contains(term)) df++;
            }
            documentFrequency.put(term, df);
        }

        List<Ranked> result = new ArrayList<>();
        for (int index = 0; index < documents.size(); index++) {
            List<String> documentTokens = corpusTokens.get(index);
            Map<String, Long> termFrequency = new HashMap<>();
            documentTokens.forEach(term -> termFrequency.merge(term, 1L, Long::sum));
            double score = 0;
            for (String term : queryTerms) {
                long frequency = termFrequency.getOrDefault(term, 0L);
                if (frequency == 0) continue;
                int df = documentFrequency.get(term);
                double idf = Math.log(1.0 + (documents.size() - df + 0.5) / (df + 0.5));
                double lengthNorm = 1.0 - b + b * documentTokens.size() / averageLength;
                score += idf * (frequency * (k1 + 1.0)) / (frequency + k1 * lengthNorm);
            }
            if (score > 0) result.add(new Ranked(documents.get(index).id(), score));
        }
        return sorted(result);
    }

    static List<Ranked> exactCosine(List<TextDocument> documents,
                                    String trustedTenant,
                                    double[] queryVector) {
        requireText(trustedTenant, "trusted tenant");
        validateVector(queryVector);
        List<Ranked> result = new ArrayList<>();
        for (TextDocument document : documents) {
            if (document.tenant().equals(trustedTenant)) {
                result.add(new Ranked(document.id(), cosine(queryVector, document.vector())));
            }
        }
        return sorted(result);
    }

    static double cosine(double[] left, double[] right) {
        validateVector(left);
        validateVector(right);
        if (left.length != right.length) throw new IllegalArgumentException("dimension mismatch");
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) throw new IllegalArgumentException("zero vector");
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /** Combine positions, never incomparable raw lexical/vector scores. Ranks start at one. */
    static List<Ranked> reciprocalRankFusion(List<List<String>> rankings, int rankConstant) {
        if (rankings.size() < 2) throw new IllegalArgumentException("at least two rankings required");
        if (rankConstant < 1) throw new IllegalArgumentException("rank constant must be positive");
        Map<String, Double> scores = new LinkedHashMap<>();
        for (List<String> ranking : rankings) {
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < ranking.size(); index++) {
                String id = ranking.get(index);
                requireText(id, "ranked id");
                if (!seen.add(id)) throw new IllegalArgumentException("duplicate id in one ranking");
                scores.merge(id, 1.0 / (rankConstant + index + 1.0), Double::sum);
            }
        }
        return sorted(scores.entrySet().stream()
                .map(entry -> new Ranked(entry.getKey(), entry.getValue()))
                .toList());
    }

    record Candidate(String id,
                     String tenant,
                     boolean aclAllows,
                     double fusedScore,
                     double rerankerScore,
                     int estimatedTokens) {
        Candidate {
            requireText(id, "candidate id");
            requireText(tenant, "candidate tenant");
            if (!Double.isFinite(fusedScore) || !Double.isFinite(rerankerScore)) {
                throw new IllegalArgumentException("scores must be finite");
            }
            if (estimatedTokens <= 0) throw new IllegalArgumentException("token estimate must be positive");
        }
    }

    record RerankResult(List<String> sentToReranker,
                        List<String> finalOrder,
                        boolean usedFallback,
                        int admittedTokens) {
        RerankResult {
            sentToReranker = List.copyOf(sentToReranker);
            finalOrder = List.copyOf(finalOrder);
        }
    }

    /**
     * ACL and tenant filtering happen before an external pair scorer sees text. The candidate
     * and token budgets are hard limits. A timeout retains the deterministic fused order.
     */
    static RerankResult rerank(List<Candidate> candidates,
                               String trustedTenant,
                               int maxCandidates,
                               int maxTokens,
                               boolean rerankerCompleted) {
        requireText(trustedTenant, "trusted tenant");
        if (maxCandidates <= 0 || maxTokens <= 0) {
            throw new IllegalArgumentException("reranker budgets must be positive");
        }
        List<Candidate> eligible = candidates.stream()
                .filter(candidate -> candidate.tenant().equals(trustedTenant) && candidate.aclAllows())
                .sorted(Comparator.comparingDouble(Candidate::fusedScore).reversed()
                        .thenComparing(Candidate::id))
                .toList();

        List<Candidate> admitted = new ArrayList<>();
        int tokens = 0;
        for (Candidate candidate : eligible) {
            if (admitted.size() == maxCandidates) break;
            if (tokens + candidate.estimatedTokens() > maxTokens) continue;
            admitted.add(candidate);
            tokens += candidate.estimatedTokens();
        }
        List<String> sent = admitted.stream().map(Candidate::id).toList();
        List<Candidate> finalCandidates = new ArrayList<>(admitted);
        if (rerankerCompleted) {
            finalCandidates.sort(Comparator.comparingDouble(Candidate::rerankerScore).reversed()
                    .thenComparing(Candidate::id));
        }
        return new RerankResult(sent,
                finalCandidates.stream().map(Candidate::id).toList(),
                !rerankerCompleted,
                tokens);
    }

    static long rawVectorBytes(long vectors, int dimensions, int bytesPerCoordinate) {
        if (vectors <= 0 || dimensions <= 0 || bytesPerCoordinate <= 0) {
            throw new IllegalArgumentException("positive vector sizing inputs required");
        }
        return Math.multiplyExact(Math.multiplyExact(vectors, dimensions), bytesPerCoordinate);
    }

    static List<Ranked> sorted(List<Ranked> scores) {
        return scores.stream()
                .sorted(Comparator.comparingDouble(Ranked::score).reversed().thenComparing(Ranked::id))
                .toList();
    }

    static void validateVector(double[] vector) {
        if (vector == null || vector.length == 0) throw new IllegalArgumentException("vector required");
        for (double value : vector) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("vector values must be finite");
        }
    }

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
    }

    static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void expectClose(double actual, double expected, double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
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
        List<TextDocument> corpus = List.of(
                new TextDocument("pay-code", "acme", "ERR_PAY_042 retry payment gateway", new double[]{1, 0}),
                new TextDocument("pay-overview", "acme", "payment failures and recovery overview", new double[]{0.8, 0.6}),
                new TextDocument("other-tenant", "beta", "ERR_PAY_042 confidential beta procedure", new double[]{1, 0})
        );

        List<Ranked> lexical = bm25(corpus, "ERR_PAY_042", 1.2, 0.75);
        expect(lexical.size() == 2, "exact identifier should match the two documents containing it");
        expect(Set.of("pay-code", "other-tenant").equals(
                Set.of(lexical.get(0).id(), lexical.get(1).id())), "identifier matches should be preserved");
        groups++;

        expectThrows(IllegalArgumentException.class, () -> bm25(corpus, "ERR", -1, 0.75),
                "negative k1 must fail");
        expectThrows(IllegalArgumentException.class, () -> bm25(corpus, "ERR", 1.2, 1.1),
                "b outside [0,1] must fail");
        groups += 2;

        List<Ranked> exact = exactCosine(corpus, "acme", new double[]{1, 0});
        expect(exact.size() == 2, "trusted tenant must be applied before vector ranking");
        expect("pay-code".equals(exact.get(0).id()), "exact semantic rank should put identical vector first");
        expect(exact.stream().noneMatch(hit -> hit.id().equals("other-tenant")),
                "cross-tenant vector must not enter candidates");
        groups++;

        expectClose(cosine(new double[]{1, 0}, new double[]{0.8, 0.6}), 0.8, 1e-12,
                "cosine trace mismatch");
        expectThrows(IllegalArgumentException.class, () -> cosine(new double[]{1}, new double[]{1, 0}),
                "dimension mismatch must fail");
        groups += 2;

        long oneMillion = rawVectorBytes(1_000_000, 768, 4);
        expect(oneMillion == 3_072_000_000L, "raw float32 vector estimate mismatch");
        expectClose(oneMillion / Math.pow(1024, 3), 2.86102294921875, 1e-12,
                "GiB conversion mismatch");
        expectThrows(ArithmeticException.class, () -> rawVectorBytes(Long.MAX_VALUE, 768, 4),
                "overflowing capacity estimate must fail");
        groups += 2;

        List<Ranked> fused = reciprocalRankFusion(List.of(
                List.of("A", "X", "B", "Y"),
                List.of("B", "C", "D", "A")
        ), 60);
        Map<String, Double> fusedById = new HashMap<>();
        fused.forEach(hit -> fusedById.put(hit.id(), hit.score()));
        expectClose(fusedById.get("A"), 1.0 / 61 + 1.0 / 64, 1e-12, "A RRF mismatch");
        expectClose(fusedById.get("B"), 1.0 / 63 + 1.0 / 61, 1e-12, "B RRF mismatch");
        expect("B".equals(fused.get(0).id()), "B should narrowly win the source trace");
        groups++;

        expectThrows(IllegalArgumentException.class,
                () -> reciprocalRankFusion(List.of(List.of("A", "A"), List.of("B")), 60),
                "duplicate ids in one ranking must fail");
        groups++;

        List<Candidate> candidates = List.of(
                new Candidate("runbook-r7", "acme", true, 0.032, 0.71, 420),
                new Candidate("incident-42", "acme", true, 0.031, 0.94, 500),
                new Candidate("long-appendix", "acme", true, 0.030, 0.99, 900),
                new Candidate("revoked", "acme", false, 0.040, 1.00, 100),
                new Candidate("beta-secret", "beta", true, 0.050, 1.00, 100)
        );
        RerankResult reranked = rerank(candidates, "acme", 3, 1_000, true);
        expect(reranked.sentToReranker().equals(List.of("runbook-r7", "incident-42")),
                "ACL, tenant, and token budgets must apply before reranking");
        expect(reranked.finalOrder().equals(List.of("incident-42", "runbook-r7")),
                "completed reranker should reorder the admitted pair");
        expect(reranked.admittedTokens() == 920 && !reranked.usedFallback(),
                "reranker accounting mismatch");
        groups++;

        RerankResult timeout = rerank(candidates, "acme", 3, 1_000, false);
        expect(timeout.finalOrder().equals(List.of("runbook-r7", "incident-42")),
                "timeout fallback must preserve deterministic fused order");
        expect(timeout.usedFallback(), "timeout must be observable");
        groups++;

        expectThrows(IllegalArgumentException.class,
                () -> rerank(candidates, "acme", 0, 1_000, true),
                "zero candidate budget must fail");
        groups++;

        System.out.println("RetrievalContracts: " + groups + " scenario groups passed");
    }
}
