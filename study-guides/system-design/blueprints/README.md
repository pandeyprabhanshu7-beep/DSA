# System Design Blueprints

The [canonical Markdown guide](guide.md) is the source of truth. The [responsive searchable HTML reader](guide.html) is generated from it by `../build_guides.py` and embeds the exact Markdown bytes for validation.

`VideoPlaybackContracts.java` makes the capacity, adaptive-bitrate, and progress-ordering contracts executable. `RideHailingContracts.java` exercises location freshness, assignment ownership, reconnect ordering, and payment identity. `FinancialLedgerContracts.java` covers balanced entries, idempotent replay, contested debits, uncertain external outcomes and projection deduplication. `WebCrawlerContracts.java` checks URL identity, admission deduplication, robots rules, origin politeness, retry timing, address boundaries and recrawl delay. `DistributedIdContracts.java` verifies the 41/10/12 Snowflake-like layout, same-tick saturation, rollback and lease fencing, range allocation and deterministic UUIDv7 fields. `DesignDefenseContracts.java` verifies queue arithmetic, authority boundaries, caller-scoped idempotency, timeout evidence and invariant-preserving degradation. All run with Java 17 source-file launch.

This is an incremental guide. All five named source blueprints and the cross-question design-defense supplement are promoted, exhausting the confirmed source through line 1264. The wider enrichment backlog is not complete.
