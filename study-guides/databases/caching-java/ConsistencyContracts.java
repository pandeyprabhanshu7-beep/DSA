import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Executable teaching models for guide Section 1. Run with assertions enabled. */
public final class ConsistencyContracts {
    private ConsistencyContracts() {}

    static final class AtomicLedger {
        private final Map<String, Long> balances = new HashMap<>();

        AtomicLedger(long a, long b) {
            balances.put("A", a);
            balances.put("B", b);
        }

        void transfer(String from, String to, long amount, boolean failAfterDebit) {
            if (amount <= 0 || from.equals(to)) throw new IllegalArgumentException("invalid transfer");
            Map<String, Long> staged = new HashMap<>(balances);
            long source = require(staged, from);
            if (source < amount) throw new IllegalStateException("insufficient funds");
            staged.put(from, source - amount);
            if (failAfterDebit) throw new InjectedFailure();
            staged.put(to, Math.addExact(require(staged, to), amount));
            balances.clear();
            balances.putAll(staged); // one publication point models atomic commit
        }

        long balance(String account) { return require(balances, account); }
        long total() { return balances.values().stream().mapToLong(Long::longValue).sum(); }

        private static long require(Map<String, Long> values, String account) {
            Long value = values.get(account);
            if (value == null) throw new IllegalArgumentException("unknown account: " + account);
            return value;
        }
    }

    static final class InjectedFailure extends RuntimeException {}
    static final class ResponseLost extends RuntimeException {}

    record Receipt(String operationId, long fromBalance, long toBalance) {}

    static final class IdempotentTransferService {
        private final AtomicLedger ledger;
        private final Map<String, Receipt> outcomes = new HashMap<>();

        IdempotentTransferService(AtomicLedger ledger) { this.ledger = ledger; }

        Receipt transfer(String operationId, long amount, boolean loseResponse) {
            Receipt existing = outcomes.get(operationId);
            if (existing != null) return existing;

            // In production, the uniqueness check, balance changes, and outcome insert
            // belong to one database transaction.
            ledger.transfer("A", "B", amount, false);
            Receipt committed = new Receipt(operationId, ledger.balance("A"), ledger.balance("B"));
            outcomes.put(operationId, committed);
            if (loseResponse) throw new ResponseLost();
            return committed;
        }

        Receipt status(String operationId) { return outcomes.get(operationId); }
    }

    enum ReadPolicy { EVENTUAL, READ_YOUR_WRITES }
    record VersionedBalance(long value, long version, String servedBy) {}

    static final class ReplicatedBalance {
        private long primaryValue;
        private long primaryVersion;
        private long replicaValue;
        private long replicaVersion;

        ReplicatedBalance(long initial) {
            primaryValue = replicaValue = initial;
        }

        long writePrimary(long value) {
            primaryValue = value;
            return ++primaryVersion;
        }

        VersionedBalance read(ReadPolicy policy, long requiredVersion) {
            if (policy == ReadPolicy.READ_YOUR_WRITES && replicaVersion < requiredVersion) {
                return new VersionedBalance(primaryValue, primaryVersion, "primary");
            }
            return new VersionedBalance(replicaValue, replicaVersion, "replica");
        }

        void replay() {
            replicaValue = primaryValue;
            replicaVersion = primaryVersion;
        }
    }

    record ItemClock(long addVersion, long removeVersion) {
        ItemClock merge(ItemClock other) {
            return new ItemClock(
                    Math.max(addVersion, other.addVersion),
                    Math.max(removeVersion, other.removeVersion));
        }

        boolean present() { return addVersion > 0 && addVersion >= removeVersion; }
    }

    static final class AddWinsCart {
        private final Map<String, ItemClock> clocks = new HashMap<>();

        void add(String item, long version) {
            clocks.compute(item, (key, old) -> new ItemClock(
                    Math.max(version, old == null ? 0 : old.addVersion()),
                    old == null ? 0 : old.removeVersion()));
        }

        void remove(String item, long version) {
            clocks.compute(item, (key, old) -> new ItemClock(
                    old == null ? 0 : old.addVersion(),
                    Math.max(version, old == null ? 0 : old.removeVersion())));
        }

