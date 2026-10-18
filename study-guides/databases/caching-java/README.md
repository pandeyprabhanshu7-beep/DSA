# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–8 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, 231–286, 287–356, 357–396, 397–493, and 494–556. Section 8 adds exact LRU and FIFO traces, LFU tie-breaking and aging, full-2Q ghost history and scan resistance, implementation caveats, and four executable Java models. The next bounded section is Section 9, “Cache stampede: coordinate fills, bound fallback,” source lines 557–588.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

