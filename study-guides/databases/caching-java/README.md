# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–39 are promoted from confirmed Library source ranges through lines 62–2169. Section 39 adds four flash-sale inventory labs: hot-SKU admission arithmetic, an idempotent conditional reservation transaction, payment-versus-expiry state transitions, and bounded stock-token conservation. The next bounded section is Section 40, source lines 2170–2209, bounded by the Section 41 heading at line 2210.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

