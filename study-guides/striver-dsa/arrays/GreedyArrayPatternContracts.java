import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Dependency-free contracts and independent small-input oracles for array greedies. */
public final class GreedyArrayPatternContracts {
    private GreedyArrayPatternContracts() {}

    static boolean canJump(int[] jumps) {
        requireNonNegative(jumps, "jumps");
        if (jumps.length == 0) throw new IllegalArgumentException("jumps must be non-empty");
        long farthest = 0;
        for (int index = 0; index < jumps.length && index <= farthest; index++) {
            farthest = Math.max(farthest, (long) index + jumps[index]);
            if (farthest >= jumps.length - 1L) return true;
        }
        return jumps.length == 1;
    }

    static int completeCircuit(int[] gas, int[] cost) {
        requireNonNegative(gas, "gas");
        requireNonNegative(cost, "cost");
        if (gas.length == 0 || gas.length != cost.length) {
            throw new IllegalArgumentException("gas and cost must have the same positive length");
        }
        long total = 0, tank = 0;
        int candidate = 0;
        for (int i = 0; i < gas.length; i++) {
            long delta = (long) gas[i] - cost[i];
            total += delta;
            tank += delta;
            if (tank < 0) {
                candidate = i + 1;
                tank = 0;
            }
        }
        return total < 0 ? -1 : candidate % gas.length;
    }

