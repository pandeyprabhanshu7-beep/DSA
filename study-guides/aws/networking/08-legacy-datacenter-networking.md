# Legacy / In-house Data Center Networking: Complete Flow Before Cloud




![Legacy data center network](assets/diagrams/networking/08-legacy-dc.svg)

## 1. Why learn the legacy design first?

AWS did not invent IP routing, subnets, firewalls, load balancing, DNS, NAT, private connectivity, or segmentation. Cloud services mostly make these capabilities programmable and managed at a different operational layer.

If you understand the old data-center path, VPC architecture becomes a mapping exercise rather than memorization.

## 2. A representative enterprise Internet-facing architecture

```text
Users
  |
Internet
  |
ISP A -------- ISP B
  |              |
Edge Router A  Edge Router B
       \        /
        HA Firewall Pair
             |
          DMZ VLAN
     WAF / Reverse Proxy
      Load Balancer VIP
             |
       Internal Firewall
             |
          App VLAN
             |
       Internal Firewall
             |
           DB VLAN
```

Real environments vary widely. Some combine WAF/LB/firewall functions; others separate them into dedicated appliance clusters.

## 3. The physical layer people forget

A data center also requires:

- carrier circuits/fiber cross-connects,
- patch panels,
- top-of-rack switches,
- core/distribution switches,
- redundant power,
- optics/transceivers,
- rack space,
- spare ports/capacity,
- out-of-band management.

Cloud removes much direct hardware operation from application teams, but networking constraints still exist logically.

## 4. Carrier/ISP edge

An enterprise can connect to one or multiple ISPs.

Small design:

```text
enterprise router -> one ISP -> default route
```

Larger resilient design:

```text
         ISP A
          |
      Edge Router A
         \ /
      Enterprise
         / \
      Edge Router B
          |
         ISP B
```

Public BGP may be used when the organization has the addressing/ASN/design requirements to advertise prefixes through multiple providers.

## 5. Public IP ownership and publishing services

A company might receive/provider-assign public addresses or own provider-independent address space. It then exposes services through:

- static NAT,
- firewall VIPs,
- load-balancer virtual IPs,
- reverse proxies,
- public DNS.

Example:
```text
www.corp.example -> 203.0.113.50
```

At the edge:

```text
203.0.113.50:443 -> load balancer/WAF tier
```

The backend server can remain on private RFC1918 addressing.

## 6. VLANs and subnets

A typical segmentation plan:

```text
DMZ VLAN       10.10.10.0/24
Web VLAN       10.10.20.0/24
App VLAN       10.10.30.0/24
DB VLAN        10.10.40.0/24
Management     10.10.50.0/24
Backup         10.10.60.0/24
```

VLAN is a Layer-2 segmentation mechanism; subnet is an IP Layer-3 concept. They are often mapped one-to-one operationally but are not definitionally the same thing.

## 7. Default gateway and first-hop redundancy

Servers in a VLAN need a default gateway, often provided by redundant switches/routers/firewalls using a first-hop redundancy mechanism or clustered virtual gateway.

Example server:

```text
IP:      10.10.30.25/24
Gateway: 10.10.30.1
```

The virtual gateway can survive one physical network device failing.

## 8. Core/distribution/access networking

Classic campus/data-center hierarchy:

```text
servers
  ↓
access / top-of-rack switches
  ↓
distribution/aggregation
  ↓
core
  ↓
firewall/WAN/Internet
```

Modern leaf-spine fabrics flatten this topology, but the reasoning remains: many server links aggregate into redundant high-capacity routed paths.

## 9. DMZ/perimeter zone

Internet-facing components are isolated from trusted internal networks.

Why?

If the public web server is compromised, the attacker should not automatically receive unrestricted Layer-3 access to database/admin networks.

Policy can be:

```text
Internet -> WAF/LB :443 only
WAF/LB -> web/app :8080 only
app -> DB :5432 only
admin -> management :approved protocols only
```

## 10. Traditional firewall pair

Enterprises often run two firewalls for HA:

```text
Firewall A active
Firewall B standby
```

or active/active designs depending platform/topology.

Operational work includes:

