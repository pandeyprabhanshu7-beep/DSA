# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–41 are promoted from confirmed Library source ranges through lines 62–2253. Section 41 adds four activity-history labs: capacity arithmetic, a tenant/user/time partition with tie-safe pagination, hot-user sharding with k-way merge costs, and explicit retention/replay/ordering/analytics policies. The next bounded section is Section 42, source lines 2256–2269, bounded by the Section 43 heading at line 2270.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

