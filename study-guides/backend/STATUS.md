# Backend Enrichment Status

Last updated: 2026-10-23.

## Spring AI and Python APIs

State: **IN PROGRESS; Sections 1–5 promoted**.

Canonical pair:

- [Markdown](spring-ai-python/guide.md)
- [responsive searchable HTML](spring-ai-python/guide.html)

Source comparison:

- Selected `atlas_guides/spring_ai_python_api_interview_guide.md` from `ai_cicd_spring_python_guides_complete.zip`, bundle SHA-256 `c52c8295ae7ff7d08013e30a8d7097480719db78104a91b79e367a382465dd31`, member SHA-256 `af25c41d54a3ca18dfc60b6cf828515ab6afb7e39cf64c7df94fe4a44eb4a553`.
- Compared standalone `spring_ai_python_api_interview_guide.html`, SHA-256 `6289a19e0ce5514c25d96d44806d47063e726244b5d55d13422234d3e499f3a3`; it is byte-identical to the generated HTML inside the bundle, so it was not promoted as a duplicate source.
- Section 5 is exactly editable-source lines 203–263 and standalone-HTML lines 388–434, ending before Section 6 at respective lines 264 and 435.
- The ZIP passed integrity and path-traversal checks.

Completed sections:

1. **Choose the layer before choosing the library** (source lines 6–30) — requirements and ownership boundaries, four selection examples, request/release traces, minimal Java/Python contracts, current primary references, and explicit framework-escalation tests.
2. **Python library map: purpose, ownership and trade-offs** (source lines 31–56) — all 17 source projects retained as a responsibility map; four selection proofs for a classical baseline, pinned local model, bounded asynchronous API and replay-safe durable graph; paired Java/Python contracts; and refreshed primary references.
3. **Java library map and how it differs** (source lines 57–82) — all 12 source projects retained as an ownership map; four worked boundaries for a secured MCP tool, framework-port migration, DJL/direct-ONNX inference and a Java-to-Python service split; paired Java/Python contracts; and refreshed primary references.
4. **Start with the offline lab** (source lines 84–202) — authority-first retrieval, lexical/cosine traces, numerical guards, duplicate-safe RRF, citation membership and paired 31-scenario contracts.
5. **A model call at the wire level** (source lines 203–263) — trusted-origin and request contracts, raw HTTP trace, 20-second phase budget, replay-aware retry state, layered completion validation, capacity/cost arithmetic, safe telemetry and paired 30-scenario contracts.

Validation:

- Java 17 source-file launch and Python 3 passed every paired contract; Section 5 produced the identical `PARITY|RETRY_1000|ACCEPTED|INCOMPLETE|400|2600` trace and 30 scenario groups per language.
- Deterministic reader generation, exact embedded-Markdown recovery, hashes, unique IDs, search entries, balanced fences, Mermaid sources, local links/fragments, JSON, reader JavaScript and rebuild identity were validated after the content change.
- The live literal DSA Java-marker audit remains 170.
- Standalone `javac`, framework/native dependency compilation, live model/provider calls, browser/accessibility integration, pool/load tests and rendered Mermaid QA were unavailable and are not represented as passes.

Next action: rotate fairly to Arrays Section 4 problem 5, **Move Zeroes**, at Markdown line 635 / DOCX extracted line 419. On the next Spring/Python turn, process Section 6, **Embedding calls and response validation**, beginning at source line 264. Do not imply the 25-section source is complete.
