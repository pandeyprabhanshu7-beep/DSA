import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** Executable contracts for the cross-blueprint design-defense supplement. */
public final class DesignDefenseContracts {
    enum Role { SOURCE_OF_TRUTH, CACHE, INDEX, PROJECTION, EVENT_LOG }
    enum Decision { SERVE, DEGRADE, REJECT, RECONCILE }

    record QueuePlan(long arrivalsPerSecond, long servicePerSecond, long burstSeconds,
                     long queueCapacity) {
        QueuePlan {
            if (arrivalsPerSecond < 0 || servicePerSecond < 0 || burstSeconds < 0
                    || queueCapacity < 0) {
                throw new IllegalArgumentException("rates, duration and capacity must be non-negative");
            }
        }

        long backlog() {
            long excess = Math.max(0L, Math.subtractExact(arrivalsPerSecond, servicePerSecond));
            return Math.multiplyExact(excess, burstSeconds);
        }

        boolean fits() {
            return backlog() <= queueCapacity;
        }

        long drainSeconds(long recoveryServicePerSecond) {
            long spare = Math.subtractExact(recoveryServicePerSecond, arrivalsPerSecond);
            if (backlog() == 0) return 0;
            if (spare <= 0) throw new IllegalArgumentException("recovery needs positive spare capacity");
            return Math.floorDiv(Math.addExact(backlog(), spare - 1), spare);
        }
    }

    record Reply(String operationId, String requestHash, String result) { }

    static final class IdempotencyRegistry {
        private final Map<String, Reply> replies = new HashMap<>();

        Reply execute(String caller, String key, String requestHash, String result) {
            requireText(caller, "caller");
            requireText(key, "key");
            requireText(requestHash, "requestHash");
            String scopedKey = caller + "\u0000" + key;
            Reply prior = replies.get(scopedKey);
            if (prior != null) {
                if (!prior.requestHash().equals(requestHash)) {
                    throw new IllegalArgumentException("same key, different request");
                }
                return prior;
            }
            Reply created = new Reply("op-" + (replies.size() + 1), requestHash, result);
            replies.put(scopedKey, created);
            return created;
        }
    }

    static boolean mayDecide(Role role) {
        return role == Role.SOURCE_OF_TRUTH;
    }

    static Decision overloadDecision(String system, boolean authorityHealthy, boolean capacityAvailable) {
        requireText(system, "system");
        if (!authorityHealthy) {
            return switch (system) {
                case "ledger", "id-generator", "ride-assignment" -> Decision.REJECT;
                case "video", "crawler" -> Decision.DEGRADE;
                default -> throw new IllegalArgumentException("unknown system");
            };
        }
        if (capacityAvailable) return Decision.SERVE;
        return switch (system) {
            case "video", "crawler" -> Decision.DEGRADE;
            case "ride-assignment" -> Decision.REJECT;
            case "ledger" -> Decision.REJECT;
            case "id-generator" -> Decision.REJECT;
            default -> throw new IllegalArgumentException("unknown system");
        };
    }

    static Decision timeoutDecision(boolean requestWasIdempotent, boolean authoritativeResultKnown) {
        if (authoritativeResultKnown) return Decision.SERVE;
        return requestWasIdempotent ? Decision.RECONCILE : Decision.REJECT;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is blank");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public static void main(String[] args) {
        int groups = 0;

        QueuePlan burst = new QueuePlan(80_000, 40_000, 30, 1_000_000);
        check(burst.backlog() == 1_200_000, "exact backlog"); groups++;
        check(!burst.fits(), "queue must not hide overload"); groups++;
        check(burst.drainSeconds(90_000) == 120, "drain time"); groups++;
        check(new QueuePlan(40_000, 40_000, 30, 0).backlog() == 0, "balanced rate"); groups++;
        expectFailure(() -> new QueuePlan(-1, 1, 1, 1), "negative rate accepted"); groups++;

        check(mayDecide(Role.SOURCE_OF_TRUTH), "authority rejected"); groups++;
        check(!mayDecide(Role.CACHE) && !mayDecide(Role.INDEX)
                && !mayDecide(Role.PROJECTION) && !mayDecide(Role.EVENT_LOG),
                "derived state became authority"); groups++;

        IdempotencyRegistry registry = new IdempotencyRegistry();
        Reply first = registry.execute("merchant-7", "K1", "A>B:1000:USD", "POSTED");
        Reply retry = registry.execute("merchant-7", "K1", "A>B:1000:USD", "ignored");
        check(first == retry && first.operationId().equals("op-1"), "retry changed effect"); groups++;
        Reply otherCaller = registry.execute("merchant-8", "K1", "A>B:1000:USD", "POSTED");
        check(!otherCaller.operationId().equals(first.operationId()), "scope collision"); groups++;
        expectFailure(() -> registry.execute("merchant-7", "K1", "A>B:2000:USD", "POSTED"),
                "key reuse mismatch accepted"); groups++;

        check(timeoutDecision(true, false) == Decision.RECONCILE, "unknown idempotent timeout"); groups++;
        check(timeoutDecision(false, false) == Decision.REJECT, "unsafe retry allowed"); groups++;
        check(timeoutDecision(false, true) == Decision.SERVE, "known result hidden"); groups++;

        check(overloadDecision("video", true, false) == Decision.DEGRADE, "video ladder"); groups++;
        check(overloadDecision("crawler", false, false) == Decision.DEGRADE, "crawler slowdown"); groups++;
        check(overloadDecision("ride-assignment", false, true) == Decision.REJECT, "unsafe claim"); groups++;
        check(overloadDecision("ledger", false, true) == Decision.REJECT, "unsafe money write"); groups++;
        check(overloadDecision("id-generator", false, true) == Decision.REJECT, "stale lease"); groups++;
        check(overloadDecision("video", true, true) == Decision.SERVE, "healthy path"); groups++;
        expectFailure(() -> overloadDecision("mystery", true, false), "unknown system accepted"); groups++;

        Random random = new Random(0x5EEDL);
        for (int i = 0; i < 10_000; i++) {
            long arrivals = random.nextInt(1_000_001);
            long service = random.nextInt(1_000_001);
            long seconds = random.nextInt(301);
            QueuePlan plan = new QueuePlan(arrivals, service, seconds, Long.MAX_VALUE);
            long expected = Math.max(0L, arrivals - service) * seconds;
            check(plan.backlog() == expected, "randomized backlog conservation");
        }
        groups++;

        System.out.println("DesignDefenseContracts: " + groups
                + " scenario groups + 10000 randomized capacity checks passed");
        System.out.println("PARITY|BACKLOG=1200000|DRAIN=120|REPLAY=op-1|VIDEO=DEGRADE|LEDGER=REJECT");
    }
}
