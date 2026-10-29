import java.util.Arrays;

public class DebuggingScenariosContracts {
    private static int scenarios;

    enum CacheDecision { HIT, SCOPE_MISS, REVISION_MISS, REVOKED }
    enum ToolDecision { EXECUTE, INVALID_IDENTITY, DENY_SCOPE, INVALID_PAYLOAD }
    enum StreamDecision { OPEN, COMPLETE, FAILED, INCOMPLETE }
    enum NetworkDecision { READY, WRONG_NAMESPACE, DNS, TCP, AUTH, PROTOCOL }
    enum PackStatus { READY, NO_EVIDENCE, INVALID, OVER_BUDGET }
    enum IngestDecision { UPSERT, NOOP, NEW_GENERATION, REJECT_UNSTABLE_ID }

    record PackResult(PackStatus status, int used, int selected, int skipped) {}

    static CacheDecision cache(boolean sameTenant, boolean sameAuthorizationRevision,
                               boolean sameIndexGeneration, boolean evidenceVisible) {
        if (!sameTenant) return CacheDecision.SCOPE_MISS;
        if (!sameAuthorizationRevision || !sameIndexGeneration) return CacheDecision.REVISION_MISS;
        if (!evidenceVisible) return CacheDecision.REVOKED;
        return CacheDecision.HIT;
    }

    static ToolDecision tool(boolean identityVerified, boolean accountInTrustedScope,
                             boolean payloadValid) {
        if (!identityVerified) return ToolDecision.INVALID_IDENTITY;
        if (!accountInTrustedScope) return ToolDecision.DENY_SCOPE;
        return payloadValid ? ToolDecision.EXECUTE : ToolDecision.INVALID_PAYLOAD;
    }

    static StreamDecision stream(boolean completeEvent, boolean failedEvent,
                                 boolean connectionClosed) {
        if (completeEvent && failedEvent) throw new IllegalArgumentException("conflicting terminal events");
        if (completeEvent) return StreamDecision.COMPLETE;
        if (failedEvent) return StreamDecision.FAILED;
        return connectionClosed ? StreamDecision.INCOMPLETE : StreamDecision.OPEN;
    }

    static NetworkDecision network(boolean localhost, boolean samePod, boolean dns,
                                   boolean tcp, boolean authenticated, boolean protocol) {
        if (localhost && !samePod) return NetworkDecision.WRONG_NAMESPACE;
        if (!localhost && !dns) return NetworkDecision.DNS;
        if (!tcp) return NetworkDecision.TCP;
        if (!authenticated) return NetworkDecision.AUTH;
        return protocol ? NetworkDecision.READY : NetworkDecision.PROTOCOL;
    }

    static PackResult pack(int contextWindow, int fixedReservations, int questionTokens,
                           int... passageTokens) {
        if (contextWindow <= 0 || fixedReservations < 0 || questionTokens <= 0
                || Arrays.stream(passageTokens).anyMatch(value -> value <= 0)) {
            return new PackResult(PackStatus.INVALID, 0, 0, 0);
        }
        long base = (long) fixedReservations + questionTokens;
        if (base > contextWindow) return new PackResult(PackStatus.OVER_BUDGET, 0, 0, 0);
        int used = (int) base;
        int selected = 0;
        int skipped = 0;
        for (int tokens : passageTokens) {
            if (tokens <= contextWindow - used) {
                used += tokens;
                selected++;
            } else {
                skipped++;
            }
        }
        PackStatus status = selected == 0 ? PackStatus.NO_EVIDENCE : PackStatus.READY;
        return new PackResult(status, used, selected, skipped);
    }

    static IngestDecision ingest(boolean deterministicId, boolean sameSourceRevision,
                                 boolean existingSameContent) {
        if (!deterministicId) return IngestDecision.REJECT_UNSTABLE_ID;
        if (!sameSourceRevision) return IngestDecision.NEW_GENERATION;
        return existingSameContent ? IngestDecision.NOOP : IngestDecision.UPSERT;
    }

