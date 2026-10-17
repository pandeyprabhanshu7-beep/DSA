# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–6 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, 231–286, 287–356, and 357–396. Section 6 adds topology/capacity arithmetic, Window TinyLFU admission intuition, Redis client-tracking disconnect and ordering repairs, Memcached placement/failure semantics, two-level invalidation traces, and tenant/locale/schema/permission-safe keys. The next bounded section is Section 7, “Four read/write strategies,” beginning at source line 397.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

