# IP Addressing, Subnetting, NAT, PAT, CGNAT & IPv6




![NAT and CGNAT mental model](assets/diagrams/networking/04-ip-nat.svg)

## 1. The address question

Every routed packet needs source and destination IP addresses. Before learning CIDR math, understand the job:

> **An IP address identifies an interface/end point in an IP routing context, and the prefix tells routers which block/network it belongs to.**

IPv4 is 32 bits. IPv6 is 128 bits.

## 2. IPv4 dotted decimal is just a human representation

```text
192.168.1.25
```

is four 8-bit numbers:

```text
192      168      1        25
11000000 10101000 00000001 00011001
```

Routers perform prefix comparisons in binary. Humans use CIDR notation to describe how many leading bits form the network prefix.

## 3. CIDR notation

```text
192.168.1.0/24
```

means the first 24 bits are the prefix.

A `/24` contains 256 total IPv4 addresses in the mathematical block. Whether all are assignable depends on platform/network rules. AWS reserves addresses within each subnet, so do not equate mathematical block size with usable AWS host count.

Common sizes:

| Prefix | Total IPv4 addresses | Mental shortcut |
|---:|---:|---|
| /16 | 65,536 | large VPC/site block |
| /20 | 4,096 | medium block |
| /24 | 256 | classic small LAN-sized block |
| /26 | 64 | quarter of a /24 |
| /27 | 32 | eighth of a /24 |
| /28 | 16 | small block |
| /32 | 1 | single IPv4 address/host route |

Formula:

```text
IPv4 block size = 2^(32 - prefix length)
```

## 4. How a host decides “same subnet or router?”

Host:

```text
IP: 10.20.30.25/24
```

Destination A:

```text
10.20.30.99
```

Same `/24` prefix -> local-link delivery.

Destination B:

```text
10.20.31.99
```

Different `/24` -> use a router according to routing table.

This is why an incorrect subnet mask/prefix can create bizarre connectivity: the host may incorrectly believe a remote IP is local and ARP for it, or incorrectly send a local peer to a router.

## 5. Private IPv4 ranges (RFC 1918)

The classic private ranges are:

```text
10.0.0.0/8
172.16.0.0/12
192.168.0.0/16
```

These can be reused by many organizations and are not globally routed as normal public Internet addresses.

### The overlap problem

Company A:

```text
10.0.0.0/8
```

Company B:

```text
10.0.0.0/8
```

If they later connect networks, overlapping addresses make routing ambiguous. This is a common legacy acquisition/VPN/cloud-migration problem.

AWS VPC IPAM and disciplined enterprise IP planning help reduce this risk, but they do not magically fix existing overlaps.

## 6. Public IPv4

A public IPv4 address is intended to be globally routable/unique in Internet routing space under the relevant allocation/announcement model.

Do not confuse:

```text
“public IP exists”
```

with:

```text
“the service is reachable from everyone.”
```

Reachability also needs routing, firewall/security permission, a listening service, and correct return path.

## 7. Special documentation ranges used in this guide

To avoid accidentally using real routable addresses in examples, documentation commonly uses ranges such as:

```text
192.0.2.0/24
198.51.100.0/24
203.0.113.0/24
```

Treat addresses from those blocks in diagrams as examples, not real targets.

## 8. NAT: what it actually changes

Network Address Translation rewrites address information as packets cross a translation boundary.

The most familiar case is source NAT for outbound IPv4:

```text
Inside packet:
src 10.0.1.25
 dst 198.51.100.20

After source NAT:
src 203.0.113.5
 dst 198.51.100.20
```

A translation table preserves enough state to map return traffic.

## 9. PAT/NAPT: why many private devices share one public IPv4

In common home/enterprise usage, address **and port** translation is involved.

```text
10.0.1.25:51001 -> 203.0.113.5:62001
10.0.1.26:51001 -> 203.0.113.5:62002
```

The same public address can represent many simultaneous flows because translated ports distinguish mappings.

AWS documentation explicitly notes that what industry commonly calls NAT devices perform both address and port translation in these scenarios.

## 10. Static one-to-one NAT vs dynamic/PAT

### One-to-one mapping
A public address maps predictably to one inside address. Enterprise firewalls often support static NAT for published servers.

### Dynamic source NAT/PAT
Many inside clients share one/few outside addresses for outbound sessions.

### Destination NAT / port forwarding
Inbound traffic to a public address/port is translated toward an internal server.

Example:

```text
203.0.113.10:443 -> 10.0.10.50:443
```

A reverse proxy/load balancer can also publish applications, but that is application/proxying rather than merely packet address translation.

## 11. NAT is not a security architecture by itself

It is tempting to say “private IP = secure.” That is incomplete.

