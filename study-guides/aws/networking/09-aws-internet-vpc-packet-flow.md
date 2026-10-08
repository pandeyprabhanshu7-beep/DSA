# AWS Internet & VPC Packet Flow — From Browser to Private Application and Back




![AWS ingress request path](assets/diagrams/networking/09-aws-ingress.svg)

![AWS private-subnet egress path](assets/diagrams/networking/10-aws-egress.svg)

## 1. The AWS networking idea that removes most confusion

Do not ask only:

> “Is the subnet public?”

Ask this complete set:

```text
What IP does the resource have?
Which route table applies?
What target does the best route select?
What translation occurs, if any?
Which stateful/stateless policies apply?
What is the return path?
What endpoint is actually listening?
```

A VPC is software-defined networking, but packet reachability still depends on those ordinary networking questions.

## 2. Core objects and their responsibilities

| AWS object | Mental model | It does NOT automatically do |
|---|---|---|
| VPC | regional private routing/addressing boundary | make resources public |
| Subnet | AZ-scoped IP allocation/routing association | become public because named “public” |
| ENI | virtual network interface with IP/security associations | guarantee route to Internet |
| Route table | destination prefix → target decision | grant firewall permission |
| Internet Gateway | VPC Internet-routing edge; public IPv4 mapping behavior | assign app auth or open ports |
| NAT Gateway | source translation for initiated private IPv4 egress | accept unsolicited inbound sessions to private instances |
| Security Group | stateful allow policy on supported resource interfaces | explicit deny rules like NACL |
| Network ACL | stateless subnet-level allow/deny rules | remember connection return state |
| VPC endpoint | private connectivity to supported service | make every Internet service private |
| Transit Gateway | regional transit routing hub | inspect traffic by itself |
| Network Firewall | managed traffic inspection via routed endpoints | inspect traffic not routed through it |
| Route 53/VPC Resolver | DNS/name resolution | create IP reachability by itself |

## 3. What makes an AWS subnet “public”?

AWS documentation describes a subnet whose associated route table has a route to an Internet Gateway as a public subnet.

Typical IPv4 public route table:

```text
10.0.0.0/16  -> local
0.0.0.0/0    -> igw-1234
```

But an EC2 instance also needs suitable public addressing and security rules to be directly reachable from the Internet over IPv4.

### A route to IGW is necessary, not sufficient

Bad mental model:

```text
public subnet -> every instance exposed
```

Better:

```text
subnet route can reach IGW
+ resource has public IPv4/EIP if direct IPv4 Internet identity required
+ SG/NACL allow desired traffic
+ service listens
+ return route/state works
```

## 4. EC2 public IPv4: what the guest actually sees

AWS documents an important detail: an EC2 public IPv4 is mapped to the primary private IPv4 through NAT. The guest OS normally sees its private IPv4 on the network interface, not the public IPv4 as another ordinary local NIC address.

Example:

```text
EC2 guest sees:       10.0.1.25
AWS console shows:    public 203.0.113.25 (example)
Internet client uses: 203.0.113.25
IGW mapping delivers: private 10.0.1.25 inside VPC
```

This is why a process can listen on `0.0.0.0:443`/private interface and receive traffic sent to the associated public address through the VPC Internet path.

## 5. Direct public EC2 ingress flow

Simplified example:

```text
Internet client
 -> Internet routing/AWS edge
 -> Internet Gateway
 -> public IPv4 mapping to instance private IPv4
 -> subnet/NACL boundary
 -> ENI/Security Group
 -> EC2 operating system
 -> host firewall
 -> application listener
```

Return traffic must pass the reverse policy/path.

This pattern is valid for selected use cases but is usually not the preferred architecture for a scalable public web application; load balancers/CDNs provide better abstraction and operational controls.

## 6. Recommended multi-tier public web flow

```text
Browser
  ↓
Route 53 DNS
  ↓
CloudFront (optional CDN/edge)
  ↓
AWS WAF (often associated at edge/ALB/API surface)
  ↓
Application Load Balancer in public subnets
  ↓
APP security group relationship
  ↓
EC2/ECS/EKS targets in private app subnets
  ↓
DB security group relationship
  ↓
RDS/Aurora in isolated/private DB subnets
```

