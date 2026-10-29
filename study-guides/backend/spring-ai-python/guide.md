# Spring AI and Python APIs

This is the canonical incremental guide. It compares Java and Python implementation choices by contract and ownership boundary rather than treating a framework, model, runtime, and host as interchangeable. The guide currently processes source Sections 1–23 through line 1332.

## Section 1 — Choose the layer before choosing the library

### Start with requirements, not package names

Before selecting Spring AI, FastAPI, an orchestration framework, or a model client, write the observable contract:

| Question | Required answer |
|---|---|
| What enters and leaves? | typed request, typed success, explicit no-answer and dependency-failure outcomes |
| Which evidence is authoritative? | database/search results before model prose; tenant and resource scope enforced before prompt construction |
| Which effects occur? | read-only model call, retrieval, durable write, tool invocation, or human approval |
| What may be retried? | only idempotent/read operations unless a durable operation key reconciles effects |
| What must survive restart? | nothing for a simple call; workflow state for branching, delayed or human-reviewed work |
| Who owns each layer? | team/service plus versioned framework, runtime, model revision, and operator |

The minimum useful pipeline is usually a typed boundary, deterministic business/retrieval logic, a model adapter, and an output validator. Add an orchestration layer when it removes repeated integration work or owns durable branching—not merely because the application uses a model.

```mermaid
flowchart TB
  API["Business API and identity"] --> POL["Authorization and policy"]
  POL --> WF["Workflow: plain code or durable engine"]
  WF --> RET["Retrieval adapter"]
  WF --> MOD["Model adapter"]
  WF --> TOOL["Tool adapter"]
  RET --> DATA[("Authorized data")]
  MOD --> RUN["Inference runtime and model artifact"]
  TOOL --> SYS["Authorized business system"]
  WF --> VAL["Output validator"]
```

The diagram is a contract map, not a requirement that every box be a separate service.

### Example 1 — one-call classification API

Requirement: classify a support ticket into one of five allowed categories and return a confidence/abstain outcome. No retrieval, tools, or durable branching.

Selected layers: HTTP/DTO validation → model adapter → allow-list validator. In Spring, a controller plus `ChatModel`/`ChatClient` adapter can be enough. In Python, FastAPI/Pydantic plus one explicit HTTP or SDK adapter can be enough. An agent graph adds persistence and transition states that the requirement never asked for.

```mermaid
sequenceDiagram
  participant C as Client
  participant A as API boundary
  participant M as Model adapter
  C->>A: ticket text + operation ID
  A->>A: authenticate, size-check, redact
  A->>M: typed classification prompt
  M-->>A: candidate category
  A->>A: validate allow-list or abstain
  A-->>C: category | abstain | dependency failure
```

Expected outcomes:

| Input condition | Output |
|---|---|
| model returns `billing` | accepted typed category |
| model returns `finance-admin` | `abstain` because it is outside the allow-list |
| provider times out | dependency failure, not a fabricated category |

### Example 2 — tenant-scoped RAG answer

Requirement: answer only from documents the authenticated user may read and cite every claim. The layer selection changes because retrieval is now an authority boundary.

Selected layers: API/identity → authorization predicate → retrieval adapter → context packer → model adapter → citation/output validator. A vector database is one possible retrieval implementation; it is not “RAG” by itself. The application must apply tenant/resource filters before candidate text enters the prompt and must reject citations outside the retrieved evidence IDs.

Trace:

| Step | Fact established | Failure behavior |
|---:|---|---|
| 1 | token identifies subject and tenant | reject invalid identity |
| 2 | policy derives permitted resource scope | fail closed |
| 3 | retrieval returns scoped evidence IDs | empty evidence becomes `NO_EVIDENCE` |
| 4 | packer preserves question and bounded evidence | reject over-budget request deterministically |
| 5 | model proposes answer and citations | no direct authority |
| 6 | validator proves every cited ID was retrieved | reject unsupported output |

Choose an orchestration framework only when repeated provider/retriever integrations or trace hooks outweigh its abstraction cost. The authorization predicate remains application-owned even when a framework supplies the retriever.

### Example 3 — refund tool with human review

Requirement: inspect an order, draft a refund, pause for approval above a threshold, execute exactly once, and resume after a restart. This does justify a durable workflow or graph.

```mermaid
stateDiagram-v2
  [*] --> Inspect
  Inspect --> Propose
  Propose --> Approve: amount above threshold
  Propose --> Execute: amount within policy
  Approve --> Execute: approved
  Approve --> Rejected: rejected or expired
  Execute --> Reconcile: timeout or unknown result
  Reconcile --> Done: operation ID found
  Execute --> Done: confirmed
```

The model may propose arguments; it does not authorize the refund. The tool adapter rechecks account/order scope and policy. Persist the operation ID before calling the payment system, and reconcile an unknown result before retrying. “The framework retries tools” is not a correctness policy.

### Example 4 — local model development versus production hosting

Requirement: prototype locally, then deploy reproducibly. Record four separate identities:

| Identity | Example | What a mismatch explains |
|---|---|---|
| client/framework | `spring-ai:2.0.1` or a pinned Python client | request/response mapping and retry behavior |
| inference runtime/API | Ollama release or hosted-provider API revision | supported fields, context/runtime behavior |
| model artifact | repository plus immutable commit/digest | tokenizer, weights, license and behavior |
| operator/host | laptop, internal platform, or named cloud account/region | network, policy, quota, observability and data handling |

“We use Spring AI,” “we use Python,” or “we use Hugging Face” does not identify the inference contract. Hugging Face can host versioned model artifacts and metadata; Ollama can run a local model or expose a cloud API; Spring AI is an application integration abstraction. Pin what you deploy and capture the resolved identities in release evidence.

```mermaid
flowchart LR
  APP["Application code"] --> CLIENT["Framework/client version"] --> API["Runtime/API version"] --> MODEL["Model revision/digest"]
  HOST["Operator, region, policy"] --> API
  DATA["Authorized inputs"] --> APP
```

### When to escalate from plain code to a framework

Use an explicit gate:

1. Start with narrow adapters and one end-to-end test.
2. Measure duplicated integration code, not imagined future nodes.
3. Escalate when there are several repeated integrations, durable branching, restart/resume, or human review.
4. Keep business authorization, idempotency, output semantics, and release identity outside opaque framework defaults.
5. Re-run contract tests when changing a library, runtime, model revision, or host independently.

The paired executable exercises encode this selection rule and the four-part release identity:

```bash
java -ea study-guides/backend/spring-ai-python/LayerSelectionContracts.java
python3 study-guides/backend/spring-ai-python/layer_selection_contracts.py
```

### Common mistakes and interview follow-ups

- **Framework equals model:** ask which process performs inference and which artifact it loaded.
- **Schema-valid equals correct:** DTO/Pydantic validation proves shape, not factual support or authorization.
- **Async equals nonblocking:** a blocking client inside a Python `async def` still blocks its worker; Java virtual threads still need downstream capacity limits.
- **Provider portability is perfect:** common interfaces do not erase provider-specific tool, streaming, token, safety, or error semantics.
- **Agent equals tool call:** one deterministic function call does not require a durable agent loop.

Interview variations: replace the model provider without changing the business API; move local inference to a managed host; add tenant-scoped retrieval; add a tool with an unknown outcome; explain exactly which new requirement justifies a workflow engine.

### Current primary references

