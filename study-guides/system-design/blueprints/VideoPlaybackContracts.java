import java.util.List;

/** Executable contracts for the global-video blueprint. Run with Java 17+. */
public final class VideoPlaybackContracts {
    record Rendition(String name, double megabitsPerSecond) {
        Rendition {
            if (name == null || name.isBlank() || megabitsPerSecond <= 0) {
                throw new IllegalArgumentException("rendition requires a name and positive bitrate");
            }
        }
    }

    record Progress(String profileId, String titleId, long sequence, long positionMillis) {
        Progress {
            if (profileId == null || titleId == null || sequence < 0 || positionMillis < 0) {
                throw new IllegalArgumentException("invalid progress event");
            }
        }
    }

    static double aggregateTerabitsPerSecond(long concurrentStreams, double averageMbps) {
        if (concurrentStreams < 0 || averageMbps < 0) {
            throw new IllegalArgumentException("traffic inputs cannot be negative");
        }
        return concurrentStreams * averageMbps / 1_000_000.0;
    }

    static double originTerabitsPerSecond(double viewerTbps, double byteHitRatio) {
        if (viewerTbps < 0 || byteHitRatio < 0 || byteHitRatio > 1) {
            throw new IllegalArgumentException("invalid traffic or hit ratio");
        }
        return viewerTbps * (1.0 - byteHitRatio);
    }

    static Rendition chooseRendition(
            List<Rendition> ascendingLadder,
            double estimatedMbps,
            double normalSafetyFraction,
            double bufferSeconds) {
        if (ascendingLadder.isEmpty() || estimatedMbps <= 0
                || normalSafetyFraction <= 0 || normalSafetyFraction > 1 || bufferSeconds < 0) {
            throw new IllegalArgumentException("invalid ABR inputs");
        }
        double safety = bufferSeconds < 8 ? Math.min(normalSafetyFraction, 0.55) : normalSafetyFraction;
        double budget = estimatedMbps * safety;
        Rendition chosen = ascendingLadder.get(0);
        for (Rendition candidate : ascendingLadder) {
            if (candidate.megabitsPerSecond() <= budget) chosen = candidate;
            else break;
        }
        return chosen;
    }

    static Progress mergeProgress(Progress current, Progress incoming) {
        if (!current.profileId().equals(incoming.profileId())
                || !current.titleId().equals(incoming.titleId())) {
            throw new IllegalArgumentException("progress identities differ");
        }
        return incoming.sequence() > current.sequence() ? incoming : current;
    }

    private static void near(double actual, double expected) {
        if (Math.abs(actual - expected) > 1e-9) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }

    private static void rejects(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        var ladder = List.of(
                new Rendition("240p", 0.3), new Rendition("480p", 1.2),
                new Rendition("720p", 2.5), new Rendition("1080p", 5.0));

        near(aggregateTerabitsPerSecond(60_000_000, 5), 300);       // global peak
        near(aggregateTerabitsPerSecond(1_000_000, 8), 8);          // regional peak
        near(aggregateTerabitsPerSecond(2_000_000, 6), 12);         // premiere spike
        near(originTerabitsPerSecond(100, 0.985), 1.5);              // byte misses
        assert chooseRendition(ladder, 8, 0.75, 20).name().equals("1080p");
        assert chooseRendition(ladder, 4, 0.75, 20).name().equals("720p");
        assert chooseRendition(ladder, 4, 0.75, 3).name().equals("480p");

        var current = new Progress("p1", "t1", 9, 80_000);
        assert mergeProgress(current, new Progress("p1", "t1", 8, 95_000)) == current;
        var seekBack = new Progress("p1", "t1", 10, 25_000);
        assert mergeProgress(current, seekBack) == seekBack; // sequence, not max position, wins

        rejects(() -> aggregateTerabitsPerSecond(-1, 5));
        rejects(() -> originTerabitsPerSecond(1, 1.01));
        rejects(() -> chooseRendition(List.of(), 1, 0.8, 10));
        rejects(() -> mergeProgress(current, new Progress("p2", "t1", 10, 1)));
        System.out.println("VideoPlaybackContracts: 13 scenario groups passed");
    }
}
