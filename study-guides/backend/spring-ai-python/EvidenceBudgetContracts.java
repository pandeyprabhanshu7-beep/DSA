import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EvidenceBudgetContracts {
    private static int scenarios;

    record Reservations(long contextWindow, long output, long instructions,
                        long history, long safety, long serialization) {}

    record Passage(String id, long tokens) {}

    static long evidenceBudget(Reservations r) {
        if (r.contextWindow() < 0 || r.output() < 0 || r.instructions() < 0
                || r.history() < 0 || r.safety() < 0 || r.serialization() < 0) {
            throw new IllegalArgumentException("token reservations must be nonnegative");
        }
        try {
            long remaining = r.contextWindow();
            remaining = Math.subtractExact(remaining, r.output());
            remaining = Math.subtractExact(remaining, r.instructions());
            remaining = Math.subtractExact(remaining, r.history());
            remaining = Math.subtractExact(remaining, r.safety());
            remaining = Math.subtractExact(remaining, r.serialization());
            if (remaining < 0) throw new IllegalArgumentException("reservations exceed context");
            return remaining;
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("token arithmetic overflow", overflow);
        }
    }

    static List<Passage> pack(List<Passage> passages, long budget) {
        if (budget < 0) throw new IllegalArgumentException("negative budget");
        Set<String> ids = new HashSet<>();
        for (Passage passage : passages) {
            if (passage.id() == null || passage.id().isBlank()) {
                throw new IllegalArgumentException("blank passage ID");
            }
            if (passage.tokens() <= 0) throw new IllegalArgumentException("invalid token count");
            if (!ids.add(passage.id())) throw new IllegalArgumentException("duplicate passage ID");
        }
        List<Passage> selected = new ArrayList<>();
        long remaining = budget;
        for (Passage passage : passages) {
            if (passage.tokens() <= remaining) {
                selected.add(passage);
                remaining -= passage.tokens();
            }
        }
        return List.copyOf(selected);
    }

    static boolean serializedPromptFits(long contextWindow, long reservedOutput,
                                        long actualInputTokens) {
        if (contextWindow < 0 || reservedOutput < 0 || actualInputTokens < 0) {
            throw new IllegalArgumentException("token counts must be nonnegative");
        }
        try {
            return Math.addExact(reservedOutput, actualInputTokens) <= contextWindow;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    private static List<String> ids(List<Passage> passages) {
        return passages.stream().map(Passage::id).toList();
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("check failed");
        scenarios++;
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected rejection");
        } catch (IllegalArgumentException expected) {
            scenarios++;
        }
    }

    private static long next(long state) {
        return (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
    }

    public static void main(String[] args) {
        Reservations canonical = new Reservations(4096, 900, 300, 500, 196, 0);
        check(evidenceBudget(canonical) == 2200);
        List<Passage> ranked = List.of(new Passage("a", 1400),
                new Passage("b", 1100), new Passage("c", 700));
        check(ids(pack(ranked, 2200)).equals(List.of("a", "c")));
        check(pack(ranked, 2200).stream().mapToLong(Passage::tokens).sum() == 2100);
        check(ids(pack(ranked, 2500)).equals(List.of("a", "b")));
        check(ids(pack(ranked, 3200)).equals(List.of("a", "b", "c")));
        check(pack(List.of(new Passage("exact", 100)), 100).size() == 1);
        check(pack(List.of(new Passage("too-large", 101)), 100).isEmpty());
        check(pack(List.of(new Passage("one", 1)), 0).isEmpty());
        check(pack(List.of(), 0).isEmpty());
        check(evidenceBudget(new Reservations(100, 20, 20, 20, 20, 20)) == 0);
        check(evidenceBudget(new Reservations(4096, 900, 300, 500, 196, 120)) == 2080);
        check(serializedPromptFits(4096, 900, 3196));
        check(!serializedPromptFits(4096, 900, 3197));
        check(!serializedPromptFits(Long.MAX_VALUE, 1, Long.MAX_VALUE));
        check(ids(pack(List.of(new Passage("p1", 70), new Passage("p2", 40),
                new Passage("p3", 30)), 100)).equals(List.of("p1", "p3")));
        check(ids(pack(List.of(new Passage("first", 60), new Passage("second", 60)), 60))
                .equals(List.of("first")));
        check(ids(pack(List.of(new Passage("small", 1), new Passage("large", Long.MAX_VALUE)),
                Long.MAX_VALUE)).equals(List.of("small")));
        check(ranked.equals(List.of(new Passage("a", 1400),
                new Passage("b", 1100), new Passage("c", 700))));

        rejects(() -> evidenceBudget(new Reservations(-1, 0, 0, 0, 0, 0)));
        rejects(() -> evidenceBudget(new Reservations(1, -1, 0, 0, 0, 0)));
        rejects(() -> evidenceBudget(new Reservations(10, 11, 0, 0, 0, 0)));
        rejects(() -> evidenceBudget(new Reservations(Long.MAX_VALUE, 0, 0, 0, 0, -1)));
        rejects(() -> pack(List.of(), -1));
        rejects(() -> pack(List.of(new Passage("a", 0)), 100));
        rejects(() -> pack(List.of(new Passage("a", -1)), 100));
        rejects(() -> pack(List.of(new Passage("a", 1), new Passage("a", 2)), 100));
        rejects(() -> pack(List.of(new Passage("", 1)), 100));
        rejects(() -> pack(List.of(new Passage(" ", 1)), 100));
        rejects(() -> pack(List.of(new Passage(null, 1)), 100));
        rejects(() -> serializedPromptFits(-1, 0, 0));
        rejects(() -> serializedPromptFits(10, -1, 0));
        rejects(() -> serializedPromptFits(10, 0, -1));

        long state = 0x20E71DEAL;
        for (int trial = 0; trial < 10_000; trial++) {
            state = next(state); long budget = state % 900;
            state = next(state); int count = 1 + (int) (state % 12);
            List<Passage> values = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                state = next(state);
                values.add(new Passage("p" + index, 1 + state % 300));
            }
            List<Passage> actual = pack(values, budget);
            List<String> expected = new ArrayList<>();
            long oracleRemaining = budget;
            for (Passage value : values) {
                if (value.tokens() <= oracleRemaining) {
                    expected.add(value.id());
                    oracleRemaining -= value.tokens();
                }
            }
            assert ids(actual).equals(expected) : "order/selection mismatch";
            assert actual.stream().mapToLong(Passage::tokens).sum() <= budget;
            assert new HashSet<>(ids(actual)).size() == actual.size();
        }

        assert scenarios == 32 : "scenario count " + scenarios;
        System.out.println("PARITY|BUDGET=CHECKED|ORDER=RANKED|SKIP=OVERSIZE|COUNTS=POSITIVE|IDS=UNIQUE|SERIALIZED=RECOUNTED|OVERFLOW=REJECTED|SELECTED=a,c");
        System.out.println("Evidence budget contracts passed: 32 scenario groups + 10000 seeded packing trials");
    }
}
