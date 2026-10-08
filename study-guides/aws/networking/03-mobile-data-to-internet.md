# Phone on Mobile Data → the Internet: 4G/5G Mental Model




![Mobile data path](assets/diagrams/networking/03-mobile-data.svg)

## 1. Why mobile data deserves its own flow

When your phone leaves Wi‑Fi and uses cellular data, the application still speaks IP to remote services, but the **access network and subscriber/session machinery are different**.

A useful simplified path is:

```text
App on phone
  ↓
IP stack on phone
  ↓
4G/5G radio access
  ↓
cell site / base station
  ↓
mobile transport/backhaul
  ↓
mobile packet core
  ↓
operator policy + address translation / Internet edge
  ↓
peering/transit
  ↓
website/cloud/CDN
```

This is deliberately simplified. Mobile cores contain many control-plane functions, policy/QoS functions, subscriber databases, and user-plane anchors.

## 2. Two planes: control plane vs user plane

This distinction is the key to understanding cellular networks.

### Control plane
Handles things such as:

- authentication/subscriber identity,
- mobility and registration,
- session establishment,
- policy/QoS decisions,
- selecting user-plane functions,
- signaling during handover.

### User plane
Carries the actual application packets after connectivity is established.

```text
Control plane: “Who is this subscriber and how should the session work?”
User plane:    “Carry these IP packets to/from the data network.”
```

In 5G terminology, the **UPF (User Plane Function)** is a major user-plane component that forwards UE traffic between the radio access side and Data Networks such as the Internet. GSMA descriptions of 5G connectivity model a PDU session as the connectivity abstraction between applications on the UE and a Data Network.

## 3. Terms you should recognize

| Term | Meaning at beginner level |
|---|---|
| UE | User Equipment: phone/modem/device |
| RAN | Radio Access Network |
| eNodeB | 4G/LTE base station term |
| gNB | 5G NR base station term |
| EPC | 4G Evolved Packet Core |
| 5GC | 5G Core |
| UPF | 5G user-plane forwarding/anchor function |
| PGW | 4G packet data network gateway function |
| APN | 4G-style access point/profile concept for data services |
| DNN | 5G Data Network Name; selects/identifies data-network connectivity context |
| PDU session | 5G connectivity session between UE and a data network |

You do not need to memorize every 3GPP interface to understand AWS networking. Learn the packet path and plane separation first.

## 4. Step 1 — the phone attaches/registers with the mobile network

Unlike home Wi‑Fi, cellular access is tightly integrated with operator subscriber identity and mobility.

Conceptually:

1. Phone discovers suitable radio service.
2. SIM/eSIM-based subscriber credentials participate in authentication.
3. Network establishes control-plane context.
4. The device requests packet-data connectivity.
5. Policy/session functions select how user traffic should be carried.
6. A user-plane path is established toward a Data Network such as the Internet.

The exact procedures differ by generation and deployment mode. The learning objective is not protocol-message memorization; it is to understand that **IP packets ride on top of a managed mobile session**.

## 5. Step 2 — the phone gets IP connectivity

The carrier provides addressing/session information. Depending on the network and device, the phone may receive IPv4, IPv6, or dual-stack connectivity.

For IPv4, carriers commonly use address sharing/NAT because public IPv4 space is scarce. The address shown inside the phone may not be the public address seen by an Internet website.

Possible simplified path:
```text
Phone IP
  ↓
mobile core user-plane tunnel
  ↓
carrier NAT/CGNAT
  ↓
public Internet address
```

IPv6 changes the addressing model because globally unique IPv6 addresses do not require IPv4-style NAT merely to conserve addresses, although firewall/policy controls are still needed.

## 6. Step 3 — radio traffic reaches the base station

Your IP packet is carried over radio protocols to the serving cell/base station. Radio conditions affect:

- latency,
- retransmissions,
- throughput,
- handover behavior,
- packet loss/jitter under congestion,
- power usage.

A full cellular stack is far more complex than “Wi‑Fi but farther.” Spectrum scheduling, mobility, QoS, and operator core behavior all participate.

## 7. Step 4 — transport/backhaul moves traffic into the packet core

Cell sites connect into provider transport networks using fiber, microwave, Ethernet/IP/MPLS, segment routing, or other carrier technologies. The provider must carry both control signaling and user traffic reliably across its network.

This middle network is largely invisible to the app. Your phone does not have a routing table entry for every cell-site/core router.

## 8. Step 5 — packet core forwards toward the Data Network

In 5G, user traffic is carried through the user-plane architecture to a UPF. A UPF can be placed centrally or closer to the edge depending on latency/service design.

Conceptual user-plane flow:

```text
UE
 -> gNB
 -> user-plane tunnel
 -> UPF
 -> Data Network (Internet/private enterprise network/edge application)
```

GSMA material describes the UPF as forwarding UE traffic between access networks such as 5G gNBs and Data Networks, with QoS enforcement under session-control policy.

## 9. Why carriers use multiple UPFs / local breakout

If every packet from every city had to travel to one distant national gateway before reaching a nearby app, latency and transport cost could be poor.

A provider can place user-plane functions closer to users or selected services:

```text
Phone in Atlanta
 -> local/regional UPF
 -> nearby CDN/edge service
```

instead of:

```text
Phone in Atlanta
 -> distant national core anchor
 -> back across network
 -> nearby destination
```

This is one reason mobile/edge architectures discuss local breakout.

