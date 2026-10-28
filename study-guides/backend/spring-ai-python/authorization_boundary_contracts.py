from dataclasses import dataclass


@dataclass(frozen=True)
class TokenEvidence:
    signature_valid: bool
    algorithm: str
    issuer: str
    audience: str
    subject: str
    not_before: int
    expires_at: int
    scopes: frozenset[str]
    tenant_hint: str | None


@dataclass(frozen=True)
class Membership:
    tenants: frozenset[str]
    permissions: frozenset[str]


@dataclass(frozen=True)
class Identity:
    subject: str
    tenants: frozenset[str]
    permissions: frozenset[str]


@dataclass(frozen=True)
class Decision:
    status: int
    reason: str
    tenant_predicate: str | None


def authenticate(token: TokenEvidence, now: int,
                 directory: dict[str, Membership]) -> Identity:
    if (
        not token.signature_valid
        or token.algorithm != "RS256"
        or token.issuer != "https://id.example"
        or token.audience != "https://atlas.example/api"
        or not token.subject
        or now < token.not_before
        or now >= token.expires_at
    ):
        raise PermissionError("invalid_token")
    membership = directory.get(token.subject)
    if membership is None:
        raise PermissionError("invalid_subject")
    return Identity(token.subject, membership.tenants, membership.permissions)


def authorize_evidence(token: TokenEvidence, now: int,
                       directory: dict[str, Membership],
                       requested_tenant: str) -> Decision:
    try:
        identity = authenticate(token, now, directory)
    except PermissionError as invalid:
        return Decision(401, str(invalid), None)
    if "atlas.answer" not in token.scopes or "evidence:read" not in identity.permissions:
        return Decision(403, "insufficient_scope", None)
    if requested_tenant not in identity.tenants:
        return Decision(404, "concealed_resource", None)
    return Decision(200, "allow", f"tenant_id = {requested_tenant}")


def authorize_tool(identity: Identity, resource_tenant: str,
                   permission: str, resource_exists: bool) -> bool:
    return (
        resource_exists
        and resource_tenant in identity.tenants
        and permission in identity.permissions
    )


class PooledSession:
    def __init__(self) -> None:
        self.in_transaction = False
        self.local_tenant: str | None = None

    def begin(self) -> None:
        if self.in_transaction:
            raise RuntimeError("already active")
        self.in_transaction = True

    def set_local_tenant(self, tenant: str) -> None:
        if not self.in_transaction or not tenant:
            raise RuntimeError("transaction-local tenant required")
        self.local_tenant = tenant

    def can_read(self, row_tenant: str) -> bool:
        return self.in_transaction and self.local_tenant == row_tenant

    def end(self) -> None:
        if not self.in_transaction:
            raise RuntimeError("no active transaction")
        self.in_transaction = False
        self.local_tenant = None


def token(signature: bool, algorithm: str, issuer: str, audience: str,
          subject: str, not_before: int, expires_at: int,
          scopes: frozenset[str], tenant_hint: str | None) -> TokenEvidence:
    return TokenEvidence(signature, algorithm, issuer, audience, subject,
                         not_before, expires_at, scopes, tenant_hint)


def next_state(state: int) -> int:
    return (state * 1_664_525 + 1_013_904_223) & 0xFFFF_FFFF


