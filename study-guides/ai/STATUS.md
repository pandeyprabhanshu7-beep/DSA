# AI Enrichment Status

Last updated: 2026-10-27.

## AI platform engineering

State: **PROMOTED incrementally; Chapters 1–18 complete**.

Canonical pair:

- [Markdown](platform/guide.md)
- [mobile-friendly searchable HTML](platform/guide.html)

Source comparison:

- Selected the expanded 21-chapter `ai_platform_master_guide.html`, SHA-256 `c0d23c3a9b64d88e8ad52720402b162c124283958aa7ad0afe228f9a4bc2b24a`.
- Used bundle member `02_rag_mcp.md`, SHA-256 `45896b5283ca92e712f7cb3db075b0f076edfaeff34d9127cd867520ff5dfa05`, as the editable companion source.
- Compared `ai_platform_engineering_illustrated.html`, SHA-256 `006aaba7bfaf978849a5fe1df6a8e9f611c0d99a806ef6c3a05c3eeb2101beb9`; it is byte-identical to the HTML inside the bundle and is not a competing canonical edition.
- Processed only Chapter 1, bounded by the Chapter 2 heading. It now includes four authority-selection examples, evidence and mutation traces, invariants, failure review, current primary references, and a Java 17 routing contract.
- Compared Chapter 2 in the master and bundled editable companion; their token, embedding, and generation material agrees, so no duplicate guide was created.
- Deepened Chapter 2 with four model-mechanics examples, token and migration traces, four diagrams, current primary references, and an executable Java 17 contract for context admission, embedding compatibility, guarded cosine similarity, decoding metadata, and semantic output acceptance.
- Compared Chapter 3 in selected master lines 96–138 and editable companion lines 65–127; both specify versioned ingestion, bounded parsing, chunking, staged publication, and deletion, so no duplicate guide was created.
- Deepened Chapter 3 with four ingestion examples, retry/limit/window/publication traces, five diagrams, current primary references, and an executable Java 17 contract for idempotency, monotonic revisions, resource limits, deterministic chunk windows, coherent generations, tombstones, and ACL epochs.
- Compared Chapter 4 in selected master lines 139–168 and editable companion lines 129–178; both cover lexical/vector retrieval, capacity, rank fusion, reranking, and authorization-before-disclosure, so no duplicate guide was created.
- Deepened Chapter 4 with four retrieval examples, exact identifier/ANN/RRF/reranker traces, five diagrams, current primary references, and an executable Java 17 contract for BM25, tenant-scoped exact vectors, checked capacity, deterministic RRF, and authorization-first bounded reranking with timeout fallback.
- Compared Chapter 5 in selected master lines 169–204 and editable companion lines 180–222; both specify the pgvector schema pattern, fixed example dimension, parameterized cosine query, tenant/generation scope, ACL caveat, and datastore-selection criteria, so no duplicate guide was created.
- Deepened Chapter 5 with four PostgreSQL/RAG data-design examples, retry/publication/query/migration traces, five diagrams, current primary references, and an executable Java 17 state-machine contract for immutable chunk identity, generation-wide embedding specifications, guarded publication, authorization-first exact search, bounded top-k, and monotonic tombstones.
- Compared Chapter 6 in selected master lines 205–226 and editable companion lines 224–256; both specify host-built evidence envelopes, untrusted retrieved content, citation membership and support checks, calibrated abstention, conflict handling, and separation of advice from action, so no duplicate guide was created.
- Deepened Chapter 6 with four context/citation/abstention examples, envelope/citation/decision/action traces, five diagrams, current primary references, and an executable Java 17 contract for authorization-first context construction, exact citation support, server-rendered provenance, typed abstention/conflict, and a separate action boundary, plus 10,000 seeded evidence-set trials.
- Compared Chapter 7 in selected master lines 227–248 and editable companion lines 257–290; both specify versioned evaluation data, retrieval and answer metrics, judge calibration, paired release comparison, critical-slice protection and reproducibility identities, so no duplicate guide was created.
- Deepened Chapter 7 with four evaluation examples, stage-isolation and release-gate traces, five diagrams, current primary references, and an executable Java 17 contract for duplicate-aware retrieval metrics, typed stage outcomes, hard-slice gates and exact paired sign-test evidence, plus 10,000 seeded ranking checks.
- Compared Chapter 8 in selected master lines 249–259 and editable companion lines 291–309; both cover cache identity/invalidation, a ten-second latency-budget example, bounded retry/cancellation controls, and complete cost accounting, so no duplicate guide was created.
- Deepened Chapter 8 with four cache/deadline/failure/cost examples, four diagrams, cache and retry traces, current primary references, and an executable Java 17 contract for behavior-complete keys, ACL/source invalidation, deadline arithmetic, replay-safe retry admission, bounded full jitter, and unit-safe cost totals, plus 10,000 seeded jitter checks.
- Compared Chapter 9 in selected master lines 260–377 and editable companion lines 311–414; both cover host/client/server/model boundaries, tools/resources/prompts, explicitly separate modern and legacy protocol eras, current HTTP request metadata, and stdio versus Streamable HTTP, so no duplicate guide was created.
- Deepened Chapter 9 with four ownership/discovery/cancellation/transport examples, four diagrams, current primary references, and an executable Java 17 contract for modern metadata, capability admission, model/host/server authority composition, separately gated legacy lifecycle, request correlation, transport cancellation, and terminal-state ownership, plus 10,000 seeded trials.
- Compared Chapter 10 in selected master lines 388–398 and editable companion lines 415–433; both cover token/resource authorization, object ownership, prompt-injection containment, least tool exposure, explicit approval, idempotency, uncertain outcomes, version-fenced compensation, bounded execution and redacted audit fields, so no duplicate guide was created.
- Deepened Chapter 10 with four authorization/injection/approval/idempotency examples, four diagrams, current primary references, and an executable Java 17 contract for exact issuer/audience/scope and resource checks, fixed destinations, approval fingerprints, version fences, durable operation reconciliation and bounded execution, plus 10,000 seeded authorization trials.
- Reconciled the queued `Observability and operations` label against both confirmed sources: selected master lines 399–411 and editable companion lines 435–449 identify Chapter 11 as `Putting RAG and MCP together without unnecessary agents`, ending before Chapter 12. No duplicate or off-sequence topic was promoted.
- Deepened Chapter 11 with four deterministic/degradation/checkpoint/bounded-agent examples, four diagrams, an exact budgeted failure trace, current primary references, and an executable Java 17 contract for deterministic-first plan selection, typed failures, explicit transitions, fenced side-effect state, safe correlated events and 10,000 seeded planner-oracle trials.
- Compared Chapter 12 in selected master lines 483–487 and editable companion lines 453–457; both cover managed AWS/Azure/Google retrieval services, an independent pgvector/Sentence Transformers/vLLM stack, retained team ownership, regional constraints and a reproducible comparison, so no duplicate guide was created.
- Deepened Chapter 12 with four managed/independent/bake-off/migration examples, four diagrams, ownership, capacity, cost and migration traces, current primary references, and an executable Java 17 contract for hard capability qualification, team/provider ownership, outcome-based cost, checked capacity and reversible migration, plus 10,000 seeded capacity-oracle trials.
- Compared Chapter 13 in selected master lines 417–423 and editable companion lines 459–471; both expose the same five exercises and senior-answer pattern, and the companion ends at that boundary, so no duplicate guide was created.
- Deepened Chapter 13 with four integrated interview cases covering all five source exercises, five diagrams, diagnosis/migration/revocation/mutation/retrieval traces, current primary references, and an executable Java 17 contract for first-failed-stage diagnosis, generation-fenced embeddings, authorization-epoch cache admission, reconciliation-first mutation retry and hard retrieval-slice gates, plus 10,000 seeded diagnosis-oracle trials.
- Compared Chapter 14 in selected master HTML lines 425–512 (Pandoc conversion lines 553–616). The companion Markdown and byte-identical illustrated HTML end after Chapter 13, so the expanded master is the only confirmed substantive Chapter 14 source and no duplicate guide was created.
- Deepened Chapter 14 with four control-plane/fallback/tenant/revocation examples, four diagrams, snapshot/route/scope/cache traces, current primary references, and an executable Java 17 contract for bounded signed snapshots, emergency revocation, policy-compatible fallback, verified tenant selection and release-fenced cache identity, plus 10,000 seeded route-admission trials.
- Compared Chapter 15 in selected master HTML lines 513–539 (Pandoc conversion lines 617–661). The companion Markdown and byte-identical illustrated HTML end after Chapter 13, so the expanded master is the only confirmed substantive Chapter 15 source and no duplicate guide was created.
- Deepened Chapter 15 with four prefill/decode/KV/batching/prefix-cache examples, four diagrams, latency and scheduler traces, current primary references, and an executable Java 17 contract for checked KV arithmetic, bounded admission, exact prefix identity, tenant-aware scheduling and phase-specific SLO signals, plus 10,000 seeded reservation/admission trials.
- Compared Chapter 16 in selected master HTML lines 540–563 (Pandoc conversion lines 662–705). The companion Markdown and byte-identical illustrated HTML still end after Chapter 13, so the expanded master is the only confirmed substantive Chapter 16 source and no duplicate guide was created.
- Deepened Chapter 16 with four embedding/retrieval/answer/prefix-cache examples, four diagrams, authorization and stale-publication traces, current primary references, and an executable Java 17 contract for behavior-complete cache keys, tenant/ACL/generation scope, risk-gated semantic reuse, generation-fenced publication and exact prefix compatibility, plus 10,000 seeded dual-oracle trials.
- Compared Chapter 17 in selected master HTML lines 596–623 (Pandoc conversion lines 706–741). The companion Markdown and byte-identical illustrated HTML still end after Chapter 13, so the expanded master is the only confirmed substantive Chapter 17 source and no duplicate guide was created.
- Deepened Chapter 17 with four concurrency/context/summary/deletion examples, four diagrams, short-transaction and deletion-graph traces, current Spring AI and PostgreSQL primary references, and an executable Java 17 contract for append-only ordering, tenant/owner scope, expected-version finalization, bounded whole-event context, summary lineage/authority denial and derived-store deletion state, plus 10,000 seeded oracle trials.
- Compared Chapter 18 in selected master HTML lines 624–652 (Pandoc conversion lines 742–777). The companion Markdown and byte-identical illustrated HTML still end after Chapter 13, so the expanded master is the only confirmed substantive Chapter 18 source and no duplicate guide was created.
- Deepened Chapter 18 with four identity/parser/publication/authority examples, four diagrams, replay, rejection, cutover and stale-ACL traces, current primary references, and an executable Java 17 contract for full transformation identity, bounded parsing, resumable stages, generation-CAS publication, tombstones, evidence-time ACL checks and versioned embedding spaces, plus 10,000 seeded parser-policy trials.