- rule changes/tickets,
- NAT rules,
- software upgrades,
- failover tests,
- state synchronization,
- hardware replacement,
- interface/VLAN configuration,
- capacity planning,
- logging/SIEM integration.

AWS managed networking removes some appliance lifecycle work but replaces it with route/policy/service design responsibilities.

## 11. Load balancer appliance

A legacy load balancer commonly exposes a VIP:

```text
VIP 10.10.10.50:443
```

Backend pool:

```text
10.10.20.11:8080
10.10.20.12:8080
10.10.20.13:8080
```

It monitors health and chooses a backend. L7 devices can also route by hostname/path and terminate TLS.

AWS mapping:

```text
F5/NetScaler/HAProxy cluster -> ALB/NLB depending requirement
```

## 12. Internal DNS

Corporate DNS often handles both:

- public zones or delegation relationships,
- internal-only names such as `db01.prod.corp.local`/enterprise domains.

Clients point at corporate recursive resolvers, often integrated with Active Directory in Windows-heavy environments.

Hybrid cloud introduces split resolution problems:

```text
on-prem clients must resolve AWS private names
AWS workloads must resolve on-prem private names
```

Route 53 Resolver endpoints/rules solve the DNS forwarding part; VPN/DX/TGW solve the network reachability part.

## 13. Inbound web request: complete legacy flow

Assume user requests:

```text
https://www.corp.example/orders
```

### Step 1: DNS
Public authoritative DNS returns `203.0.113.50`.

### Step 2: Internet routing
BGP/Internet routing carries traffic toward the enterprise's provider/edge.

### Step 3: edge router
Enterprise edge receives the public-prefix traffic and forwards according to internal edge routing.

### Step 4: perimeter firewall
Firewall permits TCP 443 for the published service and may perform NAT/VIP forwarding.

### Step 5: WAF/reverse proxy
Inspects HTTP/TLS according to architecture; can block web attacks.

### Step 6: load balancer
Chooses a healthy application target.

### Step 7: internal routing/firewall
Traffic crosses into application VLAN only on approved port.

### Step 8: application
App processes business logic.

### Step 9: DB flow
App opens a separate connection to DB network, e.g. TCP 5432.

### Step 10: response
Return traffic follows valid routes/state through the environment and back to the user.

## 14. Outbound app-to-SaaS flow

App `10.10.30.25` calls vendor API `api.vendor.example:443`.

```text
App
 -> default gateway
 -> internal/core routing
 -> egress firewall
 -> source NAT
 -> ISP
 -> Internet/BGP
 -> vendor edge
```

The vendor may allowlist enterprise public NAT addresses.

Problem during migration:

```text
on-prem egress IP is allowlisted
AWS NAT Gateway EIP is not
```

The cloud app can resolve DNS and reach the Internet but gets HTTP 403 or connection rejection from vendor policy. That is not necessarily an AWS routing failure.

## 15. East-west app-to-DB flow

```text
App 10.10.30.25:ephemeral
 -> route to 10.10.40.0/24
 -> internal firewall policy
 -> DB 10.10.40.10:5432
```

The DB does not need Internet access to serve internal clients.

This maps nicely to AWS:

```text
APP-SG -> DB-SG :5432
DB subnet no default Internet route
```

## 16. Corporate user to internal app

A user in an office may traverse:

```text
Laptop
 -> access switch/Wi-Fi
 -> campus gateway
 -> WAN/MPLS/SD-WAN
 -> data center firewall
 -> internal load balancer
 -> app
```

If remote:

```text
Laptop
 -> home ISP
 -> corporate VPN gateway
 -> internal network
 -> app
```

This is the conceptual predecessor to Client VPN, Site-to-Site VPN, Direct Connect, Zero Trust/private access products, and hybrid cloud routing.

## 17. MPLS/private WAN legacy model

Enterprises historically connected offices/data centers using carrier private WANs such as MPLS L3VPN services.

Conceptually:

```text
Branch A -> carrier private WAN -> Data Center
Branch B -> carrier private WAN -> Data Center
```

Cloud migration adds AWS as another site through VPN/Direct Connect, or shifts to Internet/SD-WAN-centric designs.

## 18. Two data centers for DR

Legacy resiliency might use:

