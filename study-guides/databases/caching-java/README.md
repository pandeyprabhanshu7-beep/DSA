# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–37 are promoted from confirmed Library source ranges through lines 62–2038. Section 37 adds four cache-component execution stories: typed first-probe envelopes and metrics, owner-safe miss coalescing, separate Redis/database failure boundaries, and lease-expiry behavior under immutable versus mutable data contracts. The next bounded section is Section 38, source lines 2039–2102, bounded by the Section 39 heading at line 2103.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

