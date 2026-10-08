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
| S1 Stack Using Array | ✅ | ✅ | Four examples, array invariant, Java, trace, proof and boundaries |
| S5 Stack Using Linked List | ✅ | ✅ | Four examples, pointer diagram, Java, trace, proof and boundaries |
| S2 Queue Using Circular Array | ✅ | ✅ | Four concrete examples, circular-state diagram, Java, wrap-around trace, invariant and proof |
| S3 Stack Using One Queue | ✅ | ✅ | Four examples, queue-rotation diagram, Java, detailed trace, proof and complexity |
| S4 Queue Using Two Stacks | ✅ | ✅ | Full lazy-transfer solution, four examples, detailed state trace, Java, proof, amortized analysis, boundaries and memory trick |
| S26 Sliding Window Maximum | ✅ | ✅ | Complete Java, deque proof, detailed trace and boundaries |
| G-32 Dijkstra — Priority Queue | ✅ | ✅ | Enriched Markdown synchronized into canonical responsive Graph HTML |
| Bellman–Ford — negative edges/cycles | ✅ | ✅ | Dedicated deep-dive pair under canonical Graph folders |
| G-46 Disjoint Set Union | ✅ | ✅ | Path compression, union-by-size, dry run, proof, amortized complexity and variations |
| G-55 Bridges / Critical Connections | ✅ | ✅ | Edge-ID-aware low-link deep dive |
| G-56 Articulation Points | ✅ | ✅ | Root/non-root rules and low-link reasoning |
| LL-28 Reverse Nodes in Groups of K | ✅ | ✅ | Complete Java, ten-node trace, identity and remainder semantics |
| BT-13 Maximum Depth / Height | ✅ | ✅ | DFS/BFS baselines, postorder proof and deep examples |
| BT-16 Maximum Path Sum | ✅ | ✅ | Gain/candidate distinction and all-negative handling |
| H-10 Subarray Sum Equals K | ✅ | ✅ | Prefix-frequency proof, Java and overflow handling |
| H-16 Minimum Window Substring | ✅ | ✅ | Four examples, brute force, frequency-window invariant, detailed ADOBECODEBANC trace, Java, proof and O(m+n) derivation |

## Completion audit — 2026-10-08 ET (updated 00:33)

The five canonical Markdown guides and their five responsive HTML reading editions exist in the repository.

Current explicit Java placeholder count:

| Guide | Cards with placeholder Java |
|---|---:|
| Graph | 51 |
| Stack & Queue | 22 |
| Linked List | 27 |
| Trees & BST | 51 |
| HashMap & Hashing | 26 |
| **Total** | **177** |

Previously removed placeholders:
- S4 Queue Using Two Stacks
- H-16 Minimum Window Substring

Prior enhancement removed two placeholders:
- S2 Queue Using Circular Array
- S3 Stack Using One Queue

This run removed two more placeholders:
- S1 Stack Using Array
- S5 Stack Using Linked List

Earlier S2/S3 verification: Java 17 compilation, explicit cases and 20,000 deterministic randomized paired operations against ArrayDeque.

S1/S5 verification: Java 17 javac/java passed explicit cases, empty/full/negative-capacity exceptions, duplicate and extreme values, and 20,000 deterministic randomized operations compared with ArrayDeque. Matching HTML payload equals canonical Markdown. Matching HTML payload verified equal to canonical Markdown.

All five canonical responsive HTML files have been resynchronized so their embedded Markdown payload now exactly matches the paired canonical Markdown file.

Remaining work:
1. Replace the **177** remaining Java placeholders with problem-specific code.
2. Replace generic examples with concrete 3–4 example sets.
3. Expand hard-card dry runs into explicit state tables.
4. Add correctness proofs and boundary-condition reasoning card by card.
5. Add/refresh focused visuals where they materially improve understanding.
6. Keep Markdown and HTML synchronized after every content change.

## Version policy

Only one canonical repository copy is kept for each main topic. Enrichment updates canonical paths instead of creating names such as V2, V3, FINAL-2 or ULTIMATE-NEW.

Focused deep-dive modules are allowed only when they add substantial subtopic material and are maintained as one Markdown/HTML pair rather than historical duplicates.

## Provenance

These repository editions preserve the latest retained topic structure and study methodology from the ChatGPT project. Some older generated attachment download handles expired before GitHub promotion, so repository editions are maintained from the canonical repository content and current project requirements rather than represented as byte-for-byte copies of inaccessible attachments.

## Enrichment checklist

- [x] meaningful topic directories
- [x] canonical latest-only version policy
- [x] Markdown + mobile HTML pair for all five guides
- [x] same-file navigation
- [x] question indexes
- [x] responsive graphics
- [x] HTML payload synchronized with Markdown for all five guides
- [x] S4 Queue Using Two Stacks deepened
- [x] H-16 Minimum Window Substring deepened
- [x] S2 Circular Queue and S3 One-Queue Stack deepened and Java-tested
- [x] S1 Array Stack and S5 Linked Stack deepened and Java-tested
- [ ] replace remaining 177 Java placeholders
- [ ] ongoing research-driven dry-run / example / boundary expansion
