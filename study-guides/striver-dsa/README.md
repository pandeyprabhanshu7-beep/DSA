# Striver DSA Study Library

Canonical interview-study guides live here. Each topic keeps one latest Markdown source and one mobile-friendly HTML reader; historical V2/V3/FINAL-copy files are intentionally avoided.

## Guides

| Topic | Markdown | Mobile HTML | Focus |
|---|---|---|---|
| Stack & Queue | [Guide](stack-queue/markdown/Striver_Stack_Queue_FINAL_Deep_Dive.md) | [Reader](stack-queue/html/index.html) | stack/queue patterns, monotonic structures, implementation |
| Linked List | [Guide](linked-list/markdown/Striver_LinkedList_FINAL_Deep_Dive.md) | [Reader](linked-list/html/index.html) | pointer patterns, reversal, cycle, merge/reorder |
| Trees & BST | [Guide](trees-bst/markdown/Striver_Trees_BST_FINAL_Deep_Dive.md) | [Reader](trees-bst/html/index.html) | traversal reasoning, recursion, BST and tree interview patterns |
| HashMap & Hashing | [Guide](hashmap-hashing/markdown/HashMap_Hashing_FINAL_Deep_Dive.md) | [Reader](hashmap-hashing/html/index.html) | frequency, lookup, prefix-state and hashing patterns |
| Graph DSA | [Guide](graph-dsa/markdown/Striver_Graph_DSA_FINAL_Deep_Dive.md) | [Reader](graph-dsa/html/index.html) | traversal, topo, shortest paths, MST/DSU, SCC and low-link |
| Arrays | [Guide](arrays/guide.md) | [Reader](arrays/guide.html) | array contracts, decision gates, Java toolkit, prefix-state compression, local occupancy, direction flags, increasing-subsequence thresholds, compaction, sorted two pointers, windows, prefix balance, offline difference events, randomized rank selection, dense order-preserving coordinates, answer-space/partition search, intervals, matrix traversal, merge counting, circular Kadane, monotonic structures, greedy frontiers, XOR cancellation, rotated pivots and virtual matrix search |

Graph also contains a focused [Bellman–Ford Markdown deep dive](graph-dsa/markdown/bellman-ford-deep-dive.md) with a matching [HTML reader](graph-dsa/html/bellman-ford-deep-dive.html), plus executable contracts for [G-1 graph modeling](graph-dsa/java/GraphModelContracts.java), [G-2 matrix/list representation parity](graph-dsa/java/GraphRepresentationContracts.java), [G-3 typed Java adjacency lists](graph-dsa/java/GraphAdjacencyListContracts.java), [G-4 connected components](graph-dsa/java/GraphComponentsContracts.java), [G-5 breadth-first search](graph-dsa/java/GraphBfsContracts.java), [G-6 depth-first search](graph-dsa/java/GraphDfsContracts.java), [G-7 Number of Provinces](graph-dsa/java/GraphProvincesContracts.java), [G-8 eight-direction islands](graph-dsa/java/GraphEightDirectionIslandsContracts.java), [G-9 four-direction Flood Fill](graph-dsa/java/GraphFloodFillContracts.java), [G-10 multi-source Rotten Oranges](graph-dsa/java/GraphRottingOrangesContracts.java), [G-11 undirected-cycle BFS](graph-dsa/java/GraphUndirectedCycleBfsContracts.java), and [G-12 undirected-cycle DFS](graph-dsa/java/GraphUndirectedCycleDfsContracts.java).

The Trees & BST guide includes compiled companions for [plain BST insertion/deletion](trees-bst/java/BstIntSet.java) and its [oracle harness](trees-bst/java/BstIntSetCheck.java), plus [DFS processing moments](trees-bst/java/TreeTraversalMoments.java) and their [independent iterative-oracle checks](trees-bst/java/TreeTraversalMomentsCheck.java).

## How to study

For each question, move in this order: **requirement → examples → pattern → brute force → repeated work → invariant → optimized solution → dry run → Java → correctness → boundary cases → complexity → variations**.

Do not memorize code before you can state the invariant. For graphs, first write **NODE, EDGE, DIRECTION, COST, GOAL**. For pointer problems, draw ownership before changing links. For hashing, define exactly what the key and stored value mean.

## Navigation and mobile use

The HTML readers provide responsive layouts, searchable section navigation, table scrolling, code-copy controls, dark/light mode, and same-file anchors. Markdown remains the canonical editable study source; when it is enriched, the matching HTML payload must be synchronized in the same promotion pass.

## Canonical-version policy

- Never replace a newer repository file with an older chat/project artifact.
- Keep one canonical latest guide per topic.
- Do not create historical duplicates such as `V2`, `V3`, `FINAL-2`, or `ULTIMATE`.
- A focused subtopic deep dive is acceptable only when it adds substantial material and its Markdown/HTML pair is maintained intentionally.
- Preserve source attribution boundaries: external references validate algorithms and claims; added examples, memory tricks, proofs, diagrams, and interview notes are labeled as study-guide material.

The selected Arrays source pair is exhausted: source Sections 1–3, all 47 Section 4 problems, all five Section 15 techniques, Sections 16–20, and the final Markdown-only externally cross-checked supplement are promoted. The wider collection and placeholder backlog remain incomplete.

See [STATUS.md](STATUS.md) for promotion and deep-enrichment progress.


