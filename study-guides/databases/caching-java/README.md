# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–3 are promoted from confirmed Library source ranges 62–133, 134–176, and 177–230. Section 3 adds WAL/memtable recovery, snapshot-aware version selection, tombstone-retention safety, compaction trade-offs, amplification accounting, disk headroom, and write-stall signals. The next bounded section is Section 4, “Row-oriented versus column-oriented storage,” beginning at source line 231; resolve its exact end before processing.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

