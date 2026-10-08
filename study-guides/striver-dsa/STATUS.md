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
| S6 Queue Using Linked List | ✅ | ✅ | Four examples, head/tail invariant, Java, full-to-empty trace, proof and boundaries |
| S9 Infix to Postfix | ✅ | ✅ | Five verified examples, operator precedence/associativity, syntax checks, Java, trace, proof and Mermaid |
| S10 Prefix to Infix | ✅ | ✅ | Five verified examples, reverse scan, operand-order trace, Java, proof and Mermaid |
| S8 Min Stack | ✅ | ✅ | Four examples, saved-minimum pairs, Java, restoration trace, proof and encoded-state alternative |
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
| BST-6 Insert into a BST | ✅ | ✅ | Strict-set contract, four examples, path invariant, Java, trace, proof and height-sensitive complexity |
| BST-7 Delete a Node from BST | ✅ | ✅ | Three structural cases, four-operation trace, corrected Java, successor proof, boundaries and trade-offs |
| H-10 Subarray Sum Equals K | ✅ | ✅ | Prefix-frequency proof, Java and overflow handling |
| H-16 Minimum Window Substring | ✅ | ✅ | Four examples, brute force, frequency-window invariant, detailed ADOBECODEBANC trace, Java, proof and O(m+n) derivation |

## Completion audit — 2026-10-08 ET (updated 19:32)

The five canonical Markdown guides and their five responsive HTML reading editions exist in the repository.

Current explicit Java placeholder count:

| Guide | Cards with placeholder Java |
|---|---:|
| Graph | 51 |
| Stack & Queue | 18 |
| Linked List | 27 |
| Trees & BST | 49 |
| HashMap & Hashing | 27 |
| **Total** | **172** |

Previously removed placeholders:
- S4 Queue Using Two Stacks
- H-16 Minimum Window Substring

Prior enhancement removed two placeholders:
- S2 Queue Using Circular Array
- S3 Stack Using One Queue

Prior run removed two more placeholders:
- S1 Stack Using Array
- S5 Stack Using Linked List

Prior run removed two more placeholders:
- S6 Queue Using Linked List
- S8 Min Stack

This run removed two more placeholders:
- S9 Infix to Postfix
- S10 Prefix to Infix

S9/S10 verification: Java 17 javac compilation and execution, 11 explicit valid examples, 15 invalid-input cases, and 10,000 deterministic randomized expression pairs (20,000 random comparisons), totaling 20,026 assertions. The tested algorithms are the methods reproduced in the guides; HTML embedded Markdown equality verified.

Earlier S2/S3 verification: Java 17 compilation, explicit cases and 20,000 deterministic randomized paired operations against ArrayDeque.

S1/S5 verification: Java 17 javac/java passed explicit cases, empty/full/negative-capacity exceptions, duplicate and extreme values, and 20,000 deterministic randomized operations compared with ArrayDeque. Matching HTML payload equals canonical Markdown. Matching HTML payload verified equal to canonical Markdown.

S6/S8 verification: Java sources compiled with javac --release 17 and executed on OpenJDK 21; explicit cases, empty exceptions, duplicate and extreme values, plus 20,000 deterministic randomized operations per structure against ArrayDeque/reference minimum scan (40,000 total). Markdown/HTML embedded payload equality checked.

All five canonical responsive HTML files have been resynchronized so their embedded Markdown payload now exactly matches the paired canonical Markdown file.

Remaining work:
1. Replace the **172** remaining Java placeholders with problem-specific code.
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
- [ ] replace remaining 172 Java placeholders
- [ ] ongoing research-driven dry-run / example / boundary expansion


## Re-audit and branch promotion — 2026-10-08

Promoted the prepared S1, S2, S3, S5, S6, S8, S9 and S10 enrichments from the existing automation branches into the current mainline content. All eight Java implementations compile under Java 17. Six structures pass 10,000 seeded randomized operation rounds; expression conversions pass associativity and operand-order checks. The stack HTML embeds the exact current Markdown. Corrected MinStack push complexity to amortized O(1) for ArrayDeque growth.

The fresh scan of canonical source supersedes historical counts above:

| Guide | Explicit Java placeholders |
|---|---:|
| hashmap-hashing | 27 |
| stack-queue | 18 |
| trees-bst | 51 |
| graph-dsa | 51 |
| linked-list | 27 |
| **Total** | **174** |

This literal-marker count is a backlog indicator, not certification that all other cards are fully reviewed. No claim of complete enrichment is made.

## BST core enrichment — 2026-10-08 ET

BST-6 insertion and BST-7 deletion now have concrete contracts, four examples each, explicit invariants and traces, corrected Java, correctness proofs, height-sensitive complexity, boundary cases and interview variations. `BstIntSetCheck` passed 456,423 assertions, including 1,000 shuffled insert/delete permutations and 200,000 seeded mixed operations against `TreeSet` with repeated structural validation. The canonical HTML embeds the exact Markdown payload; this pass also restored its missing Markdown render step and repaired one stale top anchor.

Fresh literal-marker audit after these two cards: Graph 51, Stack & Queue 18, Linked List 27, Trees & BST 49, HashMap & Hashing 27; **total 172**.
