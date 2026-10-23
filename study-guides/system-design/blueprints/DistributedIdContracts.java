import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Executable invariants for Blueprint 5. Run with: java -ea DistributedIdContracts.java */
public final class DistributedIdContracts {
    static final int TIMESTAMP_BITS = 41;
    static final int WORKER_BITS = 10;
    static final int SEQUENCE_BITS = 12;
    static final long MAX_TIMESTAMP = (1L << TIMESTAMP_BITS) - 1;
    static final long MAX_WORKER = (1L << WORKER_BITS) - 1;
    static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    static final long MAX_UUID_V7_TIMESTAMP = (1L << 48) - 1;

    record WorkerLease(long workerId, long generation, long expiresAtMillis) {}
    record Parts(long elapsedMillis, long workerId, long sequence) {}
    record Range(long startInclusive, long endExclusive) {
        Range {
            if (startInclusive < 0 || endExclusive <= startInclusive) {
                throw new IllegalArgumentException("invalid range");
            }
        }

        long size() { return endExclusive - startInclusive; }
    }

    static final class SnowflakeGenerator {
        private final long customEpochMillis;
        private final long workerId;
        private final long leaseGeneration;
        private long lastTimestampMillis = -1;
        private long sequence;

        SnowflakeGenerator(long customEpochMillis, WorkerLease lease) {
            if (customEpochMillis < 0 || lease.workerId() < 0 || lease.workerId() > MAX_WORKER) {
                throw new IllegalArgumentException("epoch and worker must fit their bit budgets");
            }
            this.customEpochMillis = customEpochMillis;
            this.workerId = lease.workerId();
            this.leaseGeneration = lease.generation();
        }

        synchronized long next(long nowMillis, WorkerLease lease) {
            requireLiveLease(nowMillis, lease);
            if (nowMillis < lastTimestampMillis) {
                throw new IllegalStateException("wall clock moved backward");
            }
            long elapsed = nowMillis - customEpochMillis;
            if (elapsed < 0 || elapsed > MAX_TIMESTAMP) {
                throw new IllegalStateException("timestamp exceeds 41-bit epoch window");
            }
            if (nowMillis == lastTimestampMillis) {
                if (sequence == MAX_SEQUENCE) {
                    throw new IllegalStateException("sequence exhausted; wait for the next millisecond");
                }
                sequence++;
            } else {
                lastTimestampMillis = nowMillis;
                sequence = 0;
            }
            return (elapsed << (WORKER_BITS + SEQUENCE_BITS))
                    | (workerId << SEQUENCE_BITS)
                    | sequence;
        }

        private void requireLiveLease(long nowMillis, WorkerLease lease) {
            if (lease.workerId() != workerId || lease.generation() != leaseGeneration) {
                throw new IllegalStateException("stale or mismatched worker lease");
            }
            if (nowMillis >= lease.expiresAtMillis()) {
                throw new IllegalStateException("worker lease expired");
            }
        }
    }

    static final class SegmentAllocator {
        private long next;

        SegmentAllocator(long first) {
            if (first < 0) throw new IllegalArgumentException("negative first ID");
            next = first;
        }

        synchronized Range allocate(long size) {
            if (size <= 0) throw new IllegalArgumentException("range size must be positive");
            long end = Math.addExact(next, size);
            Range range = new Range(next, end);
            next = end;
            return range;
        }
    }

    static Parts decode(long id) {
        if (id < 0) throw new IllegalArgumentException("sign bit must remain zero");
        return new Parts(
                id >>> (WORKER_BITS + SEQUENCE_BITS),
                (id >>> SEQUENCE_BITS) & MAX_WORKER,
                id & MAX_SEQUENCE);
    }

    static UUID uuidV7(long unixMillis, int randA, long randB) {
        if (unixMillis < 0 || unixMillis > MAX_UUID_V7_TIMESTAMP
                || randA < 0 || randA > 0xFFF
                || randB < 0 || randB > 0x3FFF_FFFF_FFFF_FFFFL) {
            throw new IllegalArgumentException("UUIDv7 field exceeds its bit budget");
        }
        long most = (unixMillis << 16) | 0x7000L | randA;
        long least = 0x8000_0000_0000_0000L | randB;
        return new UUID(most, least);
    }