- [Spring AI introduction](https://docs.spring.io/spring-ai/reference/index.html), [Model API](https://docs.spring.io/spring-ai/reference/api/index.html), and [Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html) — provider abstractions and model integration boundaries.
- [Spring AI tool calling](https://docs.spring.io/spring-ai/reference/api/tools.html) — current distinction between model-returned tool calls and application-driven execution.
- [Ollama API introduction](https://docs.ollama.com/api/introduction) and [usage metrics](https://docs.ollama.com/api/usage) — local/cloud runtime distinction and runtime evidence.
- [Hugging Face Model Hub](https://huggingface.co/docs/hub/models-the-hub) and [model cards](https://huggingface.co/docs/hub/model-cards) — versioned model artifacts and accompanying metadata.
- [FastAPI request bodies](https://fastapi.tiangolo.com/tutorial/body/) and [async guidance](https://fastapi.tiangolo.com/async/) — transport/validation and blocking-boundary behavior.

## Section 2 — Python library map: purpose, ownership and trade-offs

### Select one owner for each required job

A Python AI stack is not a single ladder from “basic” to “advanced.” The projects in the source map own different jobs. Select from observable requirements, and remove any package whose responsibility you cannot name.

| Job family | Focused choices from the source | Owned contract | Do not assume |
|---|---|---|---|
| arrays and tabular analysis | NumPy, pandas | dtypes, shapes, vector math, cleaning, inspection | a DataFrame is a durable corpus store |
| statistical or neural modeling | scikit-learn, PyTorch, Transformers, Sentence Transformers | fit/evaluate, tensor execution, model/tokenizer loading, embeddings/reranking | a large language model is the best baseline |
| local or explicit model transport | Ollama Python, HTTPX | typed runtime client or explicit sync/async HTTP exchange | a client package installs or authorizes a model |
| API boundary | FastAPI, Pydantic | HTTP/ASGI handling, typed parsing, JSON Schema | schema-valid output is factually supported |
| orchestration and retrieval | LangChain, LangGraph, LlamaIndex, Haystack | integration glue, durable graph state, ingestion/indexing, component pipelines | the framework supplies business authorization or idempotency |
| protocols and evidence | MCP SDK, Ragas, MLflow | tool/resource protocol, task evaluation, experiment/trace evidence | a judge score or stored trace is automatically safe or calibrated |

The smallest correct stack often spans two or three rows. It should not contain one tool from every row. Before adding a package, write:

1. the job it owns and the interface on each side;
2. the artifact, configuration, and library versions required to reproduce it;
3. the resource model—memory, accelerator, connection pool, worker, or persisted state;
4. the failure and retry boundary;
5. the evidence that would justify keeping the abstraction.

```mermaid
flowchart TB
  REQ["Requirement and measurable outcome"] --> JOB["One owned job"]
  JOB --> MIN["Smallest tool set"]
  MIN --> CON["Versioned boundary contract"]
  CON --> TEST["Failure and replay tests"]
  TEST --> KEEP{"Abstraction earns its cost?"}
  KEEP -->|yes| PROD["Promote with evidence"]
  KEEP -->|no| DROP["Remove or narrow it"]
```

### Example 1 — prove the classical baseline before adding a model runtime

Requirement: classify short support tickets with a reproducible held-out score and explain which terms influenced the decision. Start with NumPy/scikit-learn and a TF-IDF plus linear-classifier pipeline. pandas may help inspect data, but it is not required in the serving path. PyTorch, Transformers, a model server, and an agent framework own no required job yet.

The split must happen before any learned preprocessing is fit. A pipeline makes the vectorizer and classifier fit only on training folds; fitting TF-IDF on the full corpus leaks test-set document statistics into evaluation.

```python
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline

baseline = make_pipeline(
    TfidfVectorizer(ngram_range=(1, 2), min_df=2),
    LogisticRegression(max_iter=1_000, random_state=7),
)
baseline.fit(train_text, train_label)       # train partition only
score = baseline.score(test_text, test_label)  # untouched holdout
```

Trace:

| Step | Evidence | Reject when |
|---:|---|---|
| 1 | immutable dataset revision and label policy | labels or duplicate policy are unknown |
| 2 | train/test split IDs written before fitting | preprocessing saw holdout rows |
| 3 | one pipeline fits vectorizer and classifier | test score drove repeated manual tuning |
| 4 | baseline metric plus confusion slices | aggregate score hides a critical class |
| 5 | latency and artifact size measured | a larger model has no demonstrated gain |

```mermaid
flowchart LR
  DATA["Versioned labeled rows"] --> SPLIT["Split first"]
  SPLIT --> TRAIN["Fit TF-IDF + classifier on train"]
  SPLIT --> HOLD["Untouched holdout"]
  TRAIN --> ART["Pinned pipeline artifact"]
  ART --> EVAL["Transform and score holdout"]
  HOLD --> EVAL
```

### Example 2 — load a local pretrained model as a versioned artifact

Requirement: compare a local pretrained classifier with the baseline. Transformers owns model/tokenizer construction; PyTorch owns tensor execution and the loaded state. Neither owns the business label policy, evaluation split, or deployment authorization.

Treat the tokenizer, model weights/configuration, runtime version, and task head as one compatibility set. Pin an immutable repository revision for both tokenizer and model, record the resolved commit, and evaluate the exact promoted artifact. A floating repository name can resolve to new bytes without an application-code change.

```python
from transformers import AutoModelForSequenceClassification, AutoTokenizer

MODEL = "org/ticket-classifier"
REVISION = "immutable-commit-sha"
tokenizer = AutoTokenizer.from_pretrained(MODEL, revision=REVISION)
model = AutoModelForSequenceClassification.from_pretrained(MODEL, revision=REVISION)
model.eval()
```

| Boundary | Required release evidence | Typical mismatch |
|---|---|---|
| tokenizer | repository, revision, vocabulary/config digest | token IDs differ from training |
| model | repository, revision, task head, weights/config digest | wrong output shape or labels |
| runtime | Python, Transformers, PyTorch, device/backend versions | unsupported operator or device behavior |
| application | label map, thresholds, input limits | technically valid but wrong business result |

```mermaid
sequenceDiagram
  participant R as Release builder
  participant H as Artifact repository
  participant T as Tokenizer
  participant M as Model runtime
  R->>H: resolve immutable revision
  H-->>R: config, vocabulary, weights, digests
  R->>T: load same pinned revision
  R->>M: load task head + weights
  R->>R: evaluate exact artifact and label map
  R-->>R: emit manifest before promotion
```

### Example 3 — explicit asynchronous provider API

Requirement: expose a typed Python endpoint that calls one remote model provider. The narrow stack is FastAPI for the ASGI boundary, Pydantic for structural request/response validation, and one long-lived HTTPX `AsyncClient` for the provider protocol. This does not yet require LangChain or a graph.

Create the client in application lifespan, close it at shutdown, bound pool size, and set connect/read/write/pool timeouts intentionally. Do not create a client inside a hot loop: the reusable client owns connection pooling. An `async def` route remains blockable if it directly calls a blocking SDK or CPU-heavy model; isolate that work or choose a synchronous route/worker plan deliberately.

```python
timeout = httpx.Timeout(connect=0.25, read=2.0, write=0.5, pool=0.1)
limits = httpx.Limits(max_connections=64, max_keepalive_connections=16)
client = httpx.AsyncClient(timeout=timeout, limits=limits)

async def call_provider(request: ProviderRequest) -> ProviderResponse:
    response = await client.post(PROVIDER_URL, json=request.model_dump())
    response.raise_for_status()
    parsed = ProviderResponse.model_validate(response.json())
    return validate_business_semantics(parsed)  # shape is not truth
```

Trace:

| Event | Owner | Observable outcome |
|---|---|---|
| request parse fails | Pydantic/API boundary | stable 4xx; provider is not called |
| pool slot unavailable | HTTPX pool timeout | bounded dependency failure and saturation metric |
| connection/read phase expires | HTTPX adapter | phase-specific timeout; retry only if operation semantics allow |
| JSON has correct shape but unsupported citation | application validator | reject or abstain |
| application shuts down | lifespan owner | client closes; connections are released |

```mermaid
sequenceDiagram
  participant C as Client
  participant F as FastAPI + Pydantic
  participant H as Shared HTTPX client
  participant P as Provider
  C->>F: typed request
  F->>F: auth, size, schema checks
  F->>H: async call with phase budgets
  H->>P: pooled HTTP request
  P-->>H: status + JSON
  H-->>F: parsed provider DTO
  F->>F: business/evidence validation
  F-->>C: success, abstain, or typed failure
```

### Example 4 — add durable graph orchestration only for replayable state

Requirement: draft a refund, pause for human approval, survive restart, then call a payment API exactly once from the application's point of view. LangGraph can own persisted graph position and resumption. HTTPX or a focused SDK still owns transport; the payment system owns the actual effect; application code owns authorization and idempotency.

Persist the operation ID and proposed arguments before the effect. On resume, a node may run again, so first query the effect ledger/provider by operation ID. “The graph checkpointed” does not prove the external refund did or did not happen.

| Replay state | Decision |
|---|---|
| no approval | remain paused; make no payment call |
| approved, operation absent | execute with stable idempotency key |
| timeout, operation status unknown | reconcile before any retry |
| operation already applied | record result and advance without a second effect |

```mermaid
stateDiagram-v2
  [*] --> Draft
  Draft --> PersistIntent
  PersistIntent --> AwaitApproval
  AwaitApproval --> Reconcile: approved or resumed
  Reconcile --> Execute: operation absent
  Reconcile --> Record: operation already applied
  Execute --> Reconcile: timeout or crash
  Execute --> Record: confirmed
  Record --> [*]
```

Use LlamaIndex or Haystack instead when ingestion, indexing, and explicit retrieval components dominate. Use LangChain when numerous model/tool/retrieval integrations eliminate proven repeated glue. Use the MCP SDK when the requirement is an interoperable tool/resource/prompt protocol. Add Ragas or MLflow when an evaluation or evidence-retention requirement exists—and define calibration, redaction, access, and retention before storing production prompts or traces.

### Executable selection and boundary contracts

The dependency-free Java/Python pair proves the four minimal tool sets, clean-evaluation gate, pinned model/tokenizer identity, phase-timeout budget, and replay-safe effect decision:

```bash
java -ea study-guides/backend/spring-ai-python/LibrarySelectionContracts.java
python3 study-guides/backend/spring-ai-python/library_selection_contracts.py
```

These programs test decision logic; they do not pretend to compile FastAPI, HTTPX, scikit-learn, PyTorch, Transformers, or LangGraph integrations without those dependency graphs.

### Common mistakes and interview follow-ups

- **Install the whole ecosystem:** require a named owner and deletion test for every dependency.
- **DataFrame as production storage:** pandas is excellent for bounded analysis, not tenant authorization, transactions, or durable corpus serving.
- **Pretrained means reproducible:** pin model and tokenizer revisions plus runtime/device compatibility.
- **Pydantic means correct:** structural parsing cannot prove citation support, policy, or factual truth.
- **Async means unlimited:** bound connection pools, workers, queues, and every wait phase.
- **Checkpoint means exactly once:** graph replay requires idempotent effects or reconciliation with stable operation IDs.
- **Evaluation framework means objective judge:** calibrate against reviewed data and track judge/model versions.

Interview variations: remove a framework without changing the business contract; prove the classical baseline is insufficient; replace a local model with a hosted adapter; diagnose pool starvation separately from provider read timeout; resume after a crash between external success and local checkpoint.

### Current primary references

- [scikit-learn common pitfalls](https://scikit-learn.org/stable/common_pitfalls.html) and [pipelines](https://scikit-learn.org/stable/modules/compose.html) — split-before-fit discipline and leakage-resistant preprocessing.
- [Hugging Face Transformers auto classes](https://huggingface.co/docs/transformers/model_doc/auto) and [model loading](https://huggingface.co/docs/transformers/models) — task-specific model/tokenizer construction and pretrained artifact loading.
- [PyTorch serialization semantics](https://docs.pytorch.org/docs/stable/notes/serialization.html) — state-dictionary ownership and safer artifact loading boundaries.
- [FastAPI concurrency](https://fastapi.tiangolo.com/async/) and [lifespan events](https://fastapi.tiangolo.com/advanced/events/) — sync/async execution boundaries and resource lifecycle.
- [HTTPX async support](https://www.python-httpx.org/async/), [timeouts](https://www.python-httpx.org/advanced/timeouts/), [resource limits](https://www.python-httpx.org/advanced/resource-limits/), and [clients](https://www.python-httpx.org/advanced/clients/) — reusable pools and bounded wait phases.
- [LangGraph Functional API](https://docs.langchain.com/oss/python/langgraph/functional-api) — durable replay and idempotent-side-effect requirements.

## Section 3 — Java library map: choose the owner, then the abstraction

### Preserve the source map, but select by contract

The Java projects in the source solve different jobs. Spring AI and LangChain4j are application-integration choices; the MCP SDK is a protocol implementation; Spring Security, Reactor, validation, resilience, telemetry and JDBC are general application infrastructure; DJL and ONNX Runtime Java can run models inside the JVM. None of them makes authorization, evidence quality, idempotency or capacity decisions for the application.

| Library / project | Job it can own | Python-side analogy | Boundary to make explicit |
|---|---|---|---|
| Spring AI | Spring-native chat, embedding, vector-store and tool adapters | parts of LangChain/LlamaIndex integrated with Spring | provider features and business policy remain outside the abstraction |
| LangChain4j | Java LLM integrations, AI Services, tools, memory and RAG helpers | similar problem space to LangChain | independent Java API, not a Python translation or a model runtime |
| MCP Java SDK | MCP client/server protocol implementation | official Python MCP SDK | pin SDK and protocol revision; negotiate capabilities |
| Spring AI MCP starters | Boot auto-configuration for MCP transports and annotations | protocol SDK integrated into a web framework | HTTP transports need a deliberate authentication/authorization boundary |
| Spring Security | authentication plus request, method and domain authorization | framework identity/policy middleware | endpoint authentication does not prove resource-level access |
| Reactor / WebFlux | nonblocking streams, demand and scheduling | async streams/ASGI concepts | blocking work, scheduler choice and bounded demand remain design decisions |
| Jakarta Validation | structural constraints on Java DTOs | Pydantic constraints | valid shape does not prove truth, authorization or citation support |
| Resilience4j | retry, rate limit, circuit breaker, bulkhead and time limit | focused resilience primitives | choose one retry owner and preserve a total deadline |
| Micrometer / OpenTelemetry | metrics, observations and distributed traces | OTel Python and metrics clients | prompts, resource IDs and user IDs are unsafe high-cardinality labels |
| Deep Java Library (DJL) | engine-agnostic Java deep-learning APIs and translators | selected PyTorch/Transformers workflows | native engine, model format, preprocessing and predictor ownership must match |
| ONNX Runtime Java | direct JVM inference for compatible ONNX artifacts | ONNX Runtime Python | operator support, tensor shape/type and pre/post-processing are part of the artifact contract |
| JDBC / database drivers | SQL transactions and database wire access | DB-API drivers such as psycopg | pooling, transaction scope and publication consistency still apply in AI services |

Use the narrowest row set that satisfies a written requirement. Do not install both Spring AI and LangChain4j merely to preserve optionality. Hide the selected framework behind an application-owned port only when there is a real replacement or testing need, and test the semantic gap—stream events, tool-call arguments, token accounting, errors and cancellation—not just method signatures.

```mermaid
flowchart TB
  REQ["Business requirement"] --> PORT["Application-owned port"]
  PORT --> APP["Spring AI or LangChain4j adapter"]
  PORT --> INF["DJL or ONNX inference adapter"]
  PORT --> PY["Versioned Python service adapter"]
  APP --> CROSS["Security, limits, telemetry, persistence"]
  INF --> CROSS
  PY --> CROSS
```

### Example 1 — Spring business service with an MCP tool

Requirement: a signed-in support agent asks for an order summary; the model may call a read-only `lookup_order` tool, but only for an order the agent may view. The service must emit traces without prompt or customer data in metric labels.

Choose Spring AI for the model/tool adapter, an MCP Java SDK or Spring AI MCP starter only if an MCP transport is actually required, Spring Security for request and method authorization, Jakarta Validation for DTO shape, and Micrometer/OpenTelemetry for bounded evidence. Keep the resource predicate in application code and repeat it inside the tool boundary. Spring AI's current MCP starter documentation explicitly says HTTP transports expose an unauthenticated JSON-RPC endpoint by default, so network reachability must not be treated as authority.

```java
public OrderSummary lookupOrder(ToolRequest request, Principal principal) {
    var orderId = requireValidId(request.orderId());       // shape only
    authorization.checkCanReadOrder(principal, orderId);   // resource authority
    return orders.readSummary(orderId);                    // scoped projection
}
```

Trace:

| Step | Owner | Required result |
|---:|---|---|
| 1 | Spring Security | authenticated principal and request-level policy |
| 2 | application service | authorized resource scope before prompt/tool exposure |
| 3 | model adapter | proposed tool name and structurally parsed arguments |
| 4 | tool service | resource authorization repeated; read executes only if allowed |
| 5 | telemetry | low-cardinality outcome (`allowed`, `denied`, `invalid`, `failed`) plus trace correlation |

```mermaid
sequenceDiagram
  participant U as Support agent
  participant A as Secured API
  participant M as Model adapter
  participant T as Order tool
  U->>A: signed request
  A->>A: authenticate + derive scope
  A->>M: prompt + allowed tool schema
  M-->>A: proposed lookup_order(args)
  A->>T: principal + validated args
  T->>T: authorize order resource
  T-->>A: scoped summary or denial
```

Never expose database rows merely because the generated tool arguments pass Jakarta Validation. DTO validation answers “is the input shaped correctly?”; authorization answers “may this principal act on this resource?”

### Example 2 — Spring AI versus LangChain4j behind one business port

Requirement: generate a typed, cited answer and allow a later provider/framework replacement without changing the public business API. Define the application port around outcomes the product owns:

```java
sealed interface AnswerResult permits Answered, NoEvidence, DependencyFailure {}
interface EvidenceAnswerer {
    AnswerResult answer(AuthorizedQuestion question, Deadline deadline);
}
```

A Spring AI adapter may use `ChatClient`; a LangChain4j adapter may use an AI Service. That common port is useful only if its contract includes citation membership, cancellation, deadline propagation and typed failure mapping. Lowest-common-denominator wrappers that discard streaming/tool/provider features create apparent portability while moving surprises to production.

Compatibility trace:

| Probe | Old adapter | Candidate adapter | Promotion gate |
|---|---|---|---|
| empty authorized evidence | `NoEvidence` | must also abstain | no provider call with fabricated context |
| unsupported citation | reject | reject | every citation belongs to authorized evidence IDs |
| deadline expires | cancel/map timeout | same typed outcome | no hidden second retry owner |
| provider tool call | normalized arguments | normalized arguments | schema and effect policy are equivalent |

```mermaid
flowchart LR
  API["Stable business API"] --> PORT["EvidenceAnswerer contract"]
  PORT --> SAI["Spring AI adapter"]
  PORT --> LC4J["LangChain4j adapter"]
  SAI --> ORACLE["Shared contract tests"]
  LC4J --> ORACLE
```

Select one production adapter. Run the other only in an evaluation or migration lane until the shared oracles and provider-specific tests pass.

### Example 3 — DJL versus direct ONNX Runtime inference

Requirement: run a classifier in the JVM with bounded concurrency and reproducible inputs. Choose DJL when engine-agnostic model loading, translators and a broader deep-learning API earn their cost. Choose direct ONNX Runtime Java when the promoted artifact is ONNX and explicit tensor/session control is preferable. DJL can itself use an ONNX Runtime engine; that does not make direct ORT calls and DJL's abstraction identical.

The release unit must bind these values:

| Identity | Example evidence | Reject when |
|---|---|---|
| model bytes | immutable URI plus SHA-256 | digest differs |
| format/opset | ONNX plus declared opset | runtime/provider lacks required operators |
| preprocessing | tokenizer/normalizer code digest | training and serving transforms differ |
| tensor contract | names, dtypes and shapes | request shape/type is incompatible |
| runtime/native provider | DJL engine or ORT version plus CPU/CUDA provider | native library or accelerator is incompatible |
| concurrency owner | fixed predictor/session admission limit | request bypasses the permit gate |

```java
if (!artifact.digest().equals(actualDigest)) throw new IllegalStateException("model drift");
if (!permits.tryAcquire()) return InferenceResult.saturated();
try {
    return predictor.predict(preprocessor.translate(input));
} finally {
    permits.release();
}
```

```mermaid
flowchart TB
  REQ["Pinned artifact manifest"] --> PRE["Preprocess + tensor contract"]
  PRE --> GATE{"Capacity permit?"}
  GATE -->|no| SAT["Typed saturation"]
  GATE -->|yes| RUN["DJL predictor or ORT session"]
  RUN --> POST["Validated output + metrics"]
```

Do not share a stateful predictor because construction is expensive unless its API explicitly permits that concurrency. Pool or create per-request resources according to the selected runtime's ownership contract, and close native resources deterministically.

### Example 4 — keep specialized Python inference behind a versioned service boundary

Requirement: Java owns identity, orders and durable workflow; Python owns a specialized GPU model. Split the process when release cadence, native dependencies, accelerator scheduling or team ownership justify the operational boundary—not because Python is “the AI language.”

The Java-to-Python envelope needs an immutable schema version, operation ID, absolute deadline, trace context, authenticated service identity and model capability/version. The Java service authorizes business resources before sending the minimum necessary input. The Python service authenticates the caller, validates its own request limits, admits work only while capacity and deadline remain, and returns a typed outcome.

| Event | Java decision | Python decision |
|---|---|---|
| invalid business resource | reject locally | no request received |
| stale schema/model capability | map compatibility failure | reject before inference |
| no capacity before deadline | do not multiply retries | return `SATURATED` with bounded retry guidance |
| transport timeout, operation read-only | retry only within remaining deadline and one retry owner | deduplicate expensive work when an operation ID cache is configured |
| result shape valid but policy-invalid | reject at business boundary | record technical success only |

```mermaid
sequenceDiagram
  participant J as Java workflow
  participant P as Python inference API
  participant G as GPU admission gate
  J->>J: authorize + set absolute deadline
  J->>P: schema, operation, trace, minimal input
  P->>P: authenticate + validate capability
  P->>G: acquire within remaining deadline
  alt admitted
    G-->>P: permit
    P-->>J: typed result + model identity
  else saturated
    G-->>P: no permit
    P-->>J: SATURATED
  end
```

### Version, retry and observability discipline

As verified on 23 October 2026, the Spring reference lists Spring AI 2.0.1 as stable and states that the 2.0.x line supports Spring Boot 4.0.x and 4.1.x. Treat that as a compatible release family, not permission to float dependencies: import the Spring AI BOM, pin the application build, record the resolved graph, and review upgrades. The MCP Java SDK documentation is independently versioned; pin the SDK and record the negotiated MCP revision/capabilities rather than assuming every server supports the same transport or feature set.

Retries belong at one layer. An application retry around a Spring AI client that already retries, combined with an ingress retry, can multiply calls. Keep one absolute deadline, allocate phase budgets from its remaining time, and do not retry effectful tools without an idempotency/reconciliation contract. Bulkheads and Reactor demand are related but not interchangeable: a stream can honor downstream demand while an unbounded number of independent requests still compete for the same database, provider or GPU.

Metrics must aggregate. Put bounded values such as adapter name, model family, outcome and failure class in metric tags; put request/resource identifiers and detailed events in access-controlled traces or logs. Redact prompt/tool payloads by default and define retention before enabling framework observations in production.

### Executable Java/Python contracts

The paired standard-library exercises encode all twelve source-map selections, framework-port promotion gates, pinned inference identity, bounded inference admission, and a versioned cross-language service envelope:

```bash
java -ea study-guides/backend/spring-ai-python/JavaLibraryMapContracts.java
python3 study-guides/backend/spring-ai-python/java_library_map_contracts.py
```

They test decision and boundary logic. They do not claim to compile Spring AI, LangChain4j, MCP, DJL or ONNX Runtime integrations without those dependency graphs and native runtimes.

### Common mistakes and interview follow-ups

- **Two frameworks for optionality:** demand an application contract and migration evidence; otherwise choose one.
- **MCP endpoint equals secure tool:** require transport authentication plus resource/effect authorization inside every tool.
- **Reactive equals bounded:** name the queue, demand, scheduler and downstream capacity limit.
- **Circuit breaker equals retry policy:** breakers stop calls after evidence of failure; they do not decide whether an effect is safe to repeat.
- **Model format equals serving contract:** include preprocessing, tensor names/types/shapes, runtime/native provider and output mapping.
- **Trace equals safe evidence:** define redaction, access, retention and cardinality before recording prompts or identities.

Interview variations: expose the same MCP tool over stdio and Streamable HTTP; replace Spring AI with LangChain4j while preserving failure semantics; choose DJL over direct ORT for two model formats; diagnose blocking JDBC inside a WebFlux chain; split GPU inference to Python without moving business authorization.

### Current primary references

- [Spring AI introduction](https://docs.spring.io/spring-ai/reference/index.html), [getting started](https://docs.spring.io/spring-ai/reference/getting-started.html), [tool calling](https://docs.spring.io/spring-ai/reference/api/tools.html), and [MCP server starters](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) — current release family, application abstractions, tool execution and the explicit MCP transport-security warning.
- [LangChain4j introduction](https://docs.langchain4j.dev/intro/) and [AI Services](https://docs.langchain4j.dev/tutorials/ai-services/) — Java-native integration scope and high-level typed interface.
- [Official MCP SDK tiers](https://modelcontextprotocol.io/docs/2026-07-28/sdk) and [MCP Java SDK overview](https://java.sdk.modelcontextprotocol.io/latest/overview/) — maintenance/support expectations, SDK version and protocol implementation.
- [Spring Security authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/) and [Reactor backpressure](https://projectreactor.io/docs/core/release/reference/coreFeatures/simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it.html) — request/method authorization and demand propagation.
- [Resilience4j introduction](https://resilience4j.readme.io/docs/getting-started) and [bulkheads](https://resilience4j.readme.io/docs/bulkhead) — composable resilience primitives and concurrency isolation.
- [DJL overview](https://docs.djl.ai/master/index.html), [inference performance](https://docs.djl.ai/master/docs/development/inference_performance_optimization.html), and [ONNX Runtime Java](https://onnxruntime.ai/docs/get-started/with-java.html) — engine abstraction, predictor/concurrency considerations and direct JVM ONNX execution.

## Section 4 — Start with the offline lab

### Requirements before infrastructure

The first retrieval exercise should be runnable without a model, API key, vector database, network call or third-party package. That constraint is useful: it makes five contracts independently testable before probabilistic generation can hide a defect.

| Contract | Required invariant | Failure outcome |
|---|---|---|
| candidate authority | derive the tenant/resource scope from trusted identity, then exclude ineligible documents before scoring | reject a missing scope; return no evidence for an unknown scope |
| lexical baseline | tokenize deterministically, score only eligible documents and break equal scores by immutable ID | return an empty ranking for no positive overlap |
| vector math | require equal non-empty dimensions, finite coordinates and non-zero directions | reject invalid vectors rather than emit `NaN` |
| rank fusion | give one contribution per document per input ranking and use a stable final tie-break | reject an invalid rank constant |
| citation membership | every citation must belong to the exact evidence set supplied to generation | reject missing or unknown IDs; evaluate entailment separately |

```mermaid
flowchart TB
  ID["Trusted identity"] --> SCOPE["Authorized scope"]
  SCOPE --> ELIG["Eligible documents only"]
  ELIG --> LEX["Lexical ranking"]
  ELIG --> VEC["Vector ranking"]
  LEX --> FUSE["Duplicate-safe RRF"]
  VEC --> FUSE
  FUSE --> PACK["Bounded evidence set"]
  PACK --> CITE["Citation allow-list"]
  CITE --> ANSWER["Accept or abstain"]
```

The order is part of the security contract. Filtering after global retrieval is not equivalent: an unauthorized document may already have influenced top-k selection, logs, caches or timing. The offline function accepts `trusted_tenant` only after authentication and authorization have resolved it; it must not be bound directly from an arbitrary request field.

### Example 1 — authorization before similarity

Corpus:

| ID | tenant | relevant terms |
|---|---|---|
| `payments-r7` | `acme` | `payments` once, `rollback` twice |
| `cache-r2` | `acme` | neither query term |
| `vectors-r3` | `acme` | neither query term |
| `private-r1` | `globex` | both query terms repeated; deliberately stronger lexical match |

Request: query `payments rollback`, trusted scope `acme`, `top_k=3`.

Trace:

1. The authority predicate selects the three Acme documents. `private-r1` is never passed to the scorer even though its text would score higher.
2. Token counts for the query are `{payments: 1, rollback: 1}`. `payments-r7` is the only eligible positive-overlap document.
3. The result is exactly `payments-r7`. An unknown tenant or an Acme query such as `quasar` returns `NO_EVIDENCE`; it does not broaden scope or fabricate a fallback.

```python
eligible = (doc for doc in CORPUS if doc.tenant == trusted_tenant)
scored = ((lexical_score(query, doc.text), doc) for doc in eligible)
ranked = sorted(
    (pair for pair in scored if pair[0] > 0.0),
    key=lambda pair: (-pair[0], pair[1].id),
)[:top_k]
```

The deterministic ID tie-break matters because equal floating-point scores otherwise inherit container or database iteration order. Reproducible ordering makes parity tests, caches and incident traces comparable.

### Example 2 — bag-of-words cosine by hand

For query vector \(q=(1,1)\), and the document's query-term projection \(d=(1,2)\):

\[
q \cdot d = 3,\qquad \lVert q\rVert=\sqrt{2},\qquad \lVert d\rVert=\sqrt{5},\qquad
\cos(q,d)=\frac{3}{\sqrt{10}}\approx 0.948683.
\]

That projected value is not the final corpus score, because the full document norm also contains every other token. The implementation builds a `Counter`/map over all lower-cased `[a-z0-9]+` terms, computes the dot product over query terms and divides by both full norms. Repeated terms therefore affect direction, while document length affects the denominator.

| Input | Result | Why |
|---|---:|---|
| empty query | `0.0` | query has no direction |
| no term overlap | `0.0` | dot product is zero |
| positive overlap | `(0, 1]` for nonnegative counts | ordinary cosine bound |
| equal scores | lower document ID first | explicit deterministic order |

This is a transparent baseline, not BM25: it has no inverse-document-frequency term or length normalization parameter. It is also not a neural embedding: the coordinates are literal token counts, not learned latent features. Naming the baseline correctly prevents test results from being attributed to an algorithm that was never run.

Complexity for \(D\) eligible documents, query token count \(Q\), total eligible document tokens \(T\), and requested \(k\): tokenization and scoring cost \(O(Q+T)\); the simple full sort costs \(O(D\log D)\); stored score pairs cost \(O(D)\). A production top-k heap can reduce ranking work to \(O(D\log k)\), but it does not change the scope-first invariant.

### Example 3 — cosine guards are part of the API

The generic vector function tests shape and numerical validity independently of lexical retrieval:

```java
if (left.length == 0 || left.length != right.length) {
    throw new IllegalArgumentException("non-empty equal dimensions required");
}
if (!Double.isFinite(left[i]) || !Double.isFinite(right[i])) {
    throw new IllegalArgumentException("finite vector coordinates required");
}
```

Expected traces:

| left | right | outcome |
|---|---|---|
| `[1, 0]` | `[1, 0]` | `1.0`, same direction |
| `[1, 0]` | `[0, 1]` | `0.0`, orthogonal |
| `[1, 0]` | `[-1, 0]` | `-1.0`, opposite direction |
| `[]` | `[]` | reject empty dimensions |
| `[0, 0]` | `[1, 0]` | reject directionless zero vector |
| `[NaN]` | `[1]` | reject non-finite coordinate |
| `[1]` | `[1, 2]` | reject dimension mismatch |

A naive sum of squares can overflow even when every coordinate is finite. The paired contracts first divide each vector by its own maximum absolute coordinate. Cosine is invariant under positive scaling, so `[1e308, 1e308]` compared with itself safely yields `1.0` instead of `Infinity / Infinity`. The implementation still checks the result and clamps only final round-off to `[-1, 1]`; it never uses clamping to disguise invalid input.

For vector dimension \(n\), the guarded calculation takes \(O(n)\) time. The teaching implementation stores two scaled arrays in Python and uses scalar accumulation in Java; a production implementation may fuse those passes while preserving identical validation.

### Example 4 — duplicate-safe RRF and citation gates

Fuse these two rankings with rank constant 60:

1. lexical: `payments-r7`, `cache-r2`, `payments-r7`
2. vector: `cache-r2`, `payments-r7`, `vectors-r3`

Within the first ranking, the second `payments-r7` is discarded before ranks are assigned. The contributions are:

| ID | lexical contribution | vector contribution | total |
|---|---:|---:|---:|
| `cache-r2` | \(1/62\) | \(1/61\) | \(1/61+1/62\) |
| `payments-r7` | \(1/61\) | \(1/62\) | \(1/61+1/62\) |
| `vectors-r3` | — | \(1/63\) | \(1/63\) |

The first two totals tie, so immutable ID order produces `cache-r2`, `payments-r7`, `vectors-r3`. Without per-ranking duplicate removal, one retriever could overweight a document by repeating it. With \(R\) rankings and \(N\) total entries, fusion is \(O(N)\) expected time for sets/maps plus \(O(U\log U)\) to sort \(U\) unique IDs.

```mermaid
sequenceDiagram
  participant R as Retrieved evidence
  participant M as Model output
  participant V as Validator
  R->>V: allow {payments-r7}
  M->>V: claim [payments-r7]
  V->>V: parse IDs and test subset
  alt missing or unknown ID
    V-->>M: reject output
  else IDs are members
    V-->>M: membership passed
  end
```

Membership is necessary but insufficient. “The moon is green `[payments-r7]`” passes an ID allow-list yet is not entailed by the passage. Keep three checks distinct: citation syntax, membership in supplied evidence, and claim-to-passage support. The offline lab owns the first two; a separate grounded-evaluation contract owns the third.

### Paired executable contract

The Java and Python programs use the same corpus, token grammar, scope-first retrieval, numerically guarded cosine, duplicate-safe RRF and citation rules. Both print the same parity line before their language-specific test summary:

```text
PARITY|payments-r7|NO_EVIDENCE|cache-r2,payments-r7,vectors-r3|1
```

Run them from the repository root:

```bash
java -ea study-guides/backend/spring-ai-python/OfflineRetrievalLabContracts.java
python3 study-guides/backend/spring-ai-python/offline_retrieval_lab_contracts.py
```

The 31 scenario groups per language cover positive ranking, adversarial cross-tenant text, unknown scope, no-overlap abstention, `top_k` bounds, repeated tokens, empty lexical input, cosine identities, overflow resistance, invalid vectors, RRF duplicates/ties, empty fusion and citation acceptance/rejection. Matching output demonstrates the cross-language algorithm contract; it does not prove the two standard libraries use identical internal data structures.

### Failure matrix and review checklist

| Fault | Detection point | Safe response |
|---|---|---|
| caller supplies tenant text directly | API/authentication boundary | ignore request scope; derive authorized scope from verified identity |
| unknown authorized scope | candidate selection | `NO_EVIDENCE` |
| `top_k` outside `[1,20]` | retrieval input validation | reject |
| empty/no-overlap query | lexical scorer/retriever | empty ranking, then abstain |
| vector mismatch, zero, `NaN` or infinity | cosine preconditions | reject before ranking |
| duplicate ID inside one ranking | RRF per-ranking set | count once at its first rank |
| equal fused score | final comparator | order by immutable ID |
| missing/foreign citation | membership validator | reject generated output |
| allowed citation with unsupported prose | entailment evaluator | reject or send to review; membership alone cannot decide |

Before replacing the corpus with a database or vector engine, preserve these oracles: unauthorized documents are never scored, no evidence is a first-class outcome, numerical errors fail closed, ranking is reproducible, and citations are checked against the exact supplied evidence set. Infrastructure integration should be tested against those contracts instead of redefining them.

### Current primary references

Verified on 23 October 2026:

- [Python `collections.Counter`](https://docs.python.org/3/library/collections.html#collections.Counter), [`math.isfinite`](https://docs.python.org/3/library/math.html#math.isfinite), and [`unittest`](https://docs.python.org/3/library/unittest.html) — standard-library counting, finiteness and executable-test semantics.
- [Java `Pattern`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html) and [`Comparator`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html) — reusable compiled token grammar and explicit total ordering.
- [Elasticsearch reciprocal rank fusion](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/reciprocal-rank-fusion) — the current rank contribution formula, one-based ranks and rank-constant constraints.
- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html) — deny by default, validate permission on every request, test authorization and do not let framework capabilities define requirements.

### Source provenance and continuation

The selected editable source is `atlas_guides/spring_ai_python_api_interview_guide.md` from `ai_cicd_spring_python_guides_complete.zip`, bundle SHA-256 `c52c8295ae7ff7d08013e30a8d7097480719db78104a91b79e367a382465dd31`, member SHA-256 `af25c41d54a3ca18dfc60b6cf828515ab6afb7e39cf64c7df94fe4a44eb4a553`. Sections 1–4 promote source lines 6–202. Section 4 expands the source's dependency-free lab with four requirement-driven examples, exact traces, numerical overflow guards, complexity analysis, adversarial authorization checks, paired 31-scenario executable contracts and primary references checked on 23 October 2026. The standalone HTML SHA-256 `6289a19e0ce5514c25d96d44806d47063e726244b5d55d13422234d3e499f3a3` is comparison evidence rather than a duplicate guide.

## Section 5 — A model call at the wire level

### Make the transport contract explicit

A model call is an HTTP exchange with unusually expensive and slow failure modes. The client library owns connection reuse, TLS negotiation, byte transfer and serialization. The application still owns endpoint trust, authentication material, request identity, admission, total deadline, replay policy, response limits, completion semantics, domain validation and safe observability.

| Requirement | Concrete contract | Failure behavior |
|---|---|---|
| trusted destination | base URI comes from deployment configuration; remote origins require HTTPS; reject URL credentials, query and fragment | fail startup or request construction |
| bounded request | pin model/revision policy, input limit, output-token limit and non-streaming/streaming mode | reject before acquiring scarce model capacity |
| bounded transport | reuse one client/pool; set pool, connect, write and read-inactivity timeouts beneath an outer deadline | return a phase-specific dependency failure |
| replay safety | distinguish “not sent,” transient response and unknown result after send; tool/effect calls are not blind retries | reconcile effects or surface unknown outcome |
| layered acceptance | validate status, media type, bytes, JSON/provider envelope, completion reason, schema and business rules | reject, abstain or mark incomplete |
| useful telemetry | record IDs, model identity, phase latency, outcome, token counts and bounded error class | never log secrets or full prompts by default |

`stream: false` is useful when the application must validate one complete JSON value before returning it. Streaming changes the protocol contract: the client must parse a sequence, enforce cumulative byte/token/time bounds, detect terminal completion, cancel on disconnect, and avoid exposing an answer before validation permits it.

```mermaid
sequenceDiagram
  participant A as Application
  participant P as Reused client and pool
  participant M as Model runtime
  participant V as Validator
  A->>A: authorize, admit, assign request ID
  A->>P: bounded request + outer deadline
  P->>M: TLS/auth + POST /api/chat
  M-->>P: status + bounded JSON
  P-->>A: parsed provider envelope
  A->>V: completion + schema + business checks
  V-->>A: accepted | incomplete | rejected
```

### Example 1 — trace one complete Ollama chat exchange

For a trusted local runtime, `http://127.0.0.1:11434` is a reasonable development origin. A remote runtime must use an authenticated HTTPS origin chosen by configuration, not a caller-supplied URL. This representative request asks the runtime for one complete response and caps generation:

```http
POST /api/chat HTTP/1.1
Host: 127.0.0.1:11434
Content-Type: application/json
X-Request-ID: req_01HZX9A2

{
  "model": "qwen3:8b",
  "messages": [{"role": "user", "content": "Classify: duplicate invoice"}],
  "stream": false,
  "options": {"temperature": 0, "num_predict": 120}
}
```

A representative successful envelope is:

```json
{
  "model": "qwen3:8b",
  "message": {"role": "assistant", "content": "billing"},
  "done": true,
  "done_reason": "stop",
  "total_duration": 1850000000,
  "load_duration": 120000000,
  "prompt_eval_count": 900,
  "eval_count": 100
}
```

Trace the fields by owner:

| Check | Observed | Interpretation |
|---|---:|---|
| HTTP status/media type | `200`, `application/json` | transport envelope may be parsed; content is not accepted yet |
| body limit | `640 < 1,048,576` bytes | bounded memory use |
| requested/returned model | `qwen3:8b` | expected runtime identity; a production alias policy must resolve to immutable release evidence |
| completion | `done=true`, `done_reason=stop` | provider says generation terminated normally |
| domain value | `billing` | accepted only if it is in the application's category allow-list |
| usage | 900 prompt + 100 output tokens | 1,000 tokens for cost/capacity evidence |

Ollama can also accept `format: "json"` or a JSON Schema. That narrows syntax; it does not prove truth, authorization or business validity. In Spring AI, `ChatClient` can hide the wire DTO and map structured output, but the same endpoint, deadline, completion and domain contracts must remain visible at the adapter boundary.

### Example 2 — allocate timeout and concurrency budgets

Suppose the API has a 20-second dependency deadline. One explicit phase allocation is:

| Phase | Ceiling | What expiry means |
|---|---:|---|
| wait for pool slot | 250 ms | local saturation; model request was not sent |
| connect/TLS | 2,000 ms | origin unavailable or handshake too slow |
| write request | 1,000 ms | upload stalled; whether bytes were sent matters to replay |
| read inactivity | 16,750 ms | no response bytes within the allowed interval |
| outer stopwatch | 20,000 ms | absolute application deadline across every phase and retry |

HTTPX exposes pool, connect, write and read timeouts. They are phase/inactivity bounds, not a substitute for the absolute stopwatch across acquisition, transfer, parsing, validation and retries. Create one `Client`/`AsyncClient` for its lifecycle so connection pooling works; do not instantiate it inside the hot request loop.

```python
timeout = httpx.Timeout(pool=0.250, connect=2.0, write=1.0, read=16.750)
limits = httpx.Limits(max_connections=480, max_keepalive_connections=120)
client = httpx.AsyncClient(base_url=MODEL_ORIGIN, timeout=timeout, limits=limits)
# Wrap the complete operation in the application's 20 s deadline.
```

At 50 admitted requests/second and 8 seconds mean dependency latency, Little's Law gives (L=\lambda W=50\times8=400) mean in-flight calls. A 64-connection default would queue this target before the model is necessarily saturated. A 480 hard cap provides an illustrative 20% headroom; it is not a universal setting. Measure latency distribution and runtime concurrency, then bound the pool, admission queue and model scheduler consistently. When all 480 slots are committed, reject or shed according to the API contract rather than creating an unbounded queue.

```mermaid
flowchart TB
  IN["Admitted call: 20 s outer deadline"] --> POOL["Pool: 0.25 s"]
  POOL --> CONN["Connect/TLS: 2 s"]
  CONN --> WRITE["Write: 1 s"]
  WRITE --> READ["Read inactivity: 16.75 s"]
  READ --> VAL["Parse and validate before deadline"]
```

### Example 3 — retry by observed state, not exception name alone

HTTP `POST` is not inherently idempotent. A read-only model inference can be *application-classified* as replayable, but a retry may still duplicate billing and return different text. Once tools or other effects are possible, a model request is not pure inference: preserve a logical operation ID and reconcile the effect instead of blindly replaying the turn.

| Observation | Pure inference | Tool/effect-capable request |
|---|---|---|
| pool/connect failure proved no bytes sent | retry within attempt/deadline caps | retry; external effect was not requested |
| `429`/`503` response | retry only under explicit policy; honor valid `Retry-After`, add bounded jitter | generally do not replay the whole turn; follow provider/tool reconciliation contract |
| read timeout after request bytes sent | outcome unknown; retry only if duplicate cost/nondeterminism are explicitly accepted | do not retry blindly; reconcile by stable operation ID |
| `400`/`401`/`403` or schema rejection | fail; fix request, credentials or contract | fail closed |
| attempt/outer deadline exhausted | return typed dependency failure | return typed failure or unknown-effect state |

The same stable logical request ID is useful for traces and provider-supported deduplication, but a header by itself does not make a provider idempotent. Bound attempts, include retry delay inside the original deadline, and cap `Retry-After` according to product latency policy.

```mermaid
stateDiagram-v2
  [*] --> Attempt
  Attempt --> Retry: proved not sent
  Attempt --> Retry: replayable 429/503
  Attempt --> Unknown: timeout after send
  Attempt --> Fail: permanent response
  Retry --> Attempt: budget remains
  Retry --> Fail: cap/deadline reached
  Unknown --> Retry: pure + duplicate risk accepted
  Unknown --> Reconcile: tools or effects
  Attempt --> Done: validated completion
```

### Example 4 — validate in layers and keep cost arithmetic auditable

Validation is ordered so an attacker or broken runtime cannot skip directly to domain parsing:

```mermaid
flowchart TB
  S["Status, media type, byte limit"] --> J["JSON parse"]
  J --> P["Provider envelope and model identity"]
  P --> C{"done reason"}
  C -->|stop| D["Schema + business rules"]
  C -->|length| I["INCOMPLETE: never treat as full answer"]
  C -->|unknown| R["Reject unknown completion"]
  D --> A["ACCEPTED or abstain"]
```

For the example response, assume illustrative rates of $2 per million input tokens and $8 per million output tokens. Use integer microdollars and round each charged component upward:

\[
\lceil 900\times2{,}000{,}000/1{,}000{,}000\rceil+
\lceil 100\times8{,}000{,}000/1{,}000{,}000\rceil
=1{,}800+800=2{,}600\text{ microdollars}.
\]

That arithmetic is evidence, not a claim about a current provider price. Record the actual rate-card version separately. A response with `done=true` and `done_reason=length` is transport-complete but application-incomplete; do not parse a truncated JSON object as a successful classification.

Safe telemetry should include request/trace ID, configured and returned model identity, attempt number, pool/connect/write/read/total latency, status/error class, byte count, completion reason, token counts, validation outcome and rate-card version. Redact authorization headers. Hash, sample or omit prompts and answers unless a reviewed purpose, access policy and retention period require their content. Keep metric labels bounded—request IDs belong in traces/logs, not high-cardinality metric dimensions.

### Paired executable contract

The dependency-free Java/Python pair implements the same endpoint, 20-second phase budget, retry state, completion validation, integer-cost and Little's-Law examples:

```bash
java -ea study-guides/backend/spring-ai-python/ModelWireContracts.java
python3 study-guides/backend/spring-ai-python/model_wire_contracts.py
```

Both print this parity trace before their language-specific 30-scenario summary:

```text
PARITY|RETRY_1000|ACCEPTED|INCOMPLETE|400|2600
```

The contracts prove decision logic without opening a socket. They do not prove a live Ollama/Spring AI/provider integration, TLS configuration, pool tuning or model behavior.

### Current primary references

Verified on 23 October 2026:

- [Ollama chat API](https://docs.ollama.com/api/chat) and [structured outputs](https://docs.ollama.com/capabilities/structured-outputs) — request fields, non-streaming mode, response completion/usage fields and JSON/JSON-Schema formatting.
- [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html) — synchronous/reactive fluent client, structured mapping, advisors and the warning around sensitive logging.
- [HTTP Semantics, RFC 9110](https://www.rfc-editor.org/rfc/rfc9110) and [Additional HTTP Status Codes, RFC 6585](https://www.rfc-editor.org/rfc/rfc6585) — method/idempotency semantics, `Retry-After`, `429` and response handling.
- [HTTPX timeouts](https://www.python-httpx.org/advanced/timeouts/), [resource limits](https://www.python-httpx.org/advanced/resource-limits/), [clients](https://www.python-httpx.org/advanced/clients/) and [exceptions](https://www.python-httpx.org/exceptions/) — phase timeouts, pooling/reuse, bounds and failure taxonomy.

### Source provenance and continuation

The confirmed editable range is source lines 203–263, ending immediately before Section 6. The matching standalone HTML range is lines 388–434, with Section 6 beginning at line 435; its substance agrees with the editable member, so no duplicate guide was created. Section 5 preserves the source's Ollama and Spring `ChatClient` model-call boundary, then adds four requirement-driven traces, phase/deadline arithmetic, replay-aware failure states, layered validation, capacity/cost evidence, four diagrams and paired 30-scenario contracts. Section 6, **Complete Python RAG endpoint: trace every subcomponent**, begins at source line 264 and is promoted below; the source is not complete.

## 6. Complete Python RAG endpoint: trace every subcomponent

This endpoint is a deliberately small, local RAG laboratory: FastAPI owns HTTP integration and lifespan, Pydantic bounds DTO fields, one process-scoped HTTPX client talks to Ollama, and standard-library functions validate vectors, ranking and citations. The corpus is fixed to a synthetic `acme` scope. That is a safe demo boundary, not authenticated tenancy; production retrieval must derive scope from validated identity and enforce it before search.

The contract is explicit:

- startup either publishes one complete immutable retrieval snapshot or fails readiness;
- a parsed question is 3–2,000 characters and nonblank, while the server/proxy separately bounds request bytes before parsing;
- admission waits at most 50 ms for one of four process-local permits;
- a 12-second outer deadline contains admission, embedding, retrieval, generation and validation; HTTPX phase timeouts remain separate guards;
- embeddings must have the expected count, one nonzero dimension, finite coordinates and nonzero norms;
- ranking is descending cosine with document ID as the deterministic tie-breaker;
- an uncalibrated “always top two” demo is not a production no-answer policy;
- supplied citation membership and semantic support are separate checks; and
- every acquired permit is released exactly once, including error and cancellation paths.

### Example 1 — publish an all-or-nothing startup snapshot

Given three documents, `/api/embed` must return three vectors. Validate all vectors into temporary state first; only then publish the document/vector pair used by requests. If vector 2 contains `NaN`, vector 3 has a different dimension, or the response contains only two vectors, startup fails and the old or absent snapshot remains authoritative. Never expose “documents from revision B with vectors from revision A.”

```python
def checked_snapshot(documents, vectors):
    if not documents or len(vectors) != len(documents):
        raise ValueError("embedding count mismatch")
    dimension = len(vectors[0])
    if dimension == 0:
        raise ValueError("empty embedding")
    frozen = []
    for vector in vectors:
        if len(vector) != dimension or not all(math.isfinite(x) for x in vector):
            raise ValueError("invalid embedding shape or coordinate")
        if math.sqrt(sum(x * x for x in vector)) == 0:
            raise ValueError("zero-norm embedding")
        frozen.append(tuple(vector))
    return tuple(documents), tuple(frozen), dimension  # publish this object atomically
```

```mermaid
stateDiagram-v2
  [*] --> Loading
  Loading --> Validating: embed all documents
  Validating --> Ready: publish complete snapshot
  Validating --> Failed: count, shape, finite or norm check fails
  Ready --> Serving
  Failed --> [*]
```

FastAPI lifespan code before `yield` runs before the application accepts requests, and code after `yield` performs cleanup. Use that boundary to create one reusable client and one coherent snapshot per process. A successful upstream status is necessary but not sufficient evidence that an embedding response is usable.

### Example 2 — bound queueing and the whole attempt

For three Uvicorn workers and four permits per worker, the process-local upper bound is `3 × 4 = 12` admitted handlers, not four globally. It is also not a provider quota: Pods and replicas multiply it again. Enforce a fleet-wide budget at an upstream admission layer or distributed limiter when that is the requirement.

| Observation | Endpoint result | Why |
|---|---:|---|
| blank or field longer than 2,000 characters | `422` | request contract failed |
| no permit within 50 ms | `429` | bounded load shedding; do not hide an unbounded queue |
| HTTPX connect/read/write/pool failure | `502` | dependency transport failed |
| any admitted attempt exceeds 12 seconds | `504` | application deadline expired |
| valid request but calibrated retrieval finds no evidence | `200` abstention | expected domain outcome, not an infrastructure error |

```mermaid
flowchart TB
  Q["Validated question"] --> A{"Permit within 50 ms?"}
  A -->|no| R["429: shed"]
  A -->|yes| D["12 s outer deadline"]
  D --> E["embed → retrieve → generate → validate"]
  E --> O["answer or typed abstention"]
  D -->|expires| T["504"]
```

Python documents that a semaphore decrements on acquisition and increments on release; its preferred manual equivalent uses `try/finally`. `asyncio.timeout()` cancels the current task when its deadline expires and converts cancellation to `TimeoutError` outside the context. HTTPX connect, read, write and pool timeouts constrain individual network phases; none alone is an end-to-end request deadline. Local cancellation also does not prove the upstream stopped computing.

### Example 3 — deterministic retrieval, untrusted context and two gates

Suppose a normalized query produces these scores:

| Document | Cosine | Rank |
|---|---:|---:|
| `payments-r7` | `1.000` | 1 |
| `runbook-r2` | `0.970` | 2 |
| `catalog-r1` | `-1.000` | 3 |

Descending score selects the first two. If equal scores occur, ascending stable document ID breaks the tie, making tests and incident replay deterministic. Context retains document IDs and revisions and is delimited as untrusted data; a sentence inside evidence that says “ignore the system message” is not executable instruction.

The output passes two different gates:

1. **Membership:** every cited ID exists in the selected snapshot, and at least one citation is present.
2. **Support:** the cited evidence actually entails the answer claim.

Membership can be exact code. Support needs a domain-specific verifier, reviewed structured extraction, or a conservative abstention policy; a citation-shaped token is not proof. If the best score is below a versioned, evaluated threshold, return a typed `ABSTAIN` without calling generation. Record the embedding model, corpus revision and threshold version with the decision.

```mermaid
flowchart TB
  V["Validated query vector"] --> C["Exact cosine for one snapshot"]
  C --> S["Sort: score desc, ID asc"]
  S --> H{"Best score ≥ threshold?"}
  H -->|no| AB["ABSTAIN"]
  H -->|yes| G["Generate from delimited evidence"]
  G --> M{"Citations are selected IDs?"}
  M -->|no| X["502 malformed dependency output"]
  M -->|yes| P{"Claims supported?"}
  P -->|no| AB
  P -->|yes| OK["ANSWER"]
```

Ollama's `/api/embed` accepts one string or a list and returns an `embeddings` array. Use the same pinned embedding model for corpus and query vectors. The API currently documents L2-normalized output, but local validation still protects the application from malformed, mismatched or substituted responses.

### Example 4 — map failure by meaning and release once

Acquire a permit only after request validation, set `acquired = True` immediately after success, and release in `finally` only when that flag is true. This avoids both leaks and over-release. `asyncio.BoundedSemaphore` can additionally expose over-release during development, but it remains process-local.

```mermaid
stateDiagram-v2
  [*] --> Waiting
  Waiting --> Shed: admission timeout
  Waiting --> Held: acquired
  Held --> Answer: validated supported result
  Held --> Abstain: no supported evidence
  Held --> BadGateway: transport or malformed dependency output
  Held --> GatewayTimeout: outer deadline
  Answer --> Released
  Abstain --> Released
  BadGateway --> Released
  GatewayTimeout --> Released
  Released --> [*]
```

Do not collapse all failures into `500`, and do not convert no-evidence into `502`. A malformed embedding/chat envelope or an invented citation is a dependency-contract failure (`502`); the outer deadline is `504`; admission exhaustion is `429`; a validated absence of evidence is a successful abstention. Safe telemetry includes trace ID, snapshot/model revisions, queue wait, permit outcome, phase and total latency, selected IDs/scores, validation outcome and status. Do not place raw questions, answers or document text in logs by default.

### Paired executable contract

The dependency-free pair exercises snapshot publication, DTO bounds, exact cosine and tie order, abstention, citation membership/support, fleet-capacity arithmetic, failure mapping and exactly-once release. It also runs 10,000 seeded cosine self-similarity trials:

```bash
java -ea study-guides/backend/spring-ai-python/PythonRagEndpointContracts.java
python3 study-guides/backend/spring-ai-python/python_rag_endpoint_contracts.py
```

Both print this parity trace before their language-specific summary:

```text
PARITY|READY|ANSWER|ABSTAIN|429|504|502|RELEASED
```

The pair proves the pure decision contracts without third-party packages or sockets. It does not prove FastAPI/Pydantic serialization, ASGI body limits, HTTPX/Ollama integration, cancellation propagation, authentication, multi-process coordination, threshold quality or semantic support.

### Current primary references

Verified on 23 October 2026:

- [FastAPI lifespan events](https://fastapi.tiangolo.com/advanced/events/) — before/after-`yield` startup and shutdown boundaries for shared resources.
- [Python `asyncio` synchronization primitives](https://docs.python.org/3/library/asyncio-sync.html) and [timeouts](https://docs.python.org/3/library/asyncio-task.html#timeouts) — semaphore acquire/release behavior, `try/finally`, timeout cancellation and `TimeoutError` conversion.
- [HTTPX async support](https://www.python-httpx.org/async/), [timeouts](https://www.python-httpx.org/advanced/timeouts/), [resource limits](https://www.python-httpx.org/advanced/resource-limits/) and [exceptions](https://www.python-httpx.org/exceptions/) — reusable async clients, connection bounds, phase-specific timeout semantics and transport failure taxonomy.
- [Ollama embedding API](https://docs.ollama.com/api/embed) and [embedding capability guide](https://docs.ollama.com/capabilities/embeddings) — batch `input`, returned `embeddings`, normalized-vector behavior and consistent-model guidance.

### Source provenance and continuation

The confirmed editable range is source lines 264–427, ending immediately before Section 7. The matching standalone HTML range is lines 435–579, with Section 7 beginning at line 580; it describes the same endpoint and was used as a comparison, not promoted as a duplicate. Section 6 retains the source's complete FastAPI/Pydantic/HTTPX/Ollama path and local-run boundary, then adds four requirement-driven traces, calibrated-abstention semantics, failure/status mapping, four diagrams and paired 32-scenario/10,000-trial contracts. Section 7, **Complete Spring AI RAG path**, begins at source line 428 and is promoted below. Sections 8–25 remain queued; the source is not complete.

## 7. Complete Spring AI RAG path: make the lifecycle observable

The Spring implementation should satisfy the same business contract as Section 6 without pretending that the framework removes lifecycle, authorization, capacity or grounding decisions. `EmbeddingModel` owns vector-provider integration, `ChatClient` owns the fluent chat call, and Spring Boot owns dependency injection, availability and server shutdown. The application still owns a coherent corpus snapshot, trusted scope, admission, total deadline, deterministic ranking and output acceptance.

Write the contract before wiring beans:

| Boundary | Required invariant | Failure meaning |
|---|---|---|
| release identity | resolved Boot/Spring AI versions plus immutable chat and embedding model identities | deployment evidence is incomplete; do not call it reproducible |
| startup publication | one generation contains matching evidence and validated vectors | remain unready; never publish a partial index |
| request admission | caller is validated, capacity is acquired once, and one absolute deadline follows the attempt | `429` before send or `504` after deadline, not an unbounded queue |
| retrieval | trusted scope and one snapshot precede deterministic score/ID ordering | empty calibrated result is `ABSTAIN`, not infrastructure failure |
| generation | only delimited selected evidence enters the prompt | model output is a proposal, never authority |
| acceptance | citations are selected IDs and claims are supported | malformed/invented references are dependency failure; unsupported claims abstain |
| shutdown | readiness refuses new traffic while bounded in-flight work drains | grace expiry is observable cancellation, not proof the provider stopped |

### Example 1 — pin the deployable combination, not an isolated snippet

The Library source used Spring Boot `4.1.1` and Spring AI `2.0.1`. On 24 October 2026 the official stable documentation lists Boot `4.1.2` and Spring AI `2.0.2`. A copied POM can therefore be internally plausible and already stale. Choose a tested combination, commit the lock, and record the resolved dependency graph with the model artifacts. This bounded guide uses the current stable pair as an example, not a promise that every future starter remains compatible:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.1.2</version>
</parent>
<properties>
  <java.version>21</java.version>
  <spring-ai.version>2.0.2</spring-ai.version>
</properties>
<dependencyManagement><dependencies><dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-bom</artifactId>
  <version>${spring-ai.version}</version><type>pom</type><scope>import</scope>
</dependency></dependencies></dependencyManagement>
<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
  <dependency><groupId>org.springframework.ai</groupId><artifactId>spring-ai-starter-model-ollama</artifactId></dependency>
</dependencies>
```

The BOM aligns Spring AI artifacts; it does not select the chat model, embedding model, Ollama runtime, corpus or prompt. Exclude the MCP server starter unless this service actually exposes an MCP server. Extra starters widen configuration, transport and security surface.

Use current property families and externalize model identity:

```yaml
spring:
  ai:
    model:
      chat: ollama
      embedding: ollama
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://127.0.0.1:11434}
      init:
        pull-model-strategy: never
      chat:
        model: ${CHAT_MODEL}
        temperature: 0.0
        num-predict: 400
      embedding:
        model: ${EMBED_MODEL}
        truncate: false
  lifecycle:
    timeout-per-shutdown-phase: 20s
management.endpoint.health.probes.enabled: true
```

Trace one release as `boot=4.1.2`, `spring-ai=2.0.2`, `ollama=<runtime digest>`, `chat=<artifact digest>`, `embedding=<artifact digest>`, `corpus=42`, `prompt=rag-v9`, `threshold=payments-v4`. A model name alone is mutable evidence. `pull-model-strategy: never` keeps an ordinary API restart from silently downloading an artifact; provisioning must prove the required models exist.

```mermaid
flowchart TB
  BUILD["Resolved Boot + Spring AI graph"] --> REL["Release evidence"]
  CHAT["Chat model digest"] --> REL
  EMB["Embedding model digest"] --> REL
  CORP["Corpus + prompt + threshold revisions"] --> REL
  REL --> DEP["Deploy one tested combination"]
```

### Example 2 — build privately, publish one ready snapshot

Do not perform a large corpus embedding loop in every request or expose a mutable list while startup is still filling it. A production ingestion job usually builds the durable index separately. For this fixed in-memory lab, build temporary evidence/vector state during startup, validate the entire generation, freeze/defensively copy it, then publish one reference and mark readiness accepting traffic.

```java
record Snapshot(String generation, List<Evidence> evidence,
                List<double[]> vectors, int dimension) {}

final AtomicReference<Snapshot> current = new AtomicReference<>();

Snapshot buildCandidate(List<Evidence> docs) {
    List<double[]> vectors = docs.stream()
        .map(doc -> embeddings.embed(doc.text()))
        .map(floatVector -> widenAndValidate(floatVector))
        .toList();
    return validateAndFreeze("corpus-42/embed-v3", docs, vectors);
}

void start() {
    Snapshot candidate = buildCandidate(FIXTURE); // not externally visible
    current.set(candidate);                       // one publication point
    availability.publishEvent(new AvailabilityChangeEvent<>(
        this, ReadinessState.ACCEPTING_TRAFFIC));
}
```

If vector 2 has a different dimension, vector 3 contains `NaN`, two evidence IDs collide, or the provider returns only two vectors for three documents, the candidate is rejected and readiness stays closed. Merely having a non-null `EmbeddingModel` bean is liveness, not proof this service can answer from a coherent generation.

Trace for `corpus-42/embed-v3`:

| Step | State | Published snapshot |
|---:|---|---|
| 1 | three evidence records loaded | none |
| 2 | vectors 1–2 valid; vector 3 wrong dimension | none |
| 3 | startup attempt fails and reports cause | none |
| 4 | next attempt validates all three and clones arrays | generation `corpus-42/embed-v3` |
| 5 | readiness becomes accepting | same immutable generation |

```mermaid
stateDiagram-v2
  [*] --> Refusing
  Refusing --> Building: embed fixed corpus
  Building --> Refusing: any validation fails
  Building --> Published: all records valid
  Published --> Accepting: publish readiness
  Accepting --> Refusing: shutdown begins
```

### Example 3 — carry one deadline through admission, embedding and chat

The source's immediate `Semaphore.tryAcquire()` is valid fail-fast load shedding. If the API contract allows a 50 ms admission wait, use timed `tryAcquire(50, MILLISECONDS)` and restore interruption rather than silently swallowing it. Release only after successful acquisition and only in `finally`:

```java
boolean acquired = false;
long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
try {
    acquired = slots.tryAcquire(50, TimeUnit.MILLISECONDS);
    if (!acquired) throw new Rejected("model concurrency exhausted");
    long remainingMs = remainingMillis(deadline, System.nanoTime());
    if (remainingMs == 0) throw new DeadlineExceeded();
    return answerWithin(question, snapshot(), deadline); // every adapter gets remaining time
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    throw new RequestCancelled(interrupted);
} finally {
    if (acquired) slots.release();
}
```

For three JVM processes and four permits each, the upper bound is `3 × 4 = 12` admitted calls. Four is not a fleet quota; replicas multiply it. Java 21 virtual threads can reduce thread-per-request overhead but do not increase Ollama/GPU capacity. Spring's graceful shutdown refuses new traffic and gives in-flight requests the configured phase timeout; it does not replace the request deadline or guarantee provider cancellation.

```mermaid
sequenceDiagram
  participant C as Client
  participant A as Spring API
  participant E as EmbeddingModel
  participant M as ChatClient
  C->>A: validated question
  A->>A: permit ≤ 50 ms; deadline = now + 12 s
  A->>E: embed with remaining budget
  E-->>A: validated query vector
  A->>A: rank one snapshot
  A->>M: bounded evidence + remaining budget
  M-->>A: proposed answer + citations
  A->>A: validate, release once
  A-->>C: answer | abstain | typed failure
```

Useful low-cardinality telemetry is stage, outcome, model/corpus revision, permit outcome and latency bucket. Spring AI instruments `ChatClient`, `ChatModel`, `EmbeddingModel` and `VectorStore`; do not enable raw prompt/response logging by default because evidence and questions may be sensitive.

### Example 4 — separate model failure, citation membership and support

The source wraps almost every `RuntimeException` as `502`. That is simple, but it can erase application bugs and policy outcomes. Validate the request before admission; use typed adapter exceptions; map the outer deadline separately; and keep successful no-evidence/unsupported outcomes distinct from malformed provider output.

For selected IDs `{payments-r7, runbook-r2}`:

| Candidate output | Membership | Support | Result |
|---|---|---|---|
| “Check migration compatibility `[payments-r7]`” | pass | pass | `200 ANSWER` |
| “Rollback is always safe `[payments-r7]`” | pass | fail | `200 ABSTAIN` |
| “Use document `[invented-r9]`” | fail | not evaluated | `502 DEPENDENCY_OUTPUT` |
| empty content or no citation | fail | not evaluated | `502 DEPENDENCY_OUTPUT` |
| best score below calibrated threshold | no generation call | n/a | `200 ABSTAIN` |

Keep the evidence ID and revision in the returned envelope. Returning all top-k documents even when only one was cited blurs provenance; return cited evidence for the accepted answer and keep the full selected set only in access-controlled trace evidence. A regex can prove token membership, not semantic support. Use domain checks, reviewed structured extraction, calibrated verification or conservative abstention.

```mermaid
flowchart TB
  R["Rank one authorized snapshot"] --> T{"Best score passes threshold?"}
  T -->|no| AB["200 ABSTAIN"]
  T -->|yes| G["Generate from delimited evidence"]
  G --> M{"Nonempty citations ⊆ selected IDs?"}
  M -->|no| D["502 dependency output"]
  M -->|yes| S{"Every claim supported?"}
  S -->|no| AB
  S -->|yes| OK["200 ANSWER + cited evidence"]
```

### Paired executable contract

The dependency-free Java/Python pair proves release identity, all-or-nothing snapshot publication, defensive vector copies, deterministic ranking, calibrated abstention, citation membership/support, deadline arithmetic, process/fleet capacity and exactly-once release:

```bash
java -ea study-guides/backend/spring-ai-python/SpringRagPathContracts.java
python3 study-guides/backend/spring-ai-python/spring_rag_path_contracts.py
```

Both print:

```text
PARITY|CONFIGURED|READY|ANSWER|ABSTAIN|429|504|502|RELEASED
```

Each implementation executes 36 scenario groups and 10,000 seeded cosine checks. These pure contracts do not compile Spring AI, start a servlet container, call Ollama, prove cancellation propagation, calibrate the retrieval threshold, authorize real tenants, render Actuator probes or measure load.

### Current primary references

Verified on 24 October 2026:

- [Spring AI dependency management](https://docs.spring.io/spring-ai/reference/getting-started.html) — the BOM aligns Spring AI modules but is not a plugin or model lock.
- [Spring AI Ollama chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html) and [Embedding Model API](https://docs.spring.io/spring-ai/reference/api/embeddings.html) — current starter/property families, model options and vector dimensionality.
- [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html) and [observability](https://docs.spring.io/spring-ai/reference/observability/index.html) — fluent calls, advisor logging caution, metrics and traces for AI components.
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html), [application availability](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html) and [graceful shutdown](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html) — current stable release evidence, readiness transitions and bounded shutdown behavior.
- [Java `Semaphore`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html) — immediate/timed acquisition, interruption and application-owned release discipline.

### Source provenance and continuation

The confirmed editable range is source lines 428–620, ending immediately before Section 8. The matching standalone HTML range is lines 580–752, with Section 8 beginning at line 753; dependency, configuration, service and build/run content agree. The canonical section preserves that Spring AI path but updates the dated `4.1.1`/`2.0.1` example to the current documented `4.1.2`/`2.0.2` pair, removes the unrelated MCP starter from the minimal RAG dependency set, and makes lifecycle publication, total deadlines, typed failures, grounding and shutdown behavior testable. Section 8, **Replacing in-memory vectors with durable retrieval**, begins at editable source line 621 / standalone HTML line 753 and is promoted below. Sections 9–25 remain queued; the source is not complete.

## 8. Replacing in-memory vectors with durable retrieval: preserve scope, generation and evidence identity

Moving vectors to PostgreSQL is not a storage-only refactor. The durable path must preserve the same answer contract as the in-memory lab while adding migration ownership, authorization predicates, generation cutover, query planning, connection-pool pressure and index-recall evidence. A `VectorStore` removes adapter boilerplate; it does not decide who may see a chunk, which corpus generation is active, whether an approximate index returned enough authorized neighbors or how long a request may hold a database connection.

Write these invariants before choosing HNSW, IVFFlat or exact search:

| Boundary | Required invariant | Failure meaning |
|---|---|---|
| schema and model identity | dimension, distance operator, embedding-model revision and chunk schema are one reviewed release contract | reject incompatible ingestion or query vectors; never silently truncate |
| row authority | every row carries immutable tenant, document, revision, chunk and generation identity | an unscoped query is a security defect, not a recall optimization |
| generation publication | ingestion writes a private generation; one small control record activates it only after validation | readers see the old complete generation or the new complete generation, never a mixture |
| retrieval | authorization and active generation constrain candidates before result acceptance; ordering is distance then stable ID | too few authorized results means abstain/fallback within policy, never broaden scope |
| ANN quality | approximate results are compared with an exact authorized baseline on representative slices | a fast plan with unacceptable recall fails the release gate |
| runtime budget | pool wait, SQL execution and result decoding share the caller's remaining deadline | pool exhaustion is admission pressure; statement expiry is retrieval timeout |
| resource ownership | the transaction and connection end before model generation or token streaming begins | a model call must not pin a scarce database session |

### Example 1 — ingest privately, validate, then switch one generation pointer

Suppose `corpus-42` is serving while a new embedding model builds `corpus-43`. Do not delete or mutate the active rows in place. Write immutable `corpus-43` rows, verify expected counts, unique identities, dimensions, finite values, source revisions and retrieval quality, then update one control row in a short transaction.

```sql
CREATE TABLE rag_generation (
  tenant_id text NOT NULL,
  generation text NOT NULL,
  embedding_model text NOT NULL,
  dimensions integer NOT NULL CHECK (dimensions > 0),
  state text NOT NULL CHECK (state IN ('BUILDING', 'READY', 'ACTIVE', 'RETIRED')),
  expected_chunks bigint NOT NULL CHECK (expected_chunks >= 0),
  PRIMARY KEY (tenant_id, generation)
);

CREATE TABLE rag_chunk (
  tenant_id text NOT NULL,
  generation text NOT NULL,
  document_id text NOT NULL,
  revision bigint NOT NULL,
  chunk_id text NOT NULL,
  body text NOT NULL,
  embedding vector(768) NOT NULL,
  PRIMARY KEY (tenant_id, generation, document_id, revision, chunk_id),
  FOREIGN KEY (tenant_id, generation)
    REFERENCES rag_generation (tenant_id, generation)
);
```

The fixed `vector(768)` column makes a wrong dimension fail at storage. The generation record still stores `dimensions=768` so release evidence can prove that application configuration and schema agree. A deployment may instead use one table per model family when dimensions change; it must not reinterpret old rows as vectors from the new model.

Trace a safe cutover for tenant `payments`:

| Step | `corpus-42` | `corpus-43` | Reader-visible generation |
|---:|---|---|---|
| 1 | `ACTIVE`, 2,400 chunks | absent | `corpus-42` |
| 2 | unchanged | `BUILDING`, 2,397/2,400 rows | `corpus-42` |
| 3 | unchanged | validator finds three missing rows | `corpus-42` |
| 4 | unchanged | rebuilt, 2,400 rows; exact/ANN gates pass; `READY` | `corpus-42` |
| 5 | marked `RETIRED` and retained for rollback | marked `ACTIVE` in one transaction | `corpus-43` |
| 6 | removed only after rollback window and no readers | unchanged | `corpus-43` |

```mermaid
stateDiagram-v2
  [*] --> Building
  Building --> Building: ingest immutable chunks
  Building --> Rejected: count, identity or vector check fails
  Building --> Ready: integrity and recall gates pass
  Ready --> Active: atomic control-row switch
  Active --> Retired: newer generation activates
  Retired --> [*]: rollback window and readers drain
```

Activation is control-plane data. Keep it small and strongly consistent. If authorization differs inside one tenant, include immutable ACL-group or policy-version metadata in each row and bind the trusted value as another predicate; never copy an untrusted request field directly into the filter.

### Example 2 — bind values, validate the vector, and make ordering replayable

The Spring AI path should use trusted server-side scope and the active generation in its metadata filter. Schema creation belongs to reviewed migrations; application startup should not receive broad extension or DDL privileges merely because automatic initialization exists.

```java
var f = new FilterExpressionBuilder();
var request = SearchRequest.builder()
    .query(question)
    .topK(6)
    .filterExpression(f.and(
        f.eq("tenant_id", authenticatedTenant),
        f.eq("generation", activeGeneration),
        f.eq("acl_group", authorizedGroup)).build())
    .build();
List<Document> candidates = vectorStore.similaritySearch(request);
```

The direct Python repository path is useful when SQL plans, deterministic tie handling or transaction-local settings must be explicit. Values stay in the driver parameter sequence. The embedding is rejected before SQL if its length differs, a coordinate is non-finite or the vector is all zeros.

```python
import math

def vector_literal(values: list[float], dimensions: int) -> str:
    if len(values) != dimensions or not all(math.isfinite(x) for x in values):
        raise ValueError("invalid embedding")
    if not any(x != 0.0 for x in values):
        raise ValueError("zero embedding")
    return "[" + ",".join(format(float(x), ".9g") for x in values) + "]"

def retrieve(conn, vector, *, tenant, generation, dimensions, k, timeout_ms):
    if not 1 <= k <= 50 or not 1 <= timeout_ms <= 5_000:
        raise ValueError("invalid retrieval bounds")
    literal = vector_literal(vector, dimensions)
    with conn.transaction():
        with conn.cursor() as cur:
            cur.execute("SET LOCAL statement_timeout = %s", (timeout_ms,))
            cur.execute("""
                SELECT document_id, revision, chunk_id, body,
                       embedding <=> %s::vector AS distance
                FROM rag_chunk
                WHERE tenant_id = %s AND generation = %s
                ORDER BY distance, document_id, revision, chunk_id
                LIMIT %s
            """, (literal, tenant, generation, k))
            return cur.fetchall()
```

`tenant` and `generation` may contain hostile-looking text without changing the SQL grammar because they are values, not string-built SQL. Table/column names cannot use value placeholders; keep identifiers static or build them only from an allowlist with the driver's SQL-composition API. The final ID tuple makes equal-distance results replayable across tests and traces.

```mermaid
flowchart TB
  I["Authenticated identity"] --> S["Trusted tenant + ACL scope"]
  G["Active generation record"] --> S
  Q["Validated query vector"] --> SQL["Parameterized SQL"]
  S --> SQL
  SQL --> O["Distance, then stable-ID order"]
  O --> R["Bounded evidence envelope"]
```

### Example 3 — measure filtered ANN against the exact authorized answer

Approximate indexes trade recall for latency. Filtering makes the trade more subtle: an ANN scan can visit global neighbors and discard rows that fail `tenant_id`, `generation` or ACL predicates, returning fewer than `k` authorized results even when more exist. That is not permission to retry without filters.

For a payments slice with exact authorized top five `{p1,p2,p3,p4,p5}`:

| Candidate plan | Returned IDs | Recall@5 | Decision |
|---|---|---:|---|
| exact scan | `p1,p2,p3,p4,p5` | 1.00 | baseline |
| HNSW default search budget | `p1,p2,p4` | 0.60 | fail: too few and below 0.95 gate |
| HNSW iterative strict scan | `p1,p2,p3,p4,p5` | 1.00 | pass for this slice |
| unscoped HNSW | `p1,c7,h2,p2,p3` | not evaluated | security failure, even if latency is excellent |

The pgvector project documents that approximate filtering is applied after the index scan and that iterative scans can visit more tuples until enough filtered results are found or a configured bound is reached. Strict iterative order preserves distance order; relaxed order may improve recall/latency and requires an outer reorder if strict ranking matters. Treat those settings as measured plan inputs, not universal defaults.

```mermaid
flowchart TB
  A["Exact authorized top-k baseline"] --> C["Compare candidate ANN plan"]
  C --> S{"Scope identical?"}
  S -->|no| X["Reject security defect"]
  S -->|yes| N{"Returned enough rows?"}
  N -->|no| F["Abstain or bounded exact fallback"]
  N -->|yes| R{"Recall and p95 meet gates?"}
  R -->|no| X
  R -->|yes| P["Promote index/settings evidence"]
```

Evaluate by tenant size, ACL selectivity, query family and corpus generation. Record the exact plan, pgvector/PostgreSQL versions, index parameters, recall distribution and tail latency. A global mean can hide a small tenant or restrictive ACL slice with zero useful neighbors. When a bounded exact fallback is permitted, it must use the same authorization predicates and remaining deadline.

### Example 4 — budget pool wait and SQL, then release before generation

Assume a request has 900 ms remaining after authentication and query embedding. Pool wait is capped at 100 ms and retrieval execution at 250 ms. The application gets a connection in 40 ms, sets a transaction-local 250 ms statement timeout, retrieves six rows in 75 ms, ends the transaction and returns the connection. Roughly 785 ms remains for prompt assembly, model generation and final validation. The connection is not held while the model streams.

| Phase | Cap | Actual | Remaining deadline | Resource state |
|---|---:|---:|---:|---|
| pool acquisition | 100 ms | 40 ms | 860 ms | connection held |
| transaction + vector query | min(250, 860) ms | 75 ms | 785 ms | connection held |
| decode/copy bounded rows | included | 0–small | about 785 ms | connection released |
| model generation + validation | remaining budget | variable | must finish before 0 | no DB resource held |

If the pool wait expires, return admission pressure (`503` with an intentional retry policy, or the API's documented overload status) before sending SQL. If PostgreSQL cancels the statement at its timeout, report a retrieval timeout; do not continue with partial rows. If caller cancellation arrives, request cancellation and still close the transaction, but remember that a client-side cancel attempt is not proof the server stopped immediately.

```mermaid
sequenceDiagram
  participant A as API
  participant P as Pool
  participant D as PostgreSQL
  participant M as Model
  A->>P: acquire within min(pool cap, remaining)
  P-->>A: connection
  A->>D: BEGIN; SET LOCAL statement_timeout
  A->>D: scoped vector query
  D-->>A: bounded rows or typed timeout
  A->>P: end transaction; release
  A->>M: evidence + remaining deadline
  M-->>A: proposed answer
  A->>A: validate citations/support
```

Pool size is a server-capacity decision, not a per-process convenience. Eight application instances with 12 connections each can consume 96 sessions before migrations, operators and other workloads. Bound the pool queue as well as its size, publish wait/timeout/utilization metrics, and size from database capacity and measured service time rather than thread count.

### Paired executable contract

The dependency-free pair models private-generation promotion, fixed vector validation, parameter/value separation, trusted-scope exact retrieval, stable tie order, ANN recall gating, deadline-derived statement budgets and exactly-once connection release:

```bash
java -ea study-guides/backend/spring-ai-python/DurableRetrievalContracts.java
python3 study-guides/backend/spring-ai-python/durable_retrieval_contracts.py
```

Both print the same first line:

```text
PARITY|GEN=corpus-43|SCOPE=payments|TOP=payments-r8|RECALL=1.000|POOL=RELEASED|TIMEOUT=250
```

The randomized portion injects unauthorized decoys and checks that exact results never escape the trusted tenant/generation, while independent score ordering agrees for 10,000 seeded trials. These pure contracts do not create PostgreSQL tables, exercise a real HNSW/IVFFlat plan, compile Spring AI, prove row-level-security policy, calibrate production recall, load-test a connection pool or verify cancellation on a live server.

### Current primary references

Verified on 24 October 2026:

- [Spring AI PGvector](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html) — current starter, dimensions, schema-initialization behavior, distance/index options and metadata filters.
- [Spring AI RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html) — advisors and retrieval components that can reduce wiring while leaving application policy observable.
- [pgvector](https://github.com/pgvector/pgvector) — exact/approximate operators, HNSW/IVFFlat indexes, filtering behavior and bounded iterative scans.
- [PostgreSQL client connection defaults](https://www.postgresql.org/docs/current/runtime-config-client.html) — `statement_timeout`, `lock_timeout` and idle-transaction limits.
- [Psycopg parameters](https://www.psycopg.org/psycopg3/docs/basic/params.html), [transactions](https://www.psycopg.org/psycopg3/docs/basic/transactions.html), [async behavior](https://www.psycopg.org/psycopg3/docs/advanced/async.html) and [pool API](https://www.psycopg.org/psycopg3/docs/api/pool.html) — value binding, transaction ownership, cancellation semantics, bounded wait queues and pool timeouts.

### Source provenance and continuation

The selected editable bundle member's Section 8 is source lines 621–677; Section 9 begins at line 678. The matching standalone HTML is lines 753–795; Section 9 begins at line 796. Both contain the same Spring `VectorStore`, parameterized psycopg and higher-level-advisor substance, so the HTML remains a comparison source rather than a duplicate guide. This canonical section retains those boundaries and adds generation-safe migration, exact-versus-ANN recall gates, deterministic scoped SQL, four worked traces, four diagrams, deadline/pool ownership and paired executable contracts. Section 9, **Structured outputs: validate syntax and business meaning**, begins at editable source line 678 / standalone HTML line 796. Sections 9–25 remain queued; the source is not complete.

## 9. Structured outputs: validate syntax, shape and business meaning

A model's JSON is untrusted input, even when a provider accepts a response schema or a framework maps the response directly to a record. Format instructions improve probability; they do not grant authority. A safe boundary distinguishes five outcomes: invalid JSON syntax, invalid structural schema, invalid application meaning, accepted output and repair exhaustion.

For an incident-summary response, define the contract before prompting:

| Layer | Requirement | Example rejection |
|---|---|---|
| envelope | UTF-8 response is at most 2,048 bytes and contains exactly one JSON document | prose before JSON, trailing document or oversized payload |
| syntax | RFC 8259 JSON; duplicate object names rejected rather than silently overwritten | two `service` fields |
| structure | exactly `service`, `severity`, `summary`, `evidence_ids`; no missing or unknown fields | model invents `auto_resolve: true` |
| field constraints | strings have bounds; severity is an enum; evidence is a nonempty unique list of at most eight bounded IDs | empty summary or repeated evidence ID |
| business meaning | service belongs to the authenticated request's allowed set and every evidence ID is in the exact supplied evidence envelope | valid-looking `foreign-99` citation |
| server authority | request ID, tenant, release, policy version, reviewer decision and side effects come from trusted application state | output-supplied `approved: true` |
| repair | each retry receives only bounded diagnostics, shares the original deadline and is fully revalidated | parse succeeds on retry but citation membership fails |

```mermaid
flowchart TB
  B["Bounded model bytes"] --> J{"One strict JSON document?"}
  J -->|no| X1["SYNTAX_INVALID"]
  J -->|yes| S{"Exact schema and limits?"}
  S -->|no| X2["SCHEMA_INVALID"]
  S -->|yes| M{"Trusted-scope meaning valid?"}
  M -->|no| X3["BUSINESS_INVALID"]
  M -->|yes| A["ACCEPTED + server provenance"]
```

Spring AI 2.0.2 describes `StructuredOutputConverter` as best effort and recommends schema validation. `BeanOutputConverter` derives Draft 2020-12 JSON Schema and maps JSON to a target type; `.entity(IncidentSummary.class)` is convenient syntax over that conversion. Neither successful deserialization nor a Java record constructor proves request-specific authorization, evidence provenance or domain policy. Likewise, Pydantic guarantees that its resulting model conforms to configured types and constraints; coercion is enabled in many ordinary configurations, so an external trust boundary should choose strictness and extra-field behavior deliberately.

### Example 1 — accept one fully traced summary

Trusted request state:

```text
request_id       = req-73                 # server generated
allowed_services = {payments}             # authentication + authorization
evidence_ids     = {evt-17, log-22}        # exact retrieved envelope
release          = corpus-43/qwen3:8b      # server deployment state
```

Candidate model output:

```json
{"service":"payments","severity":"HIGH","summary":"Payment errors rose after deploy.","evidence_ids":["evt-17","log-22"]}
```

Trace:

1. The 127 UTF-8 bytes are below the 2,048-byte cap; the strict decoder consumes one document and finds no duplicate names.
2. The key set is exact. Three scalar fields are strings, severity is in the four-value enum, and the evidence list is bounded and unique.
3. `payments` belongs to the trusted allowed-service set. Both proposed IDs are members of the evidence envelope supplied to this model call.
4. The application returns `ACCEPTED` and attaches `req-73`, the authenticated tenant, `corpus-43/qwen3:8b` and the validator policy version from server state. Those fields were never offered to the model as decision authority.

```mermaid
sequenceDiagram
  participant A as Authorized API
  participant R as Retriever
  participant M as Model
  participant V as Strict validator
  A->>R: trusted tenant + active generation
  R-->>A: evt-17, log-22
  A->>M: delimited evidence + response schema
  M-->>V: proposed JSON only
  A->>V: allowed service and evidence sets
  V-->>A: ACCEPTED structured value
  A->>A: attach request/release provenance
```

Membership is still not entailment. If the summary says “database loss occurred” while both passages discuss only elevated latency, citation membership passes but claim-to-passage support fails in a subsequent grounded-output gate. Keep syntax, structure, membership and support observable as separate checks so an incident can be attributed to the right stage.

### Example 2 — reject parser and schema traps before domain code

These candidates must not converge to the same generic “bad response” metric:

| Candidate | Typed result | Why |
|---|---|---|
| `{` | `SYNTAX_INVALID` | truncated document |
| valid object followed by `{} ` | `SYNTAX_INVALID` | trailing second value |
| two `service` names | `SYNTAX_INVALID` | duplicate-name ambiguity; last-value-wins is unsafe |
| `{"service":1,...}` | `SCHEMA_INVALID` | no implicit number-to-string conversion |
| exact fields plus `"auto_resolve":true` | `SCHEMA_INVALID` | unknown capability-like field |
| nine evidence IDs | `SCHEMA_INVALID` | cardinality exceeds the reviewed prompt/evaluation bound |
| `"summary":" x"` | `SCHEMA_INVALID` | noncanonical leading whitespace |

The Python standard decoder exposes `object_pairs_hook`, which lets the boundary reject duplicate names instead of accepting the final value. Pydantic can then express the declarative portion without silently accepting extras or coercing types:

```python
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field, field_validator

class IncidentSummary(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    service: str = Field(min_length=1, max_length=64)
    severity: Literal["LOW", "MEDIUM", "HIGH", "CRITICAL"]
    summary: str = Field(min_length=1, max_length=240)
    evidence_ids: list[str] = Field(min_length=1, max_length=8)

    @field_validator("summary")
    @classmethod
    def canonical_summary(cls, value: str) -> str:
        if value != value.strip():
            raise ValueError("summary must be trimmed")
        return value

    @field_validator("evidence_ids")
    @classmethod
    def unique_evidence(cls, value: list[str]) -> list[str]:
        if len(value) != len(set(value)):
            raise ValueError("duplicate evidence ID")
        return value
```

Run a byte-limited, duplicate-aware JSON decoder first, then `IncidentSummary.model_validate(decoded, strict=True)`. Calling `model_construct()` would intentionally bypass validation and is therefore wrong for model output. JSON Schema can express required fields, enums, maximum lengths, `uniqueItems` and `unevaluatedProperties: false`; it cannot express every request-specific semantic relationship. That is why structural and general-purpose semantic validation remain separate phases.

```mermaid
flowchart LR
  R["Raw response"] --> D["Duplicate-aware decoder"]
  D --> P["Pydantic strict model"]
  P --> C["Request-context checks"]
  C --> G["Grounded-claim checks"]
  G --> O["Typed outcome"]
```

### Example 3 — mapping is not authorization or automatic Jakarta Validation

This candidate is structurally valid:

```json
{"service":"orders","severity":"HIGH","summary":"Rollback advised.","evidence_ids":["foreign-99"]}
```

For the request in Example 1 it is `BUSINESS_INVALID` twice: `orders` is outside the allowed service set, and `foreign-99` was not supplied as evidence. Expanding either set from model output would reverse the trust boundary.

A Java record can carry structural annotations, but mapping JSON into the record does not universally mean that Jakarta Validation has run. Invoke the configured `Validator` explicitly and then apply request-context checks:

```java
record IncidentSummary(
    @jakarta.validation.constraints.Size(min = 1, max = 64) String service,
    Severity severity,
    @jakarta.validation.constraints.Size(min = 1, max = 240) String summary,
    @jakarta.validation.constraints.Size(min = 1, max = 8) List<String> evidenceIds) {}

IncidentSummary candidate = chatClient.prompt(prompt).call().entity(IncidentSummary.class);
Set<ConstraintViolation<IncidentSummary>> violations = validator.validate(candidate);
if (!violations.isEmpty()) throw new SchemaInvalid(violations);
if (!allowedServices.contains(candidate.service())) throw new BusinessInvalid("service");
if (!allowedEvidence.containsAll(candidate.evidenceIds())) throw new BusinessInvalid("evidence");
```

`validator.validate` covers the constraints actually declared and supported by the configured provider. Application code still owns uniqueness, request-specific subset relationships, policy lookups and evidence support. A converter exception maps to syntax/shape failure; a constraint violation maps to schema failure; a failed trusted-set comparison maps to business failure. Preserve those distinctions in logs and metrics without logging the full incident text by default.

```mermaid
flowchart TB
  E["entity(IncidentSummary.class)"] --> V["validator.validate(candidate)"]
  V --> U["Uniqueness and canonical-form rules"]
  U --> T["Trusted service/evidence subset"]
  T --> Q["Claim support / review policy"]
  Q --> P["Server-owned provenance + publish"]
```

### Example 4 — repair is a bounded state machine, not a loop until parse succeeds

Assume two attempts, 60 ms of validator/repair budget, a modeled 20 ms cost per attempt and a 10 ms response reserve:

| Attempt | Candidate | Validation result | Remaining budget | Decision |
|---:|---|---|---:|---|
| 1 | truncated `{` | `SYNTAX_INVALID` | 40 ms | one repair is still affordable |
| 2 | valid payments summary citing `evt-17` | `ACCEPTED` after all layers | 20 ms | return structured value |

If attempt 2 instead cites `foreign-99`, it is not “close enough”; final status is `EXHAUSTED` with the last internal reason recorded as business-invalid. If only 25 ms was available initially, the 20 ms attempt plus 10 ms reserve would not fit and no attempt would start. The outer request deadline wins over every nominal retry count.

```mermaid
stateDiagram-v2
  [*] --> CheckBudget
  CheckBudget --> Exhausted: no attempt or reserve budget
  CheckBudget --> Validate: attempt fits and limit remains
  Validate --> Accepted: every layer passes
  Validate --> CheckBudget: typed failure and repair allowed
  Validate --> Exhausted: limit reached or deadline spent
  Accepted --> [*]
  Exhausted --> [*]
```

A repair prompt should contain the stable schema and a bounded error code such as `SCHEMA_INVALID:evidence_ids_unique`; do not echo arbitrary parser text, secrets or the full previous output. Repaired output returns to byte and syntax validation first. Never resume midway because a repair can change fields that previously passed.

### Paired executable contract

The dependency-free Java/Python pair implements a strict, duplicate-aware flat JSON boundary, exact fields, enum/length/cardinality/uniqueness rules, trusted service/evidence subsets and deadline-aware repair:

```bash
java -ea study-guides/backend/spring-ai-python/StructuredOutputContracts.java
python3 study-guides/backend/spring-ai-python/structured_output_contracts.py
```

Both print the same first line:

```text
PARITY|ACCEPTED|SYNTAX_INVALID|SCHEMA_INVALID|BUSINESS_INVALID|EXHAUSTED|3398
```

Each implementation passes 36 explicit scenario groups and 10,000 seeded scope/evidence trials. The Java parser is deliberately limited to this flat teaching schema rather than presented as a replacement for a maintained JSON library. These contracts do not call a model, prove provider-native schema enforcement, run Spring/Jackson/Jakarta/Pydantic integration, evaluate entailment, render prompts safely or calibrate repair cost under production load.

### Current primary references

Verified on 25 October 2026:

- [Spring AI output converters](https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html) and [structured-output schema validation](https://docs.spring.io/spring-ai/reference/api/structured-output/schema-validation.html) — converter best-effort behavior, Draft 2020-12 schema generation, provider schema mode and validation switches.
- [Pydantic models](https://docs.pydantic.dev/latest/concepts/models/), [strict mode](https://docs.pydantic.dev/latest/concepts/strict_mode/) and [validators](https://docs.pydantic.dev/latest/concepts/validators/) — validated model construction, deliberate coercion controls, extra-field policy and field/model validators.
- [Python `json`](https://docs.python.org/3/library/json.html) — `JSONDecodeError`, strict document decoding, nonstandard numeric behavior and `object_pairs_hook` for duplicate-name policy.
- [JSON Schema Draft 2020-12 validation vocabulary](https://json-schema.org/draft/2020-12/json-schema-validation) and [Understanding JSON Schema](https://json-schema.org/understanding-json-schema/) — structural keywords and the explicit structural-versus-semantic validation boundary.
- [Jakarta Validation 3.1 specification](https://jakarta.ee/specifications/bean-validation/3.1/) — constraint declaration, validation routines, groups and violation reporting; mapping and validation are separate responsibilities.

### Source provenance and continuation

The selected editable bundle member's Section 9 is source lines 678–714; Section 10 begins at line 715. The matching standalone HTML is lines 796–831; Section 10 begins at line 832. Both sources agree on the incident-summary fields, Pydantic validation, Spring AI record conversion, explicit business checks and bounded repair loop. This canonical section preserves that intent while separating syntax, structural schema, request-specific meaning and grounding; adding duplicate/extra-field policy, server-owned provenance, four worked traces, four diagrams and paired executable contracts. Section 10, **MCP implementations in Python and Java**, begins at editable source line 715 / standalone HTML line 832. Sections 10–25 remain queued; the source is not complete.

## 10. MCP implementations in Python and Java: expose a contract, not ambient authority

MCP standardizes how a host discovers and invokes server capabilities; it does not decide whether a function should be remote, authenticate the caller, authorize a resource, make tool annotations truthful, or turn fixture data into live operational truth. Start from the business boundary, then choose a local callback, an MCP tool or an MCP resource.

### Requirements and invariants

1. **Choose exposure by ownership.** Keep a function as a local application callback when one application owns its lifecycle and no interoperable discovery is needed. Use an MCP tool when multiple hosts need a standardized executable capability. Use an MCP resource for addressable, read-oriented content such as `runbook://payments/7`.
2. **Separate the protocol declaration from the business service.** The decorator or annotation declares a tool name, schema and metadata; a small adapter validates delivered arguments, obtains trusted caller context, invokes a framework-independent service, and maps the typed result.
3. **Treat schemas as shape gates, not policy.** Generated input/output schemas help clients and reject malformed calls. They do not prove tenant access, object ownership, freshness, human intent or semantic correctness.
4. **State provenance explicitly.** Training data returns `source=synthetic-training-fixture` and `live=false`. A live adapter must own backend authorization, deadlines, error/freshness semantics and an observation timestamp before it may claim live state.
5. **Authorize the exact capability and object.** Validate token issuer/audience/expiry outside these teaching contracts, require the capability scope, and check the service or resource tenant inside the trusted server boundary. Never accept tenant or approval authority from model arguments.
6. **Transport changes the deployment boundary.** For stdio, the host launches the intended process and protocol bytes alone use stdout; diagnostics go to stderr. Streamable HTTP is a network endpoint and requires an explicit security layer, even on a framework-generated route.
7. **Annotations are client hints.** `readOnlyHint`, `destructiveHint`, `idempotentHint` and `openWorldHint` can improve review UX when the server is trusted. They never grant permission or constrain the implementation by themselves.
8. **Test both the adapter and the delivered shape.** Direct function tests cover business logic; contract tests also exercise exact tool names, required/unknown fields, URI identity, authorization failures, transport admission, nonlive provenance and Java/Python parity.

### Example 1 — decide whether the function should be MCP at all

The payments application owns `lookupRunbook`. If only its `ChatClient` uses the function in the same process, a Spring AI `@Tool` or ordinary callback is enough. Adding a server, client, transport and discovery lifecycle would add failure modes without adding ownership value.

When an IDE host, an operations host and a support host all need the same versioned capability, expose a thin MCP adapter over the existing service. The underlying method remains independently testable.

```mermaid
flowchart TD
    F["Business function"] --> Q{"Several independent hosts need discovery?"}
    Q -->|no| L["Local callback / @Tool"]
    Q -->|yes| K{"Executable action?"}
    K -->|yes| T["MCP tool adapter"]
    K -->|no, addressable content| R["MCP resource adapter"]
```

| Need | Correct boundary | Why |
|---|---|---|
| one Spring application calls `lookupRunbook` | local callback | one owner; no protocol hop |
| several MCP hosts request deployment status | MCP tool | discoverable typed execution |
| hosts read immutable runbook revision 7 | MCP resource | stable URI and read semantics |
| model invents `runbook://payments/latest` | reject | mutable aliases hide revision/freshness identity |

MCP is valuable when interoperability is a requirement, not as a synonym for every model-callable Java or Python function.

### Example 2 — build the Python v2 server around a typed adapter

The official Python SDK v2 uses `MCPServer`. A minimal adapter can retain the source's safe training behavior while keeping authorization and business work outside the decorator:

```python
from mcp.server import MCPServer

mcp = MCPServer("Atlas training tools")

@mcp.tool()
def deployment_status(service: str) -> dict:
    caller = trusted_request_context()       # transport/auth middleware, not model input
    authorize(caller, "status:read", service)
    if service not in {"payments", "catalog"}:
        raise ValueError("unknown training service")
    return {"service": service, "ready": 3, "desired": 3,
            "source": "synthetic-training-fixture", "live": False}

@mcp.resource("runbook://payments/7")
def payments_runbook() -> str:
    caller = trusted_request_context()
    authorize(caller, "runbook:read", "runbook://payments/7")
    return "Training runbook revision 7: verify migration compatibility."
```

```mermaid
sequenceDiagram
    participant H as MCP host
    participant A as Python adapter
    participant P as Policy
    participant S as Business service
    H->>A: deployment_status(service=payments)
    A->>A: validate delivered schema
    A->>P: trusted caller + capability + object
    P-->>A: allow tenant-a
    A->>S: read bounded status
    S-->>A: typed fixture result
    A-->>H: source=fixture, live=false
```

Exact outcomes make the boundary teachable:

- `{service: "payments"}` plus the correct audience, scope and tenant returns a nonlive fixture;
- an extra argument is `BAD_SCHEMA`, even if the business method would ignore it;
- `orders` is `UNKNOWN_SERVICE`, not a guessed backend name;
- a valid shape with the wrong tenant is `TENANT_DENIED`;
- `runbook://payments/7` is allowed while an unregistered or mutable URI is denied.

For stdio, start the program through a compatible MCP host. It is a protocol process, not a terminal chat interface, and application logging must not corrupt protocol stdout. For Streamable HTTP, use the SDK's supported transport and place authentication/authorization at the network boundary.

### Example 3 — make the Spring `@McpTool` adapter explicit

Spring AI can scan `@McpTool` methods and generate schemas. Keep the annotated method small and make the policy dependency visible:

```java
@Component
final class StatusTools {
    private final StatusService service;
    private final CapabilityPolicy policy;

    @McpTool(
        name = "deployment_status",
        description = "Return bounded deployment status for an authorized service.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true, destructiveHint = false,
            idempotentHint = true, openWorldHint = false))
    Status deploymentStatus(
        @McpToolParam(description = "payments or catalog", required = true)
        String serviceName) {
        Caller caller = policy.currentCaller();
        policy.require(caller, "status:read", serviceName);
        return service.fixture(serviceName); // source=synthetic..., live=false
    }
}
```

```mermaid
flowchart LR
    D["@McpTool declaration"] --> S["Generated schemas + hints"]
    C["Trusted caller context"] --> P["Capability/object policy"]
    S --> A["Thin adapter"]
    P --> A
    A --> B["Framework-independent service"]
    B --> O["Typed result + provenance"]
```

`generateOutputSchema=true` describes the nonprimitive return shape; it does not run employee authorization. Likewise, `readOnlyHint=true` helps a trusted client reason about interaction risk but cannot stop code from mutating a database. Backend credentials, service methods and policy checks must make the hint true.

An application `@Tool` passed to `ChatClient` and an `@McpTool` exported from an MCP server may call the same `StatusService`, but they are different adapters. That separation lets the local application avoid a needless network hop while remote hosts use the interoperable contract.

### Example 4 — admit stdio and HTTP by different evidence

Stdio trust starts with process ownership: the host launches a pinned command, supplies a minimal environment, writes protocol requests to stdin, reads protocol responses from stdout and treats stderr as diagnostics. A random process already attached to a terminal is not equivalent.

Streamable HTTP trust starts with the network request: TLS, authenticated caller, token audience for this MCP resource server, expiry, rate/admission limits and object authorization. The Spring MCP starter wires the endpoint but does not authenticate it automatically.

```mermaid
flowchart TD
    R["Incoming tool call"] --> T{"Transport"}
    T -->|stdio| P{"Host spawned + stdout clean?"}
    T -->|HTTP| A{"Authenticated + intended audience?"}
    P -->|no| X["Reject unsafe channel"]
    A -->|no| Y["401 / deny"]
    P -->|yes| Z["Validate schema and policy"]
    A -->|yes| Z
    Z --> O{"Exact object authorized?"}
    O -->|no| D["403 / deny"]
    O -->|yes| E["Execute bounded adapter"]
```

Do not use annotations as a shortcut around this flow. An untrusted server can falsely advertise a mutating tool as read-only; a trusted server can still contain a bug. The host may use hints to choose a confirmation UI, but deterministic policy and server-side authorization own the decision.

### Paired executable contract

The dependency-free pair models local-versus-MCP selection, exact tool shape, synthetic provenance, URI resources, scope/tenant checks, transport admission and nonauthoritative hints:

```bash
java -ea study-guides/backend/spring-ai-python/McpImplementationContracts.java
python3 study-guides/backend/spring-ai-python/mcp_implementation_contracts.py
```

Both print the same first line:

```text
PARITY|LOCAL=CALLBACK|REMOTE=MCP_TOOL|RESOURCE=runbook://payments/7|FIXTURE=NONLIVE|HTTP=AUTH_REQUIRED|HINT=NONAUTH|ALLOWED=2456
```

Each implementation passes 32 explicit scenario groups and 10,000 seeded service/tenant trials. These pure contracts do not import the MCP SDK, start Spring Boot, inspect real generated schemas, authenticate a live HTTP endpoint, launch an MCP host, validate JSON-RPC bytes or query Kubernetes. Those require dependency and integration environments and must not be inferred from source-file execution.

### Current primary references

Verified on 25 October 2026:

- [Official MCP Python SDK](https://github.com/modelcontextprotocol/python-sdk), [v2 documentation](https://modelcontextprotocol.github.io/python-sdk/) and [v2 changes](https://github.com/modelcontextprotocol/python-sdk/blob/main/docs/whats-new.md) — `MCPServer`, tool/resource decorators, Python requirements and supported transports.
- [Spring AI MCP overview](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html), [server annotations](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html) and [`McpTool` API](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/mcp/annotation/McpTool.html) — declarative server capabilities, generated schemas and annotation fields.
- [Spring AI MCP server starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) — stdio, Streamable HTTP and stateless transports, plus the explicit unauthenticated-by-default HTTP boundary.
- [Spring AI tool calling](https://docs.spring.io/spring-ai/reference/api/tools.html) — the application owns execution; local tool callbacks and MCP exports are different integration boundaries.
- Model Context Protocol, [tools](https://modelcontextprotocol.io/specification/2026-07-28/server/tools) and [authorization](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization) — tool declarations/calls, user control and resource-server authorization requirements.
- Model Context Protocol, [tool annotations as risk vocabulary](https://blog.modelcontextprotocol.io/posts/2026-03-16-tool-annotations/) — annotations are hints and are not trustworthy policy when the server itself is untrusted.

### Source provenance and continuation

The selected editable bundle member's Section 10 is source lines 716–783; Section 11 begins at line 785. The matching standalone HTML is lines 825–881; Section 11 begins at line 882. Both sources contain the same Python v2 server, synthetic deployment-status contract, revisioned runbook resource, Spring `@McpTool` example, unauthenticated-by-default HTTP warning and local-callback-versus-MCP distinction. This canonical section retains that substance while adding boundary-selection rules, trusted caller/object checks, delivered-shape and transport admission, four worked examples, four diagrams and paired executable contracts. Section 11, **Streaming, concurrency and cancellation**, begins at editable source line 785 / standalone HTML line 882. Sections 11–25 remain queued; the source is not complete.

## 11. Streaming, concurrency and cancellation: make partial delivery an explicit state machine

Streaming changes latency and failure shape; it does not turn fragments into a validated answer. A Spring AI `Flux<String>` or an asynchronous Python generator may emit arbitrary chunks: half a word, part of a JSON string, a tool-call delta or provider metadata. Treat every delta as provisional until the stream ends and the application has run the same citation, authorization and business checks required for a nonstreaming answer.

### Requirements and invariants

1. **Choose the least powerful transport that fits.** Use SSE for authenticated server-to-client text events when ordinary HTTP request/response semantics are enough. Use WebSocket only when the application needs ongoing client-to-server messages, binary messages or an application protocol that cannot be represented cleanly as separate HTTP requests.
2. **Define an application protocol.** Every event or message needs a type, request ID, monotonically ordered sequence within that request and a terminal outcome. TCP order does not define what `delta`, `final`, `cancelled` or `error` means.
3. **Bound every queue and lifetime.** Limit admitted streams, pending outbound frames, event/message size, total output, idle time and the end-to-end deadline. A reactive type or `async def` does not create provider quota, memory or database capacity.
4. **Make replay truthful.** An SSE `id` is a replay cursor only if events are retained durably for a documented window. Validate `Last-Event-ID`; replay the missing interval, return an explicit reset when it has expired, and never silently present a gap as continuous output.
5. **Separate provisional and final.** The client may render low-risk deltas as provisional, but only a terminal event produced after validation may say `complete`. Structured or high-risk output should be buffered until it passes its whole-object checks.
6. **Cancellation is cooperative and observable.** Stop local work, cancel upstream where the client/provider supports it, release permits exactly once and record `abandoned`. A disconnect does not prove that a tool call or other side effect rolled back.
7. **Authenticate before opening; authorize every operation.** A successful WebSocket handshake is not authorization for every later message. SSE reconnect and WebSocket resume must remain bound to the same principal, tenant and request object. Do not put bearer tokens in URLs.
8. **Design for intermediaries.** Configure proxy buffering, idle timeouts and heartbeats deliberately. A heartbeat proves only that some path was live recently; it is not an answer, an acknowledgement of a business operation or proof that the provider stopped.

### Example 1 — choose SSE for one-way deltas and frame every event

An answer endpoint receives one prompt and sends progress, token and terminal events. The client does not need to send commands on the same connection, so SSE preserves ordinary HTTP admission and reconnect semantics without inventing a duplex protocol.

```mermaid
flowchart TD
    R["Streaming requirement"] --> C{"Client sends messages while open?"}
    C -->|no| B{"Text server events over HTTP?"}
    B -->|yes| S["SSE + typed events"]
    B -->|no| H["Ordinary HTTP or file stream"]
    C -->|yes| W["WebSocket + application protocol"]
    W --> Q["Bounded outbound queue"]
```

The wire format is a sequence of UTF-8 fields terminated by a blank line. Multiline payloads require one `data:` field per logical line; raw user text must not be concatenated into the `event:` or `id:` fields.

```text
event: delta
id: 42
data: {"requestId":"r-17","sequence":42,"text":"partial"}

event: final
id: 43
data: {"requestId":"r-17","sequence":43,"status":"validated"}

```

A WebFlux controller can return `Flux<ServerSentEvent<StreamEvent>>` and declare `text/event-stream`. Keep transport mapping thin: admission, tenant policy, replay lookup and final validation belong to explicit services. Spring AI provides the chunk source:

```java
Flux<String> chunks = chatClient.prompt().user(question).stream().content();

return chunks
    .index()
    .map(item -> ServerSentEvent.<StreamEvent>builder()
        .event("delta")
        .id(Long.toString(item.getT1() + 1))
        .data(StreamEvent.delta(requestId, item.getT1() + 1, item.getT2()))
        .build())
    .doOnCancel(() -> lifecycle.abandon(requestId));
```

That sketch shows event mapping, not a durable replay ledger. If the service cannot replay event `42` after a reconnect, it must not imply that the ID is a durable resume contract. In current FastAPI, `EventSourceResponse` and `ServerSentEvent` provide SSE-aware framing; a generic `StreamingResponse` streams iterator bytes but leaves the event protocol to the application. In either framework, a producer must reach cancellation points and must not block its event-loop thread with synchronous provider calls or CPU-heavy parsing.

### Example 2 — reconnect by cursor, or reset visibly

Suppose request `r-17` retained events 40–57. The browser reconnects with `Last-Event-ID: 43`. After authenticating and checking ownership of `r-17`, the server replays 44–57 and then follows the live tail. A cursor of 12 is outside the retention window, so the server returns a typed reset instead of silently jumping to 58.

```mermaid
sequenceDiagram
    participant U as EventSource client
    participant A as SSE endpoint
    participant L as Event ledger
    participant G as Generation
    U->>A: GET r-17 + Last-Event-ID 43
    A->>A: authenticate + authorize request
    A->>L: read 44 through latest
    L-->>A: events 44..57
    A-->>U: replay 44..57
    G-->>A: event 58
    A->>L: append 58
    A-->>U: live event 58
```

| Cursor | Retained window | Outcome | Why |
|---:|---:|---|---|
| absent | 40–57 | start the documented live/new-request behavior | there is no replay claim |
| 43 | 40–57 | replay 44–57, then follow live | the complete suffix exists |
| 57 | 40–57 | follow live | client already has the latest retained event |
| 12 | 40–57 | `reset_required` | the missing interval is gone |
| `43x` | 40–57 | reject the cursor | cursors are untrusted input |

An ID orders events only inside the application contract that issued it. Do not compare IDs from different requests or tenants. Retain terminal events long enough for the documented reconnect window, and make the final event self-describing so a reconnecting client can distinguish `validated`, `invalid`, `incomplete` and `abandoned`.

### Example 3 — use WebSocket for duplex control, with a bounded queue

A collaborative client needs to start a request, acknowledge rendered output and cancel generation without opening separate HTTP requests. WebSocket fits, but the protocol still needs exact schemas and state transitions.

```json
{"type":"start","requestId":"r-22","prompt":"...","deadlineMs":20000}
{"type":"ack","requestId":"r-22","sequence":18}
{"type":"cancel","requestId":"r-22","reason":"user"}
```

Server messages use a disjoint set such as `accepted`, `delta`, `final`, `error` and `cancelled`. Validate maximum message bytes, exact fields, request ownership and the legal state for every message. A second `start` with an active `requestId` is a protocol error, not a second hidden generation.

```mermaid
sequenceDiagram
    participant C as Client
    participant W as WebSocket handler
    participant Q as Queue max 32
    participant P as Provider
    C->>W: start r-22
    W->>W: authorize + admit
    W->>P: begin with deadline
    P-->>Q: delta sequence 1
    Q-->>C: delta sequence 1
    C->>W: cancel r-22
    W->>P: cooperative cancel
    W-->>C: cancelled or terminal error
```

Backpressure is not “keep appending to a list until the browser catches up.” Give each connection a bounded outbound queue. If it is full, the contract pair in this repository rejects the new frame and closes with registered code `1013` (`Try Again Later`). Other policies are possible—drop only explicitly lossy telemetry or coalesce replaceable progress—but never drop answer deltas and still claim a complete answer.

Reactive Streams demand can bound compatible upstream publishers. It cannot force a remote model provider, socket callback or blocking library to honor demand. Bridge such producers through a documented bounded buffer and overflow policy. Similarly, `asyncio.Queue(maxsize=n)` is useful only if producers await `put()` or handle overload; `put_nowait()` plus an unbounded retry list just moves the leak.

### Example 4 — cancel locally, reconcile effects, and release only a validated final

The client closes after seeing five deltas. The server should cancel its subscription/task, attempt provider cancellation where supported, release the stream permit exactly once and record an `abandoned` terminal outcome. It must not infer that an already-started tool side effect was undone.

```mermaid
stateDiagram-v2
    [*] --> Admitted
    Admitted --> Streaming
    Streaming --> Provisional: delta
    Provisional --> Provisional: more deltas
    Provisional --> ValidatedFinal: end + all gates pass
    Provisional --> InvalidFinal: end + gate fails
    Provisional --> Incomplete: deadline or budget
    Provisional --> Abandoned: disconnect or cancel
    ValidatedFinal --> [*]
    InvalidFinal --> [*]
    Incomplete --> [*]
    Abandoned --> Reconcile: inspect side-effect keys
    Reconcile --> [*]
```

For Python, cancellation is delivered at an `await`. Cleanup may catch `asyncio.CancelledError`, but it should normally re-raise it after releasing local resources; swallowing it can break `TaskGroup` and timeout semantics. For Reactor, attach cancellation/termination cleanup deliberately and keep blocking JDBC, synchronous SDK calls and CPU-heavy work off event-loop threads. Virtual threads can make suitable blocking Java code cheaper to schedule, but they do not raise provider quotas or database pool size.

The visible completion rules are precise:

| Observed state | Client-visible terminal status | Side-effect conclusion |
|---|---|---|
| stream still open after deltas | `provisional` | none |
| upstream ended; citations and business checks pass | `validated` | report only recorded effects |
| upstream ended; a final gate fails | `invalid` | do not relabel deltas as a valid answer |
| deadline/token/output budget exceeded | `incomplete` | reconcile any issued idempotency keys |
| disconnect or explicit cancellation | `abandoned` | unknown until reconciled |

A deadline covers queue wait, retrieval, tools, provider generation and final validation. Heartbeats may keep an idle connection observable, but they do not extend that business deadline. Track at least admitted, active, queue wait, first-event latency, terminal outcome, abandoned work, overflow and upstream-cancel acknowledgement with bounded metric labels.

### Paired executable contract

The dependency-free pair models transport selection, safe SSE framing, replay windows, bounded WebSocket queues, provisional/final states and the fact that disconnect does not prove rollback:

```bash
java -ea study-guides/backend/spring-ai-python/StreamingTransportContracts.java
python3 study-guides/backend/spring-ai-python/streaming_transport_contracts.py
```

Both print the same first line:

```text
PARITY|ONE_WAY=SSE|DUPLEX=WEBSOCKET|RESUME=REPLAY|OVERFLOW=CLOSE_1013|DISCONNECT=ABANDONED|FINAL=VALIDATED|ACCEPTED=3439
```

Each implementation passes 36 explicit scenario groups and 10,000 seeded queue-admission trials. These pure contracts do not start Spring Boot or FastAPI, open sockets, authenticate a proxy, call a live model, prove provider-side cancellation, load-test concurrency, render a browser client or verify a production replay store. Those require framework and integration environments and must not be inferred from source-file execution.

### Current primary references

Verified on 25 October 2026:

- [Spring AI `ChatClient`](https://docs.spring.io/spring-ai/reference/api/chatclient.html) and [Chat Model](https://docs.spring.io/spring-ai/reference/api/chatmodel.html) — synchronous/streaming APIs, `Flux` responses and the distinction between chunks and aggregated responses.
- Spring Framework, [WebFlux controller return values](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/return-types.html), [WebFlux overview](https://docs.spring.io/spring-framework/reference/web/webflux.html) and [WebSocket support](https://docs.spring.io/spring-framework/reference/web/webflux-websocket.html) — `Flux<ServerSentEvent>`, `text/event-stream`, Reactive Streams backpressure and full-duplex WebSocket handling.
- [FastAPI SSE](https://fastapi.tiangolo.com/tutorial/server-sent-events/), [streaming responses](https://fastapi.tiangolo.com/advanced/custom-response/) and [WebSockets](https://fastapi.tiangolo.com/advanced/websockets/) — SSE field framing, generator cancellation points and WebSocket accept/send/receive/disconnect handling.
- WHATWG HTML, [server-sent events](https://html.spec.whatwg.org/dev/server-sent-events.html) — `text/event-stream`, event grammar, IDs, reconnect behavior and `Last-Event-ID`.
- IETF [RFC 6455](https://www.rfc-editor.org/rfc/rfc6455.html) and the [IANA WebSocket registries](https://www.iana.org/assignments/websocket) — opening/closing behavior, control frames and registered close code `1013`.
- Project Reactor, [backpressure](https://projectreactor.io/docs/core/release/reference/subscribe-backpressure.html) and [programmatic sequence cleanup](https://projectreactor.io/docs/core/release/reference/coreFeatures/programmatically-creating-sequence.html) — demand propagation, cancellation and disposal callbacks.
- Python, [`asyncio` tasks and cancellation](https://docs.python.org/3/library/asyncio-task.html) — cancellation at suspension points, `CancelledError`, timeouts and structured-concurrency behavior.

### Source provenance and continuation

The selected editable bundle member's Section 11 is source lines 785–814; Section 12 begins at line 816. The matching standalone HTML is lines 882–901; Section 12 begins at line 902. Both sources contain the same request-budget diagram, Spring AI `Flux<String>` and ASGI-generator boundary, provisional-display warning, cancellation propagation, WebFlux blocking caveat, Python cooperative-async caveat and measured view of the GIL. This canonical section retains that substance while adding SSE-versus-WebSocket selection, exact event framing, authenticated replay cursors, bounded queues and overflow, terminal-state semantics, four worked examples, four diagrams and paired executable contracts. Section 12, **More Python AI: embeddings, reranking and classical ML**, begins at editable source line 816 / standalone HTML line 902. Sections 12–25 remain queued; the source is not complete.

## 12. More Python AI: embeddings, reranking and classical ML

The question is not whether a neural model is more fashionable than a linear model. It is which measurable task needs to be solved, what data and runtime boundaries are allowed, and which candidate earns promotion against the smallest honest baseline. Embedding retrieval, pairwise reranking, classical classification and generative answering are different jobs; combining them without separate evaluation hides where quality, latency or authorization failed.

### Requirements and invariants

1. **Name the task and unit of truth.** Retrieval ranks versioned document IDs for a query; reranking reorders only that authorized candidate set; classification chooses from a fixed label policy; generation creates new text. Give each stage its own metric and failure outcome.
2. **Keep identity complete.** Pin the encoder, tokenizer, reranker and any classifier by immutable revision or digest. Record library/runtime version, preprocessing configuration, embedding dimension, normalization, distance convention, label map and operator/device configuration.
3. **Validate vectors before scoring.** Require the configured dimension, finite values and nonzero norms when cosine normalization needs them. A cosine score is meaningful only for embeddings produced by the compatible model and preprocessing contract.
4. **Retrieve broadly, rerank narrowly.** A bi-encoder can precompute document vectors and cheaply search many items. A cross-encoder jointly scores each query–document pair, so run it only on a bounded, already-authorized shortlist. Reranking must not introduce a document that retrieval and policy did not admit.
5. **Split before anything learns.** Freeze train/validation/test membership before fitting TF-IDF, imputers, feature selectors, thresholds or model parameters. Group near-duplicates and related users/incidents together; use a time split when production predicts the future.
6. **Tune on validation, report once on test.** Repeatedly choosing models or thresholds from test results turns the test set into training information. Store row/group IDs and dataset revision so the split is auditable.
7. **Promote on task value and operational budgets.** Compare quality slices, false-positive/false-negative cost, p95 latency, memory, artifact size and throughput against the baseline. A higher offline score that violates the serving budget is not a successful replacement.
8. **Treat export as a new release.** Python service, DJL and ONNX Runtime are deployment choices, not proof of equivalence. Re-run golden inputs through the exact tokenizer/preprocessor, input names/dtypes/shapes, model and label/postprocessing logic; gate promotion on tolerance and semantic parity.

### Example 1 — retrieve with a bi-encoder, then rerank the authorized shortlist

Requirement: search 500,000 tenant-scoped runbook chunks and return the best three evidence IDs within 180 ms p95. The corpus changes hourly, while the query changes per request. Precompute normalized document embeddings with one pinned bi-encoder, retrieve a bounded top 40, then apply a pinned cross-encoder to those 40 query–chunk pairs. The cross-encoder never sees candidates outside the caller's policy scope.

```mermaid
flowchart LR
    Q["Query + trusted scope"] --> E["Pinned query encoder"]
    E --> R["Scoped vector search: top 40"]
    R --> C["Authorized candidate IDs"]
    C --> X["Cross-encoder pair scores"]
    X --> K["Stable top 3 + evidence IDs"]
```

```python
from sentence_transformers import CrossEncoder, SentenceTransformer

ENCODER_ID = "sentence-transformers/all-MiniLM-L6-v2"
RERANKER_ID = "cross-encoder/ms-marco-MiniLM-L6-v2"
# Production: resolve and record immutable revisions/digests for both artifacts.
encoder = SentenceTransformer(ENCODER_ID)
reranker = CrossEncoder(RERANKER_ID)

query_vector = encoder.encode([query], normalize_embeddings=True)[0]
candidates = scoped_index.search(
    vector=query_vector, tenant_id=trusted_tenant, limit=40
)  # each item has id, text and retrieval_score
pair_scores = reranker.predict([(query, item.text) for item in candidates])
ranked = sorted(
    zip(candidates, pair_scores), key=lambda pair: (-float(pair[1]), pair[0].id)
)[:3]
```

This is an integration sketch, not a license or quality recommendation for those example model IDs. The production loader must pin artifacts, check model cards and licenses, enforce input limits, bound batch and shortlist sizes, and fail readiness when dimensions or revisions do not match the active index.

Worked trace for three already-authorized candidates:

| Candidate | Cosine retrieval score | Retrieval rank | Pair score | Final rank |
|---|---:|---:|---:|---:|
| `runbook/payments/v7#rollback` | 0.81 | 2 | 4.7 | 1 |
| `runbook/cache/v3#ttl` | 0.87 | 1 | -1.2 | 3 |
| `runbook/payments/v6#deploy` | 0.74 | 3 | 2.1 | 2 |

The pair score is model-specific; do not call it a calibrated probability unless calibration was measured for this model and domain. Use retrieval recall at candidate `k` to decide whether the desired evidence reaches the reranker, and nDCG/MRR or task-specific ranking judgments to decide whether reranking helps. End-to-end answer quality cannot diagnose a missing candidate by itself.

### Example 2 — make the classical baseline hard to beat honestly

Requirement: route support tickets into stable `billing`, `identity` and `delivery` queues. No text generation is required. A TF-IDF plus logistic-regression pipeline is the first candidate because it is cheap, inspectable and directly aligned with fixed labels.

```python
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline

# split_ids was frozen first; duplicate incidents share one group.
pipeline = make_pipeline(
    TfidfVectorizer(ngram_range=(1, 2), min_df=2, sublinear_tf=True),
    LogisticRegression(max_iter=1_000, class_weight="balanced", random_state=7),
)
pipeline.fit(train_text, train_label)       # vocabulary and weights learn from train only
validation_score = pipeline.predict_proba(validation_text)
# choose model/thresholds on validation; evaluate the locked release once on test
```

```mermaid
flowchart TD
    D["Versioned rows + group/time keys"] --> S["Freeze train / validation / test IDs"]
    S --> T["Fit TF-IDF + classifier on train"]
    T --> V["Choose model and thresholds on validation"]
    V --> L["Lock artifact + label map"]
    L --> H["One final test evaluation"]
```

Assume the untouched test set has 1,000 tickets. Candidate A reaches 91% accuracy but misses 18 of 40 urgent identity cases. Candidate B reaches 89% accuracy and misses 3 urgent cases. If an urgent miss costs 20 units and an ordinary error costs 1, the aggregate accuracy winner may be the business loser. Report the confusion matrix and slices, then state the cost rule before looking at model results.

| Checkpoint | Evidence | Reject when |
|---:|---|---|
| 1 | dataset revision, label policy, group/time keys | label provenance is unknown |
| 2 | immutable split IDs | preprocessing or duplicate grouping saw holdout rows |
| 3 | train-only fitted pipeline | vocabulary, scaler or selector fit on all rows |
| 4 | validation decisions and thresholds | test score influenced another choice |
| 5 | per-class metrics, cost, latency and artifact manifest | only aggregate accuracy is reported |

Use probability calibration only when downstream decisions need meaningful probabilities, and evaluate that calibration on unseen data. A `predict_proba` method name alone does not make scores calibrated or thresholds portable across changing class prevalence.

### Example 3 — run a pinned PyTorch model without mixing training and serving state

Requirement: compare a pretrained local classifier with the classical baseline. The release manifest binds tokenizer/model revisions, task head and label map to Python, Transformers, PyTorch and device/backend versions. Load once during process startup, call `eval()` for modules such as dropout and batch normalization, and use inference mode for work that will not participate in autograd.

```python
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

MODEL_ID = "org/ticket-classifier"
REVISION = "immutable-commit-sha"
tokenizer = AutoTokenizer.from_pretrained(MODEL_ID, revision=REVISION)
model = AutoModelForSequenceClassification.from_pretrained(
    MODEL_ID, revision=REVISION
).eval().to(device)

def classify(texts: list[str]) -> torch.Tensor:
    encoded = tokenizer(
        texts, padding=True, truncation=True, max_length=256, return_tensors="pt"
    ).to(device)
    with torch.inference_mode():
        return model(**encoded).logits.cpu()
```

`eval()` changes the behavior of modules that distinguish training and evaluation; `inference_mode()` disables additional autograd tracking. They solve different problems, so one is not a substitute for the other. Bound request text, batch size, token length and queue delay. Warm up the exact shapes you expect, measure p50/p95/p99 and peak memory, and treat an out-of-memory failure as an admission/capacity problem rather than blindly retrying the same batch.

```mermaid
sequenceDiagram
    participant R as Release builder
    participant A as Artifact registry
    participant S as Python service
    participant E as Evaluation gate
    R->>A: pin tokenizer, model, config and digests
    S->>A: resolve exact manifest
    S->>S: load, eval, warm up and health-check
    E->>S: golden + holdout batches
    S-->>E: logits, latency and memory
    E-->>R: promote or reject with evidence
```

Reproducibility is bounded: identical seeds do not guarantee identical results across releases, platforms and devices. Preserve the environment and resolved artifact identities, set deterministic controls where required, and define numeric tolerances rather than claiming bitwise identity without evidence.

### Example 4 — move inference to Java only after whole-pipeline parity

Requirement: a Java service must infer locally because a separate Python service violates the deployment topology. ONNX Runtime provides a JVM binding for supported exported graphs; DJL can provide a higher-level Java inference boundary with an engine and translator. Neither automatically reproduces Python tokenization, truncation, padding, label ordering or postprocessing.

```mermaid
flowchart TD
    N{"Must inference live in JVM?"} -->|no| P["Versioned Python service contract"]
    N -->|yes| J{"Supported export/engine?"}
    J -->|no| X["Reject migration"]
    J -->|yes| G["Golden whole-pipeline parity"]
    G -->|fails| X
    G -->|passes| C["Shadow traffic + capacity gate"]
    C -->|passes| O["Canary JVM runtime"]
```

```java
try (var environment = ai.onnxruntime.OrtEnvironment.getEnvironment();
     var options = new ai.onnxruntime.OrtSession.SessionOptions();
     var session = environment.createSession("classifier.onnx", options);
     var inputIds = ai.onnxruntime.OnnxTensor.createTensor(environment, ids, shape);
     var mask = ai.onnxruntime.OnnxTensor.createTensor(environment, masks, shape);
     var result = session.run(java.util.Map.of(
         "input_ids", inputIds, "attention_mask", mask))) {
    // Verify configured output name/shape, then apply the versioned label map.
}
```

Before canarying, assert exact input/output names, integer/float dtypes, dynamic axes, maximum lengths, special tokens, label order and finite outputs. A useful parity suite includes empty/minimal input, Unicode, truncation boundary, maximum batch, every label and adversarial near-ties. Compare logits within a justified absolute/relative tolerance and require identical final labels at the released threshold. Then shadow realistic traffic and measure latency, memory, throughput and native-resource cleanup.

| Boundary | Must match | Failure example |
|---|---|---|
| tokenizer/preprocessor | revision, normalization, vocabulary, special tokens, truncation/padding | different token IDs |
| graph/session | model digest, opset/operators, input names/dtypes/shapes, execution provider | load failure or different logits |
| postprocessor | output name, label order, activation and thresholds | valid tensor mapped to wrong label |
| service | batching, deadline, overload and error semantics | fast model hidden behind an unbounded queue |

If exact preprocessing cannot be reproduced safely in the JVM, keep a narrow, versioned Python inference service rather than shipping a silently different model. Network separation has cost, but semantic mismatch is not portability.

### Paired executable contract

The dependency-free pair makes vector validation, stable top-k, shortlist-only reranking, leakage checks, deployment parity and quality/latency promotion gates executable without downloading a model:

```bash
java -ea study-guides/backend/spring-ai-python/EmbeddingRerankingContracts.java
python3 study-guides/backend/spring-ai-python/embedding_reranking_contracts.py
```

Both print the same first line:

```text
PARITY|FIRST=CLASSICAL_BASELINE|RETRIEVAL=BI_ENCODER|RERANK=CROSS_ENCODER|SPLIT=BEFORE_FIT|JVM=PARITY_GATED|PROMOTED=3094
```

Each implementation passes 29 explicit scenario groups and 10,000 seeded quality/latency trials. These contracts use tiny in-memory vectors; they do not download Sentence Transformers, fit scikit-learn, load PyTorch/Transformers or ONNX Runtime, inspect an exported graph, compare native numerical kernels, call a vector database, benchmark an accelerator or prove model quality. Those claims require the pinned dependency, artifact, data and serving environments.

### Current primary references

Verified on 25 October 2026:

- Sentence Transformers, [semantic search](https://sbert.net/examples/sentence_transformer/applications/semantic-search/README.html), [retrieve and rerank](https://sbert.net/examples/sentence_transformer/applications/retrieve_rerank/README.html) and [CrossEncoder API](https://sbert.net/docs/package_reference/cross_encoder/model.html) — bi-encoder retrieval, pairwise cross-encoder scoring and why reranking is bounded to a candidate set.
- scikit-learn, [common pitfalls](https://scikit-learn.org/stable/common_pitfalls.html), [pipelines](https://scikit-learn.org/stable/modules/compose.html) and [cross-validation](https://scikit-learn.org/stable/modules/cross_validation.html) — split-before-fit, leakage prevention and keeping a final test set outside model selection.
- PyTorch, [`Module.eval()`](https://docs.pytorch.org/docs/stable/generated/torch.nn.Module.html), [`inference_mode`](https://docs.pytorch.org/docs/stable/generated/torch.autograd.grad_mode.inference_mode.html) and [reproducibility](https://docs.pytorch.org/docs/stable/notes/randomness.html) — evaluation behavior, disabled autograd tracking and the limits of cross-platform/release reproducibility.
- ONNX Runtime, [Java binding](https://onnxruntime.ai/docs/get-started/with-java) and [Java API](https://onnxruntime.ai/docs/api/java/) — session/input/output ownership, supported JVM packages and native-value cleanup.
- Deep Java Library, [main documentation](https://docs.djl.ai/) and [inference performance guidance](https://docs.djl.ai/master/docs/development/inference_performance_optimization.html) — Java engine/translator abstraction and predictor lifecycle/performance considerations.

### Source provenance and continuation

The selected editable bundle member's Section 12 is source lines 816–867; Section 13 begins at line 869. The matching standalone HTML begins Section 12 at line 902 and Section 13 at line 942. Both sources contain the same local Sentence Transformer/CrossEncoder example, warning to pin revisions and inspect licenses, TF-IDF/logistic-regression baseline, leakage warning, PyTorch/Transformers inference guidance and Java service/DJL/ONNX alternatives. This canonical section retains that substance while adding task and identity invariants, authorized shortlist-only reranking, explicit evaluation and cost traces, Python serving gates, whole-pipeline JVM parity, four worked examples, four diagrams and paired executable contracts. Section 13, **Provider adapters and portability**, begins at editable source line 869 / standalone HTML line 942. Sections 13–25 remain queued; the source is not complete.

## 13. Provider adapters and portability: preserve the business contract, qualify every capability

An adapter reduces mechanical change; it does not make providers semantically identical. Define the application's stable contract first, keep vendor extensions behind an explicitly owned boundary, and qualify each provider/model/revision against the exact feature set the workload needs.

| Choice | Application calls | Best fit | Portability limit |
|---|---|---|---|
| direct provider SDK | vendor request and response types | newest provider-specific capability | migration changes authentication, payloads, errors and semantics |
| Spring AI or orchestration abstraction | common `ChatModel`, `EmbeddingModel` or runnable interface | a deliberately small portable core | common types cannot manufacture unsupported tools, schemas or streaming behavior |
| explicit HTTP adapter | an application-owned port implemented with inspected wire contracts | tight control, small dependency surface and contract tests | the team owns authentication, serialization, retries and compatibility |
| self-hosted runtime | a private inference endpoint | placement, artifact and capacity control | the team owns serving, upgrades, overload and model/runtime compatibility |

### Requirements and invariants

1. **The business port is provider-neutral.** It carries business input, a request/operation ID, deadline and required capabilities—not a vendor SDK object or arbitrary option map.
2. **Capability admission is conjunctive.** Provider, endpoint, model revision, region and authentication path must support every required feature. A text-only fallback is not qualified for a tool or strict-schema request.
3. **Extensions name their owner.** A provider-only option belongs in a typed provider adapter. Reject it at the portable boundary instead of silently dropping it.
4. **Identity is environmental.** Prefer the supported workload identity for AWS, Azure and Google. Keep secrets server-side where a provider requires a key; never bake developer credentials into images.
5. **Retries follow observed state.** A locally rejected or definitely unsent request is retryable. After an ambiguous accepted request, retry only when the operation is replay-safe and the provider honors an idempotency key; otherwise reconcile.
6. **Migration is an evaluated release.** Pin endpoint/model/revision, run contract and task-quality suites, compare latency, errors and cost per successful outcome, canary, and retain a tested rollback.

```mermaid
flowchart TD
    B["Business request"] --> P["Application model port"]
    P --> C{"Capability set admitted?"}
    C -->|yes| A["Qualified provider adapter"]
    C -->|no| F["Typed unsupported-capability failure"]
    A --> R["Normalized result plus provider evidence"]
```

### Example 1 — keep a portable core and an owned extension lane

Suppose the answer path requires text, streaming and a deadline. Those fields belong in the stable application port. A provider's reasoning-control field or cache hint belongs in a provider-specific request type because a second provider may reject, ignore or reinterpret it.

```java
public record AnswerRequest(
        String operationId, String prompt, long deadlineEpochMillis,
        boolean streaming, java.util.Set<String> requiredFeatures) {}

public sealed interface AnswerOptions permits PortableOptions, ManagedProviderOptions {}
public record PortableOptions(double temperature) implements AnswerOptions {}
public record ManagedProviderOptions(String serviceTier) implements AnswerOptions {}
```

```python
from dataclasses import dataclass

@dataclass(frozen=True)
class AnswerRequest:
    operation_id: str
    prompt: str
    deadline_epoch_ms: int
    required_features: frozenset[str]

# Provider-only fields live on that adapter's typed configuration, not **kwargs.
@dataclass(frozen=True)
class ManagedProviderOptions:
    service_tier: str
```

Trace: `business request → portable validation → capability check → provider mapping → normalized result`. The normalized result records provider, endpoint class, model/revision, finish reason, usage units and request correlation without exposing secrets. Unknown finish reasons remain unknown; do not coerce them to success.

```mermaid
sequenceDiagram
    participant S as Service
    participant P as Portable port
    participant A as Provider adapter
    participant V as Vendor API
    S->>P: AnswerRequest(op-42, TEXT+STREAM)
    P->>A: validated portable request
    A->>V: vendor request + workload identity
    V-->>A: vendor response + request ID
    A-->>P: normalized result + release evidence
    P-->>S: business result
```

The seam makes replacement possible, but the capability test makes replacement honest. Spring AI's `ChatModel` and `EmbeddingModel` provide portable Java shapes; provider-specific options still need isolated handling. The same rule applies to Python orchestration libraries: a shared method name is not a semantic guarantee.

### Example 2 — build and verify a capability matrix before routing

Treat capability data as reviewed release configuration, not optimistic runtime fallback:

| Route | Identity | text | stream | tools | strict schema | embeddings | Qualified for |
|---|---|---:|---:|---:|---:|---:|---|
| `managed/m-17/us-east` | workload | yes | yes | yes | yes | no | tool-backed structured answer |
| `local/sha256:abc` | workload | yes | yes | no | no | yes | private text and embedding workloads |
| `alternate/m-2/eu` | static key | yes | no | no | no | no | rejected by current identity policy |

```text
request required={TEXT,TOOLS,STRICT_JSON}
primary managed/m-17: identity=yes, features=superset -> ADMIT
alternate local/sha256:abc: missing={TOOLS,STRICT_JSON} -> REJECT
result: route primary; no silent degradation
```

```mermaid
flowchart LR
    Q["Required features"] --> I{"Identity qualified?"}
    I -->|no| X["Reject route"]
    I -->|yes| S{"All features supported?"}
    S -->|no| X
    S -->|yes| E{"Extension owner matches?"}
    E -->|no| X
    E -->|yes| A["Admit pinned route"]
```

Maintain separate matrices for chat, embeddings and reranking; identical provider names do not imply identical endpoint support. Revalidate on a model revision, endpoint, region, SDK/adapter or identity change. “OpenAI-compatible” should be read as a claim about some wire shapes, never as proof of identical token accounting, tool-call JSON, structured-output guarantees, errors, context limits or streaming chunks.

### Example 3 — make retry and failover depend on evidence

Provider errors need a normalized category plus the original correlation details. Preserve at least `UNSENT`, `REJECTED`, `ACCEPTED_UNKNOWN` and `COMPLETED`:

| Observation | Safe action | Reason |
|---|---|---|
| local admission rejected / connect definitely failed before send | retry another qualified route within the same deadline | no remote effect occurred |
| provider explicitly rejected before acceptance | retry if policy permits | response proves non-acceptance |
| timeout after acceptance is possible | retry only with replay safety **and** provider deduplication; otherwise reconcile | duplicate work or charges may already exist |
| completed response validated | return once and record evidence | retry would duplicate a finished operation |

```mermaid
stateDiagram-v2
    [*] --> Unsent
    Unsent --> Retry: budget and qualified route
    Retry --> Completed: validated response
    Retry --> Rejected: explicit non-acceptance
    Rejected --> Retry: budget remains
    Retry --> Unknown: acceptance may have occurred
    Unknown --> Reconcile: no proven dedupe
    Unknown --> Retry: replay-safe plus dedupe
    Completed --> [*]
```

Carry one end-to-end deadline across queueing, authentication refresh, connection, provider processing and response validation. Failover does not reset the clock. Do not retry a tool side effect merely because the model-provider call is retryable; the tool has its own idempotency and reconciliation contract.

### Example 4 — migrate with shadow evidence, canary gates and rollback

For a proposed `managed/m-17 → alternate/a-9` migration:

1. freeze prompt, tools, schema, retrieval generation and evaluation set;
2. run adapter contract tests for serialization, streaming assembly, error mapping and required capabilities;
3. shadow only policy-approved inputs and compare task outcomes—not raw text equality;
4. calculate quality, p95, error rate and **cost per successful outcome** using the same traffic slice;
5. canary an explicit cohort, watch hard safety and quality slices, then promote or roll back the route pointer.

```text
release gate
contract_pass=true
quality=0.86 >= 0.82
p95=700 ms <= 800 ms
cost_per_success=$0.018 <= $0.020
decision=PROMOTE
```

```mermaid
flowchart TD
    B["Pinned baseline"] --> T["Adapter contract tests"]
    T --> S["Policy-safe shadow evaluation"]
    S --> C{"Quality, latency, errors and cost pass?"}
    C -->|no| K["Keep baseline"]
    C -->|yes| Y["Canary cohort"]
    Y --> P{"Hard slices pass?"}
    P -->|no| R["Rollback route pointer"]
    P -->|yes| G["Promote pinned candidate"]
```

A migration is reversible only when the previous adapter configuration, model/revision and compatible state remain available. If provider-managed conversation IDs, caches or stored files are part of the workflow, either externalize that state behind an application contract or describe the migration limitation explicitly.

### Paired executable contract

The dependency-free pair makes capability admission, extension ownership, workload-identity policy, ambiguous-outcome handling and migration gates executable without a provider SDK or network call:

```bash
java -ea study-guides/backend/spring-ai-python/ProviderPortabilityContracts.java
python3 study-guides/backend/spring-ai-python/provider_portability_contracts.py
```

Both print the same first line:

```text
PARITY|BOUNDARY=PORTABLE_CORE|EXTENSIONS=OWNED|IDENTITY=WORKLOAD|UNKNOWN=RECONCILE|MIGRATION=EVALUATED|PROMOTED=1630
```

Each implementation passes 23 explicit scenario groups and 10,000 seeded migration-gate trials. These contracts do not call Spring AI, a Python orchestration package, OpenAI, Bedrock, Azure or Vertex AI; do not refresh cloud credentials; do not prove regional/model feature availability; and do not measure real quality, latency, error rate, token accounting or cost. Those claims require the pinned adapter/provider environment and an approved evaluation workload.

### Current primary references

Verified on 26 October 2026:

- Spring AI, [Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html), [Embeddings Model API](https://docs.spring.io/spring-ai/reference/api/embeddings.html) and [chat-model comparison](https://docs.spring.io/spring-ai/reference/api/chat/comparison.html) — portable interfaces, request/response types and provider capability differences.
- Amazon Bedrock, [Converse API](https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html) and [API compatibility](https://docs.aws.amazon.com/bedrock/latest/userguide/models-api-compatibility.html) — a common messages interface, model-specific request fields and endpoint/model capability differences.
- Microsoft, [Azure OpenAI with Microsoft Entra ID](https://learn.microsoft.com/en-us/azure/foundry-classic/openai/how-to/managed-identity) — developer identity, managed identity, role assignment and secret-free deployed authentication.
- Google Cloud, [authentication for APIs and services](https://cloud.google.com/docs/authentication) and [Google Gen AI SDK](https://cloud.google.com/vertex-ai/generative-ai/docs/sdks/overview) — Application Default Credentials and selecting Vertex AI versus the Gemini developer API.
- OpenAI, [API authentication](https://platform.openai.com/docs/api-reference/authentication) — server-side bearer keys, secure loading and project/organization request attribution.

### Source provenance and continuation

The selected editable bundle member's complete Section 13 is source lines 869–880; Section 14 begins at line 882. The matching standalone HTML begins Section 13 at line 942 and Section 14 at line 980. Both sources contain the same four-choice adapter table, warning about changing model/region/API support, limits of “OpenAI-compatible” behavior, cloud identity guidance and selection criteria of task quality, data handling, latency, quotas and total cost. This canonical section retains that substance while adding the portable-core/provider-extension boundary, conjunctive capability admission, typed retry evidence, an evaluated migration/rollback path, four worked examples, four diagrams and paired executable contracts. Section 14, **Observability and debugging by boundary**, begins at editable source line 882 / standalone HTML line 980. Sections 14–25 remain queued; the source is not complete.

## 14. Observability and debugging by boundary: preserve causal evidence without leaking content

Observability should answer **which boundary changed the outcome**. A single request trace connects admission, retrieval, tool and model work, but each span owns its own timing, outcome and release evidence. Metrics describe aggregate behavior; traces preserve causal structure; logs retain selected events. None of these signals is an authorization source or proof that an answer is correct.

```mermaid
flowchart TD
    A["Request: release + trace ID"] --> Q["Admission / queue"]
    Q --> R["Authorized retrieval"]
    Q --> T["Tool boundary"]
    Q --> M["Model boundary"]
    R --> O["Output validation"]
    T --> O
    M --> O
    O --> E["Outcome + evaluation evidence"]
```

### Requirements and invariants

1. **Release identity is complete.** Record the application release, model/revision, prompt version, index generation and adapter configuration needed to reproduce the path.
2. **Stage outcomes are explicit.** Queue, pool, retrieval, authorization, context admission, tool, model and output validation each report duration and typed outcome.
3. **Trace continuity is causal, not authoritative.** Propagate trace context across Java, Python, queues and services, but reauthenticate and reauthorize at every protected boundary.
4. **Metric dimensions are bounded.** Model class, release cohort, boundary and outcome are suitable labels; user IDs, prompt text, document IDs and arbitrary error text are not.
5. **Content is off by default.** Prompt, completion, tool arguments/results and retrieved text require explicit enablement, a protected sink, retention limits and access controls.
6. **Counts retain stage evidence.** Candidate, authorized, context and cited counts let investigators locate a loss without recording raw evidence.
7. **Security failures override availability.** Cross-tenant evidence stops and isolates the path. Similarity thresholds, retries or fallback cannot repair missing authorization.
8. **Health is boundary-specific.** A listening port does not prove tool registration, compatible sync/async mode, schema publication or SDK support.
9. **Language parity starts with inputs.** Compare pinned model/revision, prompt hash, temperature, token limit, embedding revision and ordered candidate IDs before blaming Java or Python.

| Signal | Safe bounded fields | Protected high-cardinality detail | Do not export by default |
|---|---|---|---|
| Metrics | boundary, outcome, model class, release cohort | none | user/document/request IDs, prompt text |
| Traces | release versions, counts, durations, completion category | request/provider correlation IDs | raw prompts, completions, retrieved text, tool payloads |
| Logs | typed event, trace ID, redacted error category | audited object/operation IDs where required | secrets, access tokens, unrestricted provider bodies |

Spring AI builds its observations on Micrometer and keeps prompt/completion and tool content export disabled by default because those values can be large and sensitive. Micrometer distinguishes low-cardinality keys used for metrics from high-cardinality trace/log detail. Prometheus creates a new time series for every label-value combination, so unbounded IDs are both a cost and reliability risk. OpenTelemetry context joins the path across services, while its semantic conventions provide shared names; pin convention and instrumentation versions because GenAI conventions continue to evolve.

### Example 1 — high p99 latency while CPU remains low

Atlas p99 rises from 1.2 seconds to 4.8 seconds, yet application CPU stays at 22%. “Add threads” is not a diagnosis. Split the request into queue, connection-pool, retrieval, tool, model and output spans and compare their distributions for the same release cohort.

| Boundary | p50 | p99 | Saturation evidence | Interpretation |
|---|---:|---:|---|---|
| Admission queue | 18 ms | 2,900 ms | permits 100%, queue depth rising | primary bottleneck |
| Connection pool | 6 ms | 40 ms | 42% borrowed | not dominant |
| Retrieval | 65 ms | 180 ms | stable candidates | not dominant |
| Provider/model | 520 ms | 940 ms | no throttle response | slower, but not the 4.8 s cause |
| Output validation | 4 ms | 11 ms | stable | not dominant |

```mermaid
flowchart LR
    P["High end-to-end p99"] --> C{"CPU saturated?"}
    C -->|no| W["Compare queue / pool / provider waits"]
    W --> D{"Largest same-request span"}
    D -->|queue| A["Reduce admission or add qualified capacity"]
    D -->|pool| B["Inspect leases, deadlines and pool size"]
    D -->|provider| F["Inspect throttling, quotas and upstream latency"]
```

The exact trace `queue=2,940 ms → pool=12 → retrieval=103 → model=811 → output=7` makes queueing the first repair target. A Resilience4j bulkhead can bound concurrent work at one JVM boundary; it does not enforce a fleet-wide token quota or create upstream model capacity. Capacity changes should follow the measured saturated resource and preserve one end-to-end deadline.

### Example 2 — a relevant document is retrieved but never cited

Do not jump directly to prompt tuning. Trace the evidence through extraction, candidate generation, authorization, context admission and citation validation:

```text
source revision runbook-r17: extracted=true
retrieval candidates=[d17,d42,d51,d88]
authorized=[d17,d42,d51]
reranked=[d42,d17,d51]
context admitted=[d42,d51]  # d17 lost to token budget
cited=[d42]
diagnosis=CONTEXT_TRUNCATION for d17
```

```mermaid
flowchart TD
    S{"Extracted?"} -->|no| X["Extraction miss"]
    S -->|yes| R{"Candidate found?"}
    R -->|no| Y["Retrieval miss"]
    R -->|yes| A{"Authorized?"}
    A -->|no| Z["Expected policy filter"]
    A -->|yes| C{"Admitted to context?"}
    C -->|no| T["Truncation / budget loss"]
    C -->|yes| G{"Cited?"}
    G -->|no| M["Generation / citation miss"]
    G -->|yes| K["Complete evidence path"]
```

Candidate IDs can be protected trace attributes or server-side incident records; they must not become unbounded metric labels. Store counts in ordinary metrics and keep object-level IDs behind trace access controls. The first missing transition selects the subsystem and its regression test.

### Example 3 — a cross-tenant document appears

A payments request returns one payments chunk and one executive-only chunk. This is not a relevance bug. Stop release of the answer, isolate the affected retrieval/cache path, retain protected incident evidence, advance authorization/cache epochs as needed, and run negative tenant tests before reopening.

```mermaid
sequenceDiagram
    participant H as Host
    participant R as Retrieval
    participant V as Release validator
    participant I as Incident control
    H->>R: verified scope tenant=payments
    R-->>V: tenants={payments, executive}
    V-->>H: STOP_AND_ISOLATE
    V->>I: protected trace + release/index IDs
    I->>R: disable route / advance authority
```

The public metric can increment `retrieval_scope_violation_total{release_cohort="r14", outcome="blocked"}`. The trace may retain the protected correlation and offending object identifiers under restricted access. Never emit tenant names, document IDs or text as metric labels. Raising the similarity threshold, reducing top-k or asking the model to ignore the chunk does not restore authorization.

### Example 4 — the server is healthy, but the tool is absent; Java and Python disagree

An MCP server answers its health endpoint, but Atlas cannot discover `lookup_runbook`. Prove each boundary in order: server process and transport, tool registration, sync/async server mode, schema publication, negotiated protocol/SDK support, and host policy admission. Port health is only the first predicate.

After registration is repaired, Java returns a cited answer while Python abstains. Compare the reproducibility vector before inspecting language runtimes:

| Field | Java | Python | First difference |
|---|---|---|---|
| model revision | `m-17` | `m-17` | — |
| prompt hash | `sha256:p` | `sha256:p` | — |
| temperature | `0.0` | `0.0` | — |
| output token limit | `800` | `800` | — |
| embedding revision | `embed-v4` | `embed-v4` | — |
| ordered candidate IDs | `[d1,d7]` | `[d7,d1]` | **candidate order** |

```mermaid
flowchart TD
    H["Healthy port"] --> R{"Tool registered?"}
    R -->|no| X["Fix scanning / registration"]
    R -->|yes| S{"Mode + SDK compatible?"}
    S -->|no| Y["Fix runtime contract"]
    S -->|yes| P{"Host policy admits tool?"}
    P -->|no| Z["Expected policy denial"]
    P -->|yes| C["Compare Java/Python release vectors"]
```

This preserves two separate claims: capability discovery is not endpoint liveness, and output parity is meaningless until inputs and selected evidence match.

### Paired executable contract

The dependency-free pair makes boundary timing, stage-first retrieval diagnosis, tenant-leak response, tool-registration proof, bounded labels, protected content capture and Java/Python parity evidence executable:

```bash
java -ea study-guides/backend/spring-ai-python/ObservabilityBoundaryContracts.java
python3 study-guides/backend/spring-ai-python/observability_boundary_contracts.py
```

Both print the same first line:

```text
PARITY|TRACE=BOUNDARY_FIRST|METRICS=BOUNDED_LABELS|CONTENT=DEFAULT_OFF|TENANT_LEAK=STOP|TOOLS=REGISTRATION_PROVED|MISMATCH=CONFIG_FIRST|MODEL_DOMINANT=1639
```

Each implementation passes 37 explicit scenario groups and 10,000 seeded dominant-boundary trials. These contracts do not start Spring Boot, FastAPI, an MCP server, OpenTelemetry Collector, Prometheus or a provider; they do not validate export pipelines, sampling, retention, dashboards, alert delivery or production access controls. Those require the deployed telemetry path and incident exercises.

### Current primary references

Verified on 26 October 2026:

- Spring AI, [Observability](https://docs.spring.io/spring-ai/reference/observability/index.html) — Micrometer-backed observations for model, vector-store, advisor and tool paths; prompt, completion, query-response and tool content export remain disabled by default because of size and sensitivity.
- OpenTelemetry, [Traces](https://opentelemetry.io/docs/concepts/signals/traces/), [Context propagation](https://opentelemetry.io/docs/concepts/context-propagation/), [Python instrumentation](https://opentelemetry.io/docs/languages/python/instrumentation/) and [Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/) — causal request structure, cross-process propagation, language instrumentation and versioned shared attribute names.
- Micrometer, [Observation introduction](https://docs.micrometer.io/micrometer/reference/observation/introduction.html), [Observation components](https://docs.micrometer.io/micrometer/reference/observation/components.html) and [High-cardinality tag detector](https://docs.micrometer.io/micrometer/reference/concepts/high-cardinality-tags-detector.html) — low-cardinality meter tags versus high-cardinality trace/log detail and cardinality detection.
- Prometheus, [Metric and label naming](https://prometheus.io/docs/practices/naming/) and [Instrumentation](https://prometheus.io/docs/practices/instrumentation/) — each label combination creates a time series; unbounded labels increase memory, CPU, disk and network cost.
- Resilience4j, [Bulkhead](https://resilience4j.readme.io/docs/bulkhead) — semaphore and fixed-thread-pool concurrency limits at a selected application boundary.

### Source provenance and continuation

The selected editable bundle member's complete Section 14 is source lines 882–915, bounded before Section 15 at line 917. The matching standalone HTML is lines 980–1002, bounded before Chapter 15 at line 1003. Both contain the same request/retrieval/tool/model trace, release and usage fields, content-sensitivity warning, metric-cardinality rule, OpenTelemetry/Resilience4j boundary, and five debugging prompts. The canonical section preserves all five prompts in four connected worked examples, adds explicit invariants, trace schemas, four diagrams and paired executable contracts, and does not copy the older generated layout as a second guide. Section 15, **Testing pyramid for this repository**, begins at editable line 917 / standalone HTML line 1003. Sections 15–25 remain queued; the source is not complete.

## 15. Testing pyramid for this repository: make every claim earn its environment

The source's six layers are not six interchangeable ways to say “tests passed.” Each layer is the least expensive environment that can produce evidence for a different claim. A unit test can prove deterministic RRF arithmetic; it cannot prove a PostgreSQL tenant predicate, a provider's current wire behavior, an SLO under load or a Kubernetes rollout.

| Layer | Representative target | Evidence it can produce | Claim it must not make |
|---|---|---|---|
| Pure unit | RRF, scope predicate, citation membership, budget arithmetic | deterministic input/output and invariants | database, network or framework wiring works |
| Adapter contract | HTTP request/response mapping, timeout, 429/5xx, stream framing | owned serialization and typed failure behavior | the live provider still behaves that way |
| Database integration | migration, pgvector query, generation and tenant filter | driver/schema/operator/transaction interaction | retrieval is relevant for every question |
| Model evaluation | frozen corpus, questions, expected evidence and slices | measured retrieval/answer quality for one workload revision | all future prompts or judge infallibility |
| Load test | mixed prompt/output lengths, cancellation and concurrency | latency, errors and resource release under the tested load | unlimited capacity or another environment's performance |
| Cluster smoke | image, config, secret references, probes and a small request | this release can start and serve in this cluster | backup restore, regional failover or disaster recovery |

### Requirements and invariants

1. **Name the claim before selecting the test.** Use the lowest layer that can observe the real boundary; do not mock away the property under test.
2. **Fast tests own deterministic breadth.** Run pure logic and adapter cases on every change, including invalid, empty, timeout, cancellation and authorization-negative paths.
3. **Mocks stop at an owned boundary.** A provider stub is useful for the request you send and response you parse. It is not evidence about current provider availability, quotas, model quality or pricing.
4. **Integration means the real protocol and feature.** For pgvector, run the actual PostgreSQL extension, migration, vector dimension/operator and tenant/generation predicate rather than substituting an in-memory map.
5. **Evaluation data is an artifact.** Version corpus, questions, expected evidence, protected slices, metric code, judge prompt/model and thresholds. Keep release data separate from tuning data.
6. **Security failures fail closed at every layer.** Cross-tenant rows, unauthorized tools, leaked secrets or missing negative tests block promotion even when average quality and latency pass.
7. **Load uses a workload identity.** Record arrival pattern, concurrency, prompt/output distributions, cache state, cancellation rate, data generation, release and load-generator capacity.
8. **A threshold is a release predicate.** Publish the exact numerator, denominator, percentile and comparison; “looks good” is not a gate.
9. **Cluster probes have separate meanings.** Startup protects slow initialization, readiness controls traffic eligibility and liveness requests a restart. A single always-200 endpoint cannot prove all three.
10. **Promotion requires same-release evidence.** Do not combine unit results from `r15`, evaluation from `r14` and a cluster smoke from an unlabelled image.

### Example 1 — fast unit and adapter tests catch different regressions

Atlas has a deterministic authorized-retrieval core and an HTTP provider adapter. A change accidentally reverses the tie-breaker for equal RRF scores and maps a provider timeout to a generic 500. Two focused suites locate the failures without starting Spring Boot, Python ASGI, a database or a model.

| Case | Input | Expected evidence | Layer |
|---|---|---|---|
| deterministic tie | `p-high=0.91`, `p-tie=0.91` | IDs ordered stably: `p-high,p-tie` | unit |
| tenant-negative | request tenant `payments`, candidate tenant `executive` | candidate absent before ranking/context | unit |
| timeout | no response before owned deadline | typed `TIMEOUT`, no blind retry after cancellation | adapter |
| throttling | HTTP 429 + bounded retry metadata | typed `RATE_LIMITED` with parsed delay bounded by deadline | adapter |
| malformed success | HTTP 200, missing required output field | `INVALID_RESPONSE`, not success | adapter |

```mermaid
flowchart LR
    C["Changed code"] --> U["Pure unit: logic + invariants"]
    C --> A["Adapter contract: owned wire boundary"]
    U -->|tie failure| R["Repair ranking code"]
    A -->|timeout mapping failure| W["Repair adapter mapping"]
    U -->|pass| N["Does not prove transport"]
    A -->|pass| P["Does not prove live provider"]
```

The Java side can test a controller or application slice with Spring's focused test facilities, while Python can parametrize the same behavior table with `pytest.mark.parametrize`. If the purpose is the HTTP client's real codec, headers and network-error behavior, use a mock web server rather than replacing the client itself with a method mock. The resulting evidence should include the tested schema/release identity and every typed outcome, not merely a coverage percentage.

### Example 2 — a disposable real pgvector database proves scope and query behavior

An in-memory fake accepts arbitrary vector lengths and sorts a Java/Python list after retrieval. Production PostgreSQL does neither. The database test starts a pinned pgvector image, runs the real migration and inserts these rows:

| ID | Tenant | Generation | Similarity to query | Expected for `payments/g43` |
|---|---|---|---:|---|
| `p-high` | payments | g43 | 0.91 | rank 1 |
| `p-low` | payments | g43 | 0.71 | rank 2 |
| `exec` | executive | g43 | 0.99 | **never returned** |
| `old` | payments | g42 | 0.96 | **never returned** |

Run the production query shape, with authority predicates inside the query:

```sql
SELECT chunk_id, 1 - (embedding <=> CAST(:query AS vector)) AS score
FROM chunk_embedding
WHERE tenant_id = :verified_tenant
  AND generation = :published_generation
ORDER BY embedding <=> CAST(:query AS vector), chunk_id
LIMIT :k;
```

The trace must be `migration → extension/dimension check → seed → parameterized query → IDs [p-high,p-low] → rollback/teardown`. Assert the negative rows explicitly; “two rows returned” is insufficient because the wrong two rows could satisfy that count.

```mermaid
sequenceDiagram
    participant T as Integration test
    participant P as Pinned pgvector container
    participant D as Production DAO
    T->>P: start + run migration
    T->>P: seed two tenants + two generations
    T->>D: verified tenant=payments, generation=g43
    D->>P: parameterized vector query
    P-->>D: p-high, p-low
    D-->>T: ordered scoped IDs
    T->>T: assert exec and old are absent
```

Testcontainers for Java documents both `pgvector/pgvector` compatibility and a `jdbc:tc:pgvector:pg16:///...` path; Testcontainers Python supplies a real PostgreSQL container for the Python driver. Pin the database/extension image and migration revision in evidence. This layer proves the tested driver, schema, vector operator and predicates together. It still does not prove production data quality, managed-database policy, backup recovery or relevance on unseen questions.

### Example 3 — quality evaluation is a versioned release gate, not one judge call

For `evalset-v15`, freeze 100 questions with expected evidence IDs and declared slices: 50 normal answerable, 20 paraphrases, 15 no-answer, 10 authorization-negative and 5 long-context. Evaluate the exact candidate release `r15` against the current baseline `r14`.

| Metric/slice | r14 | r15 | Gate | Result |
|---|---:|---:|---:|---|
| retrieval recall@5, all answerable | 0.89 | 0.92 | ≥ 0.90 | pass |
| citation membership | 0.99 | 1.00 | = 1.00 | pass |
| authorization-negative leakage | 0/10 | 0/10 | = 0 | pass |
| no-answer precision | 0.93 | 0.91 | ≥ 0.92 | **block** |
| calibrated support score | 0.87 | 0.90 | ≥ 0.88 | pass |

Despite a higher overall score, `r15` is blocked by no-answer precision. Repair the behavior, produce a new release identity and rerun the unchanged holdout. Do not repeatedly tune to the release set until it becomes training data.

```mermaid
flowchart TD
    R["Pinned release + evalset-v15"] --> X["Retrieval metrics by slice"]
    R --> A["Answer / citation checks"]
    R --> J["Calibrated evaluator evidence"]
    X --> G{"Every hard gate passes?"}
    A --> G
    J --> G
    G -->|no| B["Block and diagnose first failed slice"]
    G -->|yes| L["Advance to bounded load test"]
```

Spring AI's `Evaluator` and `RelevancyEvaluator` can contribute one model-based signal using the user input, contextual data and response. They do not replace deterministic membership/authorization checks or a calibrated human-labelled holdout. Record evaluator model, prompt and repeated-run policy because a nondeterministic or upgraded judge can move the gate without application changes.

### Example 4 — load and cluster smoke complete a same-release evidence chain

After functional and quality gates pass, run a representative test for `r15`: 60% short questions, 25% long-context questions, 10% no-answer and 5% cancellations; fixed arrival rate; cold and warm cache phases; 15 minutes after warm-up. The declared release gates are error rate `<1%`, p95 end-to-end `<900 ms`, tenant leakage `=0`, dropped iterations `=0` for the target rate and all cancellation permits released.

| Phase | Evidence | Result |
|---|---|---|
| load admission | target 80 requests/s, achieved 80, dropped 0 | pass |
| latency/errors | p95 720 ms, error rate 0.4% | pass |
| cancellation | 400 cancellations, 400 terminal releases | pass |
| cluster startup | startup probe succeeds after model/index load | pass |
| readiness | pod leaves endpoints during dependency failure | pass |
| liveness | deadlocked fixture is restarted | pass |
| authorized smoke | one cited answer and one forbidden request | pass |

```mermaid
stateDiagram-v2
    [*] --> FastTests
    FastTests --> DatabaseIntegration: pass
    DatabaseIntegration --> Evaluation: pass
    Evaluation --> Load: hard slices pass
    Load --> ClusterSmoke: SLO thresholds pass
    ClusterSmoke --> Promote: same release + probes + auth pass
    FastTests --> Block: fail
    DatabaseIntegration --> Block: fail
    Evaluation --> Block: fail
    Load --> Block: fail
    ClusterSmoke --> Block: fail
```

k6 thresholds are executable pass/fail criteria, so CI should consume its non-zero exit when an SLO threshold fails. Also record the load generator's saturation; a generator at its own limit can distort latency or under-deliver arrivals. In Kubernetes, readiness removes an unready pod from Service traffic, while liveness can restart a broken container and startup prevents premature checks during initialization. Exercise each transition; a manifest review or one `/health` response is not runtime proof.

### Paired executable release-gate contract

[`TestingPyramidContracts.java`](TestingPyramidContracts.java) and [`testing_pyramid_contracts.py`](testing_pyramid_contracts.py) make layer-to-claim selection, adapter outcomes, tenant-scoped row ordering, evidence identity and fail-closed promotion executable without external dependencies:

```bash
java -ea study-guides/backend/spring-ai-python/TestingPyramidContracts.java
python3 study-guides/backend/spring-ai-python/testing_pyramid_contracts.py
```

Both print the same first line:

```text
PARITY|FAST=UNIT_ADAPTER|DB=REAL_PGVECTOR|QUALITY=FIXED_DATASET|LOAD=SLO_THRESHOLDS|CLUSTER=PROBES|RELEASE=EVIDENCE_GATED|PROMOTED=646
```

Each implementation checks 29 explicit scenario groups and 10,000 seeded release-gate trials. The oracle covers release-identity mismatch, security and functional failures, quality thresholds, load thresholds and promotion precedence. These dependency-free contracts do not start Spring Boot, pytest, PostgreSQL, a model, k6 or Kubernetes; the corresponding layers require their named real environments.

### Current primary references

Verified on 26 October 2026:

- Spring Boot, [Testing](https://docs.spring.io/spring-boot/reference/testing/index.html), [Testing Spring Boot applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html) and [Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) — test support, focused application slices, `@SpringBootTest` web modes and container-managed integration tests.
- Spring Framework, [WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html) and [Testing client applications](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html) — mock-server/controller tests versus real HTTP-client and network-condition evidence.
- Testcontainers for Java, [PostgreSQL module](https://java.testcontainers.org/modules/databases/postgres/) and [JDBC support](https://java.testcontainers.org/modules/databases/jdbc/) — disposable PostgreSQL plus explicit pgvector image/JDBC support; Testcontainers Python, [PostgresContainer](https://testcontainers-python.readthedocs.io/en/testcontainers-v4.2.0/modules/postgres/README.html) — a real PostgreSQL fixture for Python integration tests.
- pytest, [fixtures](https://docs.pytest.org/en/stable/explanation/fixtures.html) and [parametrization](https://docs.pytest.org/en/stable/how-to/parametrize.html) — reliable test context and table-driven cases across inputs/configurations.
- Spring AI, [Evaluation testing](https://docs.spring.io/spring-ai/reference/api/testing.html) — `Evaluator`, `EvaluationRequest` and context-aware relevancy evaluation, used here as one calibrated quality signal rather than authorization truth.
- Grafana k6, [Thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/) and [large-test execution](https://grafana.com/docs/k6/latest/testing-guides/running-large-tests/) — executable SLO pass/fail criteria and load-generator capacity caveats.
- Kubernetes, [Configure liveness, readiness and startup probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/) — distinct restart, traffic-eligibility and initialization behavior.

### Source provenance and continuation

The selected editable bundle member's complete Section 15 is source lines 917–930, bounded before Section 16 at line 932. The compared standalone HTML is lines 1003–1052, bounded before Chapter 16 at line 1053. Both contain the same six-layer table and the same explicit list of production gaps, so the editable Markdown remains the structural source and no duplicate guide was created. This canonical section retains every source layer while adding claim boundaries, hard security/release-identity gates, four worked traces, four diagrams, real pgvector and workload examples, current primary references and paired executable contracts. Section 16, **A four-week implementation path**, begins at editable line 932 / standalone HTML line 1053. Sections 16–25 remain queued; the source is not complete.

## 16. A four-week implementation path: build an evidence chain, not a demo calendar

The source proposes a sensible sequence: prove the offline core, add one model-backed path, expose a read-only MCP fixture, then deploy the same candidate into a disposable environment. The schedule is useful only if every week produces durable evidence for the next one. “The demo worked on Friday” is not an exit criterion.

| Week | Entry condition | Deliverable | Required failure drill | Exit evidence |
|---|---|---|---|---|
| 1 — offline core | frozen documents, questions and authority cases | deterministic Java/Python lexical baseline | paraphrase miss and cross-tenant candidate | versioned fixture, trace and regression test |
| 2 — model retrieval | Week 1 evidence accepted | one fixed model/embedding path and lexical/vector/hybrid comparison | unsupported answer and model timeout | same-corpus evaluation with a safe winner or explicit no-winner |
| 3 — MCP boundary | read-only application capability exists | discoverable, schema-valid tool/resource in a compatible host | unknown input, unauthorized scope and timeout | protocol trace plus negative authorization evidence |
| 4 — disposable deployment | prior gates attach to one release | candidate image in a disposable non-production environment | bad startup, dependency outage, overload and recovery | same-release rollout, probe, resilience and evaluation evidence |

```mermaid
flowchart TD
    W1["W1: deterministic offline baseline"] --> G1{"fixture + auth regressions pass?"}
    G1 -->|yes| W2["W2: fixed model and retrieval comparison"]
    W2 --> G2{"quality + no-answer gates pass?"}
    G2 -->|yes| W3["W3: read-only MCP boundary"]
    W3 --> G3{"discovery + schema + auth + timeout pass?"}
    G3 -->|yes| W4["W4: disposable same-release deployment"]
    W4 --> P{"probes + resilience + evaluation pass?"}
    P -->|yes| R["promotion evidence chain"]
    G1 -->|no| B["block, diagnose, record"]
    G2 -->|no| B
    G3 -->|no| B
    P -->|no| B
```

### Requirements and invariants

1. **Each week has entry criteria, a deliverable, executable evidence, a failure drill and an exit gate.** A calendar date cannot override a failed gate.
2. **Identity follows the evidence.** Record the release, source/fixture revision, corpus, model/embedding revision, schema and environment. Comparisons hold every identity constant except the variable under study.
3. **Change one variable at a time.** Comparing lexical retrieval on one corpus with vector retrieval on another does not measure retrieval strategy.
4. **Authorization-negative evidence is a hard gate.** A higher relevance score cannot compensate for a cross-tenant result, an over-broad MCP tool or a leaked secret.
5. **A tutorial run is not completion.** Exit evidence includes repeatable commands, assertions and typed failures—not screenshots or an unreviewed transcript.
6. **Every material choice gets a decision record.** Capture context, decision, rejected alternative and a measurable condition that would trigger reconsideration.
7. **Dependency failures remain typed.** Discovery, schema, authorization, timeout, startup, readiness, overload and evaluation failures must not collapse into “AI error.”
8. **Week 4 uses a disposable non-production environment and the same candidate release.** Rebuilding after evaluation breaks the evidence chain.

### Example 1 — Week 1 turns a lexical miss into a controlled baseline

Start both languages without a model, network or database. Freeze `atlas-path-v16` with four questions: one exact phrase, one paraphrase, one genuine no-answer and one cross-tenant lure. The baseline uses token normalization, deterministic scoring, authority filtering before ranking and citation membership.

| Question | Authorized evidence | Lexical result | Required outcome |
|---|---|---|---|
| “reset a payment token” | `pay-17` contains exact phrase | `pay-17` score 7 | cited answer |
| “rotate the credential used for charges” | `pay-17` uses different words | no candidate | abstain and record retrieval miss |
| “change the lunar region” | none | no candidate | abstain |
| payments request; executive document has exact phrase | executive-only `exec-9` | removed before scoring | abstain, security gate passes |

The useful trace is explicit:

```text
fixture=atlas-path-v16 release=r16 tenant=payments
q=paraphrase normalized=[rotate,credential,used,charges]
authorized_docs=[pay-17,pay-22] lexical_candidates=[]
answer=ABSTAIN reason=NO_SUPPORTED_EVIDENCE
diagnosis=LEXICAL_PARAPHRASE_MISS regression=paraphrase-02
```

Do not weaken abstention to make the example look successful. Preserve the miss as a regression case, then use it to justify the bounded Week 2 experiment. A minimal deterministic core can expose the same contract in either language:

```java
List<Chunk> allowed = chunks.stream()
    .filter(c -> c.tenant().equals(verifiedTenant))
    .toList();
List<Scored> ranked = lexicalRank(query, allowed); // stable score, then chunk ID
return ranked.isEmpty() ? Answer.abstain("NO_SUPPORTED_EVIDENCE")
                        : Answer.cited(ranked.getFirst().chunkId());
```

```python
allowed = [chunk for chunk in chunks if chunk.tenant == verified_tenant]
ranked = lexical_rank(query, allowed)  # stable score, then chunk ID
answer = abstain("NO_SUPPORTED_EVIDENCE") if not ranked else cited(ranked[0].chunk_id)
```

```mermaid
sequenceDiagram
    participant T as Test fixture
    participant A as Authority filter
    participant L as Lexical core
    participant G as Exit gate
    T->>A: query + verified tenant + frozen docs
    A-->>L: authorized documents only
    L-->>T: no candidate for paraphrase
    T->>T: add paraphrase-02 regression
    T-->>G: exact pass + safe abstention + tenant negative pass
```

Week 1 exits when the fixture revision, expected IDs, abstentions, score trace and security-negative test are committed and both languages agree. It does not claim semantic retrieval quality.

### Example 2 — Week 2 compares retrieval strategies while the model stays fixed

Pin `model=m17`, `embedding=e4`, `corpus=atlas-path-v16`, chunker `c3`, question set `q16` and release `r16`. Now the only comparison variable is retrieval strategy. Run lexical, vector and hybrid retrieval on the same 100 questions, including paraphrase, no-answer and authorization-negative slices.

| Strategy | Supported answers | Unsafe answers | No-answer precision | Decision |
|---|---:|---:|---:|---|
| lexical | 60 | 0 | 1.00 | safe baseline, weak paraphrase recall |
| vector | 92 | 1 | 0.91 | blocked by unsafe answer |
| hybrid | 91 | 0 | 0.97 | selected for this revision |

The vector score is not a win because one unsupported answer is a hard failure. Hybrid is the safe winner even though its supported-answer count is one lower. If every strategy has an unsafe answer, the correct result is `NO_SAFE_WINNER`, not “pick the least bad.”

```text
run=retrieval-2026-10-26 release=r16 corpus=atlas-path-v16 model=m17
change=retriever-only
vector/paraphrase-02 candidates=[pay-17:0.84] supported=true
vector/no-answer-07 candidates=[pay-88:0.79] generated=true cited=false
vector outcome=BLOCK_UNSUPPORTED_ANSWER
hybrid/no-answer-07 candidates=[] outcome=ABSTAIN
selected=HYBRID reason=highest_supported_with_zero_unsafe
```

```mermaid
flowchart TD
    I["Fixed release, corpus, chunks and model"] --> L["Lexical run"]
    I --> V["Vector run"]
    I --> H["Hybrid run"]
    L --> S{"unsafe answers = 0?"}
    V --> S
    H --> S
    S -->|no| X["exclude strategy"]
    S -->|yes| Q["compare declared quality slices"]
    Q --> C["select best safe result or no winner"]
```

Spring AI's evaluation interfaces can add a context-aware relevancy signal, but deterministic authorization, citation membership and expected-evidence checks remain authoritative. Week 2 exits with a versioned result table, per-slice failures, the fixed identity vector and a decision record. It does not claim provider permanence or general intelligence.

### Example 3 — Week 3 proves an MCP capability, not merely a healthy process

Expose one read-only fixture capability, `lookup_runbook`, from both language implementations. Its input schema accepts a bounded `topic` and server-derived scope; the client cannot choose a tenant. Register the capability, connect a compatible host, inspect discovery and schemas, then exercise success and three negative paths.

| Step | Request | Expected result |
|---|---|---|
| discovery | `tools/list` | exactly one `lookup_runbook` with the reviewed schema |
| success | topic `token rotation`, verified payments scope | revisioned synthetic citation |
| unknown | topic outside fixture | typed `NOT_FOUND`, no invented content |
| unauthorized | executive topic under payments scope | typed `FORBIDDEN`, no object existence leak |
| timeout | fixture delay exceeds owned deadline | typed `TIMEOUT`, cancelled work terminates |

```text
host=mcp-inspector sdk_revision=pinned protocol=negotiated
tools/list -> lookup_runbook schema_sha256=9f... scope=server-derived read_only=true
tools/call token-rotation -> OK citation=fixture://runbook/pay-17@v3
tools/call executive-plan -> FORBIDDEN response_body=generic
tools/call delayed -> TIMEOUT elapsed_ms=501 deadline_ms=500 terminal=true
gate=MCP_BOUNDARY_READY
```

```mermaid
sequenceDiagram
    participant H as Compatible host
    participant S as MCP server
    participant A as Authority
    participant F as Read-only fixture
    H->>S: tools/list
    S-->>H: reviewed lookup_runbook schema
    H->>S: tools/call(topic, authenticated context)
    S->>A: derive and verify scope
    A-->>S: payments scope
    S->>F: bounded lookup + deadline
    F-->>S: revisioned result or typed failure
    S-->>H: schema-valid response
```

Use MCP Inspector interactively while developing, and its CLI mode for repeatable discovery/call checks in CI. A green HTTP health endpoint cannot prove registration, schema publication, host admission or authorization. Week 3 exits only when all four predicates—discovery, schema, authorization-negative behavior and bounded timeout—pass for the reviewed fixture.

### Example 4 — Week 4 deploys the same release and rehearses failure

Build one immutable image for `r16`, attach its digest to the earlier evidence and deploy that digest to a disposable namespace. Separate startup, readiness and liveness checks. Then run the small authorized smoke plus controlled dependency failure, overload and recovery drills before any promotion decision.

| Drill | Observable transition | Required evidence |
|---|---|---|
| slow initialization | startup remains false; liveness does not kill early | startup eventually succeeds within budget |
| model outage | readiness becomes false; pod leaves Service endpoints | no unsupported fallback; recovery restores readiness |
| overload | admission rejects before unbounded queue growth | typed overload; latency and permits recover |
| stuck process | liveness fails after startup | Kubernetes restarts the container |
| bad candidate | rollout does not become available | rollout status fails and rollback path is ready |
| recovered candidate | same digest serves authorized smoke | evaluation artifact and deployment identity join |

```text
release=r16 image=registry/atlas@sha256:16ab... namespace=atlas-r16
startup 0 -> 1 at 34s
authorized_smoke=PASS forbidden_smoke=PASS
model_outage readiness 1 -> 0 endpoints 3 -> 0 unsupported_answers=0
model_recovery readiness 0 -> 1 endpoints 0 -> 3
overload admitted=40 rejected=60 leaked_permits=0
evaluation=evalset-v16 thresholds=PASS rollout=COMPLETE
promotion_gate=READY
```

```mermaid
stateDiagram-v2
    [*] --> Starting
    Starting --> Ready: startup and readiness pass
    Starting --> Blocked: startup budget fails
    Ready --> Unready: model outage or overload breach
    Unready --> Ready: dependency and permits recover
    Ready --> Promote: same-release evaluation and smoke pass
    Unready --> Rollback: recovery budget fails
    Ready --> Rollback: evaluation or security gate fails
```

GitHub Actions deployment environments can require reviewers, restrict branches/tags and gate access to environment secrets. Those controls govern deployment admission; they do not replace runtime probes, evaluation or authorization tests. Kubernetes Deployment rollout status proves rollout progress for the configured workload, while startup, readiness and liveness probes own different runtime claims.

### Decision record and promotion manifest

Keep each record short enough to review but precise enough to invalidate later:

| Field | Week 2 example |
|---|---|
| Context | lexical misses 18/20 paraphrases; vector has one unsupported no-answer |
| Decision | use hybrid retrieval for `r16` after zero-unsafe slice passes |
| Rejected alternative | change model and retriever together |
| Reconsider when | recall@5 falls below 0.90 or no-answer precision below 0.95 |

The promotion manifest should reference, rather than copy, the four evidence artifacts and their immutable identities. The gate rejects missing weeks, duplicate week evidence, a different release, any security failure or the first failed weekly predicate. This prevents a late success from erasing an earlier failure.

### Paired executable evidence-chain contract

[`ImplementationPathContracts.java`](ImplementationPathContracts.java) and [`implementation_path_contracts.py`](implementation_path_contracts.py) make safe retrieval selection, MCP boundary precedence, deployment failures, decision-record completeness and same-release promotion executable without external dependencies:

```bash
java -ea study-guides/backend/spring-ai-python/ImplementationPathContracts.java
python3 study-guides/backend/spring-ai-python/implementation_path_contracts.py
```

Both print the same first line:

```text
PARITY|W1=OFFLINE_BASELINE|W2=MODEL_RETRIEVAL|W3=MCP_BOUNDARY|W4=DISPOSABLE_DEPLOYMENT|ADR=MEASURED|PROMOTION=EVIDENCE_CHAIN|READY=5437
```

Each implementation passes 31 explicit scenario groups and 10,000 seeded evidence-chain trials. The oracle covers missing and duplicate evidence, release mismatch, security precedence, each weekly gate, safe retrieval selection, MCP discovery/schema/auth/timeout failures and deployment release/startup/readiness/resilience/evaluation failures. These dependency-free contracts do not start Spring Boot, a Python API, an MCP host, a model, GitHub Actions or Kubernetes; those claims require the named integration and disposable environments.

### Current primary references

Verified on 26 October 2026:

- Spring AI, [Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html) and [Evaluation testing](https://docs.spring.io/spring-ai/reference/api/testing.html) — current dependency/configuration entry point plus `Evaluator`, `EvaluationRequest` and context-aware relevancy evaluation; deterministic security and evidence checks remain separate gates.
- Model Context Protocol, [Inspector](https://modelcontextprotocol.io/docs/tools/inspector) and [Inspector CLI](https://modelcontextprotocol.io/docs/tools/inspector#cli-mode) — the reference server-testing tool and repeatable CLI discovery/call workflow.
- Model Context Protocol Python SDK, [README and server quickstart](https://github.com/modelcontextprotocol/python-sdk) and Spring AI, [MCP server boot starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) — current language-specific server construction, transport and capability-registration boundaries.
- Kubernetes, [Deployments](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/), [rollout commands](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_rollout/) and [liveness, readiness and startup probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/) — controlled rollout/rollback plus distinct restart, traffic-admission and initialization semantics.
- GitHub Actions, [Deployment environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments) and [environment protection rules](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments#deployment-protection-rules) — reviewer, branch/tag and secret-access gates around deployments.

### Source provenance and continuation

The selected editable bundle member's complete Section 16 is source lines 932–942, bounded before Section 17 at line 944. The compared standalone HTML is lines 1053–1059, bounded before Chapter 17 at line 1060. Both prescribe the same four weeks and the same requirement for a short decision record, so the editable Markdown remains the structural source and no duplicate guide was created. This canonical section preserves every source task while adding entry/exit gates, fixed identities, four worked traces, five diagrams, safe-selection and failure-precedence rules, current primary references and paired executable contracts. Section 17 follows after the fair domain rotation; Sections 17–25 remain queued, so the source collection is not complete.

## 17. Design an AI API as a durable business contract

A public API is the product boundary, not a serialized provider SDK. Clients should see application concepts—questions, owned conversations, citations and ingestion jobs—while the server owns tenant identity, provider choice, model revision, evidence policy and release identity. This keeps provider migration possible and prevents callers from selecting unsafe models, arbitrary base URLs or filesystem paths.

### Contract requirements and invariants

1. **Derive authority from verified identity.** Tenant and subject never come from the JSON body. Every conversation, citation, upload and job lookup is re-authorized.
2. **Keep the DTO provider-neutral.** Public fields describe business intent. Provider parameters remain behind a versioned adapter and a server-side capability policy.
3. **Treat correlation as untrusted data.** A bounded `X-Request-ID` can aid correlation, but it is not authentication, an idempotency key or permission to create arbitrary log labels.
4. **Return validated evidence, not private reasoning.** Citations must be visible to the subject, members of the supplied evidence set and supportive of the returned claim. Do not expose prompts, tokens, stack traces or raw chain-of-thought.
5. **Make failure ownership stable.** Authentication, authorization, input, local capacity, dependency availability, gateway timeout and insufficient evidence are different outcomes. Stable application codes let clients react without parsing prose.
6. **Scope idempotency by authority and operation.** `(verified tenant, operation, key)` identifies one canonical payload hash and one job. The same key and same hash replays the original result; the same key with a different hash conflicts.
7. **Commit the idempotency record, job and outbox event atomically.** A database uniqueness constraint arbitrates concurrent creators. A failed transaction leaves none of the three visible.
8. **Assume at-least-once delivery.** An outbox relay can publish before crashing and publish again after restart. Workers deduplicate stage effects; external side effects need their own reconciliation identifiers.
9. **Job state is monotonic and tenant-scoped.** Polling uses the verified tenant, and a concealment policy can return the same `404` for an absent job and another tenant's job.
10. **A timeout makes write outcome unknown.** Reconcile through the returned or deterministic job resource before blindly resubmitting.

```mermaid
flowchart LR
    C["Public client DTO"] --> A["Application contract"]
    A --> I["Identity and authority"]
    A --> E["Evidence policy"]
    A --> P["Provider adapter"]
    P --> X["Provider-specific request"]
```

### Example 1 — synchronous answers expose application evidence

The request expresses intent and optional owned context. The service derives security and runtime choices:

```http
POST /v1/answers HTTP/1.1
Authorization: Bearer <user-access-token>
Content-Type: application/json
X-Request-ID: web-7e51

{
  "question": "How do I roll back payments in staging?",
  "conversationId": "conv_42",
  "responseMode": "answer-with-citations"
}
```

```json
{
  "answerId": "ans_01",
  "status": "complete",
  "answer": "Use the approved rollback job for the staging release.",
  "citations": [
    {"documentId": "payments-r7", "revision": 7, "chunkId": "c03"}
  ],
  "evidenceGeneration": "runbooks-g43",
  "releaseId": "atlas-r18",
  "requestId": "srv-91c0"
}
```

The server trace is auditable without capturing secrets or content by default:

```text
request=srv-91c0 client_request=web-7e51 subject_hash=89f... tenant=payments
conversation=conv_42 ownership=PASS admission=ADMITTED
evidence_generation=runbooks-g43 visible_candidates=12 supplied=[payments-r7/c03]
provider_adapter=chat-port-v4 model_policy=standard-answer deadline_ms=1800
schema=PASS citation_membership=PASS citation_support=PASS outcome=COMPLETE
```

An insufficient-evidence result is still a successful domain response. It has no invented answer or citation:

```json
{
  "answerId": "ans_02",
  "status": "insufficient_evidence",
  "answer": null,
  "citations": [],
  "requestId": "srv-91c1"
}
```

Minimal controller code should pass a verified principal and a closed request DTO, not a client-supplied tenant or provider object:

```java
AnswerResponse answer(VerifiedPrincipal principal, AnswerRequest request) {
    ValidatedQuestion question = validator.validate(request.question());
    OwnedConversation conversation = conversations.requireOwned(
        principal.tenant(), principal.subject(), request.conversationId());
    return answerService.answer(principal, conversation, question, request.responseMode());
}
```

```python
def answer(principal: VerifiedPrincipal, request: AnswerRequest) -> AnswerResponse:
    question = validator.validate(request.question)
    conversation = conversations.require_owned(
        principal.tenant, principal.subject, request.conversation_id
    )
    return answer_service.answer(principal, conversation, question, request.response_mode)
```

### Example 2 — error semantics identify the repair owner

Use `application/problem+json` with a stable application `type`/code, a safe detail and a server request ID. The HTTP status is necessary but does not carry the whole business meaning.

| Condition | HTTP result | Retry rule |
|---|---|---|
| malformed JSON | `400` | repair representation |
| well-formed but invalid/oversized DTO | documented `422` | repair request; do not retry unchanged |
| missing or invalid credentials | `401` plus applicable challenge | reauthenticate |
| authenticated but forbidden | `403`, or consistent `404` concealment | do not retry for permission |
| local admission exhausted | `429` plus bounded `Retry-After` when known | back off with jitter and deadline |
| required dependency unavailable | `503` | retry only if operation policy permits |
| upstream deadline while acting as a gateway | `504` | writes remain outcome-unknown |
| no supporting evidence | `200` with `insufficient_evidence` | clarify or escalate |

```json
{
  "type": "https://atlas.example/problems/local-overload",
  "title": "Request not admitted",
  "status": 429,
  "detail": "Capacity is temporarily unavailable.",
  "instance": "/v1/answers/srv-91c2",
  "code": "LOCAL_ADMISSION_EXHAUSTED",
  "requestId": "srv-91c2"
}
```

```mermaid
flowchart TD
    R["Request"] --> A{"authenticated?"}
    A -->|no| U["401"]
    A -->|yes| Z{"authorized?"}
    Z -->|no| F["403 or concealed 404"]
    Z -->|yes| V{"valid and admitted?"}
    V -->|invalid| Q["400 or 422"]
    V -->|overload| L["429"]
    V -->|yes| D{"dependency and deadline?"}
    D -->|unavailable| S["503"]
    D -->|gateway timeout| G["504"]
    D -->|yes| E["complete or insufficient evidence"]
```

Precedence matters when several facts are false. Authentication and authorization fail before content-dependent detail; validation fails before expensive work; admission fails before queue growth. A `503` does not hide a caller input error, and `insufficient_evidence` does not become a `500` merely because a model participated.

### Example 3 — asynchronous ingestion is replay-safe

The client supplies a high-entropy idempotency key and a reference to an already authorized upload. The service canonicalizes the business payload, hashes it and commits one record:

```http
POST /v1/ingestion-jobs HTTP/1.1
Authorization: Bearer <token>
Idempotency-Key: upload-42-7d36f410
Content-Type: application/json

{"sourceId":"runbooks/payments","sourceRevision":"r8","uploadId":"owned-upload-42"}
```

```http
HTTP/1.1 202 Accepted
Location: /v1/ingestion-jobs/job_42
Content-Type: application/json

{"jobId":"job_42","state":"accepted"}
```

`202 Accepted` means processing was accepted, not completed or guaranteed to succeed. The `Location` resource is the authority for later state.

| Arrival | Stored scope/hash | Result |
|---|---|---|
| first request | none | atomically create key, `job_42`, outbox; return `202` |
| retry after lost response | same tenant/operation/key and same hash | return `job_42`; do not create work |
| accidental key reuse | same scope, different hash | `409` application conflict |
| same key, other tenant | different scope | independent job after authorization |
| concurrent first requests | unique constraint decides winner | loser reads winner and applies same-hash rule |

```sql
BEGIN;
INSERT INTO idempotency_record(tenant_id, operation, key, payload_hash, job_id)
VALUES (:tenant, 'INGEST', :key, :hash, :job)
ON CONFLICT DO NOTHING;
INSERT INTO ingestion_job(job_id, tenant_id, state) VALUES (:job, :tenant, 'ACCEPTED');
INSERT INTO outbox(event_id, aggregate_id, kind) VALUES (:event, :job, 'INGEST_REQUESTED');
COMMIT;
```

The sketch omits the required winner-read branch: production code inserts all three only when it owns the idempotency row; otherwise it reads the committed row and compares hashes. Do not continue after `ON CONFLICT DO NOTHING` as though ownership were acquired.

```mermaid
sequenceDiagram
    participant C as Client
    participant A as API
    participant D as Database
    participant R as Outbox relay
    participant W as Worker
    C->>A: POST + key + canonical payload
    A->>D: transaction: scoped key + job + outbox
    D-->>A: commit job_42
    A-->>C: 202 + Location
    R->>D: claim unpublished event
    R->>W: publish job_42
    W->>D: idempotent stage write
```

Trace the ambiguous-response case rather than assuming failure:

```text
t0 key=upload-42-7d36f410 hash=ab31... transaction=COMMIT job=job_42
t1 api response lost; client outcome=UNKNOWN
t2 client repeats same key/hash
t3 uniqueness winner found job_42; response=202 Location=/v1/ingestion-jobs/job_42
jobs_created=1 outbox_events_created=1
```

### Example 4 — duplicate delivery and polling stay safe

The relay cannot atomically commit its database acknowledgement with a broker publish in the general case. A crash after publish leaves the event eligible for another delivery. The worker therefore applies a unique `(job_id, stage)` effect or an equivalent compare-and-set transition.

```text
relay publish event=evt_42 delivery=1
worker apply job_42/EXTRACTED -> committed
relay crash before sent_at update
relay restart; publish event=evt_42 delivery=2
worker apply job_42/EXTRACTED -> duplicate, no effect
worker apply job_42/INDEXED -> committed
state ACCEPTED -> EXTRACTED -> INDEXED; backward/skip transitions rejected
```

```mermaid
stateDiagram-v2
    [*] --> Accepted
    Accepted --> Extracted: unique stage effect
    Extracted --> Indexed: unique stage effect
    Indexed --> Complete: publication committed
    Accepted --> Failed: terminal typed failure
    Extracted --> Failed: terminal typed failure
    Indexed --> Failed: terminal typed failure
```

`GET /v1/ingestion-jobs/job_42` first verifies identity and then queries with tenant scope. Returning `404` for both absent and foreign jobs avoids an existence oracle, but logs should retain an internal forbidden-versus-absent distinction. The response can expose state, safe failure code, progress counters and immutable input revision; it must not expose another tenant's source name, internal broker offsets or secrets.

Exactly-once job creation does not make a remote side effect exactly once. If a worker calls a document converter, object store or billing system, attach a stable operation ID and reconcile that provider's result before repeating an outcome-unknown call.

### Paired executable durable-API contract

[`DurableApiContracts.java`](DurableApiContracts.java) and [`durable_api_contracts.py`](durable_api_contracts.py) encode the status precedence, scoped-key/hash rules, atomic rollback, duplicate relay, monotonic stage writes and tenant-concealed polling without external dependencies:

```bash
java -ea study-guides/backend/spring-ai-python/DurableApiContracts.java
python3 study-guides/backend/spring-ai-python/durable_api_contracts.py
```

Both print the same first line:

```text
PARITY|SYNC=PROVIDER_NEUTRAL|ERROR=PROBLEM_DETAIL|ASYNC=202_LOCATION|IDEMPOTENCY=SCOPED_HASH|OUTBOX=AT_LEAST_ONCE|WORKER=STAGE_DEDUP|READY=2741
```

Each implementation passes 30 explicit scenario groups and 10,000 seeded answer-precedence trials. The contract is an executable decision model, not proof of Spring MVC wiring, ASGI behavior, database isolation, broker durability or external-side-effect reconciliation; those require real integration tests.

### Current primary references

Verified on 27 October 2026:

- IETF, [RFC 9110: HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html) — `202` is intentionally noncommittal; `401`, `403`, `409`, `422`, `503` and `504` have distinct semantics, and `503` may carry `Retry-After`.
- IETF, [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html) — current standards-track `application/problem+json` fields and the warning not to expose implementation internals.
- Spring Framework, [Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html) — `ProblemDetail`, `ErrorResponse`, `ResponseEntityExceptionHandler` and `application/problem+json` support for Spring MVC.
- IETF HTTPAPI Working Group, [Idempotency-Key HTTP Header Field draft](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/) — key uniqueness, payload fingerprints and replay/conflict patterns. This is an expired Internet-Draft, not an Internet Standard; the application must document scope, canonicalization, retention and concurrent-request behavior.
- PostgreSQL, [Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html) and [Transactions](https://www.postgresql.org/docs/current/tutorial-transactions.html) — uniqueness as the concurrency arbiter and all-or-nothing visibility for the idempotency/job/outbox write set.

### Source provenance and continuation

The selected editable bundle member's complete Section 17 is source lines 944–1031, bounded before Section 18 at line 1033. The compared standalone HTML is lines 1060–1163, bounded before Section 18 at line 1164. Both contain the same synchronous answer DTO, response/error guidance, asynchronous ingestion contract and outbox warning, so the editable Markdown remains the structural source and no duplicate guide was created. This canonical section retains those requirements while adding exact precedence, four worked traces, four diagrams, canonical-hash and concurrency rules, tenant-concealed polling, external-side-effect reconciliation, current primary references and paired executable contracts. Section 18, **Streaming: transport success and answer completion are different**, begins at editable line 1033 / standalone HTML line 1164. Sections 18–25 remain queued; the wider collection is not complete.

## 18. Streaming: transport success and answer completion are different

Section 11 chose transports and defined general streaming states. This section narrows the production contract: after the response is committed, answer success is expressed by a terminal application event, not by changing the HTTP status. Every reconnect, side effect and cleanup callback must preserve that distinction.

### Requirements and invariants

1. **Commit HTTP status only once.** Authenticate, authorize, validate request shape and acquire admission capacity before starting the body. After bytes for a `200` response are committed, a generation failure cannot become a second HTTP response; emit a typed `error` event if possible and close.
2. **Use an explicit terminal event.** `metadata`, `delta` and `citation` are provisional. Exactly one ordered `complete` or `error` event may terminate the application stream. EOF without either terminal event means `incomplete`, never successful.
3. **Frame data, do not concatenate it.** SSE is UTF-8 text with events separated by a blank line. Serialize payloads as JSON so quotes, backslashes and newlines remain inside `data:` rather than injecting fields or frame boundaries. Restrict event names and IDs to an application grammar.
4. **Make sequence and replay claims truthful.** Persist a contiguous, request-scoped event suffix before advertising resumability. Validate `Last-Event-ID`, authorize the answer again, replay the complete missing interval and return `reset_required` when retention cannot cover it.
5. **Deduplicate actions independently of event replay.** A reconnect can repeat delivery even when event IDs are ordered. Every tool call or other mutation needs a stable action ID and a server-side idempotency record; replaying a `tool_result` must not invoke the tool again.
6. **Bound ownership.** The caller owns one end-to-end deadline, admission permit, bounded event buffer, upstream handle and replay-retention policy. Spring or FastAPI framing adapters do not create provider capacity or cancel remote computation automatically.
7. **Release exactly once.** Completion, timeout, network error, explicit cancellation and setup failure may race. Route all of them through one idempotent cleanup gate that cancels upstream where supported, closes resources and releases the permit once.
8. **Do not swallow cancellation.** Python `asyncio.CancelledError` is a `BaseException`; use `finally` for cleanup and normally propagate cancellation. In Spring MVC, completion callbacks can run for timeout or network error as well as normal completion, so callbacks must tolerate repeated cleanup attempts.

### Example 1 — frame JSON and make the terminal event visible

The application admits request `r42`, assigns answer `a42` and persists each sequence before sending it. The model's newline stays escaped inside JSON:

```text
event: metadata
id: 1
data: {"requestId":"r42","answerId":"a42"}

event: delta
id: 2
data: {"text":"Use the approved\nstaging rollback job."}

event: complete
id: 3
data: {"status":"complete","citations":["payments-r7"]}

```

The blank line dispatches each event. `id: 3` is an application cursor only because this service retains the ordered events for `a42`; it is not a global offset. The terminal payload is sent only after citation membership, support and business validation pass.

```mermaid
flowchart TD
    R["Authorized request"] --> A{"Admit capacity?"}
    A -->|no| O["503 before body"]
    A -->|yes| S["Commit 200 stream"]
    S --> P["Persist + send provisional events"]
    P --> V{"Final gates pass?"}
    V -->|yes| C["complete + close"]
    V -->|no| E["error + close"]
```

A minimal Python framing adapter can keep the event grammar and JSON encoding in one place. It remains the caller's job to authorize, admit, deadline and close the upstream response:

```python
import json

ALLOWED = {"metadata", "delta", "citation", "complete", "error"}

def sse_frame(event: str, event_id: int, payload: dict) -> bytes:
    if event not in ALLOWED or event_id < 1:
        raise ValueError("invalid SSE field")
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    return f"event: {event}\nid: {event_id}\ndata: {body}\n\n".encode()
```

### Example 2 — EOF is incomplete; an application error is not a new HTTP status

Suppose the provider emits two deltas and then fails. If no response bytes were sent yet, the endpoint may still return an ordinary problem response. Once the `200 text/event-stream` body has started, the application can only send an `error` event if the connection remains writable, then close. If even that write fails, the client sees EOF without a terminal event and records `incomplete`.

```mermaid
stateDiagram-v2
    [*] --> Open: 200 body starts
    Open --> Open: metadata / delta / citation
    Open --> Complete: final checks pass
    Open --> Error: typed failure event sent
    Open --> Incomplete: EOF / disconnect / timeout
    Complete --> [*]
    Error --> [*]
    Incomplete --> [*]
```

| Wire observation | Client answer state | May be treated as verified? |
|---|---|---|
| deltas, stream still open | provisional | no |
| `complete` at next sequence | complete | yes, subject to the declared final gates |
| `error` at next sequence | failed | no |
| EOF, timeout or sequence gap before terminal | incomplete | no |
| second terminal event | protocol violation | no |

A client must not infer completion from a syntactically well-formed final sentence, a `[DONE]` provider marker or a clean TCP close. Only the application's ordered terminal event carries that meaning.

### Example 3 — reconnect replays events, not side effects

Request `a42` retains events 7–12. A reconnect with `Last-Event-ID: 9` is authenticated, re-authorized for `a42`, and replays 10–12. Event 10 reports the result of tool action `tool-a42-1`; the service returns the recorded result instead of calling the tool again. A cursor of 4 cannot be satisfied from the retained suffix and receives `reset_required`.

```mermaid
sequenceDiagram
    participant C as Client
    participant A as SSE endpoint
    participant L as Event ledger
    participant T as Action ledger
    C->>A: a42 + Last-Event-ID 9
    A->>A: authenticate + authorize a42
    A->>L: read contiguous 10..12
    L-->>A: tool_result, delta, complete
    A->>T: verify tool-a42-1 already applied
    T-->>A: recorded result
    A-->>C: replay 10..12; no tool execution
```

Replay is refused rather than guessed when the cursor is malformed, ahead of the latest event, older than the retained predecessor, belongs to another answer or would cross an authorization change. A server may instead create a new answer resource, but it must not splice a gap into the old stream and call it continuous.

### Example 4 — one cleanup gate handles racing callbacks

Spring MVC's emitter callbacks may observe completion, timeout or an error. The provider task may also notice cancellation. All paths call the same cleanup object; its compare-and-set winner cancels the upstream and releases the permit, while later calls become no-ops.

```java
final class Cleanup {
    private final AtomicBoolean done = new AtomicBoolean();
    private final Future<?> upstream;
    private final Semaphore permits;

    void run() {
        if (done.compareAndSet(false, true)) {
            upstream.cancel(true);       // cooperative; provider may already be working
            permits.release();           // exactly one release
        }
    }
}

SseEmitter emitter = new SseEmitter(20_000L);
emitter.onCompletion(cleanup::run);
emitter.onTimeout(cleanup::run);
emitter.onError(error -> cleanup.run());
```

The executor must also be bounded. One virtual thread per admitted stream can reduce Java thread-management cost, but admission is still capped by provider quota, database connections, memory and replay-buffer capacity. A reactive return type does not make a blocking provider client nonblocking.

```mermaid
flowchart TD
    N["normal completion"] --> G["atomic cleanup gate"]
    T["timeout"] --> G
    D["disconnect / error"] --> G
    X["explicit cancel"] --> G
    G -->|winner| U["cancel upstream + close + release permit"]
    G -->|later caller| Z["no-op"]
```

For Python, put release and upstream close in `finally`. If cancellation is caught to add telemetry, re-raise it after cleanup; a broad `except BaseException` that converts cancellation into an application `error` event can defeat task-group and timeout semantics.

### Paired executable SSE-completion contract

[`SseCompletionContracts.java`](SseCompletionContracts.java) and [`sse_completion_contracts.py`](sse_completion_contracts.py) model JSON-safe framing, contiguous sequences, exactly one terminal event, EOF-to-incomplete conversion, retained replay, idempotent action IDs and exactly-once permit release without external dependencies:

```bash
java -ea study-guides/backend/spring-ai-python/SseCompletionContracts.java
python3 study-guides/backend/spring-ai-python/sse_completion_contracts.py
```

Both print the same first line:

```text
PARITY|FRAMING=SSE_JSON|TERMINAL=EXACTLY_ONCE|CLOSE=INCOMPLETE|REPLAY=ORDERED|ACTION=IDEMPOTENT|PERMIT=RELEASE_ONCE|COMPLETE=3281
```

Each implementation passes 30 explicit scenario groups and 10,000 seeded terminal/cleanup trials. These contracts do not open a socket, prove proxy behavior, authenticate a real reconnect, persist an event/action ledger, cancel a provider, start Spring/FastAPI or load-test admission; those require framework and integration tests.

### Current primary references

Verified on 28 October 2026:

- WHATWG HTML, [Server-sent events](https://html.spec.whatwg.org/dev/server-sent-events.html) — UTF-8 `text/event-stream` grammar, blank-line dispatch, `id`, reconnection and `Last-Event-ID` behavior.
- Spring Framework, [`SseEmitter`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html) and [`ResponseBodyEmitter`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseBodyEmitter.html) — typed SSE event builders, custom timeouts and completion/error/timeout callbacks.
- FastAPI, [Stream Data](https://fastapi.tiangolo.com/advanced/stream-data/), [StreamingResponse](https://fastapi.tiangolo.com/advanced/custom-response/) and [Lifespan Events](https://fastapi.tiangolo.com/advanced/events/) — iterator-byte ownership, cancellation points and application-scoped resource setup/cleanup.
- Python, [`asyncio` tasks and cancellation](https://docs.python.org/3/library/asyncio-task.html) — `CancelledError` as `BaseException`, `try/finally` cleanup and normally propagating cancellation after cleanup.

### Source provenance and continuation

The selected editable bundle member's complete Section 18 is source lines 1033–1117, bounded before Section 19 at line 1119. The compared standalone HTML is lines 1164–1232, bounded before Section 19 at line 1233; the bundle's HTML member is byte-identical to that standalone file. Both contain the same SSE frame, post-commit failure rule, authenticated-POST caveat, replay/action warning, Python cancellation/lifespan ownership and Spring MVC emitter cleanup requirements. The editable Markdown remains the structural source and no duplicate guide was created. Because canonical Section 11 already covers general transport choice, this section adds the narrower terminal-event, JSON framing, replay/action idempotency and cleanup-race contracts instead of duplicating it. Section 19, **Authentication in Spring and Python: verified claims to query predicates**, begins at editable line 1119 / standalone HTML line 1233. Sections 19–25 remain queued; the wider collection is not complete.

## 19. Authentication in Spring and Python: verified claims to query predicates

Authentication is not “decode a JWT and copy `tenant` into SQL.” The production boundary is a chain of evidence: validate the token for this resource, resolve the verified subject to current server-side membership, derive query predicates from that identity, and authorize every post-model tool action again. Spring Security and FastAPI can host the chain; the application still owns its policy.

### Requirements and invariants

1. **Treat every received field as untrusted until verification completes.** Pin acceptable algorithms and trusted issuer/key sources. Validate the signature, issuer, audience, expiry, not-before time and required subject; never choose an arbitrary key URL from an untrusted header.
2. **Bind keys to the issuer and token to the resource.** A valid signature under some key is insufficient. The key must belong to the configured issuer and the audience must name this API. An ID token or token for another service is not an access token for this service.
3. **Separate identity hints from authority.** A verified `tenant` claim is authoritative only when the issuer contract says so. Otherwise resolve `(issuer, subject)` through a server-owned directory and use its current memberships and permissions.
4. **Create one immutable request identity.** Framework adapters produce `Identity(subject, memberships, permissions, tokenId/expiry)`. Controllers, repositories and tools accept that object, not a caller-supplied tenant string.
5. **Compile authorization into data access.** The repository adds tenant/project/ACL predicates from the identity before retrieval. Never fetch globally and filter after ranking or generation; existence, scores, snippets and timing can leak.
6. **Use row security as defense in depth, not the only gate.** Enable policies deliberately, test with a non-owner/non-`BYPASSRLS` role, and set request context transaction-locally. A session-level tenant variable on a pooled connection can leak into the next request.
7. **Authorize after the model.** Tool schemas validate shape, not ownership. Re-check resource scope and action permission under the verified subject; a host or MCP service credential must not silently widen the user's authority.
8. **Make delegation explicit.** Decide whether a downstream call is service identity, user impersonation or constrained delegation. Preserve current subject and actor where the authorization system supports it; use a resource-specific, least-privilege token rather than forwarding the original bearer token everywhere.
9. **Fail closed with stable semantics.** Missing/invalid credentials are `401` with a Bearer challenge; a valid identity lacking permission is `403`. Tenant-concealed object lookup may return `404`, but logs and metrics still record the internal denial reason without token contents.

```mermaid
flowchart TD
    T["Bearer token"] --> C{"signature + alg + issuer + audience + time valid?"}
    C -->|no| U["401 invalid_token"]
    C -->|yes| S["verified issuer + subject"]
    S --> D["resolve current memberships"]
    D --> P{"required permission?"}
    P -->|no| F["403 or concealed 404"]
    P -->|yes| I["immutable request identity"]
    I --> Q["repository/tool predicates"]
```

### Example 1 — good signature, wrong audience: reject before the repository

An identity provider issues a correctly signed token with `iss=https://id.example`, `sub=user-7`, `aud=analytics-api`, `exp=17:00`, and `tenant=customer-a`. The answer service expects audience `atlas-api`. Signature verification succeeds, but audience validation fails, so the request is `401 invalid_token`. The service performs no directory lookup, retrieval, model call or tool action.

Spring Security supports independent issuer and audience configuration:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://id.example
          audiences: https://atlas.example/api
```

A FastAPI dependency should call a maintained verifier with an explicit algorithm allow-list, issuer and audience. Merely Base64-decoding the payload proves nothing:

```python
def verified_claims(token: str) -> dict:
    return jwt.decode(
        token,
        key=trusted_key_for_configured_issuer(),
        algorithms=["RS256"],
        issuer="https://id.example",
        audience="https://atlas.example/api",
        options={"require": ["exp", "iss", "aud", "sub"]},
    )
```

The key resolver is configuration-bound and rotation-aware. It does not fetch a URL supplied by `jku`, concatenate raw `kid` into SQL/path names, or let the token select an algorithm outside the allow-list.

```mermaid
sequenceDiagram
    participant C as Caller
    participant V as JWT verifier
    participant D as Directory
    participant R as Repository
    C->>V: signed token for analytics-api
    V->>V: signature valid; audience mismatch
    V-->>C: 401 invalid_token
    Note over D,R: not called
```

### Example 2 — verified subject, untrusted tenant hint: membership owns the predicate

`(https://id.example, user-7)` is verified. The token and request body both say `tenant=customer-b`, but the service's membership directory currently grants `user-7` read access only to `customer-a`. The application does not convert either caller value into authority. It either rejects the requested tenant or derives `tenant_id IN ('customer-a')` from the directory result.

```java
record Identity(String subject, Set<String> tenants, Set<String> permissions) {}

List<Evidence> findEvidence(Identity identity, String requestedTenant, String query) {
    if (!identity.permissions().contains("evidence:read")
            || !identity.tenants().contains(requestedTenant)) {
        throw new AccessDeniedException("not permitted");
    }
    return evidenceRepository.search(
            requestedTenant, query); // parameterized tenant predicate is mandatory
}
```

The repository signature makes the authority input visible. A weaker API such as `search(String tenantFromBody, String query)` invites accidental bypass. Cache keys, vector filters, reranker inputs, citations and answer persistence must all retain the same identity/tenant scope.

```mermaid
flowchart LR
    H["token/body tenant hint: B"] -. untrusted .-> X["never becomes predicate"]
    S["verified issuer + subject"] --> M["directory membership: A"]
    M --> Q["WHERE tenant_id = A"]
    Q --> R["authorized evidence only"]
```

### Example 3 — transaction-local database context cannot survive pool reuse

Request A checks out connection 12 and is authorized for `customer-a`. Inside a transaction it sets a local context and queries an RLS-protected table. Commit or rollback ends the local value before the connection returns to the pool. Request B then receives the same physical connection but cannot inherit A's tenant.

```sql
BEGIN;
SELECT set_config('app.tenant_id', :verified_tenant, true); -- true = local
SELECT id, body
FROM evidence
WHERE tenant_id = :verified_tenant AND document_id = ANY(:authorized_ids);
COMMIT;

ALTER TABLE evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE evidence FORCE ROW LEVEL SECURITY;
CREATE POLICY evidence_tenant ON evidence
USING (tenant_id = current_setting('app.tenant_id', true));
```

The explicit query predicate remains useful for review and plan selectivity; RLS is a second gate. Tests must use the same application role, transaction and pool behavior as production. Table owners and roles with `BYPASSRLS` can bypass policies, and `SET SESSION` persists beyond a transaction.

```mermaid
sequenceDiagram
    participant A as Request A
    participant P as Pool connection 12
    participant B as Request B
    A->>P: BEGIN; SET LOCAL tenant=A
    A->>P: scoped query; COMMIT
    Note over P: local setting ends
    B->>P: BEGIN without tenant context
    P-->>B: default deny / no rows
```

### Example 4 — a valid tool schema does not authorize the proposed action

The model proposes `refund(orderId="order-b", amount=5000)`. The JSON schema accepts the shape, and the MCP connection is authenticated with a service credential. The tool still resolves `order-b`, verifies that the request identity belongs to its tenant, requires `orders:refund`, enforces the amount policy and records a stable action ID. A caller scoped only to customer A receives a concealed denial for customer B; the refund is not attempted.

```mermaid
flowchart TD
    M["model proposes tool arguments"] --> Z{"schema valid?"}
    Z -->|no| E["reject malformed call"]
    Z -->|yes| A{"subject membership + action scope + resource policy?"}
    A -->|no| D["deny; no side effect"]
    A -->|yes| K["idempotency record + mutation"]
    K --> L["audit subject, actor, resource, outcome"]
```

Transport-level MCP authorization protects the resource server, but the handler remains responsible for object ownership and operation policy. If the host acts on behalf of a user, constrained delegation keeps the user subject and current actor distinct; forwarding a broad service token or the original bearer token erases those boundaries.

### Paired executable identity-to-authority contract

[`AuthorizationBoundaryContracts.java`](AuthorizationBoundaryContracts.java) and [`authorization_boundary_contracts.py`](authorization_boundary_contracts.py) model the pure decision boundary without parsing or signing real JWTs. They require signature/algorithm/issuer/audience/time/subject evidence, resolve current memberships server-side, compile tenant predicates, prevent pooled transaction-context carryover and authorize tool resources separately:

```bash
java -ea study-guides/backend/spring-ai-python/AuthorizationBoundaryContracts.java
python3 study-guides/backend/spring-ai-python/authorization_boundary_contracts.py
```

Both print the same first line:

```text
PARITY|JWT=VERIFIED|AUDIENCE=BOUND|TENANT=DIRECTORY|QUERY=SCOPED|RLS=LOCAL|TOOL=RECHECKED|DELEGATION=EXPLICIT
```

Each implementation passes 30 explicit scenario groups and 10,000 seeded dual-oracle trials. These contracts do not perform cryptography, fetch JWKS, start Spring/FastAPI, connect to PostgreSQL, configure an identity provider, exchange downstream tokens or invoke an MCP server. Those claims require library, framework and integration tests with rotated keys, production roles and real pool reuse.

### Current primary references

Verified on 28 October 2026:

- Spring Security, [OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) — issuer/JWK discovery, signature/issuer/time validation, audience configuration and authority mapping.
- FastAPI, [OAuth2 with Bearer JWT](https://fastapi.tiangolo.com/tutorial/security/oauth2-jwt/) — dependency-based bearer extraction, maintained JWT verification, expiry handling and `401` challenges.
- IETF, [RFC 8725: JWT Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725.html) — algorithm allow-lists, complete cryptographic validation, issuer/key binding, audience checks and untrusted-claim handling.
- PostgreSQL, [Row Security Policies](https://www.postgresql.org/docs/current/ddl-rowsecurity.html), [`CREATE POLICY`](https://www.postgresql.org/docs/current/sql-createpolicy.html) and [`SET`](https://www.postgresql.org/docs/current/sql-set.html) — policy behavior, owner/`BYPASSRLS` exceptions, `USING`/`WITH CHECK`, and transaction-local versus session settings.
- Model Context Protocol, [Authorization](https://modelcontextprotocol.io/specification/latest/basic/authorization) and [TypeScript SDK authorization](https://ts.sdk.modelcontextprotocol.io/v2/serving/authorization) — MCP servers as resource servers, resource-specific bearer validation, scope challenges and verified auth context in handlers.
- IETF, [RFC 8693: OAuth 2.0 Token Exchange](https://www.rfc-editor.org/rfc/rfc8693.html) — explicit delegation versus impersonation and subject/actor representation.

### Source provenance and continuation

The selected editable bundle member's complete Section 19 is source lines 1119–1153, bounded before Section 20 at line 1154. The compared standalone HTML is lines 1233–1257, bounded before Section 20 at line 1258; the bundle's generated HTML is byte-identical to the standalone file. Both sources require issuer/audience/signature/time verification, immutable identity, membership-derived repository scope, pool-safe row-security context and post-model tool authorization. The editable Markdown remains the structural source and no duplicate guide was created. This canonical section adds exact failure semantics, four worked traces, five diagrams, constrained delegation, reproducible primary references and paired executable contracts. Section 20, **Paired coding exercise: evidence token budgeting**, begins at editable line 1154 / standalone HTML line 1258. Sections 20–25 remain queued; the wider collection is not complete.

## 20. Paired coding exercise: evidence token budgeting

Token budgeting is a preflight safety contract, not a synonym for `topK` and not permission to truncate governing instructions. The packer below receives **already authorized, already ranked** evidence whose counts were produced with the exact tokenizer and serialization rules of the target model revision. It greedily preserves rank order, skips a passage that does not fit, and may accept a smaller later passage. It neither tokenizes nor solves a knapsack problem.

### Requirements and invariants

1. **Authorize before counting or ranking.** Tenant, ACL, generation and document filters apply before any passage enters this function. Packing must never broaden scope or turn an unauthorized near-match into a fallback.
2. **Pin the counting identity.** Record the model/tokenizer revision, template revision, special-token behavior and tool-schema revision with the count. A count from a different tokenizer or template is only an estimate.
3. **Reserve every non-evidence region.** `evidence = context − output − instructions − history − safety − serialization`. Every term is nonnegative; checked arithmetic rejects overflow and total reservations greater than the context window.
4. **Treat rank order as immutable input.** The packer does not rerank. Equal-score tie order must already be deterministic, normally score descending then stable evidence ID.
5. **Require stable, unique IDs and positive counts.** Duplicate IDs make citations ambiguous; zero or negative counts invalidate the accounting. The function returns a new immutable selection and does not mutate caller input.
6. **Define skip semantics explicitly.** A passage larger than the remaining budget is skipped, not truncated. A smaller later passage may fit. This is deterministic greedy packing, not prefix-only selection or utility maximization.
7. **Make dependency units atomic.** If a child chunk is unsafe without its heading, definition or preceding paragraph, rank and count the complete bundle as one unit. Do not let the packer manufacture context-free fragments.
8. **Count the serialized prompt before send.** Source labels, citation delimiters, chat roles, special tokens, JSON schemas and tool definitions consume input tokens too. Recount the exact rendered request; provider-reported usage is useful after a call, not a preflight proof.
9. **Bound recovery.** If the exact serialization exceeds the input allowance, remove the lowest-ranked selected unit, render once more, and stop after a fixed number of attempts. Never drop system instructions, the user question or authorization constraints to make evidence fit.
10. **Observe without leaking content.** Record revision IDs, reservations, selected evidence IDs, estimated versus actual input counts, retry count and rejection reason. Avoid prompt/evidence bodies in ordinary logs and metrics.

```mermaid
flowchart TD
    A["authorized candidates"] --> R["deterministic ranking"]
    R --> C["target-tokenizer counts"]
    C --> P["rank-preserving greedy pack"]
    P --> S["serialize exact request"]
    S --> F{"input + output <= context?"}
    F -->|yes| M["send immutable request"]
    F -->|no| B["drop lowest selected bundle or reject"]
    B --> S
```

### Example 1 — derive one auditable evidence allowance

For a 4,096-token context, reserve 900 output tokens, 300 instruction tokens, 500 history tokens and a 196-token safety margin. With no separately measured serialization overhead in this first arithmetic trace:

| Region | Tokens | Running remainder |
|---|---:|---:|
| context window | 4,096 | 4,096 |
| reserved output | 900 | 3,196 |
| instructions | 300 | 2,896 |
| history | 500 | 2,396 |
| safety margin | 196 | **2,200** |

The evidence budget is therefore `4096 − 900 − 300 − 500 − 196 = 2200`. If template labels and separators are measured separately at 120 tokens, evidence falls to 2,080. The subtraction must fail closed when any input is negative, the sum exceeds 4,096 or fixed-width arithmetic overflows:

```java
static long evidenceBudget(long context, long... reservations) {
    if (context < 0) throw new IllegalArgumentException("negative context");
    long remaining = context;
    for (long reservation : reservations) {
        if (reservation < 0) throw new IllegalArgumentException("negative reservation");
        remaining = Math.subtractExact(remaining, reservation);
        if (remaining < 0) throw new IllegalArgumentException("reservations exceed context");
    }
    return remaining;
}
```

```mermaid
flowchart LR
    W["4096 context"] --> O["−900 output"]
    O --> I["−300 instructions"]
    I --> H["−500 history"]
    H --> G["−196 safety"]
    G --> E["2200 evidence"]
```

### Example 2 — skip an oversized middle passage, preserve ranking

Ranked passages `a=1400`, `b=1100`, `c=700` enter with a 2,200-token evidence allowance. The algorithm accepts `a`, skips `b` because only 800 remain, then accepts `c`. Selection is `[a, c]`, totaling 2,100 with 100 unused.

| Step | Candidate | Remaining before | Decision | Remaining after | Selected IDs |
|---:|---|---:|---|---:|---|
| 1 | `a` / 1,400 | 2,200 | accept | 800 | `a` |
| 2 | `b` / 1,100 | 800 | skip whole passage | 800 | `a` |
| 3 | `c` / 700 | 800 | accept | 100 | `a,c` |

```python
from dataclasses import dataclass

@dataclass(frozen=True)
class Passage:
    id: str
    tokens: int

def pack(passages: tuple[Passage, ...], budget: int) -> tuple[Passage, ...]:
    if budget < 0:
        raise ValueError("negative budget")
    if any(not p.id.strip() or p.tokens <= 0 for p in passages):
        raise ValueError("invalid passage")
    if len({p.id for p in passages}) != len(passages):
        raise ValueError("duplicate passage ID")
    selected: list[Passage] = []
    remaining = budget
    for passage in passages:                 # caller's rank order
        if passage.tokens <= remaining:
            selected.append(passage)
            remaining -= passage.tokens
    return tuple(selected)
```

This choice is intentionally not optimal for every objective. With a budget of 100 and sizes `70, 40, 30`, greedy rank preservation selects the first and third. If business utility says two lower-ranked passages are better, define and test that objective separately rather than quietly changing this contract.

```mermaid
stateDiagram-v2
    [*] --> Remaining2200
    Remaining2200 --> Remaining800: accept a/1400
    Remaining800 --> Remaining800: skip b/1100
    Remaining800 --> Remaining100: accept c/700
    Remaining100 --> [*]: return a,c
```

### Example 3 — preserve required context by packing bundles

Suppose chunk `policy-7b` contains an exception but depends on definition `policy-7a`. Counting and ranking them independently could select only the exception. Instead, ingestion creates immutable bundle `policy-7=[7a,7b]`, counts the exact serialized bundle including its internal separator, and ranks that unit. The packer either accepts both or neither.

| Candidate unit | Members | Count | Independent? | Packing rule |
|---|---|---:|---|---|
| `policy-7` | `7a,7b` | 620 | no | atomic bundle |
| `faq-2` | `2a` | 280 | yes | single-member bundle |
| `policy-8` | `8a,8b,8c` | 910 | no | atomic bundle |

At budget 900, `policy-7` is accepted, `faq-2` is accepted exactly, and `policy-8` is skipped. Citations may still name member IDs, but the retrieval unit and its count are versioned together. If bundles overlap, deduplicate before ranking; repeated text consumes tokens even when IDs differ.

```mermaid
flowchart TD
    D["document structure"] --> U1["bundle policy-7: 7a + 7b"]
    D --> U2["bundle faq-2: 2a"]
    D --> U3["bundle policy-8: 8a + 8b + 8c"]
    U1 --> P["rank and pack atomic units"]
    U2 --> P
    U3 --> P
    P --> C["citations retain member provenance"]
```

### Example 4 — exact serialization is the final gate

The estimator selected evidence totaling 2,100, but the rendered input includes 300 instruction tokens, 500 history tokens, 2,100 evidence tokens, 170 source labels/separators and 180 tool-schema tokens: 3,250 input tokens. Adding the reserved 900 output tokens yields 4,150, so the request does **not** fit a 4,096 context even though the evidence-only pack passed.

The adapter drops the lowest-ranked selected bundle `c`, renders again and obtains 2,538 actual input tokens. `2,538 + 900 = 3,438`, so it sends. If the tokenizer/model revision at send time differs from the pinned counting identity, or the bounded retry cannot produce a fitting request, it rejects locally with an observable budget error rather than relying on provider truncation.

```text
attempt=0 selected=[a,c] evidence=2100 actual_input=3250 total_with_output=4150 -> OVER
attempt=1 selected=[a]   evidence=1400 actual_input=2538 total_with_output=3438 -> SEND
```

Do not use response usage metadata to claim the preflight count was exact: that metadata arrives after the provider processes a request and may use provider-specific accounting. It is valuable for drift alarms: compare estimated input with returned prompt usage by model/template revision, without logging prompt text.

```mermaid
sequenceDiagram
    participant P as Packer
    participant S as Serializer
    participant T as Target tokenizer
    participant M as Model adapter
    P->>S: selected a,c
    S->>T: exact roles + labels + tools + evidence
    T-->>S: 3250 input; over with output
    S->>P: remove lowest selected c
    P->>S: selected a
    S->>T: exact request
    T-->>M: 2538 input; send
```

### Paired executable evidence-budget contract

[`EvidenceBudgetContracts.java`](EvidenceBudgetContracts.java) and [`evidence_budget_contracts.py`](evidence_budget_contracts.py) implement the same dependency-free decision boundary. They exercise checked reservation arithmetic, exact fit, zero budget, rank-preserving skip behavior, immutable inputs, positive counts, unique IDs, blank-ID rejection, serialized-prompt gating and overflow handling:

```bash
java -ea study-guides/backend/spring-ai-python/EvidenceBudgetContracts.java
python3 study-guides/backend/spring-ai-python/evidence_budget_contracts.py
```

Both print the same first line:

```text
PARITY|BUDGET=CHECKED|ORDER=RANKED|SKIP=OVERSIZE|COUNTS=POSITIVE|IDS=UNIQUE|SERIALIZED=RECOUNTED|OVERFLOW=REJECTED|SELECTED=a,c
```

Each implementation passes 32 explicit scenario groups and 10,000 seeded packing trials. These pure contracts do not load a production tokenizer, render Spring AI messages, start FastAPI, call a provider, prove a model's context limit, measure quality or validate bundle construction. Those claims require the pinned tokenizer/model/template artifacts and integration/evaluation fixtures.

### Current primary references

Verified on 28 October 2026:

- Hugging Face Transformers, [Tokenizer API](https://huggingface.co/docs/transformers/main_classes/tokenizer) and [padding/truncation](https://huggingface.co/docs/transformers/main/pad_truncation) — target-tokenizer encoding, special-token behavior, maximum length and explicit truncation strategies.
- Spring AI, [AI metadata](https://docs.spring.io/spring-ai/reference/api/aimetadata.html), [Chat Client API](https://docs.spring.io/spring-ai/reference/api/chatclient.html) and [observability](https://docs.spring.io/spring-ai/reference/observability/) — provider-returned usage metadata, response access and token-usage metrics; these are post-call evidence, not an exact preflight tokenizer.
- Oracle Java SE 21, [`Math`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html) — `addExact`/`subtractExact` overflow behavior used by the Java contract.
- Python, [`dataclasses`](https://docs.python.org/3/library/dataclasses.html) — frozen value objects used to keep reservation and passage inputs read-only by convention.

### Source provenance and continuation

The selected editable bundle member's complete Section 20 is source lines 1154–1244, bounded before Section 21 at line 1245. The compared standalone HTML is lines 1258–1341, bounded before Section 21 at line 1342; the bundle HTML member is byte-identical to that standalone file. Both sources define the same already-authorized/already-ranked greedy packer, `a,c` trace, validation rules and serialized-prompt warning. The editable Markdown remains the structural source and no duplicate guide was created. This canonical section adds checked multi-region arithmetic, target-tokenizer identity, atomic dependency bundles, bounded exact-serialization recovery, four worked traces, five diagrams, current primary references and paired 32-scenario/10,000-trial contracts. Section 21, **Interview questions with worked answers: foundations and APIs**, begins at editable line 1245 / standalone HTML line 1342. Sections 21–25 remain queued; the wider collection is not complete.

## 21. Interview questions with worked answers: foundations and APIs

A strong foundation answer separates the **business contract**, the **mechanism selected to satisfy it**, and the **evidence proving the mechanism works**. “Use a vector database,” “use Python,” “set temperature to zero” and “deserialize the JSON” each skip at least one of those layers. The six source questions collapse into four reusable design decisions below.

### Answer discipline and production requirements

1. **Name the required behavior first.** Exact identifiers, semantic paraphrases, authorization scope, latency, structured-output rules and retry semantics are requirements; products and frameworks are implementation choices.
2. **Keep retrieval broader than vector search.** RAG means selecting external evidence for generation. Lexical, relational, graph, vector and hybrid retrieval can all participate; evaluate the chosen pipeline on representative queries and permission slices.
3. **Place code with its operational owner.** Prefer the runtime that already owns identity, transactions, observability and deployment unless a measured library or execution requirement justifies another service. A split introduces schema, network, authentication, rollout and incident boundaries.
4. **Admit providers by capabilities, not interface shape.** A common `ChatModel`-style API does not prove equal schema support, tool semantics, streaming, cancellation, token accounting, context limits or residency. Resolve a model revision and test the required capability set before traffic.
5. **Treat low temperature as a tuning choice, not a proof.** Retrieval order, model/adapter revisions, tool data and serving implementations can still change. Record a complete replay identity and test invariants; use ordinary deterministic code for exact policy and arithmetic.
6. **Return typed outcomes.** `ANSWER`, `NO_ANSWER`, `UPSTREAM_FAILURE`, `INVALID_OUTPUT` and `FORBIDDEN` have different retry, alerting and client behavior. An empty string cannot preserve those distinctions.
7. **Validate in layers.** Parse, schema, domain, evidence and authorization checks are separate gates. Schema-conforming output can still be financially invalid, unsupported or forbidden.
8. **Bound repair.** A schema-repair retry may correct syntax or shape. It must not override a failed domain or authorization decision, and every retry consumes latency and tokens.

```mermaid
flowchart TD
    Q["business question + caller scope"] --> R["choose retrieval mechanism"]
    R --> E["authorized evidence"]
    E --> M["capability-admitted model route"]
    M --> V["parse → schema → domain → evidence → authorization"]
    V --> O["typed API outcome"]
```

### Example 1 — exact incident code plus semantic symptom

The request is: “Why did `PAY-409` happen, and could it explain customers charged twice?” The literal code is an exact token; the symptom is a paraphrase. A pure vector query can miss or demote the exact identifier, while a pure lexical query can miss documents that describe duplicate capture without the phrase “charged twice.” Use both channels under the same tenant/ACL/generation filter, fuse stable document IDs deterministically, then evaluate recall and citation support.

| Channel | Query | Authorized candidates | Why it exists |
|---|---|---|---|
| lexical | `PAY-409` | `runbook-17`, `incident-88` | exact identifier precision |
| semantic | “customers charged twice” | `incident-88`, `postmortem-12` | paraphrase recall |
| fused | stable rank fusion | `incident-88`, `runbook-17`, `postmortem-12` | retain both signals |

The important trace is scope before ranking:

```text
scope={tenant:t7, acl_epoch:42, generation:g19}
lexical=[runbook-17, incident-88]
semantic=[incident-88, postmortem-12]
deduplicate+fuse=[incident-88, runbook-17, postmortem-12]
fetch+reauthorize=3 visible documents
answer citations=[incident-88, runbook-17]
```

If `postmortem-12` belongs to another tenant, it must never appear in either candidate list, logs or model context. Post-filtering after an unscoped approximate search is not equivalent: it can leak metadata and reduce usable recall.

```mermaid
flowchart LR
    Q["PAY-409 + duplicate charge"] --> L["scoped lexical"]
    Q --> S["scoped semantic"]
    L --> F["stable ID fusion"]
    S --> F
    F --> A["fetch + reauthorize"]
```

**Interview follow-up:** a vector store is one possible index. RAG is the larger evidence-selection-and-generation pattern. PostgreSQL full-text search, SQL predicates or a graph traversal may be the correct retriever for a particular query class.

### Example 2 — one deployable Java service before a Python split

An existing payment-support API already owns Spring Security identity, JDBC transactions, Micrometer telemetry and the release pipeline. Its only new requirement is remote chat plus retrieval. Start with Spring AI inside that service: the business boundary stays local and one team owns the failure path.

Split out a Python service only when a concrete requirement demands it—for example, a GPU-local reranker or document parser whose maintained implementation is Python-first—and measure the new boundary:

| Decision evidence | Keep in Java service | Add a Python service |
|---|---|---|
| identity and transaction owner | already local | must be propagated and rechecked |
| specialized runtime | ordinary remote model/RAG | GPU-local Python-only component |
| latency | one in-process orchestration path | network hop + queue + serialization |
| deployment | one compatibility tuple | two releases and an API schema |
| fallback | local typed degradation | deadline/circuit policy across services |

```mermaid
flowchart TD
    R["measured requirement"] --> D{"requires separate runtime?"}
    D -->|no| J["Java owns API + AI orchestration"]
    D -->|yes| B["versioned authenticated boundary"]
    B --> P["Python owns specialized execution"]
    P --> J
```

The split is justified by capability and ownership, not by a belief that “AI must be Python.” Preserve a provider-neutral business DTO at the Java API. Do not expose a provider SDK response as the service contract; that turns every provider change into a client migration.

### Example 3 — a common model API still needs capability admission

Suppose route `portable` supports tools, JSON Schema and usage metadata; route `streaming` supports tools, streaming, cancellation and usage; route `complete` supports all five. A request requiring **JSON Schema plus cancellation** may only use `complete`, even though all three implement the same high-level chat interface.

| Route | tools | JSON Schema | streaming | cancellation | usage | Admitted? |
|---|:---:|:---:|:---:|:---:|:---:|---|
| `portable` | ✓ | ✓ | — | — | ✓ | no: cancellation missing |
| `streaming` | ✓ | — | ✓ | ✓ | ✓ | no: schema missing |
| `complete` | ✓ | ✓ | ✓ | ✓ | ✓ | yes |

```java
static Provider admit(List<Provider> ordered, Set<Capability> required) {
    return ordered.stream()
            .filter(p -> p.capabilities().containsAll(required))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no capable route"));
}
```

Resolve and record `provider`, model/deployment revision, adapter revision, prompt/template revision, tool/schema revision, retrieval generation and evidence IDs. Temperature zero may reduce sampling variability, but it does not freeze those dependencies. For exact totals, eligibility and authorization, the model should supply proposals or explanations while deterministic code remains authoritative.

```mermaid
sequenceDiagram
    participant H as Host
    participant C as Capability registry
    participant M as Model route
    H->>C: require schema + cancellation
    C-->>H: complete / resolved revision
    H->>M: prompt + immutable contract
    M-->>H: candidate + route metadata
    H->>H: validate invariants, not prose bytes
```

Golden tests should include unsupported schema keywords, malformed tool arguments, cancellation, missing usage, empty context and tool-call replay. Portability is the passing intersection of those tests—not the fact that dependency injection compiled.

### Example 4 — no answer, outage and invalid output are different states

Use a discriminated response envelope. This example keeps transport success separate from domain success:

```json
{"status":"NO_ANSWER","answer":null,"evidenceIds":[],"retryable":false}
```

```json
{"status":"UPSTREAM_FAILURE","answer":null,"evidenceIds":[],"retryable":true}
```

`NO_ANSWER` means authorized retrieval completed but evidence was insufficient. `UPSTREAM_FAILURE` means the dependency could not complete under the route's retry/deadline policy. A client may retry the latter according to server guidance; repeating the former unchanged normally wastes capacity.

For a candidate answer, apply the gates in order:

| Gate | Example failure | Outcome | Repair allowed? |
|---|---|---|---|
| provider | deadline before completion | `UPSTREAM_FAILURE` | route policy may retry |
| evidence | no supported passage | `NO_ANSWER` | not a format repair |
| parse | prose around malformed JSON | `INVALID_OUTPUT` | bounded schema repair |
| schema | missing required `status` | `INVALID_OUTPUT` | bounded schema repair |
| domain | `refundAmount=-5` | `INVALID_OUTPUT` | deterministic rejection |
| evidence | cited ID absent from authorized context | `INVALID_OUTPUT` | deterministic rejection |
| authorization | refund account outside caller scope | `FORBIDDEN` | never model-repaired |

```python
def classify(c: AnswerCandidate) -> Outcome:
    if not c.provider_available:
        return Outcome.UPSTREAM_FAILURE
    if not c.evidence_sufficient:
        return Outcome.NO_ANSWER
    if not (c.parsed and c.schema_valid and c.domain_valid):
        return Outcome.INVALID_OUTPUT
    if not c.authorized:
        return Outcome.FORBIDDEN
    return Outcome.ANSWER
```

```mermaid
stateDiagram-v2
    [*] --> Provider
    Provider --> UPSTREAM_FAILURE: unavailable/deadline
    Provider --> Evidence: available
    Evidence --> NO_ANSWER: insufficient
    Evidence --> Validate: sufficient
    Validate --> INVALID_OUTPUT: parse/schema/domain/evidence fail
    Validate --> FORBIDDEN: authorization fail
    Validate --> ANSWER: every gate passes
```

Spring AI schema validation and Pydantic strict models can enforce shape, but neither proves repository truth or authorization. Re-fetch authoritative records and recheck policy before any side effect. A repair loop receives only repairable validation feedback, has a small attempt/deadline budget and reports cumulative cost.

### Paired executable foundations/API contract

[`FoundationsApiContracts.java`](FoundationsApiContracts.java) and [`foundations_api_contracts.py`](foundations_api_contracts.py) implement the same dependency-free boundary. They cover exact/semantic/hybrid retrieval selection, immutable capability sets, deterministic first-capable admission, typed outcomes, invalid provider IDs and unavailable capability combinations:

```bash
java -ea study-guides/backend/spring-ai-python/FoundationsApiContracts.java
python3 study-guides/backend/spring-ai-python/foundations_api_contracts.py
```

Both print the same first line:

```text
PARITY|RETRIEVAL=LEXICAL,SEMANTIC,HYBRID|CAPABILITIES=ADMITTED|OUTCOMES=ANSWER,NO_ANSWER,UPSTREAM_FAILURE,INVALID_OUTPUT,FORBIDDEN|ORDER=DETERMINISTIC
```

Each implementation passes 24 explicit scenario groups, 10,000 seeded outcome truth-table trials and 10,000 seeded capability-subset trials. These pure contracts do not run a vector index, Spring AI, FastAPI, Pydantic, a model provider or a policy service; those require integration fixtures and pinned model/index/schema/policy revisions.

### Current primary references

Verified on 29 October 2026:

- Spring AI, [Retrieval Augmented Generation](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html) — modular retrieval, vector-store filters, document joining/post-processing and empty-context behavior.
- PostgreSQL 18, [Full Text Search](https://www.postgresql.org/docs/current/textsearch.html) — lexical parsing, matching, ranking and index choices; evidence that retrieval need not be vector-only.
- Spring AI, [Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html) and [Chat Client API](https://docs.spring.io/spring-ai/reference/api/chatclient.html) — portable and provider-specific options, response metadata, synchronous/streaming behavior and advisor ordering.
- Spring AI, [Structured Output Converters](https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html) and [schema validation/self-correction](https://docs.spring.io/spring-ai/reference/api/structured-output/validation.html) — best-effort conversion, JSON Schema validation, bounded retries, streaming limitation and cumulative usage.
- Pydantic 2.14, [Validation](https://pydantic.dev/docs/validation/latest/get-started/) — strict versus coercing validation and model-derived JSON Schema.
- FastAPI, [Handling Errors](https://fastapi.tiangolo.com/tutorial/handling-errors/) and [direct responses](https://fastapi.tiangolo.com/advanced/response-directly/) — explicit error responses and the fact that directly returned responses bypass automatic response-model validation.

### Source provenance and continuation

The selected editable bundle member's complete Section 21 is source lines 1245–1272, bounded before Section 22 at line 1273. The standalone HTML begins the same section at line 1342, and the bundle's generated HTML is byte-identical to that standalone file. Both sources contain the same six foundation/API interview questions on retrieval versus vector databases, Java/Python placement, abstraction limits, nondeterminism, no-answer versus outage and layered structured-output validation. The editable Markdown remains the structural source and no duplicate guide was created. This canonical section preserves those answers while adding a production answer rubric, four worked examples, five diagrams, typed outcome/capability contracts, current primary references and paired 24-scenario/20,000-trial executables. Section 22, **Interview questions with worked answers: concurrency, data and tools**, begins at editable line 1273. Sections 22–25 remain queued; the wider collection is not complete.

## 22. Concurrency, data migrations and tool safety

The source questions look separate—an async endpoint freezes, virtual threads meet a small database pool, 429 retries multiply, embeddings change, ANN filtering leaks tenants, MCP is confused with an agent, a tool loop will not stop and a refund may be retried—but they share one production rule:

> Make the scarce resource, irreversible boundary and identity explicit **before** scheduling work. Then admit, retry, publish and reconcile against bounded state rather than against hope.

An interview-quality answer should name the invariant, trace one concrete request, show the overload or ambiguity case and identify the metric that would prove the fix. “Make it async,” “use virtual threads,” “retry with backoff,” “swap the embedding model” and “add an idempotency key” are implementation hints, not complete safety arguments.

### Example 1 — dispatch work without hiding the real bottleneck

**Requirement.** A Python endpoint and a Java endpoint both call a database and an external model provider. The service must remain responsive under load, reject work before unbounded queueing and preserve one end-to-end deadline.

Classify work before choosing an execution primitive:

| Work | Python placement | Java placement | Capacity that still must be bounded |
|---|---|---|---|
| native non-blocking I/O | event loop task | async client or request thread | sockets, provider quota, deadline |
| legacy blocking I/O | bounded `to_thread`/executor lane | virtual thread on Java 21+ | executor admission, DB pool, provider concurrency |
| CPU-heavy parse/rerank | process/worker pool | bounded CPU executor | CPU slots and queue |

`asyncio.to_thread()` keeps blocking I/O off the event-loop thread; it does not turn an arbitrary CPU-bound Python function into scalable parallel work. A Java virtual thread makes a blocked request cheaper than dedicating a platform thread, but it does not create a new database connection or provider permit. Acquire the dependency permit and a bounded queue slot **before** offload. Otherwise millions of cheap waiters can still overwhelm a 40-connection pool.

```mermaid
flowchart TD
    A[Request with deadline] --> B{Dependency permit and queue slot?}
    B -->|no| C[Reject or shed]
    B -->|yes| D{Work class}
    D -->|native async I/O| E[Event loop]
    D -->|blocking I/O| F[Bounded thread or virtual thread]
    D -->|CPU| G[Process or CPU worker]
```

**Capacity trace.** Suppose four replicas each allow 30 concurrent DB calls while the database pool supports only 80 connections. The fleet can demand `4 × 30 = 120`, so local settings are unsafe even though every replica appears bounded. A safer initial cap is at most `floor((80 - reserve) / 4)` per replica, and a rollout must recompute it as replica count changes. Provider concurrency and database connections need separate semaphores because holding one while waiting indefinitely for the other creates convoying.

```python
async def handle(request):
    remaining = request.deadline - monotonic()
    if remaining <= 0 or not dependency_slots.try_acquire():
        raise Overloaded(retry_after_seconds=1)
    try:
        # blocking_lookup is I/O-bound and the offload lane is separately bounded.
        return await asyncio.wait_for(
            asyncio.to_thread(blocking_lookup, request.key),
            timeout=remaining,
        )
    finally:
        dependency_slots.release()
```

Measure event-loop lag, executor queue age, rejected admission, in-use/waiting DB connections, provider concurrency and deadline exhaustion. A flat event-loop-lag graph with a rising DB wait queue proves only that the freeze moved; it does not prove the service is healthy.

**Common failure.** Offloading first and taking the semaphore inside the worker lets the offload queue become the hidden unbounded buffer. Admission belongs before scheduling.

### Example 2 — one retry owner, one deadline and no replay of ambiguous writes

**Requirement.** A provider returns HTTP 429 with `Retry-After`. The service may retry safe reads, but must not amplify overload or repeat a write whose outcome is unknown.

If client, gateway and service each make three attempts, a single user action can create `3 × 3 × 3 = 27` provider calls. Select one retry owner. Every other layer performs one attempt and returns typed evidence such as `rate_limited`, `retry_after`, `attempts_used` and `deadline_remaining`.

```mermaid
sequenceDiagram
    participant C as Client
    participant S as Retry owner
    participant P as Provider
    C->>S: request + deadline
    S->>P: attempt 1
    P-->>S: 429 + Retry-After
    S->>S: cap + jitter + deadline check
    alt delay fits and replay is safe
        S->>P: attempt 2
        P-->>S: result
        S-->>C: result + attempts=2
    else unsafe or deadline exhausted
        S-->>C: typed terminal outcome
    end
```

The retry decision is ordered:

1. Is this layer the retry owner?
2. Is replay safe—read-only or protected by a stable operation identity?
3. Is the attempt budget still available?
4. Does server guidance plus capped jitter fit inside the remaining deadline?
5. If all gates pass, retry; otherwise return a terminal typed outcome.

```java
RetryDecision decide(RetryInput r) {
    if (!r.owner()) return NOT_OWNER;
    if (!r.replaySafe()) return NON_REPLAYABLE;
    if (r.attempt() >= r.maxAttempts()) return EXHAUSTED;
    long delay = cappedJitter(r.retryAfterMillis(), r.backoffMillis());
    if (delay < 0 || r.nowMillis() > r.deadlineMillis() - delay) return DEADLINE;
    return RETRY;
}
```

The subtraction form avoids overflow from `now + delay`. Parse both allowed `Retry-After` forms, cap unreasonable values, use a monotonic clock for elapsed budgets and record the provider request ID. A connection timeout after sending a write is **ambiguous**, not automatically failed: look up by the original idempotency key or reconcile provider state. Never mint a new key merely to obtain another attempt.

**Validation trace.** With `now=850 ms`, `deadline=1,000 ms`, `Retry-After=200 ms`, retry is rejected because it cannot start before the deadline. With the same times and a capped delay of `100 ms`, a safe read can retry. A refund without durable idempotency state cannot.

### Example 3 — migrate embedding identity and filter tenants before ANN

**Requirement.** Replace embedding model `m1` with `m2` without mixing vector spaces, losing rollback or returning another tenant's documents.

Treat an embedding index as a generation, not as a mutable bag of vectors. Its identity includes at least model/version, dimensions, preprocessing/chunking revision, distance function and source revision. Query vectors and document vectors must share that identity.

```mermaid
flowchart TD
    A[Immutable source revision] --> B[Build generation g2 with m2 identity]
    B --> C[Reconcile count and failures]
    C --> D[Shadow queries and quality gates]
    D -->|pass| E[Atomically publish pointer to g2]
    D -->|fail| F[Keep g1 published]
    E --> G[Observe and retain g1 for rollback]
```

A safe migration is:

1. create a new generation with immutable identity;
2. backfill immutable source revisions and checkpoint failures;
3. reconcile expected counts, duplicate/missing IDs and authorization metadata;
4. compare fixed queries against an exact or approved baseline, including per-tenant recall;
5. atomically switch a publication pointer only after gates pass;
6. keep the previous generation readable until the observation window closes.

Tenant scope is a retrieval predicate, not a cosmetic post-processing step. Prefer tenant partitions, separate tables or an index/search path whose candidate set is scoped before approximate search. Then reauthorize every fetched document against the request's current membership because index metadata can be stale.

```mermaid
flowchart LR
    A[Authenticated tenant] --> B[Tenant partition or prefilter]
    B --> C[ANN candidate search]
    C --> D[Fetch authoritative rows]
    D --> E[Reauthorize every row]
    E --> F[Prompt evidence]
```

**Recall and leak trace.** A global ANN search returns 10 neighbors, only one from tenant A; post-filtering yields one result even though five strong tenant-A neighbors exist just outside the global top 10. Increasing the global limit may improve recall but still exposes cross-tenant candidates to application memory and timing. A tenant partition or enforced prefilter searches the correct candidate population; row-level reauthorization then prevents stale membership from becoming evidence.

```sql
-- tenant_id is derived from authenticated membership, never model or request text.
SELECT id, body
FROM tenant_chunks
WHERE tenant_id = :trusted_tenant
ORDER BY embedding <=> :query_embedding
LIMIT :k;
```

**Publication invariant.** A route is ready only when the generation is published, query/model preprocessing identities match, tenant filtering occurs before ANN and every fetched row passes current scope checks. An evaluation score alone cannot satisfy any of those gates.

### Example 4 — separate MCP transport from agent control and reconcile tools

**Requirement.** An agent uses an MCP-exposed refund tool. It must terminate, avoid duplicate refunds and recover from an unknown provider response.

MCP defines a protocol boundary through which a client can discover and invoke capabilities. An agent is the control loop that chooses whether, when and how often to call them. The protocol does not supply business authorization, budget ownership, durable idempotency or a stopping policy.

```mermaid
flowchart TD
    A[Agent state and budgets] --> B{Continue?}
    B -->|no| C[Terminal outcome]
    B -->|yes| D[MCP client validates tool schema]
    D --> E[Tool service authorizes and claims operation]
    E --> F[Provider call]
    F --> G[Persist result or unknown outcome]
    G --> A
```

Stop before the next model/tool call when any independent limit is reached: wall-clock deadline, cumulative input/output tokens, total model calls, repeated identical calls, total tool calls or a per-tool cap. Persist counters with the run so a worker restart cannot reset the budget. Treat repeated identical tool requests as a signal to stop or reconcile, not as progress.

A refund identity should bind the trusted scope, operation and canonical payload:

```text
idempotency_key = HMAC(server_secret,
  tenant_id || ":refund:" || payment_id || ":" || canonical_payload_hash)
```

Store the key, canonical payload hash, authorization decision, provider request ID and state transactionally before or with the effect claim. Then apply this state machine:

| Existing record | Payload hash | Provider outcome | Decision |
|---|---|---|---|
| none | current | n/a | claim and execute once |
| yes | same | known | replay cached terminal result |
| yes | different | any | conflict; do not execute |
| yes | same | unknown | reconcile by original key/request ID |

```mermaid
stateDiagram-v2
    [*] --> CLAIMED: new stable key
    CLAIMED --> SUCCEEDED: provider confirms
    CLAIMED --> FAILED: definitive failure
    CLAIMED --> UNKNOWN: timeout after send
    UNKNOWN --> SUCCEEDED: reconciliation finds refund
    UNKNOWN --> FAILED: authoritative lookup proves absent
    SUCCEEDED --> SUCCEEDED: same-key replay
```

The tool service rechecks authorization from trusted identity at execution time; the model cannot confer permission. A changed amount under an existing key is a conflict, not a second refund. An unknown outcome remains non-terminal until authoritative reconciliation proves whether the effect occurred.

### Paired executable concurrency/data/tool contract

[`ConcurrencyDataToolsContracts.java`](ConcurrencyDataToolsContracts.java) and [`concurrency_data_tools_contracts.py`](concurrency_data_tools_contracts.py) implement the same dependency-free decision boundary:

```bash
java -ea study-guides/backend/spring-ai-python/ConcurrencyDataToolsContracts.java
python3 study-guides/backend/spring-ai-python/concurrency_data_tools_contracts.py
```

Both print the same first line:

```text
PARITY|PY_BLOCKING=BOUNDED_THREAD|JAVA_BLOCKING=VIRTUAL_THREAD|DEPENDENCY=GATED|RETRY=DEADLINE_BOUND|MIGRATION=IDENTITY_PINNED|ANN=PREFILTERED|TOOL=RECONCILE|LOOP=BOUNDED
```

Each passes 29 explicit scenario groups plus 10,000 seeded retry-decision trials and 10,000 seeded retrieval-admission trials. The Java source intentionally runs on Java 17 and models the Java 21 virtual-thread dispatch decision without calling Java 21 APIs. These contracts do not open a database, run an ANN index, contact a provider, start Spring AI/FastAPI, speak MCP or issue a refund; those require integration fixtures with controlled failures and authoritative reconciliation.

### Correctness checklist and follow-ups

- Admission is fleet-aware and occurs before offload; no queue is accidentally unbounded.
- Every request has one deadline, propagated through queue, dependency and retry waits.
- Exactly one retry owner respects server guidance, attempt caps, jitter and replay safety.
- Embedding generations pin model and preprocessing identity, publish atomically and retain rollback.
- Tenant scope narrows candidates before ANN and every fetched row is reauthorized.
- MCP schema validation is not confused with authorization, idempotency or agent loop control.
- Tool loops persist independent wall, token, model-call, repeat and per-tool limits.
- Ambiguous side effects are reconciled under the original identity; a new key never masks uncertainty.

Useful follow-up questions are: “Where is admission acquired?”, “What is the fleet-wide limit?”, “Who owns retries?”, “How is an unknown write represented?”, “What exact identity is published with the index?”, “Can an unauthorized vector enter the candidate set?”, “Which counters survive restart?” and “Which authoritative lookup proves the refund result?”

### Current primary references

Verified on 29 October 2026:

- Python, [`asyncio.to_thread()`](https://docs.python.org/3/library/asyncio-task.html#asyncio.to_thread) — offloads blocking I/O from the event-loop thread and documents the usual GIL limitation for CPU-bound work.
- OpenJDK, [JEP 444: Virtual Threads](https://openjdk.org/jeps/444) and Oracle, [Virtual Threads](https://docs.oracle.com/en/java/javase/26/core/virtual-threads.html) — virtual threads improve thread-per-request scalability; they are not faster threads and do not replace scarce dependency limits.
- IETF, [RFC 6585 §4](https://www.rfc-editor.org/rfc/rfc6585.html#section-4) and [RFC 9110 §10.2.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3) — 429 semantics and the `Retry-After` field.
- pgvector, [Filtering, multitenancy and iterative index scans](https://github.com/pgvector/pgvector#filtering) — approximate filtering can reduce results; tenant partitioning/separate tables and iterative scans are documented controls.
- Model Context Protocol, [Architecture](https://modelcontextprotocol.io/docs/learn/architecture) and [Authorization](https://modelcontextprotocol.io/specification/latest/basic/authorization) — protocol roles and authorization boundary; application control policy remains explicit.
- Stripe, [Idempotent requests](https://docs.stripe.com/api/idempotent_requests) — stable keys replay the original result and changed parameters are rejected, illustrating payload-bound operation identity.

### Source provenance and continuation

The selected editable bundle member's complete Section 22 is source lines 1273–1306, bounded before Section 23 at line 1307. The standalone HTML begins Section 22 at line 1357 and Section 23 at line 1375; the bundle's generated HTML is byte-identical to the standalone source. Both sources contain the same eight questions on Python/Java concurrency, retry storms, embedding migration, tenant-safe ANN filtering, MCP versus agent loops and replay-safe refunds. The editable Markdown remains the structural source and no duplicate guide was created. This canonical section consolidates the eight questions into four production examples, five diagrams, paired 29-scenario/20,000-trial contracts and current primary references. Section 23, **Scenario questions**, begins at editable line 1307. Sections 23–25 remain queued; the wider collection is not complete.

## 23. Six coding and debugging interview scenarios

These scenarios—tenant-leaking caches, truncated SSE, Kubernetes-only connection failures, lost questions during packing, duplicate ingestion after a crash and schema-valid access to the wrong account—share one debugging discipline:

> Locate the first boundary where trusted identity, completion, network reachability, budget or durable work identity stops being explicit. Repair that boundary, then prove the repair with an adversarial replay.

Do not start with a framework switch. First capture a minimal trace, name the violated invariant and separate authoritative facts from symptoms. A strong interview answer uses this order:

| Step | Question | Evidence |
|---:|---|---|
| 1 | What was the intended contract? | trusted scope, terminal event, endpoint identity, budget tuple or source revision |
| 2 | Where was it first violated? | cache lookup, event reducer, DNS/TCP/TLS hop, packer or chunk upsert |
| 3 | Can the failure be replayed deterministically? | two-tenant fixture, cut stream, in-Pod probe, exact token fixture or crash checkpoint |
| 4 | What repair closes the class of failures? | scoped key plus reauthorization, explicit terminal state, configured Service, reservation-first packer or stable chunk identity |
| 5 | What regression test would fail on the old code? | cross-scope read, close-before-complete, wrong namespace, oversized-first passage or duplicate redelivery |

### Example 1 — one authority invariant closes both cache leaks and wrong-account tools

**Requirements.** A cached answer may be reused only inside the same trusted tenant, authorization revision, index generation and evidence visibility set. A tool may act only on an account derived from verified identity or proven membership; a model-supplied account ID is untrusted even when its JSON type is valid.

The cache identity should bind the facts that make the answer reusable:

```text
cache_key = HMAC(server_secret,
  tenant_id || authz_revision || index_generation || normalized_question_hash)
```

This key prevents a direct cross-tenant hit, but it is not sufficient by itself. On reuse, fetch the cited evidence under the caller's **current** scope. An ACL revocation after cache creation turns the entry into a miss; it does not grant historical access. Do not place raw user text or bearer tokens in the key or logs.

```mermaid
flowchart TD
    A[Verified subject] --> B[Directory-owned tenant and account scope]
    B --> C[Scoped cache identity]
    C --> D{Revision and generation match?}
    D -->|no| E[Miss and recompute]
    D -->|yes| F[Reauthorize cited evidence]
    F -->|visible| G[Return cached answer]
    F -->|revoked| E
```

**Two-tenant trace.** Tenant A and tenant B both ask “What is the renewal date?” Tenant A's answer cites private document `A-7`.

| Request | Key scope | Current evidence check | Outcome |
|---|---|---|---|
| A, first request | `A / r4 / g9 / q1` | `A-7` visible | compute and cache |
| B, same question | `B / r2 / g9 / q1` | A's entry is unreachable | scoped miss |
| A after ACL revision `r5` | old entry says `r4` | revision differs | stale miss |
| A with matching revision but revoked `A-7` | key matches | `A-7` denied | revoked miss |

The tool boundary uses the same rule in a different form:

```java
Account account = accounts.findByIdAndMemberId(request.accountId(), identity.subject())
        .orElseThrow(NotFound::new); // conceal a foreign account when policy requires
policy.requireAllowed(identity, account, request.operation());
validateBusinessPayload(request);
return executor.execute(account, request.operation());
```

The repository query is scoped by trusted membership, then business authorization is checked again at execution. A hidden field, prompt, retrieved document or schema-valid tool argument never selects authority. Test direct HTTP calls as well as model-generated calls; prompt filters cannot replace object-level authorization.

### Example 2 — a closed SSE connection is not a completed answer

**Requirement.** The client persists `COMPLETE` only after an application terminal event. Deltas are provisional. A transport close with no `complete` or `failed` event is `INCOMPLETE`, even if text was received.

Use ordered event IDs and explicit event types:

```text
id: 41
event: delta
data: {"text":"partial"}

id: 42
event: complete
data: {"answerId":"ans-9","citationIds":["doc-4"]}
```

```mermaid
stateDiagram-v2
    [*] --> OPEN
    OPEN --> OPEN: ordered delta
    OPEN --> COMPLETE: complete event
    OPEN --> FAILED: failed event
    OPEN --> INCOMPLETE: disconnect or EOF
    INCOMPLETE --> OPEN: resume from trusted cursor
    COMPLETE --> [*]
    FAILED --> [*]
```

**Cut-stream trace.** The server emits IDs 39, 40 and 41, then the proxy closes the connection. The reducer keeps the text for display but stores state `INCOMPLETE` and last contiguous ID `41`. A reconnect may present that cursor. The server either resumes with event 42 or returns a typed result saying replay is unavailable; the client never fabricates `COMPLETE` from EOF.

Browser `EventSource` can reconnect and send `Last-Event-ID`, so handlers must tolerate delivery around reconnects. Deduplicate by stable event ID, reject gaps or move to a resync state, and never replay an external write merely because the answer stream reconnected. An action needs its own stable operation identity.

```mermaid
sequenceDiagram
    participant U as UI reducer
    participant P as Proxy
    participant S as Answer service
    S-->>P: delta 39, 40, 41
    P--xU: connection closes
    U->>U: state=INCOMPLETE, cursor=41
    U->>S: reconnect with cursor 41
    alt replay retained
        S-->>U: complete 42
    else replay unavailable
        S-->>U: explicit resync outcome
    end
```

Test connection close after zero, one and several deltas; close immediately before the terminal event; duplicate the last event; introduce a gap; and send conflicting terminal events. The state machine must never have both success and failure terminals.

### Example 3 — debug Kubernetes networking one layer at a time

**Requirement.** A workload must call the configured model endpoint from its own network environment. `localhost` is valid only when the model process shares the Pod network namespace; it does not refer to the developer laptop or another Pod.

```mermaid
flowchart TD
    A[Resolved endpoint configuration] --> B{localhost?}
    B -->|yes| C{Model in same Pod?}
    C -->|no| D[Wrong network namespace]
    C -->|yes| F[TCP probe]
    B -->|no| E[Service DNS lookup]
    E --> F
    F --> G[TLS and authentication]
    G --> H[Model protocol and readiness]
    H --> I[Application request]
```

**Layered trace.** Local development succeeds at `http://localhost:11434`; the deployed Pod reports connection refused.

| Probe from the application Pod | Result | Conclusion |
|---|---|---|
| configuration dump names `localhost` | unexpected | endpoint identity is already wrong unless the model is a sidecar |
| `model-runtime.ai.svc.cluster.local` resolves | yes | cluster DNS and Service name are valid |
| TCP connect to Service port | yes | routing and listener work; continue upward |
| authenticated health/model request | `401` | network works; fix credentials/audience, not DNS |
| authenticated model request | schema error | transport works; inspect protocol/version mapping |

Use a Kubernetes Service DNS name or an explicitly approved external endpoint. Check NetworkPolicy, egress controls, TLS trust and credentials without exposing a model port publicly as a shortcut. Record the resolved endpoint and deployment identity in release evidence so “works locally” cannot silently select another runtime.

### Example 4 — preserve the question and make ingestion replay converge

This example joins the source's two data-path coding tasks because both require an immutable identity before greedy or retried work begins.

**Packing requirement.** Reserve output, system/safety instructions, history and the user question before evidence. Reject invalid arithmetic. Visit deduplicated evidence in ranked order, skip a passage that does not fit and continue; record selected and skipped IDs.

With a 100-token window, fixed reservations `20`, question `10` and evidence sizes `[80, 20]`, the evidence allowance is `70`. Passage 1 does not fit, passage 2 does, and final use is `20 + 10 + 20 = 50`. The question is never dropped to make room for evidence.

```python
def pack(window, fixed, question, ranked_passages):
    if window <= 0 or fixed < 0 or question.tokens <= 0:
        raise ValueError("invalid token budget")
    used = fixed + question.tokens
    if used > window:
        return "OVER_BUDGET", [], []
    selected, skipped = [], []
    for passage in deduplicate(ranked_passages):
        (selected if passage.tokens <= window - used else skipped).append(passage.id)
        if passage.id in selected:
            used += passage.tokens
    return "READY" if selected else "NO_EVIDENCE", selected, skipped
```

Production code must count the exact target tokenizer after final serialization; the simplified contract uses supplied positive token counts so both languages can prove the decision boundary without a tokenizer dependency.

**Ingestion requirement.** A redelivered job must converge to the same visible generation as one clean attempt. Derive stable chunk identity from trusted scope and immutable source revision, then use an idempotent upsert or uniqueness constraint:

```text
chunk_id = H(tenant_id || source_id || source_revision || chunk_ordinal || chunk_content_hash)
```

```mermaid
flowchart TD
    A[Immutable source revision] --> B[Deterministic chunk plan]
    B --> C[Idempotent chunk upserts]
    C --> D{Crash?}
    D -->|yes| E[Redelivery with same identities]
    E --> C
    D -->|no| F[Reconcile expected IDs and failures]
    F -->|complete| G[Atomically publish generation]
    F -->|incomplete| H[Keep previous generation visible]
```

**Crash trace.** A ten-chunk revision crashes after chunks 0–4. Redelivery recomputes the same ten IDs: five writes become no-ops and five upsert missing rows. Reconciliation proves exactly IDs 0–9 exist before publication. Physical attempts differ from a clean run; the published logical result does not. A changed source revision builds a new generation instead of overwriting an already published identity.

Test crashes before the first write, mid-batch, after all writes but before acknowledgement and during publication. Also test a content mismatch under an existing identity; treat it as corruption/conflict, not a successful replay.

### Paired executable debugging-scenarios contract

[`DebuggingScenariosContracts.java`](DebuggingScenariosContracts.java) and [`debugging_scenarios_contracts.py`](debugging_scenarios_contracts.py) implement the same dependency-free decisions:

```bash
java -ea study-guides/backend/spring-ai-python/DebuggingScenariosContracts.java
python3 study-guides/backend/spring-ai-python/debugging_scenarios_contracts.py
```

Both print the same first line:

```text
PARITY|CACHE=SCOPED|STREAM=TERMINAL_EVENT|NETWORK=LAYERED|PACK=QUESTION_RESERVED|INGEST=IDEMPOTENT|TOOL=AUTHORIZED
```

Each passes 31 explicit scenario groups plus 10,000 seeded cache-decision trials and 10,000 seeded ingestion-replay trials. The contracts prove ordering and fail-closed decisions; they do not run Redis, a browser/EventSource client, Kubernetes DNS/NetworkPolicy, a tokenizer, a queue, a vector index, Spring AI, FastAPI or a real account backend. Those need integration fixtures that can cut connections, revoke access and crash workers at controlled boundaries.

### Correctness checklist and interview follow-ups

- Cache identity contains trusted scope and revisions; reuse reauthorizes every cited object.
- Tool scope comes from verified identity or membership, never from model/request text alone.
- Only an explicit application terminal event marks a streamed answer complete.
- Event IDs are contiguous or trigger resynchronization; answer reconnect never replays a write.
- Endpoint identity is inspected before DNS, TCP, TLS/authentication and protocol probes.
- The question and fixed reservations are protected before evidence packing.
- Chunk IDs bind tenant, source, revision, ordinal and content; redelivery converges before publication.
- A changed immutable revision creates a new generation; partial work never becomes visible.

Useful follow-ups are: “Which revision invalidates the cache?”, “What proves the cited object is still visible?”, “What exact event changes state to complete?”, “Where does localhost point inside this Pod?”, “Does an oversized first passage block later evidence?”, “What identity makes redelivery a no-op?” and “Can direct HTTP bypass the model-facing authorization check?”

### Current primary references

Verified on 29 October 2026:

- OWASP, [Insecure Direct Object Reference Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html) — every object access needs server-side authorization; scope lookups to the authenticated user's permitted dataset and do not rely on hard-to-guess identifiers.
- WHATWG HTML, [Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html) — `EventSource` connection states, reconnection, event IDs, `Last-Event-ID` and exact stream parsing; application completion remains an explicit event contract.
- Kubernetes, [Services, Load Balancing, and Networking](https://kubernetes.io/docs/concepts/services-networking/) and [DNS for Services and Pods](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/) — each Pod has its own network namespace, containers in one Pod share `localhost`, and Services receive discoverable DNS names.
- Amazon SQS, [at-least-once delivery](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues-at-least-once-delivery.html) — duplicate delivery can occur and consumers must be idempotent.
- This guide's Section 20, **Evidence token budgeting** — reservation-first arithmetic, exact target-tokenizer recounting, rank-preserving skip packing and dependency bundles.

### Source provenance and continuation

The refreshed selected bundle member's complete Section 23 is source lines 1307–1332, bounded before Section 24 at line 1333. Fresh Pandoc 3.1.3 extraction places the standalone HTML section at lines 1398–1423, ending before Section 24 at line 1424; the bundle's generated HTML remains byte-identical to the standalone source. Both sources contain the same six scenarios on tenant cache leakage, incomplete SSE, Kubernetes endpoint diagnosis, question-preserving context packing, crash-safe ingestion and wrong-account tool authorization. The editable Markdown remains the structural source and no duplicate guide was created. This canonical section preserves all six scenarios while adding four connected production traces, five diagrams, current primary references and paired 31-scenario/20,000-trial contracts. Section 24, **Architecture interview questions**, begins at editable line 1333. Sections 24–25 remain queued; the wider collection is not complete.
