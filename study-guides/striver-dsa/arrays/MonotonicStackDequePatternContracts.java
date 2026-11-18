import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Random;

public class MonotonicStackDequePatternContracts {
    private static final int MOD = 1_000_000_007;
    private static int groups;
    private static boolean randomizedPhase;

    static int[] nextGreater(int[] values) {
        int[] answer = new int[values.length];
        Arrays.fill(answer, -1);
        Deque<Integer> unresolved = new ArrayDeque<>();
        for (int i = 0; i < values.length; i++) {
            while (!unresolved.isEmpty() && values[unresolved.peekLast()] < values[i]) {
                answer[unresolved.removeLast()] = values[i];
            }
            unresolved.addLast(i);
        }
        return answer;
    }

    static int[] nextGreaterCircular(int[] values) {
        int n = values.length;
        int[] answer = new int[n];
        Arrays.fill(answer, -1);
        Deque<Integer> unresolved = new ArrayDeque<>();
        for (int step = 0; step < 2 * n; step++) {
            int i = step % n;
            while (!unresolved.isEmpty() && values[unresolved.peekLast()] < values[i]) {
                answer[unresolved.removeLast()] = values[i];
            }
            if (step < n) unresolved.addLast(i);
        }
        return answer;
    }

    static int[] daysUntilWarmer(int[] temperatures) {
        int[] answer = new int[temperatures.length];
        Deque<Integer> unresolved = new ArrayDeque<>();
        for (int day = 0; day < temperatures.length; day++) {
            while (!unresolved.isEmpty()
                    && temperatures[unresolved.peekLast()] < temperatures[day]) {
                int earlier = unresolved.removeLast();
                answer[earlier] = day - earlier;
            }
            unresolved.addLast(day);
        }
        return answer;
    }

