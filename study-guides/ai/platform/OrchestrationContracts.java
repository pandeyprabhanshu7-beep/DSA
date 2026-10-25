import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Dependency-free contracts for bounded RAG + MCP orchestration.
 *
 * Run with: java -ea OrchestrationContracts.java
 */
public final class OrchestrationContracts {
    enum PlanKind { DETERMINISTIC, BOUNDED_AGENT }
    enum Step { CLASSIFY, RETRIEVE, READ_STATUS, ASSEMBLE, GENERATE, VALIDATE, COMPLETE }
    enum Failure { NONE, STATUS_TIMEOUT, AUTHENTICATION, TENANT_SCOPE, MUTATION_UNKNOWN }
    enum Outcome { COMPLETE, DEGRADED, DENIED, UNKNOWN, PAUSED }
    enum OperationState { NOT_STARTED, INTENT_RECORDED, COMMITTED, UNKNOWN }

    record TaskShape(boolean knownSteps, boolean dataDependentBranching,
                     boolean consequentialAction, int candidateTools) {}

    record Budget(int callsLeft, long millisLeft, int costLeft) {
        Budget {
            if (callsLeft < 0 || millisLeft < 0 || costLeft < 0) {
                throw new IllegalArgumentException("negative budget");
            }
        }

        boolean admits(int calls, long millis, int cost) {
            return calls >= 0 && millis >= 0 && cost >= 0
                    && calls <= callsLeft && millis <= millisLeft && cost <= costLeft;
        }

        Budget spend(int calls, long millis, int cost) {
            if (!admits(calls, millis, cost)) throw new IllegalStateException("budget exhausted");
            return new Budget(callsLeft - calls, millisLeft - millis, costLeft - cost);
        }
    }

    record Decision(PlanKind kind, List<Step> fixedPlan, int maximumAgentSteps) {
        Decision {
            fixedPlan = List.copyOf(fixedPlan);
            if (kind == PlanKind.DETERMINISTIC && fixedPlan.isEmpty()) {
                throw new IllegalArgumentException("deterministic plan must be explicit");
            }
            if (kind == PlanKind.BOUNDED_AGENT && maximumAgentSteps <= 0) {
                throw new IllegalArgumentException("agent must be bounded");
            }
        }
    }

    record StageEvent(String runId, String traceId, Step step, String status,
                      String configRevision, List<String> evidenceIds,
                      String operationId, String safeDetail) {
        StageEvent {
            requireText(runId, "runId");
            requireText(traceId, "traceId");
            Objects.requireNonNull(step, "step");
            requireText(status, "status");
            requireText(configRevision, "configRevision");
            evidenceIds = List.copyOf(evidenceIds);
            if (safeDetail.contains("Bearer ") || safeDetail.contains("api_key=")) {
                throw new IllegalArgumentException("secret-like detail must not be logged");
            }
        }
    }

    record RunResult(Outcome outcome, boolean liveStatusKnown, int statusAttempts,
                     List<StageEvent> events, String answerQualifier) {
        RunResult {
            events = List.copyOf(events);
        }
    }

    record SideEffect(String operationId, String fingerprint, OperationState state,
                      String resultId) {
        SideEffect {
            requireText(operationId, "operationId");
            requireText(fingerprint, "fingerprint");
        }

        SideEffect recordIntent() {
            if (state != OperationState.NOT_STARTED) throw new IllegalStateException("intent already recorded");
            return new SideEffect(operationId, fingerprint, OperationState.INTENT_RECORDED, null);
        }

        SideEffect commit(String result) {
            if (state != OperationState.INTENT_RECORDED) throw new IllegalStateException("missing durable intent");
            requireText(result, "result");
            return new SideEffect(operationId, fingerprint, OperationState.COMMITTED, result);
        }

        SideEffect loseResponse() {
            if (state != OperationState.COMMITTED) throw new IllegalStateException("nothing committed");
            return new SideEffect(operationId, fingerprint, OperationState.UNKNOWN, resultId);
        }

        String reconcile(String retryFingerprint) {
            if (!fingerprint.equals(retryFingerprint)) throw new IllegalStateException("operation key conflict");
            if (resultId != null) return resultId;
            throw new IllegalStateException("outcome still unknown");
        }
    }

