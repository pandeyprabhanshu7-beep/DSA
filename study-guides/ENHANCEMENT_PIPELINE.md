# Continuous Computer Science Guide Enrichment

Last inventory: 2026-10-07 (America/New_York).

## Canonical target
Repository: `pandeyprabhanshu7-beep/DSA`, branch `main`.
Start each run by rereading `study-guides/striver-dsa/README.md` and `STATUS.md`. Existing canonical Markdown and matching HTML are the destination. Never upload an older chat attachment over a richer, newer GitHub edition merely because of file modification time.

## Verified canonical DSA folders
- `study-guides/striver-dsa/graph-dsa/`
- `study-guides/striver-dsa/stack-queue/`
- `study-guides/striver-dsa/linked-list/`
- `study-guides/striver-dsa/trees-bst/`
- `study-guides/striver-dsa/hashmap-hashing/`

Each existing topic has `markdown/` and `html/`; keep them synchronized on every update. Focused substantial subtopic guides are permitted only when they are not revision duplicates.

## Sources to reconcile (NOT repository publications)
The user's earlier ChatGPT work and file library include the following *candidate sources*. Compare section coverage and quality before merging. Do not infer newer = better.

| Candidate source | Subject | Action |
|---|---|---|
| `DSA_Graph_FAANG_Mermaid_Question_Workbook.md` | Graph algorithms | Compare with canonical graph guide; cherry-pick better examples/figures |
| `DSA_Tree_FAANG_Mermaid_Question_Workbook.md` | Trees | Compare with canonical tree guide |
| `Take_U_Forward_Graph_Complete_Deep_Guide_v2.md` | Graph | Historical edition; use only missing valuable material if accessible |
| `Take_U_Forward_Stack_Queue_Complete_Deep_Guide_v2.md` | Stack and queue | Historical edition; use only missing valuable material if accessible |
| `AI_RAG_CAG_Deep_Learning_Mermaid_Developer_Guide.md` | AI / RAG / CAG | Candidate for one canonical AI topic |
| `system_design_fundamentals_deep_research.md` | System design | Candidate for one canonical system-design topic |
| `top_5_system_design_interview_blueprints.md` | System-design case studies | Keep as distinct topic only if materially distinct |
| `ai_platform_master_guide.html` | AI platform | Inspect underlying material and compare with AI source before choosing canonical MD |
| `cicd_aws_azure_teamcity_jenkins_guide.html` | CI/CD and cloud | Inspect underlying Markdown/source if available |
| `spring_ai_python_api_interview_guide.html` | Spring AI and Python | Inspect underlying Markdown/source if available |

The names above record historical work, **not** evidence these source files are all locally or programmatically accessible to every future run. Never claim a file was imported unless actually read and committed.

## Hourly enhancement algorithm
1. Inspect main branch and latest `STATUS.md`. Reassess current placeholders and prior commits.
2. Choose only 1–2 unfinished question cards or substantial topic sections per run. Highest priority: incomplete Java code, wrong/problematic examples, missing proofs, missing dry runs.
3. Identify the canonical target by conceptual topic, not a version suffix. Merge improvements **in place**, preserving existing correct material.
4. Provide clear requirements, 3–4 distinct correct examples with expected outputs, brute force, pattern-selection clues, optimized invariant, table-based dry run, compilable Java (where applicable), key line annotations, correctness argument, complexity derivation, edge cases, common errors, and interview variations.
5. Use diagrams where they help: Mermaid graphs, tree states, algorithmic data flows, pointer changes, or architectural components. Avoid decorative generic flowcharts and broken image paths; include alt text or descriptive captions.
6. Validate Java against sample and adversarial tests when a runner is available. Otherwise flag test status accurately; do not publish claims of executed tests.
7. Rebuild/synchronize matching HTML from the final Markdown; verify the source payload matches, check links and image references.
8. Update `STATUS.md` and relevant README links. Push a small coherent commit only when GitHub write access and validation succeed.
9. Report exact edited file paths, GitHub commit URL or reason no push occurred, 1–2 completed sections, and remaining backlog.

## Deduplication and safety
- No `v2`, `v3`, `final2`, `updated`, or `ultimate` alternate copies.
- Never overwrite a newer canonical guide wholesale with an older chat-generated version.
- Preserve source attribution and do not invent citations.
- Do not silently delete old sources; archive/retire only after verifying equivalent or better content is retained.
- If any content is inaccessible or a GitHub write fails, record the blocker and leave existing canonical material intact.

## Initial repository audit
The canonical status inspected on 2026-10-07 identifies 181 Java-placeholder cards: Graph 51, Trees & BST 51, Linked List 27, Stack & Queue 26, and HashMap & Hashing 26. These are baseline counts, not live running totals; reread STATUS before each run.
