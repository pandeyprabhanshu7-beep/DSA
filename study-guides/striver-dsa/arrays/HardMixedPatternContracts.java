import java.util.*;

/** Executable contracts for the seven Hard-mixed drills not covered by earlier cards. */
public class HardMixedPatternContracts {
    private static int groups;
    private static long trials;
    private static final Random RANDOM = new Random(0x484152444D495845L);

    public static void main(String[] args) {
        deterministic();
        randomized(20_000);
        System.out.printf("Hard mixed contracts: %d groups, %,d seeded trials, %,d oracle/preservation checks%n",
                groups, trials, trials * 7);
    }

    static long countRangeSum(int[] nums, long lower, long upper) {
        Objects.requireNonNull(nums);
        if (lower > upper) throw new IllegalArgumentException("lower > upper");
        long[] prefix = new long[nums.length + 1];
        for (int i = 0; i < nums.length; i++) prefix[i + 1] = prefix[i] + nums[i];
        return countRangeSum(prefix, new long[prefix.length], 0, prefix.length, lower, upper);
    }

    private static long countRangeSum(long[] p, long[] work, int lo, int hi, long lower, long upper) {
        if (hi - lo <= 1) return 0;
        int mid = (lo + hi) >>> 1;
        long count = countRangeSum(p, work, lo, mid, lower, upper)
                + countRangeSum(p, work, mid, hi, lower, upper);
        int first = mid, past = mid;
        for (int left = lo; left < mid; left++) {
            while (first < hi && p[first] - p[left] < lower) first++;
            while (past < hi && p[past] - p[left] <= upper) past++;
            count += past - first;
        }
        int i = lo, j = mid, w = lo;
        while (i < mid || j < hi) work[w++] = j == hi || (i < mid && p[i] <= p[j]) ? p[i++] : p[j++];
        System.arraycopy(work, lo, p, lo, hi - lo);
        return count;
    }

    static int[] maxSumThreeSubarrays(int[] nums, int k) {
        Objects.requireNonNull(nums);
        if (k <= 0 || 3L * k > nums.length) throw new IllegalArgumentException("need three positive-width windows");
        int windows = nums.length - k + 1;
        long[] sum = new long[windows];
        long running = 0;
        for (int i = 0; i < nums.length; i++) {
            running += nums[i];
            if (i >= k) running -= nums[i - k];
            if (i >= k - 1) sum[i - k + 1] = running;
        }
        int[] left = new int[windows], right = new int[windows];
        for (int i = 1; i < windows; i++) left[i] = sum[i] > sum[left[i - 1]] ? i : left[i - 1];
        right[windows - 1] = windows - 1;
        for (int i = windows - 2; i >= 0; i--) right[i] = sum[i] >= sum[right[i + 1]] ? i : right[i + 1];
        long best = Long.MIN_VALUE;
        int[] answer = null;
        for (int middle = k; middle + k < windows; middle++) {
            int a = left[middle - k], c = right[middle + k];
            long candidate = sum[a] + sum[middle] + sum[c];
            if (candidate > best) {
                best = candidate;
                answer = new int[]{a, middle, c};
            }
        }
        return answer;
    }

    static long subarraysWithKDistinct(int[] nums, int k) {
        Objects.requireNonNull(nums);
        if (k <= 0) return 0;
        return atMost(nums, k) - atMost(nums, k - 1);
    }

    private static long atMost(int[] nums, int budget) {
        Map<Integer, Integer> frequency = new HashMap<>();
        long total = 0;
        int left = 0;
        for (int right = 0; right < nums.length; right++) {
            frequency.merge(nums[right], 1, Integer::sum);
            while (frequency.size() > budget) {
                int value = nums[left++];
                if (frequency.merge(value, -1, Integer::sum) == 0) frequency.remove(value);
            }
            total += right - left + 1L;
        }
        return total;
    }

