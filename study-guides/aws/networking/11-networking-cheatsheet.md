# Networking Cheat Sheet, Memory Map & Interview Questions



[← Troubleshooting labs](10-network-troubleshooting-labs.md) · [Bootcamp home](../index.md)

## 1. One-page mental map

```text
NAME
DNS
  ↓
ADDRESS
IP + subnet/prefix
  ↓
LOCAL DELIVERY
MAC / Wi-Fi / Ethernet / ARP or IPv6 ND
  ↓
NEXT HOP
routing table / default gateway / longest prefix
  ↓
NETWORK-TO-NETWORK
ISP / AS / BGP / peering / transit
  ↓
BOUNDARY
NAT / firewall / proxy / load balancer
  ↓
TRANSPORT
TCP or QUIC/UDP, ports
  ↓
SECURITY
TLS
  ↓
APPLICATION
HTTP/API/database protocol
```

## 2. Fast definitions

| Term | Remember this |
|---|---|
| IP | Layer-3 addressing/routed delivery |
| subnet/prefix | block of IP addresses sharing leading prefix bits |
| MAC | local-link interface identifier used for frame delivery |
| ARP | IPv4 local neighbor IP→MAC resolution |
| default gateway | next hop for destinations without a more specific local route |
| route table | destination prefix → next hop/interface |
| longest prefix | most specific matching route wins |
| NAT | rewrite address information |
| PAT/NAPT | translate ports too, allowing address sharing |
| CGNAT | provider-scale NAT; shared `100.64.0.0/10` often used internally |
| ASN | autonomous-system number used in interdomain routing context |
| BGP | policy-driven route exchange between routing domains |
| peering | networks exchange traffic/routes directly under agreement |
| transit | upstream provides broader Internet reachability |
| DNS | names → records such as A/AAAA |
| TCP | reliable ordered byte stream with connection state |
| UDP | datagram transport without TCP's connection/reliability semantics |
| TLS | cryptographic authentication/confidentiality/integrity layer |
| WAF | HTTP-aware Layer-7 request filtering |
| stateful firewall | remembers permitted flow state |
| stateless ACL | each packet/direction must independently match rules |
| ingress | traffic entering a stated boundary |
| egress | traffic leaving a stated boundary |
| north–south | across environment/perimeter boundary |
| east–west | between internal workloads/segments |

## 3. Home Internet flow

```text
Browser
 -> DNS
 -> laptop routing table
 -> Wi-Fi frame to default gateway
 -> home NAT/firewall
 -> modem/ONT/access network
 -> ISP
 -> peering/transit/IXP
 -> destination AS/CDN
 -> TLS endpoint
 -> web app
```

## 4. Mobile Internet flow

```text
Phone/UE
 -> 4G/5G RAN
 -> mobile transport
 -> packet core / UPF-style user plane
 -> carrier policy/NAT/Internet edge
 -> peering/transit
 -> destination
```

## 5. Legacy data-center ingress

```text
Internet
 -> ISP/BGP edge router
 -> HA firewall/NAT
 -> DMZ WAF/reverse proxy
 -> load balancer
 -> internal firewall
 -> app VLAN
 -> DB firewall
 -> DB VLAN
```

## 6. AWS web ingress

```text
Route 53
 -> CloudFront/WAF (optional)
 -> ALB public endpoint
 -> ALB-SG
 -> APP-SG/private app
 -> DB-SG/private DB
```

## 7. AWS private IPv4 egress

```text
private EC2/task
 -> private route table 0.0.0.0/0
 -> NAT Gateway
 -> public subnet route
 -> Internet Gateway
 -> Internet/vendor
```

## 8. Common port memory (learn context, not just numbers)

| Protocol/service | Common port |
|---|---:|
| HTTP | TCP 80 |
| HTTPS | TCP 443; HTTP/3 commonly QUIC/UDP 443 |
| SSH | TCP 22 |
| DNS | UDP/TCP 53; encrypted DNS uses other transports/ports such as HTTPS 443 |
| PostgreSQL | TCP 5432 |
| MySQL | TCP 3306 |
| Redis | commonly TCP 6379 |
| SMTP | TCP 25/587/465 depending usage |

