# Ingress, Egress, Firewalls, WAF, Proxies, DMZ & Stateful vs Stateless Filtering




![Firewall and DMZ flow](assets/diagrams/networking/07-firewalls.svg)

## 1. Start with the boundary

“Ingress” and “egress” are relative terms.

For an application VPC:

```text
Internet -> VPC/application = ingress
VPC/application -> Internet = egress
```

For a database subnet:

```text
app subnet -> DB subnet = ingress to DB subnet
DB response -> app subnet = egress from DB subnet
```

Always say **ingress to what** or **egress from what**.

## 2. A firewall is a policy enforcement point, not one specific product

A network firewall monitors/controls traffic based on rules. Products differ in what context they inspect:

- packet header only,
- connection state,
- protocol/application metadata,
- TLS-decrypted content where configured,
- signatures/IPS rules,
- domain/URL/user identity integrations.

Cloudflare's firewall primer correctly frames a firewall as controlling traffic between trust boundaries and emphasizes both inbound and outbound control.

## 3. Stateless filtering

A stateless rule evaluates packets independently.

Example:

```text
allow inbound TCP dst port 443
```

That rule does not automatically imply:

```text
allow return packets to client ephemeral ports
```

unless separate rules cover them.

### AWS analogy
Network ACLs are stateless. AWS documentation specifically warns that return traffic must satisfy NACL rules independently.

## 4. Stateful filtering

A stateful firewall tracks a flow, commonly using the protocol and 5-tuple plus protocol-specific state and timeouts:

```text
protocol = TCP
source   = 10.0.1.25:51514
target   = 10.0.2.40:443
state    = SYN_SENT -> ESTABLISHED -> CLOSING
```

If policy allows:

```text
client -> server TCP 443
```

return packets that belong to that established allowed flow can normally pass without an independent broad inbound/outbound rule for the reverse ephemeral port.

### Packet-by-packet state trace

| Step | Packet | Stateful decision |
|---:|---|---|
| 1 | client `51514 -> 443`, SYN | Match new-flow policy; create provisional state |
| 2 | server `443 -> 51514`, SYN-ACK | Reverse 5-tuple matches the tracked flow; allow |
| 3 | client ACK | Mark connection established |
| 4 | encrypted application data in either direction | Allow while packets match valid state/policy |
| 5 | FIN/ACK exchange or RST | Move toward closed state |
| 6 | Idle timeout expires | Delete state; a late packet may require a new allowed flow |

Stateful does **not** mean “allow forever.” Implementations enforce TCP-state rules, idle timeouts, capacity limits and product-specific behavior. UDP has no handshake, so state is inferred from recent matching datagrams and shorter timeouts are common.

### AWS analogy
Security Groups are stateful. Response traffic for an allowed flow is recognized as part of the stateful model.

## 5. The ephemeral-port trap

Client initiates:

```text
client:51514 -> server:443
```

Server responds:

```text
server:443 -> client:51514
```

A stateless ACL must permit the relevant reverse-direction ephemeral-port traffic. This is one reason NACL rules are easier to misconfigure than Security Groups for ordinary application flows.

### NACL rule direction depends on who initiated

For an instance acting as an HTTPS **client**:

| Subnet-boundary direction | Destination port | Purpose |
|---|---:|---|
| outbound | 443 | initial request to server |
| inbound | client's ephemeral range | server response to the chosen source port |

For an instance acting as an HTTPS **server**:

| Subnet-boundary direction | Destination port | Purpose |
|---|---:|---|
| inbound | 443 | initial client request |
| outbound | remote client's ephemeral range | response to client source port |

Do not blindly copy one range. Ephemeral ranges vary by operating system and client population. AWS examples commonly use ranges such as `32768-65535`, while heterogeneous internet clients can require a broader `1024-65535` policy. Use the narrowest range that matches the actual endpoints and architecture.

## 6. Layer 3/4 network firewall vs Layer 7 WAF

### Network firewall
Common decision inputs:

```text
source/destination IP
protocol
source/destination port
connection state
IDS/IPS signatures/domain rules depending product
```