    static long next(long state) {
        return (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
    }

    static void check(boolean condition) {
        assert condition;
        scenarios++;
    }

    public static void main(String[] args) {
        check(cache(false, true, true, true) == CacheDecision.SCOPE_MISS);
        check(cache(true, false, true, true) == CacheDecision.REVISION_MISS);
        check(cache(true, true, false, true) == CacheDecision.REVISION_MISS);
        check(cache(true, true, true, false) == CacheDecision.REVOKED);
        check(cache(true, true, true, true) == CacheDecision.HIT);

        check(tool(false, true, true) == ToolDecision.INVALID_IDENTITY);
        check(tool(true, false, true) == ToolDecision.DENY_SCOPE);
        check(tool(true, true, false) == ToolDecision.INVALID_PAYLOAD);
        check(tool(true, true, true) == ToolDecision.EXECUTE);

        check(stream(false, false, false) == StreamDecision.OPEN);
        check(stream(false, false, true) == StreamDecision.INCOMPLETE);
        check(stream(true, false, true) == StreamDecision.COMPLETE);
        check(stream(false, true, true) == StreamDecision.FAILED);
        try {
            stream(true, true, false);
            check(false);
        } catch (IllegalArgumentException expected) {
            check(true);
        }

        check(network(true, false, false, false, false, false) == NetworkDecision.WRONG_NAMESPACE);
        check(network(false, false, false, false, false, false) == NetworkDecision.DNS);
        check(network(false, false, true, false, false, false) == NetworkDecision.TCP);
        check(network(false, false, true, true, false, false) == NetworkDecision.AUTH);
        check(network(false, false, true, true, true, false) == NetworkDecision.PROTOCOL);
        check(network(false, false, true, true, true, true) == NetworkDecision.READY);
        check(network(true, true, false, true, true, true) == NetworkDecision.READY);

        check(pack(100, 20, 10, 30, 40).equals(new PackResult(PackStatus.READY, 100, 2, 0)));
        check(pack(100, 20, 10, 80, 20).equals(new PackResult(PackStatus.READY, 50, 1, 1)));
        check(pack(100, 20, 10, 80).equals(new PackResult(PackStatus.NO_EVIDENCE, 30, 0, 1)));
        check(pack(100, 95, 10, 1).status() == PackStatus.OVER_BUDGET);
        check(pack(0, 0, 1, 1).status() == PackStatus.INVALID);
        check(pack(100, 0, 10, 0).status() == PackStatus.INVALID);

        check(ingest(false, true, false) == IngestDecision.REJECT_UNSTABLE_ID);
        check(ingest(true, false, false) == IngestDecision.NEW_GENERATION);
        check(ingest(true, true, true) == IngestDecision.NOOP);
        check(ingest(true, true, false) == IngestDecision.UPSERT);

        long state = 0xD23B6A11L;
        for (int trial = 0; trial < 10_000; trial++) {
            state = next(state); boolean tenant = (state & 1) != 0;
            state = next(state); boolean authorization = (state & 1) != 0;
            state = next(state); boolean generation = (state & 1) != 0;
            state = next(state); boolean visible = (state & 1) != 0;
            CacheDecision expected = !tenant ? CacheDecision.SCOPE_MISS
                    : !(authorization && generation) ? CacheDecision.REVISION_MISS
                    : !visible ? CacheDecision.REVOKED : CacheDecision.HIT;
            assert cache(tenant, authorization, generation, visible) == expected;
        }

        for (int trial = 0; trial < 10_000; trial++) {
            state = next(state); boolean stable = (state & 1) != 0;
            state = next(state); boolean revision = (state & 1) != 0;
            state = next(state); boolean same = (state & 1) != 0;
            IngestDecision expected = !stable ? IngestDecision.REJECT_UNSTABLE_ID
                    : !revision ? IngestDecision.NEW_GENERATION
                    : same ? IngestDecision.NOOP : IngestDecision.UPSERT;
            assert ingest(stable, revision, same) == expected;
        }

        assert scenarios == 31 : "scenario count " + scenarios;
        System.out.println("PARITY|CACHE=SCOPED|STREAM=TERMINAL_EVENT|NETWORK=LAYERED|PACK=QUESTION_RESERVED|INGEST=IDEMPOTENT|TOOL=AUTHORIZED");
        System.out.println("Debugging scenarios contracts passed: 31 scenario groups + 20000 seeded trials");
    }
}
