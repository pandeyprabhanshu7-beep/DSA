# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–9 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, 231–286, 287–356, 357–396, 397–493, 494–556, and 557–588. Section 9 adds stampede arithmetic, per-key local single-flight, owner-checked Redis fill leases, finite waiter/fallback decisions, origin concurrency limits, and four executable Java models. The next bounded section is Section 10, “Cache penetration: absent keys and Bloom filters,” source lines 589–615, bounded by the Section 11 heading.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

