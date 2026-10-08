# DevOps Enrichment Status

Last updated: 2026-10-08.

## CI/CD, Kubernetes and cloud delivery

State: **PROMOTED; deep enrichment remains in progress**.

Canonical pair:

- [Markdown](cicd/guide.md)
- [mobile-friendly searchable HTML](cicd/guide.html)

Source comparison:

- Selected `cicd_aws_azure_teamcity_jenkins_guide.md` from `ai_cicd_spring_python_guides_complete.zip`; bundle SHA-256 `c52c8295ae7ff7d08013e30a8d7097480719db78104a91b79e367a382465dd31`, source SHA-256 `10deac7075831e2c2fbd88757ead4e284c7ff0fcd4ab3e9693553502be91919e` (1,427 lines before repository additions).
- Compared standalone `cicd_aws_azure_teamcity_jenkins_guide.html`, SHA-256 `a78c3ee9f83d6970c9fc55e81d6b355cfb66c13e798ed27bc487fc39aeee1f4d`; it is byte-identical to the generated HTML inside the bundle.
- The archive passed ZIP integrity and traversal checks. Nine CI/CD topic figures were retained; application source code stays in its dedicated repositories rather than being copied here.

Completed deep sections:

1. **Claim-bound release authorization** — four allow/deny decisions, OIDC-to-STS packet trace, exact-claim invariant, Java 17 policy model, proof, boundaries and 10,000 seeded mutations.
2. **Rollback and concurrent releases** — four race outcomes, compare-and-set trace, immutable release record, Java 17 concurrency model, proof, output-cost derivation and 10,000 stale-writer checks.

Validation recorded for this promotion:

- Both resolved Library sources were read; the ZIP was safely inspected and hashed before extraction.
- Both embedded Java 17 programs passed source-file compilation/execution: explicit authorization/retry/race/rollback cases plus 20,000 seeded adversarial rounds in total.
- Deterministic Pandoc rebuild passed; the HTML payload exactly recovers Markdown SHA-256 `f1bafdaca870dc09dbba7ae92f56519f948cef5fb9f4c9d06a18d62084c7494d`.
- All 619 HTML IDs are unique; internal fragments, nine relative PNG assets, 30 search-index targets and all 12 Mermaid blocks passed static validation.
- All nine retained figures passed PNG signature/dimension checks and were visually inspected as a contact sheet.
- The live literal DSA Java-marker audit remains 174 (Graph 51, Stack/Queue 18, Linked List 27, Trees/BST 51, Hashing 27).
- No live AWS/Azure deployment, registry push, Kubernetes API call, TeamCity DSL compilation or Jenkins execution was performed. Browser rendering remains unexecuted.

Next action:

1. Rotate fairly to the accessible unpromoted frontend domain, preferring the Angular Markdown/DOCX pair.
2. On the next DevOps pass, deepen probe-driven rollout failure diagnosis and TeamCity/Jenkins agent isolation.
3. Keep the bundle's AI-platform and Spring/Python guides queued as distinct canonical topics, not duplicate CI/CD editions.
