# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–42 are promoted from confirmed Library source ranges through lines 62–2269. Section 42 adds four interview-explanation labs: a seven-slot contract lint, an outbox-relay acknowledgement trace, a cache-loader ownership trace, and a connection-pool checkout/cleanup trace. The next bounded section is Section 43, source lines 2270–2328, bounded by the Section 44 heading at line 2329.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

