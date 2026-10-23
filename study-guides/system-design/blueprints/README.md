# System Design Blueprints

The [canonical Markdown guide](guide.md) is the source of truth. The [responsive searchable HTML reader](guide.html) is generated from it by `../build_guides.py` and embeds the exact Markdown bytes for validation.

`VideoPlaybackContracts.java` makes the capacity, adaptive-bitrate, and progress-ordering contracts executable. `RideHailingContracts.java` exercises location freshness, assignment ownership, reconnect ordering, and payment identity. `FinancialLedgerContracts.java` covers balanced entries, idempotent replay, contested debits, uncertain external outcomes and projection deduplication. All run with Java 17 source-file launch.

This is an incremental guide. Blueprints 1–3 are promoted; the remaining two source blueprints stay queued and must be processed one bounded section at a time.
