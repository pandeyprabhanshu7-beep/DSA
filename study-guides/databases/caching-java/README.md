# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–5 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, 231–286, and 287–356. Section 5 adds an exact anomaly matrix, statement-versus-transaction snapshot traces, PostgreSQL and InnoDB differences, write-skew and lost-update repairs, JDBC transaction boundaries, bounded whole-transaction retry, and unknown-outcome handling. The next bounded section is Section 6, “Local, distributed, and two-level caches,” beginning at source line 357.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