### Web Application Firewall (WAF)
Understands HTTP/application-layer concepts such as:

```text
URI path
headers
query strings
HTTP methods
request body patterns
SQLi/XSS signatures
rate-based web rules
```

A WAF is not a replacement for subnet/port-level firewalling. A network firewall is not automatically aware that `/admin/deleteUser` is a dangerous HTTP request.

## 7. Host firewall vs network firewall

A server can have local firewall rules:

```text
Linux nftables/iptables/firewalld
Windows Defender Firewall
```

Even if every upstream firewall allows TCP 443, the host firewall can still block the connection.

Troubleshooting therefore asks:

```text
perimeter policy?
subnet policy?
resource/ENI policy?
host firewall?
process listening?
```

## 8. DMZ/perimeter zone

Traditional enterprises often expose Internet-facing components in a separate perimeter network/DMZ rather than placing them directly inside trusted application/database networks.

Classic pattern:

```text
Internet
  ↓
edge router
  ↓
external firewall
  ↓
DMZ: reverse proxy/WAF/load balancer
  ↓
internal firewall
  ↓
application network
  ↓
DB network
```

The DMZ limits how far an Internet-facing compromise can move without crossing additional policy boundaries.

AWS Prescriptive Guidance still uses the perimeter-zone idea when explaining migrations to architectures with CloudFront/WAF, Network Firewall, ALB, and routed inspection.

## 9. North–south inspection

North–south traffic crosses an environment/perimeter:

```text
Internet -> workload
workload -> Internet
on-prem -> cloud
cloud -> on-prem
```

Controls may include:

- DDoS protection,
- edge WAF,
- network firewall/IPS,
- reverse proxy/load balancer,
- NAT,
- egress proxy/domain allowlists.

## 10. East–west inspection

East–west traffic moves inside the environment:

```text
web -> app
app -> DB
VPC A -> VPC B
workload -> shared service
```

Legacy networks often relied heavily on VLANs + internal firewalls. Cloud environments can combine Security Groups, network firewalls, Transit Gateway route segmentation, Kubernetes policies, service-mesh/mTLS, and identity-based application controls.

## 11. Egress security is as important as ingress

If an attacker compromises an application, unrestricted egress can enable:

- command-and-control,
- data exfiltration,
- malware download,
- calls to unauthorized external services.

Legacy enterprises frequently use outbound firewalls/proxies and domain/category filtering. AWS designs can use:

- restrictive Security Groups where practical,
- AWS Network Firewall domain/rule policies,
- centralized egress VPCs,
- NAT Gateway with fixed Elastic IPs for vendor allowlists,
- VPC endpoints to keep AWS-service traffic off NAT/Internet paths.

## 12. Proxy vs firewall vs NAT vs load balancer

| Component | Main job | Does it usually create a new app/transport connection? |
|---|---|---|
| Router | forward packets between networks | no |
| NAT | translate address/port | no app proxy required |
| Stateful firewall | permit/deny flows with state | typically no app proxy required |
| Forward proxy | makes requests on behalf of clients | yes |
| Reverse proxy | receives client requests for servers | yes |
| Load balancer | distribute flows/requests | depends on L4/L7 implementation; often distinct frontend/backend state |
| WAF | inspect HTTP requests | usually attached/integrated at an L7 proxy/service |

These boxes can be combined in products, which is why enterprise diagrams sometimes show one appliance performing several roles.

## 13. Forward proxy

Used by clients for outbound access:

```text
Employee browser -> corporate proxy -> Internet
```

Benefits can include:

- URL/domain policy,
- authentication/user attribution,
- malware scanning,
- logging,
- controlled egress addresses.

## 14. Reverse proxy

Used by servers/applications:

```text
Internet client -> reverse proxy -> backend server
```

The client thinks it is talking to the service endpoint. The reverse proxy can terminate TLS, route by hostname/path, enforce headers, cache, and shield origin topology.

ALB and CloudFront can participate in reverse-proxy-like roles at different scopes.

## 15. IDS vs IPS

### IDS
Detects/alerts on suspicious traffic.

