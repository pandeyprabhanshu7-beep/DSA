import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class InferenceServingContracts {
    enum LoadShape { PREFILL_HEAVY, DECODE_HEAVY, BALANCED }
    enum Admission { ADMIT, QUEUE, REJECT_CAPACITY, REJECT_DEADLINE, REJECT_TENANT }

    record ModelSpec(int layers, int kvHeads, int headDimension, int bytesPerElement) {
        ModelSpec {
            if (layers <= 0 || kvHeads <= 0 || headDimension <= 0 || bytesPerElement <= 0) {
                throw new IllegalArgumentException("model dimensions must be positive");
            }
        }

        long kvBytesPerToken() {
            long value = Math.multiplyExact(2L, layers);
            value = Math.multiplyExact(value, kvHeads);
            value = Math.multiplyExact(value, headDimension);
            return Math.multiplyExact(value, bytesPerElement);
        }

        long kvBytes(long tokens) {
            if (tokens < 0) throw new IllegalArgumentException("negative token count");
            return Math.multiplyExact(kvBytesPerToken(), tokens);
        }
    }

    record Request(String id, String tenant, int promptTokens, int cachedPrefixTokens,
                   int maxOutputTokens, long deadlineMillis) {
        Request {
            if (id == null || id.isBlank() || tenant == null || tenant.isBlank()
                    || promptTokens < 0 || cachedPrefixTokens < 0
                    || cachedPrefixTokens > promptTokens || maxOutputTokens < 1
                    || deadlineMillis <= 0) {
                throw new IllegalArgumentException("invalid request");
            }
        }

        int uncachedPromptTokens() {
            return promptTokens - cachedPrefixTokens;
        }

        long incrementalKvTokens() {
            return Math.addExact((long) uncachedPromptTokens(), maxOutputTokens);
        }

        int nextStepTokens(int prefillChunkTokens) {
            if (prefillChunkTokens <= 0) throw new IllegalArgumentException("invalid prefill chunk");
            return uncachedPromptTokens() > 0
                    ? Math.min(uncachedPromptTokens(), prefillChunkTokens) : 1;
        }
    }

    record Capacity(long totalKvBytes, long usedKvBytes, int queueSize, int maxQueueSize,
                    int tenantActive, int maxTenantActive, long estimatedServiceMillis) {
        Capacity {
            if (totalKvBytes < 0 || usedKvBytes < 0 || usedKvBytes > totalKvBytes
                    || queueSize < 0 || maxQueueSize < 0 || tenantActive < 0
                    || maxTenantActive < 1 || estimatedServiceMillis < 0) {
                throw new IllegalArgumentException("invalid capacity snapshot");
            }
        }
    }

    record CacheIdentity(String modelRevision, String tokenizerRevision,
                         String adapterRevision, String cacheSalt, String tokenPrefixHash) {
        CacheIdentity {
            if (modelRevision == null || modelRevision.isBlank()
                    || tokenizerRevision == null || tokenizerRevision.isBlank()
                    || adapterRevision == null || adapterRevision.isBlank()
                    || cacheSalt == null || cacheSalt.isBlank()
                    || tokenPrefixHash == null || tokenPrefixHash.isBlank()) {
                throw new IllegalArgumentException("incomplete prefix-cache identity");
            }
        }
    }

    record LatencyTrace(long queueMillis, long tokenizeMillis, long prefillMillis,
                        long firstTokenComputeMillis, List<Long> interTokenMillis) {
        LatencyTrace {
            interTokenMillis = List.copyOf(interTokenMillis);
            if (queueMillis < 0 || tokenizeMillis < 0 || prefillMillis < 0
                    || firstTokenComputeMillis < 0 || interTokenMillis.stream().anyMatch(x -> x < 0)) {
                throw new IllegalArgumentException("negative latency");
            }
        }

        long timeToFirstTokenMillis() {
            return Math.addExact(Math.addExact(queueMillis, tokenizeMillis),
                    Math.addExact(prefillMillis, firstTokenComputeMillis));
        }

        long endToEndMillis() {
            long total = timeToFirstTokenMillis();
            for (long interval : interTokenMillis) total = Math.addExact(total, interval);
            return total;
        }

        double meanInterTokenMillis() {
            if (interTokenMillis.isEmpty()) return 0.0;
            long total = 0;
            for (long interval : interTokenMillis) total = Math.addExact(total, interval);
            return (double) total / interTokenMillis.size();
        }
    }

    record EngineMetrics(double kvUsage, int waiting, long p95QueueMillis,
                         long p95TtftMillis, long p95InterTokenMillis) {
        EngineMetrics {
            if (!Double.isFinite(kvUsage) || kvUsage < 0.0 || kvUsage > 1.0 || waiting < 0
                    || p95QueueMillis < 0 || p95TtftMillis < 0 || p95InterTokenMillis < 0) {
                throw new IllegalArgumentException("invalid metrics");
            }
        }
    }

    static LoadShape loadShape(Request request) {
        long prompt = request.uncachedPromptTokens();
        long output = request.maxOutputTokens();
        if (prompt >= Math.multiplyExact(output, 4L)) return LoadShape.PREFILL_HEAVY;
        if (output >= Math.multiplyExact(prompt, 4L)) return LoadShape.DECODE_HEAVY;
        return LoadShape.BALANCED;
    }

    static long incrementalReservationBytes(ModelSpec model, Request request) {
        Objects.requireNonNull(model);
        Objects.requireNonNull(request);
        return model.kvBytes(request.incrementalKvTokens());
    }

    static Admission admit(ModelSpec model, Request request, Capacity capacity) {
        Objects.requireNonNull(capacity);
        if (capacity.tenantActive() >= capacity.maxTenantActive()) return Admission.REJECT_TENANT;
        if (capacity.estimatedServiceMillis() >= request.deadlineMillis()) return Admission.REJECT_DEADLINE;
        long free = capacity.totalKvBytes() - capacity.usedKvBytes();
        if (incrementalReservationBytes(model, request) > free) return Admission.REJECT_CAPACITY;
        return capacity.queueSize() == 0 ? Admission.ADMIT
                : capacity.queueSize() < capacity.maxQueueSize() ? Admission.QUEUE
                : Admission.REJECT_CAPACITY;
    }

    static boolean mayReusePrefix(CacheIdentity cached, CacheIdentity requested) {
        return Objects.equals(cached, requested);
    }

    static boolean overloaded(EngineMetrics metrics, double maxKvUsage,
                              long queueSloMillis, long ttftSloMillis,
                              long interTokenSloMillis) {
        if (!Double.isFinite(maxKvUsage) || maxKvUsage <= 0.0 || maxKvUsage > 1.0
                || queueSloMillis <= 0 || ttftSloMillis <= 0 || interTokenSloMillis <= 0) {
            throw new IllegalArgumentException("invalid overload policy");
        }
        return metrics.kvUsage() >= maxKvUsage
                || (metrics.waiting() > 0 && metrics.p95QueueMillis() > queueSloMillis)
                || metrics.p95TtftMillis() > ttftSloMillis
                || metrics.p95InterTokenMillis() > interTokenSloMillis;
    }

    static List<String> fairBatch(List<Request> arrivals, int maxSequences,
                                  int maxScheduledTokens, int prefillChunkTokens) {
        if (maxSequences < 1 || maxScheduledTokens < 1 || prefillChunkTokens < 1) {
            throw new IllegalArgumentException("invalid scheduler bounds");
        }
        Map<String, Deque<Request>> byTenant = new LinkedHashMap<>();
        for (Request request : arrivals) {
            byTenant.computeIfAbsent(request.tenant(), ignored -> new ArrayDeque<>()).add(request);
        }
        List<String> selected = new ArrayList<>();
        int scheduledTokens = 0;
        boolean progress;
        do {
            progress = false;
            for (Deque<Request> queue : byTenant.values()) {
                if (selected.size() == maxSequences || queue.isEmpty()) continue;
                Request candidate = queue.peek();
                int tokens = candidate.nextStepTokens(prefillChunkTokens);
                if (scheduledTokens + tokens <= maxScheduledTokens) {
                    selected.add(queue.remove().id());
                    scheduledTokens += tokens;
                    progress = true;
                }
            }
        } while (progress && selected.size() < maxSequences);
        return List.copyOf(selected);
    }

    static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        int scenarios = 0;
        ModelSpec gqa = new ModelSpec(32, 8, 128, 2);
        expect(gqa.kvBytesPerToken() == 131_072L, "128 KiB per token"); scenarios++;
        expect(gqa.kvBytes(8_192) == 1_073_741_824L, "one GiB sequence"); scenarios++;
        expect(gqa.kvBytes(0) == 0L, "zero tokens"); scenarios++;
        try { new ModelSpec(0, 8, 128, 2); throw new AssertionError("zero layers accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }
        try { gqa.kvBytes(-1); throw new AssertionError("negative tokens accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }

        Request prefill = new Request("p", "alpha", 12_000, 0, 32, 12_000);
        Request decode = new Request("d", "beta", 120, 0, 800, 12_000);
        Request balanced = new Request("b", "alpha", 1_000, 0, 500, 12_000);
        Request cached = new Request("c", "alpha", 8_000, 7_000, 200, 12_000);
        expect(loadShape(prefill) == LoadShape.PREFILL_HEAVY, "prefill shape"); scenarios++;
        expect(loadShape(decode) == LoadShape.DECODE_HEAVY, "decode shape"); scenarios++;
        expect(loadShape(balanced) == LoadShape.BALANCED, "balanced shape"); scenarios++;
        expect(cached.incrementalKvTokens() == 1_200, "cached prefix incremental tokens"); scenarios++;
        expect(incrementalReservationBytes(gqa, cached) == 157_286_400L, "cached reservation"); scenarios++;
        try { new Request("bad", "alpha", 10, 11, 1, 10); throw new AssertionError("oversized prefix accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }

        long gib = 1L << 30;
        Capacity open = new Capacity(2 * gib, gib, 0, 4, 0, 2, 4_000);
        Request fits = new Request("fit", "alpha", 2_000, 0, 1_000, 10_000);
        expect(admit(gqa, fits, open) == Admission.ADMIT, "admit fitting request"); scenarios++;
        expect(admit(gqa, fits, new Capacity(2 * gib, gib, 1, 4, 0, 2, 4_000)) == Admission.QUEUE, "bounded queue"); scenarios++;
        expect(admit(gqa, fits, new Capacity(2 * gib, gib, 4, 4, 0, 2, 4_000)) == Admission.REJECT_CAPACITY, "full queue rejects"); scenarios++;
        expect(admit(gqa, prefill, open) == Admission.REJECT_CAPACITY, "kv pressure rejects"); scenarios++;
        expect(admit(gqa, fits, new Capacity(2 * gib, gib, 0, 4, 2, 2, 4_000)) == Admission.REJECT_TENANT, "tenant share"); scenarios++;
        expect(admit(gqa, fits, new Capacity(2 * gib, gib, 0, 4, 0, 2, 10_000)) == Admission.REJECT_DEADLINE, "no completion path"); scenarios++;

        CacheIdentity key = new CacheIdentity("model@7", "tok@3", "base", "tenant-a", "sha256:abc");
        expect(mayReusePrefix(key, key), "exact prefix identity"); scenarios++;
        expect(!mayReusePrefix(key, new CacheIdentity("model@8", "tok@3", "base", "tenant-a", "sha256:abc")), "model fence"); scenarios++;
        expect(!mayReusePrefix(key, new CacheIdentity("model@7", "tok@4", "base", "tenant-a", "sha256:abc")), "tokenizer fence"); scenarios++;
        expect(!mayReusePrefix(key, new CacheIdentity("model@7", "tok@3", "base", "tenant-b", "sha256:abc")), "salt fence"); scenarios++;
        expect(!mayReusePrefix(key, new CacheIdentity("model@7", "tok@3", "base", "tenant-a", "sha256:def")), "token prefix fence"); scenarios++;

        LatencyTrace trace = new LatencyTrace(300, 20, 480, 20, List.of(80L, 90L, 100L));
        expect(trace.timeToFirstTokenMillis() == 820, "ttft components"); scenarios++;
        expect(trace.endToEndMillis() == 1_090, "end to end"); scenarios++;
        expect(trace.meanInterTokenMillis() == 90.0, "inter-token mean"); scenarios++;
        expect(new LatencyTrace(1, 1, 1, 1, List.of()).meanInterTokenMillis() == 0.0, "single token has no gap"); scenarios++;

        List<Request> arrivals = List.of(
                new Request("a-long", "alpha", 1_000, 0, 20, 10_000),
                new Request("a-next", "alpha", 0, 0, 20, 10_000),
                new Request("b-decode", "beta", 0, 0, 20, 10_000),
                new Request("c-short", "gamma", 40, 0, 20, 10_000));
        expect(fairBatch(arrivals, 3, 129, 128).equals(List.of("a-long", "b-decode")), "token bound"); scenarios++;
        expect(fairBatch(arrivals, 4, 200, 64).equals(List.of("a-long", "b-decode", "c-short", "a-next")), "tenant round robin"); scenarios++;

        expect(overloaded(new EngineMetrics(.93, 2, 100, 800, 45), .90, 500, 1_000, 100), "kv overload"); scenarios++;
        expect(overloaded(new EngineMetrics(.60, 4, 700, 800, 45), .90, 500, 1_000, 100), "queue overload"); scenarios++;
        expect(overloaded(new EngineMetrics(.60, 0, 0, 1_200, 45), .90, 500, 1_000, 100), "ttft overload"); scenarios++;
        expect(overloaded(new EngineMetrics(.60, 0, 0, 800, 120), .90, 500, 1_000, 100), "itl overload"); scenarios++;
        expect(!overloaded(new EngineMetrics(.60, 0, 0, 800, 45), .90, 500, 1_000, 100), "healthy engine"); scenarios++;

        Random random = new Random(0x15CAFE);
        int trials = 10_000;
        for (int i = 0; i < trials; i++) {
            int prompt = random.nextInt(8_001);
            int cachedTokens = random.nextInt(prompt + 1);
            int output = random.nextInt(1_000) + 1;
            Request generated = new Request("r" + i, "tenant", prompt, cachedTokens, output, 20_000);
            long expectedTokens = (long) prompt - cachedTokens + output;
            long expectedBytes = Math.multiplyExact(131_072L, expectedTokens);
            expect(incrementalReservationBytes(gqa, generated) == expectedBytes, "reservation oracle " + i);
            long free = random.nextLong(expectedBytes + 1);
            Capacity generatedCapacity = new Capacity(expectedBytes + 100, expectedBytes + 100 - free,
                    0, 4, 0, 2, 1_000);
            Admission expectedAdmission = expectedBytes <= free ? Admission.ADMIT : Admission.REJECT_CAPACITY;
            expect(admit(gqa, generated, generatedCapacity) == expectedAdmission, "admission oracle " + i);
        }

        expect(scenarios == 33, "scenario count");
        System.out.println("SCENARIOS=" + scenarios + "|TRIALS=" + trials
                + "|PHASES=PREFILL_AND_DECODE|CAPACITY=KV_TOKEN_BUDGET"
                + "|SCHEDULER=TENANT_ROUND_ROBIN|CACHE=EXACT_PREFIX_IDENTITY"
                + "|SLO=TTFT_ITL_E2E");
    }
}
