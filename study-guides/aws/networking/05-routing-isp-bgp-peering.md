# Routing, ISPs, Autonomous Systems, BGP, Peering & Transit




![Internet routing and BGP](assets/diagrams/networking/05-routing-bgp.svg)

## 1. Routing in one sentence

> **Routing is the process of selecting a next hop/interface for a packet based primarily on its destination IP prefix and the routes known to the device.**

A router usually does not know the complete future path of one packet. It knows its own routing table and forwards to a next hop. The next router repeats the process.

## 2. The routing table mental model

Example:

```text
Destination          Next hop / target
10.0.0.0/8           internal-router
172.16.50.0/24       vpn-tunnel
198.51.100.0/24      peer-A
0.0.0.0/0            isp-default
```

For destination `198.51.100.25`, the `/24` route is more specific than `0.0.0.0/0`, so it wins.

### Longest prefix match

This principle appears everywhere:

- host routing tables,
- enterprise routers,
- AWS VPC route tables,
- Transit Gateway route tables.

AWS explicitly documents most-specific/longest-prefix matching as the normal route-selection behavior before additional route-priority rules.

## 3. Connected, static, default and dynamic routes

### Connected route
A network is directly attached to an interface.

### Static route
An administrator explicitly configures destination -> next hop.

### Default route
Catch-all route such as:

```text
0.0.0.0/0 -> ISP
::/0      -> IPv6 upstream
```

### Dynamic route
A routing protocol learns reachability from other routers and installs/selects routes according to protocol rules/policy.

Examples in enterprises include OSPF, IS-IS, BGP; exact usage varies.

## 4. Why you do not put “the whole Internet” into a home router manually

A home router normally sends unknown destinations to its ISP via a default route. The ISP participates in much larger routing systems and knows many prefixes.

```text
Laptop: “not local -> gateway”
Home router: “not local -> ISP”
ISP edge/core: “this prefix -> this backbone/peer/transit path”
Destination AS: “this prefix is mine -> deliver internally”
```

Each device only needs the level of routing knowledge appropriate to its role.

## 5. Autonomous Systems: the Internet as organizations

An **Autonomous System (AS)** is a routing domain under common administrative control that presents routing policy to other networks. ISPs, cloud providers, CDNs, large enterprises, universities, and carriers can operate ASes.

Each participating AS can use an Autonomous System Number (ASN) for external BGP routing.

Think:

```text
Internet = many ASes + links + agreements + routing policy
```

not:

```text
Internet = one company/router hierarchy
```

## 6. BGP: what it really does

Border Gateway Protocol exchanges reachability information for IP prefixes between routing domains and applies policy to choose/advertise paths.

Cloudflare's BGP learning material describes the Internet as many autonomous systems exchanging routes, and correctly notes that route choice can include business/policy considerations rather than merely geographic distance.

### Do not memorize this wrong sentence

> “BGP chooses the shortest path.”

Better:

> **BGP chooses a best path according to attributes and operator policy. AS-path length can matter, but it is not the only consideration.**

## 7. BGP simplified example

Suppose AS65010 owns:

```text
198.51.100.0/24
```

It advertises that prefix to two upstream networks:

```text
           ISP A (AS65001)
          /
AS65010 --
          \
           ISP B (AS65002)
```

Other networks learn one or more possible AS paths. Their policies determine which path becomes preferred for forwarding toward that prefix.

## 8. Peering vs transit

### Peering
Networks exchange traffic directly, typically for their own/customer routes according to policy/agreement.

### Transit
A customer network pays an upstream provider for broader/global Internet reachability.

### Why this matters

Two companies in the same city may send traffic through a distant path if they lack direct peering or policy selects another route. A CDN invests heavily in peering/edge presence to bring content closer in network terms.

## 9. Internet Exchange Point (IXP)

An IXP is shared interconnection infrastructure where multiple networks can connect and exchange traffic. It reduces the need for a unique physical circuit between every pair of networks.

Conceptually:

```text
ISP A ---+
CDN   ---+--- IXP switching fabric
Cloud ---+
ISP B ---+
```

BGP sessions and policy determine what routes/traffic are exchanged.

## 10. Interior vs exterior routing

A large ISP can use one set of protocols/technologies internally and BGP externally.

```text
inside AS:  IGP / MPLS / segment routing / iBGP design
between AS: eBGP at boundaries
```

You do not need to master carrier architecture for AWS, but this prevents the misconception that every Internet router talks eBGP directly to every other router.

## 11. The data plane vs routing/control plane

### Control plane
Learns/selects routes, runs routing protocols, builds forwarding state.

### Data plane
Forwards actual packets using that state.

This is analogous to cloud control plane vs packet data path:

```text
AWS API: configure route table
VPC data plane: forward packets according to route table
```

## 12. Asymmetric routing

The forward and return path may differ:

```text
Client -> ISP A -> Transit X -> Server
Server -> Peer Y -> ISP B -> Client
```

The Internet does not promise symmetric hop-by-hop paths.

Why it matters:

- stateful middleboxes often need both directions,
- on-prem firewalls can drop return traffic if it bypasses the expected appliance,
- AWS Network Firewall stateful inspection requires correct/symmetric routing through inspection endpoints for flows being inspected.

## 13. ECMP and multiple next hops

Networks can use multiple equal-cost paths to distribute traffic. Hashing may keep packets of a flow on a consistent path while different flows use different paths.

Do not assume one traceroute run represents every flow or every moment.

## 14. Route convergence and failures

If a link/router disappears, routing systems need time to detect the failure and select/propagate alternatives. During convergence, packets can be lost or paths can change.

