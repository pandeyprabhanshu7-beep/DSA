# Networking Bootcamp — Start Here



![Networking roadmap](assets/diagrams/networking/00-roadmap.svg)

If networking feels mysterious, do **not** begin with VPC route tables. Begin with one question:

> **When I type `https://example.com` on my laptop or phone, what physical and logical systems touch my traffic before the page appears?**

AWS networking is mostly the same networking ideas exposed as software-defined resources. The names change; the packet still needs an address, a next hop, a permitted path, a transport session, and an application endpoint.

## The seven questions to ask for every network problem

1. **Name:** What hostname did the client use, and what IP did DNS return?
2. **Address:** What source and destination IPs exist at this point in the path? Were they translated?
3. **Local link:** How does the device reach the next-hop router on this LAN/WLAN/cellular access network?
4. **Route:** Which routing table chooses the next hop for the destination prefix?
5. **Policy:** Which firewall/security policy can allow or deny the flow?
6. **Transport:** Which protocol and ports are used? Did TCP/QUIC establish a session?
7. **Application:** Did TLS and HTTP/application behavior succeed after the network path worked?

### Memory trick

> **N-A-L-R-P-T-A = Name, Address, Link, Route, Policy, Transport, Application.**

If you debug in that order, you avoid the common mistake of changing firewall rules when the real problem is DNS, routing, or an application that is not listening.

## Recommended study order

| Order | Lecture | Why it comes here |
|---:|---|---|
| 1 | [Layers, Frames, Packets, Ports & Encapsulation](01-layers-frames-packets-ports.md) | Learn the vocabulary that all later diagrams use. |
| 2 | [Your PC on Wi‑Fi → the Internet](02-home-wifi-to-internet.md) | Builds the complete path from a familiar device. |
| 3 | [Phone on Mobile Data → the Internet](03-mobile-data-to-internet.md) | Shows why cellular access differs from Wi‑Fi while IP remains the common layer. |
| 4 | [IP Addressing, Subnetting, NAT & CGNAT](04-ip-subnetting-nat-cgnat.md) | Explains private/public addresses and why NAT exists. |
| 5 | [Routing, ISPs, Autonomous Systems, BGP, Peering & Transit](05-routing-isp-bgp-peering.md) | Explains how packets cross independent networks. |
| 6 | [DNS → TCP/QUIC → TLS → HTTP](06-dns-tcp-tls-http.md) | Reconstructs a browser request in time order. |
| 7 | [Ingress, Egress, Firewalls, WAF, Proxies & DMZs](07-ingress-egress-firewalls-dmz.md) | Explains security controls at different layers. |
| 8 | [Legacy/In-house Data Center Network Flow](08-legacy-datacenter-networking.md) | Gives the exact mental bridge to enterprise networking. |
| 9 | [AWS Internet & VPC Packet Flow](09-aws-internet-vpc-packet-flow.md) | Maps every legacy component to AWS. |
| 10 | [Network Troubleshooting Labs](10-network-troubleshooting-labs.md) | Teaches how to prove which layer is broken. |
| 11 | [Networking Cheat Sheet & Interview Questions](11-networking-cheatsheet.md) | Revision and recall. |

## One diagram to remember before anything else

```text
Application wants: https://shop.example.com/orders
                         |
                         v
                    DNS resolves name
                         |
                         v
             destination IP: 198.51.100.20
                         |
                         v
             local routing table decides:
             remote network -> default gateway
                         |
                         v
      Laptop --Wi-Fi--> home router --ISP--> Internet routers
                         |
                         v
               destination edge/firewall/LB
                         |
                         v
                    application server
```

The important idea is that **your laptop usually does not know the complete Internet path**. It knows the destination IP and a local route, often a default route. Each router independently performs a similar next-hop decision.

## What “the Internet” actually means

The Internet is a **network of networks**, not one giant LAN. Large networks are organized into independently administered routing domains called **Autonomous Systems (ASes)**. ISPs, cloud providers, CDNs, universities, and large enterprises may operate ASes. BGP is the principal protocol used to exchange reachable IP prefixes and routing policy between ASes.

