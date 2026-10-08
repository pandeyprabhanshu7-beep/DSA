# Local Project Study Notes

This separate folder receives selected, enriched technical material from the uploaded `untitled.zip`. The original archive is preserved separately; only useful, validated teaching batches are published here.

## First completed batch

- [Fenwick Tree — Markdown](advanced-trees/fenwick-tree.md)
- [Fenwick Tree — HTML](advanced-trees/fenwick-tree.html)
- [Java implementation](advanced-trees/java/Fenwick.java)
- [Validation harness](advanced-trees/java/FenwickCheck.java)

## Intake state

53 files: 28 Markdown candidates, 18 Mermaid sources, and 7 other files. Candidate means eligible for review, not certification that its content is correct. Older Graph/Stack/Tree/Linked List/Hashing editions must be compared with existing canonical repository guides before importing improvements. Do not create parallel replacement books here. Arrays, advanced trees, AI, frontend and system-design material can be published in bounded topic sections under this folder after comparison with other queued sources. One real Markdown guide is stored inside `.idea`; retain it as a candidate rather than excluding it by directory name alone.

See [intake manifest](INTAKE_MANIFEST.json) for source hashes, statuses and exact continuation. No build/IDE metadata was published. No source program from the archive was executed.

## Continue next

Read advanced-trees section 9: Segment Tree + Lazy Propagation. Estimated next-batch budget: approximately 12,000 tokens, a conservative heuristic rather than a reading of remaining account quota. Stop at a durable checkpoint if resources are insufficient; later hourly runs retry.

## Maintain readers

Run `python build_readers.py` (requires Python `markdown`) after editing local Markdown. Source Markdown is canonical; HTML is generated from it. Keep topic Java and diagram changes in the same validated commit.

Validation: Java 17 compilation, 100,000 randomized updates, 200,000 oracle queries, local link/anchor checks and SVG XML validation passed. Browser visual QA remains pending.
