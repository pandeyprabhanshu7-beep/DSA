# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–20 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, 231–286, 287–356, 357–396, 397–493, 494–556, 557–588, 589–615, 616–637, 638–687, 688–718, 719–768, 769–774, 775–819, 820–906, 907–941, 942–974, and 975–1090. Section 20 turns the immutable CMS revision design into an explicit production contract: tenant-scoped same-slot keys, authorization-first reads, bounded Redis-to-primary degradation, strict post-publish negative-cache handling, least-privilege schema ownership, and joined timeout budgets through four executable Java models. The next bounded section is Section 21, “Complete Java cache-aside component,” source lines 1091–1340, bounded by the Section 22 heading.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