    private OrchestrationContracts() {}

    static Decision choose(TaskShape task) {
        Objects.requireNonNull(task, "task");
        if (task.candidateTools < 0) throw new IllegalArgumentException("negative tool count");
        if (task.knownSteps && !task.dataDependentBranching) {
            return new Decision(
                    PlanKind.DETERMINISTIC,
                    List.of(Step.CLASSIFY, Step.RETRIEVE, Step.READ_STATUS, Step.ASSEMBLE,
                            Step.GENERATE, Step.VALIDATE, Step.COMPLETE),
                    0);
        }
        int cap = Math.max(1, Math.min(8, task.candidateTools + (task.consequentialAction ? 2 : 1)));
        return new Decision(PlanKind.BOUNDED_AGENT, List.of(), cap);
    }

    static RunResult runKnownReadWorkflow(Failure failure, Budget initial, String runId) {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(initial, "initial");
        requireText(runId, "runId");
        String traceId = "trace-" + runId;
        String revision = "atlas-r18";
        List<StageEvent> events = new ArrayList<>();
        Budget budget = initial;

        budget = budget.spend(1, 10, 1);
        events.add(event(runId, traceId, Step.CLASSIFY, "OK", revision, List.of(), null, "live status requested"));

        if (failure == Failure.AUTHENTICATION || failure == Failure.TENANT_SCOPE) {
            String code = failure == Failure.AUTHENTICATION ? "AUTHENTICATION_DENIED" : "TENANT_SCOPE_DENIED";
            events.add(event(runId, traceId, Step.RETRIEVE, code, revision, List.of(), null, "fail closed"));
            return new RunResult(Outcome.DENIED, false, 0, events, "no answer disclosed");
        }

        budget = budget.spend(1, 20, 1);
        events.add(event(runId, traceId, Step.RETRIEVE, "OK", revision, List.of("runbook:payments@7"), null,
                "authorized revision"));

        if (failure == Failure.MUTATION_UNKNOWN) {
            events.add(event(runId, traceId, Step.READ_STATUS, "WRONG_WORKFLOW", revision,
                    List.of("runbook:payments@7"), "op-91", "mutation requires reconciliation"));
            return new RunResult(Outcome.UNKNOWN, false, 0, events, "side effect outcome unknown");
        }

        int attempts = 0;
        boolean liveKnown = false;
        while (attempts < 2 && budget.admits(1, 100, 2)) {
            attempts++;
            budget = budget.spend(1, 100, 2);
            if (failure != Failure.STATUS_TIMEOUT) {
                liveKnown = true;
                events.add(event(runId, traceId, Step.READ_STATUS, "OK", revision,
                        List.of("runbook:payments@7"), null, "health=GREEN"));
                break;
            }
            events.add(event(runId, traceId, Step.READ_STATUS, "TIMEOUT", revision,
                    List.of("runbook:payments@7"), null, "attempt=" + attempts));
        }

        budget = budget.spend(1, 10, 1);
        events.add(event(runId, traceId, Step.ASSEMBLE, "OK", revision,
                List.of("runbook:payments@7"), null, liveKnown ? "evidence+live" : "evidence-only"));
        budget = budget.spend(1, 20, 1);
        events.add(event(runId, traceId, Step.GENERATE, "OK", revision,
                List.of("runbook:payments@7"), null, "bounded response"));
        budget = budget.spend(1, 10, 1);
        events.add(event(runId, traceId, Step.VALIDATE, liveKnown ? "ACCEPT" : "ACCEPT_WITH_DISCLOSURE",
                revision, List.of("runbook:payments@7"), null, "citation checked"));

        Outcome outcome = liveKnown ? Outcome.COMPLETE : Outcome.DEGRADED;
        String qualifier = liveKnown ? "documented procedure; live health verified"
                : "documented procedure; live health unavailable after bounded retry";
        events.add(event(runId, traceId, Step.COMPLETE, outcome.name(), revision,
                List.of("runbook:payments@7"), null, qualifier));
        return new RunResult(outcome, liveKnown, attempts, events, qualifier);
    }

