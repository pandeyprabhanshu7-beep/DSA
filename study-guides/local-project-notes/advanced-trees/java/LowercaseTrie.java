import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Trie for non-empty lowercase ASCII words. Prefixes may be empty.
 * This mutable class is not thread-safe.
 */
public final class LowercaseTrie {
    private static final int ALPHABET_SIZE = 26;

    private static final class Node {
        private final Node[] children = new Node[ALPHABET_SIZE];
        private boolean word;
    }

    private final Node root = new Node();
    private int wordCount;

    public int size() {
        return wordCount;
    }

    /** Returns true only when this call adds a previously absent word. */
    public boolean insert(String word) {
        validate(word, false, "word");
        Node current = root;
        for (int i = 0; i < word.length(); i++) {
            int index = word.charAt(i) - 'a';
            if (current.children[index] == null) {
                current.children[index] = new Node();
            }
            current = current.children[index];
        }
        if (current.word) {
            return false;
        }
        current.word = true;
        wordCount++;
        return true;
    }

    public boolean contains(String word) {
        validate(word, false, "word");
        Node node = walk(word);
        return node != null && node.word;
    }

    /** The empty prefix is valid because it denotes the root path. */
    public boolean startsWith(String prefix) {
        validate(prefix, true, "prefix");
        return walk(prefix) != null;
    }

    /** Returns at most limit stored words in ascending ASCII order. */
    public List<String> autocomplete(String prefix, int limit) {
        validate(prefix, true, "prefix");
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        if (limit == 0) {
            return List.of();
        }
        Node start = walk(prefix);
        if (start == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(Math.min(limit, wordCount));
        collect(start, new StringBuilder(prefix), limit, result);
        return result;
    }

    private Node walk(String text) {
        Node current = root;
        for (int i = 0; i < text.length(); i++) {
            current = current.children[text.charAt(i) - 'a'];
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private void collect(Node node, StringBuilder path, int limit,
                         List<String> result) {
        if (node.word) {
            result.add(path.toString());
            if (result.size() == limit) {
                return;
            }
        }
        for (int index = 0; index < ALPHABET_SIZE; index++) {
            Node child = node.children[index];
            if (child == null) {
                continue;
            }
            path.append((char) ('a' + index));
            collect(child, path, limit, result);
            path.setLength(path.length() - 1);
            if (result.size() == limit) {
                return;
            }
        }
    }

    private static void validate(String text, boolean allowEmpty,
                                 String label) {
        Objects.requireNonNull(text, label);
        if (!allowEmpty && text.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character < 'a' || character > 'z') {
                throw new IllegalArgumentException(
                        label + " must contain only lowercase a-z: " + text);
            }
        }
    }
}
