"""Dependency-free contracts for local callbacks, MCP tools and resources."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


MCP_AUDIENCE = "https://mcp.atlas.example"
NOW = 1_800_000_000


class Exposure(Enum):
    LOCAL_CALLBACK = "LOCAL_CALLBACK"
    MCP_TOOL = "MCP_TOOL"
    MCP_RESOURCE = "MCP_RESOURCE"


class Transport(Enum):
    STDIO = "STDIO"
    STREAMABLE_HTTP = "STREAMABLE_HTTP"


class Decision(Enum):
    ALLOW = "ALLOW"
    BAD_SCHEMA = "BAD_SCHEMA"
    UNKNOWN_SERVICE = "UNKNOWN_SERVICE"
    UNAUTHENTICATED = "UNAUTHENTICATED"
    BAD_AUDIENCE = "BAD_AUDIENCE"
    EXPIRED = "EXPIRED"
    MISSING_SCOPE = "MISSING_SCOPE"
    TENANT_DENIED = "TENANT_DENIED"
    UNSAFE_STDIO = "UNSAFE_STDIO"


@dataclass(frozen=True)
class Caller:
    tenant: str
    scopes: frozenset[str]
    audience: str
    expires_at: int


@dataclass(frozen=True)
class ToolHints:
    read_only: bool
    destructive: bool
    idempotent: bool
    open_world: bool


@dataclass(frozen=True)
class ToolSpec:
    name: str
    required_fields: frozenset[str]
    hints: ToolHints


@dataclass(frozen=True)
class Status:
    service: str
    ready: int
    desired: int
    source: str
    live: bool


@dataclass(frozen=True)
class Outcome:
    decision: Decision
    value: Status | None = None


def choose_exposure(consumers: int, standard_discovery: bool, addressable_document: bool) -> Exposure:
    if consumers < 1:
        raise ValueError("consumer count")
    if addressable_document:
        return Exposure.MCP_RESOURCE
    return Exposure.LOCAL_CALLBACK if consumers == 1 and not standard_discovery else Exposure.MCP_TOOL


def _authorize(caller: Caller | None, scope: str, resource_tenant: str) -> Decision:
    if caller is None:
        return Decision.UNAUTHENTICATED
    if caller.audience != MCP_AUDIENCE:
        return Decision.BAD_AUDIENCE
    if caller.expires_at <= NOW:
        return Decision.EXPIRED
    if scope not in caller.scopes:
        return Decision.MISSING_SCOPE
    if caller.tenant != resource_tenant:
        return Decision.TENANT_DENIED
    return Decision.ALLOW


def deployment_status(spec: ToolSpec, arguments: dict[str, object], caller: Caller | None,
                      service_tenant: dict[str, str]) -> Outcome:
    if (spec.name != "deployment_status" or set(arguments) != set(spec.required_fields)
            or type(arguments.get("service")) is not str):
        return Outcome(Decision.BAD_SCHEMA)
    service = arguments["service"]
    assert isinstance(service, str)
    if service not in service_tenant:
        return Outcome(Decision.UNKNOWN_SERVICE)
    auth = _authorize(caller, "status:read", service_tenant[service])
    if auth is not Decision.ALLOW:
        return Outcome(auth)
    return Outcome(Decision.ALLOW, Status(service, 3, 3, "synthetic-training-fixture", False))


def read_runbook(uri: str, caller: Caller | None, resource_tenant: dict[str, str]) -> Decision:
    import re
    if re.fullmatch(r"runbook://[a-z0-9-]+/[1-9][0-9]*", uri) is None:
        return Decision.BAD_SCHEMA
    tenant = resource_tenant.get(uri)
    return Decision.TENANT_DENIED if tenant is None else _authorize(caller, "runbook:read", tenant)


def transport_admission(transport: Transport, host_spawned: bool,
                        logs_only_to_stderr: bool, caller: Caller | None) -> Decision:
    if transport is Transport.STDIO:
        return Decision.ALLOW if host_spawned and logs_only_to_stderr else Decision.UNSAFE_STDIO
    if caller is None:
        return Decision.UNAUTHENTICATED
    if caller.audience != MCP_AUDIENCE:
        return Decision.BAD_AUDIENCE
    if caller.expires_at <= NOW:
        return Decision.EXPIRED
    return Decision.ALLOW


def hints_grant_permission(_hints: ToolHints) -> bool:
    return False


def main() -> None:
    scenarios = 0
    hints = ToolHints(True, False, True, False)
    spec = ToolSpec("deployment_status", frozenset({"service"}), hints)
    service_tenant = {"payments": "tenant-a", "catalog": "tenant-a"}
    resources = {"runbook://payments/7": "tenant-a"}
    reader = Caller("tenant-a", frozenset({"status:read", "runbook:read"}), MCP_AUDIENCE, NOW + 300)

    checks = [
        choose_exposure(1, False, False) is Exposure.LOCAL_CALLBACK,
        choose_exposure(2, False, False) is Exposure.MCP_TOOL,
        choose_exposure(1, True, False) is Exposure.MCP_TOOL,
        choose_exposure(1, False, True) is Exposure.MCP_RESOURCE,
        choose_exposure(3, True, True) is Exposure.MCP_RESOURCE,
    ]
    for check in checks:
        assert check
        scenarios += 1
    try:
        choose_exposure(0, False, False)
        raise AssertionError
    except ValueError:
        scenarios += 1

    ok = deployment_status(spec, {"service": "payments"}, reader, service_tenant)
    checks = [
        ok.decision is Decision.ALLOW and ok.value is not None and ok.value.service == "payments",
        ok.value is not None and not ok.value.live and ok.value.source == "synthetic-training-fixture",
        deployment_status(spec, {}, reader, service_tenant).decision is Decision.BAD_SCHEMA,
        deployment_status(spec, {"service": "payments", "extra": "x"}, reader, service_tenant).decision is Decision.BAD_SCHEMA,
        deployment_status(spec, {"service": 7}, reader, service_tenant).decision is Decision.BAD_SCHEMA,
        deployment_status(spec, {"service": "orders"}, reader, service_tenant).decision is Decision.UNKNOWN_SERVICE,
        deployment_status(ToolSpec("other", frozenset({"service"}), hints), {"service": "payments"}, reader, service_tenant).decision is Decision.BAD_SCHEMA,
        deployment_status(spec, {"service": "payments"}, None, service_tenant).decision is Decision.UNAUTHENTICATED,
        deployment_status(spec, {"service": "payments"}, Caller("tenant-a", frozenset({"status:read"}), "other", NOW + 1), service_tenant).decision is Decision.BAD_AUDIENCE,
        deployment_status(spec, {"service": "payments"}, Caller("tenant-a", frozenset({"status:read"}), MCP_AUDIENCE, NOW), service_tenant).decision is Decision.EXPIRED,
        deployment_status(spec, {"service": "payments"}, Caller("tenant-a", frozenset(), MCP_AUDIENCE, NOW + 1), service_tenant).decision is Decision.MISSING_SCOPE,
        deployment_status(spec, {"service": "payments"}, Caller("tenant-b", frozenset({"status:read"}), MCP_AUDIENCE, NOW + 1), service_tenant).decision is Decision.TENANT_DENIED,
        read_runbook("runbook://payments/7", reader, resources) is Decision.ALLOW,
        read_runbook("runbook://payments/latest", reader, resources) is Decision.BAD_SCHEMA,
        read_runbook("https://payments/7", reader, resources) is Decision.BAD_SCHEMA,
        read_runbook("runbook://catalog/7", reader, resources) is Decision.TENANT_DENIED,
        read_runbook("runbook://payments/7", Caller("tenant-b", frozenset({"runbook:read"}), MCP_AUDIENCE, NOW + 1), resources) is Decision.TENANT_DENIED,
        read_runbook("runbook://payments/7", Caller("tenant-a", frozenset(), MCP_AUDIENCE, NOW + 1), resources) is Decision.MISSING_SCOPE,
        transport_admission(Transport.STDIO, True, True, None) is Decision.ALLOW,
        transport_admission(Transport.STDIO, False, True, None) is Decision.UNSAFE_STDIO,
        transport_admission(Transport.STDIO, True, False, None) is Decision.UNSAFE_STDIO,
        transport_admission(Transport.STREAMABLE_HTTP, False, True, None) is Decision.UNAUTHENTICATED,
        transport_admission(Transport.STREAMABLE_HTTP, False, True, reader) is Decision.ALLOW,
        transport_admission(Transport.STREAMABLE_HTTP, False, True, Caller("tenant-a", frozenset(), "other", NOW + 1)) is Decision.BAD_AUDIENCE,
        not hints_grant_permission(hints),
        not hints_grant_permission(ToolHints(False, True, False, True)),
    ]
    for check in checks:
        assert check
        scenarios += 1

    state = 0xC0FFEE
    allowed = 0
    for _ in range(10_000):
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        service = "payments" if (state >> 8) & 1 else "orders"
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        tenant = "tenant-a" if (state >> 16) & 1 else "tenant-b"
        candidate = Caller(tenant, frozenset({"status:read"}), MCP_AUDIENCE, NOW + 1)
        outcome = deployment_status(spec, {"service": service}, candidate, service_tenant)
        expected = service == "payments" and tenant == "tenant-a"
        assert (outcome.decision is Decision.ALLOW) == expected
        allowed += int(expected)

    print(f"PARITY|LOCAL=CALLBACK|REMOTE=MCP_TOOL|RESOURCE=runbook://payments/7|FIXTURE=NONLIVE|HTTP=AUTH_REQUIRED|HINT=NONAUTH|ALLOWED={allowed}")
    print(f"Python MCP implementation contracts passed: {scenarios} scenario groups + 10000 seeded trials")


if __name__ == "__main__":
    main()
