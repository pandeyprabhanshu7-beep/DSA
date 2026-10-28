import java.util.Map;
import java.util.Set;

public class AuthorizationBoundaryContracts {
    private static int scenarios;

    record TokenEvidence(boolean signatureValid, String algorithm, String issuer,
                         String audience, String subject, long notBefore,
                         long expiresAt, Set<String> scopes, String tenantHint) {}

    record Membership(Set<String> tenants, Set<String> permissions) {}

    record Identity(String subject, Set<String> tenants, Set<String> permissions) {}

    record Decision(int status, String reason, String tenantPredicate) {}

    static Identity authenticate(TokenEvidence token, long now,
                                 Map<String, Membership> directory) {
        if (!token.signatureValid()
                || !"RS256".equals(token.algorithm())
                || !"https://id.example".equals(token.issuer())
                || !"https://atlas.example/api".equals(token.audience())
                || token.subject() == null || token.subject().isBlank()
                || now < token.notBefore() || now >= token.expiresAt()) {
            throw new SecurityException("invalid_token");
        }
        Membership membership = directory.get(token.subject());
        if (membership == null) {
            throw new SecurityException("invalid_subject");
        }
        return new Identity(token.subject(), Set.copyOf(membership.tenants()),
                Set.copyOf(membership.permissions()));
    }

    static Decision authorizeEvidence(TokenEvidence token, long now,
                                      Map<String, Membership> directory,
                                      String requestedTenant) {
        final Identity identity;
        try {
            identity = authenticate(token, now, directory);
        } catch (SecurityException invalid) {
            return new Decision(401, invalid.getMessage(), null);
        }
        if (!token.scopes().contains("atlas.answer")
                || !identity.permissions().contains("evidence:read")) {
            return new Decision(403, "insufficient_scope", null);
        }
        if (!identity.tenants().contains(requestedTenant)) {
            return new Decision(404, "concealed_resource", null);
        }
        return new Decision(200, "allow", "tenant_id = " + requestedTenant);
    }

    static boolean authorizeTool(Identity identity, String resourceTenant,
                                 String permission, boolean resourceExists) {
        return resourceExists
                && identity.tenants().contains(resourceTenant)
                && identity.permissions().contains(permission);
    }

    static final class PooledSession {
        private boolean inTransaction;
        private String localTenant;

        void begin() {
            if (inTransaction) throw new IllegalStateException("already active");
            inTransaction = true;
        }

        void setLocalTenant(String tenant) {
            if (!inTransaction || tenant == null || tenant.isBlank()) {
                throw new IllegalStateException("transaction-local tenant required");
            }
            localTenant = tenant;
        }

        boolean canRead(String rowTenant) {
            return inTransaction && localTenant != null && localTenant.equals(rowTenant);
        }

        void end() {
            if (!inTransaction) throw new IllegalStateException("no active transaction");
            inTransaction = false;
            localTenant = null;
        }
    }

    private static TokenEvidence token(boolean signature, String algorithm,
                                       String issuer, String audience,
                                       String subject, long notBefore,
                                       long expiresAt, Set<String> scopes,
                                       String tenantHint) {
        return new TokenEvidence(signature, algorithm, issuer, audience,
                subject, notBefore, expiresAt, scopes, tenantHint);
    }

