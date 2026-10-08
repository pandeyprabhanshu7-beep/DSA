# Your PC on Wi‑Fi → the Internet: Complete Packet Walk




![Home Wi-Fi to Internet](assets/diagrams/networking/02-home-wifi.svg)

## 1. The scenario

You are at home. Your laptop is connected to Wi‑Fi. You type:

```text
https://shop.example.com/products/42
```

Assume this simplified state:

```text
Laptop Wi-Fi IP:      192.168.1.25/24
Default gateway:      192.168.1.1
DNS resolver:         192.168.1.1 (router forwards to ISP/public resolver)
Router WAN address:   100.64.18.22   (example CGNAT/shared address)
ISP public NAT addr:  203.0.113.9    (documentation example)
Website IP:           198.51.100.20  (documentation example)
Destination TCP port: 443
```

The specific addresses are examples. `192.168.0.0/16` is private space; `100.64.0.0/10` is shared address space reserved for service-provider CGNAT use.

## 2. Before the browser can send anything: Wi‑Fi association

Wi‑Fi is the local access/link technology. The laptop discovers an SSID, authenticates/associates according to the network's security mode, and establishes link-layer connectivity to the access point.

Home equipment often combines several logical functions in one plastic box:

```text
Wi-Fi access point
Ethernet switch
IP router/default gateway
DHCP server
DNS forwarder/cache
NAT/PAT
stateful firewall
sometimes modem/ONT function
```

That is why beginners call everything “the router.” In enterprise networks these functions may be separate devices/services.

## 3. DHCP: how the laptop learns basic network configuration

A typical home client learns configuration automatically using DHCP. Conceptually it receives:

- an IP address such as `192.168.1.25`,
- a subnet prefix/netmask such as `/24`,
- a default gateway such as `192.168.1.1`,
- one or more DNS resolver addresses,
- lease timing and other options.

Without a default gateway, the laptop can still communicate with local neighbors if addressing is correct, but it normally has no route to arbitrary remote networks.

### Failure example

```text
Laptop IP: 169.254.x.x
```

On many systems, an IPv4 link-local/self-assigned address can indicate that DHCP did not provide normal configuration. You may have Wi‑Fi radio association but still lack usable routed connectivity.

## 4. DNS: turn the hostname into an IP

The browser/OS needs an address for `shop.example.com`.

The full public DNS hierarchy may involve:

```text
client/stub resolver
   ↓
recursive resolver
   ↓
root DNS
   ↓ referral
TLD (.com) DNS
   ↓ referral
authoritative DNS for example.com
   ↓
A/AAAA/CNAME/etc. answer
```

Caching means this full chain does not happen for every request.

The result might be:

```text
shop.example.com -> 198.51.100.20
```

DNS has now solved the **name problem**. It has not sent your HTTPS request.

## 5. The laptop decides: local destination or remote destination?

The laptop compares the destination with its local routes.

```text
192.168.1.0/24 -> directly connected Wi-Fi
0.0.0.0/0      -> 192.168.1.1
```

`198.51.100.20` is not in `192.168.1.0/24`, so the default route wins.

The next hop is `192.168.1.1`.

## 6. ARP: discover the gateway's local MAC address

To send a Wi‑Fi/Ethernet-layer frame toward `192.168.1.1`, the laptop needs local-link addressing. With IPv4, it may use ARP to learn the gateway's MAC address.

Critical point:

> The laptop does **not** need the website server's MAC address. MAC addresses are local-link identifiers. The first frame targets the local next hop.

## 7. TCP connection to HTTPS

The laptop chooses an ephemeral source port, for example `51514`, and initiates TCP:

```text
192.168.1.25:51514 -> 198.51.100.20:443
```

Simplified handshake:

```text
Laptop                         Website
  SYN ------------------------->
      <---------------- SYN/ACK
  ACK ------------------------->
```

If this never completes, TLS and HTTP have not started yet.

## 8. NAT/PAT at the home router