Next bounded AI-platform section: Chapter 19 after fair domain rotation. The immediate cross-domain continuation is Arrays problem 17, followed by Spring AI/Python Section 18.

Validation for Chapter 18: Java 17 source-file launch passed all eighteen AI-platform contracts; `RecoverableIngestionContracts` passed 28 scenario groups and 10,000 seeded trials. Two deterministic Pandoc builds produced identical readers. The platform reader recovered the exact 245,654-byte Markdown payload at SHA-256 `1c772a1cd96f0ac64d0cddd93eb8447b625d3984c5b28deb63128aee3ff77861`; 597 unique HTML IDs, 73 search entries, 423 fragment links, 18 resolved relative links, 238 balanced fence markers, 81 Mermaid sources, search JSON, inline/module JavaScript, responsive controls and `git diff --check` passed. Standalone `javac`, browser, assistive-technology and rendered Mermaid visual QA were unavailable. The live literal-marker audit remains 170 placeholders (51 graph, 27 hashing, 27 linked list, 18 stack/queue and 47 trees/BST); this section added none.

## AI foundations, RAG and CAG

State: **PROMOTED; deep enrichment remains in progress**.

Canonical pair:

- [Markdown](foundations-rag-cag/guide.md)
- [mobile-friendly searchable HTML](foundations-rag-cag/guide.html)

