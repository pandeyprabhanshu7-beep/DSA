# System Design Enrichment Status

Last updated: 2026-10-08.

## Fundamentals

State: **PROMOTED; deep enrichment remains in progress**.

Canonical pair:

- [Markdown](fundamentals/guide.md)
- [mobile-friendly searchable HTML](fundamentals/guide.html)

Source comparison:

- Selected `system_design_fundamentals_deep_research.md`, Library revision 1, SHA-256 `93446361f0c19cddbe2ab76bd9c32158a6dc540d10a60f2dbeffe827233efcd6` (1,785 lines before repository additions).
- Compared `system_design_fundamentals.md`, SHA-256 `8b2752df0853d73f900dd9e58b71e7593a28d7bd0fef4847ffa5ed4ad250d657` (525 lines).
- The selected source has materially broader fundamentals coverage: requirements, DNS/load balancing, caching, data stores, consistency, protocols, messaging, coordination, reliability, observability, security, provider mapping, database internals, Java protocol choices, and practice drills.
- The shorter source's distinct video, ride-hailing, ledger, crawler, and ID-generator case studies are not discarded; they belong with the separately queued system-design blueprints source.

Completed deep sections:

1. **Requirements and capacity estimation** — four calculated examples, a storage dry run, dimensional and boundary invariants, Little's Law, output-cost analysis, and a Java 17 estimator.
2. **Cache correctness** — four policies, cache-aside invariants, stale-resurrection trace, version fencing, local single-flight Java, correctness boundaries, and cost derivation.

Validation recorded for this promotion:

- Source selection used actual content from both resolved files, not metadata alone.
- Primary references refreshed against official Google SRE, AWS, and RFC documentation on 2026-10-08.
- Both embedded Java 17 programs compiled through source-file launch and executed successfully: four capacity assertions plus zero/negative boundaries, and cache hit, stale-resurrection, and 10,000 seeded randomized monotonic-version rounds.
- Deterministic Pandoc rebuild passed; the HTML Base64 payload exactly recovers Markdown SHA-256 `c91326928dc2be42c0a55da2e0a91e4953395c96d1d2be258302db434b56fce8`.
- All 382 HTML IDs are unique; internal fragments, relative files/assets, 81 search-index targets, and all 30 Mermaid source blocks passed static validation.
- The live literal DSA Java-marker audit remains 174 (Graph 51, Stack/Queue 18, Linked List 27, Trees/BST 51, Hashing 27).
- Browser visual QA was not executed because the isolated browser could not reach the loopback preview (`ERR_CONNECTION_REFUSED`); this is not represented as a pass.

Next action:

1. Resume fundamentals only for a coherent unfinished correction; otherwise rotate to the unpromoted `system-design-blueprints` source.
2. On the next fundamentals pass, add a full retry/idempotency packet trace and an adversarial timeout-after-commit example.
3. Continue to keep blueprints as a distinct topic, not a `v2` or duplicate fundamentals edition.