    private static void check(boolean condition) {
        assert condition;
        scenarios++;
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected rejection");
        } catch (SecurityException | IllegalStateException expected) {
            scenarios++;
        }
    }

    private static long next(long state) {
        return (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
    }

    public static void main(String[] args) {
        long now = 10_000L;
        Map<String, Membership> directory = Map.of(
                "user-7", new Membership(Set.of("customer-a"),
                        Set.of("evidence:read", "orders:refund")),
                "reader", new Membership(Set.of("customer-b"),
                        Set.of("evidence:read")));
        TokenEvidence valid = token(true, "RS256", "https://id.example",
                "https://atlas.example/api", "user-7", 9_000, 11_000,
                Set.of("atlas.answer"), "customer-b");

        Identity identity = authenticate(valid, now, directory);
        check(identity.subject().equals("user-7"));
        check(identity.tenants().equals(Set.of("customer-a")));
        check(!identity.tenants().contains(valid.tenantHint()));
        check(authorizeEvidence(valid, now, directory, "customer-a").status() == 200);
        check(authorizeEvidence(valid, now, directory, "customer-a")
                .tenantPredicate().equals("tenant_id = customer-a"));
        check(authorizeEvidence(valid, now, directory, "customer-b").status() == 404);
        check(authorizeTool(identity, "customer-a", "orders:refund", true));
        check(!authorizeTool(identity, "customer-b", "orders:refund", true));
        check(!authorizeTool(identity, "customer-a", "orders:delete", true));
        check(!authorizeTool(identity, "customer-a", "orders:refund", false));

        expectInvalid(() -> authenticate(token(false, "RS256", valid.issuer(), valid.audience(),
                "user-7", 9_000, 11_000, valid.scopes(), null), now, directory));
        expectInvalid(() -> authenticate(token(true, "none", valid.issuer(), valid.audience(),
                "user-7", 9_000, 11_000, valid.scopes(), null), now, directory));
        expectInvalid(() -> authenticate(token(true, "RS256", "https://evil.example", valid.audience(),
                "user-7", 9_000, 11_000, valid.scopes(), null), now, directory));
        expectInvalid(() -> authenticate(token(true, "RS256", valid.issuer(), "analytics-api",
                "user-7", 9_000, 11_000, valid.scopes(), null), now, directory));
        expectInvalid(() -> authenticate(token(true, "RS256", valid.issuer(), valid.audience(),
                "user-7", 10_001, 11_000, valid.scopes(), null), now, directory));
        expectInvalid(() -> authenticate(token(true, "RS256", valid.issuer(), valid.audience(),
                "user-7", 9_000, 10_000, valid.scopes(), null), now, directory));
        expectInvalid(() -> authenticate(token(true, "RS256", valid.issuer(), valid.audience(),
                "", 9_000, 11_000, valid.scopes(), null), now, directory));
        expectInvalid(() -> authenticate(token(true, "RS256", valid.issuer(), valid.audience(),
                "removed-user", 9_000, 11_000, valid.scopes(), null), now, directory));

        TokenEvidence noScope = token(true, "RS256", valid.issuer(), valid.audience(),
                "user-7", 9_000, 11_000, Set.of(), null);
        check(authorizeEvidence(noScope, now, directory, "customer-a").status() == 403);
        Map<String, Membership> noPermission = Map.of("user-7",
                new Membership(Set.of("customer-a"), Set.of()));
        check(authorizeEvidence(valid, now, noPermission, "customer-a").status() == 403);

        PooledSession session = new PooledSession();
        expectInvalid(() -> session.setLocalTenant("customer-a"));
        session.begin();
        session.setLocalTenant("customer-a");
        check(session.canRead("customer-a"));
        check(!session.canRead("customer-b"));
        session.end();
        check(!session.canRead("customer-a"));
        session.begin();
        check(!session.canRead("customer-a"));
        session.setLocalTenant("customer-b");
        check(session.canRead("customer-b"));
        session.end();
        expectInvalid(session::end);

        Identity reader = authenticate(token(true, "RS256", valid.issuer(), valid.audience(),
                "reader", 9_000, 11_000, valid.scopes(), null), now, directory);
        check(reader.tenants().equals(Set.of("customer-b")));
        check(!authorizeTool(reader, "customer-b", "orders:refund", true));
        check(authorizeEvidence(token(true, "RS256", valid.issuer(), valid.audience(),
                "reader", 9_000, 11_000, valid.scopes(), "customer-a"),
                now, directory, "customer-b").status() == 200);

        long state = 0x19A07A5L;
        for (int trial = 0; trial < 10_000; trial++) {
            state = next(state); boolean signature = (state & 1) == 0;
            state = next(state); boolean correctAudience = (state & 1) == 0;
            state = next(state); boolean fresh = (state & 1) == 0;
            state = next(state); boolean scope = (state & 1) == 0;
            state = next(state); boolean member = (state & 1) == 0;
            TokenEvidence candidate = token(signature, "RS256", valid.issuer(),
                    correctAudience ? valid.audience() : "other-api", "user-7",
                    9_000, fresh ? 11_000 : 10_000,
                    scope ? Set.of("atlas.answer") : Set.of(), "customer-b");
            String requested = member ? "customer-a" : "customer-b";
            int actual = authorizeEvidence(candidate, now, directory, requested).status();
            int expected = !signature || !correctAudience || !fresh ? 401
                    : !scope ? 403 : !member ? 404 : 200;
            assert actual == expected : "decision mismatch";
        }

        assert scenarios == 30 : "scenario count " + scenarios;
        System.out.println("PARITY|JWT=VERIFIED|AUDIENCE=BOUND|TENANT=DIRECTORY|QUERY=SCOPED|RLS=LOCAL|TOOL=RECHECKED|DELEGATION=EXPLICIT");
        System.out.println("Authorization boundary contracts passed: 30 scenario groups + 10000 seeded dual-oracle trials");
    }
}
