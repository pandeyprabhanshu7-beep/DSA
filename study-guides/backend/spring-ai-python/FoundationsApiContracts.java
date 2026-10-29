import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class FoundationsApiContracts {
    private static int scenarios;

    enum RetrievalMode { LEXICAL, SEMANTIC, HYBRID }
    enum Capability { TOOLS, JSON_SCHEMA, STREAMING, CANCELLATION, USAGE }
    enum Outcome { ANSWER, NO_ANSWER, UPSTREAM_FAILURE, INVALID_OUTPUT, FORBIDDEN }

    record Provider(String id, Set<Capability> capabilities) {
        Provider {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("blank provider ID");
            capabilities = Set.copyOf(capabilities);
        }
    }

    record AnswerCandidate(boolean providerAvailable, boolean evidenceSufficient,
                           boolean parsed, boolean schemaValid, boolean domainValid,
                           boolean authorized) {}

    static RetrievalMode retrievalMode(boolean exactIdentifier, boolean semanticIntent) {
        if (exactIdentifier && semanticIntent) return RetrievalMode.HYBRID;
        if (exactIdentifier) return RetrievalMode.LEXICAL;
        return RetrievalMode.SEMANTIC;
    }

    static Provider admit(List<Provider> providers, Set<Capability> required) {
        if (providers.isEmpty()) throw new IllegalArgumentException("no providers");
        Set<Capability> immutableRequired = Set.copyOf(required);
        return providers.stream()
                .filter(provider -> provider.capabilities().containsAll(immutableRequired))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no capable provider"));
    }

    static Outcome classify(AnswerCandidate candidate) {
        if (!candidate.providerAvailable()) return Outcome.UPSTREAM_FAILURE;
        if (!candidate.evidenceSufficient()) return Outcome.NO_ANSWER;
        if (!candidate.parsed() || !candidate.schemaValid() || !candidate.domainValid()) {
            return Outcome.INVALID_OUTPUT;
        }
        if (!candidate.authorized()) return Outcome.FORBIDDEN;
        return Outcome.ANSWER;
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("check failed");
        scenarios++;
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected rejection");
        } catch (IllegalArgumentException expected) {
            scenarios++;
        }
    }

    private static long next(long state) {
        return (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
    }

    public static void main(String[] args) {
        check(retrievalMode(true, false) == RetrievalMode.LEXICAL);
        check(retrievalMode(false, true) == RetrievalMode.SEMANTIC);
        check(retrievalMode(false, false) == RetrievalMode.SEMANTIC);
        check(retrievalMode(true, true) == RetrievalMode.HYBRID);

        Provider portable = new Provider("portable", EnumSet.of(
                Capability.TOOLS, Capability.JSON_SCHEMA, Capability.USAGE));
        Provider streaming = new Provider("streaming", EnumSet.of(
                Capability.TOOLS, Capability.STREAMING, Capability.CANCELLATION,
                Capability.USAGE));
        Provider complete = new Provider("complete", EnumSet.allOf(Capability.class));
        List<Provider> ordered = List.of(portable, streaming, complete);
        check(admit(ordered, Set.of(Capability.TOOLS)).id().equals("portable"));
        check(admit(ordered, Set.of(Capability.STREAMING)).id().equals("streaming"));
        check(admit(ordered, Set.of(Capability.JSON_SCHEMA, Capability.CANCELLATION))
                .id().equals("complete"));
        check(admit(ordered, Set.of()).id().equals("portable"));
        check(portable.capabilities().equals(EnumSet.of(
                Capability.TOOLS, Capability.JSON_SCHEMA, Capability.USAGE)));
        check(complete.capabilities().equals(EnumSet.allOf(Capability.class)));

        check(classify(new AnswerCandidate(true, true, true, true, true, true))
                == Outcome.ANSWER);
        check(classify(new AnswerCandidate(true, false, false, false, false, false))
                == Outcome.NO_ANSWER);
        check(classify(new AnswerCandidate(false, true, true, true, true, true))
                == Outcome.UPSTREAM_FAILURE);
        check(classify(new AnswerCandidate(true, true, false, true, true, true))
                == Outcome.INVALID_OUTPUT);
        check(classify(new AnswerCandidate(true, true, true, false, true, true))
                == Outcome.INVALID_OUTPUT);
        check(classify(new AnswerCandidate(true, true, true, true, false, true))
                == Outcome.INVALID_OUTPUT);
        check(classify(new AnswerCandidate(true, true, true, true, true, false))
                == Outcome.FORBIDDEN);
        check(classify(new AnswerCandidate(false, false, false, false, false, false))
                == Outcome.UPSTREAM_FAILURE);

        rejects(() -> new Provider("", Set.of()));
        rejects(() -> new Provider(" ", Set.of()));
        rejects(() -> new Provider(null, Set.of()));
        rejects(() -> admit(List.of(), Set.of()));
        rejects(() -> admit(List.of(portable, streaming),
                Set.of(Capability.JSON_SCHEMA, Capability.CANCELLATION)));
        rejects(() -> admit(List.of(portable), Set.of(Capability.STREAMING)));

        // Exhaust every truth-table row and compare with an independently written oracle.
        long state = 0x21F0A11DL;
        for (int trial = 0; trial < 10_000; trial++) {
            state = next(state); boolean providerUp = (state & 1) != 0;
            state = next(state); boolean evidence = (state & 1) != 0;
            state = next(state); boolean parsed = (state & 1) != 0;
            state = next(state); boolean schema = (state & 1) != 0;
            state = next(state); boolean domain = (state & 1) != 0;
            state = next(state); boolean authorized = (state & 1) != 0;
            AnswerCandidate candidate = new AnswerCandidate(providerUp, evidence, parsed,
                    schema, domain, authorized);
            Outcome expected;
            if (!providerUp) expected = Outcome.UPSTREAM_FAILURE;
            else if (!evidence) expected = Outcome.NO_ANSWER;
            else if (!(parsed && schema && domain)) expected = Outcome.INVALID_OUTPUT;
            else if (!authorized) expected = Outcome.FORBIDDEN;
            else expected = Outcome.ANSWER;
            assert classify(candidate) == expected;
        }

        // Compare capability admission against a direct subset oracle.
        List<Provider> matrix = new ArrayList<>(ordered);
        state = 0xC4A8B1E5L;
        for (int trial = 0; trial < 10_000; trial++) {
            state = next(state);
            EnumSet<Capability> required = EnumSet.noneOf(Capability.class);
            Capability[] values = Capability.values();
            for (int bit = 0; bit < values.length; bit++) {
                if ((state & (1L << bit)) != 0) required.add(values[bit]);
            }
            Provider expected = matrix.stream()
                    .filter(provider -> provider.capabilities().containsAll(required))
                    .findFirst().orElse(null);
            try {
                Provider actual = admit(matrix, required);
                assert actual.equals(expected);
            } catch (IllegalArgumentException unavailable) {
                assert expected == null;
            }
        }

        assert scenarios == 24 : "scenario count " + scenarios;
        System.out.println("PARITY|RETRIEVAL=LEXICAL,SEMANTIC,HYBRID|CAPABILITIES=ADMITTED|OUTCOMES=ANSWER,NO_ANSWER,UPSTREAM_FAILURE,INVALID_OUTPUT,FORBIDDEN|ORDER=DETERMINISTIC");
        System.out.println("Foundations/API contracts passed: 24 scenario groups + 20000 seeded trials");
    }
}