    static List<Integer> partitionLabels(String text) {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("text must be non-empty");
        int[] last = new int[26];
        Arrays.fill(last, -1);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 'a' || c > 'z') throw new IllegalArgumentException("text must be lowercase ASCII");
            last[c - 'a'] = i;
        }
        List<Integer> sizes = new ArrayList<>();
        int start = 0, end = 0;
        for (int i = 0; i < text.length(); i++) {
            end = Math.max(end, last[text.charAt(i) - 'a']);
            if (i == end) {
                sizes.add(end - start + 1);
                start = i + 1;
            }
        }
        return sizes;
    }

    static int minimumBoats(int[] people, int limit) {
        if (people == null) throw new IllegalArgumentException("people must not be null");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        int[] sorted = people.clone();
        for (int weight : sorted) {
            if (weight <= 0 || weight > limit) throw new IllegalArgumentException("each person needs a feasible positive weight");
        }
        Arrays.sort(sorted);
        int light = 0, heavy = sorted.length - 1, boats = 0;
        while (light <= heavy) {
            if (light < heavy && sorted[light] + sorted[heavy] <= limit) light++;
            heavy--;
            boats++;
        }
        return boats;
    }

    static int leastIntervals(char[] tasks, int cooldown) {
        if (tasks == null) throw new IllegalArgumentException("tasks must not be null");
        if (cooldown < 0) throw new IllegalArgumentException("cooldown must be non-negative");
        if (tasks.length == 0) return 0;
        int[] frequency = new int[26];
        for (char task : tasks) {
            if (task < 'A' || task > 'Z') throw new IllegalArgumentException("tasks must be uppercase ASCII");
            frequency[task - 'A']++;
        }
        int maximum = 0, tied = 0;
        for (int count : frequency) {
            if (count > maximum) {
                maximum = count;
                tied = 1;
            } else if (count == maximum) {
                tied++;
            }
        }
        long frame = (long) (maximum - 1) * (cooldown + 1L) + tied;
        return Math.toIntExact(Math.max(tasks.length, frame));
    }

    static long minimumMovesToEqual(int[] values) {
        if (values == null || values.length == 0) throw new IllegalArgumentException("values must be non-empty");
        long sum = 0;
        int minimum = Integer.MAX_VALUE;
        for (int value : values) {
            sum += value;
            minimum = Math.min(minimum, value);
        }
        return sum - (long) minimum * values.length;
    }

    private static boolean canJumpOracle(int[] jumps) {
        boolean[] reached = new boolean[jumps.length];
        reached[0] = true;
        for (int i = 0; i < jumps.length; i++) {
            if (!reached[i]) continue;
            for (int step = 1; step <= jumps[i] && i + step < jumps.length; step++) reached[i + step] = true;
        }
        return reached[jumps.length - 1];
    }

    private static int circuitOracle(int[] gas, int[] cost) {
        for (int start = 0; start < gas.length; start++) {
            long tank = 0;
            boolean works = true;
            for (int offset = 0; offset < gas.length; offset++) {
                int i = (start + offset) % gas.length;
                tank += (long) gas[i] - cost[i];
                if (tank < 0) { works = false; break; }
            }
            if (works) return start;
        }
        return -1;
    }

    private static List<Integer> partitionOracle(String text) {
        int boundaries = text.length() - 1;
        List<Integer> best = null;
        for (int mask = 0; mask < (1 << boundaries); mask++) {
            List<Integer> sizes = new ArrayList<>();
            int start = 0;
            for (int i = 0; i < boundaries; i++) {
                if ((mask & (1 << i)) != 0) {
                    sizes.add(i - start + 1);
                    start = i + 1;
                }
            }
            sizes.add(text.length() - start);
            if (validPartition(text, sizes) && (best == null || sizes.size() > best.size())) best = sizes;
        }
        return best;
    }

    private static boolean validPartition(String text, List<Integer> sizes) {
        int[] owner = new int[26];
        Arrays.fill(owner, -1);
        int position = 0;
        for (int part = 0; part < sizes.size(); part++) {
            for (int end = position + sizes.get(part); position < end; position++) {
                int letter = text.charAt(position) - 'a';
                if (owner[letter] != -1 && owner[letter] != part) return false;
                owner[letter] = part;
            }
        }
        return position == text.length();
    }

    private static int boatsOracle(int[] people, int limit) {
        return boatsOracle(people, limit, (1 << people.length) - 1, new HashMap<>());
    }

    private static int boatsOracle(int[] people, int limit, int mask, Map<Integer, Integer> memo) {
        if (mask == 0) return 0;
        Integer known = memo.get(mask);
        if (known != null) return known;
        int first = Integer.numberOfTrailingZeros(mask);
        int withoutFirst = mask & ~(1 << first);
        int best = 1 + boatsOracle(people, limit, withoutFirst, memo);
        for (int other = first + 1; other < people.length; other++) {
            if ((withoutFirst & (1 << other)) != 0 && people[first] + people[other] <= limit) {
                best = Math.min(best, 1 + boatsOracle(people, limit, withoutFirst & ~(1 << other), memo));
            }
        }
        memo.put(mask, best);
        return best;
    }

    private record ScheduleState(String counts, String cooling) {}

    private static int intervalOracle(char[] tasks, int cooldown) {
        int kinds = 0;
        for (char task : tasks) kinds = Math.max(kinds, task - 'A' + 1);
        int[] counts = new int[kinds], cooling = new int[kinds];
        for (char task : tasks) counts[task - 'A']++;
        return scheduleOracle(counts, cooling, cooldown, new HashMap<>());
    }

    private static int scheduleOracle(int[] counts, int[] cooling, int cooldown, Map<ScheduleState, Integer> memo) {
        int remaining = Arrays.stream(counts).sum();
        if (remaining == 0) return 0;
        ScheduleState state = new ScheduleState(Arrays.toString(counts), Arrays.toString(cooling));
        Integer known = memo.get(state);
        if (known != null) return known;
        int best = Integer.MAX_VALUE / 4;
        boolean scheduled = false;
        for (int task = 0; task < counts.length; task++) {
            if (counts[task] == 0 || cooling[task] != 0) continue;
            scheduled = true;
            int[] nextCounts = counts.clone(), nextCooling = tick(cooling);
            nextCounts[task]--;
            if (nextCounts[task] > 0) nextCooling[task] = cooldown;
            best = Math.min(best, 1 + scheduleOracle(nextCounts, nextCooling, cooldown, memo));
        }
        if (!scheduled) best = 1 + scheduleOracle(counts, tick(cooling), cooldown, memo);
        memo.put(state, best);
        return best;
    }

    private static int[] tick(int[] cooling) {
        int[] next = cooling.clone();
        for (int i = 0; i < next.length; i++) if (next[i] > 0) next[i]--;
        return next;
    }

    private static long movesOracle(int[] values) {
        int[] work = values.clone();
        long moves = 0;
        while (true) {
            int minimum = Arrays.stream(work).min().orElseThrow();
            int maximum = Arrays.stream(work).max().orElseThrow();
            if (minimum == maximum) return moves;
            for (int i = 0; i < work.length; i++) {
                if (work[i] == maximum) { work[i]--; moves++; break; }
            }
        }
    }

    private static void requireNonNegative(int[] values, String name) {
        if (values == null) throw new IllegalArgumentException(name + " must not be null");
        for (int value : values) if (value < 0) throw new IllegalArgumentException(name + " must be non-negative");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int groups = 0;
        check(canJump(new int[]{2, 3, 1, 1, 4}), "jump reachable"); groups++;
        check(!canJump(new int[]{3, 2, 1, 0, 4}), "jump blocked"); groups++;
        check(canJump(new int[]{0}), "jump singleton"); groups++;
        try { canJump(new int[]{1, -1}); throw new AssertionError("negative jump accepted"); } catch (IllegalArgumentException expected) { groups++; }

        check(completeCircuit(new int[]{1,2,3,4,5}, new int[]{3,4,5,1,2}) == 3, "circuit official"); groups++;
        check(completeCircuit(new int[]{2,3,4}, new int[]{3,4,3}) == -1, "circuit impossible"); groups++;
        check(completeCircuit(new int[]{0}, new int[]{0}) == 0, "circuit singleton"); groups++;

        check(partitionLabels("ababcbacadefegdehijhklij").equals(List.of(9,7,8)), "partition official"); groups++;
        check(partitionLabels("eccbbbbdec").equals(List.of(10)), "partition closure"); groups++;
        check(partitionLabels("abc").equals(List.of(1,1,1)), "partition independent"); groups++;

        check(minimumBoats(new int[]{1,2}, 3) == 1, "boats pair"); groups++;
        check(minimumBoats(new int[]{3,2,2,1}, 3) == 3, "boats official"); groups++;
        check(minimumBoats(new int[]{3,5,3,4}, 5) == 4, "boats singles"); groups++;

        check(leastIntervals(new char[]{'A','A','A','B','B','B'}, 2) == 8, "scheduler idle"); groups++;
        check(leastIntervals(new char[]{'A','C','A','B','D','B'}, 1) == 6, "scheduler filled"); groups++;
        check(leastIntervals(new char[]{'A','A','A','B','B','B'}, 3) == 10, "scheduler wider cooldown"); groups++;

        check(minimumMovesToEqual(new int[]{1,2,3}) == 3, "moves official"); groups++;
        check(minimumMovesToEqual(new int[]{1,1,1}) == 0, "moves equal"); groups++;
        check(minimumMovesToEqual(new int[]{-2,0,2}) == 6, "moves signed"); groups++;
        int[] preserved = {Integer.MIN_VALUE, Integer.MIN_VALUE + 2};
        int[] copy = preserved.clone();
        check(minimumMovesToEqual(preserved) == 2 && Arrays.equals(preserved, copy), "moves widened/preserved"); groups++;

        Random random = new Random(0x6AEE_D123L);
        int trials = 20_000;
        for (int trial = 0; trial < trials; trial++) {
            int n = 1 + random.nextInt(9);
            int[] jumps = new int[n];
            for (int i = 0; i < n; i++) jumps[i] = random.nextInt(5);
            check(canJump(jumps) == canJumpOracle(jumps), "jump oracle");

            int[] gas = new int[n], cost = new int[n];
            for (int i = 0; i < n; i++) { gas[i] = random.nextInt(7); cost[i] = random.nextInt(7); }
            check(completeCircuit(gas, cost) == circuitOracle(gas, cost), "circuit oracle");

            int length = 1 + random.nextInt(9);
            StringBuilder text = new StringBuilder(length);
            for (int i = 0; i < length; i++) text.append((char) ('a' + random.nextInt(5)));
            check(partitionLabels(text.toString()).equals(partitionOracle(text.toString())), "partition oracle");

            int limit = 2 + random.nextInt(12);
            int[] people = new int[n];
            for (int i = 0; i < n; i++) people[i] = 1 + random.nextInt(limit);
            int[] peopleCopy = people.clone();
            check(minimumBoats(people, limit) == boatsOracle(people, limit) && Arrays.equals(people, peopleCopy), "boats oracle/preservation");

            int taskCount = 1 + random.nextInt(8), kinds = 1 + random.nextInt(4), cooldown = random.nextInt(4);
            char[] tasks = new char[taskCount];
            for (int i = 0; i < taskCount; i++) tasks[i] = (char) ('A' + random.nextInt(kinds));
            check(leastIntervals(tasks, cooldown) == intervalOracle(tasks, cooldown), "scheduler oracle");

            int[] values = new int[1 + random.nextInt(6)];
            for (int i = 0; i < values.length; i++) values[i] = random.nextInt(11) - 5;
            check(minimumMovesToEqual(values) == movesOracle(values), "moves oracle");
        }
        System.out.printf("GreedyArrayPatternContracts: %d deterministic groups + %d seeded trials; %d oracle/preservation checks passed.%n",
                groups, trials, trials * 6);
    }
}
