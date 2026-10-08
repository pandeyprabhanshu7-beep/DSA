# AI Enrichment Status

Last updated: 2026-10-08.

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

1. Rotate to the unpromoted CI/CD, Jenkins, TeamCity and cloud source.
2. On the next AI pass, deepen hybrid retrieval/reranking evaluation and prompt-injection containment.
3. Keep project source code in the existing Spring Boot AI repositories; this repository holds learning documentation only.
