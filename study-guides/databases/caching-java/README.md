# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–22 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, 231–286, 287–356, 357–396, 397–493, 494–556, 557–588, 589–615, 616–637, 638–687, 688–718, 719–768, 769–774, 775–819, 820–906, 907–941, 942–974, 975–1090, 1091–1340, and 1341–1364. Section 22 defines the mutable-row safety boundary: optimistic row versions, same-transaction durable outbox events, recoverable relay claims, idempotent monotonic generation updates, and a publication fence that rejects stale in-flight readers. Four framework-independent Java models exercise the core states. The next bounded section is Section 23, “Fault-injection and integration test matrix,” source lines 1365–1409, bounded by the Section 24 heading.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

