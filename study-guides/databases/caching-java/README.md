# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–31 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, 231–286, 287–356, 357–396, 397–493, 494–556, 557–588, 589–615, 616–637, 638–687, 688–718, 719–768, 769–774, 775–819, 820–906, 907–941, 942–974, 975–1090, 1091–1340, 1341–1364, 1365–1409, 1410–1427, 1428–1442, 1443–1456, 1457–1532, 1533–1595, 1596–1643, 1644–1690, and 1691–1760. Section 31 designs a measured PostgreSQL index for one tenant/status endpoint, contrasts order-preserving composite scans with bitmap-plus-sort, diagnoses plan evidence, and implements scope-bound keyset pagination with timestamp ties. The next bounded section is Section 32, “Isolation laboratory with two sessions,” source lines 1761–1816, bounded by the Section 33 heading at line 1817.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

