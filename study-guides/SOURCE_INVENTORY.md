# Technical Source Inventory and Enrichment Queue

Inventory: 2026-10-08. Source metadata has been discovered; file contents have not been imported by this enrollment pass. Each run must read and compare source content before promotion. Canonical paths for new topics are proposed destinations, not claims of files already existing.

| Topic | Domain | State | Canonical destination | Source candidates |
|---|---|---|---|---|
| dsa-graphs | DSA | PROMOTED | `study-guides/striver-dsa/graph-dsa/` | Take_U_Forward_Graph_Verified_Problem_Guide_v3.md<br>Take_U_Forward_Graph_Complete_Deep_Guide_v2.md<br>DSA_Graph_FAANG_Mermaid_Question_Workbook.md<br>Take_U_Forward_Graph_Java_Mermaid_Deep_Guide.md<br>DSA_Graph_FAANG_Question_Workbook.docx |
| dsa-trees | DSA | PROMOTED | `study-guides/striver-dsa/trees-bst/` | Take_U_Forward_Tree_Verified_Problem_Guide_v3.md<br>DSA_Tree_FAANG_Mermaid_Question_Workbook.md<br>DSA_Tree_FAANG_Question_Workbook.docx |
| dsa-stack-queue | DSA | PROMOTED | `study-guides/striver-dsa/stack-queue/` | Take_U_Forward_Stack_Queue_Complete_Deep_Guide_v2.md<br>Take_U_Forward_Stack_Queue_Mermaid_Guide.md<br>Take_U_Forward_Stack_Queue_Java_Deep_Workbook.docx<br>Stack_Queue_FAANG_Question_Workbook_Expanded.docx |
| dsa-linked-list | DSA | PROMOTED | `study-guides/striver-dsa/linked-list/` | Repository source or earlier analysis; locate as needed |
| dsa-hashing | DSA | PROMOTED | `study-guides/striver-dsa/hashmap-hashing/` | Repository source or earlier analysis; locate as needed |
| dsa-arrays | DSA | IN_PROGRESS | [`study-guides/striver-dsa/arrays/`](striver-dsa/arrays/) | DSA_Arrays_FAANG_Mermaid_Question_Workbook.md<br>DSA_Arrays_FAANG_Question_Workbook_Expanded.docx |
| advanced-trees | DSA | IN_PROGRESS | `study-guides/local-project-notes/advanced-trees/` | Uploaded `untitled.zip` Advanced_Trees_FAANG_Mermaid_Study_Guide.md (sections 3–10 promoted)<br>Tree_Advanced_FAANG_Study_Guide.docx<br>advanced_trees_interview_guide.md |
| ai-foundations-rag-cag | AI | PROMOTED | `study-guides/ai/foundations-rag-cag/` | AI_RAG_CAG_Deep_Learning_Mermaid_Developer_Guide.md<br>AI_RAG_CAG_Deep_Learning_Developer_Guide.docx |
| ai-platform | AI | IN_PROGRESS | [`study-guides/ai/platform/`](ai/platform/) | ai_cicd_spring_python_guides_complete.zip<br>ai_platform_master_guide.html<br>ai_platform_engineering_complete_bundle.zip<br>ai_platform_engineering_illustrated.html |
| system-design-fundamentals | System design/data | PROMOTED | `study-guides/system-design/fundamentals/` | system_design_fundamentals_deep_research.md<br>system_design_fundamentals.md |
| system-design-blueprints | System design/data | PROMOTED | [`study-guides/system-design/blueprints/`](system-design/blueprints/) | top_5_system_design_interview_blueprints.md — all five named blueprints plus the cross-question/practice supplement promoted; source exhausted |
| database-caching-java | System design/data | PROMOTED | [`study-guides/databases/caching-java/`](databases/caching-java/) | database_internals_caching_java_master_guide.md |
| cicd-cloud-kubernetes | DevOps/backend | PROMOTED | `study-guides/devops/cicd/` | cicd_aws_azure_teamcity_jenkins_guide.html<br>ai_cicd_spring_python_guides_complete.zip |
| spring-ai-python-api | DevOps/backend | IN_PROGRESS | [`study-guides/backend/spring-ai-python/`](backend/spring-ai-python/) | ai_cicd_spring_python_guides_complete.zip<br>spring_ai_python_api_interview_guide.html |
| angular | Frontend | PROMOTED | `study-guides/frontend/angular/` | Angular_Interview_Mermaid_Workbook.md<br>Angular_Interview_Workbook.docx |
| javascript-html-css | Frontend | PROMOTED | [`study-guides/frontend/javascript-html-css/`](frontend/javascript-html-css/) | JavaScript_HTML_CSS_Interview_Mermaid_Workbook.md<br>JavaScript_HTML_CSS_Interview_Workbook.docx |
| aws-networking | AWS/cloud | PROMOTED | `study-guides/aws/networking/` | Repository source or earlier analysis; locate as needed |
| aws-master-course | AWS/cloud | NEEDS_SOURCE | `study-guides/aws/` | Repository source or earlier analysis; locate as needed |
| cloud-comparisons | AWS/cloud | NEEDS_SOURCE | `study-guides/cloud/comparisons/` | Repository source or earlier analysis; locate as needed |
| java-security-protocols | DevOps/backend | NEEDS_SOURCE | `study-guides/backend/java-security-protocols/` | Repository source or earlier analysis; locate as needed |
| ai-project-docs | AI | DISCOVERED | `study-guides/ai/projects/` | spring-boot-ai-labs.zip<br>spring-boot-ai-journey.zip<br>GITHUB_PUSH_COMMANDS.md<br>README.md<br>enterprise-rag-assistant.zip |
| ai-employment-impact | AI | DISCOVERED | `study-guides/ai/employment-impact/` | ai_jobs_elimination_deep_research_2026.md |
| technical-study-workflows | Learning workflows | DISCOVERED | `study-guides/study-workflows/` | MASTER_PROMPT.md |

