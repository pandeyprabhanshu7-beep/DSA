# AI Enrichment Status

Last updated: 2026-10-22.

## AI platform engineering

State: **PROMOTED incrementally; Chapters 1–2 complete**.

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

Next bounded section: Chapter 3, **Embeddings and retrieval engineering**.

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
2. On the next AI-platform pass, process Chapter 2 only.
3. On a later AI-foundations pass, deepen hybrid retrieval/reranking evaluation and prompt-injection containment.
4. Keep project source code in the existing Spring Boot AI repositories; this repository holds learning documentation only.
