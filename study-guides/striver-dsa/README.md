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

Graph also contains a focused [Bellman–Ford Markdown deep dive](graph-dsa/markdown/bellman-ford-deep-dive.md) with a matching [HTML reader](graph-dsa/html/bellman-ford-deep-dive.html).

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

See [STATUS.md](STATUS.md) for promotion and deep-enrichment progress.