Private IPv4 `192.168.1.25` is not globally routed on the public Internet. The home router usually translates the flow.

Before translation:

```text
src 192.168.1.25:51514
 dst 198.51.100.20:443
```

Possible mapping:

```text
192.168.1.25:51514  <-> router/external-address:62001
```

Port Address Translation lets multiple inside hosts share one external IPv4 address by assigning distinct translated ports/state.

A NAT table conceptually remembers:

```text
inside-local                outside
192.168.1.25:51514  ->  translated-address:62001
192.168.1.31:53100  ->  translated-address:62002
```

Return packets matching established translations can be mapped back to the correct internal host.

### NAT is not exactly a firewall

NAT changes addresses/ports. A stateful firewall enforces connection policy. Consumer devices often combine both, which is why the concepts get conflated.

## 9. The modem/ONT and ISP access network

Depending on your service, the physical/access path may be:

```text
Fiber: laptop -> Wi-Fi router -> ONT -> ISP optical access network
Cable: laptop -> Wi-Fi router -> cable modem -> HFC/CMTS/CCAP network
DSL:  laptop -> Wi-Fi router -> DSL modem -> copper access/DSLAM
Fixed wireless: router/CPE -> radio access -> provider aggregation
```

The home LAN frame format does not necessarily remain the same through the access provider. The provider carries IP traffic over its own access and transport technologies.

## 10. CGNAT: why “what is my IP?” can differ from your router WAN IP

IPv4 scarcity caused many providers to deploy **Carrier-Grade NAT (CGNAT)**. RFC 6598 reserved `100.64.0.0/10` as Shared Address Space for provider networks.

You can therefore have two levels of translation:

```text
Laptop 192.168.1.25
   ↓ home NAT
Router WAN 100.64.18.22
   ↓ ISP CGNAT
Shared public IPv4 203.0.113.9
   ↓ Internet
```

This helps explain why inbound port forwarding can be difficult or impossible when the provider owns the outer NAT mapping.

## 11. ISP routers and the wider Internet

Your ISP has many routers. Inside one provider, traffic may be forwarded using internal routing protocols and MPLS/SR or other transport technologies. At boundaries between independent networks, BGP exchanges prefix reachability and policy.

Your packet may traverse:

```text
ISP access router
 -> regional aggregation
 -> provider core
 -> peering/transit edge
 -> another autonomous system
 -> destination network edge
```

The exact path can change because of routing policy, failures, congestion engineering, peering changes, or CDN placement.

## 12. Peering, transit and Internet exchanges

### Peering
Two networks exchange traffic directly according to an agreement/policy.

### Transit
One network pays another network to provide reachability to wider parts of the Internet.

### IXP
An Internet Exchange Point provides shared infrastructure where many networks can interconnect and peer.

These commercial/engineering relationships help explain why the “geographically shortest” path is not always selected.

## 13. Arrival at the destination network

The IP prefix containing `198.51.100.20` is routed toward the destination operator. The first system that actually handles the application may be a CDN/edge proxy, DDoS protection layer, WAF, reverse proxy, or load balancer rather than the origin server.

Modern website path:

```text
Browser
 -> ISP
 -> Internet
 -> CDN edge
 -> WAF/security
 -> load balancer/reverse proxy
 -> app service
 -> database/cache
```

## 14. TLS starts after transport connectivity exists

Once the connection is available, TLS authenticates the server (normally via its certificate chain and hostname validation), negotiates cryptographic parameters, and derives session keys.

A certificate error tells you something useful: **you reached enough of the destination path to perform TLS**. This is different from a DNS failure or TCP timeout.

## 15. HTTP request and response

Only after the required DNS/routing/transport/security setup can the application exchange HTTP semantics:

```text
GET /products/42 HTTP/1.1
Host: shop.example.com
...
```

The response may trigger many more requests for CSS, JavaScript, images, fonts, APIs, analytics, ads, and other resources. A “single webpage load” can therefore create many connections/streams and DNS lookups.

