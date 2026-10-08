# Layers, Frames, Packets, Ports & Encapsulation




![Encapsulation and router behavior](assets/diagrams/networking/01-layers.svg)

## 1. Why layers exist

Networking is easier when each part has a narrow responsibility. Your browser should not need to know how Wi‑Fi retransmits a radio frame; your Wi‑Fi adapter should not need to understand HTTP cookies; a core Internet router should not need to parse your HTML.

The practical TCP/IP model can be remembered as:

```text
Application     HTTP, DNS, SSH, database protocol
Transport       TCP, UDP, QUIC behavior
Internet        IP, ICMP, routing between networks
Link            Ethernet, Wi-Fi, local frame delivery
Physical        copper, fiber, radio
```

The seven-layer OSI model is useful vocabulary, but real systems often blur boundaries. Use it to **locate responsibility**, not to force every protocol into a perfect box.

## 2. Four identifiers people confuse

Suppose your laptop opens HTTPS to `198.51.100.20`.

| Identifier | Example | Scope | Main purpose |
|---|---|---|---|
| MAC/link address | `8c:85:90:aa:bb:cc` | Local link | Deliver a frame to the next interface on the LAN/WLAN |
| IP address | `192.168.1.25` → `198.51.100.20` | Across routed networks | Identify source/destination IP endpoints |
| Port | `51514` → `443` | Host/transport | Identify a transport endpoint/application socket |
| DNS name | `shop.example.com` | Naming system | Human/service name that resolves to records such as IPs |

### Memory trick

> **MAC = next device, IP = remote endpoint/network, port = process, DNS = name.**

That statement is simplified but extremely useful.

## 3. Encapsulation: one request becomes several wrappers

Imagine the browser creates:

```text
GET /orders HTTP/1.1
Host: shop.example.com
```

A simplified outbound stack is:

```text
HTTP data
  ↓ encrypted/secured by TLS
TLS records
  ↓ carried by TCP
TCP segment
  src port 51514
  dst port 443
  ↓ carried by IP
IP packet
  src 192.168.1.25
  dst 198.51.100.20
  ↓ carried on the local Wi-Fi link
802.11 frame
  src MAC = laptop Wi-Fi adapter
  dst MAC = local next hop/AP/gateway side
  ↓
radio symbols/bits
```

At the destination, the wrappers are processed in the opposite direction until the web server/application receives the request.

## 4. What changes at a router?

This is a critical interview and troubleshooting concept.

A normal Layer-3 router forwards the **IP packet** between links. The local-link frame is not carried unchanged from your laptop to the remote server.

Simplified router behavior:

1. Receive an Ethernet/Wi‑Fi frame addressed to one of its local interfaces.
2. Remove the link-layer header/trailer.
3. Inspect destination IP.
4. Decrease IPv4 TTL or IPv6 Hop Limit.
5. Find the best matching route for the destination.
6. Resolve/know the next-hop link-layer address on the outgoing interface.
7. Encapsulate the IP packet inside a **new link-layer frame**.
8. Transmit toward the next hop.

Therefore:

```text
Laptop -> Router A -> Router B -> Server

IP destination may remain: 198.51.100.20
MAC destination changes:    every Layer-2 segment/hop
```

NAT is a special case because it can also rewrite IP addresses/ports.

## 5. Switch versus router

### Switch
A Layer-2 switch typically forwards frames inside a broadcast domain/VLAN using MAC address learning.

```text
Host A ---+
          +--- Switch --- Host B
Host C ---+
```

It learns which MAC addresses are reachable through which ports.

### Router
A router connects IP networks/subnets.

```text
10.0.1.0/24 --- Router --- 10.0.2.0/24
```

Hosts on `10.0.1.0/24` send remote-subnet traffic to a router/default gateway.

### Layer-3 switch
Enterprise switches can also perform routing. Do not infer capability merely from the physical shape of the appliance.

## 6. How the host knows whether a destination is local

Example host:

```text
IP address:      192.168.1.25
Subnet prefix:   /24
Default gateway: 192.168.1.1
```

For destination `192.168.1.77`, the host can determine that the destination belongs to the same `/24` and attempt local delivery.

For destination `198.51.100.20`, the destination is outside the local prefix, so a typical route table says:

```text
192.168.1.0/24  -> directly connected
0.0.0.0/0       -> 192.168.1.1
```

That `0.0.0.0/0` is the **default route**: the catch-all route when no more specific route matches.

## 7. ARP and IPv6 Neighbor Discovery

With IPv4, a host often uses **ARP** to learn the MAC address associated with a local IPv4 next hop.

Example intent:

```text
Laptop: “Who has 192.168.1.1? I need its MAC so I can send this frame.”
Router: “192.168.1.1 is at aa:bb:cc:dd:ee:ff.”
```

The laptop does **not** ARP for a remote website IP that is outside its subnet. It ARPs for the local next hop (often the default gateway).

IPv6 uses Neighbor Discovery rather than ARP.

## 8. TCP and UDP in one page