    static Set<Step> allowedAfter(Step step) {
        return switch (step) {
            case CLASSIFY -> EnumSet.of(Step.RETRIEVE);
            case RETRIEVE -> EnumSet.of(Step.READ_STATUS, Step.ASSEMBLE);
            case READ_STATUS -> EnumSet.of(Step.READ_STATUS, Step.ASSEMBLE);
            case ASSEMBLE -> EnumSet.of(Step.GENERATE);
            case GENERATE -> EnumSet.of(Step.VALIDATE);
            case VALIDATE -> EnumSet.of(Step.COMPLETE);
            case COMPLETE -> EnumSet.noneOf(Step.class);
        };
    }

    private static StageEvent event(String runId, String traceId, Step step, String status,
                                    String revision, List<String> evidence, String op, String detail) {
        return new StageEvent(runId, traceId, step, status, revision, evidence, op, detail);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int scenarios = 0;

        Decision fixed = choose(new TaskShape(true, false, false, 1));
        expect(fixed.kind == PlanKind.DETERMINISTIC, "known flow should stay deterministic"); scenarios++;
        expect(fixed.fixedPlan.size() == 7, "fixed plan should be explicit"); scenarios++;
        expect(fixed.maximumAgentSteps == 0, "fixed plan needs no agent cap"); scenarios++;
        Decision agent = choose(new TaskShape(false, true, false, 5));
        expect(agent.kind == PlanKind.BOUNDED_AGENT && agent.maximumAgentSteps == 6, "agent must be bounded"); scenarios++;
        Decision mutatingAgent = choose(new TaskShape(false, true, true, 20));
        expect(mutatingAgent.maximumAgentSteps == 8, "agent cap must have a hard ceiling"); scenarios++;

        RunResult success = runKnownReadWorkflow(Failure.NONE, new Budget(8, 1_000, 20), "success");
        expect(success.outcome == Outcome.COMPLETE && success.liveStatusKnown, "success path"); scenarios++;
        expect(success.statusAttempts == 1, "successful status should not retry"); scenarios++;
        expect(success.events.get(success.events.size() - 1).step == Step.COMPLETE, "terminal event"); scenarios++;

        RunResult degraded = runKnownReadWorkflow(Failure.STATUS_TIMEOUT, new Budget(8, 1_000, 20), "degraded");
        expect(degraded.outcome == Outcome.DEGRADED && !degraded.liveStatusKnown, "read timeout may degrade"); scenarios++;
        expect(degraded.statusAttempts == 2, "one bounded retry"); scenarios++;
        expect(degraded.answerQualifier.contains("unavailable"), "degradation must be disclosed"); scenarios++;
        expect(degraded.events.stream().filter(e -> e.status.equals("TIMEOUT")).count() == 2, "timeouts traced"); scenarios++;

        RunResult auth = runKnownReadWorkflow(Failure.AUTHENTICATION, new Budget(8, 1_000, 20), "auth");
        expect(auth.outcome == Outcome.DENIED && auth.events.size() == 2, "auth failure closes early"); scenarios++;
        RunResult tenant = runKnownReadWorkflow(Failure.TENANT_SCOPE, new Budget(8, 1_000, 20), "tenant");
        expect(tenant.outcome == Outcome.DENIED, "tenant failure cannot remove filters"); scenarios++;
        RunResult unknown = runKnownReadWorkflow(Failure.MUTATION_UNKNOWN, new Budget(8, 1_000, 20), "unknown");
        expect(unknown.outcome == Outcome.UNKNOWN, "mutation timeout is not degradation"); scenarios++;

        expect(allowedAfter(Step.CLASSIFY).equals(Set.of(Step.RETRIEVE)), "classify transition"); scenarios++;
        expect(allowedAfter(Step.READ_STATUS).containsAll(Set.of(Step.READ_STATUS, Step.ASSEMBLE)), "read retry transition"); scenarios++;
        expect(allowedAfter(Step.COMPLETE).isEmpty(), "terminal transition"); scenarios++;

        Budget budget = new Budget(3, 500, 8).spend(1, 120, 2);
        expect(budget.equals(new Budget(2, 380, 6)), "budget arithmetic"); scenarios++;
        try { budget.spend(3, 1, 1); throw new AssertionError("overspend accepted"); }
        catch (IllegalStateException expected) { scenarios++; }

        SideEffect effect = new SideEffect("op-91", "fp-A", OperationState.NOT_STARTED, null);
        effect = effect.recordIntent();
        expect(effect.state == OperationState.INTENT_RECORDED, "intent checkpoint"); scenarios++;
        effect = effect.commit("INC-1042");
        expect(effect.state == OperationState.COMMITTED, "commit checkpoint"); scenarios++;
        effect = effect.loseResponse();
        expect(effect.state == OperationState.UNKNOWN, "caller observation may be unknown"); scenarios++;
        expect(effect.reconcile("fp-A").equals("INC-1042"), "reconcile committed result"); scenarios++;
        try { effect.reconcile("fp-B"); throw new AssertionError("fingerprint conflict accepted"); }
        catch (IllegalStateException expected) { scenarios++; }

        StageEvent safe = event("r", "t", Step.RETRIEVE, "OK", "rev", List.of("doc@7"), null, "hash=abc");
        expect(safe.evidenceIds.equals(List.of("doc@7")), "evidence identity logged"); scenarios++;
        try { event("r", "t", Step.RETRIEVE, "OK", "rev", List.of(), null, "Bearer secret");
              throw new AssertionError("secret logged"); }
        catch (IllegalArgumentException expected) { scenarios++; }
        try { new Budget(-1, 0, 0); throw new AssertionError("negative budget accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }

        Random random = new Random(0xA11CE55L);
        int trials = 10_000;
        for (int i = 0; i < trials; i++) {
            boolean known = random.nextBoolean();
            boolean branching = random.nextBoolean();
            boolean consequential = random.nextBoolean();
            int tools = random.nextInt(40);
            Decision actual = choose(new TaskShape(known, branching, consequential, tools));
            PlanKind expectedKind = known && !branching ? PlanKind.DETERMINISTIC : PlanKind.BOUNDED_AGENT;
            expect(actual.kind == expectedKind, "planner oracle mismatch at " + i);
            if (actual.kind == PlanKind.BOUNDED_AGENT) {
                expect(actual.maximumAgentSteps >= 1 && actual.maximumAgentSteps <= 8, "agent cap at " + i);
            } else {
                expect(actual.fixedPlan.get(0) == Step.CLASSIFY
                        && actual.fixedPlan.get(actual.fixedPlan.size() - 1) == Step.COMPLETE,
                        "fixed endpoints at " + i);
            }
        }
        scenarios++;

        expect(scenarios == 29, "scenario count drift: " + scenarios);
        System.out.println("SCENARIOS=" + scenarios
                + "|TRIALS=" + trials
                + "|PLAN=DETERMINISTIC_FIRST"
                + "|DEGRADE=DISCLOSED"
                + "|AUTH=FAIL_CLOSED"
                + "|MUTATION=RECONCILE"
                + "|CHECKPOINT=BEFORE_AND_AFTER"
                + "|TRACE=CORRELATED");
    }
}
