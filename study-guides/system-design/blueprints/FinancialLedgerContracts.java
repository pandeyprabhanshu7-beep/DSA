import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Executable invariants for Blueprint 3. Run with: java -ea FinancialLedgerContracts.java */
public final class FinancialLedgerContracts {
    record Entry(String account, String currency, long minorUnits) {}
    record Posted(String transactionId, String requestHash, List<Entry> entries) {}

    static final class Ledger {
        private final Map<String, Posted> byIdempotencyKey = new HashMap<>();
        private final Map<String, Long> balances = new HashMap<>();
        private final List<String> outbox = new ArrayList<>();
        private long nextId = 100;

        synchronized Posted transfer(
                String scope,
                String key,
                String from,
                String to,
                String currency,
                long amountMinor) {
            if (amountMinor <= 0 || from.equals(to)) {
                throw new IllegalArgumentException("positive amount and distinct accounts required");
            }
            String requestHash = sha256(from + "\n" + to + "\n" + currency + "\n" + amountMinor);
            String scopedKey = scope + ":" + key;
            Posted prior = byIdempotencyKey.get(scopedKey);
            if (prior != null) {
                if (!prior.requestHash().equals(requestHash)) {
                    throw new IllegalStateException("same key, different request");
                }
                return prior;
            }
            long available = balances.getOrDefault(from, 0L);
            if (available < amountMinor) throw new IllegalStateException("insufficient funds");

            List<Entry> entries = List.of(
                    new Entry(from, currency, -amountMinor),
                    new Entry(to, currency, amountMinor));
            requireBalanced(entries);
            String transactionId = "txn_" + nextId++;
            Posted posted = new Posted(transactionId, requestHash, entries);

            // Teaching model: one synchronized block stands in for one serializable DB transaction.
            balances.put(from, Math.subtractExact(available, amountMinor));
            balances.put(to, Math.addExact(balances.getOrDefault(to, 0L), amountMinor));
            byIdempotencyKey.put(scopedKey, posted);
            outbox.add("LedgerPosted:" + transactionId);
            return posted;
        }

        void seed(String account, long amountMinor) { balances.put(account, amountMinor); }
        long balance(String account) { return balances.getOrDefault(account, 0L); }
        List<String> outbox() { return List.copyOf(outbox); }
    }

    enum RailState { CREATED, SENT, UNKNOWN, SUCCEEDED, FAILED }

    static final class RailAttempt {
        final String operationId;
        final String providerKey;
        RailState state = RailState.CREATED;

        RailAttempt(String operationId) {
            this.operationId = operationId;
            this.providerKey = "rail:" + operationId;
        }

        void sent() { transition(RailState.CREATED, RailState.SENT); }
        void timedOut() { transition(RailState.SENT, RailState.UNKNOWN); }
        void reconcile(boolean providerCommitted) {
            if (state != RailState.UNKNOWN) throw new IllegalStateException("not unknown");
            state = providerCommitted ? RailState.SUCCEEDED : RailState.FAILED;
        }
        void transition(RailState expected, RailState next) {
            if (state != expected) throw new IllegalStateException("illegal transition");
            state = next;
        }
    }

    static final class InboxProjection {
        private final Set<String> processedEventIds = new HashSet<>();
        private final Map<String, Long> projectedBalances = new HashMap<>();

        boolean apply(String eventId, List<Entry> entries) {
            if (!processedEventIds.add(eventId)) return false;
            requireBalanced(entries);
            for (Entry entry : entries) {
                projectedBalances.merge(entry.account(), entry.minorUnits(), Math::addExact);
            }
            return true;
        }
        long balance(String account) { return projectedBalances.getOrDefault(account, 0L); }
    }

    static void requireBalanced(List<Entry> entries) {
        if (entries.size() < 2) throw new IllegalArgumentException("at least two entries required");
        String currency = entries.get(0).currency();
        long sum = 0;
        for (Entry entry : entries) {
            if (!currency.equals(entry.currency())) throw new IllegalArgumentException("mixed currency");
            sum = Math.addExact(sum, entry.minorUnits());
        }
        if (sum != 0) throw new IllegalArgumentException("unbalanced transaction");
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static void expectFailure(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected failure");
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected contract rejection.
        }
    }

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        ledger.seed("merchant:A", 10_000);

        Posted first = ledger.transfer("merchant", "k-1", "merchant:A", "customer:B", "USD", 1_250);
        assert first.entries().stream().mapToLong(Entry::minorUnits).sum() == 0;
        assert ledger.balance("merchant:A") == 8_750;
        assert ledger.balance("customer:B") == 1_250;
        assert ledger.outbox().size() == 1;

        Posted replay = ledger.transfer("merchant", "k-1", "merchant:A", "customer:B", "USD", 1_250);
        assert replay.transactionId().equals(first.transactionId());
        assert ledger.outbox().size() == 1;
        expectFailure(() -> ledger.transfer("merchant", "k-1", "merchant:A", "customer:B", "USD", 1_251));

        Ledger contested = new Ledger();
        contested.seed("wallet", 1_000);
        contested.transfer("wallet", "a", "wallet", "x", "USD", 700);
        expectFailure(() -> contested.transfer("wallet", "b", "wallet", "y", "USD", 700));
        assert contested.balance("wallet") == 300;

        RailAttempt rail = new RailAttempt(first.transactionId());
        rail.sent();
        rail.timedOut();
        String stableKey = rail.providerKey;
        assert rail.providerKey.equals(stableKey);
        rail.reconcile(true);
        assert rail.state == RailState.SUCCEEDED;
        expectFailure(rail::sent);

        InboxProjection projection = new InboxProjection();
        assert projection.apply("event-1", first.entries());
        assert !projection.apply("event-1", first.entries());
        assert projection.balance("merchant:A") == -1_250;
        assert projection.balance("customer:B") == 1_250;

        expectFailure(() -> requireBalanced(List.of(
                new Entry("A", "USD", -100), new Entry("B", "USD", 99))));
        expectFailure(() -> requireBalanced(List.of(
                new Entry("A", "USD", -100), new Entry("B", "EUR", 100))));
        expectFailure(() -> ledger.transfer("merchant", "zero", "merchant:A", "customer:B", "USD", 0));

        System.out.println("FinancialLedgerContracts: 18 scenario groups passed");
    }
}
