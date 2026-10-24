# System Design Study Guides

Canonical study material is promoted by conceptual topic. Historical filenames and timestamps are source-discovery hints, not publication versions.

## Guides

| Topic | Markdown | Searchable HTML | Status |
|---|---|---|---|
| Fundamentals | [guide.md](fundamentals/guide.md) | [guide.html](fundamentals/guide.html) | Promoted; ongoing deep enrichment |
| Interview blueprints | [guide.md](blueprints/guide.md) | [guide.html](blueprints/guide.html) | All five named blueprints and the cross-blueprint design-defense supplement are promoted; confirmed source exhausted |

## Build

Run `python3 study-guides/system-design/build_guides.py` from the repository root. The builder requires Pandoc, creates static mobile-friendly HTML, includes a section search index, and embeds the exact source Markdown as a Base64 validation payload.

See [STATUS.md](STATUS.md) for provenance, validation, and the exact continuation.
