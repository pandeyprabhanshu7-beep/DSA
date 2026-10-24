# System Design Blueprints

This is the canonical incremental guide. Each blueprint makes its requirements, arithmetic, ownership boundaries, failure behavior, and verification evidence explicit. All five named source blueprints and the cross-question design-defense supplement are promoted; the confirmed source is processed through its end.

## Blueprint 1 — Global video streaming

### 1. Frame the problem before drawing boxes

The service must let a permitted profile browse, start, pause, seek, switch device, and resume a title with multiple audio/subtitle tracks. A studio path must ingest a pristine master, encode device-compatible renditions, package segments and publish them. The design must keep startup and rebuffering low under regional spikes, survive edge/origin failures, and prevent unauthorized playback.

State the consistency contract per fact:

| Fact | Required behavior | Why |
|---|---|---|
| entitlement and regional availability | fail closed; bounded-stale policy only if explicitly approved | a stale allow can violate licensing |
| manifest/segment grant | short-lived, scoped to asset/session/region; origin accepts only CDN identity | a signed URL is bearer authority, not DRM |
| catalog snapshot | versioned and atomically published | clients must not mix partial rights/config revisions |
| progress | eventually visible; newest per-session sequence wins | retries and device networks reorder events; backward seek is valid |
| recommendations/QoE analytics | eventual and replayable | loss or duplication should not block playback |

Three planes prevent the most common architecture mistake:

```mermaid
flowchart TB
  C["Player"] --> CP["Control plane: identity, rights, session, steering"]
  C --> DP["Media plane: manifest and segments via CDN"]
  C --> EP["Event plane: progress and QoE"]
  CP --> META[("Metadata and rights")]
  DP --> EDGE["Edge cache"] --> SHIELD["Shield"] --> ORIGIN[("Packaged media")]
  EP --> LOG[("Durable event log")] --> VIEW[("Progress view")]
```

The application API authorizes and steers; it never proxies multi-gigabyte media. That separation makes control-plane CPU and data-plane egress independently scalable.

### 2. Example 1 — capacity proves locality is the architecture

Use decimal network units and write the unit conversion:

\[
T_{Tbps}=\frac{N_{streams}\times bitrate_{Mbps}}{1{,}000{,}000}
\]

| Scenario | Inputs | Result | Design consequence |
|---|---:|---:|---|
| global peak | 60M × 5 Mbps | 300 Tbps | ordinary app servers and a few regions cannot be the media path |
| large region | 1M × 8 Mbps | 8 Tbps | steer by ISP/site health and capacity, not geography alone |
| premiere spike | 2M × 6 Mbps | 12 Tbps | pre-position the predicted hot set before release |
| origin exposure | 100 Tbps viewer traffic, 98.5% byte-hit ratio | 1.5 Tbps cache-miss traffic | a high percentage can still leave enormous origin/shield load |

Request hit ratio is misleading when objects differ greatly in size; operate on byte hit ratio and segment-age cohorts. A shield collapses duplicate fills, while regional/ISP caches localize popular bytes. Netflix's Open Connect material describes appliances placed near viewers and proactive filling; the transferable principle is forecasted placement plus measured fallback, not copying a vendor topology.

```mermaid
flowchart LR
  O[("Origin")] --> S["Origin shield"] --> R["Regional cache"] --> I["ISP cache"] --> P["Player"]
  P -. "edge down" .-> R
  R -. "region down" .-> S
```

Trace a premiere:

| Time | Event | Guardrail |
|---|---|---|
| T−24 h | planner assigns immutable segment hashes to sites | never place a title where rights forbid delivery |
| T−1 h | fill jobs verify checksum and storage headroom | incomplete renditions stay unpublished |
| T+0 | steering selects a healthy, non-saturated site | capacity token prevents overload amplification |
| T+2 min | one site fails | retry a different site, lower bitrate if throughput falls |
| T+5 min | byte misses exceed budget | shed speculative prefetch and protect shield/origin |

### 3. Example 2 — authorization, signed delivery, and DRM are different controls

`POST /v1/playback-sessions` authenticates the account, resolves profile restrictions and region, checks the versioned rights snapshot, chooses compatible media, and returns a short-lived playback grant. A representative response is:

```json
{
  "sessionId": "ps_7f3",
  "manifestUrl": "https://edge.example/t/a17/master.m3u8?grant=...",
  "expiresAt": "2026-10-21T15:20:00Z",
  "drm": {"licenseUrl": "https://license.example/v1/widevine"},
  "cdnCandidates": ["site-a", "site-b"]
}
```

The token must cover the intended path/asset, expiry, key identifier and policy claims. CDN/origin clocks need bounded skew and signing-key rotation needs an overlap window. The storage origin must reject public access and accept only the delivery service identity; otherwise a user can bypass the signed edge URL. Signing limits distribution authority, while DRM encrypts media and licenses decryption to an approved device/session—neither substitutes for the other.

```mermaid
sequenceDiagram
  participant P as Player
  participant A as Playback API
  participant E as CDN edge
  participant O as Private origin
  P->>A: create session + title/profile/device
  A-->>P: scoped expiring manifest grant
  P->>E: GET manifest/segment + grant
  E->>E: verify scope, expiry, key
  alt cache hit
    E-->>P: bytes
  else cache miss
    E->>O: authenticated origin fetch
    O-->>E: immutable bytes
    E-->>P: bytes
  end
```

Failure tests: an expired grant returns 403 without origin fetch; a valid grant for another asset fails scope validation; direct origin access fails; rotating keys accept both active keys only during the declared overlap; revocation policy states whether an already-issued short grant may live until expiry.

### 4. Example 3 — adaptive bitrate is a buffer-safety controller

HLS exposes alternative renditions through playlists and ordinary HTTP delivery. The player estimates recent throughput but should not spend the entire estimate: measurement error, shared links and segment-size variance require headroom. A simple teaching policy selects the highest rendition under `estimate × safety`; when the buffer is below eight seconds it caps safety at 0.55.

| Estimate | Buffer | Safety | Budget | Choice from 0.3/1.2/2.5/5 Mbps ladder |
|---:|---:|---:|---:|---|
| 8 Mbps | 20 s | 0.75 | 6 Mbps | 1080p / 5 Mbps |
| 4 Mbps | 20 s | 0.75 | 3 Mbps | 720p / 2.5 Mbps |
| 4 Mbps | 3 s | 0.55 | 2.2 Mbps | 480p / 1.2 Mbps |

```mermaid
stateDiagram-v2
  [*] --> Conservative: startup or low buffer
  Conservative --> Stable: buffer recovers
  Stable --> Higher: sustained headroom
  Higher --> Conservative: slow segment or error
  Stable --> Conservative: buffer below floor
```

The production controller also accounts for device decode limits, codec/HDR compatibility, segment duration, recent errors and switch hysteresis. Optimize a QoE objective—startup time, rebuffer ratio, failures, sustainable quality and disruptive switches—not maximum pixels. RFC 9317 is useful here because it treats streaming quality as an operational interaction among player, delivery network and access network.

### 5. Example 4 — progress merging must allow a backward seek

Do not merge progress with `max(position)`. A user may deliberately seek backward, and an old retry can carry a larger position than a newer event. Give each playback session a monotonically increasing sequence and use `(profileId, titleId, sessionId, sequence)` as the ordering fact.

| Arrival | Event `(sequence, position)` | Result |
|---|---|---|
| current | `(9, 80s)` | store 80s |
| delayed retry | `(8, 95s)` | ignore stale sequence |
| user seeks backward | `(10, 25s)` | store 25s |
| duplicate | `(10, 25s)` | idempotent no-op |

For multiple devices, define the product rule rather than pretending one clock solves intent: commonly, each session is ordered independently and the resume projection selects the latest accepted event time with bounded clock handling. Keep the immutable event log for replay; build a compact projection for reads. Playback must not wait for this pipeline.

### 6. Media preparation and publication contract

1. Store the pristine mezzanine in durable object storage under immutable identity.
2. Produce a per-title ladder; one fixed ladder wastes bits on simple content and damages complex content.
3. Package aligned HLS/DASH-like segments and manifests, including audio/subtitle variants.
4. Verify checksums, duration alignment, decode compatibility and rights metadata.
5. Atomically move a versioned publication pointer only after all required artifacts pass.
6. Pre-position the hot set; retain origin/shield fallback for the long tail.

Never overwrite published segment bytes in place. Immutable names make caches and retries safe; a new encode publishes a new revision.

### 7. API, storage, and ownership map

```http
GET  /v1/catalog/home?profileId=...
GET  /v1/titles/{titleId}
POST /v1/playback-sessions
GET  /v1/playback-sessions/{sessionId}/manifest
POST /v1/playback-sessions/{sessionId}/progress
```

| Data | System of record | Derived/cached form |
|---|---|---|
| title, tracks, maturity | metadata store | catalog/search projections |
| country/plan/time rights | rights store + versioned snapshots | playback decision cache bounded by policy |
| mezzanine and encoded segments | object storage | shield/edge copies |
| session/grant audit | playback service store | short-lived session cache |
| progress/QoE | durable event log | resume view and analytics |

