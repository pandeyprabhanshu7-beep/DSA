# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–35 are promoted from confirmed Library source ranges through lines 62–1945. Section 35 adds four connection-pool queue labs: borrow-to-close residency arithmetic, hold-time-driven queue stability, separate Hikari/PgBouncer waiting rooms, and explicit configuration/deadline/unknown-commit contracts. The next bounded section is Section 36, “Protocol walkthrough with actual framing examples,” source lines 1946–1983, bounded by the Section 37 heading at line 1984.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