## Discovered source files

| Filename | Last modified | Intake state |
|---|---|---|
| spring-boot-ai-labs.zip | 2026-10-07T16:41:39.053086Z | DISCOVERED |
| MASTER_PROMPT.md | 2026-10-06T19:51:18.409209Z | DISCOVERED |
| spring-boot-ai-journey.zip | 2026-10-06T19:51:18.370213Z | DISCOVERED |
| cicd_aws_azure_teamcity_jenkins_guide.html | 2026-09-17T05:55:55.269830Z | READY — compared; byte-identical to bundled generated HTML |
| ai_cicd_spring_python_guides_complete.zip | 2026-09-17T05:55:54.714170Z | IN PROGRESS — CI/CD source promoted; Spring/Python Sections 1–22 processed from the editable Markdown member and Sections 23–25 queued |
| ai_platform_master_guide.html | 2026-09-17T05:55:54.695794Z | PROMOTED — selected 21-chapter source; Chapters 1–21 processed; selected source exhausted |
| spring_ai_python_api_interview_guide.html | 2026-09-17T05:55:54.480720Z | READY — compared; byte-identical to the bundle's generated HTML and not promoted as a duplicate |
| ai_platform_engineering_complete_bundle.zip | 2026-09-16T16:22:16.910779Z | PROMOTED AS COMPANION — editable RAG/MCP source exhausted through Chapter 13 |
| ai_platform_engineering_illustrated.html | 2026-09-16T16:22:16.059506Z | READY — compared; byte-identical to bundled illustrated HTML |
| database_internals_caching_java_master_guide.md | 2026-09-16T05:20:18.877277Z | PROMOTED — Sections 1–44 lines 62–2402 processed through source end |
| top_5_system_design_interview_blueprints.md | 2026-09-11T21:27:10.870584Z | PROMOTED — Blueprints 1–5 plus cross-question/practice supplement lines 1127–1264 promoted through source end |
| system_design_fundamentals_deep_research.md | 2026-09-11T21:27:08.096531Z | PROMOTED — canonical fundamentals base |
| system_design_fundamentals.md | 2026-09-11T04:57:16.259594Z | READY — compared; distinct case studies reserved for blueprints |
| ai_jobs_elimination_deep_research_2026.md | 2026-09-10T23:06:32.260101Z | DISCOVERED |
| Take_U_Forward_Graph_Verified_Problem_Guide_v3.md | 2026-09-08T04:49:59.864498Z | DISCOVERED |
| Take_U_Forward_Tree_Verified_Problem_Guide_v3.md | 2026-09-08T04:49:59.825371Z | DISCOVERED |
| Take_U_Forward_Graph_Complete_Deep_Guide_v2.md | 2026-09-08T01:35:54.662720Z | DISCOVERED |
| Take_U_Forward_Stack_Queue_Complete_Deep_Guide_v2.md | 2026-09-08T01:35:54.662145Z | DISCOVERED |
| AI_RAG_CAG_Deep_Learning_Mermaid_Developer_Guide.md | 2026-09-08T00:10:00.856206Z | PROMOTED — canonical AI foundations/RAG/CAG base |
| DSA_Graph_FAANG_Mermaid_Question_Workbook.md | 2026-09-08T00:10:00.813753Z | DISCOVERED |
| Advanced_Trees_FAANG_Mermaid_Study_Guide.md | 2026-09-08T00:10:00.813395Z | CORE PROCESSED — sections 3–10 promoted as focused modules; sections 1–2 merged into canonical Trees/BST cards; supplementary sections 11–15 remain inventory-only |
| Angular_Interview_Mermaid_Workbook.md | 2026-09-08T00:10:00.775386Z | PROMOTED — Q1–Q74, rapid-fire items 1–39 and Final Revision Sheet processed through source end at line 1751 |
| JavaScript_HTML_CSS_Interview_Mermaid_Workbook.md | 2026-09-08T00:10:00.753196Z | PROMOTED — Q1–Q72, rapid-fire items 1–32 and the Final Revision Sheet compared/promoted through source end at line 2041 |
| DSA_Tree_FAANG_Mermaid_Question_Workbook.md | 2026-09-08T00:10:00.743385Z | DISCOVERED |
| DSA_Arrays_FAANG_Mermaid_Question_Workbook.md | 2026-09-08T00:10:00.740223Z | IN PROGRESS — selected editable source; Sections 1–3 lines 141–251 and Section 4 problems 1–22 promoted; problem 22 is bounded by lines 2250–2341 and problem 23 begins at line 2342 |
| Take_U_Forward_Graph_Java_Mermaid_Deep_Guide.md | 2026-09-07T23:36:35.951649Z | DISCOVERED |
| Take_U_Forward_Stack_Queue_Mermaid_Guide.md | 2026-09-07T18:00:03.911417Z | DISCOVERED |
| GITHUB_PUSH_COMMANDS.md | 2026-09-07T17:39:56.663357Z | DISCOVERED |
| README.md | 2026-09-07T17:39:53.017314Z | DISCOVERED |
| enterprise-rag-assistant.zip | 2026-09-07T17:39:49.442548Z | DISCOVERED |
| Take_U_Forward_Stack_Queue_Java_Deep_Workbook.docx | 2026-09-07T17:07:54.535717Z | DISCOVERED |
| Stack_Queue_FAANG_Question_Workbook_Expanded.docx | 2026-09-03T05:33:07.971008Z | DISCOVERED |
| DSA_Arrays_FAANG_Question_Workbook_Expanded.docx | 2026-09-03T01:55:56.718502Z | IN PROGRESS — Sections 1–3 and Section 4 problems 1–22 corroborated; reproducible Pandoc 3.1.3 no-wrap GFM extraction places problem 22 at lines 2219–2311 and problem 23 at line 2312; 116-page rendering previously inspected |
| AI_RAG_CAG_Deep_Learning_Developer_Guide.docx | 2026-09-02T04:40:53.490553Z | READY — compared; two useful original figures retained |
| Angular_Interview_Workbook.docx | 2026-08-31T14:40:43.673563Z | PROMOTED — Q1–Q74, rapid-fire items 1–39 and Final Revision Sheet processed through extracted source end at line 1177 |
| JavaScript_HTML_CSS_Interview_Workbook.docx | 2026-08-31T14:40:39.905787Z | PROMOTED — corroborated Q1–Q72, rapid-fire items 1–32 and the Final Revision Sheet through rendered source end at line 1445 |
| DSA_Graph_FAANG_Question_Workbook.docx | 2026-08-30T07:02:49.102302Z | DISCOVERED |
| DSA_Tree_FAANG_Question_Workbook.docx | 2026-08-30T07:02:48.410227Z | DISCOVERED |
| Tree_Advanced_FAANG_Study_Guide.docx | 2026-08-30T05:23:39.553805Z | DISCOVERED |
| advanced_trees_interview_guide.md | 2026-08-19T04:44:00.028771Z | DISCOVERED |

