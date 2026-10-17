# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–7 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, 231–286, 287–356, 357–396, and 397–493. Section 7 adds cache-aside stale-resurrection fencing, typed read-through outcomes and local load coalescing, committed-but-not-cached write-through repair, and durable bounded write-behind replay with idempotency, ordering, and backpressure. The next bounded section is Section 8, “Eviction internals: LRU, LFU, FIFO and 2Q,” source lines 494–556.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

