# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–38 are promoted from confirmed Library source ranges through lines 62–2102. Section 38 adds four multi-tenant CMS design labs: layered traffic arithmetic, an optimistic pointer-plus-outbox publish transaction, separate public/editor read policies, and independent CDN/Redis collapse budgets. The next bounded section is Section 39, source lines 2103–2169, bounded by the Section 40 heading at line 2170.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

