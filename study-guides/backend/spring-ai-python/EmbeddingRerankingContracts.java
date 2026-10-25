import java.util.Arrays;

/** Dependency-free contracts for embedding retrieval, reranking, evaluation and runtime parity. */
public final class EmbeddingRerankingContracts {
    enum Baseline { CLASSICAL_FIRST, EMBEDDING_RETRIEVAL }
    enum Deploy { PYTHON_SERVICE, JVM_ONNX, REJECT }

    static double cosine(double[] left, double[] right) {
        if (left.length == 0 || left.length != right.length) {
            throw new IllegalArgumentException("dimensions");
        }
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            if (!Double.isFinite(left[i]) || !Double.isFinite(right[i])) {
                throw new IllegalArgumentException("non-finite");
            }
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) throw new IllegalArgumentException("zero norm");
        return dot / Math.sqrt(leftNorm * rightNorm);
    }

    static int[] topK(double[][] documents, double[] query, int k) {
        if (k < 1 || k > documents.length) throw new IllegalArgumentException("k");
        Integer[] ids = new Integer[documents.length];
        double[] scores = new double[documents.length];
        for (int i = 0; i < documents.length; i++) {
            ids[i] = i;
            scores[i] = cosine(documents[i], query);
        }
        Arrays.sort(ids, (a, b) -> {
            int scoreOrder = Double.compare(scores[b], scores[a]);
            return scoreOrder != 0 ? scoreOrder : Integer.compare(a, b);
        });
        return Arrays.stream(ids).limit(k).mapToInt(Integer::intValue).toArray();
    }

    static int[] rerank(int[] candidateIds, double[] pairScores, int limit) {
        if (candidateIds.length == 0 || candidateIds.length != pairScores.length
                || limit < 1 || limit > candidateIds.length) throw new IllegalArgumentException("rerank");
        Integer[] positions = new Integer[candidateIds.length];
        for (int i = 0; i < positions.length; i++) {
            if (candidateIds[i] < 0 || !Double.isFinite(pairScores[i])) {
                throw new IllegalArgumentException("candidate");
            }
            positions[i] = i;
        }
        Arrays.sort(positions, (a, b) -> {
            int scoreOrder = Double.compare(pairScores[b], pairScores[a]);
            return scoreOrder != 0 ? scoreOrder : Integer.compare(candidateIds[a], candidateIds[b]);
        });
        return Arrays.stream(positions).limit(limit).mapToInt(i -> candidateIds[i]).toArray();
    }

    static Baseline chooseBaseline(boolean stableLabels, boolean generationRequired) {
        return stableLabels && !generationRequired
                ? Baseline.CLASSICAL_FIRST : Baseline.EMBEDDING_RETRIEVAL;
    }

    static boolean honestSplit(boolean splitBeforeFit, boolean relatedGroupsSeparated,
                               boolean testUsedForTuning) {
        return splitBeforeFit && relatedGroupsSeparated && !testUsedForTuning;
    }

    static Deploy deployment(boolean exactPreprocessing, boolean exactTensorContract,
                             boolean parityPassed, boolean supportedOperators,
                             boolean JVMRequired) {
        if (!JVMRequired) return Deploy.PYTHON_SERVICE;
        return exactPreprocessing && exactTensorContract && parityPassed && supportedOperators
                ? Deploy.JVM_ONNX : Deploy.REJECT;
    }

    static boolean promote(double candidateQuality, double baselineQuality, double minGain,
                           double p95Millis, double maxP95Millis) {
        if (!Double.isFinite(candidateQuality) || !Double.isFinite(baselineQuality)
                || minGain < 0 || maxP95Millis <= 0) throw new IllegalArgumentException("gate");
        return candidateQuality - baselineQuality >= minGain && p95Millis <= maxP95Millis;
    }

    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }

    public static void main(String[] args) {
        int scenarios = 0;
        check(Math.abs(cosine(new double[]{1, 0}, new double[]{1, 0}) - 1) < 1e-12); scenarios++;
        check(Math.abs(cosine(new double[]{1, 0}, new double[]{0, 1})) < 1e-12); scenarios++;
        check(Math.abs(cosine(new double[]{1, 1}, new double[]{1, 0}) - Math.sqrt(0.5)) < 1e-12); scenarios++;
        for (double[][] bad : new double[][][] {
                {new double[]{}, new double[]{}},
                {new double[]{1}, new double[]{1, 2}},
                {new double[]{0, 0}, new double[]{1, 0}},
                {new double[]{Double.NaN}, new double[]{1}}
        }) {
            try { cosine(bad[0], bad[1]); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { scenarios++; }
        }

        double[][] docs = {{1, 0}, {0.8, 0.2}, {0, 1}, {1, 0}};
        check(Arrays.equals(topK(docs, new double[]{1, 0}, 3), new int[]{0, 3, 1})); scenarios++;
        try { topK(docs, new double[]{1, 0}, 0); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }
        check(Arrays.equals(rerank(new int[]{0, 3, 1}, new double[]{0.2, 0.9, 0.9}, 2),
                new int[]{1, 3})); scenarios++;
        try { rerank(new int[]{0}, new double[]{}, 1); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }
        try { rerank(new int[]{-1}, new double[]{0.4}, 1); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }

        check(chooseBaseline(true, false) == Baseline.CLASSICAL_FIRST); scenarios++;
        check(chooseBaseline(false, false) == Baseline.EMBEDDING_RETRIEVAL); scenarios++;
        check(chooseBaseline(true, true) == Baseline.EMBEDDING_RETRIEVAL); scenarios++;
        check(honestSplit(true, true, false)); scenarios++;
        check(!honestSplit(false, true, false)); scenarios++;
        check(!honestSplit(true, false, false)); scenarios++;
        check(!honestSplit(true, true, true)); scenarios++;

        check(deployment(true, true, true, true, false) == Deploy.PYTHON_SERVICE); scenarios++;
        check(deployment(true, true, true, true, true) == Deploy.JVM_ONNX); scenarios++;
        check(deployment(false, true, true, true, true) == Deploy.REJECT); scenarios++;
        check(deployment(true, false, true, true, true) == Deploy.REJECT); scenarios++;
        check(deployment(true, true, false, true, true) == Deploy.REJECT); scenarios++;
        check(deployment(true, true, true, false, true) == Deploy.REJECT); scenarios++;

        check(promote(0.84, 0.80, 0.03, 95, 100)); scenarios++;
        check(!promote(0.82, 0.80, 0.03, 95, 100)); scenarios++;
        check(!promote(0.84, 0.80, 0.03, 105, 100)); scenarios++;
        try { promote(Double.NaN, 0.8, 0.03, 10, 100); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }

        long state = 0xE12BEDL;
        int promoted = 0;
        for (int i = 0; i < 10_000; i++) {
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            double baseline = 0.50 + ((state >>> 8) % 4000) / 10_000.0;
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            double candidate = 0.50 + ((state >>> 8) % 5000) / 10_000.0;
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            double latency = 20 + ((state >>> 12) % 181);
            boolean actual = promote(candidate, baseline, 0.025, latency, 120);
            boolean oracle = candidate - baseline >= 0.025 && latency <= 120;
            check(actual == oracle);
            if (actual) promoted++;
        }

        System.out.println("PARITY|FIRST=CLASSICAL_BASELINE|RETRIEVAL=BI_ENCODER|RERANK=CROSS_ENCODER"
                + "|SPLIT=BEFORE_FIT|JVM=PARITY_GATED|PROMOTED=" + promoted);
        System.out.println("Java embedding and reranking contracts passed: " + scenarios
                + " scenario groups + 10000 seeded trials");
    }
}
