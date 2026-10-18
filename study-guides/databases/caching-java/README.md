# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–21 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, 231–286, 287–356, 357–396, 397–493, 494–556, 557–588, 589–615, 616–637, 638–687, 688–718, 719–768, 769–774, 775–819, 820–906, 907–941, 942–974, 975–1090, and 1091–1340. Section 21 completes the blocking Spring cache-aside design: typed value/negative envelopes, a second lookup after lease acquisition, owner-checked Lua publication and cleanup, three bounded waiter polls, a shared SQL bulkhead, monotonic Redis cooldown, narrow exception boundaries, metrics and release tests. Four framework-independent Java models exercise the core states. The next bounded section is Section 22, “If you cache mutable rows instead,” source lines 1341–1364, bounded by the Phase 6 / Section 23 heading.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

