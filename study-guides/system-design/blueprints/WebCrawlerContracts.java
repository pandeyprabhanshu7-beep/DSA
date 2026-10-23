import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Executable invariants for Blueprint 4. Run with: java -ea WebCrawlerContracts.java */
public final class WebCrawlerContracts {
    record CrawlItem(String url, int priority, long discoveredAtMillis) {}
    record Rule(String pathPrefix, boolean allow) {}
    record Lease(String url, String origin, long expiresAtMillis) {}

    static String canonicalize(String base, String reference) {
        try {
            URI resolved = URI.create(base).resolve(reference).normalize();
            String scheme = resolved.getScheme() == null ? "" : resolved.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IllegalArgumentException("only HTTP(S) crawl targets are allowed");
            }
            if (resolved.getUserInfo() != null || resolved.getHost() == null) {
                throw new IllegalArgumentException("userinfo and non-server authorities are rejected");
            }
            String host = resolved.getHost().toLowerCase();
            int port = resolved.getPort();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
                port = -1;
            }
            String path = resolved.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            return new URI(scheme, null, host, port, path, resolved.getRawQuery(), null).toASCIIString();
        } catch (IllegalArgumentException | URISyntaxException error) {
            throw new IllegalArgumentException("invalid crawl URL", error);
        }
    }

    static String origin(String canonicalUrl) {
        URI uri = URI.create(canonicalUrl);
        int effectivePort = uri.getPort() >= 0 ? uri.getPort() : uri.getScheme().equals("https") ? 443 : 80;
        return uri.getScheme() + "://" + uri.getHost() + ":" + effectivePort;
    }

    static boolean publicLiteralAddress(String literal) {
        if (!literal.matches("[0-9a-fA-F:.]+")) return false;
        try {
            InetAddress address = InetAddress.getByName(literal);
            return !(address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress());
        } catch (Exception invalid) {
            return false;
        }
    }

    static boolean robotsAllows(List<Rule> rules, String rawPath) {
        Rule winner = null;
        for (Rule rule : rules) {
            if (rawPath.startsWith(rule.pathPrefix())
                    && (winner == null
                    || rule.pathPrefix().length() > winner.pathPrefix().length()
                    || (rule.pathPrefix().length() == winner.pathPrefix().length() && rule.allow()))) {
                winner = rule;
            }
        }
        return winner == null || winner.allow();
    }

    static final class Frontier {
        private final Set<String> seen = new HashSet<>();
        private final PriorityQueue<CrawlItem> ready = new PriorityQueue<>(
                Comparator.comparingInt(CrawlItem::priority).reversed()
                        .thenComparingLong(CrawlItem::discoveredAtMillis)
                        .thenComparing(CrawlItem::url));

        boolean discover(String base, String reference, int priority, long nowMillis) {
            String url = canonicalize(base, reference);
            if (!seen.add(url)) return false;
            ready.add(new CrawlItem(url, priority, nowMillis));
            return true;
        }

        CrawlItem poll() { return ready.poll(); }
        int seenCount() { return seen.size(); }
    }

    static final class PoliteScheduler {
        record HostState(long nextAllowedMillis, int inFlight, int maxConcurrency) {}
        private final Map<String, HostState> state = new HashMap<>();
        private final long minimumDelayMillis;

        PoliteScheduler(long minimumDelayMillis) {
            if (minimumDelayMillis < 0) throw new IllegalArgumentException("negative delay");
            this.minimumDelayMillis = minimumDelayMillis;
        }

        Lease tryLease(String canonicalUrl, long nowMillis, long leaseMillis, int maxConcurrency) {
            if (leaseMillis <= 0 || maxConcurrency <= 0) throw new IllegalArgumentException("positive limits required");
            String key = origin(canonicalUrl);
            HostState current = state.getOrDefault(key, new HostState(0, 0, maxConcurrency));
            if (current.inFlight() >= current.maxConcurrency() || nowMillis < current.nextAllowedMillis()) {
                return null;
            }
            state.put(key, new HostState(current.nextAllowedMillis(), current.inFlight() + 1, current.maxConcurrency()));
            return new Lease(canonicalUrl, key, Math.addExact(nowMillis, leaseMillis));
        }

        void complete(Lease lease, int status, long nowMillis, Long retryAfterMillis) {
            HostState current = state.get(lease.origin());
            if (current == null || current.inFlight() == 0) throw new IllegalStateException("unknown lease");
            long delay = minimumDelayMillis;
            if ((status == 429 || status == 503) && retryAfterMillis != null) {
                delay = Math.max(delay, retryAfterMillis);
            } else if (status == 429 || status >= 500) {
                delay = Math.max(delay, minimumDelayMillis * 4);
            }
            state.put(lease.origin(), new HostState(
                    Math.addExact(nowMillis, delay), current.inFlight() - 1, current.maxConcurrency()));
        }

        long nextAllowed(String canonicalUrl) {
            return state.getOrDefault(origin(canonicalUrl), new HostState(0, 0, 1)).nextAllowedMillis();
        }
    }

    static long nextRecrawlDelayMillis(boolean changed, int consecutiveFailures) {
        if (consecutiveFailures < 0) throw new IllegalArgumentException("negative failures");
        long base = changed ? 60_000L : 3_600_000L;
        int exponent = Math.min(consecutiveFailures, 8);
        return Math.multiplyExact(base, 1L << exponent);
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
        String a = canonicalize("https://Example.COM/a/b/", "../c#section");
        String b = canonicalize("https://example.com:443/", "a/c");
        assert a.equals("https://example.com/a/c");
        assert a.equals(b);
        assert canonicalize("http://EXAMPLE.com:80", "./").equals("http://example.com/");
        assert !canonicalize("https://example.com/", "?b=2&a=1").equals(
                canonicalize("https://example.com/", "?a=1&b=2"));
        expectFailure(() -> canonicalize("https://example.com", "file:///etc/passwd"));

        Frontier frontier = new Frontier();
        assert frontier.discover("https://example.com/a/", "../b#x", 3, 10);
        assert !frontier.discover("https://EXAMPLE.com:443/", "b", 9, 11);
        assert frontier.discover("https://example.com/", "news", 8, 12);
        assert frontier.poll().url().equals("https://example.com/news");
        assert frontier.seenCount() == 2;

        List<Rule> rules = List.of(
                new Rule("/private", false),
                new Rule("/private/public", true));
        assert !robotsAllows(rules, "/private/report");
        assert robotsAllows(rules, "/private/public/index.html");
        assert robotsAllows(rules, "/docs");

        PoliteScheduler scheduler = new PoliteScheduler(1_000);
        String u1 = canonicalize("https://example.com/", "one");
        String u2 = canonicalize("https://example.com/", "two");
        Lease first = scheduler.tryLease(u1, 1_000, 5_000, 1);
        assert first != null;
        assert scheduler.tryLease(u2, 1_001, 5_000, 1) == null;
        scheduler.complete(first, 200, 1_200, null);
        assert scheduler.tryLease(u2, 2_199, 5_000, 1) == null;
        Lease second = scheduler.tryLease(u2, 2_200, 5_000, 1);
        assert second != null;
        scheduler.complete(second, 429, 2_300, 10_000L);
        assert scheduler.nextAllowed(u2) == 12_300;
        assert scheduler.tryLease(u1, 12_299, 5_000, 1) == null;
        assert scheduler.tryLease(u1, 12_300, 5_000, 1) != null;

        assert !publicLiteralAddress("127.0.0.1");
        assert !publicLiteralAddress("169.254.169.254");
        assert !publicLiteralAddress("10.1.2.3");
        assert publicLiteralAddress("93.184.216.34");
        assert !publicLiteralAddress("example.com");

        assert nextRecrawlDelayMillis(true, 0) == 60_000;
        assert nextRecrawlDelayMillis(false, 0) == 3_600_000;
        assert nextRecrawlDelayMillis(true, 3) == 480_000;
        expectFailure(() -> nextRecrawlDelayMillis(true, -1));

        System.out.println("WebCrawlerContracts: 25 scenario groups passed");
    }
}
