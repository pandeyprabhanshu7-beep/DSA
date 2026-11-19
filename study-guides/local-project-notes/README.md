# Local Project Study Notes

This separate folder receives selected, enriched technical material from the uploaded `untitled.zip`. The original archive is preserved separately; only useful, validated teaching batches are published here.

## Completed advanced-tree modules

- [Requirement-First Tree Selection Matrix — Markdown](advanced-trees/tree-selection-decision-matrix.md)
- [Requirement-First Tree Selection Matrix — HTML](advanced-trees/tree-selection-decision-matrix.html)
- [Tree selection and top-K contracts](advanced-trees/java/TreeDecisionContracts.java)
- [Fenwick Tree — Markdown](advanced-trees/fenwick-tree.md)
- [Fenwick Tree — HTML](advanced-trees/fenwick-tree.html)
- [Java implementation](advanced-trees/java/Fenwick.java)
- [Validation harness](advanced-trees/java/FenwickCheck.java)
- [Segment Tree with Lazy Propagation — Markdown](advanced-trees/segment-tree-lazy-propagation.md)
- [Segment Tree with Lazy Propagation — HTML](advanced-trees/segment-tree-lazy-propagation.html)
- [Lazy segment tree Java](advanced-trees/java/LazySegmentTree.java)
- [Lazy segment tree validation harness](advanced-trees/java/LazySegmentTreeCheck.java)
- [Trie / Prefix Tree — Markdown](advanced-trees/trie-prefix-tree.md)
- [Trie / Prefix Tree — HTML](advanced-trees/trie-prefix-tree.html)
- [Lowercase trie Java](advanced-trees/java/LowercaseTrie.java)
- [Lowercase trie validation harness](advanced-trees/java/LowercaseTrieCheck.java)
- [Binary Heap / Priority Queue — Markdown](advanced-trees/binary-heap-priority-queue.md)
- [Binary Heap / Priority Queue — HTML](advanced-trees/binary-heap-priority-queue.html)
- [Integer min-heap Java](advanced-trees/java/IntMinHeap.java)
- [Integer min-heap validation harness](advanced-trees/java/IntMinHeapCheck.java)
- [B+ Tree Database Index — Markdown](advanced-trees/b-plus-tree-database-index.md)
- [B+ Tree Database Index — HTML](advanced-trees/b-plus-tree-database-index.html)
- [B+ tree immutable read model](advanced-trees/java/BPlusTreeReadModel.java)
- [B+ tree validation harness](advanced-trees/java/BPlusTreeReadModelCheck.java)
- [B-Tree Insertion and Deletion — Markdown](advanced-trees/b-tree-insertion-deletion.md)
- [B-Tree Insertion and Deletion — HTML](advanced-trees/b-tree-insertion-deletion.html)
- [Mutable integer B-tree set](advanced-trees/java/BTreeIntSet.java)
- [B-tree validation harness](advanced-trees/java/BTreeIntSetCheck.java)
- [Red-Black Tree Insertion and Deletion — Markdown](advanced-trees/red-black-tree-insertion-deletion.md)
- [Red-Black Tree Insertion and Deletion — HTML](advanced-trees/red-black-tree-insertion-deletion.html)
- [Left-leaning red-black integer set](advanced-trees/java/RedBlackIntSet.java)
- [Red-black tree validation harness](advanced-trees/java/RedBlackIntSetCheck.java)
- [AVL Tree Insertion and Deletion — Markdown](advanced-trees/avl-tree-insertion-deletion.md)
- [AVL Tree Insertion and Deletion — HTML](advanced-trees/avl-tree-insertion-deletion.html)
- [AVL integer set](advanced-trees/java/AvlIntSet.java)
- [AVL validation harness](advanced-trees/java/AvlIntSetCheck.java)