A port number is convention, not proof. Applications can listen elsewhere.

## 9. Security Group vs NACL

| | SG | NACL |
|---|---|---|
| scope | resource/ENI-associated | subnet boundary |
| state | stateful | stateless |
| actions | allow rules | allow + deny |
| return flow | recognized automatically | must match reverse rules |
| common role | primary workload policy | coarse subnet guardrail |

## 10. Router vs NAT vs firewall vs proxy

```text
Router   -> WHERE next?
NAT      -> WHAT address/port changes?
Firewall -> MAY this traffic pass?
Proxy    -> I terminate/make request on behalf of another side.
LB       -> WHICH backend handles it?
DNS      -> WHAT address/record belongs to this name?
```

## 11. Troubleshooting order

```text
DNS
 ↓
IP/interface
 ↓
route/default gateway
 ↓
NAT/edge path
 ↓
firewall/security policy
 ↓
TCP/QUIC transport
 ↓
TLS
 ↓
HTTP/application
 ↓
downstream dependency
```

## 12. Symptom → clue

| Symptom | Strong clue |
|---|---|
| NXDOMAIN | DNS/name issue |
| connection timeout | route/drop/destination/return path possible |
| connection refused | reached something that actively rejected/no listener |
| TLS hostname error | network path reached TLS stage; name/cert mismatch |
| HTTP 403 | an HTTP component denied request; not raw IP routing failure |
| ALB 503 | frontend can be reachable while targets unavailable/unhealthy |
| DB auth error | network path to DB is likely much further along than a timeout |
| works `-4`, fails `-6` | IPv6 path/config issue |

## 13. CIDR shortcuts

```text
/24 = 256
/25 = 128
/26 = 64
/27 = 32
/28 = 16
/29 = 8
/30 = 4
/31 = 2
/32 = 1
```

Each additional prefix bit halves the block.

## 14. RFC1918 and CGNAT ranges

```text
Private IPv4:
10.0.0.0/8
172.16.0.0/12
192.168.0.0/16

Carrier shared space:
100.64.0.0/10
```

## 15. Interview questions with model-answer direction

### Q1. What happens when you type a URL in a browser?
Strong answer order:

```text
parse URL -> DNS/cache -> route/default gateway -> ARP/ND local next hop
-> Internet routing/NAT -> TCP or QUIC -> TLS -> HTTP
-> CDN/LB/app -> dependent requests -> response rendering
```

Mention caching and that exact details differ for HTTP/3/CDNs.

### Q2. Why does the laptop need a default gateway?
For destinations not on a directly connected subnet, it needs a local next hop/router that can forward toward other networks.

### Q3. Why doesn't the laptop use the remote server MAC address?
MAC/link addresses are local-link scoped. The laptop frames the packet to its local next hop. Routers re-encapsulate on each link.

### Q4. What makes an AWS subnet public?
Its route table provides Internet Gateway routing; direct IPv4 reachability also requires suitable public addressing and security/listener conditions.

### Q5. Why can an EC2 instance with a public IPv4 show only a private IP in Linux?
AWS maps the public IPv4 to the primary private IPv4 through VPC Internet Gateway NAT behavior; the public address is not configured as a normal guest NIC address.

### Q6. Security Group vs NACL?
SG is stateful and workload/ENI-oriented with allow rules; NACL is stateless, subnet-oriented, supports allow/deny, and requires explicit reverse-direction permission.

### Q7. NAT Gateway vs Internet Gateway?
IGW provides the VPC Internet-routing edge and public IPv4 mapping behavior for publicly addressed resources. NAT Gateway is used so private IPv4 resources can initiate outbound connections using translated public egress without being directly publicly addressed.

### Q8. Why do private subnets need NAT for IPv4 Internet egress?
RFC1918/private addresses are not globally routable on the Internet. NAT supplies a routable translated source and return mapping.

