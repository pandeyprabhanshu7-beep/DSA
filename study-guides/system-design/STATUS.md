# System Design Enrichment Status

Last updated: 2026-10-23.

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

## Interview blueprints

State: **PROMOTED; all five named blueprints complete, source supplement remains**.

Canonical pair:

- [Markdown](blueprints/guide.md)
- [mobile-friendly searchable HTML](blueprints/guide.html)

Blueprint 1, **Global video streaming**, promotes source lines 5–253 from `top_5_system_design_interview_blueprints.md` revision 1, SHA-256 `10c3b8139474ea47f35e1fed6cb169b2728db48070f3264e1d23dfa776e9bf03`. It separates control/media/event planes and deepens the source with four worked traces: CDN capacity/locality, expiring authorized delivery, buffer-aware ABR, and sequence-ordered progress that permits backward seek.

Blueprint 2, **Real-time ride hailing**, promotes source lines 254–459 from the same confirmed revision. It defines freshness and latency contracts, separates ephemeral geo projection from authoritative assignment, and deepens four traces: expanding-cell candidate search, out-of-order locations, a single-winner offer race, and snapshot-plus-version reconnect with idempotent payment handoff.

Blueprint 3, **Distributed financial transaction ledger**, promotes source lines 460–680 from the same revision. It preserves double-entry accounting and deepens four traces: balanced posting, stable-key lost-response replay, serialized concurrent debits, and an external-rail timeout that remains `UNKNOWN` until authoritative reconciliation.

Blueprint 4, **Large-scale polite web crawler**, promotes source lines 681–902. It makes origin-scoped scheduling and SSRF boundaries explicit and deepens four traces: conservative canonicalization, priority under politeness, robots/overload eligibility, and safe conditional recrawl through redirects.

Blueprint 5, **Distributed unique ID generator**, promotes source lines 903–1125. It defines the uniqueness/order/availability contract and deepens four examples: 41/10/12 bit arithmetic and decode, same-tick saturation plus rollback, leased worker reuse with fencing, and a sequence/range/Snowflake/UUIDv7 decision comparison.

Validation for this promotion:

- Java 17 source-file launch passed the capacity, cache-miss, ABR, progress-ordering and invalid-input scenario groups in `VideoPlaybackContracts.java`.
- Java 17 source-file launch passed 22 ride-hailing scenario groups covering cell estimates, freshness/order, single-winner assignment, lost-response retry, reconnect ordering, payment identity and invalid inputs.
- Java 17 source-file launch passed 18 ledger scenario groups covering balanced/mixed-currency posting, idempotency replay and mismatch, insufficient funds, stable external identity, reconciliation and inbox deduplication.
- Java 17 source-file launch passed 25 crawler scenario groups covering URL identity, admission deduplication, robots matching, origin-scoped capacity/delay, `Retry-After`, public-address boundaries and recrawl delay.
- Java 17 source-file launch passed 24 distributed-ID scenario groups covering 41/10/12 packing and decoding, 4,096 same-tick values, overflow and rollback rejection, expired/stale lease fencing, disjoint ranges, UUIDv7 fields and the signed-long boundary.
- The deterministic reader embeds the exact 65,291-byte Markdown payload at SHA-256 `c3eb5c5c5ea0ef4cef6b8e5cf5ee82013aeb122d98a89e1f8243bd6642e0f708`; 99 unique IDs, six search entries, all 29 local fragments, 72 balanced fence markers, 20 Mermaid sources and deterministic rebuild identity passed.
- Ride-hailing primary references were refreshed against current H3, Uber Engineering, Redis, PostgreSQL, Apache Kafka, RFC 6455 and Stripe documentation on 2026-10-22.
- Ledger primary references were refreshed against current Stripe, PostgreSQL and AWS transactional-outbox documentation on 2026-10-23.
- Crawler primary references were refreshed against current RFC 9309/3986/9110/9111, Google crawling, OWASP SSRF and Java SE 17 documentation on 2026-10-23.
- Distributed-ID primary references were refreshed against the X Snowflake announcement, RFC 9562, PostgreSQL 18, etcd 3.6 and Java SE 17 documentation on 2026-10-23.
- The live literal DSA Java-marker audit is 170 (Graph 51, Stack/Queue 18, Linked List 27, Trees/BST 47, Hashing 27).
- Standalone `javac`, browser/assistive-technology integration, and rendered Mermaid visual QA were unavailable and are not represented as passes.

Next action: rotate domains fairly to Spring AI/Python Section 5 beginning at source line 203. On a later system-design pass, review the source cross-question/practice supplement at lines 1127–1264 without duplicating the richer canonical guide. The wider intake and enrichment backlog remain incomplete.