Section 2, Binary Search Tree Core, was not duplicated here. Its distinct insertion/deletion material was merged into canonical [BST-6 and BST-7](../striver-dsa/trees-bst/markdown/Striver_Trees_BST_FINAL_Deep_Dive.md#bst-6-insert-into-a-bst), with a compiled [integer BST set](../striver-dsa/trees-bst/java/BstIntSet.java) and [oracle harness](../striver-dsa/trees-bst/java/BstIntSetCheck.java).

Section 1, Tree Mental Model & Taxonomy, was also merged rather than duplicated. Its distinct definitions, tree-family decision model and traversal-work timing now deepen canonical [BT-1](../striver-dsa/trees-bst/markdown/Striver_Trees_BST_FINAL_Deep_Dive.md#bt-1-introduction-to-trees) and [BT-3](../striver-dsa/trees-bst/markdown/Striver_Trees_BST_FINAL_Deep_Dive.md#bt-3-tree-traversal-one-dfs-frame-three-processing-moments), with [compiled Java](../striver-dsa/trees-bst/java/TreeTraversalMoments.java) and an [independent oracle harness](../striver-dsa/trees-bst/java/TreeTraversalMomentsCheck.java).

## Intake state

53 files: 28 Markdown candidates, 18 Mermaid sources, and 7 other files. Candidate means eligible for review, not certification that its content is correct. Older Graph/Stack/Tree/Linked List/Hashing editions must be compared with existing canonical repository guides before importing improvements. Do not create parallel replacement books here. Arrays, advanced trees, AI, frontend and system-design material can be published in bounded topic sections under this folder after comparison with other queued sources. One real Markdown guide is stored inside `.idea`; retain it as a candidate rather than excluding it by directory name alone.

See [intake manifest](INTAKE_MANIFEST.json) for source hashes, statuses and exact continuation. No build/IDE metadata was published. No source program from the archive was executed.

## Continue next

Advanced-trees Section 11, “Decision Matrix — Which Tree Should Trigger?”, is promoted from selected Markdown lines 1167–1187 and corroborating DOCX Pandoc lines 1559–1586. After fair-domain rotation is re-evaluated, the exact advanced-trees continuation is Section 12, “FAANG Problem Ladder — Easy / Medium / Hard,” beginning at selected source line 1188 and ending before Section 13. Sections 12–15 and the wider 170-placeholder backlog remain incomplete.

## Maintain readers

Run `python build_readers.py` (requires Python `markdown`) after editing local Markdown. Source Markdown is canonical; HTML is generated from it. Keep topic Java and diagram changes in the same validated commit.

Validation: the Java 17 compiler module ran all topic harnesses. The Fenwick harness passed 100,000 randomized point updates and 200,000 oracle queries; the lazy segment tree harness passed 100,000 randomized range updates and 200,000 oracle queries; the trie harness passed 50,000 randomized insert attempts, 100,000 exact/prefix queries and 10,000 autocomplete comparisons; the heap harness passed 100,000 mixed operations and 10,000 heapify-and-drain comparisons; the B+ tree read model passed 100,000 random point lookups and 50,000 random bounded range comparisons; the B-tree set passed 1,000 shuffled insert/delete permutations and 200,000 random mixed operations across minimum degrees 2–9; the red-black and AVL sets each passed deterministic/boundary cases, 1,000 shuffled insert/delete permutations and 200,000 seeded mixed operations against `TreeSet`, with repeated structural validation. Canonical BST insertion/deletion passed 456,423 assertions. Canonical traversal moments passed 376,980 assertions across explicit shapes and 10,000 seeded random trees compared with independent iterative oracles. Generated Markdown/HTML payloads, local links/anchors and SVG XML were checked. Browser visual QA remains pending.

Section 11 validation: Java 21 source-file launch passed 20 tree-selection groups and 20,000 seeded selector/top-K trials with 80,000 checks. The responsive reader recovered the exact canonical Markdown payload; all IDs, local fragments, relative links, six Mermaid sources, search/filter JavaScript and deterministic rebuilding passed. The shared reader builder could not run because its Python `markdown` dependency is absent, so the focused Pandoc builder was used and validated instead. Standalone `javac`, browser, assistive-technology and rendered Mermaid visual QA were unavailable. The live placeholder audit remains 170.
