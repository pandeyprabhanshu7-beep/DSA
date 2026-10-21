# System Design Blueprints

The [canonical Markdown guide](guide.md) is the source of truth. The [responsive searchable HTML reader](guide.html) is generated from it by `../build_guides.py` and embeds the exact Markdown bytes for validation.

`VideoPlaybackContracts.java` makes the capacity, adaptive-bitrate, and progress-ordering contracts executable with Java 17 source-file launch.

This is an incremental guide. Blueprint 1 is promoted; later source blueprints remain queued and must be processed one bounded section at a time.
