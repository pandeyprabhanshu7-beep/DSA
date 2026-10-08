# AWS Study Guide Enrichment Status

Last enrichment: 2026-10-08 05:28 ET

## DONE

- [x] Seed `networking/AWS_Networking_Foundations_Bootcamp.md`
- [x] Seed `networking/AWS_Networking_Foundations_Bootcamp.html`
- [x] Establish AWS repository structure and scheduler continuation rules
- [x] Repair the networking diagrams, navigation and generated chapter readers
- [x] Deepen Path MTU Discovery and IPv6/AWS egress with packet traces and primary references
- [x] Deepen BGP selection/propagation and stateful-firewall return-path reasoning

## CURRENT STATE

- [x] Priority factual review of the networking bootcamp is complete for this rotation.
  - Diagram and navigation repair is complete.
  - MTU/PMTUD and IPv6 egress sections are complete for this pass.
  - BGP best-path policy/propagation and firewall/ephemeral-port return-path sections are complete for this pass.
  - No forced networking continuation remains; return through fair domain rotation for later chapter review.

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

## BGP and stateful-firewall enrichment — 2026-10-08

- Expanded BGP into learned, eligible, selected, installed and advertised states; documented NEXT_HOP, LOCAL_PREF, AS_PATH, MED and communities without treating a vendor tie-break list as protocol law.
- Added a three-candidate policy example, seven-step withdrawal/failover trace, common customer/peer/transit export model, four counterexamples, route-filtering controls and RPKI scope.
- Expanded stateful filtering with a six-step TCP state trace, initiator-dependent NACL rule tables, ephemeral-port range guidance, centralized Transit Gateway inspection trace, NAT observation points, four failure examples and a stateful invariant.
- Corrected a previously joined pair of BGP misconception bullets.
- Primary references: RFC 4271, RFC 7454, AWS Security Group connection tracking, custom NACL guidance, Network Firewall symmetric-routing guidance and Transit Gateway appliance mode.
- Validation: `build_networking.py` regenerated the two affected chapter pairs and combined HTML; generated Markdown/HTML section coverage, local links/fragments/assets, JSON, SVG XML and generator idempotence passed. Live DSA placeholder audit remains 174. Browser visual QA remains pending.

NEXT ROTATION: attempt the accessible unpromoted system-design fundamentals source. Return to AWS networking later through fair rotation; the AWS master course remains `NEEDS_SOURCE`, and the whole technical library is not complete.
