# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–10 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, 231–286, 287–356, 357–396, 397–493, 494–556, 557–588, and 589–615. Section 10 adds typed tenant-scoped negative caching, transparent Bloom bit mechanics, capacity/error-rate sizing, completeness-aware fail-open behavior, safe rebuild/deletion guidance, and four executable Java models. The next bounded section is Section 11, “Cache avalanche: TTL jitter, cold starts and outage protection,” source lines 616–637, bounded by the Section 12 heading.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

