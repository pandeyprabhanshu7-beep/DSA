import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Executable teaching models for guide Sections 1-3. Run with assertions enabled. */
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

    record CapacityPlan(int levels, long leafPages, List<Long> pagesBottomUp) {}

    static CapacityPlan capacityPlan(long entries, int leafCapacity, int fanout) {
        if (entries <= 0) throw new IllegalArgumentException("entries must be positive");
        if (leafCapacity < 2 || fanout < 2) {
            throw new IllegalArgumentException("leaf capacity and fanout must be at least two");
        }
        long leaves = ceilDiv(entries, leafCapacity);
        List<Long> pages = new ArrayList<>();
        pages.add(leaves);
        long current = leaves;
        while (current > 1) {
            current = ceilDiv(current, fanout);
            pages.add(current);
        }
        return new CapacityPlan(pages.size(), leaves, List.copyOf(pages));
    }

    private static long ceilDiv(long value, long divisor) {
        return 1 + (value - 1) / divisor;
    }

    record OrderKey(int tenantId, long createdAt, int orderId) {}

    static List<OrderKey> newestInWindow(
            List<OrderKey> indexOrder, int tenantId, long fromInclusive, long toExclusive, int limit) {
        if (fromInclusive > toExclusive || limit < 0) throw new IllegalArgumentException("invalid range");
        List<OrderKey> result = new ArrayList<>();
        for (OrderKey key : indexOrder) {
            if (key.tenantId() < tenantId) continue;
            if (key.tenantId() > tenantId) break;
            if (key.createdAt() < fromInclusive) break; // descending time within tenant
            if (key.createdAt() < toExclusive && result.size() < limit) result.add(key);
            if (result.size() == limit) break;
        }
        return List.copyOf(result);
    }

    static long extraSecondaryKeyBytes(
            long rows, int secondaryIndexes, int oldPrimaryKeyBytes, int newPrimaryKeyBytes) {
        if (rows < 0 || secondaryIndexes < 0 || oldPrimaryKeyBytes < 0 || newPrimaryKeyBytes < 0) {
            throw new IllegalArgumentException("dimensions must not be negative");
        }
        int delta = Math.subtractExact(newPrimaryKeyBytes, oldPrimaryKeyBytes);
        return Math.multiplyExact(Math.multiplyExact(rows, secondaryIndexes), delta);
    }

    record LeafInsert(List<Integer> left, List<Integer> right, Integer separator, boolean split) {}

    static LeafInsert insertIntoLeaf(int[] sortedDistinctKeys, int key, int capacity) {
        if (capacity < 2 || sortedDistinctKeys.length > capacity) {
            throw new IllegalArgumentException("invalid leaf capacity/state");
        }
        int found = Arrays.binarySearch(sortedDistinctKeys, key);
        if (found >= 0) throw new IllegalArgumentException("duplicate key");
        int insertion = -found - 1;
        List<Integer> merged = new ArrayList<>(sortedDistinctKeys.length + 1);
        for (int i = 0; i < insertion; i++) merged.add(sortedDistinctKeys[i]);
        merged.add(key);
        for (int i = insertion; i < sortedDistinctKeys.length; i++) merged.add(sortedDistinctKeys[i]);
        if (merged.size() <= capacity) {
            return new LeafInsert(List.copyOf(merged), List.of(), null, false);
        }
        int splitAt = merged.size() / 2;
        List<Integer> left = List.copyOf(merged.subList(0, splitAt));
        List<Integer> right = List.copyOf(merged.subList(splitAt, merged.size()));
        return new LeafInsert(left, right, right.get(0), true);
    }

    record LsmVersion(long sequence, String value, boolean tombstone) {
        LsmVersion {
            if (sequence <= 0) throw new IllegalArgumentException("sequence must be positive");
            if (tombstone && value != null) throw new IllegalArgumentException("tombstone has no value");
            if (!tombstone && value == null) throw new IllegalArgumentException("value is required");
        }
    }

    static final class LsmBuffer {
        private final List<Map.Entry<String, LsmVersion>> wal = new ArrayList<>();
        private final Map<String, LsmVersion> mutable = new HashMap<>();
        private final List<Map<String, LsmVersion>> tables = new ArrayList<>();

        void put(String key, String value, long sequence) {
            apply(key, new LsmVersion(sequence, value, false));
        }

        void delete(String key, long sequence) {
            apply(key, new LsmVersion(sequence, null, true));
        }

        private void apply(String key, LsmVersion version) {
            if (key.isBlank()) throw new IllegalArgumentException("key is required");
            wal.add(Map.entry(key, version)); // teaching model: WAL publication precedes memtable
            mutable.merge(key, version, LsmBuffer::newer);
        }

        void flush() {
            if (mutable.isEmpty()) return;
            tables.add(Map.copyOf(mutable));
            mutable.clear();
            wal.clear(); // safe only because the flushed table is now the durable model state
        }

        LsmBuffer crashAndRecover() {
            LsmBuffer recovered = new LsmBuffer();
            recovered.tables.addAll(tables);
            for (Map.Entry<String, LsmVersion> entry : wal) {
                recovered.mutable.merge(entry.getKey(), entry.getValue(), LsmBuffer::newer);
                recovered.wal.add(entry);
            }
            return recovered;
        }

        List<LsmVersion> candidates(String key) {
            List<LsmVersion> result = new ArrayList<>();
            if (mutable.containsKey(key)) result.add(mutable.get(key));
            for (Map<String, LsmVersion> table : tables) {
                if (table.containsKey(key)) result.add(table.get(key));
            }
            return List.copyOf(result);
        }

        int walEntries() { return wal.size(); }
        int tableCount() { return tables.size(); }

        private static LsmVersion newer(LsmVersion left, LsmVersion right) {
            return left.sequence() >= right.sequence() ? left : right;
        }
    }

    record LsmRead(boolean found, String value, long sequence, int candidatesChecked) {}

    static LsmRead newestVisible(
            List<LsmVersion> candidates, long snapshotSequence, boolean bloomMayContain) {
        if (snapshotSequence < 0) throw new IllegalArgumentException("snapshot must not be negative");
        if (!bloomMayContain) return new LsmRead(false, null, -1, 0);
        LsmVersion best = null;
        int checked = 0;
        for (LsmVersion candidate : candidates) {
            checked++;
            if (candidate.sequence() <= snapshotSequence
                    && (best == null || candidate.sequence() > best.sequence())) {
                best = candidate;
            }
        }
        if (best == null || best.tombstone()) {
            return new LsmRead(false, null, best == null ? -1 : best.sequence(), checked);
        }
        return new LsmRead(true, best.value(), best.sequence(), checked);
    }

    static boolean canDropTombstone(
            long tombstoneSequence, long oldestSnapshotSequence, boolean completeHistoryCoverage) {
        if (tombstoneSequence <= 0 || oldestSnapshotSequence < 0) {
            throw new IllegalArgumentException("invalid sequence");
        }
        return completeHistoryCoverage && tombstoneSequence < oldestSnapshotSequence;
    }

    record Amplification(double writeAmplification, double readAmplification, long headroomBytes) {}

    static Amplification amplification(
            long logicalWriteBytes,
            long walBytes,
            long flushBytes,
            long compactionBytesWritten,
            long bytesRead,
            long usefulBytesReturned,
            long liveBytes,
            long obsoleteBytes,
            long compactionOverlapBytes) {
        long[] values = {
            logicalWriteBytes, walBytes, flushBytes, compactionBytesWritten,
            bytesRead, usefulBytesReturned, liveBytes, obsoleteBytes, compactionOverlapBytes
        };
        for (long value : values) if (value < 0) throw new IllegalArgumentException("negative bytes");
        if (logicalWriteBytes == 0 || usefulBytesReturned == 0) {
            throw new IllegalArgumentException("ratio denominator must be positive");
        }
        double wa = (walBytes + flushBytes + compactionBytesWritten) / (double) logicalWriteBytes;
        double ra = bytesRead / (double) usefulBytesReturned;
        long headroom = Math.addExact(Math.addExact(liveBytes, obsoleteBytes), compactionOverlapBytes);
        return new Amplification(wa, ra, headroom);
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

    private static void capacityScenarios() {
        CapacityPlan million = capacityPlan(1_000_000, 200, 200);
        assert million.levels() == 3;
        assert million.leafPages() == 5_000;
        assert million.pagesBottomUp().equals(List.of(5_000L, 25L, 1L));
        assert capacityPlan(200, 200, 200).pagesBottomUp().equals(List.of(1L));
        try {
            capacityPlan(1, 1, 200);
            throw new AssertionError("invalid capacity accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void compositeRangeScenarios() {
        List<OrderKey> ordered = List.of(
                new OrderKey(6, 200, 1),
                new OrderKey(7, 130, 70),
                new OrderKey(7, 120, 71),
                new OrderKey(7, 110, 72),
                new OrderKey(7, 90, 73),
                new OrderKey(8, 140, 80));
        List<OrderKey> firstTwo = newestInWindow(ordered, 7, 100, 130, 2);
        assert firstTwo.stream().map(OrderKey::orderId).toList().equals(List.of(71, 72));
        assert newestInWindow(ordered, 9, 0, 500, 20).isEmpty();
    }

    private static void secondaryIndexFootprintScenarios() {
        long extra = extraSecondaryKeyBytes(50_000_000L, 4, 8, 36);
        assert extra == 5_600_000_000L;
        assert extraSecondaryKeyBytes(50_000_000L, 0, 8, 36) == 0;
        try {
            extraSecondaryKeyBytes(-1, 4, 8, 36);
            throw new AssertionError("negative dimensions accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void leafSplitScenarios() {
        LeafInsert split = insertIntoLeaf(new int[] {18, 24, 31}, 28, 3);
        assert split.split();
        assert split.left().equals(List.of(18, 24));
        assert split.right().equals(List.of(28, 31));
        assert split.separator() == 28;

        LeafInsert noSplit = insertIntoLeaf(new int[] {18, 24}, 32, 3);
        assert !noSplit.split();
        assert noSplit.left().equals(List.of(18, 24, 32));
        try {
            insertIntoLeaf(new int[] {18, 24}, 24, 3);
            throw new AssertionError("duplicate key accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void lsmWritePathScenarios() {
        LsmBuffer db = new LsmBuffer();
        db.put("cart:7", "v1", 10);
        db.put("cart:7", "v2", 11);
        assert db.walEntries() == 2;
        LsmBuffer recovered = db.crashAndRecover();
        LsmRead read = newestVisible(recovered.candidates("cart:7"), 11, true);
        assert read.found() && read.value().equals("v2") && read.sequence() == 11;

        recovered.flush();
        assert recovered.walEntries() == 0;
        assert recovered.tableCount() == 1;
        assert newestVisible(recovered.candidates("cart:7"), 11, true).value().equals("v2");
    }

    private static void lsmSnapshotReadScenarios() {
        List<LsmVersion> versions = List.of(
                new LsmVersion(30, null, true),
                new LsmVersion(25, "new", false),
                new LsmVersion(12, "old", false));
        LsmRead at20 = newestVisible(versions, 20, true);
        assert at20.found() && at20.value().equals("old") && at20.sequence() == 12;
        LsmRead at27 = newestVisible(versions, 27, true);
        assert at27.found() && at27.value().equals("new") && at27.sequence() == 25;
        LsmRead at30 = newestVisible(versions, 30, true);
        assert !at30.found() && at30.sequence() == 30;
        assert newestVisible(versions, 30, false).candidatesChecked() == 0;
    }

    private static void lsmTombstoneScenarios() {
        assert !canDropTombstone(50, 40, true);  // an older snapshot still needs pre-delete state
        assert !canDropTombstone(50, 80, false); // an older version may survive outside the inputs
        assert canDropTombstone(50, 80, true);
    }

    private static void lsmAmplificationScenarios() {
        Amplification plan = amplification(
                100, 100, 100, 500,
                64, 8,
                1_000, 120, 300);
        assert plan.writeAmplification() == 7.0;
        assert plan.readAmplification() == 8.0;
        assert plan.headroomBytes() == 1_420;
        try {
            amplification(0, 0, 0, 0, 0, 1, 0, 0, 0);
            throw new AssertionError("zero logical bytes accepted");
        } catch (IllegalArgumentException expected) {}
    }

    public static void main(String[] args) {
        if (!ConsistencyContracts.class.desiredAssertionStatus()) {
            throw new IllegalStateException("run with -ea");
        }
        atomicTransferScenarios();
        unknownOutcomeScenarios();
        replicaScenarios();
        convergenceScenarios();
        capacityScenarios();
        compositeRangeScenarios();
        secondaryIndexFootprintScenarios();
        leafSplitScenarios();
        lsmWritePathScenarios();
        lsmSnapshotReadScenarios();
        lsmTombstoneScenarios();
        lsmAmplificationScenarios();
        System.out.println("PASS 12 scenario groups: atomicity, idempotency, replica routing, convergence, capacity, composite range, secondary bytes, leaf split, LSM recovery, snapshot reads, tombstones, amplification");
    }
}

