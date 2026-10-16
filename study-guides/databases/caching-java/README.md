# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–4 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, and 231–286. Section 4 adds row/column byte accounting, vector-batch aggregation, MergeTree part and sparse-granule mechanics, sort-key and insert-batching decisions, plus an idempotent gap-detecting CDC projection. The next bounded section is Section 5 beginning at source line 287; resolve its exact heading and end before processing.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