Security depends on:

- ingress/egress policy,
- stateful firewall behavior,
- application authentication,
- patching,
- segmentation,
- logging/monitoring,
- route exposure,
- identity and data controls.

NAT can incidentally block unsolicited inbound flows if no mapping/publishing exists, but its primary conceptual job is translation.

## 12. CGNAT: NAT operated by the provider

RFC 6598 defines:

```text
100.64.0.0/10
```

as Shared Address Space for carrier/service-provider use with CGN.

Possible chain:

```text
Laptop 192.168.1.25
 -> home router 100.64.5.10
 -> ISP CGN public 203.0.113.20
 -> Internet
```

Problems/implications include:

- harder inbound hosting/port forwarding,
- shared public source addresses,
- need for port/time metadata in abuse investigations,
- application rate limiting by public IP may group unrelated subscribers.

## 13. AWS public IPv4 is a useful non-obvious example

For EC2, AWS documentation explains that a public IPv4 displayed for an instance is mapped to its primary private IPv4 through NAT. Inside the instance OS, tools such as `ip addr`/`ifconfig` show the private address rather than that public IPv4 as a directly configured NIC address.

The Internet Gateway performs one-to-one NAT behavior for instance public IPv4/Elastic IP traffic as part of Internet connectivity.

This is an excellent example of why you should distinguish:

```text
logical/public addressing shown by the cloud control plane
```

from

```text
address configured on the guest network interface
```

## 14. Subnetting exercise: split a /24

Start:

```text
10.0.8.0/24
```

Split into four equal blocks -> borrow two more prefix bits:

```text
10.0.8.0/26
10.0.8.64/26
10.0.8.128/26
10.0.8.192/26
```

Each `/26` contains 64 mathematical addresses.

### Pattern shortcut

For IPv4:

```text
/24 256
/25 128
/26 64
/27 32
/28 16
/29 8
/30 4
/31 2 (special point-to-point use possible)
/32 1
```

Each extra prefix bit halves the block.

## 15. VLSM planning example

Need:

```text
App subnet A: ~100 hosts
App subnet B: ~100 hosts
DB subnet A:  ~30 hosts
DB subnet B:  ~30 hosts
Ops subnet:   ~20 hosts
```

Do not assign arbitrary `/24`s just because they are easy. Plan growth, AZ duplication, container/pod/ENI consumption, load balancer ENIs, endpoints, and future network connections.

A cloud subnet can run out of IPs before CPU or storage becomes the bottleneck.

## 16. IPv6: key conceptual differences

IPv6 has a much larger address space and globally unique addressing is common. In AWS, IPv6 addresses are globally unique/public by default in the addressing sense.

That does **not** mean “open to the Internet.” Route tables and security controls still decide reachability.

For AWS outbound-only IPv6 from private workloads, an **egress-only Internet Gateway** can allow outbound communication while preventing Internet-initiated IPv6 connections.

### Important NAT contrast

```text
IPv4 private egress: usually NAT Gateway in AWS
IPv6 private egress: no address-conservation NAT required; use egress-only IGW for outbound-only Internet pattern
```

## 17. Dual stack

A dual-stack workload can have both IPv4 and IPv6. DNS may return A and AAAA records. Client behavior may prefer one family depending on OS/network reachability.

Troubleshooting rule:

> When “some clients work, some fail,” compare IPv4 and IPv6 separately.

It is possible to have healthy IPv4 routing but broken IPv6 routing, DNS, firewall policy, or vice versa.

## 18. Longest-prefix match preview

A router with:

```text
0.0.0.0/0        -> Internet
10.0.0.0/8       -> internal WAN
10.20.0.0/16     -> site B
10.20.30.0/24    -> special firewall
10.20.30.50/32   -> host-specific next hop
```

sending to `10.20.30.50` selects `/32`, because it is the most specific matching prefix.

This rule is foundational in enterprise routing and AWS route tables.

## 19. Address plan mistakes that hurt later

- Using overlapping RFC1918 ranges everywhere.
- Allocating subnets with no growth room.
- Forgetting Kubernetes/container ENI/pod IP consumption.
- Treating IPs as permanent server identities.
- Hardcoding IPs where DNS/service discovery should be used.
- Mixing “subnet is public” with “address is public.”
- Ignoring IPv6 when clients can reach the service over AAAA records.

## 20. Quick packet translation dry run

Client sends:

```text
10.0.1.25:51514 -> 198.51.100.20:443
```

NAT chooses:

```text
203.0.113.5:62001
```

Internet sees:

```text
203.0.113.5:62001 -> 198.51.100.20:443
```

Server responds:

```text
198.51.100.20:443 -> 203.0.113.5:62001
```