Source comparison:

- Selected `AI_RAG_CAG_Deep_Learning_Mermaid_Developer_Guide.md`, SHA-256 `d302fcc91dfc510ca514962d8fec4229c4ac418c227ca9aafd080552fbc99e08` (1,990 lines before repository additions).
- Compared `AI_RAG_CAG_Deep_Learning_Developer_Guide.docx`, SHA-256 `eb7e33869e7bd119f9b52d12ef7e9a7c301ba67065e87abf822fbd1396e51e80` (1,444 Pandoc-converted lines).
- The selected source covers 98.69% of the Word vocabulary and all its headings, with six additional headings, clickable navigation and Mermaid diagrams.
- The Word archive passed ZIP integrity and traversal checks. Two non-redundant figures—embedding space and attention heatmap—were retained under `assets/`; redundant flow diagrams were not duplicated.

Completed deep sections:

1. **RAG minimal implementation** — four requests, an authorization-aware dry run, explicit retrieval/context/citation invariants, compilable Java 17, correctness and derived output costs, adversarial tests, and 10,000 seeded tenant-isolation trials.
2. **CAG cache identity and invalidation** — four cache decisions, a version-change packet trace, length-prefixed SHA-256 keys, Java 17 validation, correctness boundaries, and 10,000 seeded invalidation trials.

Validation is recorded in the enrichment queue and the promotion commit.

Next action:

1. Rotate fairly to Arrays problem 16 after this AI-platform pass, then Spring AI/Python Section 17.
2. On the next AI-platform pass, process Chapter 18 only.
3. On a later AI-foundations pass, deepen hybrid retrieval/reranking evaluation and prompt-injection containment.
4. Keep project source code in the existing Spring Boot AI repositories; this repository holds learning documentation only.