High availability therefore involves more than owning two circuits; you must design:

- route advertisement,
- failure detection,
- policy,
- stateful firewall symmetry,
- DNS/app failover behavior,
- capacity on surviving paths.

## 15. TTL/Hop Limit and traceroute

IPv4 TTL / IPv6 Hop Limit decreases as routers forward packets. `traceroute` exploits expiration behavior to infer hops by sending probes with increasing limits.

But traceroute is not a perfect truth source:

- routers can filter diagnostic responses,
- MPLS/tunnels can hide details,
- asymmetric paths can differ,
- load balancing can show varying hops,
- asterisks do not necessarily mean forwarding is broken.

## 16. Route summarization

Instead of advertising hundreds of small prefixes individually, networks can aggregate contiguous ranges where design permits.

Example:

```text
10.20.0.0/24
10.20.1.0/24
...
10.20.255.0/24
```

can potentially be represented as:

```text
10.20.0.0/16
```

Summarization reduces routing-state complexity but can create blackholes if the summary is advertised where some subranges are not actually reachable.

## 17. Legacy enterprise Internet edge

A serious enterprise might have:

```text
             ISP A
               |
          Edge Router A
             /   \
LAN/DMZ -- Firewall pair
             \   /
          Edge Router B
               |
             ISP B
```

BGP can advertise enterprise-owned public prefixes to multiple ISPs. Firewalls/NAT sit in or near the path. Redundancy protocols, routing policy, and failover behavior must all agree.

For smaller companies, the provider may simply assign addresses/default routes without the enterprise running public BGP.

## 18. AWS route table mapping

AWS VPC route table example:

```text
10.0.0.0/16    local
10.50.0.0/16   tgw-1234
0.0.0.0/0      nat-1234
```

For destination `10.50.2.8`, the `/16` Transit Gateway route is more specific than the default NAT route.

### Public subnet

```text
10.0.0.0/16    local
0.0.0.0/0      igw-1234
```

### Private app subnet

```text
10.0.0.0/16    local
0.0.0.0/0      nat-az-a
```

The route table does not itself grant firewall permission; route and policy are separate questions.

## 19. Transit Gateway as a cloud routing hub

When dozens/hundreds of VPCs and on-prem connections exist, full-mesh VPC peering becomes operationally difficult. Transit Gateway provides a regional transit hub with attachments and its own route tables/segmentation model.

Legacy analogy:

```text
WAN/core router hub  <-> Transit Gateway
```

But remember it is a managed cloud service with AWS-specific attachment and routing semantics, not a virtual Cisco CLI box.

## 20. Direct Connect and VPN routing

### Site-to-Site VPN
Encrypted IPsec tunnels over Internet connectivity. Routes can be static or dynamically exchanged with BGP depending on setup.

### Direct Connect
Dedicated/private connectivity from your network through a Direct Connect location/provider arrangement into AWS connectivity constructs. BGP is used for route exchange on virtual interfaces.

Common hybrid design uses Direct Connect as primary with VPN backup, but actual topology, failure domains, bandwidth, and route preference need explicit design/testing.

## 21. BGP failure/attack concepts you should recognize

Because BGP is trust/policy-heavy, route leaks and hijacks can send traffic to unexpected networks. Operators mitigate with mechanisms such as route filtering, prefix limits, and RPKI-based Route Origin Validation where deployed.

You do not need to become an Internet routing security specialist to understand the lesson:

> **DNS security and TLS are not substitutes for correct routing security, and routing security is not a substitute for end-to-end application authentication/encryption. They protect different layers.**

## 22. Dry run — where does this packet go?

Routes:

```text
0.0.0.0/0        -> Internet
10.0.0.0/8       -> Corporate WAN
10.20.0.0/16     -> Site B
10.20.30.0/24    -> Inspection firewall
10.20.30.50/32   -> Emergency host route
```

Destinations:

```text
8.8.8.8       -> /0 Internet
10.99.1.2     -> /8 Corporate WAN
10.20.9.9     -> /16 Site B
10.20.30.88   -> /24 Inspection firewall
10.20.30.50   -> /32 Emergency host route
```

Always choose the most specific matching prefix first.

## 23. Common wrong mental models

- “Default route means Internet.” It means **catch-all next hop**; that next hop could be a firewall, VPN, TGW, or blackhole.
- “BGP knows application ports.” Normal IP route selection is prefix/policy based, not HTTP endpoint based.- “More hops always means slower.” Physical distance, congestion, link capacity, queuing, and processing matter too.
- “Traceroute shows the exact packet path.” It is a diagnostic approximation.
- “Two links means HA.” Routing/failover/state/capacity design decides HA.

## 24. Best Internet/AWS examples

- Cloudflare BGP explanation: https://www.cloudflare.com/learning/security/glossary/what-is-bgp/
- AWS VPC route priority / longest-prefix match: https://docs.aws.amazon.com/vpc/latest/userguide/route-tables-priority.html
- AWS Transit Gateway: https://docs.aws.amazon.com/vpc/latest/tgw/what-is-transit-gateway.html
- AWS VPN routing: https://docs.aws.amazon.com/vpn/latest/s2svpn/VPNRoutingTypes.html
- AWS Direct Connect: https://docs.aws.amazon.com/directconnect/latest/UserGuide/Welcome.html

## 25. Memory trick

> **Prefix tells where; route tells next hop; BGP tells networks what prefixes are reachable under policy.**


---



[← Previous](04-ip-subnetting-nat-cgnat.md) · [Chapter index](index.md) · [Next →](06-dns-tcp-tls-http.md)
