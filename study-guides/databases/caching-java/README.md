# Database internals, caching, and Java

- [Canonical Markdown](guide.md)
- [Responsive searchable HTML](guide.html)
- [Executable Java 17 scenarios](ConsistencyContracts.java)

This topic is **in progress**. Sections 1–30 are promoted from confirmed Library source ranges 62–133, 134–176, 177–230, 231–286, 287–356, 357–396, 397–493, 494–556, 557–588, 589–615, 616–637, 638–687, 688–718, 719–768, 769–774, 775–819, 820–906, 907–941, 942–974, 975–1090, 1091–1340, 1341–1364, 1365–1409, 1410–1427, 1428–1442, 1443–1456, 1457–1532, 1533–1595, 1596–1643, and 1644–1690. Section 30 traces one LSM key across a tombstone and two SSTables, proves snapshot/tombstone-safe compaction, accounts for Bloom-filter probes, and converts write amplification into compaction debt. The next bounded section is Section 31, “Design an index for an actual slow endpoint,” source lines 1691–1760, bounded by the Section 32 heading at line 1761.

Run `python3 build_reader.py`, `node validate_reader.mjs`, `javac ConsistencyContracts.java`, and `java -ea ConsistencyContracts` after changing this module. Markdown is canonical; the generated HTML embeds the exact Markdown bytes.

