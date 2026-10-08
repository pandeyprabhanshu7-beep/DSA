# AWS Study Guide Enrichment Status

Last pipeline seed: 2026-10-07

## DONE

- [x] Seed `networking/AWS_Networking_Foundations_Bootcamp.md`
- [x] Seed `networking/AWS_Networking_Foundations_Bootcamp.html`
- [x] Establish AWS repository structure and scheduler continuation rules

## IN_PROGRESS

- [x] Networking visual-assets promotion and link validation.
  - Current source HTML references 12 educational networking diagrams.
  - Next run should promote/repair those diagram assets first, then validate every relative image/link path.
  - After assets are synchronized, deeply review the bootcamp for missing beginner explanations, packet dry-runs, legacy flow, ISP/BGP, firewall, IPv6, MTU, DNS/TCP/TLS and troubleshooting gaps.

## TODO — highest priority

1. Promote/enrich the latest `AWS_Complete_Master_Study_Guide_Expanded.md` and matching HTML without overwriting newer repository material.
2. Promote the latest AWS course `index.md` / `index.html` and source index.
3. Split/enrich core AWS modules and focused component lectures under meaningful directories.
4. Continue 1–2 files per scheduled run, always resuming IN_PROGRESS work first.
5. Maintain parallel Markdown + mobile-friendly HTML for canonical guides.
6. Validate navigation, anchors, images and relative links on every touched guide.

## Continuation rule

If a scheduled run stops because of model/tool/output limits, leave the exact file and next section here as IN_PROGRESS. The following hourly run must resume it before selecting new work.


## Networking repair and enrichment — 2026-10-08

- Rebuilt 12 original SVG diagrams under networking/assets/diagrams/networking/.
- Preserved the current bootcamp and added an explicit bidirectional home-PAT/CGNAT packet trace with an AWS comparison and primary references.
- Promoted 12 chapter Markdown/HTML pairs extracted from the combined guide, with previous/next navigation and chapter indexes.
- Repaired combined-page navigation; supplied local CSS and chapter-title search JavaScript. Unpromoted module links are labeled as pending rather than left broken.
- All local links and HTML fragment targets on the networking pages resolve; all SVGs parse as XML. One diagram was rasterized and visually inspected.
- Browser visual QA is pending because this workspace lacks the browser binary; responsive CSS is implemented but is not represented as visually verified.

IN_PROGRESS next: broader factual review of networking chapters, replace remaining text diagrams where useful, then resume master-guide and component-module promotion. The diagrams and navigation repair are complete; the whole AWS course is not complete.
