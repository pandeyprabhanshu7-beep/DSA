# AI Enrichment Status

Last updated: 2026-10-23.

## AI platform engineering

State: **PROMOTED incrementally; Chapters 1–6 complete**.

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

Next bounded section: Chapter 7, **Evaluation: isolate failures by stage**, after the recorded domain rotation.

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

1. Rotate fairly after this AI-platform pass.
2. On the next AI-platform pass, process Chapter 7 only.
3. On a later AI-foundations pass, deepen hybrid retrieval/reranking evaluation and prompt-injection containment.
4. Keep project source code in the existing Spring Boot AI repositories; this repository holds learning documentation only.