The app instances do not need public IPv4 addresses merely because end users on the Internet use the application.

## 7. Step-by-step ingress dry run

Assume:

```text
VPC              10.0.0.0/16
Public subnet A  10.0.1.0/24
Public subnet B  10.0.2.0/24
App subnet A     10.0.11.0/24
App subnet B     10.0.12.0/24
DB subnet A      10.0.21.0/24
DB subnet B      10.0.22.0/24
```

Security intent:

```text
Internet -> ALB-SG TCP 443
ALB-SG   -> APP-SG TCP 8080
APP-SG   -> DB-SG  TCP 5432
```

### Step A — DNS
`app.example.com` resolves to CloudFront/ALB-related endpoint information according to design.

### Step B — Internet reaches AWS edge/public endpoint
The user's ISP and Internet routing deliver traffic toward the AWS-advertised prefix/edge.

### Step C — optional CloudFront/WAF
CloudFront may terminate client TLS/cache/forward to origin. WAF evaluates HTTP requests where associated.

### Step D — ALB public listener
ALB listener accepts HTTPS 443. `ALB-SG` permits client ingress.

### Step E — target selection
ALB chooses a healthy target from its target group, then initiates/uses backend connectivity to the private app IP/port.

### Step F — app SG
`APP-SG` permits TCP 8080 **from ALB-SG**, not from the whole Internet.

### Step G — app to database
Application opens a separate DB connection to port 5432. `DB-SG` permits source `APP-SG`.

This is three separate transport/security relationships, not one giant browser-to-database connection.

## 8. Why the app subnet can be private yet ALB can reach it

Private does not mean “isolated from the VPC.”

The VPC local route provides connectivity among subnets in the VPC, subject to security policies and routing nuances.

```text
10.0.0.0/16 -> local
```

Therefore public ALB subnets can route to private app subnets without giving app instances public Internet addresses.

## 9. Private IPv4 egress through NAT Gateway

App `10.0.11.25` needs `https://api.vendor.example`.

Private route table:

```text
10.0.0.0/16 -> local
0.0.0.0/0   -> nat-gw-a
```

NAT Gateway lives in a public subnet with Internet route/EIP context.

Flow:

```text
10.0.11.25:51514
 -> private route table
 -> NAT Gateway
 -> source translated to NAT EIP/port
 -> public subnet route/IGW
 -> Internet
 -> vendor:443
```

AWS docs state that the NAT device replaces the source IPv4 address and maps responses back while not accepting unsolicited Internet connections to the private resources.

## 10. NAT Gateway is AZ-scoped architecture-wise

For resilient multi-AZ designs, a common pattern is one NAT Gateway per AZ with private subnets routing to the NAT in the same AZ.

Why?

- avoid one-AZ dependency for all egress,
- reduce unnecessary cross-AZ traffic patterns/cost,
- keep failure domains aligned.

One NAT for the whole VPC may be cheaper for small/noncritical environments but carries different resilience/cost trade-offs.

## 11. VPC endpoints: avoid the NAT/Internet-style path for supported AWS services

For supported services, endpoints can provide private connectivity.

Example S3 gateway endpoint concept:

```text
private EC2 -> route/prefix-list endpoint -> S3
```

instead of:

```text
private EC2 -> NAT GW -> IGW/public service endpoint path
```

Benefits can include lower NAT dependence, tighter policy, and reduced exposure/cost depending on service/traffic pattern.

### Interface endpoints / PrivateLink
Interface endpoints create private ENIs/endpoints in your VPC for supported services/private services. Private DNS can make the standard service name resolve to the private endpoint addresses in appropriate configurations.

## 12. IPv6 Internet access

AWS IPv6 addresses are globally unique. For public IPv6 Internet access, routes can point `::/0` to an Internet Gateway when inbound/outbound reachability is desired and policy permits.

For private-subnet-style outbound-only IPv6:

```text
::/0 -> egress-only Internet Gateway
```

AWS describes the egress-only IGW as stateful: it permits outbound IPv6 communication and return traffic while preventing Internet hosts from initiating connections to those instances.

## 13. Security Groups: think relationships, not perimeter only

Good three-tier policy:

