# Local Project Study Notes

This separate folder receives selected, enriched technical material from the uploaded `untitled.zip`. The original archive is preserved separately; only useful, validated teaching batches are published here.

## Completed advanced-tree modules

- [Fenwick Tree — Markdown](advanced-trees/fenwick-tree.md)
- [Fenwick Tree — HTML](advanced-trees/fenwick-tree.html)
- [Java implementation](advanced-trees/java/Fenwick.java)
- [Validation harness](advanced-trees/java/FenwickCheck.java)
- [Segment Tree with Lazy Propagation — Markdown](advanced-trees/segment-tree-lazy-propagation.md)
- [Segment Tree with Lazy Propagation — HTML](advanced-trees/segment-tree-lazy-propagation.html)
- [Lazy segment tree Java](advanced-trees/java/LazySegmentTree.java)
- [Lazy segment tree validation harness](advanced-trees/java/LazySegmentTreeCheck.java)

## Intake state

53 files: 28 Markdown candidates, 18 Mermaid sources, and 7 other files. Candidate means eligible for review, not certification that its content is correct. Older Graph/Stack/Tree/Linked List/Hashing editions must be compared with existing canonical repository guides before importing improvements. Do not create parallel replacement books here. Arrays, advanced trees, AI, frontend and system-design material can be published in bounded topic sections under this folder after comparison with other queued sources. One real Markdown guide is stored inside `.idea`; retain it as a candidate rather than excluding it by directory name alone.

See [intake manifest](INTAKE_MANIFEST.json) for source hashes, statuses and exact continuation. No build/IDE metadata was published. No source program from the archive was executed.

## Continue next

Read advanced-trees section 8: Trie / Prefix Tree, then compare it against any richer canonical trie coverage before publishing one focused module. Estimated next-batch budget: approximately 11,000 tokens. The estimate uses the section's 1,728 characters / 4 (about 432 source tokens because a tokenizer is unavailable), plus heuristic reserves of 1,500 for comparison/research, 6,500 for teaching/code/diagram/HTML, and 2,500 for validation/checkpoint work. It is not account-quota telemetry. Stop at a durable checkpoint if resources are insufficient; later hourly runs retry.

## Maintain readers

Run `python build_readers.py` (requires Python `markdown`) after editing local Markdown. Source Markdown is canonical; HTML is generated from it. Keep topic Java and diagram changes in the same validated commit.

Validation: the Java 17 compiler module ran both topic harnesses. The Fenwick harness passed 100,000 randomized point updates and 200,000 oracle queries; the lazy segment tree harness passed deterministic/boundary cases plus 100,000 randomized range updates and 200,000 oracle queries. Generated Markdown/HTML payload hashes, local links/anchors and SVG XML were checked. Browser visual QA remains pending.