Do not memorize “BGP finds the shortest path.” That is an oversimplification. BGP is **policy-driven**; AS-path length is only one attribute among several, and commercial or engineering policy can make a longer-looking route preferable.

## How AWS fits into this picture

| Familiar network idea | Legacy/home example | AWS expression |
|---|---|---|
| Address range | Office subnet `10.20.30.0/24` | VPC/subnet CIDR |
| Routing table | Router/default gateway routes | VPC route table |
| Internet edge | ISP-facing router/firewall | Internet Gateway + public routing |
| Outbound address translation | Firewall/router PAT | NAT Gateway for private IPv4 egress |
| Stateful workload firewall | Host or perimeter firewall | Security Group |
| Stateless subnet ACL | Router ACL | Network ACL |
| Layer-7 web firewall | Appliance WAF/reverse proxy | AWS WAF |
| Deep packet/IPS firewall | Palo Alto/Fortinet/etc. | AWS Network Firewall / appliances via GWLB |
| DNS | Corporate DNS / public DNS | Route 53 + VPC Resolver |
| Load balancer | F5/HAProxy/NGINX appliance | ALB/NLB |
| Hub router | Core/WAN router | Transit Gateway |
| Private carrier circuit | MPLS/private line | Direct Connect |
| Site-to-site encrypted tunnel | IPsec VPN appliance | AWS Site-to-Site VPN |

## The three traffic directions you should recognize

### North–south
Traffic crosses the environment boundary.

```text
Internet user -> enterprise/AWS application
application -> Internet SaaS/API
```

### East–west
Traffic stays inside the environment but crosses workloads/segments.

```text
web tier -> app tier -> database
service A -> service B
VPC A -> VPC B
```

### Management/control-plane traffic
Administrators or automation configure resources; this is not always the same path as application data.

```text
Engineer -> AWS API/control plane
Application packet -> VPC data path
```

This distinction matters because a console/API action can succeed while the packet path is broken, or the packet path can work while IAM prevents you from changing it.

## Beginner mistakes this bootcamp will remove

- Thinking a public subnet is public because of its name.
- Thinking a public IP is physically configured on every NIC exactly as shown in the console.
- Thinking DNS sends your web request to the server.
- Thinking the destination MAC address of an Internet packet is the remote server’s MAC.
- Thinking routers forward based on TCP port instead of destination IP prefixes.
- Thinking NAT is the same thing as a firewall.
- Thinking “ingress” always means public Internet traffic.
- Thinking all firewalls work at the same network layer.
- Thinking Security Groups and NACLs behave identically.
- Thinking an outbound HTTPS request requires inbound port 443 to be open on the client.
- Thinking `ping` proves an HTTPS application is healthy.
- Thinking `traceroute` always reveals every router.

## Internet examples and authoritative reading

- Cloudflare, **How does the Internet work?**: https://www.cloudflare.com/learning/network-layer/how-does-the-internet-work/
- Cloudflare, **What is a router?**: https://www.cloudflare.com/learning/network-layer/what-is-a-router/
- Cloudflare, **What is BGP?**: https://www.cloudflare.com/learning/security/glossary/what-is-bgp/
- Cloudflare, **What is DNS?**: https://www.cloudflare.com/learning/dns/what-is-dns/
- AWS, **Amazon VPC User Guide**: https://docs.aws.amazon.com/vpc/latest/userguide/what-is-amazon-vpc.html

> **Teaching note:** external diagrams are linked as references. The diagrams packaged in this course are original study redraws so they remain readable, printable, and consistent with the rest of the guide.

## Self-test before moving on

You are ready for the next lecture if you can explain, without AWS terminology:

1. Why a laptop needs a default gateway.
2. Why the destination MAC on the first Wi‑Fi frame is normally the gateway/AP-side next hop rather than the remote website.
3. Why DNS and routing solve different problems.
4. Why a private IPv4 address can access the Internet through NAT.
5. Why a return packet may take a different physical/router path on the Internet.

[Next → Layers, Frames, Packets, Ports & Encapsulation](01-layers-frames-packets-ports.md)

---



[Chapter index](index.md) · [Next →](01-layers-frames-packets-ports.md)