```text
ALB-SG inbound 443 from Internet/client ranges
APP-SG inbound 8080 from ALB-SG
DB-SG  inbound 5432 from APP-SG
```

This is micro-segmentation at the workload interface level.

Security Groups are stateful; response traffic for allowed flows is automatically recognized by the stateful model.

## 14. NACLs: subnet guardrail and the return-traffic problem

NACLs are stateless and evaluated separately for inbound/outbound packets.

If you allow:

```text
inbound TCP 443
```

but deny relevant reverse ephemeral traffic, sessions fail.

AWS's own Flow Logs examples use ping/security-rule scenarios to demonstrate SG statefulness vs NACL statelessness.

## 15. Route tables and Security Groups solve different questions

```text
Route table: WHERE should packet go?
Security Group: MAY this flow pass this interface/resource boundary?
```

A perfect route plus blocked SG = no application connection.

An open SG plus missing route = no application connection.

Never troubleshoot them as one concept.

## 16. NACL and SG evaluation mental path

A simplified inbound path can include both subnet-level NACL and ENI-associated Security Group behavior. Exact distributed implementation should not be imagined as literal hardware boxes in a fixed rack order, but operationally both policy layers must permit the flow according to their semantics.

## 17. AWS Network Firewall insertion

Network Firewall creates endpoints in dedicated subnets/AZs. To inspect traffic, route tables must steer flows through those endpoints.

Possible centralized egress path:

```text
Spoke VPC
 -> Transit Gateway
 -> Inspection VPC
 -> Network Firewall endpoint
 -> NAT/egress path
 -> Internet
```

Stateful inspection requires carefully designed symmetric paths. AWS documentation explicitly warns about routing symmetry in centralized inspection designs.

## 18. Gateway Load Balancer (GWLB)

GWLB enables scalable insertion of compatible virtual network appliances from vendors or your own appliance fleet. Think of it as a service-insertion/load-distribution primitive for Layer-3 virtual appliances, not as a web ALB replacement.

Legacy mapping:

```text
physical firewall appliance cluster
 -> virtual appliance fleet behind GWLB
```

## 19. Transit Gateway packet flow

Spoke A needs Spoke B:

```text
VPC A subnet route: 10.2.0.0/16 -> TGW
TGW route table:    10.2.0.0/16 -> VPC B attachment
VPC B subnet route: return path -> TGW
```

There are **two route-table systems** to reason about:

- VPC subnet route tables,
- Transit Gateway route tables.

Missing either direction can break connectivity.

## 20. VPC Peering

Peering gives private routed connectivity between VPCs, subject to route/security configuration. It is not transitive: if A peers with B and B peers with C, A does not automatically route through B to C via those peerings.

This limitation is one reason Transit Gateway becomes attractive at larger scale.

## 21. Hybrid on-prem to AWS through VPN/Direct Connect

Conceptual path:

```text
On-prem app
 -> enterprise router/firewall
 -> VPN or Direct Connect
 -> VGW/TGW/DXGW design
 -> VPC route
 -> EC2/private load balancer
```

You need:

- non-overlapping/managed address space,
- routes advertised/propagated correctly,
- Security Groups/NACLs/firewalls,
- DNS resolution across environments,
- return routing,
- MTU considerations,
- redundancy.

## 22. Route 53 Resolver hybrid DNS

Network connectivity does not automatically solve private names.

Example:

```text
EC2 can ping 10.50.1.10 on-prem
but cannot resolve db.corp.example
```

Add/verify Resolver outbound endpoints/rules toward on-prem DNS, plus inbound endpoints if on-prem clients must resolve AWS private hosted-zone names.

## 23. VPC Flow Logs

Flow Logs capture metadata about IP flows for ENIs/subnets/VPCs depending configuration. Fields can include source/destination, ports, protocol, bytes/packets, interface and action such as `ACCEPT`/`REJECT`.

They answer:

```text
Did network policy accept/reject this flow metadata?
Did traffic reach this ENI/subnet observation point?
```

They do not show:

```text
HTTP request body
SQL statement
full packet payload
application exception
```

## 24. Reachability Analyzer

Reachability Analyzer performs static configuration analysis; it does not send test packets. It can identify a reachable configuration path or blocking component among supported AWS networking resources.