```text
Primary DC
Secondary DR DC
```

with:

- replicated databases/storage,
- DNS/global load-balancing failover,
- WAN replication links,
- duplicated firewall/LB infrastructure,
- runbooks to activate DR.

Cloud Regions/AZs change implementation but not the core questions: failure domain, RTO, RPO, data replication, traffic failover, capacity.

## 19. Change management in legacy networking

A simple requirement like “allow app A to call database B” can become:

```text
1 submit firewall ticket
2 identify exact source subnet
3 identify destination VIP/server
4 identify port/protocol
5 security review
6 change window
7 implement ACL/firewall rule
8 test
9 update CMDB/documentation
```

Cloud infrastructure-as-code can make these changes faster/repeatable, but careless automation can also reproduce bad policy instantly. Automation does not remove the need for correct intent.

## 20. Common legacy failure scenarios

### Wrong VLAN
Server connected/tagged into wrong broadcast domain.

### Duplicate IP
Two hosts use same address.

### Bad mask/gateway
Local/remote decisions fail.

### Firewall asymmetric routing
Request and reply cross different firewalls without shared state.

### NAT shadowing/order
Wrong translation rule matches before intended rule.

### Stale load-balancer pool
VIP exists but backends unhealthy.

### DNS stale record
Hostname points to retired VIP.

### Route leak/advertisement mistake
Traffic follows wrong WAN/ISP path.

### MTU mismatch through VPN
Small traffic succeeds; larger flows fail.

## 21. Legacy → AWS mapping table

| Legacy data center | AWS service/primitive |
|---|---|
| IP address management spreadsheet/IPAM appliance | VPC IPAM |
| VLAN/subnet | VPC subnet (not exactly a VLAN) |
| core router | distributed VPC routing / Transit Gateway for hub patterns |
| Internet edge router | Internet Gateway + AWS edge routing |
| firewall pair | Security Groups/NACLs and/or Network Firewall/appliances |
| NAT appliance | NAT Gateway |
| WAF appliance | AWS WAF |
| F5/ADC load balancer | ALB/NLB/GWLB depending need |
| DNS appliance/service | Route 53 + VPC Resolver |
| MPLS/private circuit | Direct Connect / carrier integration |
| IPsec concentrator | Site-to-Site VPN |
| firewall traffic logs | VPC Flow Logs / Network Firewall logs |
| network path testing | Reachability Analyzer + conventional tools |

## 22. What does not map one-to-one

Do not say:

```text
VPC = VLAN
Security Group = firewall appliance
Internet Gateway = physical router
```

These analogies help intuition but implementations differ. AWS constructs are distributed managed services with cloud-specific semantics.

## 23. The best migration question

For every legacy component ask:

```text
What responsibility did the appliance/team own?
What AWS service now owns part of that responsibility?
What responsibility still belongs to me?
```

Example NAT appliance:

```text
Old responsibilities:
hardware HA, patch OS, scale appliance, NAT rules, routing, logs

NAT Gateway removes:
OS/hardware management and much HA/scaling implementation

You still own:
subnet/route design, AZ resilience, EIP/vendor allowlists,
cost, monitoring, connection behavior, security/egress policy
```

## 24. Reference trail

- AWS perimeter-zone migration architecture: https://docs.aws.amazon.com/prescriptive-guidance/latest/migration-perimeter-zone-apps-network-firewall/architecture.html
- AWS Security Reference Architecture network guidance: https://docs.aws.amazon.com/prescriptive-guidance/latest/security-reference-architecture/network.html
- AWS hybrid networking: https://docs.aws.amazon.com/whitepapers/latest/aws-vpc-connectivity-options/welcome.html

## 25. Memory map

```text
Legacy Internet app:
DNS -> ISP/BGP -> edge router -> firewall/NAT -> DMZ/WAF/LB
    -> app VLAN -> internal firewall -> DB VLAN

AWS Internet app:
Route 53 -> AWS edge/CloudFront/WAF -> IGW/public ingress
    -> ALB -> SG -> private app -> SG -> DB
```


---



[← Previous](07-ingress-egress-firewalls-dmz.md) · [Chapter index](index.md) · [Next →](09-aws-internet-vpc-packet-flow.md)
