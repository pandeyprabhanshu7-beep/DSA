# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–34 are promoted from confirmed Library source ranges through lines 62–1909. Section 34 adds four cache-concurrency labs: owner-checked lease cleanup, generation-fenced value publication, generation-fenced negative publication, and fleet-wide concurrency/rate admission. The next bounded section is Section 35, “Connection pooling with real queue arithmetic,” source lines 1910–1945, bounded by the Section 36 heading at line 1946.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

