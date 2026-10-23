import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic, dependency-free retrieval contracts for the offline lab. */
public final class OfflineRetrievalLabContracts {
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");
    private static final Pattern CITATION = Pattern.compile("\\[([A-Za-z0-9_-]+)]");

    record Document(String id, String tenant, int revision, String text) {}

    record ScoredDocument(double score, Document document) {}

    record RankedDocument(String id, double score) {}

    record EvidenceResult(String outcome, List<String> ids) {}

    private static final List<Document> CORPUS = List.of(
            new Document(
                    "payments-r7",
                    "acme",
                    7,
                    "Payments rollback: restore the previous verified image digest. "
                            + "Check database migration compatibility before rollback."),
            new Document(
                    "cache-r2",
                    "acme",
                    2,
                    "Prevent cache stampede with bounded single flight and randomized TTL. "
                            + "A Redis lock needs ownership-aware release."),
            new Document(
                    "vectors-r3",
                    "acme",
                    3,
                    "Embedding migration requires a new index generation. "
                            + "Never mix vectors from incompatible embedding models."),
            new Document(
                    "private-r1",
                    "globex",
                    1,
                    "Payments rollback payments rollback uses the confidential Globex recovery procedure."));

    static Map<String, Integer> terms(String text) {
        var counts = new HashMap<String, Integer>();
        var matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) counts.merge(matcher.group(), 1, Integer::sum);
        return counts;
    }

    static double lexicalScore(String query, String text) {
        var queryTerms = terms(query);
        var documentTerms = terms(text);
        double querySquare = 0.0;
        double documentSquare = 0.0;
        double dot = 0.0;
        for (int count : queryTerms.values()) querySquare += count * count;
        for (int count : documentTerms.values()) documentSquare += count * count;
        if (querySquare == 0.0 || documentSquare == 0.0) return 0.0;
        for (var entry : queryTerms.entrySet()) {
            dot += entry.getValue() * documentTerms.getOrDefault(entry.getKey(), 0);
        }
        return dot / Math.sqrt(querySquare * documentSquare);
    }

    static List<ScoredDocument> retrieve(String query, String trustedTenant, int topK) {
        if (topK < 1 || topK > 20) throw new IllegalArgumentException("topK must be in [1, 20]");
        if (trustedTenant == null || trustedTenant.isBlank()) {
            throw new IllegalArgumentException("trusted tenant is required");
        }

        var scored = new ArrayList<ScoredDocument>();
        // Scope first: an ineligible document never becomes a scored candidate.
        for (var document : CORPUS) {
            if (!document.tenant().equals(trustedTenant)) continue;
            double score = lexicalScore(query, document.text());
            if (score > 0.0) scored.add(new ScoredDocument(score, document));
        }
        scored.sort((left, right) -> {
            int byScore = Double.compare(right.score(), left.score());
            return byScore != 0 ? byScore : left.document().id().compareTo(right.document().id());
        });
        return List.copyOf(scored.subList(0, Math.min(topK, scored.size())));
    }

    static double cosine(double[] left, double[] right) {
        if (left.length == 0 || left.length != right.length) {
            throw new IllegalArgumentException("non-empty equal dimensions required");
        }
        double leftScale = 0.0;
        double rightScale = 0.0;
        for (int index = 0; index < left.length; index++) {
            if (!Double.isFinite(left[index]) || !Double.isFinite(right[index])) {
                throw new IllegalArgumentException("finite vector coordinates required");
            }
            leftScale = Math.max(leftScale, Math.abs(left[index]));
            rightScale = Math.max(rightScale, Math.abs(right[index]));
        }
        if (leftScale == 0.0 || rightScale == 0.0) {
            throw new IllegalArgumentException("zero vector has no cosine direction");
        }

        double leftSquare = 0.0;
        double rightSquare = 0.0;
        double dot = 0.0;
        for (int index = 0; index < left.length; index++) {
            double scaledLeft = left[index] / leftScale;
            double scaledRight = right[index] / rightScale;
            leftSquare += scaledLeft * scaledLeft;
            rightSquare += scaledRight * scaledRight;
            dot += scaledLeft * scaledRight;
        }
        double result = dot / Math.sqrt(leftSquare * rightSquare);
        if (!Double.isFinite(result)) throw new IllegalArgumentException("cosine result must be finite");
        return Math.max(-1.0, Math.min(1.0, result));
    }

    static List<RankedDocument> rrf(List<List<String>> rankings, int rankConstant) {
        if (rankConstant < 1) throw new IllegalArgumentException("rank constant must be positive");
        var scores = new HashMap<String, Double>();
        for (var ranking : rankings) {
            var seen = new HashSet<String>();
            int rank = 0;
            for (var documentId : ranking) {
                if (documentId == null || documentId.isBlank() || !seen.add(documentId)) continue;
                rank++;
                scores.merge(documentId, 1.0 / (rankConstant + rank), Double::sum);
            }
        }
        var result = new ArrayList<RankedDocument>();
        scores.forEach((id, score) -> result.add(new RankedDocument(id, score)));
        result.sort((left, right) -> {
            int byScore = Double.compare(right.score(), left.score());
            return byScore != 0 ? byScore : left.id().compareTo(right.id());
        });
        return List.copyOf(result);
    }

    static Set<String> validateCitations(String answer, Set<String> allowedIds) {
        var cited = new LinkedHashSet<String>();
        var matcher = CITATION.matcher(answer);
        while (matcher.find()) cited.add(matcher.group(1));
        if (cited.isEmpty() || !allowedIds.containsAll(cited)) {
            throw new IllegalArgumentException("missing or unknown evidence citation");
        }
        return Set.copyOf(cited); // Membership is necessary, but does not prove entailment.
    }

    static EvidenceResult evidenceOrAbstain(String query, String trustedTenant) {
        var evidence = retrieve(query, trustedTenant, 3);
        if (evidence.isEmpty()) return new EvidenceResult("NO_EVIDENCE", List.of());
        return new EvidenceResult(
                "EVIDENCE", evidence.stream().map(item -> item.document().id()).toList());
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static void close(double actual, double expected) {
        assert Math.abs(actual - expected) <= 1e-12 : actual + " != " + expected;
    }

    public static void main(String[] args) {
        var acme = retrieve("payments rollback", "acme", 3);
        assert acme.stream().map(item -> item.document().id()).toList().equals(List.of("payments-r7"));
        assert acme.stream().allMatch(item -> item.document().tenant().equals("acme"));
        assert lexicalScore("payments rollback", CORPUS.get(3).text()) > acme.get(0).score();
        assert acme.stream().noneMatch(item -> item.document().id().equals("private-r1"));
        assert evidenceOrAbstain("payments rollback", "unknown")
                .equals(new EvidenceResult("NO_EVIDENCE", List.of()));
        assert evidenceOrAbstain("quasar", "acme")
                .equals(new EvidenceResult("NO_EVIDENCE", List.of()));
        assert retrieve("payments rollback", "acme", 1).get(0).document().id().equals("payments-r7");
        rejects(() -> retrieve("payments", "acme", 0));
        rejects(() -> retrieve("payments", "acme", 21));
        rejects(() -> retrieve("payments", " ", 3));

        assert terms("rollback rollback payments").equals(Map.of("rollback", 2, "payments", 1));
        close(lexicalScore("", "payments"), 0.0);
        assert lexicalScore("payments rollback", CORPUS.get(0).text()) > 0.0;
        assert lexicalScore("payments rollback", CORPUS.get(0).text()) <= 1.0;

        close(cosine(new double[] {1.0, 0.0}, new double[] {1.0, 0.0}), 1.0);
        close(cosine(new double[] {1.0, 0.0}, new double[] {0.0, 1.0}), 0.0);
        close(cosine(new double[] {1.0, 0.0}, new double[] {-1.0, 0.0}), -1.0);
        close(cosine(new double[] {1e308, 1e308}, new double[] {1e308, 1e308}), 1.0);
        rejects(() -> cosine(new double[] {}, new double[] {}));
        rejects(() -> cosine(new double[] {1.0}, new double[] {1.0, 2.0}));
        rejects(() -> cosine(new double[] {0.0, 0.0}, new double[] {1.0, 0.0}));
        rejects(() -> cosine(new double[] {Double.NaN}, new double[] {1.0}));
        rejects(() -> cosine(new double[] {Double.POSITIVE_INFINITY}, new double[] {1.0}));

        var fused = rrf(
                List.of(
                        List.of("payments-r7", "cache-r2", "payments-r7"),
                        List.of("cache-r2", "payments-r7", "vectors-r3")),
                60);
        assert fused.stream().map(RankedDocument::id).toList()
                .equals(List.of("cache-r2", "payments-r7", "vectors-r3"));
        close(fused.get(0).score(), 1.0 / 61 + 1.0 / 62);
        close(fused.get(1).score(), 1.0 / 61 + 1.0 / 62);
        assert rrf(List.of(List.of()), 60).isEmpty();
        rejects(() -> rrf(List.of(List.of("payments-r7")), 0));

        var allowed = new HashSet<String>();
        retrieve("payments rollback", "acme", 3)
                .forEach(item -> allowed.add(item.document().id()));
        assert validateCitations("Rollback from the verified digest [payments-r7].", allowed)
                .equals(Set.of("payments-r7"));
        rejects(() -> validateCitations("Rollback now.", allowed));
        rejects(() -> validateCitations("Use the private runbook [private-r1].", allowed));

        var parity = String.join(",", fused.stream().map(RankedDocument::id).toList());
        System.out.println("PARITY|payments-r7|NO_EVIDENCE|" + parity + "|1");
        System.out.println("OfflineRetrievalLabContracts: 31 scenario groups passed");
    }
}
