# Bellman–Ford Deep Dive — Negative Edges, Relaxation, and Negative Cycles

[← Graph guide](../markdown/Striver_Graph_DSA_FINAL_Deep_Dive.md) · [Examples](#examples) · [Dry run](#dry-run) · [Java](#java) · [Correctness](#correctness) · [Complexity](#complexity) · [Top](#bellmanford-deep-dive--negative-edges-relaxation-and-negative-cycles)

> **Source boundary:** The topic is part of the Striver/Take U Forward graph sequence. The expanded proofs, examples, memory tricks, Java engineering notes, and interview variants below are additional study-guide material. Verified against Take U Forward's Bellman–Ford tutorial and CP-Algorithms' Bellman–Ford reference.

## Problem in one line

Given a weighted directed graph and a source, compute shortest distances even when some edges are negative; also detect a **reachable negative-weight cycle**, because then finite shortest paths for affected vertices do not exist.

### Pattern recognition

Think **Bellman–Ford** when you see **single-source shortest path + negative edges**, or when the interviewer asks you to **detect a reachable negative cycle**. If every edge is nonnegative, Dijkstra is normally faster. If the graph is a DAG, topological relaxation is better. For all-pairs shortest paths on a modest dense graph, think Floyd–Warshall.

> **Memory trick:** **Relax every edge V−1 times; one more improvement means a reachable negative cycle.**

## Why Dijkstra can fail

Dijkstra relies on a greedy finalization idea: once the cheapest unsettled vertex is chosen, nonnegative future edges cannot create a cheaper route back to it. A negative edge destroys that guarantee. Bellman–Ford avoids finalizing vertices greedily. It repeatedly propagates improvements across *all* edges.

## Examples

| Case | Edges | Source | Result | Lesson |
|---|---|---:|---|---|
| 1 | 0→1(4), 0→2(5), 1→2(-2) | 0 | [0,4,2] | negative edge is valid |
| 2 | 0→1(1), 1→2(-1), 2→1(-1) | 0 | negative cycle | distances can decrease forever |
| 3 | 0→1(2), 2→3(-5), 3→2(1) | 0 | [0,2,∞,∞] | unreachable negative cycle does not affect source SSSP |
| 4 | one vertex, no edges | 0 | [0] | V−1 = 0 passes is enough |

## State and invariant

`dist[v]` is the best source→v distance discovered so far. Never relax from an unreachable vertex: `dist[u] != INF` must hold before evaluating `dist[u] + w`.

After the **i-th full pass**, every shortest path using at most **i edges** has been propagated. A simple shortest path in a graph with V vertices uses at most V−1 edges; otherwise a vertex repeats and a cycle can be removed unless a reachable negative cycle makes the optimum unbounded.

## Brute-force intuition and repeated work

A naive approach could enumerate source-to-vertex walks, but cycles create infinitely many walks and the number of simple paths can explode. Bellman–Ford compresses all relevant history into one value per vertex: the best known distance. Each edge relaxation asks whether extending that reusable summary improves another summary.

This is not memoized recursion. The reusable state is the `dist[]` array itself.

## Relaxation

For edge `u → v` with weight `w`:

```text
if u is reachable and dist[u] + w < dist[v]
    dist[v] = dist[u] + w
```

The order of edges can make improvements propagate faster within a pass, but correctness does **not** depend on a lucky order because we allow up to V−1 complete passes.

## Dry run

Graph:

```text
0 --4--> 1 --(-2)--> 2 --3--> 3
 \                         ^
  \--5----------------> 2 |
```

Edges are intentionally scanned in the order `(2,3), (1,2), (0,2), (0,1)` to show why repeated passes matter.

Initial: `[0, ∞, ∞, ∞]`

| Pass | Edge | Action | dist |
|---:|---|---|---|
| 1 | 2→3(3) | 2 unreachable | [0,∞,∞,∞] |
| 1 | 1→2(-2) | 1 unreachable | [0,∞,∞,∞] |
| 1 | 0→2(5) | set d2=5 | [0,∞,5,∞] |
| 1 | 0→1(4) | set d1=4 | [0,4,5,∞] |
| 2 | 2→3(3) | set d3=8 | [0,4,5,8] |
| 2 | 1→2(-2) | improve d2=2 | [0,4,2,8] |
| 3 | 2→3(3) | improve d3=5 | [0,4,2,5] |

No further improvement occurs. Final distances are `[0,4,2,5]`.

The important observation is not “three loops.” It is **information crossing one more edge of a shortest path after each guaranteed phase**.

## Java

```java
import java.util.*;

public final class BellmanFord {
    public record Edge(int from, int to, long weight) {}
    public record Result(long[] dist, boolean hasReachableNegativeCycle) {}

    private static final long INF = Long.MAX_VALUE / 4;

    public static Result shortestPaths(int n, List<Edge> edges, int source) {
        long[] dist = new long[n];
        Arrays.fill(dist, INF);
        dist[source] = 0L;

        // V-1 passes are sufficient when no reachable negative cycle exists.
        for (int pass = 1; pass <= n - 1; pass++) {
            boolean changed = false;

            for (Edge e : edges) {
                if (dist[e.from()] == INF) continue;

                long candidate = dist[e.from()] + e.weight();
                if (candidate < dist[e.to()]) {
                    dist[e.to()] = candidate;
                    changed = true;
                }
            }

            // Optimization: fixed point reached.
            if (!changed) break;
        }

        // One extra scan detects a reachable negative cycle.
        for (Edge e : edges) {
            if (dist[e.from()] == INF) continue;
            if (dist[e.from()] + e.weight() < dist[e.to()]) {
                return new Result(dist, true);
            }
        }

        return new Result(dist, false);
    }
}
```

### Key lines

```java
if (dist[e.from()] == INF) continue;
```

Without the reachability guard, an artificial “infinity + negative weight” calculation can masquerade as a real path.

```java
if (dist[e.from()] + e.weight() < dist[e.to()])
```

This is the relaxation invariant. Use `long` when constraints make `int` overflow plausible.

## Correctness

**Initialization:** source has distance 0; every other vertex is unknown/infinite.

**Maintenance:** assume every shortest path with at most i−1 edges has been represented after pass i−1. During pass i, the final edge `u→v` of every shortest path with i edges is examined. The prefix ending at u is already represented, so relaxing `u→v` can establish the correct value for v.

**Termination:** any finite shortest path can be chosen simple when no reachable negative cycle is involved, hence it contains at most V−1 edges. Therefore V−1 passes suffice.

**Negative-cycle test:** if an edge can still improve after those passes, a reachable walk keeps becoming cheaper beyond the simple-path bound; this implies a reachable negative cycle participates in the improvement.

## Complexity

There are at most V−1 full passes, each scanning E edges.

- Time: **O(VE)**.
- Extra algorithmic space: **O(V)** for distances, excluding the input edge list.
- Early stopping can reduce actual work when a pass performs no update, but worst-case complexity remains O(VE).

Compare: binary-heap Dijkstra is typically O((V+E) log V) but requires nonnegative weights.

## Boundary conditions

- **V=1:** zero relaxation passes.
- **Unreachable vertices:** remain INF.
- **Unreachable negative cycle:** should not trigger source-specific detection; the INF guard is essential.
- **Self-loop with negative weight:** immediately forms a reachable negative cycle if its vertex is reachable.
- **Parallel edges:** relaxation naturally keeps the best result.
- **Undirected negative edge:** representing it as two directed edges creates an immediate negative 2-edge cycle.
- **Overflow:** choose a safe INF and `long`; production code may additionally use checked/saturating arithmetic for extreme constraints.
- **Need any negative cycle anywhere:** initialize all distances to 0 (conceptually a super-source with zero edges to every vertex), rather than using a single source.

## Path reconstruction

Maintain `parent[v] = u` whenever relaxation improves v. To reconstruct a normal shortest path, walk parents backward from the target. To extract a negative cycle after an improvement on the V-th pass, follow parents V times to enter the cycle, then continue until the vertex repeats.

## Interview comparison map

```text
Unweighted / equal weights ───────────────> BFS
Weights 0 or 1 ──────────────────────────> 0-1 BFS
DAG, possibly negative edges ────────────> Topological relaxation
General graph, nonnegative weights ──────> Dijkstra
General graph, negative edges ───────────> Bellman–Ford
All pairs, modest/dense graph ───────────> Floyd–Warshall
```

## Common mistakes

1. Running Dijkstra despite negative edges.
2. Forgetting the unreachable-source guard.
3. Running only V−2 passes.
4. Calling any negative cycle in the graph a failure when it is unreachable from the chosen source.
5. Detecting a negative cycle but still presenting affected distances as finite shortest paths.
6. Using `int` when path sums can overflow.
7. Assuming early stopping changes the worst-case O(VE) bound.

## Variations to practice

- reconstruct one shortest path;
- return the actual reachable negative cycle;
- mark every vertex whose shortest distance is effectively −∞ because it is reachable from a reachable negative cycle;
- solve “Cheapest Flights Within K Stops” by limiting relaxation phases;
- compare Bellman–Ford with SPFA and explain why SPFA still has O(VE) worst case.

[↑ Top](#bellmanford-deep-dive--negative-edges-relaxation-and-negative-cycles)