    static int sumSubarrayMinimums(int[] values) {
        int n = values.length;
        int[] left = new int[n];
        int[] right = new int[n];
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            while (!stack.isEmpty() && values[stack.peekLast()] > values[i]) {
                stack.removeLast();
            }
            left[i] = stack.isEmpty() ? i + 1 : i - stack.peekLast();
            stack.addLast(i);
        }
        stack.clear();
        for (int i = n - 1; i >= 0; i--) {
            while (!stack.isEmpty() && values[stack.peekLast()] >= values[i]) {
                stack.removeLast();
            }
            right[i] = stack.isEmpty() ? n - i : stack.peekLast() - i;
            stack.addLast(i);
        }
        long sum = 0;
        for (int i = 0; i < n; i++) {
            long contribution = (long) values[i] * left[i] % MOD * right[i] % MOD;
            sum = (sum + contribution) % MOD;
        }
        return (int) sum;
    }

    static int[] stockSpans(int[] prices) {
        int[] spans = new int[prices.length];
        Deque<Integer> previousGreater = new ArrayDeque<>();
        for (int day = 0; day < prices.length; day++) {
            while (!previousGreater.isEmpty()
                    && prices[previousGreater.peekLast()] <= prices[day]) {
                previousGreater.removeLast();
            }
            spans[day] = previousGreater.isEmpty() ? day + 1 : day - previousGreater.peekLast();
            previousGreater.addLast(day);
        }
        return spans;
    }

    static boolean has132Pattern(int[] values) {
        Deque<Integer> possibleThrees = new ArrayDeque<>();
        long bestTwo = Long.MIN_VALUE;
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i] < bestTwo) return true;
            while (!possibleThrees.isEmpty() && possibleThrees.peekLast() < values[i]) {
                bestTwo = Math.max(bestTwo, possibleThrees.removeLast());
            }
            possibleThrees.addLast(values[i]);
        }
        return false;
    }

    static String removeKDigits(String number, int k) {
        if (number == null || k < 0 || k > number.length()) {
            throw new IllegalArgumentException("invalid number or k");
        }
        StringBuilder kept = new StringBuilder(number.length());
        for (int i = 0; i < number.length(); i++) {
            char digit = number.charAt(i);
            if (digit < '0' || digit > '9') throw new IllegalArgumentException("digits required");
            while (k > 0 && kept.length() > 0 && kept.charAt(kept.length() - 1) > digit) {
                kept.setLength(kept.length() - 1);
                k--;
            }
            kept.append(digit);
        }
        kept.setLength(kept.length() - k);
        int firstNonZero = 0;
        while (firstNonZero < kept.length() && kept.charAt(firstNonZero) == '0') firstNonZero++;
        return firstNonZero == kept.length() ? "0" : kept.substring(firstNonZero);
    }

    private static int[] bruteNextGreater(int[] a, boolean circular) {
        int[] answer = new int[a.length];
        Arrays.fill(answer, -1);
        for (int i = 0; i < a.length; i++) {
            int limit = circular ? a.length : a.length - i;
            for (int d = 1; d < limit; d++) {
                int j = (i + d) % a.length;
                if (a[j] > a[i]) { answer[i] = a[j]; break; }
            }
        }
        return answer;
    }

    private static int[] bruteWarmer(int[] a) {
        int[] answer = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            for (int j = i + 1; j < a.length; j++) {
                if (a[j] > a[i]) { answer[i] = j - i; break; }
            }
        }
        return answer;
    }

    private static int bruteMinimums(int[] a) {
        long sum = 0;
        for (int left = 0; left < a.length; left++) {
            int minimum = Integer.MAX_VALUE;
            for (int right = left; right < a.length; right++) {
                minimum = Math.min(minimum, a[right]);
                sum = (sum + minimum) % MOD;
            }
        }
        return (int) sum;
    }

    private static int[] bruteSpans(int[] a) {
        int[] answer = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            int j = i - 1;
            while (j >= 0 && a[j] <= a[i]) j--;
            answer[i] = i - j;
        }
        return answer;
    }

    private static boolean brute132(int[] a) {
        for (int i = 0; i < a.length; i++)
            for (int j = i + 1; j < a.length; j++)
                for (int k = j + 1; k < a.length; k++)
                    if (a[i] < a[k] && a[k] < a[j]) return true;
        return false;
    }

    private static String bruteRemove(String number, int k) {
        int keep = number.length() - k;
        String[] best = { null };
        choose(number, 0, keep, new StringBuilder(), best);
        return normalize(best[0]);
    }

    private static void choose(String number, int at, int remaining, StringBuilder chosen, String[] best) {
        if (remaining == 0) {
            String candidate = normalize(chosen.toString());
            if (best[0] == null || numericCompare(candidate, normalize(best[0])) < 0) {
                best[0] = chosen.toString();
            }
            return;
        }
        if (number.length() - at < remaining) return;
        chosen.append(number.charAt(at));
        choose(number, at + 1, remaining - 1, chosen, best);
        chosen.setLength(chosen.length() - 1);
        choose(number, at + 1, remaining, chosen, best);
    }

    private static String normalize(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '0') i++;
        return i == s.length() ? "0" : s.substring(i);
    }

    private static int numericCompare(String a, String b) {
        if (a.length() != b.length()) return Integer.compare(a.length(), b.length());
        return a.compareTo(b);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("contract failed");
        if (!randomizedPhase) groups++;
    }

    public static void main(String[] args) {
        check(Arrays.equals(nextGreater(new int[]{2, 1, 2, 4, 3}), new int[]{4, 2, 4, -1, -1}));
        check(Arrays.equals(nextGreater(new int[]{2, 2}), new int[]{-1, -1}));
        check(Arrays.equals(nextGreaterCircular(new int[]{1, 2, 1}), new int[]{2, -1, 2}));
        check(Arrays.equals(nextGreaterCircular(new int[]{}), new int[]{}));
        check(Arrays.equals(daysUntilWarmer(new int[]{73,74,75,71,69,72,76,73}),
                new int[]{1,1,4,2,1,1,0,0}));
        check(Arrays.equals(daysUntilWarmer(new int[]{60, 60}), new int[]{0, 0}));
        check(sumSubarrayMinimums(new int[]{3,1,2,4}) == 17);
        check(sumSubarrayMinimums(new int[]{11,11}) == 33);
        check(Arrays.equals(stockSpans(new int[]{100,80,60,70,60,75,85}),
                new int[]{1,1,1,2,1,4,6}));
        check(Arrays.equals(stockSpans(new int[]{5,5,5}), new int[]{1,2,3}));
        check(has132Pattern(new int[]{3,1,4,2}));
        check(!has132Pattern(new int[]{1,2,3,4}));
        check(has132Pattern(new int[]{-1,3,2,0}));
        check(removeKDigits("1432219", 3).equals("1219"));
        check(removeKDigits("10200", 1).equals("200"));
        check(removeKDigits("10", 2).equals("0"));
        check(removeKDigits("111", 1).equals("11"));
        int[] preserved = {3,1,4,2};
        int[] snapshot = preserved.clone();
        has132Pattern(preserved); nextGreater(preserved); sumSubarrayMinimums(preserved);
        check(Arrays.equals(preserved, snapshot));
        try { removeKDigits("12a", 1); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { groups++; }
        try { removeKDigits("12", 3); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { groups++; }

        randomizedPhase = true;
        Random random = new Random(0x5A17D3E9L);
        for (int trial = 0; trial < 20_000; trial++) {
            int n = 1 + random.nextInt(8);
            int[] values = new int[n];
            int[] positive = new int[n];
            for (int i = 0; i < n; i++) {
                values[i] = random.nextInt(13) - 6;
                positive[i] = 1 + random.nextInt(9);
            }
            check(Arrays.equals(nextGreater(values), bruteNextGreater(values, false)));
            check(Arrays.equals(nextGreaterCircular(values), bruteNextGreater(values, true)));
            check(Arrays.equals(daysUntilWarmer(values), bruteWarmer(values)));
            check(sumSubarrayMinimums(positive) == bruteMinimums(positive));
            check(Arrays.equals(stockSpans(positive), bruteSpans(positive)));
            check(has132Pattern(values) == brute132(values));
            int digits = 1 + random.nextInt(7);
            StringBuilder number = new StringBuilder(digits);
            for (int i = 0; i < digits; i++) number.append((char) ('0' + random.nextInt(10)));
            int k = random.nextInt(digits + 1);
            check(removeKDigits(number.toString(), k).equals(bruteRemove(number.toString(), k)));
        }
        System.out.println("MonotonicStackDequePatternContracts: groups=" + groups
                + ", seededTrials=20000, oracleChecks=140000, status=PASS");
    }
}