### TCP
TCP provides a reliable byte stream with connection state, sequencing, acknowledgements, retransmission, flow control, and congestion-control mechanisms.

Common protocols: HTTPS over TCP, SSH, many databases.

A classic TCP connection starts:

```text
Client                  Server
  | ---- SYN ----------> |
  | <--- SYN + ACK ----- |
  | ---- ACK ----------> |
  |   connected          |
```

### UDP
UDP is message/datagram-oriented and does not itself provide TCP-style connection establishment/reliability.

Common uses include DNS transport, voice/video mechanisms, gaming, and QUIC.

### QUIC / HTTP/3
Modern HTTP/3 uses QUIC over UDP and integrates transport/security behavior differently from classic HTTP over TCP + TLS. The important beginner lesson is: **UDP does not imply “unreliable application.”** Reliability can be implemented above UDP, as QUIC does.

## 9. Ports and sockets

A server process listens on a transport endpoint, for example TCP port 443.

The client usually chooses an ephemeral source port:

```text
192.168.1.25:51514  ---> 198.51.100.20:443
```

The 5-tuple commonly used to distinguish a flow is:

```text
source IP
source port
destination IP
destination port
protocol
```

This is why thousands of clients can all connect to the same server port 443 while remaining distinct flows.

## 10. MTU, fragmentation and the “small ping works, app fails” problem

Each link has a Maximum Transmission Unit (MTU). If a path cannot carry a packet as large as the sender expects, fragmentation or Path MTU Discovery behavior matters. VPN/tunnel headers reduce usable payload size.

Symptoms of MTU issues can include:

- small requests work but larger transfers stall,
- TLS connections behave strangely,
- VPN-connected applications fail selectively,
- ICMP filtering breaks Path MTU Discovery.

Do not make MTU your first guess, but know it exists when basic connectivity works yet larger packets fail.

## 11. ICMP is not “the ping protocol only”

ICMP supports network control/error messages. `ping` commonly uses ICMP Echo, but ICMP is also involved in operational behavior such as reporting unreachable destinations and supporting path diagnostics. Blocking all ICMP can create subtle operational problems.

## 12. Layer-oriented debugging

| Symptom | Likely area to check first |
|---|---|
| Wi‑Fi shows disconnected | physical/link/access |
| Has Wi‑Fi but no IP | DHCP/address configuration |
| Can reach local gateway but not Internet | routing/NAT/ISP |
| Can reach IP but hostname fails | DNS |
| TCP connection refused | destination reachable, no listener or active reject |
| TCP timeout | path/policy/drop/congestion possible |
| TLS certificate error | TLS/name/certificate/time issue |
| HTTP 403 | application/WAF/authz rather than basic routing |
| HTTP 500 | application/server dependency |

## 13. Legacy data-center connection

In a legacy enterprise, teams often divided ownership like this:

```text
Server team        host OS, NIC, TCP listener
Network team       VLAN, switch, route, BGP/OSPF
Firewall team      access policy/NAT/inspection
DNS team           zones/resolvers
Load balancer team VIP/pools/certificates
Application team   HTTP/API behavior
```

A ticket might bounce among five teams because each owns a different layer. AWS collapses some appliances into services, but the logical boundaries remain.

## 14. AWS bridge

When you later see:

```text
EC2 ENI -> subnet -> route table -> NAT gateway -> IGW
```

translate it mentally to:

```text
host interface -> local routed segment -> next-hop decision
-> address translation edge -> Internet routing edge
```

Security Groups are not “AWS magic”; they are a stateful packet/flow permission boundary associated with supported interfaces/resources. NACLs are a different, stateless subnet-level control.

## 15. Hands-on lab on your own computer

Run equivalents appropriate for your OS:

```bash
# Linux/macOS examples
ip addr                 # or ifconfig
ip route                # route table
a rp -a                 # use: arp -a (remove the space)
nslookup example.com
# or dig example.com
traceroute example.com
curl -v https://example.com/
```

Windows equivalents include:

```powershell
ipconfig /all
route print
arp -a
nslookup example.com
tracert example.com
curl.exe -v https://example.com/
```

Record:

- your private IP,
- prefix/netmask,
- default gateway,
- DNS resolver,
- resolved destination IP,
- first few route hops.

Do not be surprised if some `traceroute` hops show `*`; networks can filter or deprioritize the diagnostic messages while forwarding normal traffic.

## 16. Memory card

```text
Name        DNS
Process     Port
Remote host IP
Local hop   MAC/link address
Next hop    Route table
Permission  Firewall/policy
Proof       packet capture/log/flow evidence
```

## 17. Internet lecture trail

- Cloudflare, Network layer: https://www.cloudflare.com/learning/network-layer/what-is-the-network-layer/
- Cloudflare, Router: https://www.cloudflare.com/learning/network-layer/what-is-a-router/
- Cloudflare, Internet overview: https://www.cloudflare.com/learning/network-layer/how-does-the-internet-work/


---



[← Previous](00-networking-roadmap.md) · [Chapter index](index.md) · [Next →](02-home-wifi-to-internet.md)
