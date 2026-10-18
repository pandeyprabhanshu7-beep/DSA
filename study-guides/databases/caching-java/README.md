# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–11 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, 231–286, 287–356, 357–396, 397–493, 494–556, 557–588, 589–615, and 616–637. Section 11 adds strict-maximum versus symmetric TTL jitter, capacity-gated cold-start planning, soft/hard freshness decisions, retry-amplification arithmetic, explicit outage fallback, and four executable Java models. The next bounded section is Section 12, “Architecture: pool objects are not network proxies,” source lines 638–687, bounded by the Section 13 heading.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