NAT table finds mapping and rewrites:

```text
198.51.100.20:443 -> 10.0.1.25:51514
```

If the state expires, an unexpected later inbound packet may no longer map to the inside client.

## 21. AWS mapping

| Addressing concept | AWS object/behavior |
|---|---|
| network address plan | VPC CIDR / IPAM pool |
| subnet prefix | subnet CIDR |
| workload address | ENI private IPv4/IPv6 |
| stable public IPv4 | Elastic IP where supported |
| outbound private IPv4 translation | NAT Gateway |
| instance public IPv4 mapping | IGW/public IPv4 mapping behavior |
| outbound-only IPv6 edge | egress-only Internet Gateway |
| service-private path | VPC endpoint / PrivateLink depending service pattern |

## 22. Internet/AWS references

- RFC 1918: https://www.rfc-editor.org/rfc/rfc1918
- RFC 6598: https://www.rfc-editor.org/rfc/rfc6598
- EC2 IP addressing: https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-instance-addressing.html
- AWS public IPv4 mapping: https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/working-with-ip-addresses.html
- AWS Internet Gateway: https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html
- AWS egress-only IGW: https://docs.aws.amazon.com/vpc/latest/userguide/egress-only-internet-gateway.html

## 23. Memory tricks

```text
CIDR: more /bits = smaller network
/24 = 256, /25 = 128, /26 = 64 ...

NAT: rewrite address
PAT: rewrite/use ports too
CGNAT: provider-owned large-scale NAT

IPv4 private egress in AWS -> NAT GW
IPv6 outbound-only in AWS -> egress-only IGW
```


---


## Worked packet trace: home PAT, CGNAT and return traffic

This is a simplified **IPv4/TCP teaching example**, not a capture from a real ISP. Addresses 198.51.100.0/24 and 203.0.113.0/24 are documentation ranges. 100.64.0.0/10 is shared address space for carrier NAT; it is distinct from RFC 1918 private space. Port mappings below are illustrative and implementation-dependent.

A laptop opens HTTPS to `203.0.113.80:443`. It uses `192.168.1.20:51514`. Its home router has ISP-facing address `100.64.1.2`. The carrier translates that shared address to `198.51.100.10`.

| Observation point | TCP source | TCP destination | What changed? |
|---|---|---|---|
| Laptop to home router | 192.168.1.20:51514 | 203.0.113.80:443 | Original tuple |
| Home router to ISP | 100.64.1.2:62001 | 203.0.113.80:443 | Home PAT translates source address/port |
| Carrier to Internet | 198.51.100.10:40020 | 203.0.113.80:443 | CGNAT translates source again |
| Server response toward carrier | 203.0.113.80:443 | 198.51.100.10:40020 | Source/destination swap for response |
| Carrier response toward home | 203.0.113.80:443 | 100.64.1.2:62001 | Carrier reverses its destination mapping |
| Home response toward laptop | 203.0.113.80:443 | 192.168.1.20:51514 | Home reverses its destination mapping |

The home router retains a mapping for the laptop flow; the carrier retains a second mapping for the router flow. Translation rewrites relevant IP/transport checksums. Link-layer headers are rebuilt at router hops, while TCP sequence numbers belong to the same end-to-end connection in this basic NAT example. A reverse proxy differs: it terminates one transport connection and opens another.

**Why an unsolicited inbound SYN usually fails:** it has no corresponding NAT mapping; forwarding a port on the home router alone does not create a carrier-side mapping. Mapping expiry can also explain why a long-idle connection stops receiving traffic. NAT translation is not a replacement for firewall policy or application authentication.

### Map the idea into AWS without conflating the two designs

For a standard public-NAT IPv4 Internet path, a private workload routes to a public NAT gateway; the NAT maps its source to the NAT gateway's private address, and the Internet gateway maps that address to the associated Elastic IP. The public subnet needs an Internet gateway route, and the workload subnet needs the NAT route. Return traffic follows the established translations. Security groups track allowed flows; NACLs are stateless and must permit both directions, including required return destination ports.

**Check your understanding:** which source IP does the Internet server observe? In the home example: 198.51.100.10. In the AWS Internet-egress example: the NAT gateway's Elastic IP. Does either setup make the original private client directly reachable by unsolicited traffic? No.

Sources: [RFC 6598 shared address space](https://www.rfc-editor.org/rfc/rfc6598), [RFC 5737 documentation addresses](https://www.rfc-editor.org/rfc/rfc5737), [AWS NAT gateway behavior](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html), [AWS security groups](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html), [AWS NACLs](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-network-acls.html).




[← Previous](03-mobile-data-to-internet.md) · [Chapter index](index.md) · [Next →](05-routing-isp-bgp-peering.md)
