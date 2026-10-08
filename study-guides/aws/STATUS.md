# AWS Study Guide Enrichment Status

Last pipeline seed: 2026-10-07

## DONE

- [x] Seed `networking/AWS_Networking_Foundations_Bootcamp.md`
- [x] Seed `networking/AWS_Networking_Foundations_Bootcamp.html`
- [x] Establish AWS repository structure and scheduler continuation rules

## IN_PROGRESS

- [ ] Networking visual-assets promotion and link validation.
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