    static long uuidV7Timestamp(UUID uuid) {
        if (uuid.version() != 7 || uuid.variant() != 2) {
            throw new IllegalArgumentException("not an RFC 9562 UUIDv7");
        }
        return (uuid.getMostSignificantBits() >>> 16) & MAX_UUID_V7_TIMESTAMP;
    }

    static void expectFailure(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected failure");
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException expected) {
            // Expected contract rejection.
        }
    }

    public static void main(String[] args) {
        long epoch = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
        long now = epoch + 1_000;
        WorkerLease worker17 = new WorkerLease(17, 8, now + 10_000);
        WorkerLease worker18 = new WorkerLease(18, 3, now + 10_000);
        SnowflakeGenerator first = new SnowflakeGenerator(epoch, worker17);
        SnowflakeGenerator second = new SnowflakeGenerator(epoch, worker18);

        long a = first.next(now, worker17);
        long b = first.next(now, worker17);
        long c = second.next(now, worker18);
        assert decode(a).equals(new Parts(1_000, 17, 0));
        assert decode(b).equals(new Parts(1_000, 17, 1));
        assert decode(c).equals(new Parts(1_000, 18, 0));
        assert a < b && b < c;
        assert Set.of(a, b, c).size() == 3;

        Set<Long> sameTick = new HashSet<>();
        SnowflakeGenerator saturated = new SnowflakeGenerator(epoch, worker17);
        for (int i = 0; i <= MAX_SEQUENCE; i++) {
            assert sameTick.add(saturated.next(now, worker17));
        }
        assert sameTick.size() == 4_096;
        expectFailure(() -> saturated.next(now, worker17));
        long nextTick = saturated.next(now + 1, worker17);
        assert decode(nextTick).sequence() == 0;

        SnowflakeGenerator clockGuard = new SnowflakeGenerator(epoch, worker17);
        clockGuard.next(now + 5, worker17);
        expectFailure(() -> clockGuard.next(now + 4, worker17));
        expectFailure(() -> clockGuard.next(now + 10_000, worker17));
        WorkerLease reused17 = new WorkerLease(17, 9, now + 20_000);
        expectFailure(() -> clockGuard.next(now + 6, reused17));
        expectFailure(() -> new SnowflakeGenerator(epoch, new WorkerLease(1_024, 1, now + 1)));

        SegmentAllocator allocator = new SegmentAllocator(1_000_000);
        Range r1 = allocator.allocate(1_000);
        Range r2 = allocator.allocate(1_000);
        assert r1.equals(new Range(1_000_000, 1_001_000));
        assert r2.equals(new Range(1_001_000, 1_002_000));
        assert r1.endExclusive() == r2.startInclusive();
        assert r1.size() == 1_000;
        expectFailure(() -> allocator.allocate(0));

        long uuidMillis = 1_700_000_000_123L;
        UUID u1 = uuidV7(uuidMillis, 10, 100);
        UUID u2 = uuidV7(uuidMillis, 11, 3);
        assert u1.version() == 7 && u1.variant() == 2;
        assert uuidV7Timestamp(u1) == uuidMillis;
        assert u1.compareTo(u2) < 0;
        assert !u1.equals(uuidV7(uuidMillis, 10, 101));
        expectFailure(() -> uuidV7(-1, 0, 0));
        expectFailure(() -> uuidV7Timestamp(UUID.randomUUID()));

        long lastId = ((MAX_TIMESTAMP << (WORKER_BITS + SEQUENCE_BITS))
                | (MAX_WORKER << SEQUENCE_BITS) | MAX_SEQUENCE);
        assert lastId == Long.MAX_VALUE;
        assert decode(lastId).equals(new Parts(MAX_TIMESTAMP, MAX_WORKER, MAX_SEQUENCE));

        System.out.println("DistributedIdContracts: 24 scenario groups passed");
    }
}
