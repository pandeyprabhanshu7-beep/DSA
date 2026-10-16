# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–2 are promoted from confirmed Library source ranges 62–133 and 134–176. Section 2 adds page/fan-out arithmetic, composite range access, InnoDB secondary-index amplification, and split mechanics without duplicating the repository's existing focused B+ tree algorithm module. The next bounded section is Section 3, “LSM trees: moving write cost into compaction,” beginning at source line 177; resolve its exact end before processing.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.