## 10. What happens when you visit a website on mobile data

After the cellular session exists, application-layer behavior resembles other IP access methods:

```text
1. DNS lookup
2. destination IP selected
3. route/user-plane forwarding toward Internet
4. TCP handshake or QUIC setup
5. TLS handshake
6. HTTP request
7. response/data packets return through carrier path
```

The access network changed, but DNS/IP/transport/TLS/HTTP concepts remain reusable.

## 11. Mobile data vs Wi‑Fi: side-by-side

| Question | Home Wi‑Fi | Mobile data |
|---|---|---|
| First wireless hop | Wi‑Fi AP | cellular base station |
| Subscriber/auth model | local WLAN password/enterprise auth | SIM/eSIM + operator authentication |
| Gateway to wider network | home router/CPE | carrier packet core/user plane |
| Common IPv4 translation | home NAT, possibly ISP CGNAT | carrier CGNAT/NAT common |
| Mobility | usually reconnect/roam among WLAN APs | core/RAN designed for wide-area mobility/handover |
| Policy/QoS | home/enterprise WLAN policies | carrier subscriber/session/QoS policies |
| Internet routing | ISP after CPE | mobile operator Internet edge/peering |

## 12. Why your public IP changes when switching Wi‑Fi ↔ cellular

You changed access networks and therefore the Internet egress path.

```text
Wi-Fi:
phone -> home NAT -> home ISP -> Internet

Mobile:
phone -> carrier RAN/core -> carrier Internet edge -> Internet
```

The source public address seen by a website can change. Existing TCP sessions may break because the endpoint path/address changed unless a higher-level protocol/system supports mobility/resumption.

## 13. Tethering/hotspot: another NAT boundary can appear

When a phone shares mobile data as a hotspot, the phone may act like a small router for connected devices:

```text
Laptop private hotspot IP
 -> phone hotspot NAT/routing
 -> mobile session
 -> carrier NAT (possible)
 -> Internet
```

This can create multiple address-translation layers, similar to home NAT + CGNAT.

## 14. Enterprise private mobile networking

Mobile networks can connect devices to private enterprise networks rather than the public Internet. With appropriate carrier/private-5G design, the Data Network can be an enterprise environment or edge workload.

That means “mobile data” does not necessarily equal “public Internet.” The packet core can steer traffic according to the service/session design.

## 15. Troubleshooting mobile data by layer

### No cellular service
Check radio coverage, SIM/eSIM state, airplane mode, network registration.

### Signal exists but no data
Session/APN/DNN/subscriber provisioning, carrier outage, device IP configuration, policy.

### Some apps fail but others work
DNS, IPv4/IPv6 behavior, MTU, app endpoint, carrier filtering, TLS/certificate, service-specific issue.

### Works on Wi‑Fi, fails on mobile
Compare:

- resolved DNS answers,
- IPv4 vs IPv6 path,
- source address/geolocation restrictions,
- CGNAT/port behavior,
- MTU/path behavior,
- application WAF/rate-limiting policy.

### Works on mobile, fails on Wi‑Fi
Home DNS/router/ISP/firewall is more suspect than the destination application.

## 16. AWS connection: mobile client hitting an AWS app

A common path can be:

```text
Phone app
 -> carrier RAN + packet core
 -> carrier Internet edge
 -> Internet/peering
 -> Route 53-resolved endpoint
 -> CloudFront / API Gateway / ALB
 -> private application resources
```

The AWS VPC does not care that the first hop was 5G. By the time traffic arrives at the public AWS endpoint, it is IP traffic with source/destination addressing and transport/application protocol state.

## 17. Security implication: source IP may represent a shared carrier egress

With carrier NAT, many subscribers can share public IPv4 addresses over time or concurrently through port translation. Therefore:

- source IP is not a strong user identity,
- IP allowlists for consumer/mobile users can be brittle,
- rate limiting solely by source IP can unfairly group users,
- audit systems should use authenticated application identities in addition to network metadata.

## 18. Real-world mental exercise

Open a public “what is my IP” site on Wi‑Fi, note the address, then disable Wi‑Fi and repeat on cellular. Do not treat the result as sensitive credentials, but notice how the visible Internet egress identity changes.

Then test:

```text
DNS resolver behavior
IPv4 address
IPv6 availability
latency to same site
```

The goal is not benchmarking; it is observing that the **same phone/app can traverse two very different access networks**.

## 19. Internet/reference trail

- GSMA cellular ecosystem material describing 5G connectivity and UPF forwarding: https://www.gsma.com/solutions-and-impact/technologies/internet-of-things/wp-content/uploads/2023/02/ACJA_4_UAS_Cellular_Ecosystem_Whitepaper.pdf
- 3GPP specifications portal: https://portal.3gpp.org/
- Ericsson overview of packet core/user plane: https://www.ericsson.com/en/portfolio/cloud-software-and-services/cloud-core/packet-core/cloud-packet-core/packet-core-gateway
- RFC 6598 CGNAT shared space: https://www.rfc-editor.org/rfc/rfc6598

## 20. What to remember

> **Cellular changes the access and session machinery, not the fundamental need for DNS, IP routing, transport, security, and application protocols.**

Memory path:

```text
UE -> RAN -> mobile transport -> packet core/UPF -> carrier edge/NAT -> Internet -> service
```


---



[← Previous](02-home-wifi-to-internet.md) · [Chapter index](index.md) · [Next →](04-ip-subnetting-nat-cgnat.md)
