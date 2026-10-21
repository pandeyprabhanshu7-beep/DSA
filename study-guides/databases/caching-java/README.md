# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–40 are promoted from confirmed Library source ranges through lines 62–2209. Section 40 adds four reliable-notification labs: explicit lifecycle boundaries, a transactional inbox claim plus in-app insert, external email unknown-outcome reconciliation, and bounded retry/backlog/freshness policy. The next bounded section is Section 41, source lines 2210–2253, bounded by the Phase 9 heading at line 2254.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

