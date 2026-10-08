import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public final class LowercaseTrieCheck {
    public static void main(String[] args) {
        deterministicChecks();
        boundaryChecks();
        randomizedOracleChecks();
        System.out.println(
                "PASS: deterministic/boundary checks; 50,000 random insert "
                        + "attempts; 100,000 exact/prefix oracle queries; "
                        + "10,000 autocomplete comparisons.");
    }

    private static void deterministicChecks() {
        LowercaseTrie trie = new LowercaseTrie();
        expectTrue(trie.insert("car"), "first car");
        expectTrue(trie.insert("card"), "card");
        expectTrue(trie.insert("care"), "care");
        expectTrue(trie.insert("cat"), "cat");
        expectFalse(trie.insert("car"), "duplicate car");
        expect(4, trie.size(), "distinct size");
        expectTrue(trie.contains("car"), "car is a word");
        expectFalse(trie.contains("ca"), "ca is only a prefix");
        expectTrue(trie.startsWith("ca"), "shared prefix");
        expectFalse(trie.startsWith("cab"), "missing edge");
        expect(List.of("car", "card", "care"),
                trie.autocomplete("car", 10), "prefix before descendants");
        expect(List.of("car", "card"),
                trie.autocomplete("car", 2), "limit");
    }

    private static void boundaryChecks() {
        LowercaseTrie trie = new LowercaseTrie();
        expectTrue(trie.startsWith(""), "empty prefix is root path");
        expect(List.of(), trie.autocomplete("", 5), "empty dictionary");
        expect(List.of(), trie.autocomplete("missing", 5), "missing prefix");
        expect(List.of(), trie.autocomplete("", 0), "zero limit");

        expectThrows(NullPointerException.class, () -> trie.insert(null));
        expectThrows(NullPointerException.class, () -> trie.startsWith(null));
        expectThrows(IllegalArgumentException.class, () -> trie.insert(""));
        expectThrows(IllegalArgumentException.class, () -> trie.insert("Cat"));
        expectThrows(IllegalArgumentException.class, () -> trie.contains("a1"));
        expectThrows(IllegalArgumentException.class,
                () -> trie.autocomplete("", -1));

        expectTrue(trie.insert("a"), "single-character word");
        expect(List.of("a"), trie.autocomplete("", 5), "root enumeration");
    }

    private static void randomizedOracleChecks() {
        Random random = new Random(8_611);
        int insertAttempts = 0;
        int queries = 0;
        int autocompleteChecks = 0;

        for (int trial = 0; trial < 2_000; trial++) {
            LowercaseTrie trie = new LowercaseTrie();
            TreeSet<String> oracle = new TreeSet<>();

            for (int operation = 0; operation < 25; operation++) {
                String inserted = randomWord(random);
                boolean expectedInsert = oracle.add(inserted);
                if (trie.insert(inserted) != expectedInsert) {
                    throw new AssertionError("insert result: " + inserted);
                }
                insertAttempts++;

                String exact = random.nextBoolean()
                        ? inserted : randomWord(random);
                if (trie.contains(exact) != oracle.contains(exact)) {
                    throw new AssertionError("contains: " + exact);
                }
                queries++;

                String base = random.nextBoolean()
                        ? inserted : randomWord(random);
                String prefix = base.substring(
                        0, random.nextInt(base.length() + 1));
                boolean expectedPrefix = prefix.isEmpty()
                        || oracle.stream().anyMatch(w -> w.startsWith(prefix));
                if (trie.startsWith(prefix) != expectedPrefix) {
                    throw new AssertionError("startsWith: " + prefix);
                }
                queries++;

                if (operation % 5 == 0) {
                    int limit = random.nextInt(8);
                    List<String> expected = new ArrayList<>();
                    for (String word : oracle) {
                        if (word.startsWith(prefix) && expected.size() < limit) {
                            expected.add(word);
                        }
                    }
                    expect(expected, trie.autocomplete(prefix, limit),
                            "autocomplete " + prefix);
                    autocompleteChecks++;
                }
            }
            expect(oracle.size(), trie.size(), "random distinct size");
        }

        expect(50_000, insertAttempts, "insert-attempt count");
        expect(100_000, queries, "query count");
        expect(10_000, autocompleteChecks, "autocomplete count");
    }

    private static String randomWord(Random random) {
        int length = 1 + random.nextInt(8);
        StringBuilder word = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            word.append((char) ('a' + random.nextInt(4)));
        }
        return word.toString();
    }

    private static void expectTrue(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static void expectFalse(boolean value, String label) {
        if (value) throw new AssertionError(label);
    }

    private static void expect(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected
                    + ", got " + actual);
        }
    }

    private static void expectThrows(
            Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected " + type.getSimpleName());
        } catch (Throwable error) {
            if (!type.isInstance(error)) {
                throw new AssertionError("Expected " + type.getSimpleName()
                        + ", got " + error, error);
            }
        }
    }
}
