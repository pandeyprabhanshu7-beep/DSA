# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

The confirmed source is **fully processed**. Sections 1–44 cover source lines 62–2402. Section 44 closes the source with four executable rehearsal sessions for storage reasoning, concurrency/cache correctness, JVM-to-database timing, and end-to-end stress/failure/simplification. The wider intake and enrichment backlog remain active.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

