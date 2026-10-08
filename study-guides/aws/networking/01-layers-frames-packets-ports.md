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

**Link MTU** is the largest IP packet a particular link can carry without link-layer fragmentation. **Path MTU (PMTU)** is the smallest link MTU across the complete source-to-destination path. The sender has to respect the PMTU even when its own interface supports something larger.

```text
host A -- MTU 1500 -- router -- tunnel MTU 1420 -- router -- MTU 1500 -- host B
                                  ^
                                  path bottleneck

PMTU(A,B) = min(1500, 1420, 1500) = 1420 bytes
```

Why tunnels change the number: an outer IP, VPN, GRE or other encapsulation header consumes bytes on the physical link. A 1,500-byte inner packet plus tunnel headers may no longer fit into a 1,500-byte outer frame.

### MTU, MSS and application data are different sizes

For a basic IPv4 TCP packet with no IP/TCP options:

```text
interface MTU = 1500 bytes
IPv4 header   =   20 bytes
TCP header    =   20 bytes
TCP MSS       = 1460 bytes of TCP payload
```

MSS is a TCP payload limit advertised during the handshake; it is not the same field as MTU. TLS and HTTP headers consume additional space inside the TCP byte stream. With IPv6's 40-byte base header, the comparable TCP payload under a 1,500-byte MTU is normally 1,440 bytes before extension headers.

### What happens to an oversized packet

| Case | Network behavior | Feedback to sender |
|---|---|---|
| IPv4, DF bit clear | A router may fragment; the destination reassembles | Fragments arrive, but fragmentation is operationally fragile |
| IPv4, DF bit set | The constrained device drops the packet | ICMP Destination Unreachable, fragmentation needed — Type 3, Code 4 |
| IPv6 | Routers do not fragment in transit; the constrained device drops it | ICMPv6 Packet Too Big — Type 2; only the source may create IPv6 fragments |

Path MTU Discovery (PMTUD) uses that feedback to lower the sender's packet size. Blocking the required ICMP error messages can create a **PMTU black hole**: the TCP handshake and small requests succeed, but larger packets are repeatedly dropped.

### Detailed black-hole dry run

Assume the sender's interface MTU is 1,500, a VPN reduces the PMTU to 1,420, and IPv4 packets carry DF:

| Step | Packet/event | Result |
|---:|---|---|
| 1 | TCP SYN, about 60 bytes | Fits; VPN forwards it |
| 2 | SYN-ACK and ACK | Fit; connection becomes established |
| 3 | Small HTTP request | Fits; server receives it |
| 4 | Server sends a 1,500-byte IP packet | VPN cannot carry it and drops it |
| 5 | VPN sends ICMP Type 3/Code 4 with usable MTU | If delivered, server lowers its PMTU estimate and retransmits smaller packets |
| 6 | A NACL/firewall drops that ICMP message | Server keeps retransmitting oversized packets; user sees a stall or timeout |

This explains the apparently contradictory symptom “port 443 connects, but the page or file transfer hangs.” A successful handshake proves that **small** packets can cross; it does not prove every packet size can cross.

### Four concrete examples

1. A 64-byte `ping` succeeds, but a Linux IPv4 probe whose total IP size is 1,500 fails across a 1,420-byte VPN path.
2. An SSH login works, yet `scp` stalls when bulk transfer begins because larger segments expose the PMTU problem.
3. An HTTPS health check returns a tiny `200 OK`, but a large certificate chain or response times out.
4. Two EC2 instances communicate inside one high-MTU path, while the same payload fails after adding an internet, VPN or inspection hop with a smaller MTU.

### AWS-specific invariant

When VPC hosts have different effective MTUs or communicate through external paths, PMTUD control traffic must be able to return. AWS documents the relevant NACL permissions in both directions: IPv4 fragmentation-needed (ICMP Type 3, Code 4) and IPv6 Packet Too Big (ICMPv6 Type 2). A route and an allowed TCP port are not sufficient if the PMTUD feedback itself is discarded.

Do not assume every AWS hop has the same MTU. EC2 instance types, Transit Gateway, VPN, Direct Connect, load balancers, appliances and the public internet can place different limits on a path. The PMTU is still the minimum across the actual route.

### Troubleshooting sequence

1. Prove DNS, routes, Security Groups/NACLs and the listening port first; MTU should not be the first guess for every timeout.
2. Compare tiny traffic with a transfer large enough to fill multiple packets.
3. On Linux, `tracepath destination` can expose an observed PMTU. A controlled IPv4 probe such as `ping -M do -s 1472 destination` requests a 1,500-byte IP packet (1,472 payload + 8-byte ICMP + 20-byte IPv4 header); reduce the payload until it succeeds.
4. Capture traffic at the sender. Repeated retransmissions after large packets, without the expected ICMP error, are stronger evidence than one failed ping.
5. Inspect tunnel/inspection overhead and both directions of NACL/firewall policy. Do not “fix” the symptom by randomly shrinking every interface before identifying the narrow hop.

### Invariant and common mistakes

> Every emitted IP packet must be no larger than the sender's current PMTU estimate; a smaller-MTU hop must either provide usable feedback or the sender needs a conservative fallback.

Common mistakes:

- equating MTU with TCP MSS,
- testing only default-size `ping`,
- blocking all ICMP because it is mistaken for Echo only,
- assuming IPv6 routers fragment oversized packets,
- counting tunnel headers as application payload,
- changing MTU globally without proving where the bottleneck is.

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
arp -a
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
- AWS, Path MTU Discovery and network ACLs: https://docs.aws.amazon.com/vpc/latest/userguide/path_mtu_discovery.html
- RFC 8200, IPv6 specification: https://www.rfc-editor.org/rfc/rfc8200
- RFC 8201, IPv6 Path MTU Discovery: https://www.rfc-editor.org/rfc/rfc8201


---



[← Previous](00-networking-roadmap.md) · [Chapter index](index.md) · [Next →](02-home-wifi-to-internet.md)