### IPS
Can actively block/drop traffic based on detection rules/policy.

Modern managed firewalls can provide both inspection and prevention behavior.

AWS Network Firewall includes stateless and stateful rule engines and supports Suricata-compatible stateful rules. Deployment design must route traffic through the firewall endpoints.

## 16. Why symmetric routing matters to stateful firewalls

Stateful inspection wants to observe both directions of the same flow.

Bad topology:

```text
request -> firewall A -> server
response -> different path bypassing firewall A
```

The return path can look unrelated/invalid to stateful inspection systems or bypass intended policy entirely. A firewall that sees only the SYN-ACK has no state proving that it allowed the initiating SYN.

AWS Network Firewall documentation calls out symmetric routing requirements, particularly in centralized Transit Gateway inspection designs.

### Centralized inspection dry run

Assume Spoke A calls Spoke B through a Transit Gateway and an inspection VPC:

```text
Spoke A
  -> Transit Gateway
  -> inspection attachment / firewall endpoint
  -> Transit Gateway
  -> Spoke B
```

| Step | Expected path/state | Failure if asymmetric |
|---:|---|---|
| 1 | SYN from A is routed through firewall endpoint X | X creates flow state |
| 2 | SYN reaches B | B replies to A's ephemeral port |
| 3 | Return route sends SYN-ACK back through endpoint X | X matches reverse tuple and allows it |
| 4 | TGW returns traffic to A | TCP handshake completes |

If the return uses endpoint Y or bypasses inspection, Y has no matching state and may drop the SYN-ACK; bypass also defeats the intended inspection policy. Transit Gateway appliance mode is designed to keep a flow on the same appliance network interface for its lifetime in supported centralized designs. Appliance mode does not repair incorrect route tables: forward and return routes must still intentionally traverse the inspection attachment.

### NAT changes what the firewall sees

The observation point matters:

```text
before source NAT: 10.0.1.25:51514 -> 198.51.100.20:443
after source NAT:  203.0.113.10:62001 -> 198.51.100.20:443
```

A rule or log on the pre-NAT side may reference the private tuple; a post-NAT device may see the translated tuple. During troubleshooting, draw the tuple at **each boundary** instead of searching every log for one unchanged address.

### Four failure examples

1. The Security Group permits outbound 443, but the custom NACL blocks inbound replies to the client ephemeral port.
2. The forward flow crosses Network Firewall endpoint X; the return crosses Y because appliance mode/routing is wrong, so stateful inspection drops it.
3. A NAT mapping expires during a long idle period; a later packet no longer maps to the private client.
4. Policy allows `ALB-SG -> APP-SG:8080`, but testing directly from an admin host fails because that source relationship is intentionally absent.

> **Stateful invariant:** every packet accepted as part of an existing flow must match live state created by an allowed initiating direction, and both directions must traverse the state owner for as long as that state is required.

**Common mistakes:** opening server port 443 in both directions instead of following the reverse ephemeral destination; confusing Security Group state with NACL statelessness; assuming one successful packet proves route symmetry; ignoring state expiry; reading post-NAT logs as if they contain the original tuple; enabling a firewall without routing traffic through it.

## 17. Firewall rule examples: good vs bad

### Too broad

```text
ALLOW TCP 0-65535 FROM 0.0.0.0/0 TO app-network
```

### Better intent

```text
Internet -> public reverse proxy/WAF :443
reverse proxy -> app tier :8080
app tier -> DB tier :5432
app tier -> approved egress proxy :443
```

The second design expresses the **relationship between tiers**, making lateral movement and accidental exposure harder.

## 18. Security Group referencing in AWS

Instead of hardcoding transient instance IPs:

```text
ALB-SG -> APP-SG TCP 8080
APP-SG -> DB-SG TCP 5432
```

This expresses architectural intent. Instances can scale/change IPs without rewriting network policy around each host.

## 19. NACL use case

NACLs can provide subnet-level coarse guardrails/explicit deny capability.

They are not usually the best place to express every application relationship because:

- they are subnet-scoped,
- stateless,
- rule-number ordered,
- return ephemeral ports must be considered.

