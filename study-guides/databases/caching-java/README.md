# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–36 are promoted from confirmed Library source ranges through lines 62–1983. Section 36 adds four PostgreSQL protocol labs: exact SSLRequest bytes, incremental simple-query framing across arbitrary TCP chunks, handshake-latency versus pool-admission arithmetic, and separate server-identity/principal/tenant-authorization gates. The next bounded section is Section 37, source lines 1984–2038, bounded by the Section 38 heading at line 2039.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

