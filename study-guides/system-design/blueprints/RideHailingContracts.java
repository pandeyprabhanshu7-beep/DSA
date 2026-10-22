import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Executable contracts for the ride-hailing blueprint. Run with Java 17+. */
public final class RideHailingContracts {
    record Location(String driverId, String session, long sequence,
                    Instant observedAt, Instant receivedAt, String cell) {
        Location {
            if (driverId == null || driverId.isBlank() || session == null || session.isBlank()
                    || sequence < 0 || observedAt == null || receivedAt == null
                    || cell == null || cell.isBlank()) {
                throw new IllegalArgumentException("invalid location");
            }
        }
    }

    record DriverState(String driverId, String activeTripId, long version) {
        DriverState {
            if (driverId == null || driverId.isBlank() || version < 0) {
                throw new IllegalArgumentException("invalid driver state");
            }
        }

        boolean available() { return activeTripId == null; }
    }

    record TripView(String tripId, long version, String status) {
        TripView {
            if (tripId == null || tripId.isBlank() || version < 0
                    || status == null || status.isBlank()) {
                throw new IllegalArgumentException("invalid trip view");
            }
        }
    }

    static long locationUpdatesPerSecond(long activeDrivers, int intervalSeconds) {
        if (activeDrivers < 0 || intervalSeconds <= 0 || activeDrivers % intervalSeconds != 0) {
            throw new IllegalArgumentException("use non-negative evenly divisible inputs");
        }
        return activeDrivers / intervalSeconds;
    }

    /** Ideal hex disk only; real H3 traversal can differ around pentagonal distortion. */
    static int estimatedHexDiskCells(int ring) {
        if (ring < 0) throw new IllegalArgumentException("ring cannot be negative");
        return 1 + 3 * ring * (ring + 1);
    }

    static Location mergeLocation(Location current, Location incoming) {
        if (!current.driverId().equals(incoming.driverId())) {
            throw new IllegalArgumentException("drivers differ");
        }
        if (!current.session().equals(incoming.session())) {
            throw new IllegalArgumentException("session epoch must be resolved by the server");
        }
        return incoming.sequence() > current.sequence() ? incoming : current;
    }

    static boolean fresh(Location location, Instant decisionTime, long maxAgeSeconds) {
        if (maxAgeSeconds < 0 || decisionTime.isBefore(location.receivedAt())) {
            throw new IllegalArgumentException("invalid freshness boundary");
        }
        return !location.receivedAt().isBefore(decisionTime.minusSeconds(maxAgeSeconds));
    }

    static TripView mergeTripView(TripView current, TripView incoming) {
        if (!current.tripId().equals(incoming.tripId())) {
            throw new IllegalArgumentException("trips differ");
        }
        return incoming.version() > current.version() ? incoming : current;
    }

    static String paymentKey(String tripId, long completionVersion) {
        if (tripId == null || tripId.isBlank() || completionVersion < 0) {
            throw new IllegalArgumentException("invalid completion identity");
        }
        return "payment:" + tripId + ":v" + completionVersion;
    }

    static final class AssignmentStore {
        private final Map<String, DriverState> drivers = new HashMap<>();
        private final Map<String, String> outcomesByAttempt = new HashMap<>();

        AssignmentStore(DriverState initial) { drivers.put(initial.driverId(), initial); }

        synchronized boolean claim(String attemptId, String driverId, String tripId,
                                   long expectedVersion) {
            String prior = outcomesByAttempt.get(attemptId);
            if (prior != null) return prior.equals("WON:" + tripId);

            DriverState current = drivers.get(driverId);
            boolean won = current != null && current.available()
                    && current.version() == expectedVersion;
            if (won) {
                drivers.put(driverId, new DriverState(driverId, tripId, expectedVersion + 1));
                outcomesByAttempt.put(attemptId, "WON:" + tripId);
            } else {
                outcomesByAttempt.put(attemptId, "LOST");
            }
            return won;
        }

        DriverState driver(String driverId) { return drivers.get(driverId); }
    }

    private static void rejects(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        assert locationUpdatesPerSecond(5_000_000, 4) == 1_250_000;
        assert estimatedHexDiskCells(0) == 1;
        assert estimatedHexDiskCells(1) == 7;
        assert estimatedHexDiskCells(2) == 19;
        assert estimatedHexDiskCells(3) == 37;

        Instant noon = Instant.parse("2026-10-22T12:00:10Z");
        var current = new Location("d11", "s9", 40,
                noon.minusSeconds(6), noon.minusSeconds(6), "A");
        var delayed = new Location("d11", "s9", 39,
                noon.minusSeconds(7), noon.minusSeconds(5), "B");
        var newest = new Location("d11", "s9", 41,
                noon.minusSeconds(2), noon.minusSeconds(2), "C");
        assert mergeLocation(current, delayed) == current;
        assert mergeLocation(current, newest) == newest;
        assert fresh(current, noon, 10);
        assert !fresh(new Location("d12", "s4", 8, noon.minusSeconds(13),
                noon.minusSeconds(13), "B"), noon, 10);

        var assignments = new AssignmentStore(new DriverState("d9", null, 22));
        assert assignments.claim("a7", "d9", "trip-a", 22);
        assert assignments.claim("a7", "d9", "trip-a", 22); // lost response, same outcome
        assert !assignments.claim("b3", "d9", "trip-b", 22);
        assert assignments.driver("d9").activeTripId().equals("trip-a");
        assert assignments.driver("d9").version() == 23;

        var v17 = new TripView("trip-a", 17, "MATCHED");
        var v20 = new TripView("trip-a", 20, "IN_PROGRESS");
        assert mergeTripView(v17, v20) == v20;
        assert mergeTripView(v20, new TripView("trip-a", 19, "AT_PICKUP")) == v20;
        assert paymentKey("trip-a", 21).equals(paymentKey("trip-a", 21));

        rejects(() -> estimatedHexDiskCells(-1));
        rejects(() -> locationUpdatesPerSecond(10, 0));
        rejects(() -> mergeLocation(current,
                new Location("other", "s9", 41, noon, noon, "C")));
        rejects(() -> fresh(current, noon.minusSeconds(20), 10));
        rejects(() -> paymentKey("", 1));
        System.out.println("RideHailingContracts: 22 scenario groups passed");
    }
}
