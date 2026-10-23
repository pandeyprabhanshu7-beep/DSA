# System Design Blueprints

The [canonical Markdown guide](guide.md) is the source of truth. The [responsive searchable HTML reader](guide.html) is generated from it by `../build_guides.py` and embeds the exact Markdown bytes for validation.

`VideoPlaybackContracts.java` makes the capacity, adaptive-bitrate, and progress-ordering contracts executable. `RideHailingContracts.java` exercises location freshness, assignment ownership, reconnect ordering, and payment identity. `FinancialLedgerContracts.java` covers balanced entries, idempotent replay, contested debits, uncertain external outcomes and projection deduplication. `WebCrawlerContracts.java` checks URL identity, admission deduplication, robots rules, origin politeness, retry timing, address boundaries and recrawl delay. All run with Java 17 source-file launch.

This is an incremental guide. Blueprints 1–4 are promoted; Blueprint 5 remains queued and must be processed as a later bounded section.