Multi-GB video never belongs in the metadata database. Segment URLs identify immutable objects; manifests identify a published revision.

### 8. Failure matrix and evidence

| Failure | Expected behavior | Metric/test |
|---|---|---|
| edge unavailable | steer to another healthy site; player may reduce bitrate | failover time, rebuffer delta |
| shield/origin overload | admission control, request collapse, stale immutable edge bytes | origin egress and rejected fills |
| rights store unavailable | fail closed or use explicitly approved bounded snapshot | denial reason and snapshot age |
| progress log delayed | playback continues; client retries idempotently | consumer lag and projection freshness |
| signing key rotation | accept declared overlap, then reject old key | validation counts by key ID |
| bad encode | publication pointer remains on prior good revision | canary decode/checksum failures |

Track startup p50/p95, rebuffer percentage, fatal playback failures by device/ISP/title, sustainable bitrate, switch rate, edge saturation, byte hit ratio, shield collapse ratio, origin egress, rights-decision latency and grant denials. Percentiles need sample counts, and all SLOs need a named measurement window.

### 9. Executable contract

[`VideoPlaybackContracts.java`](VideoPlaybackContracts.java) encodes the four worked examples and boundary failures. Run:

```bash
java -ea study-guides/system-design/blueprints/VideoPlaybackContracts.java
```

The key invariant is subtle: newer progress sequence wins even if its position is smaller.

### 10. Current primary references