## 16. Return traffic: reverse direction is related but not guaranteed identical

The server's response is routed toward the source public address. The Internet can use asymmetric routing: the return path need not traverse the exact same routers.

At the ISP CGNAT and home NAT boundaries, state maps the return traffic back:

```text
198.51.100.20:443 -> 203.0.113.9:translated-port
 -> 100.64.18.22:translated-port
 -> 192.168.1.25:51514
```

The home router then emits a local Wi‑Fi frame to the laptop.

## 17. What is ingress and egress here?

The words are always relative to a boundary.

From your **home LAN**:

```text
Laptop -> Internet = egress
Internet -> Laptop = ingress
```

From the **website's data center**:

```text
Laptop request -> website = ingress
Website response/request to external API = egress
```

Never use “ingress” without mentally asking: **ingress into what?**

## 18. A packet snapshot at key boundaries

Assume a single-NAT case for simplicity.

### On laptop LAN before NAT

```text
L2 src MAC: laptop
L2 dst MAC: home gateway
IP src:     192.168.1.25
IP dst:     198.51.100.20
TCP src:    51514
TCP dst:    443
```

### On public Internet after NAT

```text
L2 addresses: change per link
IP src:      203.0.113.9
IP dst:      198.51.100.20
TCP src:     62001
TCP dst:     443
```

### On return Internet traffic

```text
IP src:  198.51.100.20
IP dst:  203.0.113.9
TCP src: 443
TCP dst: 62001
```

### Back on LAN after NAT reversal

```text
IP src:  198.51.100.20
IP dst:  192.168.1.25
TCP src: 443
TCP dst: 51514
```

## 19. “Internet works on phone but not laptop” troubleshooting

Work down a proof tree:

```text
1. Is laptop associated to Wi-Fi?
2. Did laptop receive IP/prefix/gateway/DNS?
3. Can it reach the gateway?
4. Can it reach a known IP?
5. Can it resolve DNS?
6. Can it open TCP/443?
7. Does TLS validate?
8. Does HTTP return a useful status?
```

If another Wi‑Fi device works, the WAN/ISP path is less likely to be the root cause, though not impossible because device-specific policy/DNS/IPv6 can differ.

## 20. Legacy-to-AWS analogy

| Home/ISP concept | AWS analogue |
|---|---|
| private laptop IP | EC2/task private IP |
| home LAN subnet | VPC subnet |
| local routing/default route | subnet route table |
| home NAT | NAT Gateway for private IPv4 egress |
| ISP Internet edge | Internet Gateway + AWS edge/global network |
| home firewall | Security Groups/NACLs/Network Firewall depending layer/scope |
| DNS resolver | VPC Resolver / Route 53 |

Do not force the analogy too literally; AWS implements these functions as distributed cloud primitives rather than one home-router box.

## 21. Best Internet examples

- Cloudflare, How does the Internet work?: https://www.cloudflare.com/learning/network-layer/how-does-the-internet-work/
- RFC 1918 private IPv4: https://www.rfc-editor.org/rfc/rfc1918
- RFC 6598 CGNAT Shared Address Space: https://www.rfc-editor.org/rfc/rfc6598
- Cloudflare, What is a router?: https://www.cloudflare.com/learning/network-layer/what-is-a-router/

## 22. What you must remember

1. Wi‑Fi gets you onto the local link; it is not “the Internet.”
2. DHCP commonly gives your host IP, prefix, gateway, and DNS settings.
3. DNS returns addressing information; routing moves packets.
4. Remote traffic is normally sent to the default gateway.
5. NAT can rewrite private source addressing to an Internet-routable address.
6. Your ISP may perform a second CGNAT layer.
7. BGP connects independently operated networks; it is policy-driven.
8. TLS/HTTP are later stages—do not troubleshoot them before proving basic reachability.


---



[← Previous](01-layers-frames-packets-ports.md) · [Chapter index](index.md) · [Next →](03-mobile-data-to-internet.md)