### Q9. Why doesn't IPv6 require the same NAT pattern?
IPv6 has abundant globally unique addressing; security is enforced with routing/firewalls. AWS provides an egress-only IGW for outbound-only IPv6 behavior.

### Q10. What is longest-prefix match?
Among matching destination routes, the most specific prefix is preferred, e.g. `/24` over `/16` over `/0`, subject to platform-specific tie/priority rules.

### Q11. What is BGP?
A policy-driven interdomain routing protocol used to exchange IP-prefix reachability between autonomous systems and select/advertise paths using attributes/policy.

### Q12. Peering vs transit?
Peering directly exchanges selected routes/traffic between networks; transit provides broader reachability through an upstream provider.

### Q13. Stateful vs stateless firewall?
Stateful filtering tracks flow context and recognizes return traffic; stateless filtering evaluates each packet/direction independently.

### Q14. What is ingress vs egress?
Relative to a named boundary: entering it is ingress, leaving it is egress.

### Q15. WAF vs network firewall?
WAF understands HTTP-layer request properties; a network firewall primarily enforces/inspects network/transport flows and may include deeper IPS/domain capabilities depending product.

### Q16. What does DNS do?
Maps names to resource records. It does not itself forward the application's packets to the destination.

### Q17. Why can DNS work while the website fails?
DNS only solved name→record. Routing, firewall, transport, TLS, load balancer, or app can still fail.

### Q18. Why can ping fail but HTTPS work?
ICMP Echo may be filtered while TCP/443 is permitted; ping is not a universal application-health test.

### Q19. Why does a firewall need return-path symmetry?
Stateful inspection needs to correlate both directions of a flow; asymmetric paths can bypass or confuse stateful enforcement.

### Q20. What is VPC Flow Logs' limitation?
They provide flow metadata and accept/reject-style evidence, not full packet payload or application behavior.

## 16. Scenario questions

### Scenario A
Private EC2 cannot reach vendor API.

Answer framework:

```text
DNS -> private route -> NAT -> public route/IGW -> SG/NACL
-> Network Firewall if present -> vendor allowlist -> TLS/HTTP
```

### Scenario B
ALB works, app returns 503.

Check target group health, backend SG relationship, app port/listener, health path, target capacity/app dependencies.

### Scenario C
On-prem can ping EC2 IP but cannot use `db.aws.corp`.

Likely DNS integration problem: Resolver endpoints/rules/private hosted zone path, not necessarily VPN/DX routing.

### Scenario D
Vendor sees unexpected source IP after migration.

Cloud workload is probably egressing through a new NAT/Internet path. Stabilize/communicate the correct egress IP and verify architecture.

### Scenario E
HTTPS works from one subnet but not another.

Compare route-table association, NACL, SG source, NAT/endpoint path, DNS, firewall inspection, IPv4/IPv6.

## 17. Final memory map

```text
                    NETWORKING
                        |
        +---------------+----------------+
        |               |                |
      Naming          Routing          Policy
       DNS         IP / prefixes    SG/NACL/FW/WAF
        |               |                |
        +---------> Transport <-----------+
                    TCP/UDP/QUIC
                        |
                       TLS
                        |
                   Application
                  HTTP/API/DB
```

## 18. Best source trail

- Cloudflare Internet basics: https://www.cloudflare.com/learning/network-layer/how-does-the-internet-work/
- Cloudflare BGP: https://www.cloudflare.com/learning/security/glossary/what-is-bgp/
- Cloudflare DNS: https://www.cloudflare.com/learning/dns/what-is-dns/
- Cloudflare TLS: https://www.cloudflare.com/learning/ssl/what-happens-in-a-tls-handshake/
- AWS Internet Gateway: https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html
- AWS NAT: https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat.html
- AWS route priority: https://docs.aws.amazon.com/vpc/latest/userguide/route-tables-priority.html
- AWS Flow Logs: https://docs.aws.amazon.com/vpc/latest/userguide/flow-logs.html


---

[← Previous](10-network-troubleshooting-labs.md) · [Chapter index](index.md)