Security Groups are normally the primary workload-level control in many VPC designs.

## 20. AWS Network Firewall

Use when you need managed network inspection such as:

- centralized egress filtering,
- intrusion prevention,
- domain-based controls,
- stateless/stateful policy,
- inspection across VPC/on-prem paths.

It is inserted through routing; creating a firewall without routing traffic through its endpoints does not inspect that traffic.

## 21. AWS WAF

Use at supported Layer-7 resources for HTTP/S protections such as:

- managed rule groups,
- IP/rate rules,
- SQL injection/XSS patterns,
- URI/header/query/body matching.

It does not replace Security Groups for an EC2 database port or NACLs for subnet guardrails.

## 22. Legacy inbound HTTPS flow

```text
Public DNS
 -> enterprise public VIP
 -> border router
 -> perimeter firewall permits 443
 -> WAF/reverse proxy terminates TLS
 -> load balancer chooses backend
 -> internal firewall permits proxy/LB to app port
 -> app
```

Each arrow can have a distinct owner and log source.

## 23. Legacy outbound API flow

```text
App server
 -> internal route
 -> egress firewall/proxy
 -> source NAT to public IP
 -> ISP
 -> vendor API
```

If vendor requires IP allowlisting, the enterprise stabilizes one/few egress public addresses.

AWS equivalent often becomes:

```text
private app -> NAT Gateway EIP -> IGW -> vendor
```

possibly with Network Firewall/proxy inspection inserted.

## 24. Troubleshooting a timeout across firewalls

Check in order:

```text
1 route reaches firewall?
2 firewall policy matches correct src/dst/proto/port?
3 NAT occurs where expected?
4 route after firewall reaches destination?
5 destination host firewall allows?
6 process listens?
7 return route comes back through expected stateful path?
8 translated return packet maps correctly?
```

Logs should prove each step; do not “temporarily allow all” in production as a default debugging method.

## 25. Evidence sources

Legacy:

- firewall session table,
- firewall traffic/deny logs,
- router route table,
- NAT translation table,
- load balancer logs,
- packet capture/tcpdump,
- proxy logs.

AWS:

- VPC Flow Logs,
- Network Firewall flow/alert logs,
- ALB access logs/metrics,
- WAF logs,
- Reachability Analyzer,
- route tables,
- Security Group/NACL configuration.

VPC Flow Logs can record `ACCEPT` or `REJECT` metadata, but they are not full packet payload captures and do not replace application logs.

## 26. Best references

- Cloudflare firewall overview: https://www.cloudflare.com/learning/security/what-is-a-firewall/
- AWS Security Group connection tracking: https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-connection-tracking.html
- AWS custom NACLs and ephemeral ports: https://docs.aws.amazon.com/vpc/latest/userguide/custom-network-acl.html
- AWS SG/NACL flow-log example: https://docs.aws.amazon.com/vpc/latest/userguide/flow-logs-records-examples.html
- AWS Network Firewall how it works: https://docs.aws.amazon.com/network-firewall/latest/developerguide/how-it-works.html
- AWS Network Firewall rule engines: https://docs.aws.amazon.com/network-firewall/latest/developerguide/firewall-rules-engines.html
- AWS Network Firewall symmetric-routing troubleshooting: https://docs.aws.amazon.com/network-firewall/latest/developerguide/troubleshooting-general-issues.html
- AWS Transit Gateway appliance mode: https://docs.aws.amazon.com/vpc/latest/tgw/tgw-vpc-attachments.html#appliance-mode
- AWS perimeter-zone migration architecture: https://docs.aws.amazon.com/prescriptive-guidance/latest/migration-perimeter-zone-apps-network-firewall/architecture.html

## 27. Memory trick

```text
Route asks: where next?
Firewall asks: allowed?
NAT asks: what address/port should be rewritten?
Proxy asks: can I make the request on your behalf?
Load balancer asks: which backend?
WAF asks: is this HTTP request acceptable?
```


---



[← Previous](06-dns-tcp-tls-http.md) · [Chapter index](index.md) · [Next →](08-legacy-datacenter-networking.md)
