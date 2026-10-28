# Backend Enrichment Status

Last updated: 2026-10-28.

## Spring AI and Python APIs

State: **IN PROGRESS; Sections 1–18 promoted**.

Canonical pair:

- [Markdown](spring-ai-python/guide.md)
- [responsive searchable HTML](spring-ai-python/guide.html)

Source comparison:

- Selected `atlas_guides/spring_ai_python_api_interview_guide.md` from `ai_cicd_spring_python_guides_complete.zip`, bundle SHA-256 `c52c8295ae7ff7d08013e30a8d7097480719db78104a91b79e367a382465dd31`, member SHA-256 `af25c41d54a3ca18dfc60b6cf828515ab6afb7e39cf64c7df94fe4a44eb4a553`.
- Compared standalone `spring_ai_python_api_interview_guide.html`, SHA-256 `6289a19e0ce5514c25d96d44806d47063e726244b5d55d13422234d3e499f3a3`; it is byte-identical to the generated HTML inside the bundle, so it was not promoted as a duplicate source.
- Section 18 is exactly editable-source lines 1033–1117 and standalone-HTML lines 1164–1232, ending before Section 19 at respective lines 1119 and 1233.
- The ZIP passed integrity and path-traversal checks.

Completed sections:

1. **Choose the layer before choosing the library** (source lines 6–30) — requirements and ownership boundaries, four selection examples, request/release traces, minimal Java/Python contracts, current primary references, and explicit framework-escalation tests.
2. **Python library map: purpose, ownership and trade-offs** (source lines 31–56) — all 17 source projects retained as a responsibility map; four selection proofs for a classical baseline, pinned local model, bounded asynchronous API and replay-safe durable graph; paired Java/Python contracts; and refreshed primary references.
3. **Java library map and how it differs** (source lines 57–82) — all 12 source projects retained as an ownership map; four worked boundaries for a secured MCP tool, framework-port migration, DJL/direct-ONNX inference and a Java-to-Python service split; paired Java/Python contracts; and refreshed primary references.
4. **Start with the offline lab** (source lines 84–202) — authority-first retrieval, lexical/cosine traces, numerical guards, duplicate-safe RRF, citation membership and paired 31-scenario contracts.
5. **A model call at the wire level** (source lines 203–263) — trusted-origin and request contracts, raw HTTP trace, 20-second phase budget, replay-aware retry state, layered completion validation, capacity/cost arithmetic, safe telemetry and paired 30-scenario contracts.
6. **Complete Python RAG endpoint: trace every subcomponent** (source lines 264–427) — all-or-nothing startup snapshots, process-local admission arithmetic, total/phase deadlines, deterministic retrieval, calibrated abstention, citation membership/support gates, typed failures, exactly-once permit release, four diagrams and paired 32-scenario/10,000-trial contracts.
7. **Complete Spring AI RAG path: make the lifecycle observable** (source lines 428–620) — current release identity, minimal dependency/configuration boundary, atomic readiness publication, bounded admission and deadline propagation, deterministic retrieval, membership/support gates, typed failure semantics, graceful shutdown reasoning, four diagrams and paired 36-scenario/10,000-trial contracts.
8. **Replacing in-memory vectors with durable retrieval** (source lines 621–677) — generation-safe cutover, fixed vector identity, scoped parameterized retrieval, exact-versus-ANN recall gates, pool/deadline ownership, four diagrams and paired 34-scenario/10,000-trial contracts.
9. **Structured outputs: validate syntax, shape and business meaning** (source lines 678–714) — strict duplicate-aware parsing, exact schemas, server-owned provenance, request-specific service/evidence checks, bounded repair, four diagrams and paired 36-scenario/10,000-trial contracts.
10. **MCP implementations in Python and Java** (source lines 716–783) — local-callback-versus-MCP selection, exact tool schemas, synthetic/live provenance, revisioned resources, trusted scope/tenant checks, stdio/HTTP admission, nonauthoritative hints, four diagrams and paired 32-scenario/10,000-trial contracts.
11. **Streaming, concurrency and cancellation** (source lines 785–814) — SSE-versus-WebSocket selection, exact event framing, authenticated replay cursors, bounded queues and overflow, provisional/final terminal states, cooperative cancellation and side-effect reconciliation, four diagrams and paired 36-scenario/10,000-trial contracts.
12. **More Python AI: embeddings, reranking and classical ML** (source lines 816–867) — task and artifact identity, authorized bi-encoder retrieval plus bounded cross-encoder reranking, leakage-safe classical baselines, measured PyTorch serving, whole-pipeline Java/ONNX parity, four examples, four diagrams and paired 29-scenario/10,000-trial contracts.
13. **Provider adapters and portability** (source lines 869–880) — portable core versus owned extensions, conjunctive capability and workload-identity admission, evidence-led retry/failover, evaluated migration and rollback, four examples, four diagrams and paired 23-scenario/10,000-trial contracts.
14. **Observability and debugging by boundary** (source lines 882–915) — bounded-label metrics, protected content capture, causal queue/pool/retrieval/tool/model traces, security-first tenant-leak response, tool-registration evidence, Java/Python release-vector parity, four examples, four diagrams and paired 37-scenario/10,000-trial contracts.
15. **Testing pyramid for this repository** (source lines 917–930) — claim-to-layer boundaries, unit/adapter failure traces, real pgvector scope checks, versioned model evaluation, SLO-backed load and cluster gates, same-release evidence, four examples, four diagrams and paired 29-scenario/10,000-trial contracts.
16. **A four-week implementation path** (source lines 932–942) — weekly entry/exit gates, fixed evidence identities, safe retrieval comparison, read-only MCP discovery/schema/auth/timeout proof, same-release disposable deployment and failure drills, decision records, four examples, five diagrams and paired 31-scenario/10,000-trial contracts.
17. **Design an AI API as a durable business contract** (source lines 944–1031) — provider-neutral synchronous answers, verified authority, stable problem semantics, scoped canonical-hash idempotency, atomic job/outbox creation, duplicate-safe workers, tenant-concealed polling, four examples, four diagrams and paired 30-scenario/10,000-trial contracts.
18. **Streaming: transport success and answer completion are different** (source lines 1033–1117) — post-commit terminal-event semantics, JSON-safe framing, contiguous replay, idempotent action IDs, exactly-once cleanup, cancellation propagation, four examples, four diagrams and paired 30-scenario/10,000-trial contracts.