    static String minWindow(String source, String target) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        if (target.isEmpty()) return "";
        int[] need = new int[Character.MAX_VALUE + 1];
        for (int i = 0; i < target.length(); i++) need[target.charAt(i)]++;
        int missing = target.length(), left = 0, bestStart = 0, bestLength = Integer.MAX_VALUE;
        for (int right = 0; right < source.length(); right++) {
            if (need[source.charAt(right)]-- > 0) missing--;
            while (missing == 0) {
                int length = right - left + 1;
                if (length < bestLength) { bestLength = length; bestStart = left; }
                if (++need[source.charAt(left++)] > 0) missing++;
            }
        }
        return bestLength == Integer.MAX_VALUE ? "" : source.substring(bestStart, bestStart + bestLength);
    }

    static int maximalRectangle(char[][] matrix) {
        Objects.requireNonNull(matrix);
        if (matrix.length == 0) return 0;
        int columns = Objects.requireNonNull(matrix[0]).length;
        int[] height = new int[columns + 1];
        int best = 0;
        for (char[] row : matrix) {
            if (Objects.requireNonNull(row).length != columns) throw new IllegalArgumentException("ragged matrix");
            for (int c = 0; c < columns; c++) {
                if (row[c] != '0' && row[c] != '1') throw new IllegalArgumentException("matrix must be binary");
                height[c] = row[c] == '1' ? height[c] + 1 : 0;
            }
            Deque<Integer> stack = new ArrayDeque<>();
            for (int c = 0; c <= columns; c++) {
                while (!stack.isEmpty() && height[stack.peek()] > height[c]) {
                    int h = height[stack.pop()];
                    int start = stack.isEmpty() ? 0 : stack.peek() + 1;
                    best = Math.max(best, h * (c - start));
                }
                stack.push(c);
            }
        }
        return best;
    }

    static long maxCoins(int[] nums) {
        Objects.requireNonNull(nums);
        int n = nums.length;
        long[] value = new long[n + 2];
        value[0] = value[n + 1] = 1;
        for (int i = 0; i < n; i++) {
            if (nums[i] < 0) throw new IllegalArgumentException("negative balloon");
            value[i + 1] = nums[i];
        }
        long[][] dp = new long[n + 2][n + 2];
        for (int width = 1; width <= n; width++) {
            for (int left = 1; left + width - 1 <= n; left++) {
                int right = left + width - 1;
                for (int last = left; last <= right; last++) {
                    dp[left][right] = Math.max(dp[left][right], dp[left][last - 1] + dp[last + 1][right]
                            + value[left - 1] * value[last] * value[right + 1]);
                }
            }
        }
        return dp[1][n];
    }

    static int maxEnvelopes(int[][] envelopes) {
        Objects.requireNonNull(envelopes);
        int[][] ordered = copy(envelopes);
        Arrays.sort(ordered, Comparator.<int[]>comparingInt(e -> e[0]).thenComparing((a, b) -> Integer.compare(b[1], a[1])));
        int[] tails = new int[ordered.length];
        int size = 0;
        for (int[] envelope : ordered) {
            int lo = 0, hi = size;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (tails[mid] < envelope[1]) lo = mid + 1; else hi = mid;
            }
            tails[lo] = envelope[1];
            if (lo == size) size++;
        }
        return size;
    }

    private static void deterministic() {
        check(countRangeSum(new int[]{-2, 5, -1}, -2, 2) == 3); groups++;
        check(countRangeSum(new int[]{0}, 0, 0) == 1); groups++;
        check(countRangeSum(new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE}, -1, 0) == 1); groups++;
        check(Arrays.equals(maxSumThreeSubarrays(new int[]{1,2,1,2,6,7,5,1}, 2), new int[]{0,3,5})); groups++;
        check(Arrays.equals(maxSumThreeSubarrays(new int[]{1,2,1,2,1,2,1,2,1}, 2), new int[]{0,2,4})); groups++;
        check(Arrays.equals(maxSumThreeSubarrays(new int[]{0,0,0}, 1), new int[]{0,1,2})); groups++;
        check(subarraysWithKDistinct(new int[]{1,2,1,2,3}, 2) == 7); groups++;
        check(subarraysWithKDistinct(new int[]{1,2,1,3,4}, 3) == 3); groups++;
        check(subarraysWithKDistinct(new int[]{1,1}, 1) == 3); groups++;
        check(minWindow("ADOBECODEBANC", "ABC").equals("BANC")); groups++;
        check(minWindow("a", "aa").equals("")); groups++;
        check(minWindow("aa", "aa").equals("aa")); groups++;
        check(maximalRectangle(new char[][]{{'1','0','1','0','0'},{'1','0','1','1','1'},{'1','1','1','1','1'},{'1','0','0','1','0'}}) == 6); groups++;
        check(maximalRectangle(new char[][]{{'0'}}) == 0); groups++;
        check(maximalRectangle(new char[][]{{'1'}}) == 1); groups++;
        check(maxCoins(new int[]{3,1,5,8}) == 167); groups++;
        check(maxCoins(new int[]{1,5}) == 10); groups++;
        check(maxCoins(new int[]{}) == 0); groups++;
        check(maxEnvelopes(new int[][]{{5,4},{6,4},{6,7},{2,3}}) == 3); groups++;
        check(maxEnvelopes(new int[][]{{1,1},{1,1},{1,1}}) == 1); groups++;
    }

    private static void randomized(int rounds) {
        for (int round = 0; round < rounds; round++) {
            int[] signed = ints(RANDOM.nextInt(10), -5, 6), beforeSigned = signed.clone();
            long a = RANDOM.nextInt(-8, 9), b = RANDOM.nextInt(-8, 9), lower = Math.min(a,b), upper = Math.max(a,b);
            check(countRangeSum(signed, lower, upper) == rangeOracle(signed, lower, upper) && Arrays.equals(signed, beforeSigned));

            int k = RANDOM.nextInt(1, 4), n = RANDOM.nextInt(3 * k, 11);
            int[] positive = ints(n, 0, 10), beforePositive = positive.clone();
            check(Arrays.equals(maxSumThreeSubarrays(positive, k), tripleOracle(positive, k)) && Arrays.equals(positive, beforePositive));

            int[] values = ints(RANDOM.nextInt(10), -2, 4), beforeValues = values.clone();
            int distinct = RANDOM.nextInt(1, 6);
            check(subarraysWithKDistinct(values, distinct) == distinctOracle(values, distinct) && Arrays.equals(values, beforeValues));

            String source = randomString(RANDOM.nextInt(10)), target = randomString(RANDOM.nextInt(1, 5));
            check(minWindow(source, target).equals(windowOracle(source, target)));

            char[][] matrix = randomMatrix(RANDOM.nextInt(1, 5), RANDOM.nextInt(1, 6));
            char[][] beforeMatrix = copy(matrix);
            check(maximalRectangle(matrix) == rectangleOracle(matrix) && Arrays.deepEquals(matrix, beforeMatrix));

            int[] balloons = ints(RANDOM.nextInt(7), 0, 6), beforeBalloons = balloons.clone();
            check(maxCoins(balloons) == balloonOracle(balloons) && Arrays.equals(balloons, beforeBalloons));

            int[][] envelopes = new int[RANDOM.nextInt(9)][2];
            for (int[] e : envelopes) { e[0] = RANDOM.nextInt(1, 7); e[1] = RANDOM.nextInt(1, 7); }
            int[][] beforeEnvelopes = copy(envelopes);
            check(maxEnvelopes(envelopes) == envelopeOracle(envelopes) && Arrays.deepEquals(envelopes, beforeEnvelopes));
            trials++;
        }
    }

    private static long rangeOracle(int[] a, long lower, long upper) {
        long count = 0;
        for (int i = 0; i < a.length; i++) { long sum = 0; for (int j = i; j < a.length; j++) { sum += a[j]; if (lower <= sum && sum <= upper) count++; } }
        return count;
    }

    private static int[] tripleOracle(int[] a, int k) {
        long best = Long.MIN_VALUE; int[] answer = null;
        for (int i = 0; i + 3 * k <= a.length; i++) for (int j = i + k; j + 2 * k <= a.length; j++)
            for (int m = j + k; m + k <= a.length; m++) {
                long sum = window(a,i,k) + window(a,j,k) + window(a,m,k);
                int[] candidate = {i,j,m};
                if (sum > best || sum == best && lexicographicallyBefore(candidate, answer)) { best = sum; answer = candidate; }
            }
        return answer;
    }

    private static long window(int[] a, int start, int k) { long sum=0; for(int i=start;i<start+k;i++) sum+=a[i]; return sum; }
    private static boolean lexicographicallyBefore(int[] a, int[] b) { if (b == null) return true; for(int i=0;i<a.length;i++) if(a[i]!=b[i]) return a[i]<b[i]; return false; }
    private static long distinctOracle(int[] a, int k) { long total=0; for(int i=0;i<a.length;i++){Set<Integer>s=new HashSet<>();for(int j=i;j<a.length;j++){s.add(a[j]);if(s.size()==k)total++;}}return total; }
    private static String windowOracle(String s, String t) {
        int[] need = counts(t); String best = "";
        for(int i=0;i<s.length();i++){int[] have=new int[Character.MAX_VALUE+1];for(int j=i;j<s.length();j++){have[s.charAt(j)]++;if(covers(have,need)&&(best.isEmpty()||j-i+1<best.length()))best=s.substring(i,j+1);}}
        return best;
    }
    private static int[] counts(String s){int[] c=new int[Character.MAX_VALUE+1];for(int i=0;i<s.length();i++)c[s.charAt(i)]++;return c;}
    private static boolean covers(int[] a,int[] b){for(int i=0;i<b.length;i++)if(a[i]<b[i])return false;return true;}
    private static int rectangleOracle(char[][] m){int best=0;for(int r1=0;r1<m.length;r1++)for(int r2=r1;r2<m.length;r2++)for(int c1=0;c1<m[0].length;c1++)for(int c2=c1;c2<m[0].length;c2++){boolean ok=true;for(int r=r1;r<=r2;r++)for(int c=c1;c<=c2;c++)ok&=m[r][c]=='1';if(ok)best=Math.max(best,(r2-r1+1)*(c2-c1+1));}return best;}
    private static long balloonOracle(int[] a){List<Integer> values=new ArrayList<>();for(int v:a)values.add(v);return balloonOracle(values,new HashMap<>());}
    private static long balloonOracle(List<Integer> a,Map<String,Long> memo){if(a.isEmpty())return 0;String key=a.toString();if(memo.containsKey(key))return memo.get(key);long best=0;for(int i=0;i<a.size();i++){long left=i==0?1:a.get(i-1),right=i+1==a.size()?1:a.get(i+1);List<Integer> next=new ArrayList<>(a);int value=next.remove(i);best=Math.max(best,left*value*right+balloonOracle(next,memo));}memo.put(key,best);return best;}
    private static int envelopeOracle(int[][] e){int[] memo=new int[e.length];int best=0;for(int i=0;i<e.length;i++)best=Math.max(best,envelopeFrom(e,i,memo));return best;}
    private static int envelopeFrom(int[][]e,int i,int[]memo){if(memo[i]>0)return memo[i];int best=1;for(int j=0;j<e.length;j++)if(e[j][0]>e[i][0]&&e[j][1]>e[i][1])best=Math.max(best,1+envelopeFrom(e,j,memo));return memo[i]=best;}
    private static int[] ints(int n,int lo,int hi){int[]a=new int[n];for(int i=0;i<n;i++)a[i]=RANDOM.nextInt(lo,hi);return a;}
    private static String randomString(int n){String alphabet="ABCD";StringBuilder s=new StringBuilder(n);for(int i=0;i<n;i++)s.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));return s.toString();}
    private static char[][] randomMatrix(int r,int c){char[][]m=new char[r][c];for(char[]row:m)for(int j=0;j<c;j++)row[j]=RANDOM.nextBoolean()?'1':'0';return m;}
    private static int[][] copy(int[][] a){int[][]c=new int[a.length][];for(int i=0;i<a.length;i++){if(a[i]==null||a[i].length!=2)throw new IllegalArgumentException("expected pairs");c[i]=a[i].clone();}return c;}
    private static char[][] copy(char[][] a){char[][]c=new char[a.length][];for(int i=0;i<a.length;i++)c[i]=a[i].clone();return c;}
    private static void check(boolean condition){if(!condition)throw new AssertionError("contract failed");}
}
