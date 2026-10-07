# HashMap & Hashing — Deep Study Guide

## Navigation and Index

> Use this as the home page for the book. Every card ends with a return link.

| ID | Topic | Jump |
|---|---|---|
| **H-1** | Contains Duplicate | [Jump](#h-1-contains-duplicate) |
| **H-2** | Frequency Count / Majority Element | [Jump](#h-2-frequency-count-majority-element) |
| **H-3** | Two Sum | [Jump](#h-3-two-sum) |
| **H-4** | Valid Anagram | [Jump](#h-4-valid-anagram) |
| **H-5** | Intersection of Two Arrays | [Jump](#h-5-intersection-of-two-arrays) |
| **H-6** | First Unique Character | [Jump](#h-6-first-unique-character) |
| **H-7** | Isomorphic Strings | [Jump](#h-7-isomorphic-strings) |
| **H-8** | Group Anagrams | [Jump](#h-8-group-anagrams) |
| **H-9** | Longest Consecutive Sequence | [Jump](#h-9-longest-consecutive-sequence) |
| **H-10** | Subarray Sum Equals K | [Jump](#h-10-subarray-sum-equals-k) |
| **H-11** | Longest Subarray with Sum Zero | [Jump](#h-11-longest-subarray-with-sum-zero) |
| **H-12** | Count Subarrays with XOR K | [Jump](#h-12-count-subarrays-with-xor-k) |
| **H-13** | 4Sum II — Meet in the Middle with HashMap | [Jump](#h-13-4sum-ii-meet-in-the-middle-with-hashmap) |
| **H-14** | Top K Frequent Elements | [Jump](#h-14-top-k-frequent-elements) |
| **H-15** | Longest Substring Without Repeating Characters | [Jump](#h-15-longest-substring-without-repeating-characters) |
| **H-16** | Minimum Window Substring | [Jump](#h-16-minimum-window-substring) |
| **H-17** | Valid Sudoku | [Jump](#h-17-valid-sudoku) |
| **H-18** | Insert Delete GetRandom O(1) — RandomizedSet | [Jump](#h-18-insert-delete-getrandom-o-1-randomizedset) |
| **H-19** | LRU Cache — HashMap + Doubly Linked List | [Jump](#h-19-lru-cache-hashmap-doubly-linked-list) |
| **H-20** | Path Sum III — HashMap Inside a Tree DFS | [Jump](#h-20-path-sum-iii-hashmap-inside-a-tree-dfs) |
| **H-21** | Copy List with Random Pointer — HashMap Object Mapping | [Jump](#h-21-copy-list-with-random-pointer-hashmap-object-mapping) |
| **H-22** | All O(1) Data Structure — HashMap + Frequency Bucket List | [Jump](#h-22-all-o-1-data-structure-hashmap-frequency-bucket-list) |
| **H-23** | Longest Subarray with Sum K | [Jump](#h-23-longest-subarray-with-sum-k) |
| **H-24** | Contiguous Array — Equal 0s and 1s | [Jump](#h-24-contiguous-array-equal-0s-and-1s) |
| **H-25** | Count Distinct Elements in Every Window | [Jump](#h-25-count-distinct-elements-in-every-window) |
| **H-26** | Happy Number — HashSet Cycle Detection | [Jump](#h-26-happy-number-hashset-cycle-detection) |
| **H-27** | Word Pattern — Bijection | [Jump](#h-27-word-pattern-bijection) |
| **H-28** | Clone Graph — Original Node to Copied Node | [Jump](#h-28-clone-graph-original-node-to-copied-node) |
| **H-29** | Time-Based Key-Value Store | [Jump](#h-29-time-based-key-value-store) |
| **H-30** | RandomizedCollection — Duplicates Allowed | [Jump](#h-30-randomizedcollection-duplicates-allowed) |

## How to study this book

Do not stop at “use a HashMap.” Write key → what?: membership, frequency, earliest index, latest index, prefix frequency, canonical group, or object reference. The payload is usually the actual algorithm.

**Required reading order:** requirement → examples → data picture → brute force → repeated-work diagnosis → invariant → optimized plan → dry run → Java → correctness → boundaries → complexity → memory trick → variations.

> **Source transparency:** topic ordering follows the retained Striver/Take U Forward study structure from this project. Extra examples, memory tricks, research notes and explanations are study-guide additions unless explicitly marked as verified from a creator/source.

## Pattern recognition cheat sheet

| Clue | Think about first |
|---|---|
| next/previous greater/smaller | monotonic stack |
| fixed-size best window | deque / sliding window |
| shortest unweighted path | BFS |
| nonnegative weighted shortest path | Dijkstra |
| negative edges | Bellman-Ford |
| dependencies | topological sort |
| repeated component merges | DSU |
| critical edge/vertex | low-link DFS |
| linked-list middle/cycle | slow/fast pointers |
| tree child summaries | postorder DP |
| repeated lookup/count | HashMap / HashSet |
| subarray sum/count with negatives | prefix state + HashMap |

<a id="h-1-contains-duplicate"></a>
## H-1. Contains Duplicate

[Index](#navigation-and-index) · [H-2 →](#h-2-frequency-count-majority-element)

### Detailed question understanding

**What is the problem/lesson asking?** Contains Duplicate. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-2-frequency-count-majority-element"></a>
## H-2. Frequency Count / Majority Element

[← H-1](#h-1-contains-duplicate) · [Index](#navigation-and-index) · [H-3 →](#h-3-two-sum)

### Detailed question understanding

**What is the problem/lesson asking?** Frequency Count / Majority Element. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-3-two-sum"></a>
## H-3. Two Sum

[← H-2](#h-2-frequency-count-majority-element) · [Index](#navigation-and-index) · [H-4 →](#h-4-valid-anagram)

### Detailed question understanding

**What is the problem/lesson asking?** Two Sum. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `[2,7,11,15], target=9` | `[0,1]` | complement |
| 2 | `[3,3], target=6` | `two distinct indices` | duplicate values |
| 3 | `[-1,4,2], target=3` | `-1 and 4` | negative values |

### Pattern recognition

**Primary pattern:** Complement lookup

> **KEY INTUITION —** At x, derive target−x and ask whether that exact partner occurred earlier.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
static int[] twoSum(int[] a,int target){
    Map<Integer,Integer> pos=new HashMap<>();
    for(int i=0;i<a.length;i++){
        int need=target-a[i];
        if(pos.containsKey(need)) return new int[]{pos.get(need),i};
        pos.put(a[i],i);
    }
    return new int[0];
}
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(n) time; O(n) map.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** PAIR TARGET → LOOK UP THE COMPLEMENT.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-4-valid-anagram"></a>
## H-4. Valid Anagram

[← H-3](#h-3-two-sum) · [Index](#navigation-and-index) · [H-5 →](#h-5-intersection-of-two-arrays)

### Detailed question understanding

**What is the problem/lesson asking?** Valid Anagram. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Canonical signature

> **KEY INTUITION —** Equivalent strings must produce the same stable signature: sorted form or fixed-alphabet count vector.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**O(total characters) with fixed alphabet; group storage O(input).**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** EQUIVALENCE GROUPING → CANONICAL KEY.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-5-intersection-of-two-arrays"></a>
## H-5. Intersection of Two Arrays

[← H-4](#h-4-valid-anagram) · [Index](#navigation-and-index) · [H-6 →](#h-6-first-unique-character)

### Detailed question understanding

**What is the problem/lesson asking?** Intersection of Two Arrays. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-6-first-unique-character"></a>
## H-6. First Unique Character

[← H-5](#h-5-intersection-of-two-arrays) · [Index](#navigation-and-index) · [H-7 →](#h-7-isomorphic-strings)

### Detailed question understanding

**What is the problem/lesson asking?** First Unique Character. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-7-isomorphic-strings"></a>
## H-7. Isomorphic Strings

[← H-6](#h-6-first-unique-character) · [Index](#navigation-and-index) · [H-8 →](#h-8-group-anagrams)

### Detailed question understanding

**What is the problem/lesson asking?** Isomorphic Strings. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-8-group-anagrams"></a>
## H-8. Group Anagrams

[← H-7](#h-7-isomorphic-strings) · [Index](#navigation-and-index) · [H-9 →](#h-9-longest-consecutive-sequence)

### Detailed question understanding

**What is the problem/lesson asking?** Group Anagrams. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Canonical signature

> **KEY INTUITION —** Equivalent strings must produce the same stable signature: sorted form or fixed-alphabet count vector.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**O(total characters) with fixed alphabet; group storage O(input).**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** EQUIVALENCE GROUPING → CANONICAL KEY.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-9-longest-consecutive-sequence"></a>
## H-9. Longest Consecutive Sequence

[← H-8](#h-8-group-anagrams) · [Index](#navigation-and-index) · [H-10 →](#h-10-subarray-sum-equals-k)

### Detailed question understanding

**What is the problem/lesson asking?** Longest Consecutive Sequence. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Start-only expansion

> **KEY INTUITION —** Walk a sequence only from x when x−1 is absent; this prevents repeated traversal of the same run.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(n); O(n) set.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** ONLY WALK FROM A TRUE SEQUENCE START.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-10-subarray-sum-equals-k"></a>
## H-10. Subarray Sum Equals K

[← H-9](#h-9-longest-consecutive-sequence) · [Index](#navigation-and-index) · [H-11 →](#h-11-longest-subarray-with-sum-zero)

### Detailed question understanding

**What is the problem/lesson asking?** Subarray Sum Equals K. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `[1,1,1], k=2` | `2` | prefix frequencies |
| 2 | `[1,-1,0], k=0` | `3` | negative values |
| 3 | `[3], k=3` | `1` | starts at index0 |

### Pattern recognition

**Primary pattern:** Prefix-state hashing

> **KEY INTUITION —** Translate the subarray relation into the earlier prefix state required. Count problems store frequency; longest problems store earliest index.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
static long countSubarrays(int[] a,long k){
    Map<Long,Integer> freq=new HashMap<>();
    freq.put(0L,1);
    long prefix=0, ans=0;
    for(int x:a){
        prefix+=x;
        ans+=freq.getOrDefault(prefix-k,0);
        freq.merge(prefix,1,Integer::sum);
    }
    return ans;
}
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(n) time; O(n) map.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** COUNT NEEDS HOW MANY; LONGEST NEEDS HOW EARLY.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-11-longest-subarray-with-sum-zero"></a>
## H-11. Longest Subarray with Sum Zero

[← H-10](#h-10-subarray-sum-equals-k) · [Index](#navigation-and-index) · [H-12 →](#h-12-count-subarrays-with-xor-k)

### Detailed question understanding

**What is the problem/lesson asking?** Longest Subarray with Sum Zero. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Prefix-state hashing

> **KEY INTUITION —** Translate the subarray relation into the earlier prefix state required. Count problems store frequency; longest problems store earliest index.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(n) time; O(n) map.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** COUNT NEEDS HOW MANY; LONGEST NEEDS HOW EARLY.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-12-count-subarrays-with-xor-k"></a>
## H-12. Count Subarrays with XOR K

[← H-11](#h-11-longest-subarray-with-sum-zero) · [Index](#navigation-and-index) · [H-13 →](#h-13-4sum-ii-meet-in-the-middle-with-hashmap)

### Detailed question understanding

**What is the problem/lesson asking?** Count Subarrays with XOR K. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Prefix-state hashing

> **KEY INTUITION —** Translate the subarray relation into the earlier prefix state required. Count problems store frequency; longest problems store earliest index.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(n) time; O(n) map.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** COUNT NEEDS HOW MANY; LONGEST NEEDS HOW EARLY.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-13-4sum-ii-meet-in-the-middle-with-hashmap"></a>
## H-13. 4Sum II — Meet in the Middle with HashMap

[← H-12](#h-12-count-subarrays-with-xor-k) · [Index](#navigation-and-index) · [H-14 →](#h-14-top-k-frequent-elements)

### Detailed question understanding

**What is the problem/lesson asking?** 4Sum II — Meet in the Middle with HashMap. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-14-top-k-frequent-elements"></a>
## H-14. Top K Frequent Elements

[← H-13](#h-13-4sum-ii-meet-in-the-middle-with-hashmap) · [Index](#navigation-and-index) · [H-15 →](#h-15-longest-substring-without-repeating-characters)

### Detailed question understanding

**What is the problem/lesson asking?** Top K Frequent Elements. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-15-longest-substring-without-repeating-characters"></a>
## H-15. Longest Substring Without Repeating Characters

[← H-14](#h-14-top-k-frequent-elements) · [Index](#navigation-and-index) · [H-16 →](#h-16-minimum-window-substring)

### Detailed question understanding

**What is the problem/lesson asking?** Longest Substring Without Repeating Characters. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Latest-index window

> **KEY INTUITION —** The last occurrence of a conflicting character tells exactly how far the left boundary may jump.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(n); O(k) distinct characters.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** LATEST INDEX TELLS HOW FAR TO JUMP.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-16-minimum-window-substring"></a>
## H-16. Minimum Window Substring

[← H-15](#h-15-longest-substring-without-repeating-characters) · [Index](#navigation-and-index) · [H-17 →](#h-17-valid-sudoku)

### Detailed question understanding

**What is the problem/lesson asking?** Minimum Window Substring. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Frequency-constrained window

> **KEY INTUITION —** Expand until all requirements are satisfied, then shrink while still valid.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**O(|s|+|t|) expected; O(k) counts.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** EXPAND UNTIL VALID; SHRINK WHILE VALID.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-17-valid-sudoku"></a>
## H-17. Valid Sudoku

[← H-16](#h-16-minimum-window-substring) · [Index](#navigation-and-index) · [H-18 →](#h-18-insert-delete-getrandom-o-1-randomizedset)

### Detailed question understanding

**What is the problem/lesson asking?** Valid Sudoku. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-18-insert-delete-getrandom-o-1-randomizedset"></a>
## H-18. Insert Delete GetRandom O(1) — RandomizedSet

[← H-17](#h-17-valid-sudoku) · [Index](#navigation-and-index) · [H-19 →](#h-19-lru-cache-hashmap-doubly-linked-list)

### Detailed question understanding

**What is the problem/lesson asking?** Insert Delete GetRandom O(1) — RandomizedSet. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Map + array

> **KEY INTUITION —** Map locates indices, ArrayList supplies uniform random index, swap-delete avoids O(n) shifting.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) operations; O(n) storage.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** MAP LOCATES; ARRAY RANDOMIZES.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-19-lru-cache-hashmap-doubly-linked-list"></a>
## H-19. LRU Cache — HashMap + Doubly Linked List

[← H-18](#h-18-insert-delete-getrandom-o-1-randomizedset) · [Index](#navigation-and-index) · [H-20 →](#h-20-path-sum-iii-hashmap-inside-a-tree-dfs)

### Detailed question understanding

**What is the problem/lesson asking?** LRU Cache — HashMap + Doubly Linked List. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Multi-index design

> **KEY INTUITION —** HashMap supplies direct identity; linked/frequency structures supply mutable order.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) operations; O(n) storage.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** ONE STRUCTURE LOCATES; ANOTHER ORDERS.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-20-path-sum-iii-hashmap-inside-a-tree-dfs"></a>
## H-20. Path Sum III — HashMap Inside a Tree DFS

[← H-19](#h-19-lru-cache-hashmap-doubly-linked-list) · [Index](#navigation-and-index) · [H-21 →](#h-21-copy-list-with-random-pointer-hashmap-object-mapping)

### Detailed question understanding

**What is the problem/lesson asking?** Path Sum III — HashMap Inside a Tree DFS. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-21-copy-list-with-random-pointer-hashmap-object-mapping"></a>
## H-21. Copy List with Random Pointer — HashMap Object Mapping

[← H-20](#h-20-path-sum-iii-hashmap-inside-a-tree-dfs) · [Index](#navigation-and-index) · [H-22 →](#h-22-all-o-1-data-structure-hashmap-frequency-bucket-list)

### Detailed question understanding

**What is the problem/lesson asking?** Copy List with Random Pointer — HashMap Object Mapping. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Object mapping

> **KEY INTUITION —** Memoize original→copy so cycles/shared references reuse the same clone.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(V+E) or O(n); O(number of originals) map.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** DEEP COPY NEEDS OLD→NEW MAP.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-22-all-o-1-data-structure-hashmap-frequency-bucket-list"></a>
## H-22. All O(1) Data Structure — HashMap + Frequency Bucket List

[← H-21](#h-21-copy-list-with-random-pointer-hashmap-object-mapping) · [Index](#navigation-and-index) · [H-23 →](#h-23-longest-subarray-with-sum-k)

### Detailed question understanding

**What is the problem/lesson asking?** All O(1) Data Structure — HashMap + Frequency Bucket List. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-23-longest-subarray-with-sum-k"></a>
## H-23. Longest Subarray with Sum K

[← H-22](#h-22-all-o-1-data-structure-hashmap-frequency-bucket-list) · [Index](#navigation-and-index) · [H-24 →](#h-24-contiguous-array-equal-0s-and-1s)

### Detailed question understanding

**What is the problem/lesson asking?** Longest Subarray with Sum K. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Prefix-state hashing

> **KEY INTUITION —** Translate the subarray relation into the earlier prefix state required. Count problems store frequency; longest problems store earliest index.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(n) time; O(n) map.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** COUNT NEEDS HOW MANY; LONGEST NEEDS HOW EARLY.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-24-contiguous-array-equal-0s-and-1s"></a>
## H-24. Contiguous Array — Equal 0s and 1s

[← H-23](#h-23-longest-subarray-with-sum-k) · [Index](#navigation-and-index) · [H-25 →](#h-25-count-distinct-elements-in-every-window)

### Detailed question understanding

**What is the problem/lesson asking?** Contiguous Array — Equal 0s and 1s. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Prefix-state hashing

> **KEY INTUITION —** Translate the subarray relation into the earlier prefix state required. Count problems store frequency; longest problems store earliest index.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(n) time; O(n) map.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** COUNT NEEDS HOW MANY; LONGEST NEEDS HOW EARLY.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-25-count-distinct-elements-in-every-window"></a>
## H-25. Count Distinct Elements in Every Window

[← H-24](#h-24-contiguous-array-equal-0s-and-1s) · [Index](#navigation-and-index) · [H-26 →](#h-26-happy-number-hashset-cycle-detection)

### Detailed question understanding

**What is the problem/lesson asking?** Count Distinct Elements in Every Window. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-26-happy-number-hashset-cycle-detection"></a>
## H-26. Happy Number — HashSet Cycle Detection

[← H-25](#h-25-count-distinct-elements-in-every-window) · [Index](#navigation-and-index) · [H-27 →](#h-27-word-pattern-bijection)

### Detailed question understanding

**What is the problem/lesson asking?** Happy Number — HashSet Cycle Detection. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-27-word-pattern-bijection"></a>
## H-27. Word Pattern — Bijection

[← H-26](#h-26-happy-number-hashset-cycle-detection) · [Index](#navigation-and-index) · [H-28 →](#h-28-clone-graph-original-node-to-copied-node)

### Detailed question understanding

**What is the problem/lesson asking?** Word Pattern — Bijection. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-28-clone-graph-original-node-to-copied-node"></a>
## H-28. Clone Graph — Original Node to Copied Node

[← H-27](#h-27-word-pattern-bijection) · [Index](#navigation-and-index) · [H-29 →](#h-29-time-based-key-value-store)

### Detailed question understanding

**What is the problem/lesson asking?** Clone Graph — Original Node to Copied Node. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Object mapping

> **KEY INTUITION —** Memoize original→copy so cycles/shared references reuse the same clone.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(V+E) or O(n); O(number of originals) map.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** DEEP COPY NEEDS OLD→NEW MAP.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-29-time-based-key-value-store"></a>
## H-29. Time-Based Key-Value Store

[← H-28](#h-28-clone-graph-original-node-to-copied-node) · [Index](#navigation-and-index) · [H-30 →](#h-30-randomizedcollection-duplicates-allowed)

### Detailed question understanding

**What is the problem/lesson asking?** Time-Based Key-Value Store. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Hash payload design

> **KEY INTUITION —** The real decision is not “HashMap?” but key→what exact reusable fact?

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) per ordinary hash operation; total depends on surrounding algorithm.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** STORE THE MINIMUM FACT THAT PREVENTS A RESCAN.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

<a id="h-30-randomizedcollection-duplicates-allowed"></a>
## H-30. RandomizedCollection — Duplicates Allowed

[← H-29](#h-29-time-based-key-value-store) · [Index](#navigation-and-index)

### Detailed question understanding

**What is the problem/lesson asking?** RandomizedCollection — Duplicates Allowed. Rewrite the exact input state, legal operation/relationship, and requested output before choosing a technique. Similar titles often hide materially different variants—for example nearest greater versus count of all greater values, four-direction versus eight-direction grid adjacency, or node identity versus equal node values.

### Three requirement-clarifying examples

| # | Input / setup | Output / observation | Why it matters |
|---:|---|---|---|
| 1 | `smallest valid input` | `trace base state` | base/boundary semantics |
| 2 | `typical nontrivial input` | `trace expected result` | core invariant |
| 3 | `boundary-shaped input` | `verify edge behavior` | protect implementation |

### Pattern recognition

**Primary pattern:** Map + array

> **KEY INTUITION —** Map locates indices, ArrayList supplies uniform random index, swap-delete avoids O(n) shifting.

### Brute-force / straightforward baseline

Start with the direct simulation/repeated scan that mirrors the statement. It is valuable because it exposes **what is being recomputed**. Then ask: *what exact fact from earlier work would make the next decision immediate?*

### Optimized reasoning

Name the invariant before code. The optimized solution for this family is derived from the intuition above. Every state change must preserve the invariant; if you cannot say what the working structure means after processing the first *i* items/nodes/states, the implementation is not ready.

### Detailed dry run

Show the derived lookup key, map before lookup, hit/miss, map after update, and answer after every step.

For interview practice, include at least four snapshots: initial state, first nontrivial state change, the state where the central invariant does real work, and final state. Explain **why each mutation occurred**, not merely what the values became.

### Java implementation / template

```java
// Card-specific Java implementation is expanded during the scheduled deepening pass.
// Interview discipline:
// 1) define the state/invariant,
// 2) implement exactly one valid transition,
// 3) test empty/single/duplicate/extreme cases,
// 4) use long for sums/distances when constraints can overflow int.
```

### Key line to highlight

Find the line that changes the invariant: the monotonic `while`, a graph relaxation `if`, a pointer splice, a recursive return combination, or a HashMap lookup/update. Explain what would break if its comparison or ordering changed.

### Correctness checklist

1. State the invariant before the loop/recursion.
2. Show it is true initially.
3. Show every transition preserves it.
4. Explain why the terminal state implies the requested answer.
5. For greedy algorithms, identify the exchange/cut/monotonic argument that makes the local choice safe.

### Boundary conditions

- empty/null input when the platform permits it;
- one element/node/state;
- duplicates and strict-versus-nonstrict comparisons;
- monotone/skewed/disconnected shape where relevant;
- overflow in sums, products, distances and path counts—prefer `long` when needed;
- value vs index vs node identity;
- online/streaming input versus offline preprocessing;
- repeated queries may justify preprocessing that a one-shot query does not.

### Complexity — derive it instead of memorizing it

**Expected O(1) operations; O(n) storage.**

Count how many times each element/node/edge can enter and leave the working structure, then multiply by the operation cost: array/index O(1), expected hash O(1), balanced tree/heap O(log n), full edge scan O(E).

### Memoization / repeated-work note

Memoization is useful only when the same logical state can be solved repeatedly. In many one-pass stack/list/tree problems there are no overlapping subproblems; the data structure itself stores the minimal reusable history. In graph, prefix-hash, and repeated-query problems, `visited`, `dist`, prefix maps, parent maps, or cached subtree metadata play the “do not recompute solved state” role.

> **MEMORY TRICK —** MAP LOCATES; ARRAY RANDOMIZES.

### Interview follow-ups and variations

1. Can auxiliary space be reduced, and what invariant replaces it?
2. What changes if equality/duplicate semantics change?
3. What changes if input arrives online?
4. What changes for many repeated queries?
5. Name two related problems that reuse this invariant with only one comparison or one state dimension changed.

[↑ Back to Index](#navigation-and-index)

---

## Final Revision

### What I must be able to do without notes

1. Explain the requirement before naming the algorithm.
2. State the invariant in one sentence.
3. Produce a dry run with visible state transitions.
4. Explain exactly why brute force repeats work.
5. Derive time/space complexity from operation counts.
6. Name edge cases that change strictness, ownership or reachability.
7. Explain whether memoization is useful and what the memo key would be.
8. Give at least two related problems using the same pattern.

### Source hierarchy used for continued enrichment

1. Take U Forward / Striver A2Z and official topic pages for course spine.
2. CP-Algorithms for graph/data-structure invariants, proofs and complexity.
3. Oracle/OpenJDK for Java collection behavior.
4. Canonical problem statements (e.g. LeetCode/GFG) for variants and test semantics.
5. Clearly labeled added study explanations for intuition, memory tricks and extra dry runs.

[↑ Navigation and Index](#navigation-and-index)
