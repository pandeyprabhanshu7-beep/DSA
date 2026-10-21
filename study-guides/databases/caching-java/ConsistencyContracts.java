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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/** Executable teaching models for guide Sections 1-42. Run with assertions enabled. */
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

    record TinyBPlusTree(List<Integer> separators, List<List<Integer>> leaves) {
        TinyBPlusTree {
            separators = List.copyOf(separators);
            List<List<Integer>> copied = new ArrayList<>();
            for (List<Integer> leaf : leaves) copied.add(List.copyOf(leaf));
            leaves = List.copyOf(copied);
            if (leaves.isEmpty() || separators.size() != leaves.size() - 1) {
                throw new IllegalArgumentException("one separator per right child is required");
            }
            Integer previous = null;
            for (int leafIndex = 0; leafIndex < leaves.size(); leafIndex++) {
                List<Integer> leaf = leaves.get(leafIndex);
                if (leaf.isEmpty()) throw new IllegalArgumentException("empty leaf");
                for (int key : leaf) {
                    if (previous != null && key <= previous) {
                        throw new IllegalArgumentException("keys must be globally distinct and sorted");
                    }
                    previous = key;
                }
                if (leafIndex > 0 && !separators.get(leafIndex - 1).equals(leaf.get(0))) {
                    throw new IllegalArgumentException("separator must equal right-child minimum");
                }
            }
        }
    }

    static TinyBPlusTree insertTinyBPlus(TinyBPlusTree tree, int key, int capacity) {
        if (capacity < 2) throw new IllegalArgumentException("capacity must be at least two");
        int leafIndex = 0;
        while (leafIndex < tree.separators().size()
                && key >= tree.separators().get(leafIndex)) {
            leafIndex++;
        }
        int[] current = tree.leaves().get(leafIndex).stream().mapToInt(Integer::intValue).toArray();
        LeafInsert inserted = insertIntoLeaf(current, key, capacity);
        List<List<Integer>> leaves = new ArrayList<>(tree.leaves());
        leaves.set(leafIndex, inserted.left());
        if (inserted.split()) leaves.add(leafIndex + 1, inserted.right());
        List<Integer> separators = new ArrayList<>();
        for (int i = 1; i < leaves.size(); i++) separators.add(leaves.get(i).get(0));
        return new TinyBPlusTree(separators, leaves);
    }

    record LinkedRangeScan(List<Integer> keys, int leavesVisited, Integer firstRejectedKey) {}

    static LinkedRangeScan linkedRangeScan(TinyBPlusTree tree, int lowInclusive, int highInclusive) {
        if (lowInclusive > highInclusive) throw new IllegalArgumentException("inverted range");
        int leafIndex = 0;
        while (leafIndex < tree.separators().size()
                && lowInclusive >= tree.separators().get(leafIndex)) {
            leafIndex++;
        }
        List<Integer> keys = new ArrayList<>();
        int visited = 0;
        for (int i = leafIndex; i < tree.leaves().size(); i++) {
            visited++;
            for (int key : tree.leaves().get(i)) {
                if (key < lowInclusive) continue;
                if (key > highInclusive) {
                    return new LinkedRangeScan(List.copyOf(keys), visited, key);
                }
                keys.add(key);
            }
        }
        return new LinkedRangeScan(List.copyOf(keys), visited, null);
    }

    record SecondaryLookupPlan(List<Integer> primaryKeys, int clusteredLookups, boolean covering) {}

    static SecondaryLookupPlan secondaryLookupPlan(List<Integer> matchingPrimaryKeys, boolean covering) {
        List<Integer> keys = List.copyOf(matchingPrimaryKeys);
        return new SecondaryLookupPlan(keys, covering ? 0 : keys.size(), covering);
    }

    record IndexOnlyVisibilityPlan(boolean physicallyEligible, int heapFetches) {}

    static IndexOnlyVisibilityPlan indexOnlyVisibilityPlan(
            boolean allReferencedColumnsStored, boolean... candidateHeapPagesAllVisible) {
        if (!allReferencedColumnsStored) {
            return new IndexOnlyVisibilityPlan(false, candidateHeapPagesAllVisible.length);
        }
        int heapFetches = 0;
        for (boolean allVisible : candidateHeapPagesAllVisible) {
            if (!allVisible) heapFetches++;
        }
        return new IndexOnlyVisibilityPlan(true, heapFetches);
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

    record LsmCandidate(String place, LsmVersion version) {
        LsmCandidate {
            if (place == null || place.isBlank()) throw new IllegalArgumentException("place");
            Objects.requireNonNull(version, "version");
        }
    }

    record LsmVisibilityTrace(LsmRead read, List<String> placesChecked) {}

    static LsmVisibilityTrace visibleAcrossPlaces(
            List<LsmCandidate> candidates, long snapshotSequence) {
        List<LsmVersion> versions = candidates.stream().map(LsmCandidate::version).toList();
        List<String> places = candidates.stream().map(LsmCandidate::place).toList();
        return new LsmVisibilityTrace(
                newestVisible(versions, snapshotSequence, true), List.copyOf(places));
    }

    record LsmCompactionPlan(
            List<LsmVersion> retained, boolean completeHistoryCoverage, boolean tombstoneDropped) {}

    static LsmCompactionPlan compactKeyHistory(
            List<LsmVersion> newestFirst,
            long oldestSnapshotSequence,
            boolean completeHistoryCoverage) {
        if (newestFirst.isEmpty() || oldestSnapshotSequence < 0) {
            throw new IllegalArgumentException("history and snapshot sequence required");
        }
        long previous = Long.MAX_VALUE;
        for (LsmVersion version : newestFirst) {
            if (version.sequence() >= previous) {
                throw new IllegalArgumentException("history must be newest first");
            }
            previous = version.sequence();
        }

        LsmVersion newest = newestFirst.get(0);
        if (newest.tombstone()
                && canDropTombstone(
                        newest.sequence(), oldestSnapshotSequence, completeHistoryCoverage)) {
            return new LsmCompactionPlan(List.of(), true, true);
        }

        List<LsmVersion> retained = new ArrayList<>();
        boolean keptFloorForOldestSnapshot = false;
        for (LsmVersion version : newestFirst) {
            if (version.sequence() >= oldestSnapshotSequence) {
                retained.add(version);
            } else if (!keptFloorForOldestSnapshot) {
                retained.add(version);
                keptFloorForOldestSnapshot = true;
            }
        }
        return new LsmCompactionPlan(
                List.copyOf(retained), completeHistoryCoverage, false);
    }

    record BloomProbeBudget(
            int candidateFiles,
            double falsePositiveRate,
            double expectedDataBlockProbes,
            int observedDataBlockProbes) {}

    static BloomProbeBudget bloomProbeBudget(
            int candidateFiles, double falsePositiveRate, boolean... filterMayContain) {
        if (candidateFiles < 0 || falsePositiveRate < 0 || falsePositiveRate > 1
                || filterMayContain.length != candidateFiles) {
            throw new IllegalArgumentException("invalid candidate/filter model");
        }
        int observed = 0;
        for (boolean mayContain : filterMayContain) if (mayContain) observed++;
        return new BloomProbeBudget(
                candidateFiles,
                falsePositiveRate,
                candidateFiles * falsePositiveRate,
                observed);
    }

    record CompactionDebtBudget(
            double ingestMbPerSecond,
            double outputBytesPerInputByte,
            double compactionDemandMbPerSecond,
            double sustainableDeviceMbPerSecond,
            double debtMbPerSecond,
            double debtAfterWindowMb,
            boolean stallsExpected) {}

    static CompactionDebtBudget compactionDebtBudget(
            double ingestMbPerSecond,
            double outputBytesPerInputByte,
            double sustainableDeviceMbPerSecond,
            long windowSeconds) {
        if (ingestMbPerSecond < 0 || outputBytesPerInputByte < 0
                || sustainableDeviceMbPerSecond < 0 || windowSeconds < 0) {
            throw new IllegalArgumentException("negative compaction budget input");
        }
        double demand = ingestMbPerSecond * outputBytesPerInputByte;
        double debtRate = Math.max(0, demand - sustainableDeviceMbPerSecond);
        return new CompactionDebtBudget(
                ingestMbPerSecond,
                outputBytesPerInputByte,
                demand,
                sustainableDeviceMbPerSecond,
                debtRate,
                debtRate * windowSeconds,
                debtRate > 0);
    }

    record EndpointOrder(
            int tenantId, String status, long createdAt, int id, long totalCents) {
        EndpointOrder {
            if (tenantId <= 0 || status == null || status.isBlank()
                    || createdAt <= 0 || id <= 0 || totalCents < 0) {
                throw new IllegalArgumentException("invalid endpoint order");
            }
        }
    }

    record EndpointCursor(int tenantId, String status, long lastCreatedAt, int lastId) {
        EndpointCursor {
            if (tenantId <= 0 || status == null || status.isBlank()
                    || lastCreatedAt <= 0 || lastId <= 0) {
                throw new IllegalArgumentException("invalid endpoint cursor");
            }
        }
    }

    record EndpointPage(List<EndpointOrder> rows, int matchingEntriesExamined) {}

    static EndpointPage endpointPage(
            List<EndpointOrder> compositeIndexOrder,
            int tenantId,
            String status,
            EndpointCursor cursor,
            int limit) {
        if (tenantId <= 0 || status == null || status.isBlank() || limit < 0) {
            throw new IllegalArgumentException("invalid endpoint request");
        }
        if (cursor != null
                && (cursor.tenantId() != tenantId || !cursor.status().equals(status))) {
            throw new IllegalArgumentException("cursor scope does not match request");
        }
        List<EndpointOrder> rows = new ArrayList<>();
        int examined = 0;
        for (EndpointOrder order : compositeIndexOrder) {
            if (order.tenantId() != tenantId || !order.status().equals(status)) continue;
            examined++;
            if (cursor != null) {
                boolean beforeCursor = order.createdAt() < cursor.lastCreatedAt()
                        || (order.createdAt() == cursor.lastCreatedAt()
                            && order.id() < cursor.lastId());
                if (!beforeCursor) continue;
            }
            if (rows.size() < limit) rows.add(order);
            if (rows.size() == limit) break;
        }
        return new EndpointPage(List.copyOf(rows), examined);
    }

    enum EndpointPlanKind {
        ORDERED_COMPOSITE_INDEX,
        BITMAP_AND_THEN_SORT,
        BROAD_SCAN_THEN_SORT
    }

    record EndpointPlanChoice(
            EndpointPlanKind kind,
            int candidateRows,
            int rowsSorted,
            int rowsReturned,
            boolean preservesRequiredOrder) {}

    static EndpointPlanChoice endpointPlanChoice(
            boolean alignedCompositeIndex,
            boolean separateTenantAndStatusIndexes,
            int matchingRows,
            int limit) {
        if (matchingRows < 0 || limit < 0) throw new IllegalArgumentException("row counts");
        int returned = Math.min(matchingRows, limit);
        if (alignedCompositeIndex) {
            return new EndpointPlanChoice(
                    EndpointPlanKind.ORDERED_COMPOSITE_INDEX,
                    returned, 0, returned, true);
        }
        if (separateTenantAndStatusIndexes) {
            return new EndpointPlanChoice(
                    EndpointPlanKind.BITMAP_AND_THEN_SORT,
                    matchingRows, matchingRows, returned, false);
        }
        return new EndpointPlanChoice(
                EndpointPlanKind.BROAD_SCAN_THEN_SORT,
                matchingRows, matchingRows, returned, false);
    }

    record EndpointPlanEvidence(
            int estimatedRows,
            int actualRows,
            int rowsRemovedByFilter,
            boolean sortSpilled,
            int heapFetches,
            int databaseMillis,
            int endpointMillis) {}

    static List<String> diagnoseEndpointPlan(EndpointPlanEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.estimatedRows() < 0 || evidence.actualRows() < 0
                || evidence.rowsRemovedByFilter() < 0 || evidence.heapFetches() < 0
                || evidence.databaseMillis() < 0 || evidence.endpointMillis() < 0) {
            throw new IllegalArgumentException("negative plan evidence");
        }
        List<String> findings = new ArrayList<>();
        if (evidence.actualRows() > Math.max(10, evidence.estimatedRows() * 10L)) {
            findings.add("row-estimate skew: inspect statistics and tenant/status correlation");
        }
        if (evidence.rowsRemovedByFilter() > evidence.actualRows()) {
            findings.add("broad access path: inspect composite or partial index suitability");
        }
        if (evidence.sortSpilled()) {
            findings.add("disk sort: test an order-aligned index before raising memory");
        }
        if (evidence.heapFetches() > 0) {
            findings.add("heap fetches: covering eligibility does not prove all-visible pages");
        }
        if (evidence.endpointMillis() > evidence.databaseMillis() * 3L) {
            findings.add("endpoint gap: inspect pool wait, extra calls, serialization and network");
        }
        return List.copyOf(findings);
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

    enum LabIsolation { READ_COMMITTED, REPEATABLE_READ }

    record PriceReadTrace(
            int firstRead,
            int secondRead,
            long firstSnapshot,
            long secondSnapshot,
            boolean nonRepeatableRead) {}

    static PriceReadTrace twoSessionPriceTrace(
            LabIsolation isolation, int initialPrice, int committedPrice) {
        Objects.requireNonNull(isolation, "isolation");
        if (initialPrice < 0 || committedPrice < 0) {
            throw new IllegalArgumentException("prices must be non-negative");
        }
        long firstSnapshot = 1;
        long secondSnapshot = isolation == LabIsolation.READ_COMMITTED ? 2 : 1;
        int secondRead = secondSnapshot == 2 ? committedPrice : initialPrice;
        return new PriceReadTrace(
                initialPrice,
                secondRead,
                firstSnapshot,
                secondSnapshot,
                initialPrice != secondRead);
    }

    record GuardedRosterOutcome(
            boolean aliceOnCall,
            boolean bobOnCall,
            List<String> acceptedOffCall,
            int guardAcquisitions) {
        int onCallCount() { return (aliceOnCall ? 1 : 0) + (bobOnCall ? 1 : 0); }
    }

    static GuardedRosterOutcome guardedOffCall(List<String> requestedEngineers) {
        Objects.requireNonNull(requestedEngineers, "requestedEngineers");
        boolean alice = true;
        boolean bob = true;
        List<String> accepted = new ArrayList<>();
        int acquisitions = 0;
        for (String engineer : requestedEngineers) {
            if (!engineer.equals("A") && !engineer.equals("B")) {
                throw new IllegalArgumentException("unknown engineer: " + engineer);
            }
            acquisitions++; // acquire the shift guard before reading the current roster
            int currentOnCall = (alice ? 1 : 0) + (bob ? 1 : 0);
            if (currentOnCall <= 1) continue;
            if (engineer.equals("A") && alice) {
                alice = false;
                accepted.add(engineer);
            } else if (engineer.equals("B") && bob) {
                bob = false;
                accepted.add(engineer);
            }
        }
        return new GuardedRosterOutcome(
                alice, bob, List.copyOf(accepted), acquisitions);
    }

    record TransferLockPlan(List<Integer> accountOrder) {}

    static TransferLockPlan canonicalTransferLocks(int firstAccount, int secondAccount) {
        if (firstAccount == secondAccount) {
            throw new IllegalArgumentException("a transfer requires two accounts");
        }
        return new TransferLockPlan(firstAccount < secondAccount
                ? List.of(firstAccount, secondAccount)
                : List.of(secondAccount, firstAccount));
    }

    static boolean oppositeOrderCreatesWaitCycle(
            List<Integer> transactionA, List<Integer> transactionB) {
        Objects.requireNonNull(transactionA, "transactionA");
        Objects.requireNonNull(transactionB, "transactionB");
        if (transactionA.size() != 2 || transactionB.size() != 2) {
            throw new IllegalArgumentException("two-lock traces required");
        }
        return !transactionA.get(0).equals(transactionA.get(1))
                && transactionA.get(0).equals(transactionB.get(1))
                && transactionA.get(1).equals(transactionB.get(0));
    }

    enum TransactionRetryAction { RETRY_WHOLE_TRANSACTION, STOP }

    static TransactionRetryAction retryAfterRollback(
            String sqlState, boolean rolledBack, int completedAttempts, int maxAttempts) {
        if (completedAttempts < 0 || maxAttempts <= 0) {
            throw new IllegalArgumentException("positive retry budget required");
        }
        boolean transientRollback = sqlState.equals("40001") || sqlState.equals("40P01");
        return transientRollback && rolledBack && completedAttempts < maxAttempts
                ? TransactionRetryAction.RETRY_WHOLE_TRANSACTION
                : TransactionRetryAction.STOP;
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

    enum ScanAdmission { SHARED_LRU, BYPASS_ONE_PASS }
    record ScanPollutionResult(List<String> postScanResidents, int hotMissesAfterScan) {}

    /**
     * Models a nightly one-pass search-index rebuild sharing a three-entry article cache.
     * Bypass means the batch still reads the source rows, but does not admit them here.
     */
    static ScanPollutionResult searchRebuildScan(ScanAdmission admission) {
        ExactLruCache cache = new ExactLruCache(3);
        for (String key : List.of("H1", "H2", "H3")) cache.put(key, 1);
        for (String scanKey : List.of("S1", "S2", "S3", "S4")) {
            if (admission == ScanAdmission.SHARED_LRU) cache.put(scanKey, 1);
        }
        List<String> postScan = cache.orderLeastToMostRecent();

        int misses = 0;
        for (String key : List.of("H1", "H2", "H3")) {
            if (cache.get(key) == null) {
                misses++;
                cache.put(key, 1);
            }
        }
        return new ScanPollutionResult(postScan, misses);
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

    enum JdbcDriverKind { POSTGRESQL, MYSQL, UNSUPPORTED }

    static JdbcDriverKind jdbcDriverForUrl(String url) {
        Objects.requireNonNull(url, "url");
        if (url.startsWith("jdbc:postgresql:")) return JdbcDriverKind.POSTGRESQL;
        if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mysql+srv:")) {
            return JdbcDriverKind.MYSQL;
        }
        return JdbcDriverKind.UNSUPPORTED;
    }

    enum DataSourceKind { BASIC_VENDOR, HIKARI_POOL }
    enum AcquisitionPath { FRESH_PHYSICAL, BORROW_IDLE, GROW_PHYSICAL, WAIT_OR_TIMEOUT }

    static AcquisitionPath acquisitionPath(
            DataSourceKind kind, int idleConnections, int totalConnections, int maximumPoolSize) {
        Objects.requireNonNull(kind, "kind");
        if (idleConnections < 0 || totalConnections < 0 || idleConnections > totalConnections
                || maximumPoolSize <= 0 || totalConnections > maximumPoolSize) {
            throw new IllegalArgumentException("invalid acquisition state");
        }
        if (kind == DataSourceKind.BASIC_VENDOR) return AcquisitionPath.FRESH_PHYSICAL;
        if (idleConnections > 0) return AcquisitionPath.BORROW_IDLE;
        if (totalConnections < maximumPoolSize) return AcquisitionPath.GROW_PHYSICAL;
        return AcquisitionPath.WAIT_OR_TIMEOUT;
    }

    record JdbcLayerStack(
            JdbcDriverKind driver,
            String jdbcApi,
            String wireProtocol,
            String defaultTransport,
            boolean httpByDefault) {}

    static JdbcLayerStack jdbcLayerStack(String url) {
        JdbcDriverKind driver = jdbcDriverForUrl(url);
        return switch (driver) {
            case POSTGRESQL -> new JdbcLayerStack(
                    driver, "JDBC", "PostgreSQL frontend/backend v3", "TCP", false);
            case MYSQL -> new JdbcLayerStack(
                    driver, "JDBC", "MySQL classic protocol", "TCP", false);
            case UNSUPPORTED -> throw new IllegalArgumentException("unsupported JDBC URL");
        };
    }

    enum PgTransport { TCP, UNIX_DOMAIN_SOCKET }
    enum PgEncryption { PLAINTEXT, SSL_REQUEST_TLS, DIRECT_TLS, GSSAPI }
    enum PgAuthentication { SCRAM_SHA_256, CLIENT_CERTIFICATE }

    record PgHandshakePlan(
            List<String> phases,
            boolean sendsSslRequest,
            boolean performsTlsHandshake,
            boolean performsScramExchange) {}

    static PgHandshakePlan pgHandshakePlan(
            PgTransport transport,
            PgEncryption encryption,
            PgAuthentication authentication,
            boolean tlsResumption) {
        Objects.requireNonNull(transport, "transport");
        Objects.requireNonNull(encryption, "encryption");
        Objects.requireNonNull(authentication, "authentication");
        if (transport == PgTransport.UNIX_DOMAIN_SOCKET
                && (encryption == PgEncryption.SSL_REQUEST_TLS
                        || encryption == PgEncryption.DIRECT_TLS)) {
            throw new IllegalArgumentException("TLS-over-TCP teaching path needs TCP");
        }
        List<String> phases = new ArrayList<>();
        phases.add(transport == PgTransport.TCP ? "TCP connect" : "Unix socket connect");
        boolean sslRequest = encryption == PgEncryption.SSL_REQUEST_TLS;
        boolean tls = sslRequest || encryption == PgEncryption.DIRECT_TLS;
        if (sslRequest) phases.add("SSLRequest and one-byte response");
        if (encryption == PgEncryption.GSSAPI) phases.add("GSSAPI negotiation");
        if (tls) phases.add(tlsResumption ? "resumed TLS handshake" : "full TLS handshake");
        phases.add("StartupMessage");
        boolean scram = authentication == PgAuthentication.SCRAM_SHA_256;
        phases.add(scram ? "SCRAM-SHA-256 exchange" : "client-certificate authentication");
        phases.add("ParameterStatus, BackendKeyData, ReadyForQuery");
        return new PgHandshakePlan(List.copyOf(phases), sslRequest, tls, scram);
    }

    enum PgStartupState {
        SOCKET_CREATED,
        TCP_CONNECTED,
        TLS_NEGOTIATED,
        STARTUP_SENT,
        AUTHENTICATED,
        READY_FOR_QUERY,
        POOL_USABLE
    }

    static PgStartupState advancePgStartup(PgStartupState current, PgStartupState next) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(next, "next");
        PgStartupState expected = switch (current) {
            case SOCKET_CREATED -> PgStartupState.TCP_CONNECTED;
            case TCP_CONNECTED -> PgStartupState.TLS_NEGOTIATED;
            case TLS_NEGOTIATED -> PgStartupState.STARTUP_SENT;
            case STARTUP_SENT -> PgStartupState.AUTHENTICATED;
            case AUTHENTICATED -> PgStartupState.READY_FOR_QUERY;
            case READY_FOR_QUERY -> PgStartupState.POOL_USABLE;
            case POOL_USABLE -> throw new IllegalStateException("startup is already complete");
        };
        if (next != expected) {
            throw new IllegalStateException("expected " + expected + " after " + current);
        }
        return next;
    }

    record PgFrame(char type, int lengthField, int totalBytes) {}

    static byte[] typedPgMessage(char type, byte... payload) {
        if (type > 0x7f) throw new IllegalArgumentException("ASCII message type required");
        int lengthField = Math.addExact(4, payload.length);
        byte[] encoded = new byte[Math.addExact(1, lengthField)];
        encoded[0] = (byte) type;
        encoded[1] = (byte) (lengthField >>> 24);
        encoded[2] = (byte) (lengthField >>> 16);
        encoded[3] = (byte) (lengthField >>> 8);
        encoded[4] = (byte) lengthField;
        System.arraycopy(payload, 0, encoded, 5, payload.length);
        return encoded;
    }

    static List<PgFrame> decodeTypedPgMessages(List<byte[]> socketReads) {
        Objects.requireNonNull(socketReads, "socketReads");
        int total = 0;
        for (byte[] read : socketReads) {
            total = Math.addExact(total, Objects.requireNonNull(read, "socket read").length);
        }
        byte[] stream = new byte[total];
        int copied = 0;
        for (byte[] read : socketReads) {
            System.arraycopy(read, 0, stream, copied, read.length);
            copied += read.length;
        }

        List<PgFrame> frames = new ArrayList<>();
        for (int offset = 0; offset < stream.length;) {
            if (stream.length - offset < 5) throw new IllegalArgumentException("partial header");
            int length = ((stream[offset + 1] & 0xff) << 24)
                    | ((stream[offset + 2] & 0xff) << 16)
                    | ((stream[offset + 3] & 0xff) << 8)
                    | (stream[offset + 4] & 0xff);
            if (length < 4) throw new IllegalArgumentException("invalid length field");
            int messageBytes = Math.addExact(1, length);
            if (stream.length - offset < messageBytes) {
                throw new IllegalArgumentException("partial message");
            }
            frames.add(new PgFrame((char) stream[offset], length, messageBytes));
            offset += messageBytes;
        }
        return List.copyOf(frames);
    }

    record StartupAssurance(
            boolean encrypted,
            boolean serverIdentityVerified,
            boolean databaseUserAuthenticated,
            boolean commandReady) {}

    static StartupAssurance startupAssurance(
            boolean tls,
            boolean chainTrusted,
            boolean hostnameMatched,
            boolean scramServerSignatureVerified,
            boolean authenticationOk,
            boolean readyForQuery) {
        boolean identity = tls && chainTrusted && hostnameMatched;
        boolean databaseUser = scramServerSignatureVerified && authenticationOk;
        if (readyForQuery && !databaseUser) {
            throw new IllegalArgumentException("ReadyForQuery requires completed authentication");
        }
        return new StartupAssurance(tls, identity, databaseUser, readyForQuery);
    }

    record ConnectionTracePlan(
            AcquisitionPath acquisitionPath,
            boolean physicalHandshake,
            List<String> correlationEvents) {}

    static ConnectionTracePlan connectionTracePlan(
            int idleConnections, int totalConnections, int maximumPoolSize) {
        AcquisitionPath path = acquisitionPath(
                DataSourceKind.HIKARI_POOL,
                idleConnections,
                totalConnections,
                maximumPoolSize);
        List<String> events = switch (path) {
            case BORROW_IDLE -> List.of("checkout", "lease returned");
            case GROW_PHYSICAL -> List.of(
                    "checkout", "physical create", "TCP/TLS/startup", "ReadyForQuery",
                    "pool register", "lease returned");
            case WAIT_OR_TIMEOUT -> List.of("checkout", "wait", "lease or timeout");
            case FRESH_PHYSICAL -> throw new IllegalStateException("pool path expected");
        };
        return new ConnectionTracePlan(
                path, path == AcquisitionPath.GROW_PHYSICAL, events);
    }

    enum PgIoOperation { CONNECT, READ, CANCEL }
    enum AddressOutcome { CONNECTED, TIMEOUT, REFUSED }
    enum ConnectTerminal { CONNECTED, DNS_EMPTY, ADDRESSES_EXHAUSTED }

    record PgTimeouts(int connectSeconds, int socketReadSeconds, int cancelSeconds) {
        PgTimeouts {
            if (connectSeconds < 0 || socketReadSeconds < 0 || cancelSeconds < 0) {
                throw new IllegalArgumentException("timeouts must be non-negative");
            }
        }

        int forOperation(PgIoOperation operation) {
            return switch (Objects.requireNonNull(operation, "operation")) {
                case CONNECT -> connectSeconds;
                case READ -> socketReadSeconds;
                case CANCEL -> cancelSeconds;
            };
        }
    }

    record AddressAttemptTrace(
            ConnectTerminal terminal,
            String selectedAddress,
            int attemptedAddresses,
            List<String> events) {}

    static AddressAttemptTrace attemptAddresses(
            List<String> addresses, List<AddressOutcome> outcomes) {
        Objects.requireNonNull(addresses, "addresses");
        Objects.requireNonNull(outcomes, "outcomes");
        if (addresses.size() != outcomes.size()) {
            throw new IllegalArgumentException("one outcome is required per address");
        }
        if (addresses.isEmpty()) {
            return new AddressAttemptTrace(
                    ConnectTerminal.DNS_EMPTY, null, 0, List.of("DNS returned no address"));
        }
        List<String> events = new ArrayList<>();
        for (int i = 0; i < addresses.size(); i++) {
            String address = Objects.requireNonNull(addresses.get(i), "address");
            AddressOutcome outcome = Objects.requireNonNull(outcomes.get(i), "outcome");
            events.add(address + ":" + outcome);
            if (outcome == AddressOutcome.CONNECTED) {
                return new AddressAttemptTrace(
                        ConnectTerminal.CONNECTED, address, i + 1, List.copyOf(events));
            }
        }
        return new AddressAttemptTrace(
                ConnectTerminal.ADDRESSES_EXHAUSTED, null, addresses.size(), List.copyOf(events));
    }

    enum PgSslMode { REQUIRE, VERIFY_CA, VERIFY_FULL }
    enum TlsAdmission { REJECT, ENCRYPTED_UNVERIFIED, TRUSTED_CA, VERIFIED_ENDPOINT }

    record TlsEvidence(
            boolean tlsEstablished,
            boolean chainTrusted,
            boolean hostnameMatched,
            boolean resumed,
            boolean clientCertificatePresented) {}

    static TlsAdmission tlsAdmission(PgSslMode mode, TlsEvidence evidence) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(evidence, "evidence");
        if (!evidence.tlsEstablished()) return TlsAdmission.REJECT;
        return switch (mode) {
            case REQUIRE -> TlsAdmission.ENCRYPTED_UNVERIFIED;
            case VERIFY_CA -> evidence.chainTrusted()
                    ? TlsAdmission.TRUSTED_CA : TlsAdmission.REJECT;
            case VERIFY_FULL -> evidence.chainTrusted() && evidence.hostnameMatched()
                    ? TlsAdmission.VERIFIED_ENDPOINT : TlsAdmission.REJECT;
        };
    }

    enum ChannelBindingPolicy { DISABLE, PREFER, REQUIRE }
    enum ScramMechanism { SCRAM_SHA_256, SCRAM_SHA_256_PLUS, REJECT }

    static ScramMechanism selectScramMechanism(
            Set<String> advertised,
            ChannelBindingPolicy policy,
            boolean verifiedTlsChannel) {
        Objects.requireNonNull(advertised, "advertised");
        Objects.requireNonNull(policy, "policy");
        boolean plain = advertised.contains("SCRAM-SHA-256");
        boolean plus = advertised.contains("SCRAM-SHA-256-PLUS") && verifiedTlsChannel;
        return switch (policy) {
            case REQUIRE -> plus ? ScramMechanism.SCRAM_SHA_256_PLUS : ScramMechanism.REJECT;
            case PREFER -> plus
                    ? ScramMechanism.SCRAM_SHA_256_PLUS
                    : plain ? ScramMechanism.SCRAM_SHA_256 : ScramMechanism.REJECT;
            case DISABLE -> plain ? ScramMechanism.SCRAM_SHA_256 : ScramMechanism.REJECT;
        };
    }

    record ScramCompletion(
            boolean clientProofSent,
            boolean serverSignatureVerified,
            boolean authenticationOk,
            boolean authenticated) {}

    static ScramCompletion completeScram(
            boolean clientProofSent,
            boolean serverSignatureVerified,
            boolean authenticationOk) {
        if ((serverSignatureVerified || authenticationOk) && !clientProofSent) {
            throw new IllegalArgumentException("completion cannot precede the client proof");
        }
        boolean authenticated = clientProofSent && serverSignatureVerified && authenticationOk;
        return new ScramCompletion(
                clientProofSent, serverSignatureVerified, authenticationOk, authenticated);
    }

    enum PoolingMode { SESSION, TRANSACTION }
    enum SessionNeed { STABLE_DEFAULT, PER_TRANSACTION, TEMPORARY_OBJECT, CUSTOM_SESSION_STATE }
    enum BaselinePlacement { ROLE_OR_DATABASE_DEFAULT, PHYSICAL_INIT_SQL, TRANSACTION_LOCAL, REJECT }

    static BaselinePlacement baselinePlacement(
            PoolingMode mode, SessionNeed need, boolean canUseRoleOrDatabaseDefault) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(need, "need");
        if (need == SessionNeed.STABLE_DEFAULT && canUseRoleOrDatabaseDefault) {
            return BaselinePlacement.ROLE_OR_DATABASE_DEFAULT;
        }
        if (need == SessionNeed.PER_TRANSACTION) return BaselinePlacement.TRANSACTION_LOCAL;
        if (mode == PoolingMode.TRANSACTION) return BaselinePlacement.REJECT;
        return BaselinePlacement.PHYSICAL_INIT_SQL;
    }

    enum MySqlCapability { PROTOCOL_41, SECURE_CONNECTION, PLUGIN_AUTH, SSL }

    record MySqlHandshakePlan(
            Set<MySqlCapability> negotiatedCapabilities,
            boolean tls,
            boolean identityVerified,
            List<String> events) {}

    static MySqlHandshakePlan mysqlHandshakePlan(
            Set<MySqlCapability> serverCapabilities,
            Set<MySqlCapability> requestedCapabilities,
            boolean requireTls,
            boolean verifyIdentity) {
        Objects.requireNonNull(serverCapabilities, "serverCapabilities");
        Objects.requireNonNull(requestedCapabilities, "requestedCapabilities");
        Set<MySqlCapability> negotiated = new HashSet<>(requestedCapabilities);
        negotiated.retainAll(serverCapabilities);
        boolean tls = negotiated.contains(MySqlCapability.SSL);
        if ((requireTls || verifyIdentity) && !tls) {
            throw new IllegalStateException("required TLS capability was not negotiated");
        }
        List<String> events = new ArrayList<>();
        events.add("TCP connected");
        events.add("server HandshakeV10 greeting");
        if (tls) {
            events.add("client SSLRequest with CLIENT_SSL");
            events.add("TLS established");
        }
        events.add("client HandshakeResponse");
        events.add("authentication exchange");
        events.add("OK or ERR");
        return new MySqlHandshakePlan(
                Set.copyOf(negotiated), tls, tls && verifyIdentity, List.copyOf(events));
    }

    enum MySqlAuthPath { OPTIMISTIC_PLUGIN, AUTH_SWITCH }

    record MySqlAuthNegotiation(
            String greetingPlugin,
            String accountPlugin,
            MySqlAuthPath path,
            List<String> messages) {}

    static MySqlAuthNegotiation mysqlAuthNegotiation(
            String greetingPlugin, String accountPlugin) {
        if (greetingPlugin == null || greetingPlugin.isBlank()
                || accountPlugin == null || accountPlugin.isBlank()) {
            throw new IllegalArgumentException("authentication plugin names are required");
        }
        boolean compatible = greetingPlugin.equals(accountPlugin);
        return new MySqlAuthNegotiation(
                greetingPlugin,
                accountPlugin,
                compatible ? MySqlAuthPath.OPTIMISTIC_PLUGIN : MySqlAuthPath.AUTH_SWITCH,
                compatible
                        ? List.of("HandshakeResponse", "AuthMoreData or OK")
                        : List.of("HandshakeResponse", "AuthSwitchRequest", "AuthSwitchResponse",
                                "AuthMoreData or OK"));
    }

    enum Sha2FullAuthChannel {
        VERIFIED_TLS,
        PINNED_RSA_PUBLIC_KEY,
        RETRIEVED_RSA_PUBLIC_KEY,
        NONE
    }
    enum CachingSha2Outcome {
        FAST_CHALLENGE_RESPONSE,
        FULL_OVER_TLS,
        FULL_WITH_PINNED_RSA,
        FULL_WITH_RETRIEVED_RSA,
        REJECT
    }

    static CachingSha2Outcome cachingSha2Outcome(
            boolean cacheHit,
            Sha2FullAuthChannel channel,
            boolean allowPublicKeyRetrieval) {
        Objects.requireNonNull(channel, "channel");
        if (cacheHit) return CachingSha2Outcome.FAST_CHALLENGE_RESPONSE;
        return switch (channel) {
            case VERIFIED_TLS -> CachingSha2Outcome.FULL_OVER_TLS;
            case PINNED_RSA_PUBLIC_KEY -> CachingSha2Outcome.FULL_WITH_PINNED_RSA;
            case RETRIEVED_RSA_PUBLIC_KEY -> allowPublicKeyRetrieval
                    ? CachingSha2Outcome.FULL_WITH_RETRIEVED_RSA
                    : CachingSha2Outcome.REJECT;
            case NONE -> CachingSha2Outcome.REJECT;
        };
    }

    enum Sha2CacheEvent { ORDINARY_RECONNECT, PASSWORD_CHANGE, RENAME_USER, FLUSH_PRIVILEGES, RESTART }

    static boolean sha2CacheMayRemainWarm(Sha2CacheEvent event) {
        return Objects.requireNonNull(event, "event") == Sha2CacheEvent.ORDINARY_RECONNECT;
    }

    record MySqlSessionContract(
            String characterSet,
            String collation,
            String sqlMode,
            String timeZone,
            String isolation) {
        MySqlSessionContract {
            if (List.of(characterSet, collation, sqlMode, timeZone, isolation).stream()
                    .anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("all session expectations are required");
            }
        }
    }

    static List<String> mysqlSessionMismatches(
            MySqlSessionContract expected, MySqlSessionContract actual) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        List<String> mismatches = new ArrayList<>();
        if (!expected.characterSet().equals(actual.characterSet())) mismatches.add("characterSet");
        if (!expected.collation().equals(actual.collation())) mismatches.add("collation");
        if (!expected.sqlMode().equals(actual.sqlMode())) mismatches.add("sqlMode");
        if (!expected.timeZone().equals(actual.timeZone())) mismatches.add("timeZone");
        if (!expected.isolation().equals(actual.isolation())) mismatches.add("isolation");
        return List.copyOf(mismatches);
    }

    enum PgQueryMode { SIMPLE_QUERY, UNNAMED_EXTENDED, NAMED_PREPARED }

    static PgQueryMode pgQueryMode(
            boolean preparedStatementApi,
            int completedExecutions,
            int prepareThreshold,
            boolean forceSimple) {
        if (completedExecutions < 0 || prepareThreshold < 0) {
            throw new IllegalArgumentException("execution counts cannot be negative");
        }
        if (forceSimple || !preparedStatementApi) return PgQueryMode.SIMPLE_QUERY;
        if (prepareThreshold == 0 || completedExecutions + 1 < prepareThreshold) {
            return PgQueryMode.UNNAMED_EXTENDED;
        }
        return PgQueryMode.NAMED_PREPARED;
    }

    record ExtendedQueryCycle(
            List<String> frontendMessages,
            List<String> possibleExecuteTerminals,
            boolean readyForQueryAfterSync) {}

    static ExtendedQueryCycle extendedQueryCycle(
            boolean namedStatementAlreadyPrepared,
            boolean describePortal,
            int maximumRows) {
        if (maximumRows < 0) throw new IllegalArgumentException("maximumRows cannot be negative");
        List<String> frontend = new ArrayList<>();
        if (!namedStatementAlreadyPrepared) frontend.add("Parse");
        frontend.add("Bind");
        if (describePortal) frontend.add("Describe portal");
        frontend.add("Execute(maxRows=" + maximumRows + ")");
        frontend.add("Sync");
        List<String> terminals = maximumRows == 0
                ? List.of("CommandComplete", "EmptyQueryResponse", "ErrorResponse")
                : List.of("CommandComplete", "PortalSuspended", "ErrorResponse");
        return new ExtendedQueryCycle(List.copyOf(frontend), terminals, true);
    }

    private static final Set<String> SAFE_ORDER_COLUMNS =
            Set.of("created_at", "id", "total_cents");

    static String safeOrderQuery(String requestedColumn, boolean descending) {
        if (!SAFE_ORDER_COLUMNS.contains(requestedColumn)) {
            throw new IllegalArgumentException("unapproved SQL identifier");
        }
        return "SELECT id, total_cents FROM orders "
                + "WHERE tenant_id = ? AND created_at >= ? ORDER BY "
                + requestedColumn + (descending ? " DESC" : " ASC");
    }

    enum RetrievalMode {
        BUFFER_ALL_DEFAULT,
        BUFFER_ALL_CURSOR_INELIGIBLE,
        CURSOR_BATCHED
    }

    record FetchPlan(
            RetrievalMode mode,
            long estimatedPeakPayloadBytes,
            int executeCycles,
            List<String> cleanupEvents) {}

    static FetchPlan fetchPlan(
            boolean autoCommit,
            boolean forwardOnly,
            int fetchSize,
            int expectedRows,
            int averageRowBytes,
            boolean querySucceeded) {
        if (fetchSize < 0 || expectedRows < 0 || averageRowBytes <= 0) {
            throw new IllegalArgumentException("invalid fetch dimensions");
        }
        boolean cursorEligible = fetchSize > 0 && !autoCommit && forwardOnly;
        RetrievalMode mode = cursorEligible
                ? RetrievalMode.CURSOR_BATCHED
                : fetchSize == 0
                        ? RetrievalMode.BUFFER_ALL_DEFAULT
                        : RetrievalMode.BUFFER_ALL_CURSOR_INELIGIBLE;
        long rowsHeld = cursorEligible ? Math.min(fetchSize, expectedRows) : expectedRows;
        long peakPayload = Math.multiplyExact(rowsHeld, averageRowBytes);
        int cycles = cursorEligible && expectedRows > 0
                ? Math.toIntExact((expectedRows + (long) fetchSize - 1) / fetchSize)
                : 1;
        List<String> cleanup = new ArrayList<>(List.of("close ResultSet", "close PreparedStatement"));
        if (!autoCommit) cleanup.add(querySucceeded ? "commit transaction" : "rollback transaction");
        cleanup.add("close pooled Connection lease");
        return new FetchPlan(mode, peakPayload, cycles, List.copyOf(cleanup));
    }

    record RevisionIdentity(String tenantId, String articleId, long revision) {
        RevisionIdentity {
            if (tenantId == null || tenantId.isBlank()
                    || articleId == null || articleId.isBlank()
                    || tenantId.contains("{") || tenantId.contains("}") || tenantId.contains(":")
                    || articleId.contains("{") || articleId.contains("}") || articleId.contains(":")) {
                throw new IllegalArgumentException("key components must be nonblank and delimiter-safe");
            }
            if (revision <= 0) throw new IllegalArgumentException("revision must be positive");
        }
    }

    record RevisionKeys(String body, String absent, String lease) {}

    static RevisionKeys revisionKeys(RevisionIdentity id) {
        Objects.requireNonNull(id, "id");
        String slot = "{" + id.tenantId() + "|" + id.articleId() + "|" + id.revision() + "}";
        String prefix = "cms:revision:" + slot;
        return new RevisionKeys(prefix + ":body", prefix + ":absent", prefix + ":lease");
    }

    enum RevisionReadOutcome {
        DENIED,
        CACHE_BODY,
        CACHED_ABSENT,
        DATABASE_BODY,
        DATABASE_ABSENT,
        RETRYABLE_UNAVAILABLE
    }

    record RevisionReadDecision(RevisionReadOutcome outcome, List<String> events) {}

    static RevisionReadDecision revisionReadDecision(
            boolean authorized,
            boolean cachedBody,
            boolean cachedAbsent,
            boolean strictPostPublishVisibility,
            boolean redisAvailable,
            boolean databasePermitAvailable,
            boolean databaseFound) {
        List<String> events = new ArrayList<>();
        events.add("authorize and verify publication status");
        if (!authorized) {
            events.add("deny before cache access");
            return new RevisionReadDecision(RevisionReadOutcome.DENIED, List.copyOf(events));
        }
        if (redisAvailable && cachedBody) {
            events.add("serve immutable revision body from Redis");
            return new RevisionReadDecision(RevisionReadOutcome.CACHE_BODY, List.copyOf(events));
        }
        if (redisAvailable && cachedAbsent && !strictPostPublishVisibility) {
            events.add("serve bounded negative entry");
            return new RevisionReadDecision(RevisionReadOutcome.CACHED_ABSENT, List.copyOf(events));
        }
        if (cachedAbsent && strictPostPublishVisibility) {
            events.add("bypass negative entry for strict publication visibility");
        } else if (!redisAvailable) {
            events.add("Redis unavailable");
        } else {
            events.add("cache miss");
        }
        if (!databasePermitAvailable) {
            events.add("reject because database bulkhead is full");
            return new RevisionReadDecision(
                    RevisionReadOutcome.RETRYABLE_UNAVAILABLE, List.copyOf(events));
        }
        events.add("query exact revision on primary");
        if (databaseFound) {
            if (redisAvailable) events.add("publish immutable body with bounded TTL");
            return new RevisionReadDecision(RevisionReadOutcome.DATABASE_BODY, List.copyOf(events));
        }
        if (redisAvailable) events.add("publish short negative entry");
        return new RevisionReadDecision(RevisionReadOutcome.DATABASE_ABSENT, List.copyOf(events));
    }

    record PublicationPlan(List<String> steps, boolean immediatelyVisible) {}

    static PublicationPlan publicationPlan(
            boolean priorNegativeMayExist, boolean strictImmediateVisibility) {
        List<String> steps = new ArrayList<>(List.of(
                "authorize writer",
                "insert new immutable revision",
                "commit database transaction"));
        if (priorNegativeMayExist) steps.add("delete exact revision negative key");
        if (strictImmediateVisibility) {
            steps.add("resolve published-revision pointer on primary");
            steps.add("serve exact revision while bypassing negative cache");
        } else {
            steps.add("allow bounded negative TTL to expire");
        }
        return new PublicationPlan(List.copyOf(steps), strictImmediateVisibility);
    }

    record ProductionBudget(
            long steadyFallbackMillis,
            long coldFallbackMillis,
            boolean validationBelowCheckout,
            boolean socketOutlivesStatement,
            boolean callerCoversSteadyFallback,
            boolean callerCoversColdFallback) {}

    static ProductionBudget productionBudget(
            long poolCheckoutMillis,
            long validationMillis,
            long redisConnectMillis,
            long redisReadMillis,
            long pgConnectMillis,
            long pgSocketMillis,
            long statementMillis,
            long responseReserveMillis,
            long callerMillis) {
        if (List.of(poolCheckoutMillis, validationMillis, redisConnectMillis, redisReadMillis,
                pgConnectMillis, pgSocketMillis, statementMillis, responseReserveMillis,
                callerMillis).stream().anyMatch(value -> value <= 0)) {
            throw new IllegalArgumentException("all timeout budgets must be positive");
        }
        long cacheFailure = Math.max(redisConnectMillis, redisReadMillis);
        long steady = Math.addExact(cacheFailure,
                Math.addExact(poolCheckoutMillis,
                        Math.addExact(statementMillis, responseReserveMillis)));
        long cold = Math.addExact(steady, pgConnectMillis);
        return new ProductionBudget(
                steady,
                cold,
                validationMillis < poolCheckoutMillis,
                pgSocketMillis > statementMillis,
                callerMillis >= steady,
                callerMillis >= cold);
    }

    enum EnvelopeKind { MISS, BODY, ABSENT, UNKNOWN }

    record DecodedEnvelope(EnvelopeKind kind, String body) {
        DecodedEnvelope {
            Objects.requireNonNull(kind, "kind");
            if ((kind == EnvelopeKind.BODY) != (body != null)) {
                throw new IllegalArgumentException("only a body envelope carries a body");
            }
        }
    }

    static String encodeBodyEnvelope(String body) {
        return "v1:" + Objects.requireNonNull(body, "body");
    }

    static DecodedEnvelope decodeEnvelope(String raw) {
        if (raw == null) return new DecodedEnvelope(EnvelopeKind.MISS, null);
        if (raw.equals("n1")) return new DecodedEnvelope(EnvelopeKind.ABSENT, null);
        if (raw.startsWith("v1:")) {
            return new DecodedEnvelope(EnvelopeKind.BODY, raw.substring(3));
        }
        return new DecodedEnvelope(EnvelopeKind.UNKNOWN, null);
    }

    /** In-memory state-machine model of the two owner-checking Lua scripts. */
    static final class OwnerCheckedCache {
        private String leaseOwner;
        private String value;

        boolean acquire(String token) {
            Objects.requireNonNull(token, "token");
            if (token.isBlank()) throw new IllegalArgumentException("blank token");
            if (leaseOwner != null) return false;
            leaseOwner = token;
            return true;
        }

        void expireLease() { leaseOwner = null; }

        boolean publish(String token, String envelope) {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(envelope, "envelope");
            if (!token.equals(leaseOwner)) return false;
            value = envelope;
            leaseOwner = null;
            return true;
        }

        boolean release(String token) {
            Objects.requireNonNull(token, "token");
            if (!token.equals(leaseOwner)) return false;
            leaseOwner = null;
            return true;
        }

        String leaseOwner() { return leaseOwner; }
        String value() { return value; }
    }

    enum CacheAsideOutcome {
        FIRST_LOOKUP_HIT,
        SECOND_LOOKUP_HIT,
        OWNER_DATABASE_BODY,
        OWNER_DATABASE_ABSENT,
        WAITER_CACHE_HIT,
        WAITER_DATABASE_BODY_NO_PUBLISH,
        WAITER_DATABASE_ABSENT_NO_PUBLISH
    }

    record CacheAsideDecision(
            CacheAsideOutcome outcome,
            int polls,
            boolean databaseRead,
            boolean publishAttempted,
            List<String> events) {}

    static CacheAsideDecision cacheAsideDecision(
            boolean firstLookupHit,
            boolean leaseAcquired,
            boolean secondLookupHit,
            List<Boolean> waiterPollHits,
            boolean databaseFound) {
        Objects.requireNonNull(waiterPollHits, "waiterPollHits");
        if (waiterPollHits.size() > 3) {
            throw new IllegalArgumentException("at most three bounded polls");
        }
        List<String> events = new ArrayList<>(List.of("first cache lookup"));
        if (firstLookupHit) {
            return new CacheAsideDecision(
                    CacheAsideOutcome.FIRST_LOOKUP_HIT, 0, false, false,
                    List.copyOf(events));
        }
        events.add("try owner lease");
        if (leaseAcquired) {
            events.add("second cache lookup after lease acquisition");
            if (secondLookupHit) {
                return new CacheAsideDecision(
                        CacheAsideOutcome.SECOND_LOOKUP_HIT, 0, false, false,
                        List.copyOf(events));
            }
            events.add("read database through bulkhead");
            events.add("owner-checking publish");
            return new CacheAsideDecision(
                    databaseFound
                            ? CacheAsideOutcome.OWNER_DATABASE_BODY
                            : CacheAsideOutcome.OWNER_DATABASE_ABSENT,
                    0, true, true, List.copyOf(events));
        }
        int polls = 0;
        for (boolean hit : waiterPollHits) {
            polls++;
            events.add("bounded waiter poll " + polls);
            if (hit) {
                return new CacheAsideDecision(
                        CacheAsideOutcome.WAITER_CACHE_HIT, polls, false, false,
                        List.copyOf(events));
            }
        }
        events.add("read database without publication rights");
        return new CacheAsideDecision(
                databaseFound
                        ? CacheAsideOutcome.WAITER_DATABASE_BODY_NO_PUBLISH
                        : CacheAsideOutcome.WAITER_DATABASE_ABSENT_NO_PUBLISH,
                polls, true, false, List.copyOf(events));
    }

    enum DegradedAdmission { CACHE_LOOKUP, DATABASE_FALLBACK, REJECTED }

    record CacheAdmission(
            DegradedAdmission outcome,
            boolean redisAttempted,
            boolean databasePermitAcquired) {}

    static long redisCooldownUntil(long failureNowNanos, long cooldownNanos) {
        if (failureNowNanos < 0 || cooldownNanos <= 0) {
            throw new IllegalArgumentException("monotonic time and positive cooldown required");
        }
        return Math.addExact(failureNowNanos, cooldownNanos);
    }

    static CacheAdmission cacheAdmission(
            long nowNanos,
            long cooldownUntilNanos,
            boolean redisLookupSucceeded,
            boolean databasePermitAvailable) {
        if (nowNanos < 0 || cooldownUntilNanos < 0) {
            throw new IllegalArgumentException("monotonic timestamps must not be negative");
        }
        boolean redisAttempted = nowNanos >= cooldownUntilNanos;
        if (redisAttempted && redisLookupSucceeded) {
            return new CacheAdmission(DegradedAdmission.CACHE_LOOKUP, true, false);
        }
        if (databasePermitAvailable) {
            return new CacheAdmission(
                    DegradedAdmission.DATABASE_FALLBACK, redisAttempted, true);
        }
        return new CacheAdmission(DegradedAdmission.REJECTED, redisAttempted, false);
    }

    record MutableDocument(String id, long version, String body) {
        MutableDocument {
            if (id == null || id.isBlank() || version <= 0 || body == null) {
                throw new IllegalArgumentException("valid document identity/version/body required");
            }
        }
    }

    record MutationEvent(String eventId, String aggregateId, long version, String body) {
        MutationEvent {
            if (eventId == null || eventId.isBlank()
                    || aggregateId == null || aggregateId.isBlank()
                    || version <= 0 || body == null) {
                throw new IllegalArgumentException("valid outbox event required");
            }
        }
    }

    record CommittedMutation(MutableDocument document, MutationEvent event) {}

    static CommittedMutation mutateWithOutbox(
            MutableDocument current,
            long expectedVersion,
            String newBody,
            String eventId,
            boolean failBeforeCommit) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(newBody, "newBody");
        if (current.version() != expectedVersion) {
            throw new IllegalStateException("optimistic version conflict");
        }
        long nextVersion = Math.addExact(current.version(), 1);
        MutableDocument stagedDocument =
                new MutableDocument(current.id(), nextVersion, newBody);
        MutationEvent stagedEvent =
                new MutationEvent(eventId, current.id(), nextVersion, newBody);
        if (failBeforeCommit) throw new InjectedFailure();
        return new CommittedMutation(stagedDocument, stagedEvent);
    }

    enum DeliveryState { PENDING, CLAIMED, DELIVERED }

    static final class DurableOutbox {
        private static final class Delivery {
            final MutationEvent event;
            DeliveryState state = DeliveryState.PENDING;
            String owner;

            Delivery(MutationEvent event) { this.event = event; }
        }

        private final List<Delivery> rows = new ArrayList<>();

        void append(MutationEvent event) {
            Objects.requireNonNull(event, "event");
            if (rows.stream().anyMatch(row -> row.event.eventId().equals(event.eventId()))) {
                throw new IllegalArgumentException("duplicate event ID");
            }
            rows.add(new Delivery(event));
        }

        List<MutationEvent> claim(String worker, int limit) {
            if (worker == null || worker.isBlank() || limit <= 0) {
                throw new IllegalArgumentException("worker and positive limit required");
            }
            List<MutationEvent> claimed = new ArrayList<>();
            for (Delivery row : rows) {
                if (row.state == DeliveryState.PENDING && claimed.size() < limit) {
                    row.state = DeliveryState.CLAIMED;
                    row.owner = worker;
                    claimed.add(row.event);
                }
            }
            return List.copyOf(claimed);
        }

        boolean acknowledge(String worker, String eventId) {
            Delivery row = requireEvent(eventId);
            if (row.state != DeliveryState.CLAIMED || !Objects.equals(worker, row.owner)) {
                return false;
            }
            row.state = DeliveryState.DELIVERED;
            row.owner = null;
            return true;
        }

        int recoverClaims(String failedWorker) {
            int recovered = 0;
            for (Delivery row : rows) {
                if (row.state == DeliveryState.CLAIMED
                        && Objects.equals(row.owner, failedWorker)) {
                    row.state = DeliveryState.PENDING;
                    row.owner = null;
                    recovered++;
                }
            }
            return recovered;
        }

        DeliveryState state(String eventId) { return requireEvent(eventId).state; }

        private Delivery requireEvent(String eventId) {
            return rows.stream()
                    .filter(row -> row.event.eventId().equals(eventId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown event"));
        }
    }

    enum EventApplication { APPLIED, DUPLICATE, STALE }

    static final class VersionedCacheGeneration {
        private final Set<String> seenEventIds = new HashSet<>();
        private long generation;
        private long cachedVersion;
        private String cachedBody;

        EventApplication apply(MutationEvent event) {
            Objects.requireNonNull(event, "event");
            if (!seenEventIds.add(event.eventId())) return EventApplication.DUPLICATE;
            if (event.version() <= generation) return EventApplication.STALE;
            generation = event.version();
            if (cachedVersion < generation) {
                cachedVersion = 0;
                cachedBody = null;
            }
            return EventApplication.APPLIED;
        }

        boolean publishLoaded(long loadedVersion, String body) {
            Objects.requireNonNull(body, "body");
            if (loadedVersion < generation || loadedVersion < cachedVersion) return false;
            cachedVersion = loadedVersion;
            cachedBody = body;
            return true;
        }

        long generation() { return generation; }
        long cachedVersion() { return cachedVersion; }
        String cachedBody() { return cachedBody; }
    }

    enum MutableReadPath { BYPASS_CACHE, VERSION_VALIDATED_CACHE }

    static MutableReadPath mutableReadPath(boolean versionValidationEstablished) {
        return versionValidationEstablished
                ? MutableReadPath.VERSION_VALIDATED_CACHE
                : MutableReadPath.BYPASS_CACHE;
    }

    enum FaultProbe {
        LUA_OWNER_CHECK,
        REDIS_OUTAGE_BULKHEAD,
        REDIS_CLUSTER_SLOT,
        POSTGRES_SERIALIZATION,
        LOST_COMMIT_RESPONSE,
        TLS_WRONG_HOST,
        POOL_RESOURCE_RETURN,
        CACHE_SCHEMA_ROLLOUT
    }

    enum RequiredTestLayer {
        REAL_REDIS,
        REAL_REDIS_CLUSTER,
        REAL_POSTGRESQL,
        REAL_TLS_ENDPOINT,
        REAL_POOL_AND_DATABASE
    }

    record FaultOracle(
            RequiredTestLayer layer,
            String injectedFault,
            String requiredObservation,
            String forbiddenObservation) {}

    static FaultOracle faultOracle(FaultProbe probe) {
        Objects.requireNonNull(probe, "probe");
        return switch (probe) {
            case LUA_OWNER_CHECK -> new FaultOracle(
                    RequiredTestLayer.REAL_REDIS,
                    "expire owner A, then let B acquire",
                    "A publish and cleanup are rejected while B remains owner",
                    "stale owner deletes or overwrites B");
            case REDIS_OUTAGE_BULKHEAD -> new FaultOracle(
                    RequiredTestLayer.REAL_REDIS,
                    "cut or pause Redis traffic",
                    "database fallback never exceeds the configured permits",
                    "unbounded fallback or false negative cache entries");
            case REDIS_CLUSTER_SLOT -> new FaultOracle(
                    RequiredTestLayer.REAL_REDIS_CLUSTER,
                    "run the script with matched and mismatched hash tags",
                    "matched keys execute; mismatched keys fail visibly",
                    "client silently splits an atomic multi-key script");
            case POSTGRES_SERIALIZATION -> new FaultOracle(
                    RequiredTestLayer.REAL_POSTGRESQL,
                    "barrier two conflicting serializable transactions",
                    "one aborts and the complete decision logic is retried",
                    "retry only the final UPDATE");
            case LOST_COMMIT_RESPONSE -> new FaultOracle(
                    RequiredTestLayer.REAL_POSTGRESQL,
                    "cut the connection after COMMIT reaches the server",
                    "the same operation ID resolves or safely retries",
                    "a new operation ID duplicates the business effect");
            case TLS_WRONG_HOST -> new FaultOracle(
                    RequiredTestLayer.REAL_TLS_ENDPOINT,
                    "serve a trusted certificate for another hostname",
                    "verify-full connection fails",
                    "fallback to an identity-unverified connection");
            case POOL_RESOURCE_RETURN -> new FaultOracle(
                    RequiredTestLayer.REAL_POOL_AND_DATABASE,
                    "exercise success, SQL failure and cancellation paths",
                    "active leases return to the baseline",
                    "connections or transactions remain borrowed");
            case CACHE_SCHEMA_ROLLOUT -> new FaultOracle(
                    RequiredTestLayer.REAL_REDIS,
                    "mix supported and unknown envelope versions",
                    "unknown format is a miss or separate namespace",
                    "unknown bytes are decoded as a current value");
        };
    }

    record LeaseFaultTrace(
            boolean stalePublishRejected,
            boolean staleCleanupRejected,
            String successorOwnerBeforePublish,
            String finalValue) {}

    static LeaseFaultTrace leaseExpiryFaultTrace() {
        OwnerCheckedCache cache = new OwnerCheckedCache();
        if (!cache.acquire("owner-a")) throw new AssertionError("A must acquire");
        cache.expireLease();
        if (!cache.acquire("owner-b")) throw new AssertionError("B must acquire");
        boolean stalePublishRejected = !cache.publish("owner-a", encodeBodyEnvelope("stale"));
        boolean staleCleanupRejected = !cache.release("owner-a");
        String successorOwnerBeforePublish = cache.leaseOwner();
        if (!cache.publish("owner-b", encodeBodyEnvelope("fresh"))) {
            throw new AssertionError("current owner must publish");
        }
        return new LeaseFaultTrace(
                stalePublishRejected,
                staleCleanupRejected,
                successorOwnerBeforePublish,
                decodeEnvelope(cache.value()).body());
    }

    enum IsolationHarness { REPEATABLE_READ, SERIALIZABLE }

    record OnCallSchedule(
            boolean aliceOnCall,
            boolean bobOnCall,
            int committedTransactions,
            int serializationFailures,
            int decisionEvaluations,
            boolean invariantPreserved) {}

    static OnCallSchedule onCallSchedule(IsolationHarness isolation, boolean retryWholeTransaction) {
        Objects.requireNonNull(isolation, "isolation");
        boolean alice = true;
        boolean bob = true;
        int decisions = 2; // both transactions read the initial two-person snapshot
        if (isolation == IsolationHarness.REPEATABLE_READ) {
            alice = false;
            bob = false;
            return new OnCallSchedule(alice, bob, 2, 0, decisions, alice || bob);
        }

        // Deterministic teaching schedule: Alice commits; Bob receives SQLSTATE 40001.
        alice = false;
        int commits = 1;
        int failures = 1;
        if (retryWholeTransaction) {
            decisions++; // Bob reruns the read and the decision, not only the UPDATE.
            if (alice) bob = false;
            commits++;
        }
        return new OnCallSchedule(alice, bob, commits, failures, decisions, alice || bob);
    }

    enum UnknownCommitAction {
        RETURN_STORED_OUTCOME,
        RETRY_SAME_OPERATION_ID,
        STOP_AT_DEADLINE
    }

    static UnknownCommitAction unknownCommitAction(
            boolean storedOutcomeFound, boolean deadlineRemaining) {
        if (storedOutcomeFound) return UnknownCommitAction.RETURN_STORED_OUTCOME;
        if (deadlineRemaining) return UnknownCommitAction.RETRY_SAME_OPERATION_ID;
        return UnknownCommitAction.STOP_AT_DEADLINE;
    }

    enum DatabaseFailureKind { PROCESS, STORAGE, NETWORK_PARTITION }

    enum DatabaseRecoveryAction {
        FENCE_BEFORE_FAILOVER,
        PROMOTE_ELIGIBLE_REPLICA,
        RESTORE_AND_REPLAY_WAL
    }

    record DatabaseRecoveryPlan(
            DatabaseRecoveryAction action,
            boolean writesAllowed,
            boolean reconcileUnknownOutcomes,
            int reconnectBatchSize,
            List<String> steps) {}

    static DatabaseRecoveryPlan databaseRecoveryPlan(
            DatabaseFailureKind failure,
            boolean failedPrimaryFenced,
            boolean eligibleReplica,
            int reconnectBatchSize) {
        Objects.requireNonNull(failure, "failure");
        if (reconnectBatchSize < 1) throw new IllegalArgumentException("reconnectBatchSize");
        if (!failedPrimaryFenced) {
            return new DatabaseRecoveryPlan(
                    DatabaseRecoveryAction.FENCE_BEFORE_FAILOVER,
                    false,
                    true,
                    0,
                    List.of("stop authoritative routing", "fence old primary", "verify one writer"));
        }
        if (eligibleReplica && failure != DatabaseFailureKind.STORAGE) {
            return new DatabaseRecoveryPlan(
                    DatabaseRecoveryAction.PROMOTE_ELIGIBLE_REPLICA,
                    true,
                    true,
                    reconnectBatchSize,
                    List.of("verify replay position", "promote once", "reconcile operation IDs",
                            "open reconnect batches", "watch database and pool saturation"));
        }
        return new DatabaseRecoveryPlan(
                DatabaseRecoveryAction.RESTORE_AND_REPLAY_WAL,
                false,
                true,
                reconnectBatchSize,
                List.of("select trusted base backup", "replay WAL to recovery target",
                        "validate invariants", "restore authoritative routing gradually"));
    }

    enum CacheRecoveryAction {
        RECONNECT_AND_OBSERVE,
        WARM_HOT_KEYS_WITH_BUDGET,
        SHED_LOAD_TO_PROTECT_DATABASE
    }

    record CacheRecoveryPlan(
            CacheRecoveryAction action,
            int allowedWarmers,
            int remainingDatabasePermits,
            boolean fullKeyspaceReloadAllowed,
            List<String> signals) {}

    static CacheRecoveryPlan cacheRecoveryPlan(
            boolean redisReachable,
            double hitRatio,
            int databaseActive,
            int databaseCapacity,
            int requestedWarmers,
            int warmingBudget) {
        if (hitRatio < 0 || hitRatio > 1) throw new IllegalArgumentException("hitRatio");
        if (databaseActive < 0 || databaseCapacity < 1 || databaseActive > databaseCapacity) {
            throw new IllegalArgumentException("database capacity");
        }
        if (requestedWarmers < 0 || warmingBudget < 0) {
            throw new IllegalArgumentException("warming budget");
        }
        int remaining = databaseCapacity - databaseActive;
        List<String> signals = List.of(
                "Redis reconnect rate", "cache hit ratio", "database active sessions",
                "pool pending threads", "miss-load p99");
        if (!redisReachable) {
            return new CacheRecoveryPlan(
                    remaining == 0
                            ? CacheRecoveryAction.SHED_LOAD_TO_PROTECT_DATABASE
                            : CacheRecoveryAction.RECONNECT_AND_OBSERVE,
                    0, remaining, false, signals);
        }
        if (remaining == 0) {
            return new CacheRecoveryPlan(
                    CacheRecoveryAction.SHED_LOAD_TO_PROTECT_DATABASE,
                    0, 0, false, signals);
        }
        int allowed = hitRatio < 0.80
                ? Math.min(Math.min(requestedWarmers, warmingBudget), remaining)
                : 0;
        return new CacheRecoveryPlan(
                allowed > 0
                        ? CacheRecoveryAction.WARM_HOT_KEYS_WITH_BUDGET
                        : CacheRecoveryAction.RECONNECT_AND_OBSERVE,
                allowed, remaining, false, signals);
    }

    enum PoolExhaustionCause {
        LONG_CONNECTION_HOLD,
        DATABASE_LOCK_WAIT,
        PGBOUNCER_SECOND_QUEUE,
        DATABASE_AT_CAPACITY,
        POOL_CAPACITY_MISMATCH
    }

    record PoolDiagnosis(
            PoolExhaustionCause cause,
            String firstAction,
            boolean increasePoolNow) {}

    static PoolDiagnosis diagnosePoolExhaustion(
            long usageP99Millis,
            long waitP99Millis,
            boolean databaseLockWait,
            boolean externalWorkInsideTransaction,
            boolean pgbouncerWaiting,
            int databaseActive,
            int databaseCapacity) {
        if (usageP99Millis < 0 || waitP99Millis < 0
                || databaseActive < 0 || databaseCapacity < 1
                || databaseActive > databaseCapacity) {
            throw new IllegalArgumentException("pool evidence");
        }
        if (databaseLockWait) {
            return new PoolDiagnosis(
                    PoolExhaustionCause.DATABASE_LOCK_WAIT,
                    "find blocker and shorten the locked transaction", false);
        }
        if (externalWorkInsideTransaction || usageP99Millis > 2_000) {
            return new PoolDiagnosis(
                    PoolExhaustionCause.LONG_CONNECTION_HOLD,
                    "remove external work and profile oldest holders", false);
        }
        if (pgbouncerWaiting) {
            return new PoolDiagnosis(
                    PoolExhaustionCause.PGBOUNCER_SECOND_QUEUE,
                    "inspect PgBouncer waiters and backend capacity", false);
        }
        if (databaseActive >= databaseCapacity) {
            return new PoolDiagnosis(
                    PoolExhaustionCause.DATABASE_AT_CAPACITY,
                    "shed load or reduce database work per request", false);
        }
        return new PoolDiagnosis(
                PoolExhaustionCause.POOL_CAPACITY_MISMATCH,
                "validate global connection budget before a bounded adjustment",
                waitP99Millis > 0);
    }

    enum RotationAction {
        ABORT_AND_RESTORE_OLD_MATERIAL,
        CONTINUE_OVERLAP_AND_CANARY,
        COMPLETE_AFTER_OLD_SESSIONS_DRAIN
    }

    record RotationPlan(
            RotationAction action,
            boolean activeQueriesProveRotation,
            int creationFailurePercent,
            List<String> gates) {}

    static RotationPlan rotationPlan(
            int oldSessions,
            int newConnectionAttempts,
            int newConnectionSuccesses,
            boolean trustAndCredentialOverlapPresent) {
        if (oldSessions < 0 || newConnectionAttempts < 0 || newConnectionSuccesses < 0
                || newConnectionSuccesses > newConnectionAttempts) {
            throw new IllegalArgumentException("rotation evidence");
        }
        int failurePercent = newConnectionAttempts == 0
                ? 0
                : (newConnectionAttempts - newConnectionSuccesses) * 100 / newConnectionAttempts;
        List<String> gates = List.of(
                "canary creates verified connection", "connection-creation failures stay zero",
                "old sessions drain", "new trust and credential path remains healthy");
        if (!trustAndCredentialOverlapPresent || failurePercent > 0) {
            return new RotationPlan(
                    RotationAction.ABORT_AND_RESTORE_OLD_MATERIAL,
                    false, failurePercent, gates);
        }
        if (oldSessions > 0 || newConnectionAttempts == 0) {
            return new RotationPlan(
                    RotationAction.CONTINUE_OVERLAP_AND_CANARY,
                    false, failurePercent, gates);
        }
        return new RotationPlan(
                RotationAction.COMPLETE_AFTER_OLD_SESSIONS_DRAIN,
                false, failurePercent, gates);
    }

    enum SeniorReadPath { CACHE, REPLICA, PRIMARY }

    enum FreshnessVerdict { FRESH_FOR_REQUIRED_VERSION, MAY_BE_STALE }

    static FreshnessVerdict freshnessVerdict(
            SeniorReadPath path,
            long requiredVersion,
            long cacheVersion,
            long replicaReplayVersion,
            long primaryVersion) {
        Objects.requireNonNull(path, "path");
        if (requiredVersion < 0 || cacheVersion < 0 || replicaReplayVersion < 0
                || primaryVersion < 0) {
            throw new IllegalArgumentException("versions");
        }
        long visibleVersion = switch (path) {
            case CACHE -> cacheVersion;
            case REPLICA -> replicaReplayVersion;
            case PRIMARY -> primaryVersion;
        };
        return visibleVersion >= requiredVersion
                ? FreshnessVerdict.FRESH_FOR_REQUIRED_VERSION
                : FreshnessVerdict.MAY_BE_STALE;
    }

    static boolean lsmHasWriteHeadroom(
            long logicalBytesPerSecond,
            double measuredWriteAmplification,
            long sustainableDiskBytesPerSecond) {
        if (logicalBytesPerSecond < 0 || measuredWriteAmplification < 1.0
                || sustainableDiskBytesPerSecond < 0) {
            throw new IllegalArgumentException("LSM rates");
        }
        return logicalBytesPerSecond * measuredWriteAmplification
                <= sustainableDiskBytesPerSecond;
    }

    enum RepeatableReadCase {
        SQL_STANDARD_MINIMUM,
        POSTGRESQL_SNAPSHOT_READ,
        INNODB_LOCKING_RANGE_READ
    }

    static boolean phantomPrevented(RepeatableReadCase readCase) {
        Objects.requireNonNull(readCase, "readCase");
        return readCase != RepeatableReadCase.SQL_STANDARD_MINIMUM;
    }

    enum CacheMissRoute {
        TRUST_FILTER_NEGATIVE,
        QUERY_SQL_WITH_PERMIT,
        REJECT_TO_PROTECT_DATABASE
    }

    static CacheMissRoute cacheMissRoute(
            long rowCommitPosition,
            long bloomAppliedPosition,
            boolean staleReaderInFlight,
            int availableDatabasePermits) {
        if (rowCommitPosition < 0 || bloomAppliedPosition < 0 || availableDatabasePermits < 0) {
            throw new IllegalArgumentException("cache evidence");
        }
        boolean filterCoversCommit = bloomAppliedPosition >= rowCommitPosition;
        if (filterCoversCommit && !staleReaderInFlight) {
            return CacheMissRoute.TRUST_FILTER_NEGATIVE;
        }
        return availableDatabasePermits > 0
                ? CacheMissRoute.QUERY_SQL_WITH_PERMIT
                : CacheMissRoute.REJECT_TO_PROTECT_DATABASE;
    }

    enum SeniorTimeoutBoundary {
        POOL_CHECKOUT,
        STATEMENT_EXECUTION,
        SOCKET_READ,
        END_TO_END_DEADLINE
    }

    static String timeoutOwner(SeniorTimeoutBoundary boundary) {
        Objects.requireNonNull(boundary, "boundary");
        return switch (boundary) {
            case POOL_CHECKOUT -> "Hikari connectionTimeout";
            case STATEMENT_EXECUTION -> "JDBC query timeout or database statement timeout";
            case SOCKET_READ -> "driver socket timeout";
            case END_TO_END_DEADLINE -> "caller deadline and cancellation";
        };
    }

    enum ConnectionHealthVerdict { REJECT_NOW, VALID_AT_CHECK_ONLY }

    static ConnectionHealthVerdict connectionHealthVerdict(boolean isValidNow) {
        return isValidNow
                ? ConnectionHealthVerdict.VALID_AT_CHECK_ONLY
                : ConnectionHealthVerdict.REJECT_NOW;
    }

    enum WireProtocol { POSTGRESQL, MYSQL }

    static List<String> tlsStartupOrder(WireProtocol protocol) {
        Objects.requireNonNull(protocol, "protocol");
        return switch (protocol) {
            case POSTGRESQL -> List.of(
                    "client SSLRequest", "server S", "TLS handshake", "StartupMessage", "authentication");
            case MYSQL -> List.of(
                    "server initial handshake", "client SSLRequest", "TLS handshake",
                    "HandshakeResponse", "authentication");
        };
    }

    enum IdentityLayer { TLS_SERVER_IDENTITY, DATABASE_PRINCIPAL }

    static boolean layerAuthenticatesDatabasePrincipal(
            IdentityLayer layer, boolean certificateAuthenticationConfigured) {
        Objects.requireNonNull(layer, "layer");
        return layer == IdentityLayer.DATABASE_PRINCIPAL
                || certificateAuthenticationConfigured;
    }

    record AcceptanceResult(boolean accepted, List<String> blockers) {
        AcceptanceResult {
            blockers = List.copyOf(blockers);
            if (accepted == !blockers.isEmpty()) {
                throw new IllegalArgumentException("acceptance result must match blockers");
            }
        }
    }

    static AcceptanceResult storageAcceptance(
            boolean invariantsAndDurabilityWritten,
            boolean representativePlansVerified,
            boolean maintenanceHeadroomMeasured,
            boolean immutableRevisionsEnforced) {
        List<String> blockers = new ArrayList<>();
        if (!invariantsAndDurabilityWritten) blockers.add("transaction and durability contract missing");
        if (!representativePlansVerified) blockers.add("representative query plans not verified");
        if (!maintenanceHeadroomMeasured) blockers.add("vacuum or compaction headroom unknown");
        if (!immutableRevisionsEnforced) blockers.add("revision immutability not enforced");
        return new AcceptanceResult(blockers.isEmpty(), blockers);
    }

    static AcceptanceResult cacheAcceptance(
            boolean scopeSizeFreshnessRetentionExplicit,
            boolean missAndOutageConcurrencyBounded,
            boolean correctnessLocksSeparate,
            boolean cacheHitsAuthorized) {
        List<String> blockers = new ArrayList<>();
        if (!scopeSizeFreshnessRetentionExplicit) blockers.add("cache contract incomplete");
        if (!missAndOutageConcurrencyBounded) blockers.add("miss or outage path unbounded");
        if (!correctnessLocksSeparate) blockers.add("cache lease reused as correctness lock");
        if (!cacheHitsAuthorized) blockers.add("cache hit bypasses authorization");
        return new AcceptanceResult(blockers.isEmpty(), blockers);
    }

    static AcceptanceResult connectivitySecurityAcceptance(
            int applicationInstances,
            int poolsPerInstance,
            int maximumPoolSize,
            int databaseConnectionBudget,
            int surgeAndAdminReserve,
            boolean layeredTimeoutsTested,
            boolean tlsIdentityVerified,
            boolean leastPrivilegeRolesAndAcls) {
        if (applicationInstances < 0 || poolsPerInstance < 0 || maximumPoolSize < 0
                || databaseConnectionBudget < 0 || surgeAndAdminReserve < 0
                || surgeAndAdminReserve > databaseConnectionBudget) {
            throw new IllegalArgumentException("connectivity budget");
        }
        List<String> blockers = new ArrayList<>();
        long fleetMaximum = (long) applicationInstances * poolsPerInstance * maximumPoolSize;
        if (fleetMaximum > databaseConnectionBudget - surgeAndAdminReserve) {
            blockers.add("fleet pools exceed database budget after reserve");
        }
        if (!layeredTimeoutsTested) blockers.add("timeout stack not tested together");
        if (!tlsIdentityVerified) blockers.add("TLS peer identity not verified");
        if (!leastPrivilegeRolesAndAcls) blockers.add("database roles or Redis ACLs too broad");
        return new AcceptanceResult(blockers.isEmpty(), blockers);
    }

    static AcceptanceResult releaseRecoveryAcceptance(
            boolean readinessAndRollbackExercised,
            boolean credentialRotationExercised,
            boolean unknownCommitReconciled,
            boolean backupRestoreMeasured) {
        List<String> blockers = new ArrayList<>();
        if (!readinessAndRollbackExercised) blockers.add("rolling readiness or rollback unproved");
        if (!credentialRotationExercised) blockers.add("credential rotation unproved");
        if (!unknownCommitReconciled) blockers.add("unknown commit outcome unproved");
        if (!backupRestoreMeasured) blockers.add("backup restore not exercised and measured");
        return new AcceptanceResult(blockers.isEmpty(), blockers);
    }

    enum PlanWork { CUSTOM_FOR_VALUES, GENERIC_PLAN_CANDIDATE, REANALYZE_AND_REPLAN }

    static PlanWork planWork(
            boolean namedPreparedStatement,
            int completedCustomExecutions,
            boolean catalogStatisticsOrSearchPathChanged) {
        if (completedCustomExecutions < 0) throw new IllegalArgumentException("execution count");
        if (catalogStatisticsOrSearchPathChanged) return PlanWork.REANALYZE_AND_REPLAN;
        if (namedPreparedStatement && completedCustomExecutions >= 5) {
            return PlanWork.GENERIC_PLAN_CANDIDATE;
        }
        return PlanWork.CUSTOM_FOR_VALUES;
    }

    static List<String> logicalReadPipeline(PlanWork planWork) {
        Objects.requireNonNull(planWork, "planWork");
        List<String> stages = new ArrayList<>();
        stages.add("bind names, types and permissions");
        stages.add(switch (planWork) {
            case CUSTOM_FOR_VALUES -> "plan with current parameter values";
            case GENERIC_PLAN_CANDIDATE -> "compare or reuse generic plan";
            case REANALYZE_AND_REPLAN -> "reanalyze catalog and rebuild plan";
        });
        stages.add("execute plan operators");
        stages.add("locate index entry and heap tuple");
        stages.add("pin page and test snapshot visibility");
        stages.add("encode RowDescription and DataRow");
        return List.copyOf(stages);
    }

    record TuplePointer(int pageNumber, int slotIndex) {
        TuplePointer {
            if (pageNumber < 0 || slotIndex <= 0) throw new IllegalArgumentException("tuple pointer");
        }
    }

    record PageSlot(int byteOffset, int length, boolean used) {
        PageSlot {
            if (byteOffset < 0 || length < 0) throw new IllegalArgumentException("page slot");
        }
    }

    static int resolveTupleOffset(List<PageSlot> slots, TuplePointer pointer) {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(pointer, "pointer");
        if (pointer.slotIndex() > slots.size()) throw new IllegalArgumentException("missing slot");
        PageSlot slot = slots.get(pointer.slotIndex() - 1);
        if (!slot.used()) throw new IllegalStateException("unused slot");
        return slot.byteOffset();
    }

    static List<PageSlot> relocateTupleWithinPage(
            List<PageSlot> slots, int slotIndex, int newByteOffset) {
        if (slotIndex <= 0 || slotIndex > slots.size() || newByteOffset < 0) {
            throw new IllegalArgumentException("relocation");
        }
        List<PageSlot> compacted = new ArrayList<>(slots);
        PageSlot old = compacted.get(slotIndex - 1);
        if (!old.used()) throw new IllegalStateException("unused slot");
        compacted.set(slotIndex - 1, new PageSlot(newByteOffset, old.length(), true));
        return List.copyOf(compacted);
    }

    enum PageSource { SHARED_BUFFER, OPERATING_SYSTEM_CACHE, PHYSICAL_STORAGE }

    record BufferAccess(
            PageSource source,
            boolean framePinnedDuringDecode,
            boolean contentLatchShortLived,
            boolean transactionLockHeld) {}

    static BufferAccess bufferAccess(
            boolean sharedBufferHit, boolean operatingSystemCacheHit, boolean lockingRead) {
        PageSource source = sharedBufferHit
                ? PageSource.SHARED_BUFFER
                : operatingSystemCacheHit
                        ? PageSource.OPERATING_SYSTEM_CACHE
                        : PageSource.PHYSICAL_STORAGE;
        return new BufferAccess(source, true, true, lockingRead);
    }

    record TupleVersion(String body, long xmin, Long xmax) {
        TupleVersion {
            Objects.requireNonNull(body, "body");
            if (xmin < 0 || (xmax != null && xmax < xmin)) {
                throw new IllegalArgumentException("version XIDs");
            }
        }
    }

    // Deliberately simplified teaching predicate; PostgreSQL also tracks commit status and snapshot sets.
    static boolean visibleAt(TupleVersion version, long snapshotXid) {
        Objects.requireNonNull(version, "version");
        if (snapshotXid < 0) throw new IllegalArgumentException("snapshot XID");
        return version.xmin() <= snapshotXid
                && (version.xmax() == null || version.xmax() > snapshotXid);
    }

    static TupleVersion newestVisible(List<TupleVersion> newestFirst, long snapshotXid) {
        Objects.requireNonNull(newestFirst, "newestFirst");
        return newestFirst.stream()
                .filter(version -> visibleAt(version, snapshotXid))
                .findFirst()
                .orElse(null);
    }

    static int dataRowWireBytes(int... columnBytes) {
        int bytes = 1 + Integer.BYTES + Short.BYTES; // type byte, message length, column count
        for (int length : columnBytes) {
            if (length < -1) throw new IllegalArgumentException("column length");
            bytes = Math.addExact(bytes, Integer.BYTES); // per-column length field
            if (length >= 0) bytes = Math.addExact(bytes, length);
        }
        return bytes;
    }

    enum ReadBottleneck { INDEX, HEAP_OR_TOAST, PROTOCOL_TRANSFER, JDBC_MATERIALIZATION }

    static ReadBottleneck largestReadStage(
            long indexMicros, long heapOrToastMicros,
            long protocolMicros, long jdbcMaterializationMicros) {
        long[] values = {indexMicros, heapOrToastMicros, protocolMicros, jdbcMaterializationMicros};
        for (long value : values) if (value < 0) throw new IllegalArgumentException("negative time");
        int largest = 0;
        for (int i = 1; i < values.length; i++) if (values[i] > values[largest]) largest = i;
        return ReadBottleneck.values()[largest];
    }

    enum EditTxnDecision { COMMIT, ROLLBACK_VERSION_CONFLICT }

    record EditTxnResult(
            EditTxnDecision decision,
            int updateCount,
            long storedVersion,
            boolean outboxInserted,
            boolean operationResultInserted) {}

    static EditTxnResult editTransaction(
            long currentVersion,
            long expectedVersion,
            boolean failBeforeCommit) {
        if (currentVersion <= 0 || expectedVersion <= 0) {
            throw new IllegalArgumentException("versions must be positive");
        }
        if (currentVersion != expectedVersion) {
            return new EditTxnResult(
                    EditTxnDecision.ROLLBACK_VERSION_CONFLICT,
                    0, currentVersion, false, false);
        }
        long nextVersion = Math.addExact(currentVersion, 1);
        if (failBeforeCommit) throw new InjectedFailure();
        return new EditTxnResult(EditTxnDecision.COMMIT, 1, nextVersion, true, true);
    }

    enum CrashPoint {
        BEFORE_DURABLE_COMMIT,
        AFTER_DURABLE_COMMIT_BEFORE_PAGE_FLUSH,
        RESPONSE_LOST_AFTER_DURABLE_COMMIT
    }

    enum ClientKnowledge {
        NOT_COMMITTED_AFTER_RECOVERY,
        COMMIT_ACKNOWLEDGED,
        UNKNOWN_QUERY_OPERATION_ID
    }

    record CrashOutcome(
            long recoveredVersion,
            boolean outboxRecovered,
            boolean walRedoRequired,
            ClientKnowledge clientKnowledge) {}

    static CrashOutcome recoverEdit(CrashPoint point) {
        Objects.requireNonNull(point, "point");
        return switch (point) {
            case BEFORE_DURABLE_COMMIT -> new CrashOutcome(
                    9, false, false, ClientKnowledge.NOT_COMMITTED_AFTER_RECOVERY);
            case AFTER_DURABLE_COMMIT_BEFORE_PAGE_FLUSH -> new CrashOutcome(
                    10, true, true, ClientKnowledge.COMMIT_ACKNOWLEDGED);
            case RESPONSE_LOST_AFTER_DURABLE_COMMIT -> new CrashOutcome(
                    10, true, true, ClientKnowledge.UNKNOWN_QUERY_OPERATION_ID);
        };
    }

    enum DurabilityBoundary {
        DURABLE_BEFORE_SUCCESS,
        SUCCESS_CAN_PRECEDE_DURABILITY,
        CRASH_RECOVERY_UNSAFE
    }

    static DurabilityBoundary durabilityBoundary(boolean fsync, boolean synchronousCommit) {
        if (!fsync) return DurabilityBoundary.CRASH_RECOVERY_UNSAFE;
        return synchronousCommit
                ? DurabilityBoundary.DURABLE_BEFORE_SUCCESS
                : DurabilityBoundary.SUCCESS_CAN_PRECEDE_DURABILITY;
    }

    enum EditRetryDecision {
        RETURN_RECORDED_RESULT,
        RETRY_SAME_OPERATION_ID,
        REJECT_NEW_OPERATION_ID,
        STOP_AT_DEADLINE
    }

    static EditRetryDecision resolveEditRetry(
            boolean operationResultFound,
            boolean sameOperationId,
            boolean deadlineRemaining) {
        if (operationResultFound) return EditRetryDecision.RETURN_RECORDED_RESULT;
        if (!sameOperationId) return EditRetryDecision.REJECT_NEW_OPERATION_ID;
        return deadlineRemaining
                ? EditRetryDecision.RETRY_SAME_OPERATION_ID
                : EditRetryDecision.STOP_AT_DEADLINE;
    }

    enum MaintenanceDiagnosis {
        VACUUM_RETENTION_PRESSURE,
        CHECKPOINT_RECOVERY_PRESSURE,
        BOTH_SIGNAL_FAMILIES,
        NO_PRESSURE_DETECTED
    }

    static MaintenanceDiagnosis diagnoseMaintenance(
            boolean oldSnapshotPresent,
            boolean deadVersionsGrowing,
            boolean recoveryTimeGrowing,
            boolean walSinceCheckpointGrowing) {
        boolean vacuumPressure = oldSnapshotPresent && deadVersionsGrowing;
        boolean checkpointPressure = recoveryTimeGrowing && walSinceCheckpointGrowing;
        if (vacuumPressure && checkpointPressure) return MaintenanceDiagnosis.BOTH_SIGNAL_FAMILIES;
        if (vacuumPressure) return MaintenanceDiagnosis.VACUUM_RETENTION_PRESSURE;
        if (checkpointPressure) return MaintenanceDiagnosis.CHECKPOINT_RECOVERY_PRESSURE;
        return MaintenanceDiagnosis.NO_PRESSURE_DETECTED;
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

    private static void tinyBPlusInsertScenarios() {
        TinyBPlusTree tree = new TinyBPlusTree(List.of(), List.of(List.of(10, 20, 30, 40)));
        tree = insertTinyBPlus(tree, 25, 4);
        assert tree.separators().equals(List.of(25));
        assert tree.leaves().equals(List.of(List.of(10, 20), List.of(25, 30, 40)));

        tree = insertTinyBPlus(tree, 35, 4);
        assert tree.separators().equals(List.of(25));
        tree = insertTinyBPlus(tree, 45, 4);
        assert tree.separators().equals(List.of(25, 35));
        assert tree.leaves().equals(List.of(
                List.of(10, 20), List.of(25, 30), List.of(35, 40, 45)));
    }

    private static void linkedLeafRangeScenarios() {
        TinyBPlusTree tree = new TinyBPlusTree(
                List.of(25, 35),
                List.of(List.of(10, 20), List.of(25, 30), List.of(35, 40, 45)));
        LinkedRangeScan scan = linkedRangeScan(tree, 22, 41);
        assert scan.keys().equals(List.of(25, 30, 35, 40));
        assert scan.leavesVisited() == 3 && scan.firstRejectedKey() == 45;
        assert linkedRangeScan(tree, 46, 99).keys().isEmpty();
    }

    private static void secondaryLookupPlanScenarios() {
        List<Integer> matches = List.of(901, 905, 912);
        SecondaryLookupPlan nonCovering = secondaryLookupPlan(matches, false);
        assert nonCovering.primaryKeys().equals(matches);
        assert nonCovering.clusteredLookups() == 3 && !nonCovering.covering();

        SecondaryLookupPlan covering = secondaryLookupPlan(matches, true);
        assert covering.clusteredLookups() == 0 && covering.covering();
    }

    private static void indexOnlyVisibilityScenarios() {
        IndexOnlyVisibilityPlan eligible = indexOnlyVisibilityPlan(true, true, false, true);
        assert eligible.physicallyEligible() && eligible.heapFetches() == 1;

        IndexOnlyVisibilityPlan missingColumn = indexOnlyVisibilityPlan(false, true, true, true);
        assert !missingColumn.physicallyEligible() && missingColumn.heapFetches() == 3;
        assert indexOnlyVisibilityPlan(true, true, true).heapFetches() == 0;
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

    private static void lsmThreePlaceVisibilityScenarios() {
        List<LsmCandidate> p7 = List.of(
                new LsmCandidate("mutable memtable", new LsmVersion(30, null, true)),
                new LsmCandidate("SST-B", new LsmVersion(20, "120", false)),
                new LsmCandidate("SST-A", new LsmVersion(10, "100", false)));

        LsmVisibilityTrace latest = visibleAcrossPlaces(p7, 30);
        assert !latest.read().found() && latest.read().sequence() == 30;
        assert latest.placesChecked().equals(List.of("mutable memtable", "SST-B", "SST-A"));

        LsmVisibilityTrace at25 = visibleAcrossPlaces(p7, 25);
        assert at25.read().found() && at25.read().value().equals("120");
        assert at25.read().sequence() == 20;

        LsmVisibilityTrace at15 = visibleAcrossPlaces(p7, 15);
        assert at15.read().found() && at15.read().value().equals("100");
        assert at15.read().sequence() == 10;
    }

    private static void lsmCompactionMergeSafetyScenarios() {
        List<LsmVersion> p7 = List.of(
                new LsmVersion(30, null, true),
                new LsmVersion(20, "120", false),
                new LsmVersion(10, "100", false));

        LsmCompactionPlan snapshot15 = compactKeyHistory(p7, 15, true);
        assert snapshot15.retained().stream().map(LsmVersion::sequence).toList()
                .equals(List.of(30L, 20L, 10L));

        LsmCompactionPlan snapshot25 = compactKeyHistory(p7, 25, true);
        assert snapshot25.retained().stream().map(LsmVersion::sequence).toList()
                .equals(List.of(30L, 20L));

        LsmCompactionPlan bottommostNoSnapshots = compactKeyHistory(p7, 31, true);
        assert bottommostNoSnapshots.tombstoneDropped();
        assert bottommostNoSnapshots.retained().isEmpty();

        LsmCompactionPlan incompleteCoverage = compactKeyHistory(p7, 31, false);
        assert !incompleteCoverage.tombstoneDropped();
        assert incompleteCoverage.retained().get(0).tombstone();
    }

    private static void lsmBloomProbeBudgetScenarios() {
        BloomProbeBudget expected = bloomProbeBudget(
                8, 0.01,
                false, false, false, false, false, false, false, false);
        assert Math.abs(expected.expectedDataBlockProbes() - 0.08) < 0.000001;
        assert expected.observedDataBlockProbes() == 0;

        BloomProbeBudget oneFalsePositive = bloomProbeBudget(
                8, 0.01,
                false, false, true, false, false, false, false, false);
        assert oneFalsePositive.observedDataBlockProbes() == 1;
        assert oneFalsePositive.candidateFiles() == 8;
    }

    private static void lsmCompactionDebtBudgetScenarios() {
        CompactionDebtBudget overloaded = compactionDebtBudget(50, 6, 250, 60);
        assert overloaded.compactionDemandMbPerSecond() == 300;
        assert overloaded.debtMbPerSecond() == 50;
        assert overloaded.debtAfterWindowMb() == 3_000;
        assert overloaded.stallsExpected();

        CompactionDebtBudget sustainable = compactionDebtBudget(40, 4, 250, 60);
        assert sustainable.compactionDemandMbPerSecond() == 160;
        assert sustainable.debtMbPerSecond() == 0;
        assert !sustainable.stallsExpected();
    }

    private static void endpointCompositeIndexScenarios() {
        List<EndpointOrder> indexOrder = List.of(
                new EndpointOrder(7, "PAID", 300, 73, 9_900),
                new EndpointOrder(7, "PAID", 300, 72, 8_500),
                new EndpointOrder(7, "PAID", 290, 71, 7_200),
                new EndpointOrder(7, "PAID", 280, 70, 6_100),
                new EndpointOrder(7, "PENDING", 400, 90, 4_000),
                new EndpointOrder(8, "PAID", 500, 99, 5_000));

        EndpointPage page = endpointPage(indexOrder, 7, "PAID", null, 3);
        assert page.rows().stream().map(EndpointOrder::id).toList()
                .equals(List.of(73, 72, 71));
        assert page.matchingEntriesExamined() == 3;
        assert page.rows().stream().map(EndpointOrder::totalCents).toList()
                .equals(List.of(9_900L, 8_500L, 7_200L));
    }

    private static void endpointPlanShapeScenarios() {
        EndpointPlanChoice aligned = endpointPlanChoice(true, true, 30_000, 20);
        assert aligned.kind() == EndpointPlanKind.ORDERED_COMPOSITE_INDEX;
        assert aligned.candidateRows() == 20 && aligned.rowsSorted() == 0;
        assert aligned.preservesRequiredOrder();

        EndpointPlanChoice separate = endpointPlanChoice(false, true, 30_000, 20);
        assert separate.kind() == EndpointPlanKind.BITMAP_AND_THEN_SORT;
        assert separate.candidateRows() == 30_000 && separate.rowsSorted() == 30_000;
        assert !separate.preservesRequiredOrder();
    }

    private static void endpointPlanEvidenceScenarios() {
        List<String> findings = diagnoseEndpointPlan(new EndpointPlanEvidence(
                50, 30_000, 70_000, true, 18, 12, 190));
        assert findings.size() == 5;
        assert findings.get(0).startsWith("row-estimate skew");
        assert findings.get(4).startsWith("endpoint gap");

        assert diagnoseEndpointPlan(new EndpointPlanEvidence(
                20, 20, 0, false, 0, 4, 5)).isEmpty();
    }

    private static void endpointKeysetPaginationScenarios() {
        List<EndpointOrder> indexOrder = List.of(
                new EndpointOrder(7, "PAID", 300, 73, 9_900),
                new EndpointOrder(7, "PAID", 300, 72, 8_500),
                new EndpointOrder(7, "PAID", 300, 71, 7_200),
                new EndpointOrder(7, "PAID", 290, 70, 6_100));

        EndpointPage first = endpointPage(indexOrder, 7, "PAID", null, 2);
        EndpointOrder last = first.rows().get(first.rows().size() - 1);
        EndpointCursor cursor = new EndpointCursor(
                last.tenantId(), last.status(), last.createdAt(), last.id());
        EndpointPage second = endpointPage(indexOrder, 7, "PAID", cursor, 2);
        assert first.rows().stream().map(EndpointOrder::id).toList().equals(List.of(73, 72));
        assert second.rows().stream().map(EndpointOrder::id).toList().equals(List.of(71, 70));

        try {
            endpointPage(indexOrder, 8, "PAID", cursor, 2);
            throw new AssertionError("cross-tenant cursor accepted");
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

    private static void nonRepeatableReadLabScenarios() {
        PriceReadTrace readCommitted = twoSessionPriceTrace(
                LabIsolation.READ_COMMITTED, 100, 120);
        assert readCommitted.firstRead() == 100 && readCommitted.secondRead() == 120;
        assert readCommitted.firstSnapshot() != readCommitted.secondSnapshot();
        assert readCommitted.nonRepeatableRead();

        PriceReadTrace repeatableRead = twoSessionPriceTrace(
                LabIsolation.REPEATABLE_READ, 100, 120);
        assert repeatableRead.firstRead() == 100 && repeatableRead.secondRead() == 100;
        assert repeatableRead.firstSnapshot() == repeatableRead.secondSnapshot();
        assert !repeatableRead.nonRepeatableRead();
    }

    private static void writeSkewRetryLabScenarios() {
        OnCallSchedule writeSkew = onCallSchedule(IsolationHarness.REPEATABLE_READ, false);
        assert writeSkew.committedTransactions() == 2;
        assert !writeSkew.invariantPreserved();

        OnCallSchedule retried = onCallSchedule(IsolationHarness.SERIALIZABLE, true);
        assert retried.serializationFailures() == 1;
        assert retried.decisionEvaluations() == 3;
        assert retried.invariantPreserved();
        assert retryAfterRollback("40001", true, 1, 3)
                == TransactionRetryAction.RETRY_WHOLE_TRANSACTION;
    }

    private static void guardRowLabScenarios() {
        GuardedRosterOutcome guarded = guardedOffCall(List.of("A", "B"));
        assert guarded.guardAcquisitions() == 2;
        assert guarded.acceptedOffCall().equals(List.of("A"));
        assert !guarded.aliceOnCall() && guarded.bobOnCall();
        assert guarded.onCallCount() == 1;
    }

    private static void deadlockOrderingLabScenarios() {
        assert oppositeOrderCreatesWaitCycle(List.of(10, 20), List.of(20, 10));
        TransferLockPlan a = canonicalTransferLocks(10, 20);
        TransferLockPlan b = canonicalTransferLocks(20, 10);
        assert a.accountOrder().equals(List.of(10, 20));
        assert a.equals(b);
        assert !oppositeOrderCreatesWaitCycle(a.accountOrder(), b.accountOrder());
        assert retryAfterRollback("40P01", true, 1, 3)
                == TransactionRetryAction.RETRY_WHOLE_TRANSACTION;
        assert retryAfterRollback("40P01", false, 1, 3)
                == TransactionRetryAction.STOP;
        assert retryAfterRollback("23505", true, 1, 3)
                == TransactionRetryAction.STOP;
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

    private static void evictionPolicyTraceScenarios() {
        ExactLruCache lru = new ExactLruCache(3);
        FifoCache fifo = new FifoCache(3);
        for (String key : List.of("A", "B", "C")) {
            lru.put(key, 1);
            fifo.put(key, 1);
        }

        assert lru.get("A") == 1;
        assert fifo.get("A") == 1;
        assert lru.orderLeastToMostRecent().equals(List.of("B", "C", "A"));
        assert fifo.orderOldestToNewest().equals(List.of("A", "B", "C"));

        assert lru.put("D", 1).equals("B");
        assert fifo.put("D", 1).equals("A");
        assert lru.put("B", 1).equals("C");
        assert fifo.get("B") == 1;
        assert lru.orderLeastToMostRecent().equals(List.of("A", "D", "B"));
        assert fifo.orderOldestToNewest().equals(List.of("B", "C", "D"));
    }

    private static void lfuPhaseChangeLabScenarios() {
        ExactLfuModel trace = new ExactLfuModel(3);
        for (String key : List.of("A", "B", "C")) trace.put(key, 1);
        trace.get("A");
        assert trace.put("D", 1).equals("B"); // B and C tie; B is least recent
        assert trace.put("B", 1).equals("C"); // C and D tie; C is least recent
        assert trace.contains("A") && trace.contains("D") && trace.contains("B");

        ExactLfuModel sticky = new ExactLfuModel(3);
        for (String key : List.of("A", "B", "D")) sticky.put(key, 1);
        for (int i = 0; i < 10_000; i++) sticky.get("D");
        for (int i = 0; i < 100; i++) sticky.get("A");
        for (int i = 0; i < 100; i++) sticky.get("B");
        sticky.put("E", 1);
        assert sticky.contains("D"); // an inactive historical winner still pins a slot

        ExactLfuModel aged = new ExactLfuModel(3);
        for (String key : List.of("A", "B", "D")) aged.put(key, 1);
        for (int i = 0; i < 10_000; i++) aged.get("D");
        for (int i = 0; i < 100; i++) aged.get("A");
        for (int i = 0; i < 100; i++) aged.get("B");
        for (int i = 0; i < 14; i++) aged.decay();
        assert aged.put("E", 1).equals("D"); // all counters reached one; LRU tie breaks
    }

    private static void twoQueueGhostLabScenarios() {
        FullTwoQueueModel cache = new FullTwoQueueModel(1, 2, 2);
        assert cache.request("A").hit() == TwoQHit.NEW;
        assert cache.a1inOrder().equals(List.of("A"));

        assert cache.request("B").evictedResident().equals("A");
        assert cache.a1inOrder().equals(List.of("B"));
        assert cache.ghostOrder().equals(List.of("A"));

        TwoQResult reload = cache.request("A");
        assert reload.hit() == TwoQHit.GHOST_HIT;
        assert cache.a1inOrder().equals(List.of("B"));
        assert cache.ghostOrder().isEmpty();
        assert cache.amLeastToMostRecent().equals(List.of("A"));

        cache.request("C");
        assert cache.a1inOrder().equals(List.of("C"));
        assert cache.ghostOrder().equals(List.of("B"));
        cache.request("D");
        assert cache.a1inOrder().equals(List.of("D"));
        assert cache.ghostOrder().equals(List.of("B", "C"));
        assert cache.request("A").hit() == TwoQHit.AM_HIT;
        assert cache.amLeastToMostRecent().equals(List.of("A"));
    }

    private static void cmsScanPollutionLabScenarios() {
        ScanPollutionResult shared = searchRebuildScan(ScanAdmission.SHARED_LRU);
        assert shared.postScanResidents().equals(List.of("S2", "S3", "S4"));
        assert shared.hotMissesAfterScan() == 3;

        ScanPollutionResult bypass = searchRebuildScan(ScanAdmission.BYPASS_ONE_PASS);
        assert bypass.postScanResidents().equals(List.of("H1", "H2", "H3"));
        assert bypass.hotMissesAfterScan() == 0;
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

    private static void jdbcDriverSelectionScenarios() {
        assert jdbcDriverForUrl("jdbc:postgresql://db.example/app")
                == JdbcDriverKind.POSTGRESQL;
        assert jdbcDriverForUrl("jdbc:mysql://db.example/app") == JdbcDriverKind.MYSQL;
        assert jdbcDriverForUrl("jdbc:mysql:replication://primary,replica/app")
                == JdbcDriverKind.MYSQL;
        assert jdbcDriverForUrl("https://db.example/app") == JdbcDriverKind.UNSUPPORTED;
    }

    private static void dataSourceAcquisitionScenarios() {
        assert acquisitionPath(DataSourceKind.BASIC_VENDOR, 0, 0, 8)
                == AcquisitionPath.FRESH_PHYSICAL;
        assert acquisitionPath(DataSourceKind.HIKARI_POOL, 3, 5, 8)
                == AcquisitionPath.BORROW_IDLE;
        assert acquisitionPath(DataSourceKind.HIKARI_POOL, 0, 5, 8)
                == AcquisitionPath.GROW_PHYSICAL;
        assert acquisitionPath(DataSourceKind.HIKARI_POOL, 0, 8, 8)
                == AcquisitionPath.WAIT_OR_TIMEOUT;
    }

    private static void jdbcLayerStackScenarios() {
        JdbcLayerStack postgres = jdbcLayerStack("jdbc:postgresql://db.example/app");
        assert postgres.jdbcApi().equals("JDBC");
        assert postgres.wireProtocol().equals("PostgreSQL frontend/backend v3");
        assert !postgres.httpByDefault();

        JdbcLayerStack mysql = jdbcLayerStack("jdbc:mysql://db.example/app");
        assert mysql.wireProtocol().equals("MySQL classic protocol");
        assert mysql.defaultTransport().equals("TCP");
        assert !mysql.httpByDefault();
    }

    private static void postgresHandshakeVariantScenarios() {
        PgHandshakePlan traditional = pgHandshakePlan(
                PgTransport.TCP,
                PgEncryption.SSL_REQUEST_TLS,
                PgAuthentication.SCRAM_SHA_256,
                false);
        assert traditional.sendsSslRequest();
        assert traditional.performsTlsHandshake();
        assert traditional.performsScramExchange();
        assert traditional.phases().get(1).equals("SSLRequest and one-byte response");

        PgHandshakePlan direct = pgHandshakePlan(
                PgTransport.TCP,
                PgEncryption.DIRECT_TLS,
                PgAuthentication.CLIENT_CERTIFICATE,
                true);
        assert !direct.sendsSslRequest();
        assert direct.performsTlsHandshake();
        assert !direct.performsScramExchange();

        PgHandshakePlan socket = pgHandshakePlan(
                PgTransport.UNIX_DOMAIN_SOCKET,
                PgEncryption.PLAINTEXT,
                PgAuthentication.SCRAM_SHA_256,
                false);
        assert socket.phases().get(0).equals("Unix socket connect");
        assert !socket.performsTlsHandshake();
    }

    private static void postgresChronologyScenarios() {
        PgStartupState state = PgStartupState.SOCKET_CREATED;
        state = advancePgStartup(state, PgStartupState.TCP_CONNECTED);
        state = advancePgStartup(state, PgStartupState.TLS_NEGOTIATED);
        state = advancePgStartup(state, PgStartupState.STARTUP_SENT);
        state = advancePgStartup(state, PgStartupState.AUTHENTICATED);
        state = advancePgStartup(state, PgStartupState.READY_FOR_QUERY);
        state = advancePgStartup(state, PgStartupState.POOL_USABLE);
        assert state == PgStartupState.POOL_USABLE;
        try {
            advancePgStartup(PgStartupState.TLS_NEGOTIATED, PgStartupState.AUTHENTICATED);
            throw new AssertionError("startup skipped StartupMessage");
        } catch (IllegalStateException expected) {}
    }

    private static void postgresFramingScenarios() {
        byte[] authenticationOk = typedPgMessage('R', new byte[] {0, 0, 0, 0});
        byte[] ready = typedPgMessage('Z', (byte) 'I');
        byte[] both = new byte[authenticationOk.length + ready.length];
        System.arraycopy(authenticationOk, 0, both, 0, authenticationOk.length);
        System.arraycopy(ready, 0, both, authenticationOk.length, ready.length);

        List<PgFrame> oneRead = decodeTypedPgMessages(List.of(both));
        List<PgFrame> threeReads = decodeTypedPgMessages(List.of(
                Arrays.copyOfRange(both, 0, 3),
                Arrays.copyOfRange(both, 3, 11),
                Arrays.copyOfRange(both, 11, both.length)));
        assert oneRead.equals(threeReads);
        assert oneRead.equals(List.of(new PgFrame('R', 8, 9), new PgFrame('Z', 5, 6)));
        try {
            decodeTypedPgMessages(List.of(Arrays.copyOf(both, both.length - 1)));
            throw new AssertionError("partial protocol message accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void postgresIdentityScenarios() {
        StartupAssurance encryptedOnly = startupAssurance(
                true, false, false, false, false, false);
        assert encryptedOnly.encrypted() && !encryptedOnly.serverIdentityVerified();

        StartupAssurance verified = startupAssurance(
                true, true, true, true, true, true);
        assert verified.serverIdentityVerified();
        assert verified.databaseUserAuthenticated();
        assert verified.commandReady();
        try {
            startupAssurance(true, true, true, false, false, true);
            throw new AssertionError("ReadyForQuery preceded authentication");
        } catch (IllegalArgumentException expected) {}
    }

    private static void poolTraceCorrelationScenarios() {
        ConnectionTracePlan warm = connectionTracePlan(3, 5, 8);
        assert warm.acquisitionPath() == AcquisitionPath.BORROW_IDLE;
        assert !warm.physicalHandshake();
        assert warm.correlationEvents().equals(List.of("checkout", "lease returned"));

        ConnectionTracePlan growth = connectionTracePlan(0, 5, 8);
        assert growth.acquisitionPath() == AcquisitionPath.GROW_PHYSICAL;
        assert growth.physicalHandshake();
        assert growth.correlationEvents().contains("ReadyForQuery");

        ConnectionTracePlan full = connectionTracePlan(0, 8, 8);
        assert full.acquisitionPath() == AcquisitionPath.WAIT_OR_TIMEOUT;
        assert !full.physicalHandshake();
    }

    private static void postgresSocketAttemptScenarios() {
        PgTimeouts timeouts = new PgTimeouts(3, 30, 5);
        assert timeouts.forOperation(PgIoOperation.CONNECT) == 3;
        assert timeouts.forOperation(PgIoOperation.READ) == 30;
        assert timeouts.forOperation(PgIoOperation.CANCEL) == 5;

        AddressAttemptTrace fallback = attemptAddresses(
                List.of("2001:db8::10", "192.0.2.10"),
                List.of(AddressOutcome.TIMEOUT, AddressOutcome.CONNECTED));
        assert fallback.terminal() == ConnectTerminal.CONNECTED;
        assert fallback.selectedAddress().equals("192.0.2.10");
        assert fallback.attemptedAddresses() == 2;

        assert attemptAddresses(List.of(), List.of()).terminal() == ConnectTerminal.DNS_EMPTY;
        assert attemptAddresses(
                List.of("192.0.2.20"), List.of(AddressOutcome.REFUSED)).terminal()
                == ConnectTerminal.ADDRESSES_EXHAUSTED;
    }

    private static void tlsPolicyScenarios() {
        TlsEvidence encryptedOnly = new TlsEvidence(true, false, false, false, false);
        assert tlsAdmission(PgSslMode.REQUIRE, encryptedOnly)
                == TlsAdmission.ENCRYPTED_UNVERIFIED;
        assert tlsAdmission(PgSslMode.VERIFY_CA, encryptedOnly) == TlsAdmission.REJECT;

        TlsEvidence trustedWrongName = new TlsEvidence(true, true, false, false, false);
        assert tlsAdmission(PgSslMode.VERIFY_CA, trustedWrongName) == TlsAdmission.TRUSTED_CA;
        assert tlsAdmission(PgSslMode.VERIFY_FULL, trustedWrongName) == TlsAdmission.REJECT;

        TlsEvidence resumedMutual = new TlsEvidence(true, true, true, true, true);
        assert tlsAdmission(PgSslMode.VERIFY_FULL, resumedMutual)
                == TlsAdmission.VERIFIED_ENDPOINT;
        assert resumedMutual.resumed() && resumedMutual.clientCertificatePresented();
    }

    private static void scramNegotiationScenarios() {
        Set<String> both = Set.of("SCRAM-SHA-256", "SCRAM-SHA-256-PLUS");
        assert selectScramMechanism(both, ChannelBindingPolicy.REQUIRE, true)
                == ScramMechanism.SCRAM_SHA_256_PLUS;
        assert selectScramMechanism(both, ChannelBindingPolicy.PREFER, false)
                == ScramMechanism.SCRAM_SHA_256;
        assert selectScramMechanism(
                Set.of("SCRAM-SHA-256"), ChannelBindingPolicy.REQUIRE, true)
                == ScramMechanism.REJECT;

        ScramCompletion complete = completeScram(true, true, true);
        assert complete.authenticated();
        assert !completeScram(true, false, true).authenticated();
    }

    private static void sessionBaselineScenarios() {
        assert baselinePlacement(
                PoolingMode.SESSION, SessionNeed.STABLE_DEFAULT, true)
                == BaselinePlacement.ROLE_OR_DATABASE_DEFAULT;
        assert baselinePlacement(
                PoolingMode.SESSION, SessionNeed.CUSTOM_SESSION_STATE, false)
                == BaselinePlacement.PHYSICAL_INIT_SQL;
        assert baselinePlacement(
                PoolingMode.TRANSACTION, SessionNeed.PER_TRANSACTION, false)
                == BaselinePlacement.TRANSACTION_LOCAL;
        assert baselinePlacement(
                PoolingMode.TRANSACTION, SessionNeed.TEMPORARY_OBJECT, false)
                == BaselinePlacement.REJECT;
        assert baselinePlacement(
                PoolingMode.TRANSACTION, SessionNeed.STABLE_DEFAULT, true)
                == BaselinePlacement.ROLE_OR_DATABASE_DEFAULT;
    }

    private static void mysqlHandshakeOrderScenarios() {
        Set<MySqlCapability> modern = Set.of(
                MySqlCapability.PROTOCOL_41,
                MySqlCapability.SECURE_CONNECTION,
                MySqlCapability.PLUGIN_AUTH,
                MySqlCapability.SSL);
        MySqlHandshakePlan secure = mysqlHandshakePlan(modern, modern, true, true);
        assert secure.tls() && secure.identityVerified();
        assert secure.events().indexOf("server HandshakeV10 greeting")
                < secure.events().indexOf("client SSLRequest with CLIENT_SSL");
        assert secure.events().indexOf("TLS established")
                < secure.events().indexOf("client HandshakeResponse");

        MySqlHandshakePlan plaintext = mysqlHandshakePlan(
                Set.of(MySqlCapability.PROTOCOL_41, MySqlCapability.PLUGIN_AUTH),
                modern,
                false,
                false);
        assert !plaintext.tls();
        try {
            mysqlHandshakePlan(plaintext.negotiatedCapabilities(), modern, true, false);
            throw new AssertionError("required TLS silently downgraded");
        } catch (IllegalStateException expected) {}
    }

    private static void mysqlPluginNegotiationScenarios() {
        MySqlAuthNegotiation guessed = mysqlAuthNegotiation(
                "caching_sha2_password", "caching_sha2_password");
        assert guessed.path() == MySqlAuthPath.OPTIMISTIC_PLUGIN;
        assert !guessed.messages().contains("AuthSwitchRequest");

        MySqlAuthNegotiation switched = mysqlAuthNegotiation(
                "caching_sha2_password", "authentication_ldap_sasl");
        assert switched.path() == MySqlAuthPath.AUTH_SWITCH;
        assert switched.messages().equals(List.of(
                "HandshakeResponse", "AuthSwitchRequest", "AuthSwitchResponse",
                "AuthMoreData or OK"));
    }

    private static void cachingSha2Scenarios() {
        assert cachingSha2Outcome(false, Sha2FullAuthChannel.VERIFIED_TLS, false)
                == CachingSha2Outcome.FULL_OVER_TLS;
        assert cachingSha2Outcome(false, Sha2FullAuthChannel.PINNED_RSA_PUBLIC_KEY, false)
                == CachingSha2Outcome.FULL_WITH_PINNED_RSA;
        assert cachingSha2Outcome(false, Sha2FullAuthChannel.RETRIEVED_RSA_PUBLIC_KEY, false)
                == CachingSha2Outcome.REJECT;
        assert cachingSha2Outcome(false, Sha2FullAuthChannel.RETRIEVED_RSA_PUBLIC_KEY, true)
                == CachingSha2Outcome.FULL_WITH_RETRIEVED_RSA;
        assert cachingSha2Outcome(true, Sha2FullAuthChannel.NONE, false)
                == CachingSha2Outcome.FAST_CHALLENGE_RESPONSE;

        assert sha2CacheMayRemainWarm(Sha2CacheEvent.ORDINARY_RECONNECT);
        assert !sha2CacheMayRemainWarm(Sha2CacheEvent.PASSWORD_CHANGE);
        assert !sha2CacheMayRemainWarm(Sha2CacheEvent.FLUSH_PRIVILEGES);
        assert !sha2CacheMayRemainWarm(Sha2CacheEvent.RESTART);
    }

    private static void mysqlSessionContractScenarios() {
        MySqlSessionContract expected = new MySqlSessionContract(
                "utf8mb4", "utf8mb4_0900_ai_ci", "STRICT_TRANS_TABLES",
                "UTC", "READ_COMMITTED");
        MySqlSessionContract same = new MySqlSessionContract(
                "utf8mb4", "utf8mb4_0900_ai_ci", "STRICT_TRANS_TABLES",
                "UTC", "READ_COMMITTED");
        assert mysqlSessionMismatches(expected, same).isEmpty();

        // Blank expectations are configuration errors, not implicit defaults.
        try {
            new MySqlSessionContract(
                    "latin1", "latin1_swedish_ci", "", "SYSTEM", "REPEATABLE_READ");
            throw new AssertionError("blank session contract accepted");
        } catch (IllegalArgumentException expectedFailure) {}

        MySqlSessionContract observableDrift = new MySqlSessionContract(
                "utf8mb4", "utf8mb4_general_ci", "STRICT_TRANS_TABLES",
                "America/New_York", "REPEATABLE_READ");
        assert mysqlSessionMismatches(expected, observableDrift).equals(
                List.of("collation", "timeZone", "isolation"));
    }

    private static void pgQueryModeScenarios() {
        assert pgQueryMode(false, 0, 5, false) == PgQueryMode.SIMPLE_QUERY;
        assert pgQueryMode(true, 0, 5, false) == PgQueryMode.UNNAMED_EXTENDED;
        assert pgQueryMode(true, 3, 5, false) == PgQueryMode.UNNAMED_EXTENDED;
        assert pgQueryMode(true, 4, 5, false) == PgQueryMode.NAMED_PREPARED;
        assert pgQueryMode(true, 99, 5, true) == PgQueryMode.SIMPLE_QUERY;
        assert pgQueryMode(true, 99, 0, false) == PgQueryMode.UNNAMED_EXTENDED;
    }

    private static void extendedQueryCycleScenarios() {
        ExtendedQueryCycle first = extendedQueryCycle(false, true, 0);
        assert first.frontendMessages().equals(List.of(
                "Parse", "Bind", "Describe portal", "Execute(maxRows=0)", "Sync"));
        assert first.possibleExecuteTerminals().equals(List.of(
                "CommandComplete", "EmptyQueryResponse", "ErrorResponse"));
        assert first.readyForQueryAfterSync();

        ExtendedQueryCycle pagedReuse = extendedQueryCycle(true, false, 50);
        assert !pagedReuse.frontendMessages().contains("Parse");
        assert pagedReuse.possibleExecuteTerminals().contains("PortalSuspended");
    }

    private static void preparedBoundaryScenarios() {
        String safe = safeOrderQuery("total_cents", true);
        assert safe.endsWith("ORDER BY total_cents DESC");
        assert safe.chars().filter(ch -> ch == '?').count() == 2;
        try {
            safeOrderQuery("total_cents DESC; DROP TABLE orders; --", true);
            throw new AssertionError("unapproved identifier entered SQL text");
        } catch (IllegalArgumentException expected) {}
    }

    private static void fetchAndLifecycleScenarios() {
        FetchPlan buffered = fetchPlan(true, true, 0, 10_000, 200, true);
        assert buffered.mode() == RetrievalMode.BUFFER_ALL_DEFAULT;
        assert buffered.estimatedPeakPayloadBytes() == 2_000_000;
        assert buffered.executeCycles() == 1;

        FetchPlan cursor = fetchPlan(false, true, 250, 10_000, 200, true);
        assert cursor.mode() == RetrievalMode.CURSOR_BATCHED;
        assert cursor.estimatedPeakPayloadBytes() == 50_000;
        assert cursor.executeCycles() == 40;
        assert cursor.cleanupEvents().equals(List.of(
                "close ResultSet", "close PreparedStatement", "commit transaction",
                "close pooled Connection lease"));

        FetchPlan rollback = fetchPlan(false, true, 250, 400, 200, false);
        assert rollback.cleanupEvents().contains("rollback transaction");
        assert fetchPlan(true, true, 250, 400, 200, true).mode()
                == RetrievalMode.BUFFER_ALL_CURSOR_INELIGIBLE;
    }

    private static void revisionKeyScenarios() {
        RevisionKeys keys = revisionKeys(new RevisionIdentity("tenant-a", "article-42", 7));
        assert keys.body().equals("cms:revision:{tenant-a|article-42|7}:body");
        assert keys.absent().contains("{tenant-a|article-42|7}");
        assert keys.lease().contains("{tenant-a|article-42|7}");
        assert !revisionKeys(new RevisionIdentity("tenant-b", "article-42", 7))
                .body().equals(keys.body());
        try {
            new RevisionIdentity("tenant:{escape}", "article-42", 7);
            throw new AssertionError("unsafe key component accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void revisionReadContractScenarios() {
        RevisionReadDecision denied = revisionReadDecision(
                false, true, false, false, true, true, true);
        assert denied.outcome() == RevisionReadOutcome.DENIED;
        assert denied.events().equals(List.of(
                "authorize and verify publication status", "deny before cache access"));

        RevisionReadDecision strict = revisionReadDecision(
                true, false, true, true, true, true, true);
        assert strict.outcome() == RevisionReadOutcome.DATABASE_BODY;
        assert strict.events().contains("bypass negative entry for strict publication visibility");

        RevisionReadDecision overloaded = revisionReadDecision(
                true, false, false, false, false, false, true);
        assert overloaded.outcome() == RevisionReadOutcome.RETRYABLE_UNAVAILABLE;
        assert !overloaded.events().contains("query exact revision on primary");
    }

    private static void publicationVisibilityScenarios() {
        PublicationPlan strict = publicationPlan(true, true);
        assert strict.immediatelyVisible();
        assert strict.steps().indexOf("commit database transaction")
                < strict.steps().indexOf("delete exact revision negative key");
        assert strict.steps().contains("serve exact revision while bypassing negative cache");

        PublicationPlan bounded = publicationPlan(true, false);
        assert !bounded.immediatelyVisible();
        assert bounded.steps().get(bounded.steps().size() - 1)
                .equals("allow bounded negative TTL to expire");
    }

    private static void productionBudgetScenarios() {
        ProductionBudget configured = productionBudget(
                750, 500, 300, 150, 3_000, 3_000, 1_000, 100, 5_500);
        assert configured.steadyFallbackMillis() == 2_150;
        assert configured.coldFallbackMillis() == 5_150;
        assert configured.validationBelowCheckout();
        assert configured.socketOutlivesStatement();
        assert configured.callerCoversColdFallback();

        ProductionBudget unsafe = productionBudget(
                750, 900, 300, 150, 3_000, 900, 1_000, 100, 2_200);
        assert !unsafe.validationBelowCheckout();
        assert !unsafe.socketOutlivesStatement();
        assert unsafe.callerCoversSteadyFallback();
        assert !unsafe.callerCoversColdFallback();
    }

    private static void cacheEnvelopeScenarios() {
        assert decodeEnvelope(null).kind() == EnvelopeKind.MISS;
        assert decodeEnvelope("n1").kind() == EnvelopeKind.ABSENT;
        DecodedEnvelope emptyBody = decodeEnvelope(encodeBodyEnvelope(""));
        assert emptyBody.kind() == EnvelopeKind.BODY;
        assert emptyBody.body().isEmpty();
        DecodedEnvelope article = decodeEnvelope(encodeBodyEnvelope("revision body"));
        assert article.body().equals("revision body");
        assert decodeEnvelope("v2:future-format").kind() == EnvelopeKind.UNKNOWN;
    }

    private static void ownerCheckedPublicationScenarios() {
        OwnerCheckedCache cache = new OwnerCheckedCache();
        assert cache.acquire("owner-a");
        cache.expireLease();
        assert cache.acquire("owner-b");
        assert !cache.publish("owner-a", encodeBodyEnvelope("stale"));
        assert !cache.release("owner-a");
        assert cache.leaseOwner().equals("owner-b");
        assert cache.value() == null;
        assert cache.publish("owner-b", encodeBodyEnvelope("fresh"));
        assert cache.leaseOwner() == null;
        assert decodeEnvelope(cache.value()).body().equals("fresh");
    }

    private static void cacheAsideCoordinatorScenarios() {
        CacheAsideDecision owner = cacheAsideDecision(
                false, true, false, List.of(), true);
        assert owner.outcome() == CacheAsideOutcome.OWNER_DATABASE_BODY;
        assert owner.databaseRead() && owner.publishAttempted();

        CacheAsideDecision secondHit = cacheAsideDecision(
                false, true, true, List.of(), false);
        assert secondHit.outcome() == CacheAsideOutcome.SECOND_LOOKUP_HIT;
        assert !secondHit.databaseRead() && !secondHit.publishAttempted();

        CacheAsideDecision waiterHit = cacheAsideDecision(
                false, false, false, List.of(false, true), false);
        assert waiterHit.outcome() == CacheAsideOutcome.WAITER_CACHE_HIT;
        assert waiterHit.polls() == 2 && !waiterHit.databaseRead();

        CacheAsideDecision boundedFallback = cacheAsideDecision(
                false, false, false, List.of(false, false, false), true);
        assert boundedFallback.outcome()
                == CacheAsideOutcome.WAITER_DATABASE_BODY_NO_PUBLISH;
        assert boundedFallback.databaseRead() && !boundedFallback.publishAttempted();
    }

    private static void cooldownAndBulkheadScenarios() {
        long cooldownUntil = redisCooldownUntil(10_000, 2_000);
        CacheAdmission cooling = cacheAdmission(11_500, cooldownUntil, true, true);
        assert cooling.outcome() == DegradedAdmission.DATABASE_FALLBACK;
        assert !cooling.redisAttempted() && cooling.databasePermitAcquired();

        CacheAdmission recovered = cacheAdmission(12_000, cooldownUntil, true, false);
        assert recovered.outcome() == DegradedAdmission.CACHE_LOOKUP;
        assert recovered.redisAttempted() && !recovered.databasePermitAcquired();

        CacheAdmission saturated = cacheAdmission(12_000, cooldownUntil, false, false);
        assert saturated.outcome() == DegradedAdmission.REJECTED;
        assert saturated.redisAttempted() && !saturated.databasePermitAcquired();
    }

    private static void mutableRowOutboxTransactionScenarios() {
        MutableDocument before = new MutableDocument("article-42", 7, "old");
        CommittedMutation committed = mutateWithOutbox(
                before, 7, "new", "event-8", false);
        assert committed.document().version() == 8;
        assert committed.event().version() == 8;
        assert committed.event().aggregateId().equals(before.id());

        try {
            mutateWithOutbox(before, 7, "not committed", "event-fail", true);
            throw new AssertionError("injected transaction failure did not fire");
        } catch (InjectedFailure expected) {
            assert before.version() == 7 && before.body().equals("old");
        }
        try {
            mutateWithOutbox(before, 6, "lost update", "event-conflict", false);
            throw new AssertionError("stale expected version accepted");
        } catch (IllegalStateException expected) {}
    }

    private static void durableOutboxRelayScenarios() {
        DurableOutbox outbox = new DurableOutbox();
        outbox.append(new MutationEvent("e8", "article-42", 8, "v8"));
        outbox.append(new MutationEvent("e9", "article-42", 9, "v9"));

        assert outbox.claim("worker-a", 1).stream()
                .map(MutationEvent::eventId).toList().equals(List.of("e8"));
        assert outbox.claim("worker-b", 2).stream()
                .map(MutationEvent::eventId).toList().equals(List.of("e9"));
        assert !outbox.acknowledge("worker-b", "e8");
        assert outbox.recoverClaims("worker-a") == 1;
        assert outbox.claim("worker-c", 1).get(0).eventId().equals("e8");
        assert outbox.acknowledge("worker-c", "e8");
        assert outbox.state("e8") == DeliveryState.DELIVERED;
    }

    private static void monotonicCacheEventScenarios() {
        VersionedCacheGeneration cache = new VersionedCacheGeneration();
        assert cache.publishLoaded(7, "v7");
        MutationEvent v9 = new MutationEvent("e9", "article-42", 9, "v9");
        assert cache.apply(v9) == EventApplication.APPLIED;
        assert cache.generation() == 9 && cache.cachedBody() == null;
        assert cache.apply(v9) == EventApplication.DUPLICATE;

        MutationEvent stale = new MutationEvent("e8", "article-42", 8, "v8");
        assert cache.apply(stale) == EventApplication.STALE;
        assert cache.generation() == 9;
        assert cache.publishLoaded(9, "v9");
        assert cache.cachedVersion() == 9 && cache.cachedBody().equals("v9");
    }

    private static void staleReaderFenceScenarios() {
        VersionedCacheGeneration cache = new VersionedCacheGeneration();
        assert cache.publishLoaded(7, "loaded before update");
        assert cache.apply(new MutationEvent("e8", "article-42", 8, "v8"))
                == EventApplication.APPLIED;
        assert !cache.publishLoaded(7, "late stale publication");
        assert cache.cachedBody() == null;
        assert cache.publishLoaded(8, "current publication");

        assert mutableReadPath(false) == MutableReadPath.BYPASS_CACHE;
        assert mutableReadPath(true) == MutableReadPath.VERSION_VALIDATED_CACHE;
    }

    private static void faultOracleScenarios() {
        FaultOracle lease = faultOracle(FaultProbe.LUA_OWNER_CHECK);
        assert lease.layer() == RequiredTestLayer.REAL_REDIS;
        assert lease.requiredObservation().contains("B remains owner");
        assert lease.forbiddenObservation().contains("stale owner");

        FaultOracle cluster = faultOracle(FaultProbe.REDIS_CLUSTER_SLOT);
        assert cluster.layer() == RequiredTestLayer.REAL_REDIS_CLUSTER;
        assert cluster.injectedFault().contains("mismatched hash tags");

        FaultOracle serialization = faultOracle(FaultProbe.POSTGRES_SERIALIZATION);
        assert serialization.layer() == RequiredTestLayer.REAL_POSTGRESQL;
        assert serialization.forbiddenObservation().contains("final UPDATE");

        assert faultOracle(FaultProbe.TLS_WRONG_HOST).layer()
                == RequiredTestLayer.REAL_TLS_ENDPOINT;
        assert faultOracle(FaultProbe.POOL_RESOURCE_RETURN).layer()
                == RequiredTestLayer.REAL_POOL_AND_DATABASE;
    }

    private static void leaseFaultInjectionScenarios() {
        LeaseFaultTrace trace = leaseExpiryFaultTrace();
        assert trace.stalePublishRejected();
        assert trace.staleCleanupRejected();
        assert trace.successorOwnerBeforePublish().equals("owner-b");
        assert trace.finalValue().equals("fresh");
    }

    private static void writeSkewHarnessScenarios() {
        OnCallSchedule repeatable = onCallSchedule(IsolationHarness.REPEATABLE_READ, false);
        assert repeatable.committedTransactions() == 2;
        assert !repeatable.invariantPreserved();
        assert !repeatable.aliceOnCall() && !repeatable.bobOnCall();

        OnCallSchedule serializable = onCallSchedule(IsolationHarness.SERIALIZABLE, false);
        assert serializable.committedTransactions() == 1;
        assert serializable.serializationFailures() == 1;
        assert serializable.invariantPreserved();

        OnCallSchedule retried = onCallSchedule(IsolationHarness.SERIALIZABLE, true);
        assert retried.committedTransactions() == 2;
        assert retried.serializationFailures() == 1;
        assert retried.decisionEvaluations() == 3;
        assert retried.invariantPreserved();
        assert !retried.aliceOnCall() && retried.bobOnCall();
    }

    private static void unknownCommitRecoveryScenarios() {
        AtomicLedger ledger = new AtomicLedger(500, 200);
        IdempotentTransferService service = new IdempotentTransferService(ledger);
        try {
            service.transfer("op-fault-23", 100, true);
            throw new AssertionError("response loss was not injected");
        } catch (ResponseLost expected) {
            assert unknownCommitAction(service.status("op-fault-23") != null, true)
                    == UnknownCommitAction.RETURN_STORED_OUTCOME;
        }
        Receipt resolved = service.status("op-fault-23");
        Receipt retried = service.transfer("op-fault-23", 100, false);
        assert Objects.equals(resolved, retried);
        assert ledger.balance("A") == 400 && ledger.balance("B") == 300;
        assert unknownCommitAction(false, true) == UnknownCommitAction.RETRY_SAME_OPERATION_ID;
        assert unknownCommitAction(false, false) == UnknownCommitAction.STOP_AT_DEADLINE;

        assert decodeEnvelope("v2:future-format").kind() == EnvelopeKind.UNKNOWN;
        assert faultOracle(FaultProbe.CACHE_SCHEMA_ROLLOUT)
                .requiredObservation().contains("unknown format");
    }

    private static void databaseRecoveryRunbookScenarios() {
        DatabaseRecoveryPlan partition = databaseRecoveryPlan(
                DatabaseFailureKind.NETWORK_PARTITION, false, true, 4);
        assert partition.action() == DatabaseRecoveryAction.FENCE_BEFORE_FAILOVER;
        assert !partition.writesAllowed() && partition.reconnectBatchSize() == 0;

        DatabaseRecoveryPlan promoted = databaseRecoveryPlan(
                DatabaseFailureKind.PROCESS, true, true, 4);
        assert promoted.action() == DatabaseRecoveryAction.PROMOTE_ELIGIBLE_REPLICA;
        assert promoted.writesAllowed() && promoted.reconcileUnknownOutcomes();
        assert promoted.steps().indexOf("promote once")
                < promoted.steps().indexOf("open reconnect batches");

        DatabaseRecoveryPlan restored = databaseRecoveryPlan(
                DatabaseFailureKind.STORAGE, true, false, 2);
        assert restored.action() == DatabaseRecoveryAction.RESTORE_AND_REPLAY_WAL;
        assert !restored.writesAllowed();
    }

    private static void cacheRecoveryRunbookScenarios() {
        CacheRecoveryPlan warm = cacheRecoveryPlan(true, 0.12, 36, 40, 20, 6);
        assert warm.action() == CacheRecoveryAction.WARM_HOT_KEYS_WITH_BUDGET;
        assert warm.allowedWarmers() == 4;
        assert !warm.fullKeyspaceReloadAllowed();
        assert warm.signals().contains("pool pending threads");

        CacheRecoveryPlan saturated = cacheRecoveryPlan(true, 0.05, 40, 40, 20, 6);
        assert saturated.action() == CacheRecoveryAction.SHED_LOAD_TO_PROTECT_DATABASE;
        assert saturated.allowedWarmers() == 0;

        CacheRecoveryPlan disconnected = cacheRecoveryPlan(false, 0.90, 12, 40, 4, 4);
        assert disconnected.action() == CacheRecoveryAction.RECONNECT_AND_OBSERVE;
        assert disconnected.allowedWarmers() == 0;
    }

    private static void poolExhaustionRunbookScenarios() {
        PoolDiagnosis locks = diagnosePoolExhaustion(600, 900, true, false, false, 18, 40);
        assert locks.cause() == PoolExhaustionCause.DATABASE_LOCK_WAIT;
        assert !locks.increasePoolNow();

        PoolDiagnosis holder = diagnosePoolExhaustion(4_500, 800, false, true, false, 18, 40);
        assert holder.cause() == PoolExhaustionCause.LONG_CONNECTION_HOLD;

        PoolDiagnosis secondQueue = diagnosePoolExhaustion(250, 900, false, false, true, 18, 40);
        assert secondQueue.cause() == PoolExhaustionCause.PGBOUNCER_SECOND_QUEUE;

        PoolDiagnosis boundedChange = diagnosePoolExhaustion(250, 900, false, false, false, 18, 40);
        assert boundedChange.cause() == PoolExhaustionCause.POOL_CAPACITY_MISMATCH;
        assert boundedChange.increasePoolNow();
    }

    private static void certificateCredentialRotationScenarios() {
        RotationPlan hiddenFailure = rotationPlan(18, 10, 7, true);
        assert hiddenFailure.action() == RotationAction.ABORT_AND_RESTORE_OLD_MATERIAL;
        assert hiddenFailure.creationFailurePercent() == 30;
        assert !hiddenFailure.activeQueriesProveRotation();

        RotationPlan overlap = rotationPlan(18, 10, 10, true);
        assert overlap.action() == RotationAction.CONTINUE_OVERLAP_AND_CANARY;

        RotationPlan complete = rotationPlan(0, 50, 50, true);
        assert complete.action() == RotationAction.COMPLETE_AFTER_OLD_SESSIONS_DRAIN;

        RotationPlan noOverlap = rotationPlan(18, 1, 1, false);
        assert noOverlap.action() == RotationAction.ABORT_AND_RESTORE_OLD_MATERIAL;
    }

    private static void seniorVisibilityStorageReviewScenarios() {
        assert freshnessVerdict(SeniorReadPath.CACHE, 12, 11, 12, 12)
                == FreshnessVerdict.MAY_BE_STALE;
        assert freshnessVerdict(SeniorReadPath.REPLICA, 12, 12, 11, 12)
                == FreshnessVerdict.MAY_BE_STALE;
        assert freshnessVerdict(SeniorReadPath.PRIMARY, 12, 10, 10, 12)
                == FreshnessVerdict.FRESH_FOR_REQUIRED_VERSION;

        assert !lsmHasWriteHeadroom(80, 4.0, 300);
        assert lsmHasWriteHeadroom(60, 4.0, 300);

        assert !phantomPrevented(RepeatableReadCase.SQL_STANDARD_MINIMUM);
        assert phantomPrevented(RepeatableReadCase.POSTGRESQL_SNAPSHOT_READ);
        assert phantomPrevented(RepeatableReadCase.INNODB_LOCKING_RANGE_READ);
    }

    private static void seniorCacheSafetyReviewScenarios() {
        assert cacheMissRoute(120, 119, false, 3) == CacheMissRoute.QUERY_SQL_WITH_PERMIT;
        assert cacheMissRoute(120, 120, false, 3) == CacheMissRoute.TRUST_FILTER_NEGATIVE;
        assert cacheMissRoute(120, 120, true, 3) == CacheMissRoute.QUERY_SQL_WITH_PERMIT;
        assert cacheMissRoute(120, 119, true, 0)
                == CacheMissRoute.REJECT_TO_PROTECT_DATABASE;
    }

    private static void seniorPoolTimeoutHealthReviewScenarios() {
        assert timeoutOwner(SeniorTimeoutBoundary.POOL_CHECKOUT).contains("connectionTimeout");
        assert timeoutOwner(SeniorTimeoutBoundary.STATEMENT_EXECUTION).contains("query timeout");
        assert timeoutOwner(SeniorTimeoutBoundary.SOCKET_READ).contains("socket timeout");
        assert timeoutOwner(SeniorTimeoutBoundary.END_TO_END_DEADLINE).contains("caller");
        assert connectionHealthVerdict(false) == ConnectionHealthVerdict.REJECT_NOW;
        assert connectionHealthVerdict(true) == ConnectionHealthVerdict.VALID_AT_CHECK_ONLY;
    }

    private static void seniorProtocolOutcomeReviewScenarios() {
        assert tlsStartupOrder(WireProtocol.POSTGRESQL).equals(List.of(
                "client SSLRequest", "server S", "TLS handshake", "StartupMessage", "authentication"));
        assert tlsStartupOrder(WireProtocol.MYSQL).equals(List.of(
                "server initial handshake", "client SSLRequest", "TLS handshake",
                "HandshakeResponse", "authentication"));
        assert !layerAuthenticatesDatabasePrincipal(IdentityLayer.TLS_SERVER_IDENTITY, false);
        assert layerAuthenticatesDatabasePrincipal(IdentityLayer.TLS_SERVER_IDENTITY, true);
        assert layerAuthenticatesDatabasePrincipal(IdentityLayer.DATABASE_PRINCIPAL, false);
        assert unknownCommitAction(true, false) == UnknownCommitAction.RETURN_STORED_OUTCOME;
        assert unknownCommitAction(false, true) == UnknownCommitAction.RETRY_SAME_OPERATION_ID;
    }

    private static void productionStorageAcceptanceScenarios() {
        AcceptanceResult ready = storageAcceptance(true, true, true, true);
        assert ready.accepted() && ready.blockers().isEmpty();

        AcceptanceResult syntheticOnly = storageAcceptance(true, false, true, true);
        assert !syntheticOnly.accepted();
        assert syntheticOnly.blockers().equals(List.of("representative query plans not verified"));

        AcceptanceResult commentsOnly = storageAcceptance(true, true, false, false);
        assert !commentsOnly.accepted();
        assert commentsOnly.blockers().contains("vacuum or compaction headroom unknown");
        assert commentsOnly.blockers().contains("revision immutability not enforced");
    }

    private static void productionCacheAcceptanceScenarios() {
        assert cacheAcceptance(true, true, true, true).accepted();

        AcceptanceResult outage = cacheAcceptance(true, false, true, true);
        assert !outage.accepted();
        assert outage.blockers().equals(List.of("miss or outage path unbounded"));

        AcceptanceResult unsafeTrust = cacheAcceptance(true, true, false, false);
        assert unsafeTrust.blockers().contains("cache lease reused as correctness lock");
        assert unsafeTrust.blockers().contains("cache hit bypasses authorization");
    }

    private static void productionConnectivitySecurityAcceptanceScenarios() {
        AcceptanceResult ready = connectivitySecurityAcceptance(
                6, 1, 12, 100, 20, true, true, true);
        assert ready.accepted(); // 72 application connections leave 28, including 20 reserved.

        AcceptanceResult oversized = connectivitySecurityAcceptance(
                8, 2, 10, 100, 20, true, true, true);
        assert !oversized.accepted();
        assert oversized.blockers().contains("fleet pools exceed database budget after reserve");

        AcceptanceResult insecure = connectivitySecurityAcceptance(
                4, 1, 10, 100, 20, false, false, false);
        assert insecure.blockers().size() == 3;
    }

    private static void productionReleaseRecoveryAcceptanceScenarios() {
        assert releaseRecoveryAcceptance(true, true, true, true).accepted();

        AcceptanceResult noRestore = releaseRecoveryAcceptance(true, true, true, false);
        assert !noRestore.accepted();
        assert noRestore.blockers().equals(List.of("backup restore not exercised and measured"));

        AcceptanceResult happyPathOnly = releaseRecoveryAcceptance(false, false, false, true);
        assert happyPathOnly.blockers().size() == 3;
        assert happyPathOnly.blockers().contains("unknown commit outcome unproved");
    }

    private static void databaseReadPipelineScenarios() {
        assert planWork(false, 0, false) == PlanWork.CUSTOM_FOR_VALUES;
        assert planWork(true, 5, false) == PlanWork.GENERIC_PLAN_CANDIDATE;
        assert planWork(true, 20, true) == PlanWork.REANALYZE_AND_REPLAN;

        List<String> reused = logicalReadPipeline(PlanWork.GENERIC_PLAN_CANDIDATE);
        assert reused.size() == 6;
        assert reused.get(1).equals("compare or reuse generic plan");
        assert reused.get(5).equals("encode RowDescription and DataRow");
    }

    private static void slottedPageScenarios() {
        List<PageSlot> before = List.of(
                new PageSlot(7000, 400, true),
                new PageSlot(7600, 120, true));
        TuplePointer pointer = new TuplePointer(18, 1);
        assert resolveTupleOffset(before, pointer) == 7000;

        List<PageSlot> compacted = relocateTupleWithinPage(before, 1, 7480);
        assert resolveTupleOffset(compacted, pointer) == 7480;
        assert pointer.pageNumber() == 18 && pointer.slotIndex() == 1;

        try {
            resolveTupleOffset(List.of(new PageSlot(0, 0, false)), new TuplePointer(18, 1));
            throw new AssertionError("unused slot resolved");
        } catch (IllegalStateException expected) {
            assert expected.getMessage().contains("unused");
        }
    }

    private static void bufferLayerScenarios() {
        BufferAccess hot = bufferAccess(true, true, false);
        assert hot.source() == PageSource.SHARED_BUFFER;
        assert hot.framePinnedDuringDecode() && hot.contentLatchShortLived();
        assert !hot.transactionLockHeld();

        BufferAccess osHit = bufferAccess(false, true, false);
        assert osHit.source() == PageSource.OPERATING_SYSTEM_CACHE;

        BufferAccess diskLockingRead = bufferAccess(false, false, true);
        assert diskLockingRead.source() == PageSource.PHYSICAL_STORAGE;
        assert diskLockingRead.transactionLockHeld();
    }

    private static void mvccAndProtocolScenarios() {
        List<TupleVersion> versions = List.of(
                new TupleVersion("new body", 20, null),
                new TupleVersion("old body", 10, 20L));
        assert newestVisible(versions, 19).body().equals("old body");
        assert newestVisible(versions, 20).body().equals("new body");
        assert newestVisible(versions, 9) == null;

        assert dataRowWireBytes(4, 10) == 29;
        assert dataRowWireBytes(-1) == 11;

        assert largestReadStage(30, 900, 80, 120) == ReadBottleneck.HEAP_OR_TOAST;
        assert largestReadStage(30, 100, 800, 120) == ReadBottleneck.PROTOCOL_TRANSFER;
        assert largestReadStage(30, 100, 80, 900) == ReadBottleneck.JDBC_MATERIALIZATION;
    }

    private static void committedEditTransactionScenarios() {
        EditTxnResult committed = editTransaction(9, 9, false);
        assert committed.decision() == EditTxnDecision.COMMIT;
        assert committed.updateCount() == 1 && committed.storedVersion() == 10;
        assert committed.outboxInserted() && committed.operationResultInserted();

        EditTxnResult conflict = editTransaction(10, 9, false);
        assert conflict.decision() == EditTxnDecision.ROLLBACK_VERSION_CONFLICT;
        assert conflict.updateCount() == 0 && conflict.storedVersion() == 10;
        assert !conflict.outboxInserted() && !conflict.operationResultInserted();

        try {
            editTransaction(9, 9, true);
            throw new AssertionError("failure before commit was not injected");
        } catch (InjectedFailure expected) {}
    }

    private static void walCrashBoundaryScenarios() {
        CrashOutcome before = recoverEdit(CrashPoint.BEFORE_DURABLE_COMMIT);
        assert before.recoveredVersion() == 9 && !before.outboxRecovered();
        assert !before.walRedoRequired();

        CrashOutcome after = recoverEdit(CrashPoint.AFTER_DURABLE_COMMIT_BEFORE_PAGE_FLUSH);
        assert after.recoveredVersion() == 10 && after.outboxRecovered();
        assert after.walRedoRequired();

        assert durabilityBoundary(true, true) == DurabilityBoundary.DURABLE_BEFORE_SUCCESS;
        assert durabilityBoundary(true, false)
                == DurabilityBoundary.SUCCESS_CAN_PRECEDE_DURABILITY;
        assert durabilityBoundary(false, true) == DurabilityBoundary.CRASH_RECOVERY_UNSAFE;
    }

    private static void lostEditResponseScenarios() {
        CrashOutcome lost = recoverEdit(CrashPoint.RESPONSE_LOST_AFTER_DURABLE_COMMIT);
        assert lost.recoveredVersion() == 10 && lost.outboxRecovered();
        assert lost.clientKnowledge() == ClientKnowledge.UNKNOWN_QUERY_OPERATION_ID;

        assert resolveEditRetry(true, true, true)
                == EditRetryDecision.RETURN_RECORDED_RESULT;
        assert resolveEditRetry(false, true, true)
                == EditRetryDecision.RETRY_SAME_OPERATION_ID;
        assert resolveEditRetry(false, false, true)
                == EditRetryDecision.REJECT_NEW_OPERATION_ID;
        assert resolveEditRetry(false, true, false)
                == EditRetryDecision.STOP_AT_DEADLINE;
    }

    private static void checkpointVacuumDiagnosisScenarios() {
        assert diagnoseMaintenance(true, true, false, false)
                == MaintenanceDiagnosis.VACUUM_RETENTION_PRESSURE;
        assert diagnoseMaintenance(false, false, true, true)
                == MaintenanceDiagnosis.CHECKPOINT_RECOVERY_PRESSURE;
        assert diagnoseMaintenance(true, true, true, true)
                == MaintenanceDiagnosis.BOTH_SIGNAL_FAMILIES;
        assert diagnoseMaintenance(false, false, false, false)
                == MaintenanceDiagnosis.NO_PRESSURE_DETECTED;
    }


    private enum CacheRaceEntryKind { VALUE, ABSENT }

    private record CacheRaceEntry(CacheRaceEntryKind kind, String payload) {
        CacheRaceEntry {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(payload);
        }
    }

    private record CacheRaceLeaseCleanup(
            String ownerBeforeCleanup,
            String ownerAfterCleanup,
            boolean deleted) {}

    private record CacheRacePublish(
            long observedGeneration,
            long currentGeneration,
            CacheRaceEntry entryAfter,
            boolean published) {}

    private record CacheRaceLoadBudget(
            int instanceCount,
            int permitsPerInstance,
            int databaseConcurrentBudget,
            int serviceMillis,
            int fleetConcurrentPotential,
            int safeStaticPermitsPerInstance,
            long fleetRatePerSecond,
            boolean overConcurrencyBudget) {}

    private record CacheRaceContract(
            boolean uniqueLeaseToken,
            boolean ownerCheckedRelease,
            boolean atomicGenerationPublish,
            boolean negativeEntriesFenced,
            boolean visibilitySourceDeclared,
            int admittedFleetConcurrency,
            int databaseConcurrentBudget) {
        boolean strictEnough() {
            return uniqueLeaseToken
                    && ownerCheckedRelease
                    && atomicGenerationPublish
                    && negativeEntriesFenced
                    && visibilitySourceDeclared
                    && admittedFleetConcurrency <= databaseConcurrentBudget;
        }
    }

    private static CacheRaceLeaseCleanup cacheRaceReleaseLease(
            String currentOwner,
            String releasingOwner,
            boolean ownerChecked) {
        Objects.requireNonNull(releasingOwner);
        if (!ownerChecked || Objects.equals(currentOwner, releasingOwner)) {
            return new CacheRaceLeaseCleanup(currentOwner, null, currentOwner != null);
        }
        return new CacheRaceLeaseCleanup(currentOwner, currentOwner, false);
    }

    private static CacheRacePublish cacheRacePublishIfGenerationUnchanged(
            long observedGeneration,
            long currentGeneration,
            CacheRaceEntry currentEntry,
            CacheRaceEntry candidate) {
        Objects.requireNonNull(currentEntry);
        Objects.requireNonNull(candidate);
        boolean publish = observedGeneration == currentGeneration;
        return new CacheRacePublish(
                observedGeneration,
                currentGeneration,
                publish ? candidate : currentEntry,
                publish);
    }

    private static CacheRaceLoadBudget cacheRaceLoadBudget(
            int instanceCount,
            int permitsPerInstance,
            int databaseConcurrentBudget,
            int serviceMillis) {
        if (instanceCount <= 0 || permitsPerInstance < 0
                || databaseConcurrentBudget < 0 || serviceMillis <= 0) {
            throw new IllegalArgumentException("budget inputs");
        }
        int fleetPotential = Math.multiplyExact(instanceCount, permitsPerInstance);
        long perInstanceRate = (long) permitsPerInstance * 1_000L / serviceMillis;
        return new CacheRaceLoadBudget(
                instanceCount,
                permitsPerInstance,
                databaseConcurrentBudget,
                serviceMillis,
                fleetPotential,
                databaseConcurrentBudget / instanceCount,
                Math.multiplyExact(perInstanceRate, instanceCount),
                fleetPotential > databaseConcurrentBudget);
    }

    private static void cacheLeaseExpiryRaceScenarios() {
        CacheRaceLeaseCleanup unsafe =
                cacheRaceReleaseLease("B", "A", false);
        assert unsafe.deleted() && unsafe.ownerAfterCleanup() == null;

        CacheRaceLeaseCleanup safe =
                cacheRaceReleaseLease("B", "A", true);
        assert !safe.deleted() && safe.ownerAfterCleanup().equals("B");

        CacheRaceLeaseCleanup owner =
                cacheRaceReleaseLease("A", "A", true);
        assert owner.deleted() && owner.ownerAfterCleanup() == null;
    }

    private static void cacheGenerationValueRaceScenarios() {
        CacheRaceEntry current =
                new CacheRaceEntry(CacheRaceEntryKind.VALUE, "profile-v6");
        CacheRacePublish stale = cacheRacePublishIfGenerationUnchanged(
                5, 6, current,
                new CacheRaceEntry(CacheRaceEntryKind.VALUE, "profile-v5"));
        assert !stale.published();
        assert stale.entryAfter().payload().equals("profile-v6");

        CacheRacePublish fresh = cacheRacePublishIfGenerationUnchanged(
                6, 6, current,
                new CacheRaceEntry(CacheRaceEntryKind.VALUE, "profile-v6"));
        assert fresh.published();
    }

    private static void cacheNegativeResurrectionRaceScenarios() {
        CacheRaceEntry created =
                new CacheRaceEntry(CacheRaceEntryKind.VALUE, "revision-9");
        CacheRacePublish negative = cacheRacePublishIfGenerationUnchanged(
                8, 9, created,
                new CacheRaceEntry(CacheRaceEntryKind.ABSENT, ""));
        assert !negative.published();
        assert negative.entryAfter().kind() == CacheRaceEntryKind.VALUE;
        assert negative.entryAfter().payload().equals("revision-9");
    }

    private static void cacheFleetLoadBudgetScenarios() {
        CacheRaceLoadBudget overloaded =
                cacheRaceLoadBudget(40, 8, 100, 5);
        assert overloaded.fleetConcurrentPotential() == 320;
        assert overloaded.safeStaticPermitsPerInstance() == 2;
        assert overloaded.overConcurrencyBudget();
        assert overloaded.fleetRatePerSecond() == 64_000;

        CacheRaceLoadBudget safe =
                cacheRaceLoadBudget(40, 2, 100, 5);
        assert !safe.overConcurrencyBudget();

        CacheRaceContract contract = new CacheRaceContract(
                true, true, true, true, true, 100, 100);
        assert contract.strictEnough();
        assert !new CacheRaceContract(
                true, false, true, true, true, 100, 100).strictEnough();
    }

    private record PoolResidency(
            int arrivalRatePerSecond,
            int firstSqlMillis,
            int remoteMillis,
            int secondSqlMillis,
            int poolSize,
            int holdMillis,
            double averageBorrowed,
            boolean meanDemandExceedsPool) {}

    private record PoolQueueArithmetic(
            int arrivalRatePerSecond,
            int holdMillis,
            int poolSize,
            int requestDeadlineMillis,
            int acquisitionTimeoutMillis,
            int sqlBudgetMillis,
            double averageBorrowed,
            double idealizedServiceCeilingPerSecond,
            double meanHeadroomConnections,
            boolean queuesAtMean) {
        boolean deadlineOrderValid() {
            return requestDeadlineMillis
                    > acquisitionTimeoutMillis + sqlBudgetMillis;
        }
    }

    private record LayeredPoolArithmetic(
            int instanceCount,
            int localMaximumPoolSize,
            int localActiveAtSnapshot,
            int proxyServerConnections,
            int maxClientSessions,
            int activeClientDemand,
            int proxyQueuedAtSnapshot,
            double localActiveFraction,
            boolean proxySaturated) {}

    private record PoolConfiguration(
            int minimumIdle,
            int maximumPoolSize,
            int connectionTimeoutMillis,
            int idleTimeoutMillis,
            int borrowableCapacity,
            boolean minimumIdleIsReservation,
            boolean idleTimeoutAppliesToActive) {}

    private record PoolDeadlineContract(
            int requestDeadlineMillis,
            int acquisitionTimeoutMillis,
            int sqlBudgetMillis,
            int responseMarginMillis) {
        boolean ordered() {
            return requestDeadlineMillis
                    >= acquisitionTimeoutMillis + sqlBudgetMillis
                    + responseMarginMillis;
        }

        int remainingAfterAcquireAndSqlMillis() {
            return requestDeadlineMillis
                    - acquisitionTimeoutMillis - sqlBudgetMillis;
        }
    }

    private enum PoolCommitTimeoutDecision {
        SAFE_TO_RETRY_TRANSACTION,
        QUERY_STABLE_OPERATION_ID,
        RETURN_RECORDED_RESULT,
        RETURN_COMMIT_RESPONSE
    }

    private static PoolResidency poolResidency(
            int arrivalRatePerSecond,
            int firstSqlMillis,
            int remoteMillis,
            int secondSqlMillis,
            int poolSize) {
        if (arrivalRatePerSecond < 0 || firstSqlMillis < 0
                || remoteMillis < 0 || secondSqlMillis < 0 || poolSize <= 0) {
            throw new IllegalArgumentException("pool residency inputs");
        }
        int holdMillis = Math.addExact(
                Math.addExact(firstSqlMillis, remoteMillis), secondSqlMillis);
        double averageBorrowed =
                arrivalRatePerSecond * holdMillis / 1_000.0;
        return new PoolResidency(
                arrivalRatePerSecond,
                firstSqlMillis,
                remoteMillis,
                secondSqlMillis,
                poolSize,
                holdMillis,
                averageBorrowed,
                averageBorrowed > poolSize);
    }

    private static PoolQueueArithmetic poolQueueArithmetic(
            int arrivalRatePerSecond,
            int holdMillis,
            int poolSize,
            int requestDeadlineMillis,
            int acquisitionTimeoutMillis,
            int sqlBudgetMillis) {
        if (arrivalRatePerSecond < 0 || holdMillis <= 0 || poolSize <= 0
                || requestDeadlineMillis <= 0
                || acquisitionTimeoutMillis < 0 || sqlBudgetMillis < 0) {
            throw new IllegalArgumentException("pool queue inputs");
        }
        double averageBorrowed =
                arrivalRatePerSecond * holdMillis / 1_000.0;
        double serviceCeiling = poolSize * 1_000.0 / holdMillis;
        return new PoolQueueArithmetic(
                arrivalRatePerSecond,
                holdMillis,
                poolSize,
                requestDeadlineMillis,
                acquisitionTimeoutMillis,
                sqlBudgetMillis,
                averageBorrowed,
                serviceCeiling,
                poolSize - averageBorrowed,
                averageBorrowed > poolSize);
    }

    private static LayeredPoolArithmetic layeredPoolArithmetic(
            int instanceCount,
            int localMaximumPoolSize,
            int localActiveAtSnapshot,
            int proxyServerConnections) {
        if (instanceCount <= 0 || localMaximumPoolSize <= 0
                || localActiveAtSnapshot < 0
                || localActiveAtSnapshot > localMaximumPoolSize
                || proxyServerConnections <= 0) {
            throw new IllegalArgumentException("layered pool inputs");
        }
        int maxClients = Math.multiplyExact(
                instanceCount, localMaximumPoolSize);
        int activeClients = Math.multiplyExact(
                instanceCount, localActiveAtSnapshot);
        return new LayeredPoolArithmetic(
                instanceCount,
                localMaximumPoolSize,
                localActiveAtSnapshot,
                proxyServerConnections,
                maxClients,
                activeClients,
                Math.max(0, activeClients - proxyServerConnections),
                (double) localActiveAtSnapshot / localMaximumPoolSize,
                activeClients >= proxyServerConnections);
    }

    private static PoolConfiguration interpretPoolConfiguration(
            int minimumIdle,
            int maximumPoolSize,
            int connectionTimeoutMillis,
            int idleTimeoutMillis) {
        if (minimumIdle < 0 || maximumPoolSize <= 0
                || minimumIdle > maximumPoolSize
                || connectionTimeoutMillis < 0 || idleTimeoutMillis < 0) {
            throw new IllegalArgumentException("pool configuration inputs");
        }
        return new PoolConfiguration(
                minimumIdle,
                maximumPoolSize,
                connectionTimeoutMillis,
                idleTimeoutMillis,
                maximumPoolSize,
                false,
                false);
    }

    private static PoolDeadlineContract poolDeadlineContract(
            int requestDeadlineMillis,
            int acquisitionTimeoutMillis,
            int sqlBudgetMillis,
            int responseMarginMillis) {
        if (requestDeadlineMillis <= 0 || acquisitionTimeoutMillis < 0
                || sqlBudgetMillis < 0 || responseMarginMillis < 0) {
            throw new IllegalArgumentException("deadline inputs");
        }
        return new PoolDeadlineContract(
                requestDeadlineMillis,
                acquisitionTimeoutMillis,
                sqlBudgetMillis,
                responseMarginMillis);
    }

    private static PoolCommitTimeoutDecision poolCommitTimeoutDecision(
            boolean commitSent,
            boolean responseReceived,
            boolean operationResultRecorded) {
        if (responseReceived) {
            return PoolCommitTimeoutDecision.RETURN_COMMIT_RESPONSE;
        }
        if (!commitSent) {
            return PoolCommitTimeoutDecision.SAFE_TO_RETRY_TRANSACTION;
        }
        return operationResultRecorded
                ? PoolCommitTimeoutDecision.RETURN_RECORDED_RESULT
                : PoolCommitTimeoutDecision.QUERY_STABLE_OPERATION_ID;
    }

    private static void poolResidencyArithmeticScenarios() {
        PoolResidency heldAcrossRemote =
                poolResidency(100, 8, 200, 8, 16);
        assert heldAcrossRemote.holdMillis() == 216;
        assert heldAcrossRemote.averageBorrowed() == 21.6;
        assert heldAcrossRemote.meanDemandExceedsPool();

        PoolResidency splitBoundary =
                poolResidency(100, 8, 0, 8, 16);
        assert splitBoundary.holdMillis() == 16;
        assert splitBoundary.averageBorrowed() == 1.6;
        assert !splitBoundary.meanDemandExceedsPool();
    }

    private static void poolQueueStabilityScenarios() {
        PoolQueueArithmetic healthy =
                poolQueueArithmetic(200, 25, 16, 150, 80, 40);
        assert healthy.averageBorrowed() == 5.0;
        assert healthy.idealizedServiceCeilingPerSecond() == 640.0;
        assert healthy.meanHeadroomConnections() == 11.0;
        assert healthy.deadlineOrderValid();

        PoolQueueArithmetic unstable =
                poolQueueArithmetic(200, 250, 16, 150, 80, 40);
        assert unstable.averageBorrowed() == 50.0;
        assert unstable.idealizedServiceCeilingPerSecond() == 64.0;
        assert unstable.meanHeadroomConnections() == -34.0;
        assert unstable.queuesAtMean();
    }

    private static void layeredPoolQueueScenarios() {
        LayeredPoolArithmetic layered =
                layeredPoolArithmetic(20, 16, 8, 80);
        assert layered.maxClientSessions() == 320;
        assert layered.activeClientDemand() == 160;
        assert layered.proxyQueuedAtSnapshot() == 80;
        assert layered.localActiveFraction() == 0.5;
        assert layered.proxySaturated();

        LayeredPoolArithmetic belowProxyLimit =
                layeredPoolArithmetic(20, 16, 3, 80);
        assert belowProxyLimit.proxyQueuedAtSnapshot() == 0;
        assert !belowProxyLimit.proxySaturated();
    }

    private static void poolConfigurationAndOutcomeScenarios() {
        PoolConfiguration config =
                interpretPoolConfiguration(4, 16, 80, 600_000);
        assert config.borrowableCapacity() == 16;
        assert !config.minimumIdleIsReservation();
        assert !config.idleTimeoutAppliesToActive();

        PoolDeadlineContract deadlines =
                poolDeadlineContract(150, 80, 60, 10);
        assert deadlines.ordered();
        assert deadlines.remainingAfterAcquireAndSqlMillis() == 10;

        assert poolCommitTimeoutDecision(false, false, false)
                == PoolCommitTimeoutDecision.SAFE_TO_RETRY_TRANSACTION;
        assert poolCommitTimeoutDecision(true, false, false)
                == PoolCommitTimeoutDecision.QUERY_STABLE_OPERATION_ID;
        assert poolCommitTimeoutDecision(true, false, true)
                == PoolCommitTimeoutDecision.RETURN_RECORDED_RESULT;
        assert poolCommitTimeoutDecision(true, true, true)
                == PoolCommitTimeoutDecision.RETURN_COMMIT_RESPONSE;
    }

    private enum SslNegotiationDecision {
        START_TLS,
        REJECT_TLS_REQUIRED,
        REJECT_UNEXPECTED_RESPONSE
    }

    private record ProtocolQueryDecode(
            boolean complete,
            int declaredLength,
            String query) {}

    private enum HandshakeAdmissionDecision {
        REUSE_POOLED_CONNECTION,
        OPEN_BOUNDED_FRESH_CONNECTION,
        REJECT_CAPACITY
    }

    private record ProtocolHandshakeBudget(
            int roundTripMillis,
            int sequentialWaits,
            int poolWaitMillis,
            int coldNetworkMillis,
            boolean freshConnectionSlotAvailable,
            HandshakeAdmissionDecision decision) {}

    private record ProtocolSecurityContract(
            boolean tlsEncrypted,
            boolean serverIdentityVerified,
            boolean databasePrincipalAuthenticated,
            boolean tenantObjectAuthorized) {
        boolean operationAllowed() {
            return tlsEncrypted && serverIdentityVerified
                    && databasePrincipalAuthenticated
                    && tenantObjectAuthorized;
        }
    }

    private static byte[] protocolSslRequest() {
        return ByteBuffer.allocate(8)
                .putInt(8)
                .putInt(80_877_103)
                .array();
    }

    private static SslNegotiationDecision sslNegotiationDecision(
            byte serverResponse,
            boolean tlsRequired) {
        if (serverResponse == (byte) 'S') {
            return SslNegotiationDecision.START_TLS;
        }
        if (serverResponse == (byte) 'N' && tlsRequired) {
            return SslNegotiationDecision.REJECT_TLS_REQUIRED;
        }
        return SslNegotiationDecision.REJECT_UNEXPECTED_RESPONSE;
    }

    private static byte[] simpleQueryFrame(String sql) {
        byte[] query = Objects.requireNonNull(sql).getBytes(StandardCharsets.UTF_8);
        for (byte value : query) {
            if (value == 0) throw new IllegalArgumentException("embedded NUL");
        }
        return ByteBuffer.allocate(1 + 4 + query.length + 1)
                .put((byte) 'Q')
                .putInt(4 + query.length + 1)
                .put(query)
                .put((byte) 0)
                .array();
    }

    private static ProtocolQueryDecode decodeSimpleQuery(
            byte[] received,
            int maximumDeclaredLength) {
        Objects.requireNonNull(received);
        if (maximumDeclaredLength < 5) {
            throw new IllegalArgumentException("maximum frame too small");
        }
        if (received.length < 5) {
            return new ProtocolQueryDecode(false, -1, null);
        }
        if (received[0] != (byte) 'Q') {
            throw new IllegalArgumentException("not a simple Query frame");
        }
        int declaredLength = ByteBuffer.wrap(received, 1, 4).getInt();
        if (declaredLength < 5 || declaredLength > maximumDeclaredLength) {
            throw new IllegalArgumentException("invalid Query length");
        }
        int totalBytes = Math.addExact(1, declaredLength);
        if (received.length < totalBytes) {
            return new ProtocolQueryDecode(false, declaredLength, null);
        }
        if (received[totalBytes - 1] != 0) {
            throw new IllegalArgumentException("Query is not NUL terminated");
        }
        for (int i = 5; i < totalBytes - 1; i++) {
            if (received[i] == 0) {
                throw new IllegalArgumentException("embedded NUL");
            }
        }
        String query = new String(
                received, 5, totalBytes - 6, StandardCharsets.UTF_8);
        return new ProtocolQueryDecode(true, declaredLength, query);
    }

    private static ProtocolHandshakeBudget protocolHandshakeBudget(
            int roundTripMillis,
            int sequentialWaits,
            int poolWaitMillis,
            boolean freshConnectionSlotAvailable) {
        if (roundTripMillis < 0 || sequentialWaits < 0 || poolWaitMillis < 0) {
            throw new IllegalArgumentException("handshake budget inputs");
        }
        int coldNetworkMillis = Math.multiplyExact(
                roundTripMillis, sequentialWaits);
        HandshakeAdmissionDecision decision;
        if (poolWaitMillis <= coldNetworkMillis) {
            decision = HandshakeAdmissionDecision.REUSE_POOLED_CONNECTION;
        } else if (freshConnectionSlotAvailable) {
            decision = HandshakeAdmissionDecision.OPEN_BOUNDED_FRESH_CONNECTION;
        } else {
            decision = HandshakeAdmissionDecision.REJECT_CAPACITY;
        }
        return new ProtocolHandshakeBudget(
                roundTripMillis,
                sequentialWaits,
                poolWaitMillis,
                coldNetworkMillis,
                freshConnectionSlotAvailable,
                decision);
    }

    private static void protocolSslRequestScenarios() {
        byte[] expected = new byte[] {
                0x00, 0x00, 0x00, 0x08,
                0x04, (byte) 0xD2, 0x16, 0x2F
        };
        assert Arrays.equals(protocolSslRequest(), expected);
        assert sslNegotiationDecision((byte) 'S', true)
                == SslNegotiationDecision.START_TLS;
        assert sslNegotiationDecision((byte) 'N', true)
                == SslNegotiationDecision.REJECT_TLS_REQUIRED;
        assert sslNegotiationDecision((byte) '?', true)
                == SslNegotiationDecision.REJECT_UNEXPECTED_RESPONSE;
    }

    private static void protocolSimpleQueryFramingScenarios() {
        byte[] expected = new byte[] {
                0x51, 0x00, 0x00, 0x00, 0x0E,
                0x53, 0x45, 0x4C, 0x45, 0x43, 0x54, 0x20,
                0x31, 0x3B, 0x00
        };
        byte[] frame = simpleQueryFrame("SELECT 1;");
        assert Arrays.equals(frame, expected);
        ProtocolQueryDecode partial = decodeSimpleQuery(
                Arrays.copyOf(frame, 9), 1_048_576);
        assert !partial.complete();
        assert partial.declaredLength() == 14;
        ProtocolQueryDecode complete = decodeSimpleQuery(frame, 1_048_576);
        assert complete.complete();
        assert complete.query().equals("SELECT 1;");
    }

    private static void protocolHandshakeAdmissionScenarios() {
        ProtocolHandshakeBudget coldWins = protocolHandshakeBudget(
                5, 6, 200, true);
        assert coldWins.coldNetworkMillis() == 30;
        assert coldWins.decision()
                == HandshakeAdmissionDecision.OPEN_BOUNDED_FRESH_CONNECTION;

        ProtocolHandshakeBudget reuseWins = protocolHandshakeBudget(
                5, 6, 10, true);
        assert reuseWins.decision()
                == HandshakeAdmissionDecision.REUSE_POOLED_CONNECTION;

        ProtocolHandshakeBudget bounded = protocolHandshakeBudget(
                5, 6, 200, false);
        assert bounded.decision()
                == HandshakeAdmissionDecision.REJECT_CAPACITY;
    }

    private static void protocolSecurityContractScenarios() {
        ProtocolSecurityContract authorized = new ProtocolSecurityContract(
                true, true, true, true);
        assert authorized.operationAllowed();

        ProtocolSecurityContract wrongTenant = new ProtocolSecurityContract(
                true, true, true, false);
        assert !wrongTenant.operationAllowed();

        ProtocolSecurityContract unverifiedServer = new ProtocolSecurityContract(
                true, false, true, true);
        assert !unverifiedServer.operationAllowed();
    }

    private enum FirstProbeKind {
        BODY_HIT,
        ABSENT_HIT,
        MISS,
        REDIS_FAILURE
    }

    private record FirstProbe(
            FirstProbeKind kind,
            String body,
            boolean jdbcCheckout,
            boolean countsAsFirstProbeHit,
            boolean countsAsFirstProbeMiss) {}

    private record HealthyMissStory(
            List<String> steps,
            boolean databaseRead,
            boolean publishAttempted,
            boolean leaseOwnedAtPublish) {}

    private enum DatabaseResultKind { BODY, ABSENT, FAILURE }

    private record FailureBoundaryStory(
            boolean redisErrorCounted,
            boolean cooldownEntered,
            boolean sqlAttempted,
            boolean sqlPermitReleased,
            boolean negativePublished,
            boolean databaseFailurePropagated) {}

    private record ExpiredLeaseStory(
            boolean returnRevisionToCaller,
            boolean publishAccepted,
            boolean mustReloadAuthority,
            String reason) {}

    private static FirstProbe firstProbe(String envelope, boolean redisAvailable) {
        if (!redisAvailable) {
            return new FirstProbe(
                    FirstProbeKind.REDIS_FAILURE, null, false, false, false);
        }
        if (envelope == null) {
            return new FirstProbe(
                    FirstProbeKind.MISS, null, false, false, true);
        }
        if (envelope.equals("n1")) {
            return new FirstProbe(
                    FirstProbeKind.ABSENT_HIT, null, false, true, false);
        }
        if (envelope.startsWith("v1:")) {
            return new FirstProbe(
                    FirstProbeKind.BODY_HIT,
                    envelope.substring(3),
                    false,
                    true,
                    false);
        }
        throw new IllegalArgumentException("unknown cache envelope");
    }

    private static HealthyMissStory healthyMissStory(
            boolean secondLookupPresent,
            boolean sqlPermitAvailable,
            boolean leaseOwnedAtPublish) {
        List<String> steps = new ArrayList<>();
        steps.add("initial lookup miss");
        steps.add("acquire owner lease");
        steps.add("second lookup");
        if (secondLookupPresent) {
            steps.add("return coalesced hit");
            return new HealthyMissStory(List.copyOf(steps), false, false, true);
        }
        if (!sqlPermitAvailable) {
            steps.add("reject database load budget");
            return new HealthyMissStory(List.copyOf(steps), false, false, true);
        }
        steps.add("bounded database read");
        steps.add("owner-checked publish");
        return new HealthyMissStory(
                List.copyOf(steps), true, true, leaseOwnedAtPublish);
    }

    private static FailureBoundaryStory failureBoundaryStory(
            boolean redisAvailable,
            boolean sqlPermitAvailable,
            DatabaseResultKind databaseResult) {
        boolean sqlAttempted = sqlPermitAvailable;
        boolean databaseFailure = sqlAttempted
                && databaseResult == DatabaseResultKind.FAILURE;
        boolean negativePublished = redisAvailable
                && sqlAttempted
                && databaseResult == DatabaseResultKind.ABSENT;
        return new FailureBoundaryStory(
                !redisAvailable,
                !redisAvailable,
                sqlAttempted,
                sqlAttempted,
                negativePublished,
                databaseFailure);
    }

    private static ExpiredLeaseStory expiredLeaseStory(
            boolean immutableRevision,
            boolean authorizationChecked,
            boolean stillOwnsLease) {
        if (!authorizationChecked) {
            return new ExpiredLeaseStory(
                    false, false, true, "authorization is not cache ownership");
        }
        if (stillOwnsLease) {
            return new ExpiredLeaseStory(
                    true, true, false, "current owner may publish");
        }
        if (immutableRevision) {
            return new ExpiredLeaseStory(
                    true, false, false,
                    "immutable authorized revision remains valid for this caller");
        }
        return new ExpiredLeaseStory(
                false, false, true,
                "mutable value requires a fresh authoritative decision");
    }

    private static void cacheStoryFirstProbeScenarios() {
        FirstProbe body = firstProbe("v1:hello", true);
        assert body.kind() == FirstProbeKind.BODY_HIT;
        assert body.body().equals("hello") && !body.jdbcCheckout();
        assert body.countsAsFirstProbeHit() && !body.countsAsFirstProbeMiss();

        FirstProbe absent = firstProbe("n1", true);
        assert absent.kind() == FirstProbeKind.ABSENT_HIT;
        assert absent.countsAsFirstProbeHit() && absent.body() == null;

        FirstProbe miss = firstProbe(null, true);
        assert miss.kind() == FirstProbeKind.MISS;
        assert miss.countsAsFirstProbeMiss() && !miss.jdbcCheckout();
    }

    private static void cacheStoryHealthyMissScenarios() {
        HealthyMissStory coalesced = healthyMissStory(true, true, true);
        assert !coalesced.databaseRead() && !coalesced.publishAttempted();
        assert coalesced.steps().get(2).equals("second lookup");

        HealthyMissStory owner = healthyMissStory(false, true, true);
        assert owner.databaseRead() && owner.publishAttempted();
        assert owner.leaseOwnedAtPublish();

        HealthyMissStory saturated = healthyMissStory(false, false, true);
        assert !saturated.databaseRead();
        assert saturated.steps().contains("reject database load budget");
    }

    private static void cacheStoryFailureBoundaryScenarios() {
        FailureBoundaryStory redisDown = failureBoundaryStory(
                false, true, DatabaseResultKind.BODY);
        assert redisDown.redisErrorCounted() && redisDown.cooldownEntered();
        assert redisDown.sqlAttempted() && redisDown.sqlPermitReleased();
        assert !redisDown.negativePublished();

        FailureBoundaryStory databaseDown = failureBoundaryStory(
                true, true, DatabaseResultKind.FAILURE);
        assert databaseDown.databaseFailurePropagated();
        assert databaseDown.sqlPermitReleased();
        assert !databaseDown.negativePublished();

        FailureBoundaryStory knownAbsent = failureBoundaryStory(
                true, true, DatabaseResultKind.ABSENT);
        assert knownAbsent.negativePublished();
        assert !knownAbsent.databaseFailurePropagated();
    }

    private static void cacheStoryExpiredLeaseScenarios() {
        ExpiredLeaseStory immutable = expiredLeaseStory(true, true, false);
        assert immutable.returnRevisionToCaller();
        assert !immutable.publishAccepted() && !immutable.mustReloadAuthority();

        ExpiredLeaseStory mutable = expiredLeaseStory(false, true, false);
        assert !mutable.returnRevisionToCaller();
        assert !mutable.publishAccepted() && mutable.mustReloadAuthority();

        ExpiredLeaseStory unauthorized = expiredLeaseStory(true, false, true);
        assert !unauthorized.returnRevisionToCaller();
        assert !unauthorized.publishAccepted() && unauthorized.mustReloadAuthority();
    }

    private record CmsTrafficPlan(
            long uncachedBytesPerSecond,
            long originReadsPerSecond,
            long revisionBodyLoadsPerSecond,
            long pointerChecksPerSecond) {}

    private enum CmsPublishOutcome {
        PUBLISHED,
        VERSION_CONFLICT,
        WRONG_TENANT,
        INELIGIBLE_REVISION
    }

    private record CmsPublishStory(
            CmsPublishOutcome outcome,
            boolean pointerChanged,
            boolean outboxInserted,
            long newVersion) {}

    private record CmsReadStory(
            boolean authorizationChecked,
            boolean pointerResolved,
            boolean immutableRevisionRead,
            boolean sharedCacheAllowed,
            String cacheControl) {}

    private record CmsCollapsePlan(
            long originDemand,
            long originAdmitted,
            long potentialBodyLoadDemand,
            long databaseLoadsAdmitted,
            boolean originGateActive,
            boolean databaseGateActive) {}

    private static CmsTrafficPlan cmsTrafficPlan(
            long publicReadsPerSecond,
            long meanBytes,
            double cdnHitRate,
            double revisionHitRate) {
        long origin = Math.round(publicReadsPerSecond * (1.0 - cdnHitRate));
        long bodyLoads = Math.round(origin * (1.0 - revisionHitRate));
        return new CmsTrafficPlan(
                Math.multiplyExact(publicReadsPerSecond, meanBytes),
                origin,
                bodyLoads,
                origin);
    }

    private static CmsPublishStory cmsPublishStory(
            long articleTenant,
            long revisionTenant,
            boolean revisionEligible,
            long currentVersion,
            long expectedVersion) {
        if (articleTenant != revisionTenant) {
            return new CmsPublishStory(
                    CmsPublishOutcome.WRONG_TENANT, false, false, currentVersion);
        }
        if (!revisionEligible) {
            return new CmsPublishStory(
                    CmsPublishOutcome.INELIGIBLE_REVISION, false, false, currentVersion);
        }
        if (currentVersion != expectedVersion) {
            return new CmsPublishStory(
                    CmsPublishOutcome.VERSION_CONFLICT, false, false, currentVersion);
        }
        return new CmsPublishStory(
                CmsPublishOutcome.PUBLISHED, true, true, currentVersion + 1);
    }

    private static CmsReadStory cmsReadStory(
            boolean anonymousPublicRoute,
            boolean explicitRevision,
            boolean authorized,
            boolean revisionExists) {
        if (!authorized || !revisionExists) {
            return new CmsReadStory(
                    true, !explicitRevision, false, false, "private, no-store");
        }
        if (anonymousPublicRoute && !explicitRevision) {
            return new CmsReadStory(
                    true, true, true, true,
                    "public, max-age=60, s-maxage=300");
        }
        return new CmsReadStory(
                true, !explicitRevision, true, false, "private, no-store");
    }

    private static CmsCollapsePlan cmsCollapsePlan(
            long publicReadsPerSecond,
            boolean cdnAvailable,
            double cdnHitRate,
            boolean revisionCacheAvailable,
            double revisionHitRate,
            long originBudget,
            long databaseBudget) {
        long originDemand = cdnAvailable
                ? Math.round(publicReadsPerSecond * (1.0 - cdnHitRate))
                : publicReadsPerSecond;
        long originAdmitted = Math.min(originDemand, originBudget);
        long bodyDemand = revisionCacheAvailable
                ? Math.round(originDemand * (1.0 - revisionHitRate))
                : originDemand;
        long databaseAdmitted = Math.min(bodyDemand, databaseBudget);
        return new CmsCollapsePlan(
                originDemand,
                originAdmitted,
                bodyDemand,
                databaseAdmitted,
                originDemand > originBudget,
                bodyDemand > databaseBudget);
    }

    private static void cmsTrafficArithmeticScenarios() {
        CmsTrafficPlan plan = cmsTrafficPlan(20_000, 30_000, 0.90, 0.95);
        assert plan.uncachedBytesPerSecond() == 600_000_000;
        assert plan.originReadsPerSecond() == 2_000;
        assert plan.revisionBodyLoadsPerSecond() == 100;
        assert plan.pointerChecksPerSecond() == 2_000;
    }

    private static void cmsPublishTransactionScenarios() {
        CmsPublishStory success = cmsPublishStory(7, 7, true, 11, 11);
        assert success.outcome() == CmsPublishOutcome.PUBLISHED;
        assert success.pointerChanged() && success.outboxInserted();
        assert success.newVersion() == 12;

        CmsPublishStory conflict = cmsPublishStory(7, 7, true, 12, 11);
        assert conflict.outcome() == CmsPublishOutcome.VERSION_CONFLICT;
        assert !conflict.pointerChanged() && !conflict.outboxInserted();

        CmsPublishStory crossTenant = cmsPublishStory(7, 8, true, 11, 11);
        assert crossTenant.outcome() == CmsPublishOutcome.WRONG_TENANT;
        assert !crossTenant.pointerChanged() && !crossTenant.outboxInserted();
    }

    private static void cmsReadPolicyScenarios() {
        CmsReadStory publicRead = cmsReadStory(true, false, true, true);
        assert publicRead.authorizationChecked() && publicRead.pointerResolved();
        assert publicRead.sharedCacheAllowed();
        assert publicRead.cacheControl().contains("s-maxage=300");

        CmsReadStory editorPreview = cmsReadStory(false, true, true, true);
        assert editorPreview.authorizationChecked();
        assert !editorPreview.pointerResolved();
        assert editorPreview.immutableRevisionRead();
        assert !editorPreview.sharedCacheAllowed();

        CmsReadStory revoked = cmsReadStory(false, true, false, true);
        assert revoked.authorizationChecked() && !revoked.immutableRevisionRead();
        assert revoked.cacheControl().equals("private, no-store");
    }

    private static void cmsLayerCollapseScenarios() {
        CmsCollapsePlan healthy = cmsCollapsePlan(
                20_000, true, 0.90, true, 0.95, 4_000, 500);
        assert healthy.originDemand() == 2_000;
        assert healthy.potentialBodyLoadDemand() == 100;
        assert !healthy.originGateActive() && !healthy.databaseGateActive();

        CmsCollapsePlan redisDown = cmsCollapsePlan(
                20_000, true, 0.90, false, 0.95, 4_000, 500);
        assert redisDown.originDemand() == 2_000;
        assert redisDown.potentialBodyLoadDemand() == 2_000;
        assert redisDown.databaseLoadsAdmitted() == 500;
        assert redisDown.databaseGateActive();

        CmsCollapsePlan cdnDown = cmsCollapsePlan(
                20_000, false, 0.90, true, 0.95, 4_000, 500);
        assert cdnDown.originDemand() == 20_000;
        assert cdnDown.originAdmitted() == 4_000;
        assert cdnDown.originGateActive();
    }

    private record FlashSaleAdmission(
            long attempts,
            long remainingUnits,
            long databaseAttemptBudget,
            long admitted,
            long rejected,
            boolean stockSerializationPoint) {}

    private enum ReservationOutcome {
        RESERVED,
        SOLD_OUT,
        IDEMPOTENT_REPLAY,
        IDEMPOTENCY_CONFLICT,
        INVALID_QUANTITY
    }

    private record ReservationAttempt(
            ReservationOutcome outcome,
            long newAvailable,
            boolean reservationInserted,
            boolean outboxInserted) {}

    private enum ReservationState {
        RESERVED,
        CONFIRMED,
        RELEASED,
        REFUND_PENDING,
        REFUNDED
    }

    private record ReservationTransition(
            ReservationState before,
            ReservationState next,
            boolean restoreInventory,
            boolean compensationRequired) {}

    private record StockTokenPlan(
            long authoritativeStock,
            long totalAllocated,
            long unallocated,
            boolean conservesStock) {}

    private static FlashSaleAdmission flashSaleAdmission(
            long attempts,
            long remainingUnits,
            long databaseAttemptBudget) {
        if (attempts < 0 || remainingUnits < 0 || databaseAttemptBudget < 0) {
            throw new IllegalArgumentException("flash-sale admission inputs");
        }
        long admitted = Math.min(
                attempts, Math.min(remainingUnits, databaseAttemptBudget));
        return new FlashSaleAdmission(
                attempts,
                remainingUnits,
                databaseAttemptBudget,
                admitted,
                attempts - admitted,
                true);
    }

    private static ReservationAttempt flashSaleReserve(
            long available,
            long quantity,
            boolean operationAlreadyCommitted,
            boolean requestHashMatches) {
        if (available < 0) {
            throw new IllegalArgumentException("negative available stock");
        }
        if (quantity <= 0) {
            return new ReservationAttempt(
                    ReservationOutcome.INVALID_QUANTITY,
                    available,
                    false,
                    false);
        }
        if (operationAlreadyCommitted) {
            return new ReservationAttempt(
                    requestHashMatches
                            ? ReservationOutcome.IDEMPOTENT_REPLAY
                            : ReservationOutcome.IDEMPOTENCY_CONFLICT,
                    available,
                    false,
                    false);
        }
        if (available < quantity) {
            return new ReservationAttempt(
                    ReservationOutcome.SOLD_OUT,
                    available,
                    false,
                    false);
        }
        return new ReservationAttempt(
                ReservationOutcome.RESERVED,
                available - quantity,
                true,
                true);
    }

    private static ReservationTransition flashSaleTransition(
            ReservationState before,
            boolean paymentAccepted,
            boolean expiryWon) {
        if (before == ReservationState.RESERVED && expiryWon) {
            return new ReservationTransition(
                    before, ReservationState.RELEASED, true, paymentAccepted);
        }
        if (before == ReservationState.RESERVED && paymentAccepted) {
            return new ReservationTransition(
                    before, ReservationState.CONFIRMED, false, false);
        }
        if (before == ReservationState.RELEASED && paymentAccepted) {
            return new ReservationTransition(
                    before, ReservationState.REFUND_PENDING, false, true);
        }
        return new ReservationTransition(before, before, false, false);
    }

    private static StockTokenPlan stockTokenPlan(
            long authoritativeStock,
            long... allocations) {
        if (authoritativeStock < 0) {
            throw new IllegalArgumentException("negative authoritative stock");
        }
        long allocated = 0;
        for (long allocation : allocations) {
            if (allocation < 0) {
                throw new IllegalArgumentException("negative token allocation");
            }
            allocated = Math.addExact(allocated, allocation);
        }
        boolean safe = allocated <= authoritativeStock;
        return new StockTokenPlan(
                authoritativeStock,
                allocated,
                safe ? authoritativeStock - allocated : 0,
                safe);
    }

    private static void flashSaleAdmissionScenarios() {
        FlashSaleAdmission burst =
                flashSaleAdmission(50_000, 1_000, 2_000);
        assert burst.admitted() == 1_000;
        assert burst.rejected() == 49_000;
        assert burst.stockSerializationPoint();

        FlashSaleAdmission soldOut =
                flashSaleAdmission(50_000, 0, 2_000);
        assert soldOut.admitted() == 0;
        assert soldOut.rejected() == 50_000;
    }

    private static void flashSaleAtomicReservationScenarios() {
        ReservationAttempt winner =
                flashSaleReserve(1, 1, false, true);
        assert winner.outcome() == ReservationOutcome.RESERVED;
        assert winner.newAvailable() == 0;
        assert winner.reservationInserted() && winner.outboxInserted();

        ReservationAttempt loser =
                flashSaleReserve(0, 1, false, true);
        assert loser.outcome() == ReservationOutcome.SOLD_OUT;
        assert !loser.reservationInserted() && !loser.outboxInserted();

        ReservationAttempt replay =
                flashSaleReserve(0, 1, true, true);
        assert replay.outcome() == ReservationOutcome.IDEMPOTENT_REPLAY;

        ReservationAttempt conflict =
                flashSaleReserve(0, 1, true, false);
        assert conflict.outcome() == ReservationOutcome.IDEMPOTENCY_CONFLICT;
    }

    private static void flashSalePaymentExpiryScenarios() {
        ReservationTransition confirmed = flashSaleTransition(
                ReservationState.RESERVED, true, false);
        assert confirmed.next() == ReservationState.CONFIRMED;
        assert !confirmed.restoreInventory();

        ReservationTransition expired = flashSaleTransition(
                ReservationState.RESERVED, false, true);
        assert expired.next() == ReservationState.RELEASED;
        assert expired.restoreInventory();

        ReservationTransition repeatedExpiry = flashSaleTransition(
                ReservationState.RELEASED, false, true);
        assert repeatedExpiry.next() == ReservationState.RELEASED;
        assert !repeatedExpiry.restoreInventory();

        ReservationTransition latePayment = flashSaleTransition(
                ReservationState.RELEASED, true, false);
        assert latePayment.next() == ReservationState.REFUND_PENDING;
        assert latePayment.compensationRequired();
    }

    private static void flashSaleTokenConservationScenarios() {
        StockTokenPlan safe = stockTokenPlan(1_000, 400, 350, 200);
        assert safe.totalAllocated() == 950;
        assert safe.unallocated() == 50;
        assert safe.conservesStock();

        StockTokenPlan oversubscribed =
                stockTokenPlan(1_000, 400, 350, 300);
        assert oversubscribed.totalAllocated() == 1_050;
        assert !oversubscribed.conservesStock();
    }

    private enum NotificationStage {
        OUTBOX_COMMITTED,
        BROKER_ACCEPTED,
        PROVIDER_ACCEPTED,
        DELIVERED,
        BOUNCED,
        USER_READ,
        UNKNOWN
    }

    private record NotificationBoundary(
            NotificationStage stage,
            boolean durableApplicationFact,
            boolean provesHumanRead) {}

    private record InboxDelivery(
            boolean inboxClaimCommitted,
            boolean notificationInserted,
            boolean brokerAcked,
            boolean replayShouldApply) {}

    private enum EmailAction {
        SEND_WITH_STABLE_KEY,
        RECONCILE,
        WAIT_FOR_PROVIDER_EVENT,
        COMPLETE_DELIVERED,
        COMPLETE_BOUNCED
    }

    private enum RetryAction {
        ATTEMPT_NOW,
        BACKOFF_WITH_JITTER,
        TERMINAL_FAILURE,
        RECONCILIATION_REVIEW
    }

    private record NotificationLoadPlan(
            long incomingPerSecond,
            long providerPerSecond,
            long backlogGrowthPerSecond,
            long backlogAfterWindow,
            boolean separatePriorities) {}

    private static NotificationBoundary notificationBoundary(
            NotificationStage stage) {
        return new NotificationBoundary(
                stage,
                stage != NotificationStage.UNKNOWN,
                stage == NotificationStage.USER_READ);
    }

    private static InboxDelivery inboxDelivery(
            boolean crashBeforeCommit,
            boolean crashAfterCommitBeforeAck) {
        if (crashBeforeCommit) {
            return new InboxDelivery(false, false, false, true);
        }
        return new InboxDelivery(
                true,
                true,
                !crashAfterCommitBeforeAck,
                false);
    }

    private static EmailAction emailAction(
            boolean localAttemptExists,
            boolean providerOutcomeKnown,
            boolean delivered,
            boolean bounced) {
        if (!localAttemptExists) {
            return EmailAction.SEND_WITH_STABLE_KEY;
        }
        if (!providerOutcomeKnown) {
            return EmailAction.RECONCILE;
        }
        if (delivered) {
            return EmailAction.COMPLETE_DELIVERED;
        }
        if (bounced) {
            return EmailAction.COMPLETE_BOUNCED;
        }
        return EmailAction.WAIT_FOR_PROVIDER_EVENT;
    }

    private static RetryAction retryAction(
            int attempt,
            boolean permanentFailure,
            boolean ambiguousOutcome) {
        if (permanentFailure) {
            return RetryAction.TERMINAL_FAILURE;
        }
        if (ambiguousOutcome) {
            return RetryAction.RECONCILIATION_REVIEW;
        }
        return attempt == 0
                ? RetryAction.ATTEMPT_NOW
                : RetryAction.BACKOFF_WITH_JITTER;
    }

    private static NotificationLoadPlan notificationLoadPlan(
            long incomingPerSecond,
            long providerPerSecond,
            long seconds) {
        if (incomingPerSecond < 0 || providerPerSecond < 0 || seconds < 0) {
            throw new IllegalArgumentException("notification load inputs");
        }
        long growth = Math.max(0, incomingPerSecond - providerPerSecond);
        return new NotificationLoadPlan(
                incomingPerSecond,
                providerPerSecond,
                growth,
                Math.multiplyExact(growth, seconds),
                true);
    }

    private static void notificationBoundaryScenarios() {
        NotificationBoundary accepted = notificationBoundary(
                NotificationStage.PROVIDER_ACCEPTED);
        assert accepted.durableApplicationFact();
        assert !accepted.provesHumanRead();

        NotificationBoundary delivered = notificationBoundary(
                NotificationStage.DELIVERED);
        assert !delivered.provesHumanRead();

        NotificationBoundary read = notificationBoundary(
                NotificationStage.USER_READ);
        assert read.provesHumanRead();
    }

    private static void notificationInboxScenarios() {
        InboxDelivery first = inboxDelivery(false, false);
        assert first.inboxClaimCommitted();
        assert first.notificationInserted();
        assert first.brokerAcked();

        InboxDelivery crashBeforeCommit = inboxDelivery(true, false);
        assert !crashBeforeCommit.notificationInserted();
        assert crashBeforeCommit.replayShouldApply();

        InboxDelivery lostAck = inboxDelivery(false, true);
        assert lostAck.notificationInserted();
        assert !lostAck.brokerAcked();
        assert !lostAck.replayShouldApply();
    }

    private static void notificationExternalOutcomeScenarios() {
        assert emailAction(false, false, false, false)
                == EmailAction.SEND_WITH_STABLE_KEY;
        assert emailAction(true, false, false, false)
                == EmailAction.RECONCILE;
        assert emailAction(true, true, true, false)
                == EmailAction.COMPLETE_DELIVERED;
        assert emailAction(true, true, false, true)
                == EmailAction.COMPLETE_BOUNCED;
    }

    private static void notificationRetryCapacityScenarios() {
        NotificationLoadPlan overload = notificationLoadPlan(5_000, 3_000, 3_600);
        assert overload.backlogGrowthPerSecond() == 2_000;
        assert overload.backlogAfterWindow() == 7_200_000;
        assert overload.separatePriorities();

        assert retryAction(0, false, false) == RetryAction.ATTEMPT_NOW;
        assert retryAction(2, false, false) == RetryAction.BACKOFF_WITH_JITTER;
        assert retryAction(1, true, false) == RetryAction.TERMINAL_FAILURE;
        assert retryAction(1, false, true) == RetryAction.RECONCILIATION_REVIEW;
    }

    private record ActivityCapacity(
            double averageEventsPerSecond,
            long tenXPeakPerSecond,
            long logicalBytesPerDay,
            long logicalBytesForRetention) {}

    private record HistoryPartitionKey(
            String tenantId,
            String userId,
            String dayBucket) {}

    private record ActivityCursor(
            String dayBucket,
            long eventTimeMillis,
            String eventId) {}

    private record HistoryPagePlan(
            int fromCurrentBucket,
            int fromPreviousBucket,
            int returned,
            ActivityCursor nextCursor) {}

    private record HotUserPlan(
            long eventsPerWindow,
            long targetEventsPerPartition,
            int shardCount,
            int readFanOut,
            boolean requiresKWayMerge) {}

    private enum ActivityOrderPolicy {
        EVENT_TIME_THEN_ID,
        INGESTION_ORDER
    }

    private record ActivityFailurePlan(
            boolean duplicateSuppressedByEventId,
            boolean resumeFromCommittedProgress,
            ActivityOrderPolicy ordering,
            boolean crossRegionAcknowledgementCanLag) {}

    private record ActivityRetentionPlan(
            int onlineDays,
            int expiredBuckets,
            boolean bulkPartitionRemoval,
            boolean standbyQueryMayBeCanceled) {}

    private static ActivityCapacity activityCapacity(
            long eventsPerDay,
            long bytesPerEvent,
            int retentionDays) {
        if (eventsPerDay < 0 || bytesPerEvent < 0 || retentionDays < 0) {
            throw new IllegalArgumentException("activity capacity inputs");
        }
        double average = eventsPerDay / 86_400.0;
        return new ActivityCapacity(
                average,
                Math.round(average * 10),
                Math.multiplyExact(eventsPerDay, bytesPerEvent),
                Math.multiplyExact(
                        Math.multiplyExact(eventsPerDay, bytesPerEvent),
                        retentionDays));
    }

    private static HistoryPagePlan historyPagePlan(
            int currentBucketEvents,
            int previousBucketEvents,
            int pageSize,
            ActivityCursor lastReturned) {
        if (currentBucketEvents < 0 || previousBucketEvents < 0 || pageSize < 0) {
            throw new IllegalArgumentException("history page inputs");
        }
        int current = Math.min(currentBucketEvents, pageSize);
        int previous = Math.min(previousBucketEvents, pageSize - current);
        return new HistoryPagePlan(
                current,
                previous,
                current + previous,
                current + previous == pageSize ? lastReturned : null);
    }

    private static int compareNewestFirst(ActivityCursor left, ActivityCursor right) {
        int time = Long.compare(right.eventTimeMillis(), left.eventTimeMillis());
        return time != 0 ? time : right.eventId().compareTo(left.eventId());
    }

    private static HotUserPlan hotUserPlan(
            long eventsPerWindow,
            long targetEventsPerPartition) {
        if (eventsPerWindow < 0 || targetEventsPerPartition <= 0) {
            throw new IllegalArgumentException("hot-user inputs");
        }
        long shards = (eventsPerWindow + targetEventsPerPartition - 1)
                / targetEventsPerPartition;
        int shardCount = Math.toIntExact(Math.max(1, shards));
        return new HotUserPlan(
                eventsPerWindow,
                targetEventsPerPartition,
                shardCount,
                shardCount,
                shardCount > 1);
    }

    private static ActivityFailurePlan activityFailurePlan(
            boolean stableEventId,
            boolean committedProgress,
            ActivityOrderPolicy ordering,
            boolean crossRegionSynchronous) {
        return new ActivityFailurePlan(
                stableEventId,
                committedProgress,
                ordering,
                !crossRegionSynchronous);
    }

    private static ActivityRetentionPlan activityRetentionPlan(
            int onlineDays,
            int expiredBuckets,
            boolean partitionedByTime,
            boolean reportingOnHotStandby) {
        if (onlineDays < 0 || expiredBuckets < 0) {
            throw new IllegalArgumentException("retention inputs");
        }
        return new ActivityRetentionPlan(
                onlineDays,
                expiredBuckets,
                partitionedByTime && expiredBuckets > 0,
                reportingOnHotStandby);
    }

    private static void activityCapacityScenarios() {
        ActivityCapacity capacity = activityCapacity(100_000_000, 500, 30);
        assert Math.abs(capacity.averageEventsPerSecond() - 1_157.407) < 0.001;
        assert capacity.tenXPeakPerSecond() == 11_574;
        assert capacity.logicalBytesPerDay() == 50_000_000_000L;
        assert capacity.logicalBytesForRetention() == 1_500_000_000_000L;
    }

    private static void activityPartitionAndPageScenarios() {
        HistoryPartitionKey userDay = new HistoryPartitionKey(
                "tenant-7", "user-42", "2026-10-21");
        HistoryPartitionKey otherUser = new HistoryPartitionKey(
                "tenant-7", "user-43", "2026-10-21");
        assert !userDay.equals(otherUser);

        ActivityCursor tieA = new ActivityCursor(
                "2026-10-20", 1_760_000_000_000L, "evt-b");
        ActivityCursor tieB = new ActivityCursor(
                "2026-10-20", 1_760_000_000_000L, "evt-a");
        assert compareNewestFirst(tieA, tieB) < 0;

        HistoryPagePlan page = historyPagePlan(7, 18, 20, tieB);
        assert page.fromCurrentBucket() == 7;
        assert page.fromPreviousBucket() == 13;
        assert page.returned() == 20;
        assert page.nextCursor().equals(tieB);
    }

    private static void activityHotUserScenarios() {
        HotUserPlan normal = hotUserPlan(500_000, 1_000_000);
        assert normal.shardCount() == 1;
        assert !normal.requiresKWayMerge();

        HotUserPlan hot = hotUserPlan(3_600_000, 1_000_000);
        assert hot.shardCount() == 4;
        assert hot.readFanOut() == 4;
        assert hot.requiresKWayMerge();
    }

    private static void activityRetentionAndFailureScenarios() {
        ActivityFailurePlan replay = activityFailurePlan(
                true, true, ActivityOrderPolicy.EVENT_TIME_THEN_ID, false);
        assert replay.duplicateSuppressedByEventId();
        assert replay.resumeFromCommittedProgress();
        assert replay.ordering() == ActivityOrderPolicy.EVENT_TIME_THEN_ID;
        assert replay.crossRegionAcknowledgementCanLag();

        ActivityRetentionPlan retention = activityRetentionPlan(
                30, 1, true, true);
        assert retention.bulkPartitionRemoval();
        assert retention.standbyQueryMayBeCanceled();
    }

    private record ComponentExplanation(
            String input,
            String ownedState,
            String mechanism,
            String successGuarantee,
            String failureWindow,
            String stressMetric,
            String alternative) {}

    private enum RelayCrash {
        BEFORE_BROKER_ACK,
        AFTER_ACK_BEFORE_PROGRESS,
        AFTER_PROGRESS
    }

    private enum RelayRecovery {
        RETRY_STABLE_EVENT_ID,
        REPLAY_POSSIBLE_SAME_ID,
        ALREADY_COMPLETE
    }

    private record RelayHealth(
            long backlogRows,
            long servicePerSecond,
            long oldestPendingSeconds,
            long freshnessSloSeconds,
            boolean freshnessSloBreached) {}

    private enum CacheInterviewAction {
        RETURN_FRESH_HIT,
        LOAD_AND_PUBLISH_IF_OWNER,
        SERVE_DECLARED_STALE,
        REJECT_OR_WAIT
    }

    private record CacheExplanation(
            CacheInterviewAction action,
            long authorityBudgetMillis,
            boolean publicationAuthorized) {}

    private enum PoolInterviewOutcome {
        LEASE_GRANTED,
        ACQUISITION_TIMEOUT,
        ROLLBACK_THEN_RETURN,
        EVICT_PHYSICAL_CONNECTION
    }

    private record PoolExplanation(
            int maximumPoolSize,
            int active,
            int pending,
            long acquisitionMillis,
            long acquisitionBudgetMillis,
            PoolInterviewOutcome outcome) {}

    private record CapacityDiagnosis(
            double missRequestsPerSecond,
            double databaseHeadroomPerSecond,
            long queueGrowthPerSecond,
            long queueGrowthPerHour,
            boolean databaseOverloaded,
            boolean queueUnstable) {}

    private record IndexEvidence(
            long readMillisSavedPerSecond,
            long writeMillisAddedPerSecond,
            long heapFetches,
            long returnedRows,
            boolean benefitExceedsWriteCost,
            boolean inspectVisibilityMaintenance) {}

    private enum PublicationAction {
        SERVE_MATCHING_REVISION,
        RELOAD_FROM_AUTHORITY,
        FAIL_OPEN_MEMBERSHIP_CHECK
    }

    private record PublicationDiagnosis(
            long requiredRevision,
            long observedRevision,
            boolean membershipSnapshotComplete,
            PublicationAction action) {}

    private enum BoundaryAction {
        INVESTIGATE_NON_QUERY_RESIDENCY,
        OWNER_AUTHORIZED_PUBLICATION,
        RETURN_WITHOUT_PUBLICATION,
        REQUIRE_FENCED_BUSINESS_WRITE,
        MOVE_SIDE_EFFECT_TO_OUTBOX,
        TRANSACTIONAL_INTENT_READY,
        RETIRE_POOL_CONNECTION_ON_RETURN,
        EVICT_POOL_CONNECTION,
        INVESTIGATE_DATABASE_PROXY_OR_NETWORK
    }

    private record StudyRehearsal(boolean storage, boolean correctness,
            boolean connections, boolean design) {
        boolean ready() { return storage && correctness && connections && design; }
    }

    private static boolean storageSessionReady(boolean split, boolean versions,
            boolean index, boolean metadata) {
        return split && versions && index && metadata;
    }

    private static boolean correctnessSessionReady(boolean lostUpdate, boolean versionFix,
            boolean writeSkew, boolean crossRowFix, long article, long draft, long revocation) {
        if (article < 0 || draft < 0 || revocation < 0) throw new IllegalArgumentException("stale budget");
        return lostUpdate && versionFix && writeSkew && crossRowFix
                && article >= draft && revocation == 0;
    }

    private static boolean connectionSessionReady(List<String> cold, List<String> reused,
            long checkoutMillis, long handshakeMillis, long queryMillis) {
        Objects.requireNonNull(cold); Objects.requireNonNull(reused);
        if (checkoutMillis < 0 || handshakeMillis < 0 || queryMillis < 0) {
            throw new IllegalArgumentException("connection clocks");
        }
        return cold.containsAll(List.of("TCP", "TLS", "AUTH"))
                && !reused.contains("TCP") && !reused.contains("TLS")
                && reused.contains("LEASE");
    }

    private static boolean designSessionReady(boolean requirements, boolean success,
            boolean timeout, boolean duplicate, boolean tenX, boolean cacheDown,
            boolean deletionReview) {
        return requirements && success && timeout && duplicate && tenX && cacheDown && deletionReview;
    }

    private static List<String> explanationGaps(ComponentExplanation explanation) {
        Objects.requireNonNull(explanation, "explanation");
        List<String> gaps = new ArrayList<>();
        if (explanation.input() == null || explanation.input().isBlank()) gaps.add("input");
        if (explanation.ownedState() == null || explanation.ownedState().isBlank()) {
            gaps.add("owned state");
        }
        if (explanation.mechanism() == null || explanation.mechanism().isBlank()) {
            gaps.add("mechanism");
        }
        if (explanation.successGuarantee() == null
                || explanation.successGuarantee().isBlank()) {
            gaps.add("success guarantee");
        }
        if (explanation.failureWindow() == null || explanation.failureWindow().isBlank()) {
            gaps.add("failure window");
        }
        if (explanation.stressMetric() == null || explanation.stressMetric().isBlank()) {
            gaps.add("stress metric");
        }
        if (explanation.alternative() == null || explanation.alternative().isBlank()) {
            gaps.add("alternative");
        }
        return List.copyOf(gaps);
    }

    private static RelayRecovery relayRecovery(RelayCrash crash) {
        Objects.requireNonNull(crash, "crash");
        return switch (crash) {
            case BEFORE_BROKER_ACK -> RelayRecovery.RETRY_STABLE_EVENT_ID;
            case AFTER_ACK_BEFORE_PROGRESS -> RelayRecovery.REPLAY_POSSIBLE_SAME_ID;
            case AFTER_PROGRESS -> RelayRecovery.ALREADY_COMPLETE;
        };
    }

    private static RelayHealth relayHealth(
            long backlogRows,
            long servicePerSecond,
            long oldestPendingSeconds,
            long freshnessSloSeconds) {
        if (backlogRows < 0 || servicePerSecond < 0 || oldestPendingSeconds < 0
                || freshnessSloSeconds < 0) {
            throw new IllegalArgumentException("relay health inputs");
        }
        return new RelayHealth(
                backlogRows,
                servicePerSecond,
                oldestPendingSeconds,
                freshnessSloSeconds,
                oldestPendingSeconds > freshnessSloSeconds);
    }

    private static CacheExplanation cacheExplanation(
            boolean freshHit,
            boolean ownsLease,
            boolean staleAvailable,
            long authorityBudgetMillis,
            long requiredLoadMillis) {
        if (authorityBudgetMillis < 0 || requiredLoadMillis < 0) {
            throw new IllegalArgumentException("cache explanation inputs");
        }
        if (freshHit) {
            return new CacheExplanation(
                    CacheInterviewAction.RETURN_FRESH_HIT,
                    authorityBudgetMillis,
                    false);
        }
        if (ownsLease && authorityBudgetMillis >= requiredLoadMillis) {
            return new CacheExplanation(
                    CacheInterviewAction.LOAD_AND_PUBLISH_IF_OWNER,
                    authorityBudgetMillis,
                    true);
        }
        if (staleAvailable) {
            return new CacheExplanation(
                    CacheInterviewAction.SERVE_DECLARED_STALE,
                    authorityBudgetMillis,
                    false);
        }
        return new CacheExplanation(
                CacheInterviewAction.REJECT_OR_WAIT,
                authorityBudgetMillis,
                false);
    }

    private static PoolExplanation poolExplanation(
            int maximumPoolSize,
            int active,
            int pending,
            long acquisitionMillis,
            long acquisitionBudgetMillis,
            boolean transactionOpen,
            boolean physicalConnectionBroken) {
        if (maximumPoolSize <= 0 || active < 0 || active > maximumPoolSize || pending < 0
                || acquisitionMillis < 0 || acquisitionBudgetMillis < 0) {
            throw new IllegalArgumentException("pool explanation inputs");
        }
        PoolInterviewOutcome outcome;
        if (active == maximumPoolSize && acquisitionMillis >= acquisitionBudgetMillis) {
            outcome = PoolInterviewOutcome.ACQUISITION_TIMEOUT;
        } else if (physicalConnectionBroken) {
            outcome = PoolInterviewOutcome.EVICT_PHYSICAL_CONNECTION;
        } else if (transactionOpen) {
            outcome = PoolInterviewOutcome.ROLLBACK_THEN_RETURN;
        } else {
            outcome = PoolInterviewOutcome.LEASE_GRANTED;
        }
        return new PoolExplanation(
                maximumPoolSize,
                active,
                pending,
                acquisitionMillis,
                acquisitionBudgetMillis,
                outcome);
    }

    private static CapacityDiagnosis capacityDiagnosis(
            long requestsPerSecond,
            double hitRatio,
            long safeDatabaseReadsPerSecond,
            long queueArrivalsPerSecond,
            long queueServicePerSecond) {
        if (requestsPerSecond < 0 || hitRatio < 0 || hitRatio > 1
                || safeDatabaseReadsPerSecond < 0 || queueArrivalsPerSecond < 0
                || queueServicePerSecond < 0) {
            throw new IllegalArgumentException("capacity diagnosis inputs");
        }
        double misses = requestsPerSecond * (1.0 - hitRatio);
        long growth = Math.max(0, queueArrivalsPerSecond - queueServicePerSecond);
        return new CapacityDiagnosis(
                misses,
                safeDatabaseReadsPerSecond - misses,
                growth,
                Math.multiplyExact(growth, 3_600),
                misses > safeDatabaseReadsPerSecond,
                queueArrivalsPerSecond > queueServicePerSecond);
    }

    private static IndexEvidence indexEvidence(
            long readQueriesPerSecond,
            long millisSavedPerRead,
            long writesPerSecond,
            long millisAddedPerWrite,
            long heapFetches,
            long returnedRows) {
        if (readQueriesPerSecond < 0 || millisSavedPerRead < 0 || writesPerSecond < 0
                || millisAddedPerWrite < 0 || heapFetches < 0 || returnedRows < 0) {
            throw new IllegalArgumentException("index evidence inputs");
        }
        long readSavings = Math.multiplyExact(readQueriesPerSecond, millisSavedPerRead);
        long writeCost = Math.multiplyExact(writesPerSecond, millisAddedPerWrite);
        return new IndexEvidence(
                readSavings,
                writeCost,
                heapFetches,
                returnedRows,
                readSavings > writeCost,
                heapFetches > 0 && returnedRows > 0);
    }

    private static PublicationDiagnosis publicationDiagnosis(
            long requiredRevision,
            long observedRevision,
            boolean membershipSnapshotComplete,
            boolean membershipFilterSaysAbsent) {
        if (requiredRevision < 0 || observedRevision < 0) {
            throw new IllegalArgumentException("publication diagnosis inputs");
        }
        PublicationAction action;
        if (membershipFilterSaysAbsent && !membershipSnapshotComplete) {
            action = PublicationAction.FAIL_OPEN_MEMBERSHIP_CHECK;
        } else if (observedRevision != requiredRevision) {
            action = PublicationAction.RELOAD_FROM_AUTHORITY;
        } else {
            action = PublicationAction.SERVE_MATCHING_REVISION;
        }
        return new PublicationDiagnosis(
                requiredRevision, observedRevision, membershipSnapshotComplete, action);
    }

    private static BoundaryAction poolResidencyDiagnosis(
            int poolSize, int slowQueries, int activeConnections) {
        if (poolSize <= 0 || slowQueries < 0 || activeConnections < 0
                || activeConnections > poolSize) {
            throw new IllegalArgumentException("pool residency inputs");
        }
        if (activeConnections == poolSize && slowQueries < activeConnections) {
            return BoundaryAction.INVESTIGATE_NON_QUERY_RESIDENCY;
        }
        return BoundaryAction.INVESTIGATE_DATABASE_PROXY_OR_NETWORK;
    }

    private static BoundaryAction leaseExpiryDiagnosis(
            long taskMillis, long leaseMillis, boolean businessWriteFenced) {
        if (taskMillis < 0 || leaseMillis < 0) {
            throw new IllegalArgumentException("lease diagnosis inputs");
        }
        if (taskMillis <= leaseMillis) {
            return BoundaryAction.OWNER_AUTHORIZED_PUBLICATION;
        }
        return businessWriteFenced
                ? BoundaryAction.REQUIRE_FENCED_BUSINESS_WRITE
                : BoundaryAction.RETURN_WITHOUT_PUBLICATION;
    }

    private static BoundaryAction serializableSideEffectDiagnosis(
            boolean intentStoredInsideDatabaseTransaction) {
        return intentStoredInsideDatabaseTransaction
                ? BoundaryAction.TRANSACTIONAL_INTENT_READY
                : BoundaryAction.MOVE_SIDE_EFFECT_TO_OUTBOX;
    }

    private static BoundaryAction connectionLifetimeDiagnosis(
            boolean connectionInUse,
            boolean maxLifetimeElapsed,
            boolean poolExplicitlyEvictedConnection) {
        if (connectionInUse && maxLifetimeElapsed && !poolExplicitlyEvictedConnection) {
            return BoundaryAction.INVESTIGATE_DATABASE_PROXY_OR_NETWORK;
        }
        if (poolExplicitlyEvictedConnection) {
            return BoundaryAction.EVICT_POOL_CONNECTION;
        }
        return BoundaryAction.RETIRE_POOL_CONNECTION_ON_RETURN;
    }

    private static void explanationContractScenarios() {
        ComponentExplanation worker = new ComponentExplanation(
                "order event with event_id",
                "consumer progress and inbox claim; not order truth",
                "poll, unique inbox insert, apply, then acknowledge",
                "local inbox and projection committed",
                "before commit: replay; after commit before ack: duplicate delivery",
                "oldest unprocessed age and processing-error rate",
                "CDC projection instead of broker polling");
        assert explanationGaps(worker).isEmpty();

        ComponentExplanation vague = new ComponentExplanation(
                "message", "", "Kafka", "", "", "throughput", "");
        assert explanationGaps(vague).equals(List.of(
                "owned state", "success guarantee", "failure window", "alternative"));
    }

    private static void outboxRelayExplanationScenarios() {
        assert relayRecovery(RelayCrash.BEFORE_BROKER_ACK)
                == RelayRecovery.RETRY_STABLE_EVENT_ID;
        assert relayRecovery(RelayCrash.AFTER_ACK_BEFORE_PROGRESS)
                == RelayRecovery.REPLAY_POSSIBLE_SAME_ID;
        assert relayRecovery(RelayCrash.AFTER_PROGRESS)
                == RelayRecovery.ALREADY_COMPLETE;

        RelayHealth relay = relayHealth(12_000, 8_000, 95, 30);
        assert relay.backlogRows() == 12_000;
        assert relay.oldestPendingSeconds() == 95;
        assert relay.freshnessSloBreached();
    }

    private static void cacheLoaderExplanationScenarios() {
        CacheExplanation strict = cacheExplanation(
                false, true, false, 60, 50);
        assert strict.action() == CacheInterviewAction.LOAD_AND_PUBLISH_IF_OWNER;
        assert strict.authorityBudgetMillis() == 60;
        assert strict.publicationAuthorized();

        CacheExplanation stale = cacheExplanation(
                false, false, true, 0, 50);
        assert stale.action() == CacheInterviewAction.SERVE_DECLARED_STALE;
        assert !stale.publicationAuthorized();

        assert cacheExplanation(false, true, false, 40, 50).action()
                == CacheInterviewAction.REJECT_OR_WAIT;
    }

    private static void connectionPoolExplanationScenarios() {
        PoolExplanation waiting = poolExplanation(
                20, 20, 7, 180, 100, false, false);
        assert waiting.outcome() == PoolInterviewOutcome.ACQUISITION_TIMEOUT;

        PoolExplanation dirty = poolExplanation(
                20, 11, 0, 12, 100, true, false);
        assert dirty.outcome() == PoolInterviewOutcome.ROLLBACK_THEN_RETURN;

        PoolExplanation broken = poolExplanation(
                20, 11, 0, 12, 100, false, true);
        assert broken.outcome() == PoolInterviewOutcome.EVICT_PHYSICAL_CONNECTION;
    }

    private static void absoluteRateExerciseScenarios() {
        CapacityDiagnosis diagnosis = capacityDiagnosis(100_000, 0.99, 500, 5_000, 3_000);
        assert Math.abs(diagnosis.missRequestsPerSecond() - 1_000.0) < 0.001;
        assert Math.abs(diagnosis.databaseHeadroomPerSecond() + 500.0) < 0.001;
        assert diagnosis.databaseOverloaded();
        assert diagnosis.queueUnstable();
        assert diagnosis.queueGrowthPerSecond() == 2_000;
        assert diagnosis.queueGrowthPerHour() == 7_200_000;
    }

    private static void indexEvidenceExerciseScenarios() {
        IndexEvidence useful = indexEvidence(200, 8, 50, 2, 0, 10_000);
        assert useful.benefitExceedsWriteCost();
        assert !useful.inspectVisibilityMaintenance();

        IndexEvidence heapBound = indexEvidence(10, 2, 400, 1, 8_000, 10_000);
        assert !heapBound.benefitExceedsWriteCost();
        assert heapBound.inspectVisibilityMaintenance();
    }

    private static void publicationLifecycleExerciseScenarios() {
        PublicationDiagnosis staleLayer = publicationDiagnosis(42, 41, true, false);
        assert staleLayer.action() == PublicationAction.RELOAD_FROM_AUTHORITY;

        PublicationDiagnosis incompleteBloom = publicationDiagnosis(42, 42, false, true);
        assert incompleteBloom.action() == PublicationAction.FAIL_OPEN_MEMBERSHIP_CHECK;

        PublicationDiagnosis fresh = publicationDiagnosis(42, 42, true, false);
        assert fresh.action() == PublicationAction.SERVE_MATCHING_REVISION;
    }

    private static void authorityBoundaryExerciseScenarios() {
        assert poolResidencyDiagnosis(20, 5, 20)
                == BoundaryAction.INVESTIGATE_NON_QUERY_RESIDENCY;
        assert leaseExpiryDiagnosis(6_000, 5_000, false)
                == BoundaryAction.RETURN_WITHOUT_PUBLICATION;
        assert leaseExpiryDiagnosis(6_000, 5_000, true)
                == BoundaryAction.REQUIRE_FENCED_BUSINESS_WRITE;
        assert leaseExpiryDiagnosis(4_000, 5_000, false)
                == BoundaryAction.OWNER_AUTHORIZED_PUBLICATION;
        assert serializableSideEffectDiagnosis(false)
                == BoundaryAction.MOVE_SIDE_EFFECT_TO_OUTBOX;
        assert serializableSideEffectDiagnosis(true)
                == BoundaryAction.TRANSACTIONAL_INTENT_READY;
        assert connectionLifetimeDiagnosis(true, true, false)
                == BoundaryAction.INVESTIGATE_DATABASE_PROXY_OR_NETWORK;
        assert connectionLifetimeDiagnosis(false, true, false)
                == BoundaryAction.RETIRE_POOL_CONNECTION_ON_RETURN;
        assert connectionLifetimeDiagnosis(true, false, true)
                == BoundaryAction.EVICT_POOL_CONNECTION;
    }

    private static void practicalStorageSessionScenarios() {
        assert storageSessionReady(true, true, true, true);
        assert !storageSessionReady(true, true, true, false);
    }

    private static void practicalCorrectnessSessionScenarios() {
        assert correctnessSessionReady(true, true, true, true, 60, 5, 0);
        assert !correctnessSessionReady(true, true, false, false, 60, 5, 0);
        assert !correctnessSessionReady(true, true, true, true, 60, 5, 1);
    }

    private static void practicalConnectionSessionScenarios() {
        assert connectionSessionReady(
                List.of("POOL", "DRIVER", "DNS", "TCP", "TLS", "AUTH", "SESSION"),
                List.of("POOL", "LEASE", "QUERY"), 180, 95, 12);
        assert !connectionSessionReady(
                List.of("POOL", "TCP", "TLS", "AUTH"),
                List.of("POOL", "TCP", "LEASE"), 4, 0, 12);
    }

    private static void practicalDesignSessionScenarios() {
        StudyRehearsal complete = new StudyRehearsal(true, true, true,
                designSessionReady(true, true, true, true, true, true, true));
        assert complete.ready();
        assert !designSessionReady(true, true, false, true, true, true, true);
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
        tinyBPlusInsertScenarios();
        linkedLeafRangeScenarios();
        secondaryLookupPlanScenarios();
        indexOnlyVisibilityScenarios();
        lsmWritePathScenarios();
        lsmSnapshotReadScenarios();
        lsmTombstoneScenarios();
        lsmAmplificationScenarios();
        lsmThreePlaceVisibilityScenarios();
        lsmCompactionMergeSafetyScenarios();
        lsmBloomProbeBudgetScenarios();
        lsmCompactionDebtBudgetScenarios();
        endpointCompositeIndexScenarios();
        endpointPlanShapeScenarios();
        endpointPlanEvidenceScenarios();
        endpointKeysetPaginationScenarios();
        columnSelectionScenarios();
        vectorBatchScenarios();
        granulePruningScenarios();
        projectionScenarios();
        statementSnapshotScenarios();
        writeSkewScenarios();
        lostUpdateScenarios();
        serializationRetryScenarios();
        nonRepeatableReadLabScenarios();
        writeSkewRetryLabScenarios();
        guardRowLabScenarios();
        deadlockOrderingLabScenarios();
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
        evictionPolicyTraceScenarios();
        lfuPhaseChangeLabScenarios();
        twoQueueGhostLabScenarios();
        cmsScanPollutionLabScenarios();
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
        jdbcDriverSelectionScenarios();
        dataSourceAcquisitionScenarios();
        jdbcLayerStackScenarios();
        postgresHandshakeVariantScenarios();
        postgresChronologyScenarios();
        postgresFramingScenarios();
        postgresIdentityScenarios();
        poolTraceCorrelationScenarios();
        postgresSocketAttemptScenarios();
        tlsPolicyScenarios();
        scramNegotiationScenarios();
        sessionBaselineScenarios();
        mysqlHandshakeOrderScenarios();
        mysqlPluginNegotiationScenarios();
        cachingSha2Scenarios();
        mysqlSessionContractScenarios();
        pgQueryModeScenarios();
        extendedQueryCycleScenarios();
        preparedBoundaryScenarios();
        fetchAndLifecycleScenarios();
        revisionKeyScenarios();
        revisionReadContractScenarios();
        publicationVisibilityScenarios();
        productionBudgetScenarios();
        cacheEnvelopeScenarios();
        ownerCheckedPublicationScenarios();
        cacheAsideCoordinatorScenarios();
        cooldownAndBulkheadScenarios();
        mutableRowOutboxTransactionScenarios();
        durableOutboxRelayScenarios();
        monotonicCacheEventScenarios();
        staleReaderFenceScenarios();
        faultOracleScenarios();
        leaseFaultInjectionScenarios();
        writeSkewHarnessScenarios();
        unknownCommitRecoveryScenarios();
        databaseRecoveryRunbookScenarios();
        cacheRecoveryRunbookScenarios();
        poolExhaustionRunbookScenarios();
        certificateCredentialRotationScenarios();
        seniorVisibilityStorageReviewScenarios();
        seniorCacheSafetyReviewScenarios();
        seniorPoolTimeoutHealthReviewScenarios();
        seniorProtocolOutcomeReviewScenarios();
        productionStorageAcceptanceScenarios();
        productionCacheAcceptanceScenarios();
        productionConnectivitySecurityAcceptanceScenarios();
        productionReleaseRecoveryAcceptanceScenarios();
        databaseReadPipelineScenarios();
        slottedPageScenarios();
        bufferLayerScenarios();
        mvccAndProtocolScenarios();
        committedEditTransactionScenarios();
        walCrashBoundaryScenarios();
        lostEditResponseScenarios();
        checkpointVacuumDiagnosisScenarios();
        cacheLeaseExpiryRaceScenarios();
        cacheGenerationValueRaceScenarios();
        cacheNegativeResurrectionRaceScenarios();
        cacheFleetLoadBudgetScenarios();
        poolResidencyArithmeticScenarios();
        poolQueueStabilityScenarios();
        layeredPoolQueueScenarios();
        poolConfigurationAndOutcomeScenarios();
        protocolSslRequestScenarios();
        protocolSimpleQueryFramingScenarios();
        protocolHandshakeAdmissionScenarios();
        protocolSecurityContractScenarios();
        cacheStoryFirstProbeScenarios();
        cacheStoryHealthyMissScenarios();
        cacheStoryFailureBoundaryScenarios();
        cacheStoryExpiredLeaseScenarios();
        cmsTrafficArithmeticScenarios();
        cmsPublishTransactionScenarios();
        cmsReadPolicyScenarios();
        cmsLayerCollapseScenarios();
        flashSaleAdmissionScenarios();
        flashSaleAtomicReservationScenarios();
        flashSalePaymentExpiryScenarios();
        flashSaleTokenConservationScenarios();
        notificationBoundaryScenarios();
        notificationInboxScenarios();
        notificationExternalOutcomeScenarios();
        notificationRetryCapacityScenarios();
        activityCapacityScenarios();
        activityPartitionAndPageScenarios();
        activityHotUserScenarios();
        activityRetentionAndFailureScenarios();
        explanationContractScenarios();
        outboxRelayExplanationScenarios();
        cacheLoaderExplanationScenarios();
        connectionPoolExplanationScenarios();
        absoluteRateExerciseScenarios();
        indexEvidenceExerciseScenarios();
        publicationLifecycleExerciseScenarios();
        authorityBoundaryExerciseScenarios();
        practicalStorageSessionScenarios();
        practicalCorrectnessSessionScenarios();
        practicalConnectionSessionScenarios();
        practicalDesignSessionScenarios();
        System.out.println("PASS 176 scenario groups: practical rehearsal sequence, atomicity, idempotency, replica routing, convergence, capacity, composite range, secondary bytes, leaf split, tiny B+ insert propagation, linked-leaf range scan, secondary lookup plan, index-only visibility, LSM recovery, snapshot reads, tombstones, amplification, three-place visibility, compaction merge safety, Bloom probe budget, compaction debt budget, endpoint composite index, endpoint plan shapes, endpoint plan evidence, endpoint keyset pagination, column selection, vector batches, granule pruning, CDC projection, statement snapshots, write skew, lost updates, serialization retry, two-session non-repeatable read, write-skew retry lab, guard-row serialization and retry, deadlock ordering and retry, topology estimate, admission, two-level invalidation, cache-key scope, cache-aside race, read-through outcomes, write-through repair, write-behind replay, exact LRU, LFU with aging, FIFO, full 2Q, eviction-policy trace lab, LFU phase-change lab, 2Q ghost-reload lab, CMS scan-pollution lab, stampede arithmetic, local single-flight, fill lease ownership, bounded fallback, negative-cache semantics, Bloom bit membership, Bloom sizing, Bloom lifecycle safety, TTL jitter, cold-start warming, soft/hard TTL, outage protection, pool-boundary budget, logical connection lease, explicit database routing, pooling-mode deadline, connection startup cost, connection churn, pool concurrency gate, connection ownership, Hikari controls, timeout stack, workload pool sizing, pool telemetry, JDBC driver selection, DataSource acquisition, JDBC layer stack, PostgreSQL handshake variants, PostgreSQL chronology, PostgreSQL framing, PostgreSQL identity assurance, pool trace correlation, PostgreSQL socket attempts, TLS identity policy, SCRAM negotiation, session baseline placement, MySQL handshake order, MySQL plugin negotiation, caching_sha2_password paths, MySQL session contract, pgJDBC query mode, extended-query cycle, parameter/identifier boundary, fetch and lifecycle, immutable revision keys, authorization-first cache reads, publication visibility, production timeout budget, cache-envelope parsing, owner-checked publication, cache-aside coordination, Redis cooldown and DB bulkhead, mutable-row outbox transaction, durable relay recovery, monotonic cache events, stale-reader fencing, fault-test oracles, lease fault injection, write-skew harness, unknown-commit recovery, database recovery runbook, cache recovery runbook, pool exhaustion diagnosis, certificate and credential rotation, senior visibility and storage review, senior cache safety review, senior pool timeout and health review, senior protocol and outcome review, production storage acceptance, production cache acceptance, production connectivity and security acceptance, production release and recovery acceptance, database read pipeline, slotted-page relocation, buffer-layer ownership, MVCC and protocol materialization, committed edit transaction, WAL crash boundary, lost edit response, checkpoint versus vacuum diagnosis, cache lease-expiry race, generation-fenced value publication, generation-fenced negative publication, fleet miss-load budget, connection residency arithmetic, pool queue stability, layered pool queues, pool configuration and ambiguous outcome, SSLRequest bytes, simple-query framing, handshake admission, protocol security contract, first-probe envelope semantics, owner-safe miss coalescing, Redis/database failure boundaries, expired-lease data contract, CMS traffic arithmetic, CMS publish transaction, CMS read policy, CMS layer collapse, flash-sale admission, atomic reservation, payment-expiry state race, stock-token conservation, notification lifecycle boundaries, transactional inbox dedupe, external email reconciliation, notification retry and backlog policy, activity capacity arithmetic, activity partition and cursor paging, hot-user shard fan-out, activity retention and failure policy, explanation contract lint, outbox relay interview trace, cache-loader interview trace, connection-pool interview trace, absolute-rate exercise, index-evidence exercise, publication-lifecycle exercise, authority-boundary exercise");
    }
}