Validation:

- Java 17 source-file launch and Python 3 passed all eighteen contract pairs; Section 18 produced the identical `PARITY|FRAMING=SSE_JSON|TERMINAL=EXACTLY_ONCE|CLOSE=INCOMPLETE|REPLAY=ORDERED|ACTION=IDEMPOTENT|PERMIT=RELEASE_ONCE|COMPLETE=3281` trace, 30 scenario groups and 10,000 seeded trials per language.
- Deterministic reader generation passed exact 247190-byte Markdown recovery, SHA-256 `06f44c07883c99b0703f55384f06c5af90db1c2cc843e8a3448ddf430aa881d8`, 723 unique HTML IDs, 19 search entries, 495 fragments, 8 resolved relative links, 338 balanced fence markers, 76 Mermaid sources, JSON, JavaScript syntax, responsive/search controls and rebuild identity.
- A fresh literal-marker audit found 170 live Java placeholders across the five canonical DSA guide folders (51 graph, 27 hashing, 27 linked list, 18 stack/queue and 47 trees/BST); this backend section added none.
- Standalone `javac`, Maven/framework compilation, live pgvector/model/provider/MCP-host/k6/Kubernetes integration, browser/accessibility integration and rendered Mermaid QA were unavailable and are not represented as passes; Java 17 source-file launch was the available Java execution path.

Next action: rotate fairly to AI-platform Chapter 19, then Arrays Section 4 problem 18. On the next Spring/Python turn, process Section 19, **Authentication in Spring and Python: verified claims to query predicates**, beginning at editable source line 1119 / standalone HTML line 1233. Do not imply the 25-section source or wider collection is complete.