- [Netflix Open Connect overview and briefing paper](https://openconnect.netflix.com/en/) — content localization, embedded appliances and proactive delivery.
- [Apple: HTTP Live Streaming](https://developer.apple.com/streaming/) and [HLS Authoring Specification](https://developer.apple.com/documentation/http-live-streaming/hls-authoring-specification-for-apple-devices) — playlist-based adaptive delivery and authoring constraints.
- [RFC 8216: HTTP Live Streaming](https://www.rfc-editor.org/rfc/rfc8216) — protocol semantics for playlists and media segments.
- [RFC 9317: Operational Considerations for Streaming Media](https://www.rfc-editor.org/rfc/rfc9317) — player/network interactions and QoE operations.
- [AWS CloudFront private content](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/PrivateContent.html) and [signed URLs](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-signed-urls.html) — a concrete primary-source example of restricted origins and expiring signed delivery.

### 11. Source provenance and continuation

The confirmed Library source is `top_5_system_design_interview_blueprints.md`, revision 1, SHA-256 `10c3b8139474ea47f35e1fed6cb169b2728db48070f3264e1d23dfa776e9bf03`. This section promotes source lines 5–253 and adds explicit contracts, four worked examples, executable Java, failure tests, and refreshed primary references.

## Blueprint 2 — Real-time ride hailing

### 1. Define “real time” and the ownership boundary

A rider requests a priced trip, the system searches sufficiently fresh available drivers, one driver accepts, both parties observe ordered trip state, and completion triggers one logical payment. “Real time” is not zero latency: give each promise a measurable bound.

| Contract | Example target | Correctness rule |
|---|---:|---|
| driver location | emitted every 4 s; candidate age at decision ≤ 10 s | an older sequence never replaces a newer location; stale drivers are excluded |
| dispatch | accepted offer or explicit no-match within 8 s | a geo result is only a candidate; authoritative state grants ownership |
| offer | expires after 6 s | acceptance after expiry or for a superseded attempt loses |
| trip updates | ordered per trip; reconnect snapshot within 2 s | client applies only a version greater than its last applied version |
| completion/payment | asynchronous after durable trip completion | one stable idempotency key per completion version |

Separate three data classes because they have different durability and consistency needs:

- **location telemetry** is write-heavy, replaceable and TTL-bound;
- **driver/trip ownership** is small, durable and strongly coordinated within a market;
- **events** are durable and replayable for push, payment, support and analytics.

```mermaid
flowchart TB
  Apps["Rider and driver apps"] --> Edge["API and realtime gateways"]
  Edge --> Loc["Location ingest"] --> Geo[("TTL geo index")]
  Edge --> Ride["Ride command service"] --> Dispatch["Market dispatch owner"]
  Dispatch --> Geo
  Dispatch --> Auth[("Authoritative driver and trip state")]
  Auth --> Log[("Ordered trip event log")]
  Log --> Push["Realtime fanout"] --> Apps
  Log --> Pay["Payment and reconciliation"]
```

Partition first by market/city because trips are local. Route every command for one trip and its assigned driver to the same home market. Cross-region identity, configuration and analytics can remain global; dispatch ownership should not require a cross-ocean round trip.

### 2. Example 1 — capacity and expanding-cell search

At five million active drivers sending one GPS fix every four seconds:

\[
QPS_{location}=\frac{5{,}000{,}000}{4}=1.25\text{ million updates/s}
\]

If an event averages 180 bytes before replication and indexing, ingress is about 225 MB/s or 19.44 TB/day. Retaining every raw fix for seven days would be roughly 136 TB before replication and compression. The hot path should therefore keep only the latest useful fix per driver with a TTL; a stream/object-store pipeline can retain sampled or policy-approved history separately.

Map a pickup to a cell, inspect nearby cells, filter by freshness and availability, and expand only until the candidate budget or radius limit is met. For an ideal hexagonal disk, `1 + 3k(k + 1)` estimates 1, 7, 19 and 37 cells for rings 0–3. H3 traversal can encounter pentagonal distortion, so that formula is a capacity estimate—not an API cardinality promise.

```mermaid
flowchart TD
  P["Pickup cell: k=0"] --> F0{"Enough fresh candidates?"}
  F0 -->|yes| Rank["Route/ETA rank and cap offers"]
  F0 -->|no| R1["Add ring k=1"] --> F1{"Enough?"}
  F1 -->|yes| Rank
  F1 -->|no| RN["Expand within latency/radius budget"] --> Rank
```

Trace for pickup `p7` at decision time `12:00:10`:

| Candidate | Cell ring | Last fix | Driver state | Result |
|---|---:|---:|---|---|
| d11 | 0 | `12:00:04` | available | keep; age 6 s |
| d12 | 1 | `11:59:57` | available | reject; age 13 s |
| d13 | 1 | `12:00:07` | assigned | reject; geo index is stale |
| d14 | 2 | `12:00:05` | available | keep; then rank by route ETA |

Straight-line or cell distance narrows the set; road ETA makes the final ranking. Cap concurrent offers so one request does not fan out to hundreds of drivers.

### 3. Example 2 — out-of-order location must not move a driver backward

Mobile networks reorder and duplicate uploads. Give each driver session a monotonically increasing sequence and retain both device observation time and server receipt time. The write condition is “newer session/sequence,” not “arrived last.”

| Arrival | Fix `(session, seq, observedAt, cell)` | Stored result |
|---|---|---|
| first | `(s9, 40, 12:00:04, A)` | accept A |
| delayed | `(s9, 39, 12:00:03, B)` | ignore older sequence |
| duplicate | `(s9, 40, 12:00:04, A)` | idempotent no-op |
| new | `(s9, 41, 12:00:08, C)` | replace with C |

On a driver-app restart, issue a new server-authorized session/epoch; otherwise sequence `1` could lose forever to the previous session's `41`. Do not trust a handset clock alone for freshness. Reject implausible future timestamps, bound clock skew, and use server receipt time to enforce a conservative TTL.

The geo index is a disposable projection. Update the latest-location record and cell membership idempotently; periodic repair removes a driver from an old cell if a crash occurs between those operations. Dispatch still checks authoritative availability before offering.

### 4. Example 3 — the offer is not the assignment

Two riders can see the same available driver in a slightly stale geo index. Only one transactional compare-and-set may move that driver from `AVAILABLE` to `ASSIGNED` and bind the trip in the same commit.

```mermaid
sequenceDiagram
  participant A as Trip A dispatch
  participant B as Trip B dispatch
  participant S as Driver state
  participant D as Driver
  A->>D: offer A (attempt a7, expires 10:00:06)
  B->>D: offer B (attempt b3, expires 10:00:06)
  D->>A: accept a7
  D->>B: accept b3
  par competing commits
    A->>S: claim d9 if AVAILABLE/version 22
    B->>S: claim d9 if AVAILABLE/version 22
  end
  S-->>A: 1 row updated; commit trip A
  S-->>B: 0 rows; re-dispatch trip B
```

A representative relational claim is:

```sql
UPDATE driver_state
SET status = 'ASSIGNED', active_trip_id = :trip_id, version = version + 1
WHERE market_id = :market_id
  AND driver_id = :driver_id
  AND status = 'AVAILABLE'
  AND version = :expected_version
RETURNING version;
```

The trip binding and an outbox event belong in the same database transaction. A zero-row result is an ordinary conflict, not an error to paper over. The acceptance endpoint also validates rider/trip, driver, attempt ID and expiry, and uses the request idempotency key so a timeout retry returns the original result.

Critical trace: the database commits Trip A, but the response is lost. The driver retries `accept(a7)` with the same key. The server reads the stored outcome and returns Trip A; it does not try another claim. If Trip B retries, its stored conflict remains a conflict.

### 5. Example 4 — reconnect from snapshot plus ordered replay

WebSocket provides a bidirectional transport, not application-level exactly-once delivery or durable catch-up. Persist a monotonic trip version with every state transition; push is only a low-latency delivery path.

```mermaid
stateDiagram-v2
  [*] --> REQUESTED
  REQUESTED --> OFFERING
  OFFERING --> MATCHED: atomic driver claim
  MATCHED --> ARRIVING
  ARRIVING --> IN_PROGRESS
  IN_PROGRESS --> COMPLETED
  REQUESTED --> CANCELLED
  OFFERING --> CANCELLED
  MATCHED --> CANCELLED: policy permits
```

Suppose a rider has applied version 17, disconnects, and the server reaches version 20:

| Step | Server/client action | Outcome |
|---|---|---|
| 1 | server commits v18 `DRIVER_ARRIVING` and v19 `DRIVER_AT_PICKUP` | durable before push |
| 2 | client reconnects with `lastApplied=17` | gateway authenticates trip membership |
| 3 | API returns snapshot v20 `IN_PROGRESS` | client atomically installs v20 |
| 4 | delayed push v19 arrives | ignored because `19 <= 20` |
| 5 | push v21 `COMPLETED` arrives twice | first applies; duplicate is ignored |

Snapshot-first recovery bounds event retention and avoids a replay/snapshot race: obtain a snapshot version, then subscribe or fetch events strictly after that version. Partition trip events by `trip_id` if using a log whose ordering guarantee is per partition.

Completion records an outbox message with stable key `payment:{tripId}:v{completionVersion}`. The payment worker retries uncertain failures with that same key, stores provider outcome, and reconciles pending attempts. Broker delivery claims alone cannot make an external charge exactly once.

### 6. API, data model, and command rules

```http
POST /v1/rides                         Idempotency-Key: rider-generated UUID
GET  /v1/rides/{rideId}
POST /v1/drivers/{driverId}/locations  driverSession + sequence
POST /v1/offers/{offerId}/accept       Idempotency-Key: offer-attempt ID
POST /v1/rides/{rideId}/cancel         expected trip version
GET  /v1/rides/{rideId}/snapshot       returns trip version
```

| Record | Essential fields | Authority/retention |
|---|---|---|
| `DriverLocation` | driver, session, seq, lat/lon, cell, observed/received time | latest projection with short TTL |
| `DriverState` | market, driver, status, active trip, version | authoritative durable row |
| `Trip` | rider, driver, pickup/destination, quote revision, status, version | authoritative durable row |
| `OfferAttempt` | offer, trip, driver, attempt, expiry, result | durable through retry window |
| `TripEvent` | trip, version, type, payload, time | durable replay/audit log |
| `Outbox` | event ID, aggregate/version, payload, publish state | same commit as authoritative change |

Never expose precise driver coordinates to unrelated riders. Authenticate driver devices, authorize every trip subscription, limit location precision and retention, encrypt transport/storage, and audit privileged access.

### 7. Failure matrix and operational evidence

| Failure | Safe behavior | Evidence |
|---|---|---|
| location ingest delayed | exclude fixes beyond the freshness bound; widen search only within latency budget | candidate-age histogram and stale-exclusion count |
| geo projection loses membership | repair from latest-location records; authoritative state still blocks double assignment | projection drift sample and repair lag |
| dispatch dies after claim commit | retry reads idempotent outcome; outbox republishes committed match | unknown-outcome reconciliation age |
| hot downtown cell | split worker ownership, batch ingestion, cap offers and apply backpressure | per-cell QPS/skew and queue age |
| gateway fleet restarts | jitter reconnects, admit gradually, snapshot then resume | reconnect rate and recovery p95 |
| payment timeout | same idempotency key; mark pending and reconcile provider state | pending age and duplicate-charge invariant |
| market region impaired | protect active trips; stop new dispatch before accepting ownership unsafely | failover drill and active-trip recovery time |

Alert on location-ingest lag, candidate age, match latency p50/p95/p99, no-match rate, search-ring distribution, claim conflicts, expired accepts, trip-version gaps, reconnect recovery, outbox age and payment reconciliation age. A double-assigned driver or duplicate charge is a correctness incident even when latency SLOs are green.

### 8. Executable contract

[`RideHailingContracts.java`](RideHailingContracts.java) exercises cell-count estimation, stale/out-of-order location handling, single-winner assignment, ordered reconnect state and stable payment keys. Run:

```bash
java -ea study-guides/system-design/blueprints/RideHailingContracts.java
```

The tests deliberately model a lost response after commit: retrying the same offer attempt returns the same winning trip, while a competing attempt remains rejected.

### 9. Current primary references

- [H3 introduction](https://h3geo.org/docs/) and [grid traversal API](https://h3geo.org/docs/api/traversal/) — hierarchical cells, nearby-cell traversal and documented distortion limits.
- [Uber Engineering: H3 spatial index](https://www.uber.com/us/en/blog/h3/) — the source system's public origin and marketplace/geospatial motivation.
- [Redis `GEOSEARCH`](https://redis.io/docs/latest/commands/geosearch/) — one concrete proximity-index implementation; it does not replace authoritative driver state.
- [PostgreSQL `UPDATE`](https://www.postgresql.org/docs/current/sql-update.html) and [concurrency control](https://www.postgresql.org/docs/current/mvcc.html) — conditional row updates and concurrent transaction behavior.
- [Apache Kafka introduction](https://kafka.apache.org/documentation/) — ordering within a topic-partition; key trip events consistently if that guarantee is used.
- [RFC 6455: WebSocket](https://www.rfc-editor.org/rfc/rfc6455) — bidirectional transport protocol; replay and application ordering remain application responsibilities.
- [Stripe idempotent requests](https://docs.stripe.com/api/idempotent_requests) — a primary-source example of safe request retries with a stable idempotency key.

### 10. Source provenance and continuation

The confirmed Library source remains `top_5_system_design_interview_blueprints.md`, revision 1, SHA-256 `10c3b8139474ea47f35e1fed6cb169b2728db48070f3264e1d23dfa776e9bf03`. This section promotes exactly source lines 254–459 and adds measurable requirements, four worked traces, executable Java, failure evidence and primary-source verification refreshed on 2026-10-22. Source Blueprint 3 begins at line 460 and is promoted in the next section below. The five-blueprint collection is not complete.

## Blueprint 3 — Distributed financial transaction ledger

### 1. Make the correctness boundary explicit

The authoritative ledger accepts a money-moving command, writes an immutable balanced journal transaction, and returns one durable logical result for every idempotency scope/key. Balance and history reads may use projections, but a projection never becomes the authority for whether money moved. When certainty and availability conflict, an unknown write waits for recovery or reconciliation; it is not guessed into success or retried under a fresh identity.

| Contract | Required behavior | Rejected shortcut |
|---|---|---|
| amount | positive integer minor units or an exact decimal with declared scale; one currency per journal transaction | binary floating point |
| journal | at least two entries; signed sum is exactly zero; posted entries are immutable | updating a balance without durable entries |
| retry | unique `(scope, idempotencyKey)` stores request hash and canonical outcome | a new key after timeout |
| concurrency | debit availability and all journal/outbox rows commit in one local transaction | check-then-write in separate transactions |
| external rail | stable provider operation key; timeout becomes `UNKNOWN` until queried/reconciled | assuming timeout means failure |
| publication | journal and outbox commit together; consumers deduplicate event IDs | database write followed by best-effort publish |

Separate ownership so the central invariant stays small:

```mermaid
flowchart TB
  C["Client or merchant"] --> API["Transfer API"]
  API --> CMD["Command owner: idempotency and state"]
  CMD --> DB[("Transactional journal + outbox")]
  DB --> RELAY["Outbox relay"] --> BUS[("Durable event stream")]
  BUS --> BAL["Balance/history projection"]
  BUS --> HOOK["Webhook delivery"]
  BUS --> RECON["Reconciliation"]
  CMD --> RAIL["External payment rail"]
  RAIL --> RECON
```

The journal commit is the authority for internal transfers. A card/bank rail has its own authority, so orchestration records a durable attempt state and later reconciles the two systems instead of pretending they share one atomic database transaction.

### 2. Example 1 — balance a transfer, then derive the view

For a USD 12.50 transfer from merchant A to customer B, record signed minor units:

| Transaction | Account | Currency | Amount |
|---|---|---|---:|
| `txn_100` | `merchant:A` | USD | −1250 |
| `txn_100` | `customer:B` | USD | +1250 |
| invariant |  | USD | **0** |

Do not insert one row and hope the second insert succeeds. In one transaction, insert the transaction header, both entries, the idempotency outcome, and an outbox row; commit only after the balance and currency checks pass.

```sql
BEGIN;
INSERT INTO ledger_transaction(txn_id, scope, idem_key, request_hash, status)
VALUES (:txn, :scope, :key, :hash, 'POSTED');
INSERT INTO ledger_entry(txn_id, account_id, currency, amount_minor, entry_no)
VALUES (:txn, 'merchant:A', 'USD', -1250, 1),
       (:txn, 'customer:B', 'USD',  1250, 2);
INSERT INTO outbox(event_id, aggregate_id, aggregate_version, event_type, payload)
VALUES (:event, :txn, 1, 'LedgerPosted', :payload);
COMMIT;
```

An application check is useful, but a production design also needs a database-enforced posting boundary—for example a stored posting procedure, deferred constraint trigger, or staged transaction that cannot change to `POSTED` until its currency-local sum is zero. `BIGINT` arithmetic can overflow, so use checked arithmetic and declare maximum amounts; exact `NUMERIC(p,s)` is the alternative when fractional rules do not map cleanly to one minor-unit scale.

Balance is then a derived sum of posted entries. A cached balance of 8,750 minor units is repairable; the two immutable entries are the evidence.

### 3. Example 2 — a lost response replays one logical transfer

The client sends key `k-1`; the server commits `txn_100`, but the response is lost. The same request and key must return `txn_100` without another pair of entries or outbox event. Reusing `k-1` with amount 1,251 must fail because the stored request hash differs.

```mermaid
sequenceDiagram
  participant C as Client
  participant A as Transfer API
  participant D as Ledger DB
  C->>A: transfer k-1, hash H1
  A->>D: header + entries + outcome + outbox
  D-->>A: commit txn_100
  A--xC: response lost
  C->>A: retry k-1, hash H1
  A->>D: read unique scope/key
  D-->>A: canonical txn_100
  A-->>C: replay same outcome
  C->>A: k-1, hash H2
  A-->>C: 409 key/request mismatch
```

Store canonical request bytes or a stable versioned hash. A hash built from ad-hoc JSON serialization is unsafe because field order, omitted defaults and serializer upgrades can change it. Scope keys by merchant/account and retain them at least as long as clients are allowed to retry; document what happens after expiry.

### 4. Example 3 — concurrent debits need one serialized decision

Wallet W has 1,000 minor units. Transfers A and B each try to debit 700 after both read 1,000. If they perform separate check-then-insert transactions, both may post and the wallet becomes −400.

| Step | Transfer A | Transfer B |
|---|---|---|
| 1 | reads available 1000 | reads available 1000 |
| 2 | claims/post 700 | waits or receives serialization conflict |
| 3 | commits; available becomes 300 | retries, reads 300, rejects insufficient funds |

Viable implementations include a conditional account-state update with a version/available predicate, a row lock around the debit and journal posting, or a serializable transaction with bounded retry. Choose one and test it under contention. If a transfer locks two accounts, acquire locks in a stable global order to reduce deadlocks. Sharding must preserve the same ownership rule: route an account to a home shard, or introduce an explicit cross-shard protocol and accept its availability/latency cost.

```mermaid
stateDiagram-v2
  [*] --> Proposed
  Proposed --> Posted: atomic claim and balanced journal commit
  Proposed --> Rejected: insufficient funds or policy
  Posted --> Reversed: new compensating transaction
  Rejected --> [*]
  Reversed --> [*]
```

A reversal never edits or deletes the original entries. It posts a linked compensating transaction so both business history and the accounting trail remain explainable.

### 5. Example 4 — timeout to an external rail is `UNKNOWN`, not failed

Suppose internal operation `op_7` sends an authorization under provider key `rail:op_7`; the rail commits but the connection times out. Retrying with a new key can double-charge. Mark the attempt `UNKNOWN`, retry/query only with the stable key where supported, consume signed provider events idempotently, and reconcile until the external authority is known.

| Time | Internal state | Evidence/action |
|---|---|---|
| T0 | `CREATED` | durable operation and provider key |
| T1 | `SENT` | request attempt recorded |
| T2 | `UNKNOWN` | timeout; no success/failure assumption |
| T3 | `UNKNOWN` | duplicate webhook event ID ignored |
| T4 | `SUCCEEDED` | provider query/settlement record confirms charge |
| T5 | projected | one inbox event updates balance and merchant webhook |

```mermaid
flowchart LR
  U["UNKNOWN attempt"] --> Q["Query by stable provider key"]
  U --> W["Verify and deduplicate webhook"]
  U --> S["Compare settlement file"]
  Q --> R{"Authoritative result?"}
  W --> R
  S --> R
  R -->|yes| F["Finalize succeeded or failed"]
  R -->|no| U
```

The outbox relay may publish more than once. Every downstream projection therefore keeps an inbox/processed-event record in the same local transaction as its update. Replay from the immutable stream must reproduce the same balance; periodically compare journal-derived totals, projections and provider settlement records, and route differences to an explicit exception queue.

### 6. Capacity, partitioning, and read paths

At 25,000 transfers/s with four journal entries per transfer, the journal receives 100,000 entry inserts/s. If a stored entry plus indexes averages 320 bytes, raw entry growth is about 32 MB/s, 2.76 TB/day, before replication, outbox, backups and transaction metadata. This arithmetic requires partitioning and lifecycle plans, but it does not justify weakening the invariant.

Partition by a stable ownership key and keep an account's conflicting commands at one authority. Hot merchant accounts may require sub-ledgers with an explicit aggregation invariant; simply hashing every transaction can scatter one account's spend decisions and reintroduce overspending. Serve statements and dashboards from replayable projections, with a visible freshness/version marker; use the journal for authoritative investigation and repair.

Core records:

```text
LedgerTransaction(txn_id, scope, idem_key, request_hash, type, state, created_at)
LedgerEntry(txn_id, entry_no, account_id, currency, amount_minor, created_at)
AccountState(account_id, currency, available_minor, version)
Outbox(event_id, aggregate_id, aggregate_version, payload, published_at)
Inbox(consumer, event_id, processed_at)
RailAttempt(operation_id, provider_key, state, provider_reference, version)
ReconciliationException(id, source, expected, observed, state)
```

### 7. Failure matrix and operational evidence

| Failure | Safe behavior | Evidence |
|---|---|---|
| response lost after commit | replay canonical outcome for same key/hash | replay count; duplicate-effect invariant |
| outbox relay crashes after publish | publish again; consumer inbox deduplicates | oldest unpublished age; duplicate deliveries |
| projection falls behind | journal remains authoritative; expose staleness | consumer lag and projection version |
| database primary fails mid-write | transaction is committed or absent; recover before deciding | recovery drill and uncertain-write count |
| processor timeout | `UNKNOWN`; same provider key; query/reconcile | unknown-attempt age histogram |
| settlement disagrees | preserve both facts; open exception | break count/value by rail and age |
| hot account saturates shard | admission control and ordered queue; no unsafe fail-open | per-account skew, conflict/retry rate |

Alert immediately on any posted transaction whose entries do not sum to zero, mixed-currency posting, duplicate logical effect, negative balance where disallowed, journal/projection checksum divergence, or one provider key linked to multiple operations. Track posting p50/p95/p99, serialization retries, idempotency replays/mismatches, outbox age, consumer lag, unknown rail age, reconciliation breaks and recovery objectives. Monetary totals need currency-local aggregation; never sum USD and EUR into one number.

### 8. Executable contract

[`FinancialLedgerContracts.java`](FinancialLedgerContracts.java) exercises balanced posting, replay under a stable idempotency key, key/request mismatch rejection, a contested debit, stable external-rail identity, unknown-state reconciliation, consumer deduplication, mixed-currency rejection and invalid amounts. Run:

```bash
java -ea study-guides/system-design/blueprints/FinancialLedgerContracts.java
```

The synchronized in-memory method models one database transaction for teaching; it is not a claim that JVM locking coordinates a fleet.

### 9. Current primary references

- [Stripe idempotent requests](https://docs.stripe.com/api/idempotent_requests) and [advanced error handling](https://docs.stripe.com/error-low-level) — stable retry identity and indeterminate outcomes after network/server errors.
- [Stripe webhook endpoint guidance](https://docs.stripe.com/events/manage-webhook-endpoints) — duplicate events and consumer-side event tracking.
- [PostgreSQL numeric types](https://www.postgresql.org/docs/current/datatype-numeric.html) — exact numeric types and precision/scale behavior.
- [PostgreSQL transaction isolation](https://www.postgresql.org/docs/current/transaction-iso.html) — serialization anomalies, guarantees and retry-relevant failures.
- [AWS Prescriptive Guidance: transactional outbox](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html) — atomic database/outbox writes plus duplicate-aware consumers.

### 10. Source provenance and continuation

The confirmed Library source remains `top_5_system_design_interview_blueprints.md`, revision 1, SHA-256 `10c3b8139474ea47f35e1fed6cb169b2728db48070f3264e1d23dfa776e9bf03`. This bounded pass promotes exactly source lines 460–680 and adds measurable contracts, four worked examples, executable invariants, failure evidence and primary-source verification refreshed on 2026-10-23. Source Blueprint 4 begins at line 681 and remains queued after fair domain rotation. The five-blueprint collection is not complete.

## Blueprint 4 — Large-scale, polite web crawler

### 1. Define the safety and ownership contracts first

The crawler discovers HTTP(S) URLs from approved seeds, sitemaps and fetched documents; normalizes fetch identity; consults the applicable robots policy; schedules fairly; fetches within strict network and byte limits; stores evidence; extracts links; and chooses a bounded recrawl time. Discovery is not permission to fetch, a fetched page is not automatically indexable, and `robots.txt` is not an authorization mechanism.

| Contract | Required behavior | Unsafe shortcut |
|---|---|---|
| URL identity | resolve against the document base; lowercase scheme/host; remove default port, dot segments and fragment; preserve unknown query semantics | sort/drop every query parameter |
| origin ownership | one scheduler owner coordinates `(scheme, host, effective port)` delay and concurrency | partition only by full-URL hash |
| robots | cache policy by origin and user-agent; longest matching rule wins; refresh with bounded staleness | fetch robots for every page or treat it as access control |
| fetch safety | allow HTTP(S), validate the address actually connected to, block private/link-local/loopback destinations, cap redirects/time/bytes | validate only the first hostname before redirects |
| delivery | leased work is at-least-once; content storage and downstream parsing are idempotent | assume a queue makes fetching exactly once |
| recrawl | base the next time on observed change, freshness demand, cache validators and failures | fixed interval for every URL |

Align the partition key with the politeness invariant:

```mermaid
flowchart TB
  Seeds["Seeds, sitemaps, extracted links"] --> Norm["Resolve and canonicalize"]
  Norm --> Seen[("Durable seen-URL store")]
  Seen --> Priority["Priority and recrawl queues"]
  Priority --> Owner["Origin-owned scheduler"]
  Owner --> Fetch["Bounded fetcher"]
  Fetch --> Raw[("Raw response and fetch evidence")]
  Raw --> Parse["Parser and link extractor"] --> Norm
  Raw --> Index["Downstream index or archive"]
```

The durable seen set gives exact admission decisions. A Bloom filter can protect it from obvious misses but must not silently become the only record if false-positive URL loss is unacceptable.

### 2. Example 1 — canonicalization and deduplication without changing meaning

Resolve relative links before canonicalizing. For the base `https://Example.COM/a/b/`, the reference `../c#section` becomes `https://example.com/a/c`: the host is case-insensitive, the dot segment is removed, and the fragment is not part of HTTP fetch identity. `https://example.com:443/a/c` maps to the same key because 443 is the HTTPS default.

| Candidate | Canonical fetch key | Decision |
|---|---|---|
| `https://Example.COM:443/a/../b#top` | `https://example.com/b` | admit once |
| `https://example.com/b#comments` | `https://example.com/b` | duplicate fetch identity |
| `https://example.com/?b=2&a=1` | unchanged query | distinct unless site-specific evidence says otherwise |
| `https://example.com/?a=1&b=2` | unchanged query | distinct by conservative default |

Do not decode reserved characters, collapse meaningful repeated slashes, or globally sort/delete query parameters. RFC 3986 supplies generic equivalences; application-specific query equivalence needs an explicit host policy backed by evidence. Store the original discovery URL and source edge beside the canonical key so a bad normalization rule can be audited and replayed.

```mermaid
flowchart LR
  Link["Raw link + base URI"] --> Resolve["Resolve reference"]
  Resolve --> Generic["Safe generic normalization"]
  Generic --> Key["Canonical fetch key"]
  Key --> Exact{"Seen already?"}
  Exact -->|no| Admit["Persist then enqueue"]
  Exact -->|yes| Edge["Record discovery edge only"]
```

### 3. Example 2 — priority cannot bypass per-origin politeness

Assume global priorities put two `example.com` URLs ahead of all other work. The scheduler allows one concurrent request per origin and a 1,000 ms minimum delay:

| Time | Event | Origin state/result |
|---:|---|---|
| 1,000 ms | lease `/one` for 5 s | in-flight = 1 |
| 1,001 ms | try `/two` | denied; concurrency already consumed |
| 1,200 ms | `/one` completes 200 | next allowed = 2,200 ms |
| 2,199 ms | try `/two` | denied; delay not elapsed |
| 2,200 ms | lease `/two` | allowed |

Global front queues rank importance/freshness; origin queues enforce eligibility. A worker leases a bounded batch and acknowledges after the raw response plus fetch record are durable. If it dies, lease expiry makes the item visible again, so duplicate fetches are expected and downstream writes use stable fetch identities.

At 250,000 fetches/s and 80 KiB average compressed bodies, ingress is about 20.48 GB/s or 1.77 PB/day before replication and metadata. If only 8% of pages change and policy retains unchanged bodies by reference, the changed-body component is about 142 TB/day. Write the retention horizon, replication factor and compression basis next to every storage estimate.

### 4. Example 3 — robots and overload responses modify eligibility

RFC 9309 scopes robots policy to the service that serves `/robots.txt`; cache it using normal HTTP cache controls, follow the specified redirect/error behavior, and apply the most specific matching rule for the crawler's user-agent. Given:

```text
Disallow: /private
Allow:    /private/public
```

`/private/report` is ineligible while `/private/public/index.html` is eligible because the longer matching path wins. A robots fetch failure needs an explicit state and policy; it must not be confused with an empty allow-all file.

When an eligible fetch returns 429 or 503 with `Retry-After: 10`, the origin owner moves `next_allowed_fetch_at` at least ten seconds forward. Without the header it applies capped exponential backoff with jitter. A queue retry that ignores this shared state simply shifts overload between workers.

```mermaid
stateDiagram-v2
  [*] --> Eligible: robots allow and origin ready
  Eligible --> Leased: capacity token acquired
  Leased --> Cooling: success plus minimum delay
  Leased --> Backoff: 429, 503, timeout or DNS failure
  Backoff --> Eligible: retry deadline reached
  Cooling --> Eligible: next-allowed reached
  Leased --> Blocked: robots refresh now disallows
```

Robots rules do not protect secrets: an operator must authenticate private resources. Likewise, a crawler may store a public response yet exclude it from indexing because of separate content directives or product policy.

### 5. Example 4 — safe fetch, conditional recrawl and redirect trace

An internet crawler is an SSRF-capable system by design. Isolate it from control-plane credentials and private networks; allow only HTTP(S); resolve A and AAAA records; reject loopback, private, link-local, multicast and platform metadata destinations; and ensure the validated address is the one the client connects to. Repeat the full check after every redirect, cap redirect count, and reject protocol changes outside policy.

Trace a recrawl of `https://news.example/story`:

| Step | Request/evidence | Safe decision |
|---|---|---|
| 1 | prior record has ETag `"v7"`, body hash H7 | send `If-None-Match: "v7"` |
| 2 | origin returns 304 | retain H7; update fetch evidence, not body bytes |
| 3 | next response redirects to `http://169.254.169.254/...` | reject after destination validation; do not follow |
| 4 | later 200 has ETag `"v8"`, hash H8 | store immutable body, parse once by `(URL, H8)` |
| 5 | H8 differs from H7 | shorten the next base interval; failures multiply it with a cap |

HTTP validators reduce transferred bytes, not scheduling work: a 304 still consumes origin capacity. Treat sitemap `lastmod` as a hint and measure its reliability per source. JavaScript rendering is a separate, much smaller budget selected after cheap HTML signals; renderer workers inherit the same destination and response limits.

### 6. Records, APIs and idempotent stages

```text
Url(url_hash, canonical_url, origin_id, first_seen, last_seen, priority)
Origin(origin_id, robots_version, next_allowed, max_concurrency, error_score)
Frontier(url_hash, due_at, priority, lease_owner, lease_expiry, attempt)
Fetch(fetch_id, url_hash, requested_at, final_url, status, validators, body_hash, bytes)
Content(body_hash, object_key, media_type, length)
LinkEdge(source_fetch_id, target_url_hash, relation)
```

`POST /discoveries` accepts a stable source-fetch ID plus extracted references; the normalizer inserts URL and edge records idempotently. Fetchers lease with compare-and-set ownership. The content object key is its cryptographic hash, so a retry cannot create a second logical body. Parsing deduplicates on `(parserVersion, bodyHash)` while index publication uses `(urlHash, fetchVersion)`.

Partition frontier ownership by origin hash, then keep multiple priority/time buckets inside that owner. Hot origins scale only up to their declared concurrency; adding workers must never multiply politeness capacity. Separate scheduler lag, network time, parser lag and indexing lag so one healthy aggregate does not hide a broken stage.

### 7. Failure matrix and operational evidence

| Failure | Safe behavior | Evidence |
|---|---|---|
| worker dies after fetch | lease expires; refetch allowed; content/parser writes deduplicate | expired lease age and duplicate-fetch rate |
| robots policy stale | revalidate; retain version used for every decision | robots age, status and blocked-fetch audit |
| DNS rebinding or redirect to private IP | bind connection to validated public address; validate every hop | rejection reason, resolved address and redirect chain |
| origin sends endless/huge body | header/read deadline, compressed and expanded byte caps, abort | bytes rejected and timeout phase |
| 429/503 spike | honor `Retry-After`, reduce origin concurrency, jittered backoff | per-origin status rate and next-allowed time |
| poison parse/render input | sandbox, CPU/memory/depth deadline, quarantine sample | parser version, failure fingerprint and queue age |
| frontier shard unavailable | stop issuing its host leases until ownership is fenced | epoch/lease conflicts and recovery time |

Operate on pages/s and bytes/s, but also per-origin concurrency/delay violations, robots decisions, frontier due-time lag, lease expiries, redirect blocks, status families, DNS/TLS/connect/header/body latency, compressed/expanded bytes, content-change rate, duplicate URL/body rate, render admission and end-to-end freshness percentiles. Any private-address connection or robots-disallowed fetch is a correctness incident.

### 8. Executable contract

[`WebCrawlerContracts.java`](WebCrawlerContracts.java) exercises conservative URL identity, exact admission deduplication, longest-match robots decisions, priority ordering, origin-scoped concurrency/delay, `Retry-After`, public-address boundaries and adaptive recrawl delay. Run:

```bash
java -ea study-guides/system-design/blueprints/WebCrawlerContracts.java
```

The address helper intentionally validates numeric literals only. Production code must resolve every hostname, validate all A/AAAA answers, and bind the HTTP connection to a validated address; a separate preflight lookup is not sufficient against DNS rebinding.

### 9. Current primary references

- [RFC 9309: Robots Exclusion Protocol](https://www.rfc-editor.org/rfc/rfc9309) — matching, caching, redirects and unavailable-policy behavior; robots is not access control.
- [RFC 3986: URI Generic Syntax](https://www.rfc-editor.org/rfc/rfc3986) — reference resolution, dot-segment removal, fragments and scheme-based normalization.
- [RFC 9110: HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110) and [RFC 9111: HTTP Caching](https://www.rfc-editor.org/rfc/rfc9111) — `Retry-After`, validators, 304 responses and cache revalidation.
- [Google Crawling Infrastructure: crawl budget](https://developers.google.com/crawling/docs/crawl-budget) — host capacity and demand/freshness as separate scheduling inputs.
- [OWASP SSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html) — address validation, DNS rebinding and binding the connection to the validated destination.
- [Java SE 17 `URI`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URI.html) — dependency-free reference resolution and path normalization used by the executable teaching contract.

### 10. Source provenance and continuation

The confirmed Library source remains `top_5_system_design_interview_blueprints.md`, revision 1, SHA-256 `10c3b8139474ea47f35e1fed6cb169b2728db48070f3264e1d23dfa776e9bf03`. This bounded pass promotes exactly source lines 681–902 and adds explicit safety contracts, four worked traces, executable Java, capacity arithmetic, failure evidence and primary-source verification refreshed on 2026-10-23. Source Blueprint 5 begins at line 903 and remains queued. The five-blueprint collection is not complete.

## Blueprint 5 — Distributed unique ID generator

### 1. Choose the contract before the bit layout

“Unique” does not imply “gapless,” “unguessable,” “causally ordered,” or even “strictly increasing across machines.” State each property independently:

| Property | Contract for this design | Deliberate non-guarantee |
|---|---|---|
| uniqueness | no two live generators may emit the same 64-bit value in the deployment and epoch | no recovery by checking a database after emission |
| latency and availability | local generation, p99 below 1 ms, no coordination on the hot path | a suspect worker may become unavailable to preserve uniqueness |
| ordering | increasing within one worker while its clock and lease are valid; usually time-sortable across workers | numeric order is not causality and may differ across skewed clocks |
| capacity | at least 8 million IDs/s deployment-wide with 2× burst headroom | no infinite same-millisecond burst |
| representation | non-negative signed `BIGINT`, 63 usable bits | not a secret or authorization capability |
| deployment | multiple regions; worker identities are exclusive, leased and fenced | a disconnected worker cannot generate after lease expiry |

Strict global consecutive order is excluded: it would put consensus or a single sequencer on every generation path. If regulation requires gapless document numbers, solve that narrower accounting workflow transactionally instead of changing a general object-ID service.

```mermaid
flowchart TB
  C["Lease coordinator<br/>strong exclusive slots"] -->|"worker 17, generation 8, expiry"| W1["Generator 17"]
  C -->|"worker 18, generation 3, expiry"| W2["Generator 18"]
  A["Application instances"] --> W1
  A --> W2
  W1 --> D["Databases and event logs"]
  W2 --> D
```

The coordinator is off the per-ID path, but it remains part of the safety boundary. Every generator stops before its lease expires if it cannot renew.

### 2. Example 1 — prove the bit budget and decode one ID

Use a custom epoch and reserve the signed `long` sign bit:

```text
| 1 zero sign bit | 41 elapsed milliseconds | 10 worker | 12 sequence |
```

```mermaid
flowchart LR
  T["41-bit elapsed ms"] --> P["shift left 22"]
  W["10-bit worker"] --> Q["shift left 12"]
  S["12-bit sequence"] --> O["bitwise OR"]
  P --> O
  Q --> O
```

The limits follow from the fields, not from a product claim:

- `2^41` milliseconds is about 69.7 years from the custom epoch. Record the epoch and projected exhaustion date as configuration evidence.
- `2^10 = 1,024` simultaneously assignable worker slots.
- `2^12 = 4,096` IDs per worker per millisecond, or 4.096 million sequence positions/s/worker.
- The largest packed value is `Long.MAX_VALUE`; no valid ID is negative.

For custom-epoch elapsed time `1,000`, worker `17`, sequence `3`:

```text
(1,000 << 22) | (17 << 12) | 3
= 4,194,304,000 | 69,632 | 3
= 4,194,373,635
```

Decoding reverses the shifts and masks to `(elapsed=1,000, worker=17, sequence=3)`. At a measured 8 million IDs/s, even 100 active workers average only 80,000/s each, or 80 IDs/ms; the 4,096/ms field gives 51.2× average headroom. Validate p99 bursts because averages do not prove that a single hot worker cannot saturate.

### 3. Example 2 — same-tick overflow and rollback fail closed

A generator keeps `(lastTimestamp, sequence)` under one synchronization boundary. New milliseconds reset sequence to zero; equal milliseconds increment it. The 4,097th request in one millisecond must wait for a later tick or return backpressure—it must never wrap to zero.

```mermaid
stateDiagram-v2
  [*] --> Ready
  Ready --> Emit: time advances, sequence = 0
  Emit --> Emit: same time, increment sequence
  Emit --> Saturated: sequence would exceed 4095
  Saturated --> Emit: later millisecond
  Emit --> Quarantined: clock moves backward or lease invalid
  Quarantined --> Ready: new fenced identity and safe time
```

Trace one worker at millisecond `t`:

| Observation | State before | Decision | Safety reason |
|---|---|---|---|
| first call at `t` | last time `t-1` | emit sequence 0 | new timestamp tuple |
| 4,096th call at `t` | sequence 4,094 | emit sequence 4,095 | last value in field |
| 4,097th call at `t` | sequence 4,095 | block/reject; do not mutate | wrapping would duplicate sequence 0 |
| clock reports `t-5` | last time `t` | reject and fail health | a prior `(time, worker, sequence)` could recur |
| clock reaches `t+1` with valid lease | quarantined or saturated | policy-controlled recovery, sequence 0 | tuple moves forward |

Do not silently pin the timestamp forever: that borrows finite future capacity and makes encoded time dishonest. A bounded small-rollback policy may wait, but a large rollback or restart without durable last-time evidence should quarantine the worker, acquire a newly fenced generation, or remain unavailable. A monotonic clock measures durations; it cannot by itself supply the wall-clock field persisted in the ID.

### 4. Example 3 — lease loss and worker reuse need fencing

Worker number alone is not enough. Suppose process A holds slot 17, generation 8 through `12:00:30`. A partition prevents renewal; the coordinator later grants slot 17, generation 9 to process B. Process A must stop before the old expiry. Merely reconnecting and observing “slot 17 exists” cannot reactivate generation 8.

```mermaid
sequenceDiagram
  participant A as Old worker A
  participant C as Coordinator
  participant B as Replacement B
  A->>C: acquire slot 17
  C-->>A: generation 8, expires 12:00:30
  Note over A,C: partition; renewal unavailable
  C->>B: after expiry, slot 17 generation 9
  A--xA: local deadline reached; stop
  B->>B: generate only with generation 9
```

The generation token does not fit in the sample 64-bit ID, so its job is to prevent overlapping use, not distinguish two overlapping workers after the fact. Persist the lease generation beside generator state, compare it on every renewal/restart, budget clock uncertainty and network delay into a conservative local stop deadline, and alert on any conflicting ownership observation. With etcd, an ownership key can be attached to a lease and disappears when that lease expires; the application still owns the stop-before-expiry and stale-generation checks.

### 5. Example 4 — compare sequence, ranges, Snowflake and UUIDv7

Different contracts justify different mechanisms:

| Mechanism | Worked trace | Strong point | Cost or trap |
|---|---|---|---|
| database sequence | transaction gets 501, aborts; next caller safely gets 502 | atomic distinct values inside one database | gaps are normal; DB call and regional authority are on the path |
| range/Hi-Lo | A reserves `[1,000,000, 1,001,000)`; crashes after 37 IDs; B receives the next non-overlapping range | one coordination call amortized across many IDs | unused 963-value gap; allocator availability and durable non-overlap matter |
| Snowflake-like 64-bit | `(time, worker, sequence)` generated locally under a live fenced lease | compact, high throughput, approximately ordered | clock rollback, worker reuse and bit exhaustion are correctness events |
| UUIDv7 | 48-bit Unix-millisecond timestamp plus version/variant and random/counter fields | standardized 128-bit time-ordered format; no small global worker registry required | larger indexes; per-node monotonicity and trustworthy randomness still need design |

For UUIDv7, two IDs created at the same Unix millisecond can put an incrementing value in the high random bits to preserve local ordering while retaining sufficient random entropy. RFC 9562 requires handling counter rollover without knowingly returning a duplicate and warns that UUIDs are not security capabilities. Prefer UUIDv7 when 128 bits and probabilistic collision resistance are acceptable; prefer a fenced Snowflake layout when compact 64-bit storage is a hard requirement.

```mermaid
flowchart TD
  N{"Need 64 bits?"} -->|yes| O{"Can lease worker slots safely?"}
  O -->|yes| S["Fenced Snowflake-like ID"]
  O -->|no| R["Coordinated sequence or ranges"]
  N -->|no| U["RFC 9562 UUIDv7"]
```

### 6. API, state and storage contract

Prefer an in-process library or same-host sidecar so ordinary generation has no network hop. If a remote service is unavoidable, expose bounded batches such as `POST /v1/id-batches {"count": 1000}` and make the returned batch immutable; a request retry needs an idempotency key or may intentionally allocate a second, disjoint batch.

```text
WorkerLease(slot, generation, holder, issued_at, expires_at, status)
GeneratorState(slot, generation, custom_epoch, last_timestamp, sequence, persisted_at)
RangeAllocation(range_start, range_end, holder, allocation_id, allocated_at)
```

The lease store is authoritative for slot ownership. Local generator state is authoritative for the last tuple emitted by that process and must be durably restored if identity can survive a restart. Databases store IDs as signed 64-bit integers without converting them through floating-point JSON; JavaScript clients should use strings or `BigInt` because ordinary `Number` cannot exactly represent every 63-bit integer.

Partition application data independently of the embedded worker or time bits. A monotonically increasing key improves B-tree locality but may create a hot right edge or range shard. Hash/bucket the partition key when write distribution matters, while retaining the original ID for identity and approximate time inspection.

### 7. Failure matrix and operational evidence

| Failure | Safe behavior | Required evidence |
|---|---|---|
| sequence field exhausted in one millisecond | wait/reject until a later tick; never wrap | saturation count, wait time and hottest worker |
| clock moves backward | quarantine or bounded wait; never reuse a prior tuple | rollback magnitude, source and last emitted time |
| lease renewal times out | stop before conservative local expiry | lease generation, TTL margin and stop timestamp |
| slot is reused | only new generation may start; old holder remains fenced | ownership audit and conflicting-holder alarm |
| restart loses last timestamp | acquire new identity/generation or fail | durable-state version and boot decision |
| custom epoch overflows | reject before packing | years remaining and maximum observed timestamp |
| duplicate detector fires | page immediately, stop implicated generators, preserve samples | both decoded tuples, host/lease evidence and first-seen stores |

Track IDs/s and p99 generation latency by worker, same-tick sequence high-water mark, saturation/backpressure, clock offset/rollback, lease TTL margin and renew failures, active slots, generation changes, epoch consumption and sampled duplicate checks. Test concurrent callers, exact boundaries 0/4,095/4,096, rollback sizes, restart in the same millisecond, partition through lease expiry, slot reuse, invalid worker 1,024, and the last 41-bit timestamp.

### 8. Executable contract

[`DistributedIdContracts.java`](DistributedIdContracts.java) packs and decodes the 41/10/12 layout, proves 4,096 unique same-tick values, rejects overflow, rollback, expired/stale leases and invalid bit fields, demonstrates disjoint range allocation, and constructs/validates deterministic RFC 9562 UUIDv7 fields. Run:

```bash
java -ea study-guides/system-design/blueprints/DistributedIdContracts.java
```

The UUID helper accepts preselected random/counter bits to keep the test deterministic. Production UUIDv7 generation must use a trustworthy random source and a counter/rollback policy appropriate to its concurrency and threat model.

### 9. Current primary references

- [X Engineering: Announcing Snowflake](https://blog.x.com/engineering/en_us/a/2010/announcing-snowflake) — the original service decomposes IDs into time, worker and sequence fields and uses ZooKeeper to select worker numbers.
- [RFC 9562: UUIDs](https://www.rfc-editor.org/rfc/rfc9562) — UUIDv7 layout, batch monotonicity, counter rollover, distributed generation, randomness and security limits.
- [PostgreSQL 18 sequence functions](https://www.postgresql.org/docs/current/functions-sequence.html) — `nextval` is atomic and multiuser-safe, but aborted transactions and crashes can leave gaps.
- [etcd 3.6 concurrency API](https://etcd.io/docs/v3.6/dev-guide/api_concurrency_reference_v3/) — lock ownership is attached to a lease and ends when that lease expires or is revoked.
- [Java SE 17 `Clock`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Clock.html) — injectable, testable wall-clock access; clock implementations do not guarantee perfect real-time accuracy.

### 10. Source provenance and continuation

The confirmed Library source remains `top_5_system_design_interview_blueprints.md`, revision 1, SHA-256 `10c3b8139474ea47f35e1fed6cb169b2728db48070f3264e1d23dfa776e9bf03`. This bounded pass promotes exactly source lines 903–1125 and deepens them with explicit contracts, four worked examples, four failure/decision diagrams, executable Java and primary-source verification refreshed on 2026-10-23. All five named source blueprints are now promoted, but the source's cross-question and practice supplement at lines 1127–1264 remains unprocessed and the wider intake/backlog is not complete.

## Cross-blueprint design defense — primitives, proof and failure drills

### 1. Answer in an order that exposes assumptions

The source's five designs reuse the same forces: authority, partition locality, idempotency, backpressure, derived views and safe degradation. A strong interview answer does not start with a cloud-product diagram. It moves through a falsifiable sequence:

1. **Clarify the operation.** Name the user-visible flow, scale, latency, durability, ordering and geographic boundary.
2. **Calculate one limiting resource.** Convert peak demand to an absolute rate, bandwidth, storage or concurrent-work number.
3. **Declare authority.** Identify the record that may decide the invariant and label caches, indexes, logs and projections as derived.
4. **Localize the invariant.** Choose a partition key that keeps the critical decision local, then state the skew and resharding plan.
5. **Separate write and read paths.** The authoritative write path and cheap read path usually have different stores and failure behavior.
6. **Walk one failure before naming products.** Cover timeout-after-commit, duplicate delivery, overload, stale projection, hot partition and regional loss.
7. **Attach evidence.** Give the SLO, queue-age limit, invariant alarm, reconciliation signal and capacity headroom that prove the design works.

```mermaid
flowchart TD
  C["Clarify operation and SLO"] --> E["Estimate limiting resource"]
  E --> A["Declare authority and derived state"]
  A --> P["Choose partition and transaction boundary"]
  P --> F["Trace timeout, overload and region loss"]
  F --> M["Name evidence, then map products"]
```

The ordering matters. If a candidate selects Kafka, Redis or a globally distributed database before defining the invariant, there is no way to evaluate whether the choice preserves the required semantics.

| Design | Authoritative decision | Useful derived state | Safe degradation |
|---|---|---|---|
| video | entitlement and published asset revision | CDN copies, catalog/search rows, QoE aggregates | reduce bitrate/features; never bypass entitlement |
| ride hailing | one atomic driver assignment/version | geospatial candidates, ETA, connection routing | widen ETA or reject new rides; never double-assign |
| ledger | balanced immutable journal transaction | balance/history projection, search, notifications | show pending or reject; never guess a money outcome |
| crawler | host-scoped eligibility plus durable frontier state | Bloom filter, parsed/indexed documents | reduce crawl rate/rendering; preserve robots and safety |
| ID generator | live fenced worker/range ownership plus local tuple state | decoded timestamp and monitoring samples | stop stale worker; never reuse a tuple |

### 2. Example 1 — capacity plus queue age reveals hidden overload

Assume a regional event consumer normally receives and processes 40,000 events/s. A release causes 80,000 events/s for 30 seconds. The queue can hold 1,000,000 events.

```text
excess rate = 80,000 - 40,000 = 40,000 events/s
backlog     = 40,000 * 30 = 1,200,000 events
```

The queue overflows before the burst ends. Even if storage were expanded, capacity is not repaired—it is delayed. After the burst, suppose consumers can run at 90,000/s while arrivals return to 80,000/s:

```text
spare drain rate = 90,000 - 80,000 = 10,000 events/s
drain time       = 1,200,000 / 10,000 = 120 seconds
```

If the freshness SLO is 10 seconds, a 1.2-million-item durable queue is still an SLO failure. The design needs bounded admission, producer slowdown, priority shedding, more service capacity, or a deliberately lossy telemetry path. It must not apply the same loss policy to ledger postings or driver assignments.

```mermaid
flowchart LR
  IN["80k/s arrivals"] --> Q["Queue: cap 1.0M"]
  Q --> OUT["40k/s service"]
  Q --> AGE{"Queue age within SLO?"}
  AGE -->|yes| KEEP["Continue and monitor"]
  AGE -->|no| SHED["Throttle, shed or add bounded capacity"]
```

Google's SRE guidance makes the semantic point explicit: an overloaded backend should accept only what it can process and reject the rest gracefully. Degraded responses can be correct for video/search-like reads; correctness-sensitive writes need fail-closed admission instead of an invented result.

Interview trace:

| Step | Evidence | Decision |
|---|---:|---|
| normal | arrival 40k/s, service 40k/s, queue age near zero | healthy but no burst headroom |
| burst +10 s | 400k queued | admit only if age/capacity budgets remain |
| burst +25 s | 1.0M queued | capacity boundary reached; shed or throttle now |
| recovery | 10k/s spare | 120 s to drain full burst; freshness still violated |

### 3. Example 2 — a fast projection proposes; authority disposes

A rider's geo index says driver `D7` is available in cell `8a2a...`. Two dispatch workers read the same candidate. The index is allowed to be stale because it answers “who might be nearby?”; it must not answer “who owns the driver?”

```text
projection: cell -> {D7, D9, D11}

worker A: compare-and-set D7 AVAILABLE/v41 -> ASSIGNED trip T1/v42  [wins]
worker B: compare-and-set D7 AVAILABLE/v41 -> ASSIGNED trip T2/v42  [0 rows]
worker B: discard candidate and continue
```

The same reasoning crosses domains:

- a cached balance can serve a clearly labeled read, but only the ledger journal authorizes a debit;
- a Bloom filter can avoid many crawler lookups, but its false-positive behavior cannot silently become an exact legal/safety decision;
- an edge can serve a signed video grant, but it cannot manufacture entitlement;
- a monitoring duplicate detector can stop an ID worker, but it does not allocate worker ownership.

```mermaid
sequenceDiagram
  participant W1 as Dispatch A
  participant G as Geo projection
  participant S as Driver authority
  participant W2 as Dispatch B
  W1->>G: nearby candidates
  W2->>G: nearby candidates
  G-->>W1: D7 available (possibly stale)
  G-->>W2: D7 available (possibly stale)
  W1->>S: claim D7 if AVAILABLE/v41
  S-->>W1: success, T1/v42
  W2->>S: claim D7 if AVAILABLE/v41
  S-->>W2: conflict; choose another driver
```

This is the reusable sentence to remember: **the index narrows candidates; the authoritative conditional write decides ownership**. Partition the authority by the key that owns the invariant—driver, account, origin host or worker slot—and measure contention, skew and maximum partition size.

### 4. Example 3 — timeout after commit is an evidence problem

A merchant posts transfer `A -> B, 1,000 USD-minor-units` with scoped idempotency key `K1`. The service atomically records `(merchant-7, K1, request-hash)`, the balanced ledger entries and the canonical result. The response is lost.

```mermaid
sequenceDiagram
  participant C as Client
  participant API as Transfer API
  participant DB as Authoritative DB
  C->>API: POST transfer, key K1
  API->>DB: key + request hash + balanced entries
  DB-->>API: committed operation op-1
  API--xC: response lost
  C->>API: retry same key and request
  API->>DB: read scoped key K1
  DB-->>API: existing op-1 / POSTED
  API-->>C: canonical result op-1
```

Four cases must be distinct:

| Retry | Safe result |
|---|---|
| same caller, key and request hash | return the original operation/result |
| same caller/key but different amount or destination | reject key reuse; do not reinterpret intent |
| different caller with the same text key | independent scope; never collide tenants |
| non-idempotent request with unknown outcome | do not blindly replay; query/reconcile authoritative state |

RFC 9110 defines method idempotency by intended server effect and warns against automatically retrying a non-idempotent request without evidence that it is safe or unapplied. AWS's idempotent-API guidance likewise favors an explicit caller-provided request identifier and requires recording that token consistently with the mutation. Transport success is not business success, and a timeout is not proof of failure.

For event pipelines, “exactly once” is scoped. Kafka can provide ordering within a topic partition and transactions within supported boundaries, but an application that writes an external database or calls a payment rail still needs stable operation identity, deduplication and reconciliation at that boundary.

### 5. Example 4 — the degradation ladder must preserve the invariant

At 10× traffic or during regional loss, choose from a declared ladder rather than improvising:

```mermaid
flowchart TD
  H{"Authority healthy?"}
  H -->|no| C{"Can a bounded stale read be truthful?"}
  C -->|yes| D["Degrade and label freshness"]
  C -->|no| R["Reject or report unknown"]
  H -->|yes| K{"Capacity within budget?"}
  K -->|yes| S["Serve normally"]
  K -->|no| P["Prioritize, throttle or shed"]
  P --> I{"Would degradation violate invariant?"}
  I -->|yes| R
  I -->|no| D
```

| System under pressure | Preserve | Degrade first | Reject/stop when |
|---|---|---|---|
| video blockbuster | entitlement, valid manifest revision | lower bitrate, omit recommendations, alternate CDN | authorization cannot be verified |
| ride-hailing region loss | single driver/trip owner and monotonic trip state | longer ETA, smaller market admission, snapshot on reconnect | assignment authority is unavailable |
| ledger dependency loss | balanced posting, idempotency and known state | asynchronous notifications/search; show pending | authoritative commit or reconciliation identity is unavailable |
| crawler 429/503 wave | robots, origin politeness and safe address policy | lower host rate, postpone rendering/low priority | safety/robots eligibility cannot be established |
| ID lease partition | tuple uniqueness and fenced ownership | exhaust remaining explicitly safe local window only | conservative lease deadline or clock rule is crossed |

A graceful system is not one that always returns `200`. It is one whose reduced behavior is truthful, bounded and cheaper while its invariant remains intact. Use `503` plus an appropriate retry policy for temporary overload, but cap retry attempts and add backoff/jitter so recovery traffic does not become a second outage.

### 6. Executable interview-defense contract

[`DesignDefenseContracts.java`](DesignDefenseContracts.java) makes the supplement's reusable claims executable:

- exact burst backlog and drain-time arithmetic with checked `long` operations;
- only the declared source of truth may decide an invariant;
- caller-scoped idempotency returns one logical operation and rejects key/request mismatches;
- timeout policy distinguishes a known result, reconcilable idempotent request and unsafe replay;
- overload policy degrades video/crawling reads but rejects unsafe ride, ledger and ID decisions;
- 10,000 seeded cases independently verify backlog conservation.

Run:

```bash
java -ea study-guides/system-design/blueprints/DesignDefenseContracts.java
```

Expected final trace:

```text
PARITY|BACKLOG=1200000|DRAIN=120|REPLAY=op-1|VIDEO=DEGRADE|LEDGER=REJECT
```

### 7. A compact review card

Before finishing any system-design answer, be able to point to one concrete artifact for every row:

| Question | Required artifact |
|---|---|
| What must never happen? | named invariant and its authoritative record |
| What scale changes the architecture? | unit-carrying peak calculation and headroom |
| Where is concurrency serialized? | conditional write, transaction, lease or single owner |
| What can be stale? | explicit derived view plus freshness/repair policy |
| What happens after a lost response? | stable operation identity and reconciliation trace |
| What prevents retry storms? | deadline, bounded attempts, backoff/jitter and admission budget |
| What is the hot key? | skew estimate, maximum partition and split/migration plan |
| What fails in a region outage? | routing/fencing decision and reduced truthful service |
| How do we know it works? | SLO, queue age, invariant alarm, lag and failure drill |
| Why this product? | semantic requirement first, provider mapping second |

The cloud redraw exercise should preserve these contracts across AWS, Azure, GCP and portable implementations. If swapping a product changes consistency, delivery, topology or operator burden, record the difference; a service-name mapping is not a design proof.

### 8. Current primary references

- [Google SRE: Handling Overload](https://sre.google/sre-book/handling-overload/) — accept only work that can be processed, reject excess gracefully and use semantically safe degraded responses.
- [AWS Builders' Library: Making retries safe with idempotent APIs](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/) — caller-provided request identity, duplicate intent and atomic idempotency state.
- [RFC 9110, HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html#name-idempotent-methods) — safe/idempotent method contracts, retry constraints, `503`, `Retry-After` and timeout semantics.
- [Apache Kafka documentation](https://kafka.apache.org/documentation/) — partition-key ordering and the documented scope of delivery/processing guarantees.
- [AWS Well-Architected REL05-BP03](https://docs.aws.amazon.com/wellarchitected/latest/framework/rel_mitigate_interaction_failure_limit_retries.html) — bounded retries, exponential backoff and jitter as overload controls.

### 9. Source provenance and continuation

The confirmed Library source remains `top_5_system_design_interview_blueprints.md`, revision 1, SHA-256 `10c3b8139474ea47f35e1fed6cb169b2728db48070f3264e1d23dfa776e9bf03`. This bounded pass promotes exactly the cross-question pattern map, staff-level answer template, references and practice instructions at source lines 1127–1264. It deepens them into four traced examples, four diagrams, an executable Java contract and current primary-source verification refreshed on 2026-10-24. The source is now processed through its final line; the wider repository intake and Java-placeholder backlog remain incomplete.