## Interpretation

- Existing DSA and AWS sources remain canonical. Historical attachments provide improvements, not replacement editions.
- System-design fundamentals now has one canonical [Markdown/HTML guide](system-design/); the 1,785-line deep-research source won the content comparison. The distinct case-study source has its own canonical [blueprints guide](system-design/blueprints/): all five named blueprints plus the cross-question design-defense supplement are promoted with worked traces and executable contracts, exhausting that confirmed source without creating a duplicate fundamentals edition.
- Database internals/caching/Java now has one incremental canonical [Markdown/HTML guide](databases/caching-java/). Sections 1–43 are promoted from confirmed source ranges; the B+ tree section avoids duplicating the focused algorithm module, the LSM section adds recovery/read/compaction analysis, the row/column section adds byte accounting and replayable CDC projection contracts, the isolation section adds exact anomalies and bounded retry, the cache-topology section adds JVM/distributed placement arithmetic, admission, invalidation recovery and scoped-key contracts, the eviction section contrasts exact LRU/LFU/FIFO with full 2Q scan resistance, the stampede section adds local/global fill coordination plus bounded stale/origin fallback, the penetration section adds typed negative entries plus completeness-safe Bloom filtering, the avalanche section adds TTL jitter, capacity-gated warming, soft/hard freshness and outage admission, the pool-architecture section distinguishes logical JDBC leases from physical sessions, the physical-connection section accounts for startup/churn/ownership, the Hikari-controls section joins timeouts/workload sizing/telemetry, the JDBC-boundary section separates APIs/pools/protocols/transports, the PostgreSQL connection sections add startup/framing/assurance plus timeout ownership and TLS/SCRAM policy, the MySQL section adds authentication-order contracts, the prepared-query section adds query-mode thresholds, extended-protocol recovery, safe dynamic SQL and cursor-backed retrieval ownership, the production-contract section joins immutable identity, authorization, bounded fallback, publication visibility and deadline policy, the cache component and mutable-row sections establish typed coordination/outbox fencing, the fault-injection section turns those claims into real-service oracles, the recovery section adds fenced failover, bounded cache warming, evidence-led pool diagnosis and fresh-connection rotation gates, the senior-review section converts twelve boundary questions into four evidence-led executable drills, the acceptance section makes eleven launch requirements measurable and fail-closed, the first inside-components walkthrough traces a read through plan reuse, slotted pages, buffer ownership, MVCC and JDBC, the second traces one versioned edit through WAL durability, crash recovery, operation-ID reconciliation and maintenance diagnosis, the B+ dry run adds explicit separator/split, leaf-range, InnoDB secondary and PostgreSQL index-only traces, the LSM dry run adds snapshot-visible version selection, tombstone-safe compaction, Bloom probe accounting and compaction-debt budgeting, the slow-endpoint section adds equality-prefix/order-suffix design, bitmap-plus-sort contrast, plan evidence and scope-bound keyset pagination, the two-session isolation lab adds statement/transaction snapshot traces, write-skew retry, guard-row serialization and deadlock-safe global lock order, the eviction dry-run lab adds exact survivor traces, explicit LFU aging, 2Q ghost reload and CMS scan admission, the cache-concurrency lab adds owner-checked cleanup, positive/negative generation fences and fleet admission arithmetic, the connection-pool arithmetic lab adds residency/queue math, layered wait-room evidence and explicit deadline/outcome ownership, the protocol walkthrough adds exact framing bytes, stream-safe parsing, bounded handshake admission and independent security gates, the execution-story lab adds typed first-probe envelopes, owner-safe miss coalescing, separate Redis/database failure paths and lease-expiry reasoning for immutable versus mutable data, the CMS design case separates edge bandwidth, pointer authority, immutable revision caching, outbox projections and layer-specific collapse budgets, the flash-sale case adds hot-SKU admission, atomic reservation, payment-expiry transitions and bounded token conservation, the reliable-notification case adds explicit lifecycle facts, transactional inbox deduplication, ambiguous-provider reconciliation and bounded retry/backlog/freshness policy, the activity-history case adds query-shaped bucketing, tie-safe pagination, measured hot-user sharding and separate analytical projection, the subcomponent-explanation section adds falsifiable ownership, success, crash, metric and alternative boundaries, and the debugging-exercise section adds absolute-rate, index-evidence, publication-lifecycle and authority-boundary incident reasoning. Later source sections remain queued.
- Section 42 now extends that database guide with a seven-slot falsifiable component contract plus outbox-relay, cache-loader and connection-pool ownership/failure traces.
- Section 43 now extends that database guide with four executable incident-diagnosis labs covering all ten source exercises.
- AI foundations, RAG and CAG now has one canonical [Markdown/HTML guide](ai/); the broader Mermaid source won the content comparison, while two useful original workbook figures were retained without duplicating its redundant flow diagrams.
- AI platform engineering now has one incremental canonical [Markdown/HTML guide](ai/platform/). The expanded 21-chapter master won the comparison, the bundle's RAG/MCP Markdown supplies the editable companion, and the separate illustrated HTML is not duplicated because it is byte-identical to the bundle member. Chapters 1–21 are promoted and the selected master is exhausted. Chapter 21 integrates scoped support answers, approval-bound and reconcilable refunds, fair resumable ingestion, atomic publication and complete rollback identity with an executable contract. Wider confirmed intake remains open.
- CI/CD, Kubernetes and cloud delivery now has one canonical [Markdown/HTML guide](devops/); the bundle supplied its authoritative Markdown and nine figures, while the standalone HTML was verified as an exact generated duplicate rather than promoted separately.
- Spring AI/Python API engineering now has one incremental canonical [Markdown/HTML guide](backend/spring-ai-python/). The bundle's editable Markdown was selected, while the standalone HTML was compared through the same Section 22 boundary and is not duplicated. Sections 1–22 are promoted with current release identity, atomic readiness publication, bounded admission/deadlines, deterministic retrieval, grounding gates, durable retrieval, strict structured-output validation, server-owned provenance, trusted service/evidence checks, MCP boundaries, streaming/cancellation states, embedding/reranking evaluation, provider capability admission, evidence-led retries, migration gates, boundary-first tracing, bounded metric labels, protected content capture, security-first tenant-leak handling, evidence-producing test layers, a four-week same-release evidence chain, provider-neutral answer DTOs, stable problem semantics, scoped-hash idempotency, transactional outbox processing, post-commit terminal-event semantics, replay/action idempotency, exactly-once cleanup, verified JWT-to-membership-to-query authorization, checked target-tokenizer budgeting, exact/semantic/hybrid retrieval selection, runtime-boundary ownership, capability admission, typed API outcomes, layered validation, concurrency dispatch, fleet capacity, retry ownership, generation-safe embedding migration, tenant-prefiltered ANN, bounded agent loops and ambiguous-effect reconciliation with paired Java/Python contracts. Section 23 begins at editable line 1307; the standalone HTML contains the same bounded Section 22 content and the bundle reader is byte-identical to it.
- Graph/tree/stack editions and matching DOCX documents are grouped into one destination; latest coverage must be chosen after reading.
- The AI/CI-CD/Spring-Python ZIP may contain Markdown and assets for three separate topics; inspect archive contents before deciding.
- The AI-platform illustrated edition may hold useful material missing from a later master guide. Compare content; merge the best coverage in place.
- Database internals, caching and Java connectivity are a distinct source, and arrays, advanced trees and frontend guides are newly enrolled topics.
- The uploaded advanced-trees guide's core sections 1–10 are processed under [local project notes](local-project-notes/): Fenwick, lazy segment tree, trie, heap, B+ tree, B-tree, Red-Black Tree and AVL Tree are focused modules, while sections 1–2 improved canonical BT-1/BT-3 and BST-6/BST-7 in place. Supplementary sections 11–15 remain inventory-only rather than being treated as reviewed content.
- Angular intake now has one canonical guide through Q74, rapid-fire items 1–39 and the Final Revision Sheet from both paired sources; that source pair is exhausted. JavaScript/HTML/CSS intake likewise covers Q1–Q72, rapid-fire items 1–32 and its Final Revision Sheet. Continue consolidating rather than duplicating canonical answers.
- AI projects keep their code in existing repositories. Enroll technical explanations, setup, architecture and lessons here.
- Missing AWS master-guide, cloud-comparison and Java/security source bytes remain NEEDS_SOURCE. Known topics are queued without pretending they were imported.
- No personal resumes or unrelated private documents are enrolled. Source identifiers are retrieval references, not credentials.

