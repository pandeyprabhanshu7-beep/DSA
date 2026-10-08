# AWS Study Guide Enrichment Status

Last enrichment: 2026-10-08 04:33 ET

## DONE

- [x] Seed `networking/AWS_Networking_Foundations_Bootcamp.md`
- [x] Seed `networking/AWS_Networking_Foundations_Bootcamp.html`
- [x] Establish AWS repository structure and scheduler continuation rules
- [x] Repair the networking diagrams, navigation and generated chapter readers
- [x] Deepen Path MTU Discovery and IPv6/AWS egress with packet traces and primary references

## IN_PROGRESS

- [ ] Broader factual review of the networking bootcamp.
  - Diagram and navigation repair is complete.
  - MTU/PMTUD and IPv6 egress sections are complete for this pass.
  - Next coherent section: BGP best-path policy, route propagation and failure examples, followed by firewall/ephemeral-port packet traces.

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

## MTU and IPv6 enrichment — 2026-10-08

- Replaced the brief MTU note with interface-MTU/PMTU/MSS definitions, IPv4-versus-IPv6 behavior, a PMTU-black-hole packet trace, four failure examples, an AWS NACL invariant, troubleshooting steps and common mistakes.
- Expanded IPv6 with address/route/policy separation, address forms, Neighbor Discovery and fragmentation differences, public versus outbound-only AWS route patterns, a five-step egress-only-IGW trace, four examples and dual-stack failure modes.
- Corrected the executable command example from `a rp -a` to `arp -a`.
- Primary references: AWS VPC PMTUD/NACL guidance, AWS egress-only Internet Gateway documentation, RFC 8200 and RFC 8201.
- Validation: the authoritative combined Markdown was rebuilt through `build_networking.py`; affected chapter Markdown/HTML and combined HTML were regenerated; local links, fragments, assets, JSON, SVG XML and generator idempotence passed. Browser visual QA remains pending.

IN_PROGRESS next: enrich BGP best-path policy/route propagation and stateful-firewall return-path examples, then resume master-guide and component-module promotion. The whole AWS course is not complete.