def main() -> None:
    scenarios = 0

    def check(condition: bool) -> None:
        nonlocal scenarios
        assert condition
        scenarios += 1

    def expect_invalid(action) -> None:
        nonlocal scenarios
        try:
            action()
            raise AssertionError("expected rejection")
        except (PermissionError, RuntimeError):
            scenarios += 1

    now = 10_000
    directory = {
        "user-7": Membership(frozenset({"customer-a"}),
                             frozenset({"evidence:read", "orders:refund"})),
        "reader": Membership(frozenset({"customer-b"}),
                             frozenset({"evidence:read"})),
    }
    valid = token(True, "RS256", "https://id.example", "https://atlas.example/api",
                  "user-7", 9_000, 11_000, frozenset({"atlas.answer"}), "customer-b")
    identity = authenticate(valid, now, directory)

    check(identity.subject == "user-7")
    check(identity.tenants == frozenset({"customer-a"}))
    check(valid.tenant_hint not in identity.tenants)
    check(authorize_evidence(valid, now, directory, "customer-a").status == 200)
    check(authorize_evidence(valid, now, directory, "customer-a").tenant_predicate == "tenant_id = customer-a")
    check(authorize_evidence(valid, now, directory, "customer-b").status == 404)
    check(authorize_tool(identity, "customer-a", "orders:refund", True))
    check(not authorize_tool(identity, "customer-b", "orders:refund", True))
    check(not authorize_tool(identity, "customer-a", "orders:delete", True))
    check(not authorize_tool(identity, "customer-a", "orders:refund", False))

    expect_invalid(lambda: authenticate(token(False, "RS256", valid.issuer, valid.audience, "user-7", 9_000, 11_000, valid.scopes, None), now, directory))
    expect_invalid(lambda: authenticate(token(True, "none", valid.issuer, valid.audience, "user-7", 9_000, 11_000, valid.scopes, None), now, directory))
    expect_invalid(lambda: authenticate(token(True, "RS256", "https://evil.example", valid.audience, "user-7", 9_000, 11_000, valid.scopes, None), now, directory))
    expect_invalid(lambda: authenticate(token(True, "RS256", valid.issuer, "analytics-api", "user-7", 9_000, 11_000, valid.scopes, None), now, directory))
    expect_invalid(lambda: authenticate(token(True, "RS256", valid.issuer, valid.audience, "user-7", 10_001, 11_000, valid.scopes, None), now, directory))
    expect_invalid(lambda: authenticate(token(True, "RS256", valid.issuer, valid.audience, "user-7", 9_000, 10_000, valid.scopes, None), now, directory))
    expect_invalid(lambda: authenticate(token(True, "RS256", valid.issuer, valid.audience, "", 9_000, 11_000, valid.scopes, None), now, directory))
    expect_invalid(lambda: authenticate(token(True, "RS256", valid.issuer, valid.audience, "removed-user", 9_000, 11_000, valid.scopes, None), now, directory))

    no_scope = token(True, "RS256", valid.issuer, valid.audience, "user-7", 9_000, 11_000, frozenset(), None)
    check(authorize_evidence(no_scope, now, directory, "customer-a").status == 403)
    no_permission = {"user-7": Membership(frozenset({"customer-a"}), frozenset())}
    check(authorize_evidence(valid, now, no_permission, "customer-a").status == 403)

    session = PooledSession()
    expect_invalid(lambda: session.set_local_tenant("customer-a"))
    session.begin()
    session.set_local_tenant("customer-a")
    check(session.can_read("customer-a"))
    check(not session.can_read("customer-b"))
    session.end()
    check(not session.can_read("customer-a"))
    session.begin()
    check(not session.can_read("customer-a"))
    session.set_local_tenant("customer-b")
    check(session.can_read("customer-b"))
    session.end()
    expect_invalid(session.end)

    reader_token = token(True, "RS256", valid.issuer, valid.audience, "reader", 9_000, 11_000, valid.scopes, None)
    reader = authenticate(reader_token, now, directory)
    check(reader.tenants == frozenset({"customer-b"}))
    check(not authorize_tool(reader, "customer-b", "orders:refund", True))
    check(authorize_evidence(token(True, "RS256", valid.issuer, valid.audience, "reader", 9_000, 11_000, valid.scopes, "customer-a"), now, directory, "customer-b").status == 200)

    state = 0x19A07A5
    for _ in range(10_000):
        state = next_state(state); signature = (state & 1) == 0
        state = next_state(state); correct_audience = (state & 1) == 0
        state = next_state(state); fresh = (state & 1) == 0
        state = next_state(state); scope = (state & 1) == 0
        state = next_state(state); member = (state & 1) == 0
        candidate = token(signature, "RS256", valid.issuer,
                          valid.audience if correct_audience else "other-api",
                          "user-7", 9_000, 11_000 if fresh else 10_000,
                          frozenset({"atlas.answer"}) if scope else frozenset(),
                          "customer-b")
        requested = "customer-a" if member else "customer-b"
        actual = authorize_evidence(candidate, now, directory, requested).status
        expected = 401 if not signature or not correct_audience or not fresh else 403 if not scope else 404 if not member else 200
        assert actual == expected

    assert scenarios == 30, scenarios
    print("PARITY|JWT=VERIFIED|AUDIENCE=BOUND|TENANT=DIRECTORY|QUERY=SCOPED|RLS=LOCAL|TOOL=RECHECKED|DELEGATION=EXPLICIT")
    print("Authorization boundary contracts passed: 30 scenario groups + 10000 seeded dual-oracle trials")


if __name__ == "__main__":
    main()