        void merge(AddWinsCart other) {
            other.clocks.forEach((item, clock) ->
                    clocks.merge(item, clock, ItemClock::merge));
        }

        Set<String> items() {
            Set<String> result = new HashSet<>();
            clocks.forEach((item, clock) -> { if (clock.present()) result.add(item); });
            return Set.copyOf(result);
        }

        AddWinsCart copy() {
            AddWinsCart copy = new AddWinsCart();
            copy.clocks.putAll(clocks);
            return copy;
        }
    }

    private static void atomicTransferScenarios() {
        AtomicLedger ledger = new AtomicLedger(500, 200);
        try {
            ledger.transfer("A", "B", 100, true);
            throw new AssertionError("failure injection did not fire");
        } catch (InjectedFailure expected) {
            assert ledger.balance("A") == 500;
            assert ledger.balance("B") == 200;
            assert ledger.total() == 700;
        }
        ledger.transfer("A", "B", 100, false);
        assert ledger.balance("A") == 400;
        assert ledger.balance("B") == 300;
        assert ledger.total() == 700;

        try {
            ledger.transfer("A", "B", 1_000, false);
            throw new AssertionError("overdraft accepted");
        } catch (IllegalStateException expected) {
            assert ledger.total() == 700;
        }
    }

    private static void unknownOutcomeScenarios() {
        AtomicLedger ledger = new AtomicLedger(500, 200);
        IdempotentTransferService service = new IdempotentTransferService(ledger);
        try {
            service.transfer("op-42", 100, true);
            throw new AssertionError("response-loss injection did not fire");
        } catch (ResponseLost expected) {
            assert service.status("op-42") != null;
        }
        Receipt first = service.status("op-42");
        Receipt retry = service.transfer("op-42", 100, false);
        assert Objects.equals(first, retry);
        assert ledger.balance("A") == 400;
        assert ledger.balance("B") == 300;
    }

    private static void replicaScenarios() {
        ReplicatedBalance balance = new ReplicatedBalance(500);
        long token = balance.writePrimary(400);
        VersionedBalance stale = balance.read(ReadPolicy.EVENTUAL, 0);
        assert stale.value() == 500 && stale.version() == 0;

        VersionedBalance sessionRead = balance.read(ReadPolicy.READ_YOUR_WRITES, token);
        assert sessionRead.value() == 400;
        assert sessionRead.version() == token;
        assert sessionRead.servedBy().equals("primary");

        balance.replay();
        VersionedBalance caughtUp = balance.read(ReadPolicy.READ_YOUR_WRITES, token);
        assert caughtUp.value() == 400;
        assert caughtUp.servedBy().equals("replica");
    }

    private static void convergenceScenarios() {
        AddWinsCart a = new AddWinsCart();
        AddWinsCart b = new AddWinsCart();
        a.add("mug", 1);
        b.add("pen", 1);

        AddWinsCart aThenB = a.copy();
        aThenB.merge(b);
        AddWinsCart bThenA = b.copy();
        bThenA.merge(a);
        assert aThenB.items().equals(Set.of("mug", "pen"));
        assert aThenB.items().equals(bThenA.items()); // commutative result

        b.remove("mug", 2);
        a.merge(b);
        b.merge(a);
        assert a.items().equals(Set.of("pen"));
        assert a.items().equals(b.items());

        a.add("mug", 1); // delayed old add cannot resurrect a newer removal
        b.merge(a);
        assert b.items().equals(Set.of("pen"));

        AddWinsCart beforeDuplicate = b.copy();
        b.merge(a);
        b.merge(a);
        assert b.items().equals(beforeDuplicate.items()); // idempotent repair
    }

    public static void main(String[] args) {
        if (!ConsistencyContracts.class.desiredAssertionStatus()) {
            throw new IllegalStateException("run with -ea");
        }
        atomicTransferScenarios();
        unknownOutcomeScenarios();
        replicaScenarios();
        convergenceScenarios();
        System.out.println("PASS 4 scenario groups: atomicity, idempotency, replica routing, convergence");
    }
}
