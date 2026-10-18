import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/** Executable teaching models for guide Sections 1-14. Run with assertions enabled. */
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

    record ScanEstimate(long rowStoreBytes, long selectedColumnBytes, double reductionFactor) {}

    static ScanEstimate scanEstimate(long rows, int rowWidthBytes, int... selectedColumnWidths) {
        if (rows <= 0 || rowWidthBytes <= 0 || selectedColumnWidths.length == 0) {
            throw new IllegalArgumentException("positive rows, row width, and selected columns required");
        }
        long selectedWidth = 0;
        for (int width : selectedColumnWidths) {
            if (width <= 0) throw new IllegalArgumentException("column width must be positive");
            selectedWidth = Math.addExact(selectedWidth, width);
        }
        if (selectedWidth > rowWidthBytes) {
            throw new IllegalArgumentException("selected columns exceed the modeled row width");
        }
        long rowBytes = Math.multiplyExact(rows, rowWidthBytes);
        long columnBytes = Math.multiplyExact(rows, selectedWidth);
        return new ScanEstimate(rowBytes, columnBytes, rowBytes / (double) columnBytes);
    }

    record BatchAggregate(long sumCents, int batches, int rowsChecked) {}

    static BatchAggregate sumRevenueForRegion(
            int[] regionIds, long[] revenueCents, int targetRegion, int batchSize) {
        if (regionIds.length != revenueCents.length || batchSize <= 0) {
            throw new IllegalArgumentException("aligned columns and a positive batch size required");
        }
        long sum = 0;
        int batches = 0;
        for (int start = 0; start < regionIds.length; start += batchSize) {
            int end = Math.min(start + batchSize, regionIds.length);
            batches++;
            for (int row = start; row < end; row++) {
                if (regionIds[row] == targetRegion) {
                    sum = Math.addExact(sum, revenueCents[row]);
                }
            }
        }
        return new BatchAggregate(sum, batches, regionIds.length);
    }

    record Granule(int minTenant, int maxTenant, long minDay, long maxDay, long rows) {
        Granule {
            if (minTenant > maxTenant || minDay > maxDay || rows <= 0) {
                throw new IllegalArgumentException("invalid granule bounds");
            }
        }
    }

    record GranuleSelection(List<Integer> candidateIndexes, long candidateRows) {}

    static GranuleSelection candidateGranules(
            List<Granule> granules, int tenant, long fromDay, long toDay) {
        if (fromDay > toDay) throw new IllegalArgumentException("invalid day range");
        List<Integer> candidates = new ArrayList<>();
        long rows = 0;
        for (int i = 0; i < granules.size(); i++) {
            Granule granule = granules.get(i);
            boolean tenantMayMatch = granule.minTenant() <= tenant && tenant <= granule.maxTenant();
            boolean dayMayMatch = granule.minDay() <= toDay && fromDay <= granule.maxDay();
            if (tenantMayMatch && dayMayMatch) {
                candidates.add(i);
                rows = Math.addExact(rows, granule.rows());
            }
        }
        return new GranuleSelection(List.copyOf(candidates), rows);
    }

    enum ApplyStatus { APPLIED, DUPLICATE, STALE, GAP }
    record OrderEvent(String eventId, String orderId, long version, long amountCents) {
        OrderEvent {
            if (eventId.isBlank() || orderId.isBlank() || version <= 0 || amountCents < 0) {
                throw new IllegalArgumentException("invalid order event");
            }
        }
    }
    record ProjectionResult(ApplyStatus status, long expectedVersion, long observedVersion) {}
    record ProjectedOrder(long version, long amountCents) {}

    static final class OrderProjection {
        private final Set<String> seenEventIds = new HashSet<>();
        private final Map<String, ProjectedOrder> orders = new HashMap<>();

        ProjectionResult apply(OrderEvent event) {
            ProjectedOrder current = orders.get(event.orderId());
            long currentVersion = current == null ? 0 : current.version();
            long expected = currentVersion + 1;
            if (seenEventIds.contains(event.eventId())) {
                return new ProjectionResult(ApplyStatus.DUPLICATE, expected, event.version());
            }
            if (event.version() <= currentVersion) {
                seenEventIds.add(event.eventId());
                return new ProjectionResult(ApplyStatus.STALE, expected, event.version());
            }
            if (event.version() != expected) {
                return new ProjectionResult(ApplyStatus.GAP, expected, event.version());
            }
            orders.put(event.orderId(), new ProjectedOrder(event.version(), event.amountCents()));
            seenEventIds.add(event.eventId());
            return new ProjectionResult(ApplyStatus.APPLIED, expected, event.version());
        }

        ProjectedOrder order(String orderId) { return orders.get(orderId); }

        long totalAmountCents() {
            return orders.values().stream().mapToLong(ProjectedOrder::amountCents).sum();
        }
    }

    enum SnapshotPolicy { STATEMENT, TRANSACTION }
    record CommittedValue(long version, int value) {}
    record RepeatRead(int first, int second, long firstSnapshot, long secondSnapshot) {}

    static RepeatRead repeatReadAfterCommit(SnapshotPolicy policy) {
        List<CommittedValue> history = List.of(
                new CommittedValue(1, 10),
                new CommittedValue(2, 20));
        long transactionSnapshot = 1;
        long firstSnapshot = transactionSnapshot;
        // A concurrent transaction commits version 2 between the two statements.
        long secondSnapshot = policy == SnapshotPolicy.STATEMENT ? 2 : transactionSnapshot;
        return new RepeatRead(
                visibleAt(history, firstSnapshot),
                visibleAt(history, secondSnapshot),
                firstSnapshot,
                secondSnapshot);
    }

    private static int visibleAt(List<CommittedValue> history, long snapshot) {
        CommittedValue visible = null;
        for (CommittedValue candidate : history) {
            if (candidate.version() <= snapshot
                    && (visible == null || candidate.version() > visible.version())) {
                visible = candidate;
            }
        }
        if (visible == null) throw new IllegalArgumentException("snapshot predates history");
        return visible.value();
    }

    record RosterResult(boolean aliceOnCall, boolean bobOnCall, boolean secondCommitted) {
        int onCallCount() { return (aliceOnCall ? 1 : 0) + (bobOnCall ? 1 : 0); }
    }

    static RosterResult concurrentOffCall(boolean validatePredicateVersion) {
        boolean alice = true;
        boolean bob = true;
        long rosterVersion = 1;
        long aliceSnapshotVersion = rosterVersion;
        long bobSnapshotVersion = rosterVersion;
        assert alice && bob; // both transactions read the same two-person snapshot

        alice = false;
        rosterVersion++; // Alice commits an update to her own row

        if (validatePredicateVersion && bobSnapshotVersion != rosterVersion) {
            return new RosterResult(alice, bob, false); // serialization failure; retry Bob
        }
        bob = false; // snapshot isolation alone sees no same-row write/write conflict
        return new RosterResult(alice, bob, true);
    }

    record CounterOutcome(int storedValue, boolean conflictDetected) {}

    static CounterOutcome blindIncrement(int initial) {
        int firstRead = initial;
        int secondRead = initial;
        int afterFirstCommit = Math.incrementExact(firstRead);
        int afterSecondCommit = Math.incrementExact(secondRead); // overwrites the first result
        assert afterFirstCommit == initial + 1;
        return new CounterOutcome(afterSecondCommit, false);
    }

    static CounterOutcome versionCheckedIncrement(int initial) {
        int value = initial;
        long version = 1;
        long firstExpected = version;
        long secondExpected = version;
        value = Math.incrementExact(value);
        version++;
        boolean secondConflicted = secondExpected != version;
        assert firstExpected + 1 == version;
        return new CounterOutcome(value, secondConflicted);
    }

    static final class SerializableCounter {
        private int value;
        private long version;

        SerializableCounter(int value) { this.value = value; }

        record Snapshot(int value, long version) {}
        Snapshot snapshot() { return new Snapshot(value, version); }

        boolean commit(Snapshot snapshot, int nextValue) {
            if (snapshot.version() != version) return false;
            value = nextValue;
            version++;
            return true;
        }

        void concurrentIncrement() {
            value = Math.incrementExact(value);
            version++;
        }
    }

    record RetryOutcome(int attempts, int committedValue, List<String> durableEffects) {}

    static RetryOutcome incrementWithWholeTransactionRetry(
            SerializableCounter counter, String operationId, int maxAttempts) {
        if (operationId.isBlank() || maxAttempts <= 0) {
            throw new IllegalArgumentException("operation ID and positive attempt bound required");
        }
        Set<String> outbox = new HashSet<>();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            SerializableCounter.Snapshot snapshot = counter.snapshot();
            int decidedValue = Math.incrementExact(snapshot.value());
            if (attempt == 1) counter.concurrentIncrement(); // force one serialization conflict
            if (counter.commit(snapshot, decidedValue)) {
                outbox.add(operationId); // publish only with the successful commit
                return new RetryOutcome(attempt, decidedValue, List.copyOf(outbox));
            }
            // A real client rolls back and reruns every read and decision with backoff/deadline.
        }
        throw new IllegalStateException("retry budget exhausted");
    }

    record TopologyEstimate(
            long duplicatedLocalBytes, long sharedBytes, double expectedReadMillis) {}

    static TopologyEstimate topologyEstimate(
            int instances,
            long l1EntriesPerInstance,
            long l2Entries,
            long averageEntryBytes,
            double l1HitRate,
            double l2HitRateAfterL1Miss,
            double l1Millis,
            double l2Millis,
            double authorityMillis) {
        if (instances <= 0 || l1EntriesPerInstance < 0 || l2Entries < 0
                || averageEntryBytes <= 0 || l1HitRate < 0 || l1HitRate > 1
                || l2HitRateAfterL1Miss < 0 || l2HitRateAfterL1Miss > 1
                || l1Millis < 0 || l2Millis < 0 || authorityMillis < 0) {
            throw new IllegalArgumentException("invalid topology estimate");
        }
        long duplicatedLocalBytes = Math.multiplyExact(
                Math.multiplyExact((long) instances, l1EntriesPerInstance), averageEntryBytes);
        long sharedBytes = Math.multiplyExact(l2Entries, averageEntryBytes);
        double l1MissRate = 1 - l1HitRate;
        double l2MissRate = 1 - l2HitRateAfterL1Miss;
        double expectedReadMillis = l1Millis
                + l1MissRate * (l2Millis + l2MissRate * authorityMillis);
        return new TopologyEstimate(duplicatedLocalBytes, sharedBytes, expectedReadMillis);
    }

    record AdmissionDecision(boolean admitted, String retainedKey) {}

    static AdmissionDecision tinyLfuAdmissionModel(
            String candidateKey, int candidateFrequency, String victimKey, int victimFrequency) {
        if (candidateKey.isBlank() || victimKey.isBlank()
                || candidateFrequency < 0 || victimFrequency < 0) {
            throw new IllegalArgumentException("keys and non-negative estimates required");
        }
        boolean admitted = candidateFrequency > victimFrequency;
        return new AdmissionDecision(admitted, admitted ? candidateKey : victimKey);
    }

    record CacheValue(long version, String payload) {
        CacheValue {
            if (version < 0 || payload == null) throw new IllegalArgumentException("invalid value");
        }
    }

    static final class TwoLevelCache {
        private final Map<String, CacheValue> authority = new HashMap<>();
        private final Map<String, CacheValue> l2 = new HashMap<>();
        private final Map<String, Map<String, CacheValue>> l1ByNode = new HashMap<>();
        private final Set<String> trustedInvalidationConnection = new HashSet<>();

        void registerNode(String node) {
            l1ByNode.put(node, new HashMap<>());
            trustedInvalidationConnection.add(node);
        }

        void commitAuthority(String key, CacheValue value) {
            CacheValue current = authority.get(key);
            if (current != null && value.version() <= current.version()) {
                throw new IllegalArgumentException("version must advance");
            }
            authority.put(key, value);
        }

        CacheValue read(String node, String key) {
            Map<String, CacheValue> l1 = requireNode(node);
            if (!trustedInvalidationConnection.contains(node)) {
                throw new IllegalStateException("local cache is untrusted until reconnect flush");
            }
            CacheValue local = l1.get(key);
            if (local != null) return local;
            CacheValue shared = l2.get(key);
            if (shared == null) {
                shared = Objects.requireNonNull(authority.get(key), "authority miss");
                l2.put(key, shared);
            }
            l1.put(key, shared);
            return shared;
        }

        void loseInvalidationConnection(String node) {
            requireNode(node);
            trustedInvalidationConnection.remove(node);
        }

        void invalidateAfterCommit(String key, Set<String> nodesThatReceivedEvent) {
            l2.remove(key);
            for (String node : nodesThatReceivedEvent) requireNode(node).remove(key);
        }

        CacheValue unsafeLocalForTrace(String node, String key) {
            return requireNode(node).get(key);
        }

        void reconnectAndFlush(String node) {
            requireNode(node).clear();
            trustedInvalidationConnection.add(node);
        }

        private Map<String, CacheValue> requireNode(String node) {
            Map<String, CacheValue> cache = l1ByNode.get(node);
            if (cache == null) throw new IllegalArgumentException("unknown node");
            return cache;
        }
    }

    record ScopedCacheKey(
            String tenant, String articleId, String locale, int schemaVersion, long permissionEpoch) {
        ScopedCacheKey {
            if (tenant.isBlank() || articleId.isBlank() || locale.isBlank()
                    || schemaVersion <= 0 || permissionEpoch < 0) {
                throw new IllegalArgumentException("invalid cache-key dimension");
            }
        }
    }

    static String unsafeArticleKey(String articleId) {
        return "article:" + articleId;
    }

    static boolean mayServePersonalizedValue(boolean authorizedNow, CacheValue cached) {
        return authorizedNow && cached != null;
    }

    record PendingFill(CacheValue value, long observedGeneration) {}

    static final class CacheAsideRace {
        private CacheValue authority;
        private CacheValue cache;
        private long generation;

        CacheAsideRace(CacheValue initial) { authority = initial; }

        PendingFill beginMiss() { return new PendingFill(authority, generation); }

        void commitAndInvalidate(CacheValue next) {
            if (next.version() <= authority.version()) {
                throw new IllegalArgumentException("authority version must advance");
            }
            authority = next;
            generation++;
            cache = null;
        }

        void publishUnsafe(PendingFill fill) { cache = fill.value(); }

        boolean publishIfCurrent(PendingFill fill) {
            if (fill.observedGeneration() != generation
                    || fill.value().version() < authority.version()) return false;
            cache = fill.value();
            return true;
        }

        CacheValue cacheValue() { return cache; }
        CacheValue authorityValue() { return authority; }
    }

    enum LoadKind { VALUE, ABSENT, FAILURE }
    record LoadOutcome(LoadKind kind, CacheValue value) {
        LoadOutcome {
            Objects.requireNonNull(kind, "kind");
            if ((kind == LoadKind.VALUE) != (value != null)) {
                throw new IllegalArgumentException("only VALUE has a value");
            }
        }
    }
    record LoadWave(LoadOutcome outcome, int loaderCalls, boolean cached) {}

    static final class ReadThroughModel {
        private final Map<String, CacheValue> values = new HashMap<>();
        private final Set<String> knownAbsent = new HashSet<>();
        private int loaderCalls;

        LoadWave requestWave(
                String key, int sameJvmCallers, LoadOutcome loaded, boolean negativeCacheAbsent) {
            if (key.isBlank() || sameJvmCallers <= 0) {
                throw new IllegalArgumentException("key and positive caller count required");
            }
            CacheValue cached = values.get(key);
            if (cached != null) {
                return new LoadWave(new LoadOutcome(LoadKind.VALUE, cached), loaderCalls, true);
            }
            if (knownAbsent.contains(key)) {
                return new LoadWave(new LoadOutcome(LoadKind.ABSENT, null), loaderCalls, true);
            }

            // One local in-flight computation represents same-key coalescing in one JVM.
            loaderCalls++;
            if (loaded.kind() == LoadKind.VALUE) values.put(key, loaded.value());
            if (loaded.kind() == LoadKind.ABSENT && negativeCacheAbsent) knownAbsent.add(key);
            return new LoadWave(loaded, loaderCalls,
                    loaded.kind() == LoadKind.VALUE
                            || (loaded.kind() == LoadKind.ABSENT && negativeCacheAbsent));
        }
    }

    enum WriteThroughStatus { COMMITTED_AND_CACHED, COMMITTED_REPAIR_QUEUED }
    record RepairTask(String operationId, String key, CacheValue value) {}

    static final class WriteThroughStore {
        private final Map<String, CacheValue> authority = new HashMap<>();
        private final Map<String, CacheValue> cache = new HashMap<>();
        private final Map<String, RepairTask> repairOutbox = new HashMap<>();
        private final Set<String> degradedKeys = new HashSet<>();
        private final Set<String> appliedRepairs = new HashSet<>();

        void seed(String key, CacheValue value) {
            authority.put(key, value);
            cache.put(key, value);
        }

        WriteThroughStatus write(
                String operationId, String key, CacheValue value, boolean cacheWriteFails) {
            if (operationId.isBlank() || key.isBlank()) {
                throw new IllegalArgumentException("operation ID and key required");
            }
            CacheValue current = authority.get(key);
            if (current != null && value.version() <= current.version()) {
                throw new IllegalArgumentException("authority version must advance");
            }
            authority.put(key, value); // committed; a later cache error cannot undo this
            if (cacheWriteFails) {
                repairOutbox.putIfAbsent(operationId, new RepairTask(operationId, key, value));
                degradedKeys.add(key);
                return WriteThroughStatus.COMMITTED_REPAIR_QUEUED;
            }
            cache.put(key, value);
            degradedKeys.remove(key);
            return WriteThroughStatus.COMMITTED_AND_CACHED;
        }

        CacheValue read(String key) {
            return degradedKeys.contains(key) ? authority.get(key) : cache.get(key);
        }

        void replayRepair(String operationId) {
            RepairTask task = Objects.requireNonNull(repairOutbox.get(operationId), "repair task");
            if (appliedRepairs.add(operationId)) cache.put(task.key(), task.value());
            if (cache.get(task.key()).version() >= authority.get(task.key()).version()) {
                degradedKeys.remove(task.key());
            }
        }

        CacheValue cached(String key) { return cache.get(key); }
        int repairCount() { return repairOutbox.size(); }
    }

    record WriteIntent(String operationId, String key, CacheValue value) {}
    enum EnqueueStatus { ACKNOWLEDGED, REJECTED_FULL }
    enum ReplayStatus { APPLIED, DUPLICATE, VERSION_GAP }

    static final class WriteBehindStore {
        private final int capacity;
        private final Deque<WriteIntent> durableQueue = new ArrayDeque<>();
        private final Map<String, CacheValue> visibleCache = new HashMap<>();
        private final Map<String, CacheValue> authority = new HashMap<>();
        private final Set<String> appliedOperationIds = new HashSet<>();

        WriteBehindStore(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("positive capacity required");
            this.capacity = capacity;
        }

        EnqueueStatus submit(WriteIntent intent) {
            Objects.requireNonNull(intent, "intent");
            if (intent.operationId().isBlank() || intent.key().isBlank()) {
                throw new IllegalArgumentException("operation ID and key required");
            }
            if (durableQueue.size() == capacity) return EnqueueStatus.REJECTED_FULL;
            durableQueue.addLast(intent); // durable enqueue is the acknowledgement boundary
            CacheValue visible = visibleCache.get(intent.key());
            if (visible == null || intent.value().version() >= visible.version()) {
                visibleCache.put(intent.key(), intent.value());
            }
            return EnqueueStatus.ACKNOWLEDGED;
        }

        ReplayStatus replayNext() {
            WriteIntent intent = Objects.requireNonNull(durableQueue.pollFirst(), "empty queue");
            if (!appliedOperationIds.add(intent.operationId())) return ReplayStatus.DUPLICATE;
            CacheValue current = authority.get(intent.key());
            long expected = current == null ? 1 : current.version() + 1;
            if (intent.value().version() != expected) {
                appliedOperationIds.remove(intent.operationId());
                return ReplayStatus.VERSION_GAP;
            }
            authority.put(intent.key(), intent.value());
            return ReplayStatus.APPLIED;
        }

        CacheValue visible(String key) { return visibleCache.get(key); }
        CacheValue authoritative(String key) { return authority.get(key); }
        int queued() { return durableQueue.size(); }
    }

    static final class ExactLruCache {
        private final int capacity;
        private final LinkedHashMap<String, Integer> values =
                new LinkedHashMap<>(16, 0.75f, true);

        ExactLruCache(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("positive capacity required");
            this.capacity = capacity;
        }

        String put(String key, int value) {
            if (values.containsKey(key)) {
                values.put(key, value); // an update is an access in access-order mode
                return null;
            }
            String evicted = null;
            if (values.size() == capacity) {
                evicted = values.keySet().iterator().next();
                values.remove(evicted);
            }
            values.put(key, value);
            return evicted;
        }

        Integer get(String key) { return values.get(key); }
        boolean contains(String key) { return values.containsKey(key); }
        List<String> orderLeastToMostRecent() { return List.copyOf(values.keySet()); }
    }

    static final class FifoCache {
        private final int capacity;
        private final LinkedHashMap<String, Integer> values = new LinkedHashMap<>();

        FifoCache(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("positive capacity required");
            this.capacity = capacity;
        }

        String put(String key, int value) {
            if (values.containsKey(key)) {
                values.put(key, value); // insertion order is unchanged
                return null;
            }
            String evicted = null;
            if (values.size() == capacity) {
                evicted = values.keySet().iterator().next();
                values.remove(evicted);
            }
            values.put(key, value);
            return evicted;
        }

        Integer get(String key) { return values.get(key); }
        boolean contains(String key) { return values.containsKey(key); }
        List<String> orderOldestToNewest() { return List.copyOf(values.keySet()); }
    }

    static final class ExactLfuModel {
        private static final class Entry {
            int value;
            int frequency = 1;
            long lastTouch;

            Entry(int value, long lastTouch) {
                this.value = value;
                this.lastTouch = lastTouch;
            }
        }

        private final int capacity;
        private final Map<String, Entry> values = new HashMap<>();
        private long clock;

        ExactLfuModel(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("positive capacity required");
            this.capacity = capacity;
        }

        String put(String key, int value) {
            Entry existing = values.get(key);
            if (existing != null) {
                existing.value = value;
                touch(existing);
                return null;
            }
            String evicted = null;
            if (values.size() == capacity) {
                evicted = victim();
                values.remove(evicted);
            }
            values.put(key, new Entry(value, ++clock));
            return evicted;
        }

        Integer get(String key) {
            Entry entry = values.get(key);
            if (entry == null) return null;
            touch(entry);
            return entry.value;
        }

        void decay() {
            values.values().forEach(entry -> entry.frequency = Math.max(1,
                    (entry.frequency + 1) / 2));
        }

        int frequency(String key) { return values.get(key).frequency; }
        boolean contains(String key) { return values.containsKey(key); }

        private void touch(Entry entry) {
            entry.frequency = Math.incrementExact(entry.frequency);
            entry.lastTouch = ++clock;
        }

        private String victim() {
            String victim = null;
            Entry victimEntry = null;
            for (Map.Entry<String, Entry> candidate : values.entrySet()) {
                Entry entry = candidate.getValue();
                if (victimEntry == null || entry.frequency < victimEntry.frequency
                        || (entry.frequency == victimEntry.frequency
                                && entry.lastTouch < victimEntry.lastTouch)) {
                    victim = candidate.getKey();
                    victimEntry = entry;
                }
            }
            return victim;
        }
    }

    enum TwoQHit { NEW, A1IN_HIT, GHOST_HIT, AM_HIT }
    record TwoQResult(TwoQHit hit, String evictedResident, String droppedGhost) {}

    static final class FullTwoQueueModel {
        private final int a1inCapacity;
        private final int amCapacity;
        private final int ghostCapacity;
        private final LinkedHashMap<String, Boolean> a1in = new LinkedHashMap<>();
        private final LinkedHashMap<String, Boolean> a1out = new LinkedHashMap<>();
        private final LinkedHashMap<String, Boolean> am =
                new LinkedHashMap<>(16, 0.75f, true);

        FullTwoQueueModel(int a1inCapacity, int amCapacity, int ghostCapacity) {
            if (a1inCapacity <= 0 || amCapacity <= 0 || ghostCapacity <= 0) {
                throw new IllegalArgumentException("positive queue capacities required");
            }
            this.a1inCapacity = a1inCapacity;
            this.amCapacity = amCapacity;
            this.ghostCapacity = ghostCapacity;
        }

        TwoQResult request(String key) {
            if (key.isBlank()) throw new IllegalArgumentException("key required");
            if (am.get(key) != null) return new TwoQResult(TwoQHit.AM_HIT, null, null);
            if (a1in.containsKey(key)) {
                return new TwoQResult(TwoQHit.A1IN_HIT, null, null); // FIFO position unchanged
            }
            if (a1out.remove(key) != null) {
                String evicted = null;
                if (am.size() == amCapacity) {
                    evicted = removeEldest(am);
                }
                am.put(key, Boolean.TRUE);
                return new TwoQResult(TwoQHit.GHOST_HIT, evicted, null);
            }

            String evicted = null;
            String droppedGhost = null;
            if (a1in.size() == a1inCapacity) {
                evicted = removeEldest(a1in);
                if (a1out.size() == ghostCapacity) droppedGhost = removeEldest(a1out);
                a1out.put(evicted, Boolean.TRUE);
            }
            a1in.put(key, Boolean.TRUE);
            return new TwoQResult(TwoQHit.NEW, evicted, droppedGhost);
        }

        boolean resident(String key) { return a1in.containsKey(key) || am.containsKey(key); }
        List<String> a1inOrder() { return List.copyOf(a1in.keySet()); }
        List<String> ghostOrder() { return List.copyOf(a1out.keySet()); }
        List<String> amLeastToMostRecent() { return List.copyOf(am.keySet()); }

        private static String removeEldest(LinkedHashMap<String, Boolean> map) {
            String eldest = map.keySet().iterator().next();
            map.remove(eldest);
            return eldest;
        }
    }

    record StampedeEstimate(long concurrentLoads, long aggregateDatabaseMillis) {}

    static StampedeEstimate naiveExpiryBurst(long requestsPerSecond, long loadMillis) {
        if (requestsPerSecond < 0 || loadMillis < 0) {
            throw new IllegalArgumentException("rates and latency must not be negative");
        }
        long requestMillis = Math.multiplyExact(requestsPerSecond, loadMillis);
        long concurrent = requestMillis == 0 ? 0 : ceilDiv(requestMillis, 1_000);
        return new StampedeEstimate(concurrent, Math.multiplyExact(concurrent, loadMillis));
    }

    record Flight<V>(CompletableFuture<V> future, boolean leader) {}

    /** Local duplicate suppression: the leader owns completion; waiters share its outcome. */
    static final class LocalSingleFlight<K, V> {
        private final ConcurrentHashMap<K, CompletableFuture<V>> flights =
                new ConcurrentHashMap<>();

        Flight<V> begin(K key) {
            Objects.requireNonNull(key, "key");
            CompletableFuture<V> candidate = new CompletableFuture<>();
            CompletableFuture<V> existing = flights.putIfAbsent(key, candidate);
            return existing == null
                    ? new Flight<>(candidate, true)
                    : new Flight<>(existing, false);
        }

        void succeed(K key, Flight<V> flight, V value) {
            requireLeader(flight);
            flight.future().complete(value);
            flights.remove(key, flight.future()); // conditional removal cannot erase a successor
        }

        void fail(K key, Flight<V> flight, RuntimeException failure) {
            requireLeader(flight);
            flight.future().completeExceptionally(failure);
            flights.remove(key, flight.future());
        }

        int inFlight() { return flights.size(); }

        private static void requireLeader(Flight<?> flight) {
            Objects.requireNonNull(flight, "flight");
            if (!flight.leader()) throw new IllegalStateException("only the leader completes a flight");
        }
    }

    record FillLease(String token, long expiresAtMillis) {}

    /** Single-authority lease semantics used to expose expiry and ownership races. */
    static final class FillLeaseRegistry {
        private FillLease current;

        boolean acquire(String token, long nowMillis, long ttlMillis) {
            if (token.isBlank() || nowMillis < 0 || ttlMillis <= 0) {
                throw new IllegalArgumentException("token, time, and positive TTL required");
            }
            if (current != null && current.expiresAtMillis() > nowMillis) return false;
            current = new FillLease(token, Math.addExact(nowMillis, ttlMillis));
            return true;
        }

        boolean mayPublish(String token, long nowMillis) {
            return current != null
                    && current.token().equals(token)
                    && nowMillis < current.expiresAtMillis();
        }

        boolean release(String token) {
            if (current == null || !current.token().equals(token)) return false;
            current = null;
            return true;
        }

        String owner() { return current == null ? null : current.token(); }
    }

    enum FallbackDecision {
        VALUE_APPEARED,
        SERVE_BOUNDED_STALE,
        LOAD_WITH_DATABASE_PERMIT,
        REJECT_OVERLOAD
    }

    static FallbackDecision afterBoundedWait(
            boolean valueAppeared, boolean staleEligible, boolean databasePermitAvailable) {
        if (valueAppeared) return FallbackDecision.VALUE_APPEARED;
        if (staleEligible) return FallbackDecision.SERVE_BOUNDED_STALE;
        if (databasePermitAvailable) return FallbackDecision.LOAD_WITH_DATABASE_PERMIT;
        return FallbackDecision.REJECT_OVERLOAD;
    }

    /** Tenant-scoped negative cache with a deterministic clock and typed authority outcomes. */
    static final class NegativeCache {
        private final long negativeTtlMillis;
        private final Map<String, Long> absentUntil = new HashMap<>();
        private int authorityCalls;

        NegativeCache(long negativeTtlMillis) {
            if (negativeTtlMillis <= 0) {
                throw new IllegalArgumentException("positive negative TTL required");
            }
            this.negativeTtlMillis = negativeTtlMillis;
        }

        LoadKind lookup(
                String tenant, String objectId, long nowMillis, LoadKind authorityOutcome) {
            String key = scopedKey(tenant, objectId);
            Long expiresAt = absentUntil.get(key);
            if (expiresAt != null && nowMillis < expiresAt) return LoadKind.ABSENT;
            absentUntil.remove(key);

            authorityCalls++;
            if (authorityOutcome == LoadKind.ABSENT) {
                absentUntil.put(key, Math.addExact(nowMillis, negativeTtlMillis));
            } else {
                // A value or failure must not preserve an older absence decision.
                absentUntil.remove(key);
            }
            return authorityOutcome;
        }

        void invalidate(String tenant, String objectId) {
            absentUntil.remove(scopedKey(tenant, objectId));
        }

        int authorityCalls() { return authorityCalls; }

        private static String scopedKey(String tenant, String objectId) {
            if (tenant.isBlank() || objectId.isBlank()) {
                throw new IllegalArgumentException("tenant and object ID required");
            }
            return tenant.length() + ":" + tenant + ":" + objectId;
        }
    }

    /** Transparent Bloom-filter bit mechanics; callers supply already-derived positions. */
    static final class BloomBitModel {
        private final int bitCount;
        private final BitSet bits;

        BloomBitModel(int bitCount) {
            if (bitCount <= 0) throw new IllegalArgumentException("positive bit count required");
            this.bitCount = bitCount;
            this.bits = new BitSet(bitCount);
        }

        void put(int... positions) {
            requirePositions(positions);
            for (int position : positions) bits.set(position);
        }

        boolean mightContain(int... positions) {
            requirePositions(positions);
            for (int position : positions) {
                if (!bits.get(position)) return false;
            }
            return true;
        }

        void unsafeClearForDemonstration(int... positions) {
            requirePositions(positions);
            for (int position : positions) bits.clear(position);
        }

        private void requirePositions(int[] positions) {
            if (positions.length == 0) throw new IllegalArgumentException("hash positions required");
            for (int position : positions) {
                if (position < 0 || position >= bitCount) {
                    throw new IllegalArgumentException("position outside filter");
                }
            }
        }
    }

    record BloomPlan(long bits, long bytes, int hashFunctions, double bitsPerItem) {}

    static BloomPlan bloomPlan(long expectedInsertions, double falsePositiveProbability) {
        if (expectedInsertions <= 0
                || !(falsePositiveProbability > 0.0 && falsePositiveProbability < 1.0)) {
            throw new IllegalArgumentException("positive capacity and probability in (0,1) required");
        }
        double ln2 = Math.log(2.0);
        double exactBits = -expectedInsertions * Math.log(falsePositiveProbability) / (ln2 * ln2);
        if (exactBits > Long.MAX_VALUE) throw new IllegalArgumentException("filter too large");
        long bits = (long) Math.ceil(exactBits);
        int hashes = Math.max(1, (int) Math.round((double) bits / expectedInsertions * ln2));
        return new BloomPlan(bits, ceilDiv(bits, 8), hashes, (double) bits / expectedInsertions);
    }

    enum BloomGateDecision { RETURN_ABSENT, CHECK_NEXT_LAYER }

    static BloomGateDecision bloomGate(
            boolean mightContain, boolean completenessTrusted, boolean rebuilding) {
        if (!mightContain && completenessTrusted && !rebuilding) {
            return BloomGateDecision.RETURN_ABSENT;
        }
        return BloomGateDecision.CHECK_NEXT_LAYER;
    }

    static long jitteredTtlMillis(
            long baseTtlMillis, double jitterFraction, double unitSample, boolean strictMaximum) {
        if (baseTtlMillis <= 0
                || !(jitterFraction >= 0.0 && jitterFraction < 1.0)
                || !(unitSample >= 0.0 && unitSample <= 1.0)) {
            throw new IllegalArgumentException("positive TTL, jitter in [0,1), sample in [0,1] required");
        }
        double factor = strictMaximum
                ? (1.0 - jitterFraction) + unitSample * jitterFraction
                : (1.0 - jitterFraction) + unitSample * 2.0 * jitterFraction;
        double exact = baseTtlMillis * factor;
        if (exact > Long.MAX_VALUE) throw new IllegalArgumentException("jittered TTL too large");
        return Math.round(exact);
    }

    record WarmPlan(long averagePerSecond, long safePerSecond, long minimumSeconds) {}

    static WarmPlan coldStartWarmPlan(
            long keys,
            long requestedWindowSeconds,
            long originCapacityPerSecond,
            double reserveFraction) {
        if (keys <= 0 || requestedWindowSeconds <= 0 || originCapacityPerSecond <= 0
                || !(reserveFraction >= 0.0 && reserveFraction < 1.0)) {
            throw new IllegalArgumentException("positive dimensions and reserve in [0,1) required");
        }
        long average = ceilDiv(keys, requestedWindowSeconds);
        long safe = (long) Math.floor(originCapacityPerSecond * (1.0 - reserveFraction));
        if (safe <= 0) throw new IllegalArgumentException("reserve leaves no warming capacity");
        return new WarmPlan(average, safe, ceilDiv(keys, safe));
    }

    enum FreshnessDecision {
        FRESH,
        STALE_REFRESH_LEADER,
        STALE_REFRESH_FOLLOWER,
        EXPIRED
    }

    static FreshnessDecision freshnessDecision(
            long ageMillis,
            long softTtlMillis,
            long hardTtlMillis,
            boolean staleAllowed,
            boolean refreshLeader) {
        if (ageMillis < 0 || softTtlMillis <= 0 || hardTtlMillis < softTtlMillis) {
            throw new IllegalArgumentException("require 0 <= age and 0 < soft TTL <= hard TTL");
        }
        if (ageMillis < softTtlMillis) return FreshnessDecision.FRESH;
        if (ageMillis < hardTtlMillis && staleAllowed) {
            return refreshLeader
                    ? FreshnessDecision.STALE_REFRESH_LEADER
                    : FreshnessDecision.STALE_REFRESH_FOLLOWER;
        }
        return FreshnessDecision.EXPIRED;
    }

    record RetryAmplification(long requests, long attemptsPerRequest, long downstreamAttempts) {}

    static RetryAmplification retryAmplification(long requests, int... attemptsAtLayers) {
        if (requests < 0 || attemptsAtLayers.length == 0) {
            throw new IllegalArgumentException("non-negative requests and retry layers required");
        }
        long attemptsPerRequest = 1;
        for (int attempts : attemptsAtLayers) {
            if (attempts <= 0) throw new IllegalArgumentException("attempts must be positive");
            attemptsPerRequest = Math.multiplyExact(attemptsPerRequest, attempts);
        }
        return new RetryAmplification(
                requests,
                attemptsPerRequest,
                Math.multiplyExact(requests, attemptsPerRequest));
    }

    enum OutageDecision { SERVE_STALE, PROBE_ORIGIN, REJECT_OVERLOAD }

    static OutageDecision outageDecision(
            boolean staleAllowed,
            boolean circuitAllowsProbe,
            boolean retryTokenAvailable,
            boolean originPermitAvailable) {
        if (staleAllowed) return OutageDecision.SERVE_STALE;
        if (circuitAllowsProbe && retryTokenAvailable && originPermitAvailable) {
            return OutageDecision.PROBE_ORIGIN;
        }
        return OutageDecision.REJECT_OVERLOAD;
    }

    record PoolBudget(
            long applicationClientSlots,
            long pgbouncerServerSlots,
            long usablePostgresSlots,
            boolean withinBudget) {}

    static PoolBudget poolBudget(
            int applicationNodes,
            int primaryPoolPerNode,
            int replicaPoolPerNode,
            int pgbouncerInstances,
            int serverSlotsPerPgbouncer,
            int postgresConnectionLimit,
            int reservedPostgresSlots) {
        if (applicationNodes <= 0 || primaryPoolPerNode < 0 || replicaPoolPerNode < 0
                || pgbouncerInstances <= 0 || serverSlotsPerPgbouncer <= 0
                || postgresConnectionLimit <= 0 || reservedPostgresSlots < 0
                || reservedPostgresSlots >= postgresConnectionLimit) {
            throw new IllegalArgumentException("invalid pool-budget dimension");
        }
        long perNode = Math.addExact(primaryPoolPerNode, replicaPoolPerNode);
        long clientSlots = Math.multiplyExact(applicationNodes, perNode);
        long serverSlots = Math.multiplyExact(pgbouncerInstances, serverSlotsPerPgbouncer);
        long usable = Math.subtractExact(postgresConnectionLimit, reservedPostgresSlots);
        return new PoolBudget(clientSlots, serverSlots, usable, serverSlots <= usable);
    }

    record LogicalLease(int physicalSessionId, long generation) {}

    /** A logical close invalidates the lease and returns its physical session for reuse. */
    static final class LogicalConnectionPool {
        private final long[] generations;
        private final boolean[] inUse;

        LogicalConnectionPool(int physicalSessions) {
            if (physicalSessions <= 0) throw new IllegalArgumentException("positive pool size required");
            generations = new long[physicalSessions];
            inUse = new boolean[physicalSessions];
        }

        LogicalLease checkout() {
            for (int id = 0; id < inUse.length; id++) {
                if (!inUse[id]) {
                    inUse[id] = true;
                    return new LogicalLease(id, ++generations[id]);
                }
            }
            throw new IllegalStateException("logical checkout timed out");
        }

        void execute(LogicalLease lease) { requireActive(lease); }

        void close(LogicalLease lease) {
            requireActive(lease);
            inUse[lease.physicalSessionId()] = false;
        }

        int physicalSessionCount() { return inUse.length; }

        private void requireActive(LogicalLease lease) {
            Objects.requireNonNull(lease, "lease");
            int id = lease.physicalSessionId();
            if (id < 0 || id >= inUse.length || !inUse[id]
                    || generations[id] != lease.generation()) {
                throw new IllegalStateException("logical lease is closed or superseded");
            }
        }
    }

    enum DatabaseTarget { PRIMARY, REPLICA }
    enum DatabaseOperation { READ, WRITE }
    enum ReadConsistency { EVENTUAL, READ_YOUR_WRITES }

    static DatabaseTarget routeDatabase(
            DatabaseOperation operation,
            ReadConsistency consistency,
            long requiredReplayPosition,
            long replicaReplayPosition,
            boolean replicaAvailable) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(consistency, "consistency");
        if (requiredReplayPosition < 0 || replicaReplayPosition < 0) {
            throw new IllegalArgumentException("replay positions must not be negative");
        }
        if (operation == DatabaseOperation.WRITE || !replicaAvailable) {
            return DatabaseTarget.PRIMARY;
        }
        if (consistency == ReadConsistency.READ_YOUR_WRITES
                && replicaReplayPosition < requiredReplayPosition) {
            return DatabaseTarget.PRIMARY;
        }
        return DatabaseTarget.REPLICA;
    }

    enum PgBouncerMode { SESSION, TRANSACTION, STATEMENT }
    enum DatabaseFeature {
        SINGLE_STATEMENT,
        MULTI_STATEMENT_TRANSACTION,
        LISTEN,
        SESSION_ADVISORY_LOCK,
        PROTOCOL_PREPARED_STATEMENT,
        SQL_PREPARE
    }

    static boolean poolingModeSupports(
            PgBouncerMode mode, DatabaseFeature feature, int maxPreparedStatements) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(feature, "feature");
        if (maxPreparedStatements < 0) {
            throw new IllegalArgumentException("prepared statement limit must not be negative");
        }
        if (mode == PgBouncerMode.SESSION) return true;
        if (mode == PgBouncerMode.STATEMENT) {
            return feature == DatabaseFeature.SINGLE_STATEMENT
                    || (feature == DatabaseFeature.PROTOCOL_PREPARED_STATEMENT
                            && maxPreparedStatements > 0);
        }
        return switch (feature) {
            case SINGLE_STATEMENT, MULTI_STATEMENT_TRANSACTION -> true;
            case PROTOCOL_PREPARED_STATEMENT -> maxPreparedStatements > 0;
            case LISTEN, SESSION_ADVISORY_LOCK, SQL_PREPARE -> false;
        };
    }

    record DeadlineBudget(long totalMillis, long queryMillisRemaining, boolean exhausted) {}

    static DeadlineBudget deadlineAfterQueues(
            long totalMillis, long hikariWaitMillis, long pgbouncerWaitMillis) {
        if (totalMillis <= 0 || hikariWaitMillis < 0 || pgbouncerWaitMillis < 0) {
            throw new IllegalArgumentException("positive deadline and non-negative waits required");
        }
        long spent = Math.addExact(hikariWaitMillis, pgbouncerWaitMillis);
        if (spent >= totalMillis) return new DeadlineBudget(totalMillis, 0, true);
        return new DeadlineBudget(totalMillis, totalMillis - spent, false);
    }

    record ConnectionStartupCost(
            long dnsMillis,
            long tcpMillis,
            long tlsMillis,
            long authenticationMillis,
            long backendStartupMillis,
            long initializationMillis,
            long totalMillis) {}

    static ConnectionStartupCost connectionStartupCost(
            long dnsMillis,
            long tcpMillis,
            long tlsMillis,
            long authenticationMillis,
            long backendStartupMillis,
            long initializationMillis) {
        long[] phases = {
            dnsMillis, tcpMillis, tlsMillis, authenticationMillis,
            backendStartupMillis, initializationMillis
        };
        long total = 0;
        for (long phase : phases) {
            if (phase < 0) throw new IllegalArgumentException("startup phases must not be negative");
            total = Math.addExact(total, phase);
        }
        return new ConnectionStartupCost(
                dnsMillis,
                tcpMillis,
                tlsMillis,
                authenticationMillis,
                backendStartupMillis,
                initializationMillis,
                total);
    }

    record ConnectionChurn(
            long opensPerSecond,
            long retentionSeconds,
            long estimatedRetainedSockets,
            long opensPerMinute) {}

    static ConnectionChurn connectionChurn(long opensPerSecond, long retentionSeconds) {
        if (opensPerSecond < 0 || retentionSeconds < 0) {
            throw new IllegalArgumentException("churn inputs must not be negative");
        }
        return new ConnectionChurn(
                opensPerSecond,
                retentionSeconds,
                Math.multiplyExact(opensPerSecond, retentionSeconds),
                Math.multiplyExact(opensPerSecond, 60));
    }

    record ConcurrencyGate(
            int running,
            int waiting,
            int rejected,
            boolean databaseOversubscribed) {}

    static ConcurrencyGate concurrencyGate(
            int simultaneousRequests,
            int poolSize,
            int callerWaitLimit,
            int databaseSafeConcurrency) {
        if (simultaneousRequests < 0 || poolSize <= 0 || callerWaitLimit < 0
                || databaseSafeConcurrency <= 0) {
            throw new IllegalArgumentException("invalid concurrency-gate dimension");
        }
        int running = Math.min(simultaneousRequests, poolSize);
        int remaining = simultaneousRequests - running;
        int waiting = Math.min(remaining, callerWaitLimit);
        int rejected = remaining - waiting;
        return new ConcurrencyGate(
                running, waiting, rejected, running > databaseSafeConcurrency);
    }

    enum ConnectionReturnAction { REUSE, ROLLBACK_THEN_REUSE, EVICT }

    static ConnectionReturnAction connectionReturnAction(
            boolean connectionHealthy, boolean transactionOpen) {
        if (!connectionHealthy) return ConnectionReturnAction.EVICT;
        if (transactionOpen) return ConnectionReturnAction.ROLLBACK_THEN_REUSE;
        return ConnectionReturnAction.REUSE;
    }

    static final class ConnectionUseGuard {
        private final String owner;
        private boolean open = true;

        ConnectionUseGuard(String owner) {
            this.owner = Objects.requireNonNull(owner, "owner");
        }

        void execute(String caller) {
            if (!open) throw new IllegalStateException("connection lease is closed");
            if (!owner.equals(caller)) {
                throw new IllegalStateException("connection used by a concurrent borrower");
            }
        }

        void close(String caller) {
            execute(caller);
            open = false;
        }
    }

    enum IdleRetirementDecision {
        KEEP_BORROWED,
        KEEP_MINIMUM_IDLE,
        KEEP_YOUNG_IDLE,
        ELIGIBLE_SURPLUS_IDLE
    }

    static IdleRetirementDecision idleRetirementDecision(
            boolean borrowed,
            int idleConnections,
            int minimumIdle,
            int maximumPoolSize,
            long idleAgeMillis,
            long idleTimeoutMillis) {
        if (idleConnections < 0 || minimumIdle < 0 || maximumPoolSize <= 0
                || minimumIdle > maximumPoolSize || idleAgeMillis < 0 || idleTimeoutMillis < 0) {
            throw new IllegalArgumentException("invalid idle-retirement input");
        }
        if (borrowed) return IdleRetirementDecision.KEEP_BORROWED;
        if (minimumIdle >= maximumPoolSize || idleConnections <= minimumIdle) {
            return IdleRetirementDecision.KEEP_MINIMUM_IDLE;
        }
        if (idleTimeoutMillis == 0 || idleAgeMillis < idleTimeoutMillis) {
            return IdleRetirementDecision.KEEP_YOUNG_IDLE;
        }
        return IdleRetirementDecision.ELIGIBLE_SURPLUS_IDLE;
    }

    enum TimeoutBoundary { NONE, POOL_ACQUISITION, CONNECT, LOGIN, QUERY }

    record TimeoutTrace(
            long requestBudgetMillis,
            long spentMillis,
            long remainingMillis,
            TimeoutBoundary exhaustedAt) {}

    static TimeoutTrace timeoutTrace(
            long requestBudgetMillis,
            long poolMillis,
            long connectMillis,
            long loginMillis,
            long queryMillis) {
        if (requestBudgetMillis <= 0 || poolMillis < 0 || connectMillis < 0
                || loginMillis < 0 || queryMillis < 0) {
            throw new IllegalArgumentException("invalid timeout trace");
        }
        long spent = 0;
        long[] stages = {poolMillis, connectMillis, loginMillis, queryMillis};
        TimeoutBoundary[] boundaries = {
            TimeoutBoundary.POOL_ACQUISITION,
            TimeoutBoundary.CONNECT,
            TimeoutBoundary.LOGIN,
            TimeoutBoundary.QUERY
        };
        for (int i = 0; i < stages.length; i++) {
            spent = Math.addExact(spent, stages[i]);
            if (spent >= requestBudgetMillis) {
                return new TimeoutTrace(requestBudgetMillis, spent, 0, boundaries[i]);
            }
        }
        return new TimeoutTrace(
                requestBudgetMillis, spent, requestBudgetMillis - spent, TimeoutBoundary.NONE);
    }

    record WorkloadPoolPlan(
            long throughputPerSecond,
            long meanHoldMillis,
            long averageGlobalInUseCeiling,
            double averagePerInstanceInUse,
            long configuredGlobalMaximum,
            long usableDatabaseSlots,
            boolean configuredMaximumWithinBudget) {}

    static WorkloadPoolPlan workloadPoolPlan(
            long throughputPerSecond,
            long meanHoldMillis,
            int instances,
            int maximumPoolSizePerInstance,
            long usableDatabaseSlots) {
        if (throughputPerSecond < 0 || meanHoldMillis < 0 || instances <= 0
                || maximumPoolSizePerInstance <= 0 || usableDatabaseSlots <= 0) {
            throw new IllegalArgumentException("invalid workload pool input");
        }
        long occupancyMillis = Math.multiplyExact(throughputPerSecond, meanHoldMillis);
        long averageGlobalCeiling = Math.addExact(occupancyMillis, 999) / 1_000;
        long configuredGlobalMaximum = Math.multiplyExact(
                (long) instances, maximumPoolSizePerInstance);
        return new WorkloadPoolPlan(
                throughputPerSecond,
                meanHoldMillis,
                averageGlobalCeiling,
                occupancyMillis / 1_000.0 / instances,
                configuredGlobalMaximum,
                usableDatabaseSlots,
                configuredGlobalMaximum <= usableDatabaseSlots);
    }

    enum PoolSignal {
        HEALTHY,
        DATABASE_SATURATED,
        ACQUISITION_QUEUE,
        SLOW_CONNECTION_CREATION,
        LONG_HOLD_OUTSIDE_SQL
    }

    static PoolSignal poolSignal(
            int active,
            int maximumPoolSize,
            int pending,
            long acquisitionP99Millis,
            long usageP99Millis,
            long sqlP99Millis,
            long creationP99Millis,
            boolean databaseSaturated) {
        if (active < 0 || maximumPoolSize <= 0 || active > maximumPoolSize || pending < 0
                || acquisitionP99Millis < 0 || usageP99Millis < 0 || sqlP99Millis < 0
                || creationP99Millis < 0) {
            throw new IllegalArgumentException("invalid pool observation");
        }
        if (databaseSaturated && (active == maximumPoolSize || pending > 0)) {
            return PoolSignal.DATABASE_SATURATED;
        }
        if (pending > 0 && active < maximumPoolSize && creationP99Millis >= acquisitionP99Millis) {
            return PoolSignal.SLOW_CONNECTION_CREATION;
        }
        if (pending > 0 || (active == maximumPoolSize && acquisitionP99Millis > 0)) {
            return PoolSignal.ACQUISITION_QUEUE;
        }
        if (usageP99Millis > Math.multiplyExact(Math.max(1, sqlP99Millis), 2)) {
            return PoolSignal.LONG_HOLD_OUTSIDE_SQL;
        }
        return PoolSignal.HEALTHY;
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

    private static void columnSelectionScenarios() {
        ScanEstimate estimate = scanEstimate(10_000_000L, 200, 8, 8);
        assert estimate.rowStoreBytes() == 2_000_000_000L;
        assert estimate.selectedColumnBytes() == 160_000_000L;
        assert estimate.reductionFactor() == 12.5;
        try {
            scanEstimate(10, 8, 8, 8);
            throw new AssertionError("impossible selected width accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void vectorBatchScenarios() {
        BatchAggregate aggregate = sumRevenueForRegion(
                new int[] {1, 2, 1, 3, 1},
                new long[] {100, 200, 300, 400, 500},
                1,
                2);
        assert aggregate.sumCents() == 900;
        assert aggregate.batches() == 3;
        assert aggregate.rowsChecked() == 5;
        assert sumRevenueForRegion(new int[] {}, new long[] {}, 1, 4).sumCents() == 0;
    }

    private static void granulePruningScenarios() {
        List<Granule> granules = List.of(
                new Granule(1, 1, 1, 3, 8_192),
                new Granule(1, 1, 4, 6, 8_192),
                new Granule(1, 2, 7, 9, 8_192),
                new Granule(2, 2, 1, 3, 8_192),
                new Granule(2, 2, 4, 6, 8_192),
                new Granule(3, 3, 1, 9, 8_192));
        GranuleSelection selection = candidateGranules(granules, 2, 2, 5);
        assert selection.candidateIndexes().equals(List.of(3, 4));
        assert selection.candidateRows() == 16_384;
        assert candidateGranules(granules, 9, 1, 9).candidateIndexes().isEmpty();
    }

    private static void projectionScenarios() {
        OrderProjection projection = new OrderProjection();
        OrderEvent v1 = new OrderEvent("evt-1", "order-7", 1, 1_000);
        assert projection.apply(v1).status() == ApplyStatus.APPLIED;
        assert projection.apply(v1).status() == ApplyStatus.DUPLICATE;

        OrderEvent v3 = new OrderEvent("evt-3", "order-7", 3, 1_200);
        ProjectionResult gap = projection.apply(v3);
        assert gap.status() == ApplyStatus.GAP && gap.expectedVersion() == 2;
        assert projection.order("order-7").amountCents() == 1_000;

        assert projection.apply(new OrderEvent("evt-2", "order-7", 2, 1_100)).status()
                == ApplyStatus.APPLIED;
        assert projection.apply(v3).status() == ApplyStatus.APPLIED;
        assert projection.apply(new OrderEvent("evt-old", "order-7", 2, 999)).status()
                == ApplyStatus.STALE;
        assert projection.order("order-7").version() == 3;
        assert projection.totalAmountCents() == 1_200;
    }

    private static void statementSnapshotScenarios() {
        RepeatRead readCommitted = repeatReadAfterCommit(SnapshotPolicy.STATEMENT);
        assert readCommitted.first() == 10 && readCommitted.second() == 20;
        assert readCommitted.firstSnapshot() == 1 && readCommitted.secondSnapshot() == 2;

        RepeatRead repeatable = repeatReadAfterCommit(SnapshotPolicy.TRANSACTION);
        assert repeatable.first() == 10 && repeatable.second() == 10;
        assert repeatable.firstSnapshot() == repeatable.secondSnapshot();
    }

    private static void writeSkewScenarios() {
        RosterResult snapshotIsolation = concurrentOffCall(false);
        assert snapshotIsolation.secondCommitted();
        assert snapshotIsolation.onCallCount() == 0;

        RosterResult serializable = concurrentOffCall(true);
        assert !serializable.secondCommitted();
        assert serializable.onCallCount() == 1;
    }

    private static void lostUpdateScenarios() {
        CounterOutcome blind = blindIncrement(10);
        assert blind.storedValue() == 11 && !blind.conflictDetected();
        CounterOutcome guarded = versionCheckedIncrement(10);
        assert guarded.storedValue() == 11 && guarded.conflictDetected();
    }

    private static void serializationRetryScenarios() {
        RetryOutcome outcome = incrementWithWholeTransactionRetry(
                new SerializableCounter(10), "op-counter-7", 3);
        assert outcome.attempts() == 2;
        assert outcome.committedValue() == 12;
        assert outcome.durableEffects().equals(List.of("op-counter-7"));
        try {
            incrementWithWholeTransactionRetry(new SerializableCounter(0), "", 3);
            throw new AssertionError("blank operation ID accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void topologyEstimateScenarios() {
        TopologyEstimate estimate = topologyEstimate(
                12, 50_000, 1_000_000, 1_024,
                0.90, 0.80,
                0.0001, 1.2, 8.0);
        assert estimate.duplicatedLocalBytes() == 614_400_000L;
        assert estimate.sharedBytes() == 1_024_000_000L;
        assert Math.abs(estimate.expectedReadMillis() - 0.2801) < 0.0000001;
        try {
            topologyEstimate(0, 1, 1, 1, 0.5, 0.5, 0, 1, 1);
            throw new AssertionError("zero instances accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void admissionScenarios() {
        AdmissionDecision scan = tinyLfuAdmissionModel("one-off-scan", 1, "hot-profile", 8);
        assert !scan.admitted() && scan.retainedKey().equals("hot-profile");
        AdmissionDecision popular = tinyLfuAdmissionModel("popular-article", 9, "warm-profile", 8);
        assert popular.admitted() && popular.retainedKey().equals("popular-article");
    }

    private static void twoLevelInvalidationScenarios() {
        TwoLevelCache cache = new TwoLevelCache();
        cache.registerNode("A");
        cache.registerNode("B");
        cache.commitAuthority("article:7", new CacheValue(5, "old"));
        assert cache.read("A", "article:7").version() == 5;
        assert cache.read("B", "article:7").version() == 5;

        cache.loseInvalidationConnection("A");
        cache.commitAuthority("article:7", new CacheValue(6, "new"));
        cache.invalidateAfterCommit("article:7", Set.of("B"));
        assert cache.unsafeLocalForTrace("A", "article:7").version() == 5;
        try {
            cache.read("A", "article:7");
            throw new AssertionError("untrusted local cache served after disconnect");
        } catch (IllegalStateException expected) {}

        cache.reconnectAndFlush("A");
        assert cache.read("A", "article:7").version() == 6;
        assert cache.read("B", "article:7").version() == 6;
    }

    private static void cacheKeyScopeScenarios() {
        assert unsafeArticleKey("7").equals(unsafeArticleKey("7")); // tenant collision
        ScopedCacheKey tenantA = new ScopedCacheKey("tenant-a", "7", "en-US", 3, 11);
        ScopedCacheKey tenantB = new ScopedCacheKey("tenant-b", "7", "en-US", 3, 11);
        ScopedCacheKey newPermissions = new ScopedCacheKey("tenant-a", "7", "en-US", 3, 12);
        assert !tenantA.equals(tenantB);
        assert !tenantA.equals(newPermissions);
        CacheValue personalized = new CacheValue(6, "private-body");
        assert !mayServePersonalizedValue(false, personalized);
        assert mayServePersonalizedValue(true, personalized);
    }

    private static void cacheAsideRaceScenarios() {
        CacheAsideRace unsafe = new CacheAsideRace(new CacheValue(5, "old"));
        PendingFill oldRead = unsafe.beginMiss();
        unsafe.commitAndInvalidate(new CacheValue(6, "new"));
        unsafe.publishUnsafe(oldRead);
        assert unsafe.cacheValue().version() == 5; // stale resurrection

        CacheAsideRace fenced = new CacheAsideRace(new CacheValue(5, "old"));
        PendingFill fencedRead = fenced.beginMiss();
        fenced.commitAndInvalidate(new CacheValue(6, "new"));
        assert !fenced.publishIfCurrent(fencedRead);
        assert fenced.cacheValue() == null;
        assert fenced.authorityValue().version() == 6;
    }

    private static void readThroughScenarios() {
        ReadThroughModel cache = new ReadThroughModel();
        LoadWave first = cache.requestWave("article:7", 20,
                new LoadOutcome(LoadKind.VALUE, new CacheValue(6, "body")), false);
        assert first.loaderCalls() == 1 && first.cached();
        LoadWave hit = cache.requestWave("article:7", 20,
                new LoadOutcome(LoadKind.FAILURE, null), false);
        assert hit.loaderCalls() == 1 && hit.outcome().kind() == LoadKind.VALUE;

        LoadWave failed = cache.requestWave("article:8", 20,
                new LoadOutcome(LoadKind.FAILURE, null), true);
        assert failed.loaderCalls() == 2 && !failed.cached();
        LoadWave recovered = cache.requestWave("article:8", 20,
                new LoadOutcome(LoadKind.VALUE, new CacheValue(1, "recovered")), false);
        assert recovered.loaderCalls() == 3 && recovered.outcome().kind() == LoadKind.VALUE;

        LoadWave absent = cache.requestWave("article:404", 20,
                new LoadOutcome(LoadKind.ABSENT, null), true);
        LoadWave negativeHit = cache.requestWave("article:404", 20,
                new LoadOutcome(LoadKind.FAILURE, null), true);
        assert absent.loaderCalls() == 4 && negativeHit.loaderCalls() == 4;
        assert negativeHit.outcome().kind() == LoadKind.ABSENT;
    }

    private static void writeThroughRepairScenarios() {
        WriteThroughStore store = new WriteThroughStore();
        store.seed("article:7", new CacheValue(5, "old"));
        WriteThroughStatus status = store.write(
                "op-article-6", "article:7", new CacheValue(6, "new"), true);
        assert status == WriteThroughStatus.COMMITTED_REPAIR_QUEUED;
        assert store.repairCount() == 1;
        assert store.cached("article:7").version() == 5;
        assert store.read("article:7").version() == 6; // authority while coherence is degraded
        store.replayRepair("op-article-6");
        store.replayRepair("op-article-6");
        assert store.cached("article:7").version() == 6;
        assert store.read("article:7").version() == 6;
    }

    private static void writeBehindScenarios() {
        WriteBehindStore store = new WriteBehindStore(3);
        WriteIntent v1 = new WriteIntent("op-1", "profile:7", new CacheValue(1, "one"));
        WriteIntent v2 = new WriteIntent("op-2", "profile:7", new CacheValue(2, "two"));
        assert store.submit(v1) == EnqueueStatus.ACKNOWLEDGED;
        assert store.submit(v2) == EnqueueStatus.ACKNOWLEDGED;
        assert store.submit(v1) == EnqueueStatus.ACKNOWLEDGED; // retry before worker observes it
        assert store.visible("profile:7").version() == 2;
        assert store.submit(new WriteIntent(
                "op-3", "profile:7", new CacheValue(3, "three"))) == EnqueueStatus.REJECTED_FULL;
        assert store.replayNext() == ReplayStatus.APPLIED;
        assert store.replayNext() == ReplayStatus.APPLIED;
        assert store.replayNext() == ReplayStatus.DUPLICATE;
        assert store.authoritative("profile:7").version() == 2;

        assert store.submit(new WriteIntent(
                "op-4", "profile:7", new CacheValue(4, "four"))) == EnqueueStatus.ACKNOWLEDGED;
        assert store.replayNext() == ReplayStatus.VERSION_GAP;
        assert store.authoritative("profile:7").version() == 2;
    }

    private static void exactLruScenarios() {
        ExactLruCache cache = new ExactLruCache(3);
        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);
        assert cache.get("A") == 1;
        assert cache.orderLeastToMostRecent().equals(List.of("B", "C", "A"));
        assert cache.put("D", 4).equals("B");
        assert cache.orderLeastToMostRecent().equals(List.of("C", "A", "D"));
        assert cache.contains("A") && !cache.contains("B");
    }

    private static void exactLfuScenarios() {
        ExactLfuModel cache = new ExactLfuModel(3);
        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);
        cache.get("A");
        cache.get("A");
        cache.get("B");
        assert cache.put("D", 4).equals("C");
        assert cache.frequency("A") == 3;
        assert cache.frequency("B") == 2;

        cache.decay();
        assert cache.frequency("A") == 2;
        assert cache.frequency("B") == 1;
        cache.get("D");
        assert cache.put("E", 5).equals("B");
        assert cache.contains("A") && cache.contains("D") && cache.contains("E");
    }

    private static void fifoScenarios() {
        FifoCache cache = new FifoCache(3);
        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);
        assert cache.get("A") == 1;
        assert cache.orderOldestToNewest().equals(List.of("A", "B", "C"));
        assert cache.put("D", 4).equals("A");
        assert cache.orderOldestToNewest().equals(List.of("B", "C", "D"));
        assert !cache.contains("A");
    }

    private static void fullTwoQueueScenarios() {
        FullTwoQueueModel residentHit = new FullTwoQueueModel(2, 2, 3);
        residentHit.request("A");
        residentHit.request("B");
        assert residentHit.request("B").hit() == TwoQHit.A1IN_HIT;
        residentHit.request("C");
        assert residentHit.a1inOrder().equals(List.of("B", "C"));
        assert residentHit.ghostOrder().equals(List.of("A"));

        FullTwoQueueModel cache = new FullTwoQueueModel(2, 2, 3);
        assert cache.request("A").hit() == TwoQHit.NEW;
        cache.request("B");
        assert cache.request("C").evictedResident().equals("A");
        assert cache.request("A").hit() == TwoQHit.GHOST_HIT;
        for (String key : List.of("D", "E", "F", "G")) cache.request(key);
        assert cache.resident("A");
        assert cache.amLeastToMostRecent().equals(List.of("A"));
        assert cache.a1inOrder().equals(List.of("F", "G"));
        assert cache.ghostOrder().equals(List.of("C", "D", "E"));

        ExactLruCache lru = new ExactLruCache(4);
        for (String key : List.of("A", "B", "C")) lru.put(key, 1);
        lru.get("A");
        for (String key : List.of("D", "E", "F", "G")) lru.put(key, 1);
        assert !lru.contains("A"); // the same one-pass scan displaced A under exact LRU
    }

    private static void stampedeArithmeticScenarios() {
        StampedeEstimate burst = naiveExpiryBurst(8_000, 150);
        assert burst.concurrentLoads() == 1_200;
        assert burst.aggregateDatabaseMillis() == 180_000;
        assert naiveExpiryBurst(1, 1).concurrentLoads() == 1; // conservative ceiling
        assert naiveExpiryBurst(0, 150).concurrentLoads() == 0;
    }

    private static void localSingleFlightScenarios() {
        LocalSingleFlight<String, String> flights = new LocalSingleFlight<>();
        Flight<String> leader = flights.begin("article:7");
        Flight<String> waiterA = flights.begin("article:7");
        Flight<String> waiterB = flights.begin("article:7");
        assert leader.leader() && !waiterA.leader() && !waiterB.leader();
        assert leader.future() == waiterA.future() && waiterA.future() == waiterB.future();
        assert flights.inFlight() == 1;

        flights.succeed("article:7", leader, "value-v6");
        assert waiterA.future().join().equals("value-v6");
        assert waiterB.future().join().equals("value-v6");
        assert flights.inFlight() == 0;

        Flight<String> failed = flights.begin("article:8");
        flights.fail("article:8", failed, new IllegalStateException("database timeout"));
        try {
            failed.future().join();
            throw new AssertionError("failed load looked successful");
        } catch (CompletionException expected) {
            assert expected.getCause().getMessage().equals("database timeout");
        }
        assert flights.begin("article:8").leader(); // failure cleanup permits a later retry
    }

    private static void fillLeaseScenarios() {
        FillLeaseRegistry leases = new FillLeaseRegistry();
        assert leases.acquire("loader-A", 0, 5_000);
        assert !leases.acquire("loader-B", 4_000, 5_000);
        assert leases.mayPublish("loader-A", 4_999);

        assert leases.acquire("loader-B", 5_000, 5_000); // A's lease expired
        assert leases.owner().equals("loader-B");
        assert !leases.mayPublish("loader-A", 5_001); // paused former owner is fenced locally
        assert !leases.release("loader-A");             // compare-and-delete protects B
        assert leases.release("loader-B");
    }

    private static void boundedFallbackScenarios() {
        assert afterBoundedWait(true, true, true) == FallbackDecision.VALUE_APPEARED;
        assert afterBoundedWait(false, true, true) == FallbackDecision.SERVE_BOUNDED_STALE;
        assert afterBoundedWait(false, false, true) == FallbackDecision.LOAD_WITH_DATABASE_PERMIT;
        assert afterBoundedWait(false, false, false) == FallbackDecision.REJECT_OVERLOAD;
    }

    private static void negativeCacheScenarios() {
        NegativeCache cache = new NegativeCache(1_000);
        assert cache.lookup("tenant-a", "404", 0, LoadKind.ABSENT) == LoadKind.ABSENT;
        assert cache.lookup("tenant-a", "404", 999, LoadKind.FAILURE) == LoadKind.ABSENT;
        assert cache.authorityCalls() == 1; // repeated identical miss was absorbed

        assert cache.lookup("tenant-b", "404", 999, LoadKind.VALUE) == LoadKind.VALUE;
        assert cache.authorityCalls() == 2; // tenant scope prevents cross-tenant absence
        assert cache.lookup("tenant-a", "404", 1_000, LoadKind.VALUE) == LoadKind.VALUE;
        assert cache.authorityCalls() == 3; // short negative TTL expired

        assert cache.lookup("tenant-a", "timeout", 1_001, LoadKind.FAILURE) == LoadKind.FAILURE;
        assert cache.lookup("tenant-a", "timeout", 1_002, LoadKind.ABSENT) == LoadKind.ABSENT;
        assert cache.authorityCalls() == 5; // failure was never converted into absence
        cache.invalidate("tenant-a", "timeout");
        assert cache.lookup("tenant-a", "timeout", 1_003, LoadKind.VALUE) == LoadKind.VALUE;
    }

    private static void bloomBitScenarios() {
        BloomBitModel filter = new BloomBitModel(16);
        filter.put(1, 4, 11); // inserted object A
        assert filter.mightContain(1, 4, 11);
        assert !filter.mightContain(1, 4, 12); // zero bit proves this hash tuple absent
        assert filter.mightContain(1, 4, 11);  // another object can collide: only “maybe”
    }

    private static void bloomSizingScenarios() {
        BloomPlan plan = bloomPlan(10_000_000, 0.01);
        assert plan.bits() == 95_850_584L;
        assert plan.bytes() == 11_981_323L;
        assert plan.hashFunctions() == 7;
        assert Math.abs(plan.bitsPerItem() - 9.5850584) < 0.0000001;
        long expectedPasses = (long) Math.ceil(100_000 * 0.01);
        assert expectedPasses == 1_000;
    }

    private static void bloomLifecycleScenarios() {
        assert bloomGate(false, true, false) == BloomGateDecision.RETURN_ABSENT;
        assert bloomGate(false, false, false) == BloomGateDecision.CHECK_NEXT_LAYER;
        assert bloomGate(false, true, true) == BloomGateDecision.CHECK_NEXT_LAYER;
        assert bloomGate(true, true, false) == BloomGateDecision.CHECK_NEXT_LAYER;

        BloomBitModel sharedBits = new BloomBitModel(8);
        sharedBits.put(1, 4); // object A
        sharedBits.put(4, 7); // object B shares bit 4
        assert sharedBits.mightContain(4, 7);
        sharedBits.unsafeClearForDemonstration(1, 4); // deleting A by clearing its bits
        assert !sharedBits.mightContain(4, 7); // B became a false negative
    }

    private static void ttlJitterScenarios() {
        assert jitteredTtlMillis(300_000, 0.20, 0.0, true) == 240_000;
        assert jitteredTtlMillis(300_000, 0.20, 0.5, true) == 270_000;
        assert jitteredTtlMillis(300_000, 0.20, 1.0, true) == 300_000;
        assert jitteredTtlMillis(300_000, 0.20, 0.0, false) == 240_000;
        assert jitteredTtlMillis(300_000, 0.20, 0.5, false) == 300_000;
        assert jitteredTtlMillis(300_000, 0.20, 1.0, false) == 360_000;
    }

    private static void coldStartWarmScenarios() {
        WarmPlan plan = coldStartWarmPlan(600_000, 60, 2_500, 0.20);
        assert plan.averagePerSecond() == 10_000;
        assert plan.safePerSecond() == 2_000;
        assert plan.minimumSeconds() == 300;
        assert coldStartWarmPlan(10, 3, 10, 0.0).averagePerSecond() == 4;
    }

    private static void softHardTtlScenarios() {
        assert freshnessDecision(20_000, 30_000, 120_000, true, false)
                == FreshnessDecision.FRESH;
        assert freshnessDecision(40_000, 30_000, 120_000, true, true)
                == FreshnessDecision.STALE_REFRESH_LEADER;
        assert freshnessDecision(40_000, 30_000, 120_000, true, false)
                == FreshnessDecision.STALE_REFRESH_FOLLOWER;
        assert freshnessDecision(120_000, 30_000, 120_000, true, true)
                == FreshnessDecision.EXPIRED;
        assert freshnessDecision(40_000, 30_000, 120_000, false, true)
                == FreshnessDecision.EXPIRED; // e.g. an entitlement revocation decision
    }

    private static void outageProtectionScenarios() {
        RetryAmplification twoLayers = retryAmplification(10_000, 3, 3);
        assert twoLayers.attemptsPerRequest() == 9;
        assert twoLayers.downstreamAttempts() == 90_000;
        assert retryAmplification(10_000, 3, 3, 3, 3, 3).downstreamAttempts() == 2_430_000;

        assert outageDecision(true, false, false, false) == OutageDecision.SERVE_STALE;
        assert outageDecision(false, true, true, true) == OutageDecision.PROBE_ORIGIN;
        assert outageDecision(false, false, true, true) == OutageDecision.REJECT_OVERLOAD;
        assert outageDecision(false, true, false, true) == OutageDecision.REJECT_OVERLOAD;
        assert outageDecision(false, true, true, false) == OutageDecision.REJECT_OVERLOAD;
    }

    private static void poolBoundaryBudgetScenarios() {
        PoolBudget safe = poolBudget(12, 16, 24, 2, 50, 140, 20);
        assert safe.applicationClientSlots() == 480;
        assert safe.pgbouncerServerSlots() == 100;
        assert safe.usablePostgresSlots() == 120;
        assert safe.withinBudget();

        PoolBudget unsafe = poolBudget(12, 16, 24, 3, 50, 140, 20);
        assert unsafe.applicationClientSlots() == 480;
        assert unsafe.pgbouncerServerSlots() == 150;
        assert !unsafe.withinBudget();
    }

    private static void logicalConnectionLeaseScenarios() {
        LogicalConnectionPool pool = new LogicalConnectionPool(1);
        LogicalLease first = pool.checkout();
        pool.execute(first);
        pool.close(first);
        assert pool.physicalSessionCount() == 1;

        LogicalLease second = pool.checkout();
        assert second.physicalSessionId() == first.physicalSessionId();
        assert second.generation() > first.generation();
        try {
            pool.execute(first);
            throw new AssertionError("closed logical connection remained usable");
        } catch (IllegalStateException expected) {}
        pool.execute(second);
        pool.close(second);
    }

    private static void explicitDatabaseRoutingScenarios() {
        assert routeDatabase(DatabaseOperation.WRITE, ReadConsistency.EVENTUAL, 0, 0, true)
                == DatabaseTarget.PRIMARY;
        assert routeDatabase(DatabaseOperation.READ, ReadConsistency.EVENTUAL, 700, 695, true)
                == DatabaseTarget.REPLICA;
        assert routeDatabase(DatabaseOperation.READ, ReadConsistency.READ_YOUR_WRITES, 700, 695, true)
                == DatabaseTarget.PRIMARY;
        assert routeDatabase(DatabaseOperation.READ, ReadConsistency.READ_YOUR_WRITES, 700, 700, true)
                == DatabaseTarget.REPLICA;
        assert routeDatabase(DatabaseOperation.READ, ReadConsistency.EVENTUAL, 0, 0, false)
                == DatabaseTarget.PRIMARY;
    }

    private static void poolingModeAndDeadlineScenarios() {
        assert poolingModeSupports(PgBouncerMode.SESSION, DatabaseFeature.LISTEN, 0);
        assert poolingModeSupports(
                PgBouncerMode.TRANSACTION, DatabaseFeature.MULTI_STATEMENT_TRANSACTION, 0);
        assert !poolingModeSupports(
                PgBouncerMode.TRANSACTION, DatabaseFeature.SESSION_ADVISORY_LOCK, 200);
        assert !poolingModeSupports(
                PgBouncerMode.TRANSACTION, DatabaseFeature.PROTOCOL_PREPARED_STATEMENT, 0);
        assert poolingModeSupports(
                PgBouncerMode.TRANSACTION, DatabaseFeature.PROTOCOL_PREPARED_STATEMENT, 200);
        assert !poolingModeSupports(
                PgBouncerMode.TRANSACTION, DatabaseFeature.SQL_PREPARE, 200);
        assert !poolingModeSupports(
                PgBouncerMode.STATEMENT, DatabaseFeature.MULTI_STATEMENT_TRANSACTION, 200);

        DeadlineBudget normal = deadlineAfterQueues(200, 30, 70);
        assert !normal.exhausted() && normal.queryMillisRemaining() == 100;
        DeadlineBudget hiddenQueue = deadlineAfterQueues(200, 10, 160);
        assert !hiddenQueue.exhausted() && hiddenQueue.queryMillisRemaining() == 30;
        assert deadlineAfterQueues(200, 50, 150).exhausted();
    }

    private static void connectionStartupCostScenarios() {
        ConnectionStartupCost cold = connectionStartupCost(2, 8, 16, 9, 6, 4);
        assert cold.totalMillis() == 45;
        ConnectionStartupCost noDnsOrTls = connectionStartupCost(0, 8, 0, 9, 6, 4);
        assert noDnsOrTls.totalMillis() == 27;
        try {
            connectionStartupCost(0, -1, 0, 0, 0, 0);
            throw new AssertionError("negative startup phase accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void connectionChurnScenarios() {
        ConnectionChurn perRequest = connectionChurn(400, 60);
        assert perRequest.estimatedRetainedSockets() == 24_000;
        assert perRequest.opensPerMinute() == 24_000;

        ConnectionChurn pooledRefresh = connectionChurn(2, 60);
        assert pooledRefresh.estimatedRetainedSockets() == 120;
        assert pooledRefresh.opensPerMinute() == 120;
    }

    private static void poolConcurrencyGateScenarios() {
        ConcurrencyGate bounded = concurrencyGate(120, 24, 60, 24);
        assert bounded.running() == 24;
        assert bounded.waiting() == 60;
        assert bounded.rejected() == 36;
        assert !bounded.databaseOversubscribed();

        ConcurrencyGate oversized = concurrencyGate(120, 120, 0, 24);
        assert oversized.running() == 120;
        assert oversized.waiting() == 0;
        assert oversized.databaseOversubscribed();
    }

    private static void connectionOwnershipScenarios() {
        ConnectionUseGuard lease = new ConnectionUseGuard("request-A");
        lease.execute("request-A");
        try {
            lease.execute("request-B");
            throw new AssertionError("concurrent borrower accepted");
        } catch (IllegalStateException expected) {}
        lease.close("request-A");
        try {
            lease.execute("request-A");
            throw new AssertionError("closed lease remained usable");
        } catch (IllegalStateException expected) {}

        assert connectionReturnAction(true, false) == ConnectionReturnAction.REUSE;
        assert connectionReturnAction(true, true) == ConnectionReturnAction.ROLLBACK_THEN_REUSE;
        assert connectionReturnAction(false, false) == ConnectionReturnAction.EVICT;
    }

    private static void hikariControlScenarios() {
        assert idleRetirementDecision(true, 8, 4, 16, 300_000, 120_000)
                == IdleRetirementDecision.KEEP_BORROWED;
        assert idleRetirementDecision(false, 4, 4, 16, 300_000, 120_000)
                == IdleRetirementDecision.KEEP_MINIMUM_IDLE;
        assert idleRetirementDecision(false, 8, 4, 16, 60_000, 120_000)
                == IdleRetirementDecision.KEEP_YOUNG_IDLE;
        assert idleRetirementDecision(false, 8, 4, 16, 120_000, 120_000)
                == IdleRetirementDecision.ELIGIBLE_SURPLUS_IDLE;
        assert idleRetirementDecision(false, 16, 16, 16, 300_000, 120_000)
                == IdleRetirementDecision.KEEP_MINIMUM_IDLE;
    }

    private static void timeoutStackScenarios() {
        TimeoutTrace successful = timeoutTrace(2_000, 180, 90, 130, 900);
        assert successful.exhaustedAt() == TimeoutBoundary.NONE;
        assert successful.spentMillis() == 1_300;
        assert successful.remainingMillis() == 700;

        TimeoutTrace poolExhausted = timeoutTrace(750, 750, 0, 0, 0);
        assert poolExhausted.exhaustedAt() == TimeoutBoundary.POOL_ACQUISITION;
        TimeoutTrace connectExhausted = timeoutTrace(750, 300, 450, 0, 0);
        assert connectExhausted.exhaustedAt() == TimeoutBoundary.CONNECT;
        TimeoutTrace queryExhausted = timeoutTrace(750, 100, 50, 50, 600);
        assert queryExhausted.exhaustedAt() == TimeoutBoundary.QUERY;
    }

    private static void workloadPoolSizingScenarios() {
        WorkloadPoolPlan oversized = workloadPoolPlan(1_000, 12, 20, 16, 280);
        assert oversized.averageGlobalInUseCeiling() == 12;
        assert Math.abs(oversized.averagePerInstanceInUse() - 0.6) < 0.0000001;
        assert oversized.configuredGlobalMaximum() == 320;
        assert !oversized.configuredMaximumWithinBudget();

        WorkloadPoolPlan bounded = workloadPoolPlan(1_000, 12, 20, 12, 280);
        assert bounded.configuredGlobalMaximum() == 240;
        assert bounded.configuredMaximumWithinBudget();
        assert workloadPoolPlan(1, 1, 3, 2, 10).averageGlobalInUseCeiling() == 1;
    }

    private static void poolTelemetryScenarios() {
        assert poolSignal(16, 16, 20, 700, 40, 30, 25, false)
                == PoolSignal.ACQUISITION_QUEUE;
        assert poolSignal(16, 16, 20, 700, 250, 220, 25, true)
                == PoolSignal.DATABASE_SATURATED;
        assert poolSignal(4, 16, 20, 900, 50, 40, 1_200, false)
                == PoolSignal.SLOW_CONNECTION_CREATION;
        assert poolSignal(6, 16, 0, 4, 500, 80, 30, false)
                == PoolSignal.LONG_HOLD_OUTSIDE_SQL;
        assert poolSignal(6, 16, 0, 4, 80, 60, 30, false)
                == PoolSignal.HEALTHY;
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
        columnSelectionScenarios();
        vectorBatchScenarios();
        granulePruningScenarios();
        projectionScenarios();
        statementSnapshotScenarios();
        writeSkewScenarios();
        lostUpdateScenarios();
        serializationRetryScenarios();
        topologyEstimateScenarios();
        admissionScenarios();
        twoLevelInvalidationScenarios();
        cacheKeyScopeScenarios();
        cacheAsideRaceScenarios();
        readThroughScenarios();
        writeThroughRepairScenarios();
        writeBehindScenarios();
        exactLruScenarios();
        exactLfuScenarios();
        fifoScenarios();
        fullTwoQueueScenarios();
        stampedeArithmeticScenarios();
        localSingleFlightScenarios();
        fillLeaseScenarios();
        boundedFallbackScenarios();
        negativeCacheScenarios();
        bloomBitScenarios();
        bloomSizingScenarios();
        bloomLifecycleScenarios();
        ttlJitterScenarios();
        coldStartWarmScenarios();
        softHardTtlScenarios();
        outageProtectionScenarios();
        poolBoundaryBudgetScenarios();
        logicalConnectionLeaseScenarios();
        explicitDatabaseRoutingScenarios();
        poolingModeAndDeadlineScenarios();
        connectionStartupCostScenarios();
        connectionChurnScenarios();
        poolConcurrencyGateScenarios();
        connectionOwnershipScenarios();
        hikariControlScenarios();
        timeoutStackScenarios();
        workloadPoolSizingScenarios();
        poolTelemetryScenarios();
        System.out.println("PASS 56 scenario groups: atomicity, idempotency, replica routing, convergence, capacity, composite range, secondary bytes, leaf split, LSM recovery, snapshot reads, tombstones, amplification, column selection, vector batches, granule pruning, CDC projection, statement snapshots, write skew, lost updates, serialization retry, topology estimate, admission, two-level invalidation, cache-key scope, cache-aside race, read-through outcomes, write-through repair, write-behind replay, exact LRU, LFU with aging, FIFO, full 2Q, stampede arithmetic, local single-flight, fill lease ownership, bounded fallback, negative-cache semantics, Bloom bit membership, Bloom sizing, Bloom lifecycle safety, TTL jitter, cold-start warming, soft/hard TTL, outage protection, pool-boundary budget, logical connection lease, explicit database routing, pooling-mode deadline, connection startup cost, connection churn, pool concurrency gate, connection ownership, Hikari controls, timeout stack, workload pool sizing, pool telemetry");
    }
}

