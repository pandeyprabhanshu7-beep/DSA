# Backend Enrichment Status

Last updated: 2026-10-22.

## Spring AI and Python APIs

State: **IN PROGRESS; Sections 1–2 promoted**.

Canonical pair:

- [Markdown](spring-ai-python/guide.md)
- [responsive searchable HTML](spring-ai-python/guide.html)

Source comparison:

- Selected `atlas_guides/spring_ai_python_api_interview_guide.md` from `ai_cicd_spring_python_guides_complete.zip`, bundle SHA-256 `c52c8295ae7ff7d08013e30a8d7097480719db78104a91b79e367a382465dd31`, member SHA-256 `af25c41d54a3ca18dfc60b6cf828515ab6afb7e39cf64c7df94fe4a44eb4a553`.
- Compared standalone `spring_ai_python_api_interview_guide.html`, SHA-256 `6289a19e0ce5514c25d96d44806d47063e726244b5d55d13422234d3e499f3a3`; it is byte-identical to the generated HTML inside the bundle, so it was not promoted as a duplicate source.
- The ZIP passed integrity and path-traversal checks.

Completed section:

1. **Choose the layer before choosing the library** (source lines 6–30) — requirements and ownership boundaries, four selection examples, request/release traces, minimal Java/Python contracts, current primary references, and explicit framework-escalation tests.
2. **Python library map: purpose, ownership and trade-offs** (source lines 31–56) — all 17 source projects retained as a responsibility map; four selection proofs for a classical baseline, pinned local model, bounded asynchronous API and replay-safe durable graph; paired Java/Python contracts; and refreshed primary references.

Validation:

- Java 17 source-file launch passed 9 layer-selection plus 16 library-selection scenario groups; Python 3 passed the corresponding 9 plus 15 groups.
- Deterministic reader generation passed exact 23,716-byte Markdown recovery, SHA-256 `384a45323cdee287d87170595abdcf022cf1706c65e98eaf16ca38029fea22e2`, 61 unique IDs, 3 search entries, 28 balanced fence markers, 9 Mermaid sources, internal fragments, local links, JSON parsing, reader JavaScript parsing and idempotent rebuild identity.
- The live literal DSA Java-marker audit remains 170.
- Framework dependency compilation, live model/provider calls, browser/accessibility integration, and rendered Mermaid QA were unavailable and are not represented as passes.

Next action: rotate domains fairly to Arrays Section 2, source lines 183–229. On the next Spring/Python turn, process Section 3, **Java library map and how it differs**, beginning at source line 57. Do not imply the 25-section source is complete.
