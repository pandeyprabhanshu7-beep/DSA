# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Section 1, “ACID, BASE, and the misleading SQL/NoSQL split,” is promoted from the confirmed Library source range 62–133. The next bounded section is Section 2, “B+ Trees: why indexes are shallow,” beginning at source line 134.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.