Use it before randomly editing route tables/SGs.

## 25. Flow Logs + Reachability Analyzer + app logs = layered evidence

A strong incident workflow:

```text
Reachability Analyzer: should config permit a path?
VPC Flow Logs: what flows were accepted/rejected/observed?
ALB/Network Firewall/WAF logs: what did intermediate services do?
OS tcpdump/socket tools: did packets reach host/process?
App logs/traces: what did the application do?
```

No single tool replaces all layers.

## 26. Scenario: private EC2 cannot download patches

Check:

```text
1 DNS resolves?
2 private subnet route 0.0.0.0/0 -> NAT?
3 NAT exists/available in proper public subnet/AZ design?
4 NAT subnet route 0.0.0.0/0 -> IGW?
5 NAT has required public connectivity/EIP context?
6 SG egress permits?
7 NACL allows both directions/ephemeral ports?
8 destination/IPv4 path available?
```

If calling S3, ask whether an S3 endpoint should replace NAT for that traffic.

## 27. Scenario: ALB is reachable but targets unhealthy

This proves the client-to-ALB path is at least partly working. Check the separate backend boundary:

```text
ALB target group port/protocol
ALB-SG -> APP-SG
NACLs
app process listening on expected IP/port
health-check path/status
application startup/dependency failures
```

## 28. Scenario: EC2 has public IP but Internet cannot connect

Possible causes:

- subnet route table lacks `0.0.0.0/0 -> IGW`,
- IGW not attached,
- SG inbound does not allow port/source,
- NACL blocks,
- host firewall blocks,
- service not listening,
- wrong public IP/DNS,
- IPv6 vs IPv4 mismatch,
- routing through an inspection architecture incorrectly.

Public IP alone is not enough.

## 29. Scenario: private app can call most sites but one vendor fails

Possible non-AWS-network causes:

- vendor allowlist missing NAT Gateway EIP,
- TLS SNI/certificate/protocol mismatch,
- vendor geo/ASN/security policy,
- DNS answer differs,
- rate limit,
- MTU/path issue,
- vendor outage.

First record the NAT egress public IP and exact `curl -v`/TLS symptom.

## 30. Legacy → AWS packet-path mapping

```text
Legacy:
server NIC -> VLAN -> core route -> firewall/NAT -> edge router -> ISP

AWS private egress:
ENI -> subnet route table -> NAT Gateway -> IGW -> Internet/AWS edge
```

```text
Legacy ingress:
ISP -> edge router -> firewall -> DMZ WAF/LB -> app VLAN

AWS ingress:
Internet/AWS edge -> CloudFront/WAF -> ALB -> SG -> private app
```

## 31. Best official sources

- Internet Gateway: https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html
- NAT Gateway scenarios: https://docs.aws.amazon.com/vpc/latest/userguide/nat-gateway-scenarios.html
- VPC NAT devices: https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat.html
- Route priority: https://docs.aws.amazon.com/vpc/latest/userguide/route-tables-priority.html
- VPC Resolver: https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/resolver.html
- Egress-only IGW: https://docs.aws.amazon.com/vpc/latest/userguide/egress-only-internet-gateway.html
- VPC Flow Logs: https://docs.aws.amazon.com/vpc/latest/userguide/flow-logs.html
- Reachability Analyzer: https://docs.aws.amazon.com/vpc/latest/reachability/what-is-reachability-analyzer.html
- Network Firewall architectures: https://docs.aws.amazon.com/network-firewall/latest/developerguide/architectures.html

## 32. What you must remember

```text
Public subnet = route-table property/path, not a name.
Public IPv4 alone ≠ reachable.
Route ≠ permission.
SG stateful; NACL stateless.
Private app can receive traffic from public ALB without public IP.
Private IPv4 Internet egress commonly uses NAT Gateway.
IPv6 outbound-only can use egress-only IGW.
Endpoints can avoid NAT for supported AWS-service traffic.
Network Firewall only inspects traffic routed through it.
Flow Logs show flow metadata, not application payload.
```


---



[← Previous](08-legacy-datacenter-networking.md) · [Chapter index](index.md) · [Next →](10-network-troubleshooting-labs.md)