See [pipeline rules](ENHANCEMENT_PIPELINE.md) and [machine-readable queue](ENRICHMENT_QUEUE.json).



Angular Q1–Q74 plus rapid-fire items 1–39 are tracked at [frontend/angular](frontend/angular/). The fourth rapid-fire module was reconciled from both workbooks on 2026-10-14; the Final Revision Sheet remains unprocessed.

JavaScript/HTML/CSS Q1–Q72, rapid-fire items 1–32 and the Final Revision Sheet are tracked at [frontend/javascript-html-css](frontend/javascript-html-css/). The final trigger-map/speaking-template module was reconciled from both workbooks on 2026-10-16, completing this paired-source intake. Database/caching Sections 1–44 are tracked at [databases/caching-java](databases/caching-java/); its confirmed source is exhausted through line 2402. Angular's paired source is also exhausted. AI-platform Chapters 1–21, all five System-design Blueprints plus their cross-question supplement, Spring AI/Python Sections 1–22, DSA Arrays Sections 1–3 and the bounded Arrays Section 4 problems 1–22 slice are promoted. The selected AI-platform master is exhausted after Chapter 21's three complete platform exercises. Rotate next to Spring AI/Python Section 23, then Arrays problem 23 after fair domain rotation. The wider collection and freshly verified 170-item Java-placeholder backlog remain incomplete.







