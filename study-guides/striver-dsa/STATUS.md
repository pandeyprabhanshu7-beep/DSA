# Striver DSA Promotion Status

| Guide | Markdown | Responsive HTML | Canonical path |
|---|---:|---:|---|
| Stack & Queue | ✅ | ✅ | `stack-queue/` |
| Graph DSA | ✅ | ✅ | `graph-dsa/` |
| Linked List | ✅ | ✅ | `linked-list/` |
| Trees & BST | ✅ | ✅ | `trees-bst/` |
| HashMap & Hashing | ✅ | ✅ | `hashmap-hashing/` |

## Deep-enrichment progress

| Topic/card | Deep Markdown | Matching HTML | Status |
|---|---:|---:|---|
| G-32 Dijkstra — Priority Queue | ✅ | ✅ | Enriched Markdown synchronized into canonical responsive Graph HTML |
| Bellman–Ford — negative edges/cycles | ✅ | ✅ | Dedicated deep-dive pair added under canonical Graph guide folders |
| G-46 Disjoint Set Union | ✅ | ✅ | Deepened with path compression, union-by-size, dry run, proof, amortized complexity, and variations |

| G-55 Bridges / Critical Connections | ✅ | ✅ | Canonical HTML synchronized with edge-ID-aware low-link deep dive |
| G-56 Articulation Points | ✅ | ✅ | Canonical HTML synchronized with root and non-root rules |
| S26 Sliding Window Maximum | ✅ | ✅ | Complete Java, deque proof, detailed trace and boundaries |
| LL-28 Reverse Nodes in Groups of K | ✅ | ✅ | Complete Java, ten-node trace, identity and remainder semantics |
| BT-13 Maximum Depth / Height | ✅ | ✅ | Four examples, Java DFS/BFS and path-enumeration baseline, postorder proof, Mermaid diagram; 1,500 randomized trees + 10,000-node skew BFS validated |\n| BT-16 Maximum Path Sum | ✅ | ✅ | Complete Java, gain/candidate distinction and all-negative handling |
| H-10 Subarray Sum Equals K | ✅ | ✅ | Complete Java, prefix-frequency proof and overflow handling |

## Completion audit — 2026-10-08

The first table means files exist in both formats; it does **not** mean every card has a complete deep dive. An audit of the current canonical Markdown found these explicit Java placeholders still remaining after this pass:

| Guide | Cards with placeholder Java |
|---|---:|
| Graph | 51 |
| Stack & Queue | 27 |
| Linked List | 27 |
| Trees & BST | 51 |
| HashMap & Hashing | 27 |
| **Total** | **183** |

All six HTML reading editions embed the exact current paired Markdown. BT-13 was enriched and synchronized on 2026-10-08; its pre-existing card already had a non-placeholder height method, so the **183** explicit placeholder count is unchanged. S4 Queue Using Two Stacks still has a placeholder because its write was blocked; do not mark it complete. Remaining work: replace the 183 placeholders with problem-specific implementations, precise examples, actual dry runs and correctness arguments. Generic template prose remains in many cards and needs review too. The four newly enriched Java implementations compile with the Java 17 compiler module and pass 1,000 seeded randomized cases per algorithm against independent brute-force/reference checks, including list node identity checks and extreme long targets. HTML/Markdown payload equality is verified; browser rendering was not visually tested in this pass.

## Version policy

Only one canonical repository copy is kept for a topic. Enrichment updates canonical paths instead of creating names such as V2, V3, FINAL-2 or ULTIMATE-NEW. Focused deep-dive modules are allowed when they add substantial subtopic material and are maintained as one Markdown/HTML pair rather than historical duplicates.

## Provenance

These repository editions preserve the latest retained topic structure and study methodology from the ChatGPT project. Some older generated attachment download handles expired before GitHub promotion, so the repository editions are regenerated from the retained canonical structure and project requirements rather than being represented as byte-for-byte copies of inaccessible attachments.

## Enrichment checklist

- [x] meaningful topic directories
- [x] Markdown + mobile HTML pair
- [x] same-file navigation
- [x] question indexes
- [x] pattern / memory / complexity notes
- [x] responsive graphics
- [x] synchronize G-32 Dijkstra changes into main Graph HTML
- [ ] ongoing deeper research and per-card expansion
