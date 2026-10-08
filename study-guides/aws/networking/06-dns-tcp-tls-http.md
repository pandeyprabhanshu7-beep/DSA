# DNS → Routing → TCP/QUIC → TLS → HTTP: A Browser Request in Time Order




![DNS TCP TLS HTTP flow](assets/diagrams/networking/06-dns-tcp-tls-http.svg)

## 1. The mistake this lecture fixes

When someone says “the website is down,” at least five different systems may be failing:

```text
Name resolution
IP routing
transport connection
TLS/security negotiation
HTTP/application behavior
```

A good engineer identifies **which stage failed** before changing anything.

## 2. Scenario

You enter:

```text
https://api.example.com/orders?id=42
```

Assume DNS eventually returns:

```text
api.example.com -> 198.51.100.20
```

We will trace the request from name to application response.

## 3. URL anatomy

```text
https://api.example.com:443/orders?id=42
|       |                |   |
scheme  hostname         port path/query
```

The port is often implicit:

```text
HTTP  -> 80 by convention
HTTPS -> 443 by convention
```

The hostname participates in DNS and TLS/HTTP behavior. It is not interchangeable with the IP address.

## 4. DNS: the naming layer

DNS translates names to records. For web access, A records can provide IPv4 addresses and AAAA records IPv6 addresses. CNAME and alias-style provider records can add indirection.

A full uncached conceptual lookup:

```text
Browser/OS stub
    |
    v
Recursive resolver
    |
    +--> Root server: “who handles .com?”
    |
    +--> .com TLD: “who is authoritative for example.com?”
    |
    +--> authoritative nameserver: “what is api.example.com?”
    |
    v
Answer cached and returned to client
```

Cloudflare's DNS tutorial uses this same resolver → root → TLD → authoritative mental model.

## 5. Caching changes what you observe

The answer might already be cached in:

- browser/application cache,
- OS stub resolver/cache,
- local router/enterprise DNS,
- recursive resolver.

Therefore, changing a DNS record does not guarantee every client sees the new value immediately. TTLs and negative caching matter.

### Debugging commands

```bash
nslookup api.example.com
dig api.example.com A
dig api.example.com AAAA
dig +trace example.com
```

`+trace` is a useful learning tool but differs from a normal recursive lookup path.

## 6. DNS can return different answers to different users

Reasons include:

- CDN/geo/latency routing,
- weighted routing,
- health-based failover,
- split-horizon/private DNS,
- IPv4 vs IPv6,
- resolver geography,
- cached older answer.

So “it resolves for me” does not prove another user's DNS path is identical.

## 7. AWS DNS mapping

### Route 53 authoritative DNS
Hosts public/private DNS zones and routing policies.

### VPC Resolver
AWS provides a DNS resolver to VPC resources. Current AWS docs describe the VPC Resolver/AmazonProvidedDNS as integrated into each AZ and reachable via special addresses including the VPC base + 2 IPv4 address and link-local resolver addresses.

### Route 53 Resolver endpoints/rules
Used to integrate VPC DNS with on-premises DNS systems for hybrid resolution.

A common hybrid failure:

```text
EC2 can route to on-prem network
BUT
internal.corp does not resolve
```

The route is fine; DNS forwarding/rules are wrong.

## 8. After DNS: choose a route to the returned IP

Suppose answer is `198.51.100.20`. The host routing table decides where the packet should go.

Typical home host:

```text
192.168.1.0/24 -> local
0.0.0.0/0      -> 192.168.1.1
```

The host does not ask DNS for a router. DNS answered the name question; the routing table answers the next-hop question.

## 9. TCP handshake

For classic HTTPS over TCP:

```text
Client                                  Server
51514 -> 443   SYN -------------------->
               <---------------- SYN/ACK
               ACK -------------------->
```

After this, both endpoints have connection state.

### Common outcomes

#### Connection timeout
Possible causes:

- route missing/blackhole,
- firewall silently drops,
- destination down,
- load balancer has no reachable path,
- return route missing,
- severe network loss.

#### Connection refused
Often means the destination path is reachable and something actively rejected the connection—commonly no process is listening on that port or a device sent a reset.

#### Immediate ICMP unreachable
A router/host is explicitly reporting a reachability problem.

The exact interpretation depends on environment, but these symptoms are more informative than “network issue.”

## 10. TCP source and destination ports

```text
client 192.168.1.25:51514
server 198.51.100.20:443
```

The server does **not** respond to destination port 443 on the client. It responds to the client's ephemeral port:

```text
server 198.51.100.20:443
 -> client 192.168.1.25:51514
```

This is why a stateful firewall can allow an outbound connection and automatically allow its response without a blanket inbound rule for port 443 on the client.

## 11. TCP reliability at beginner level

TCP numbers bytes, acknowledges received data, retransmits missing data, and implements flow/congestion control. It makes a lossy packet network look like a reliable ordered byte stream to the application under normal conditions.

But TCP cannot fix:

- a dead destination,
- permanent firewall blocks,
- application protocol bugs,
- excessive latency beyond application timeouts.

## 12. TLS: identity + confidentiality + integrity

After TCP is available, HTTPS performs TLS negotiation.

Modern TLS conceptually accomplishes:

1. agree on protocol/cryptographic parameters,
2. authenticate the server using its certificate/private-key proof and trust chain,
3. derive shared session keys,
4. protect subsequent application data with symmetric cryptography.

Cloudflare's TLS explanation notes that TLS handshakes authenticate, negotiate algorithms, and establish session keys; TLS 1.3 reduces handshake complexity compared with older versions.

## 13. Certificate hostname validation

If you request:

```text
https://api.example.com
```

the certificate needs to be valid for that hostname according to certificate rules. Connecting directly to the raw IP can produce a certificate/name mismatch even when routing and TCP work.

This is a frequent mistake when testing ALB/CloudFront/custom domains.

## 14. SNI: one IP can serve many TLS hostnames

Server Name Indication lets a client indicate the intended hostname during TLS negotiation so a shared endpoint can select an appropriate certificate/configuration.

This helps explain how CDNs/load balancers host many domains behind shared edge addresses.

## 15. HTTP: finally, application semantics

After the secure channel exists:

```http
GET /orders?id=42 HTTP/1.1
Host: api.example.com
Authorization: Bearer ...
```

Possible responses:

```text
200 OK        application succeeded
301/302       redirect
400           malformed/client request issue
401           authentication required/failed
403           understood but not permitted/policy block
404           route/resource not found
429           rate limit/throttling
500           application/server error
502/503/504   proxy/upstream/unavailable/timeout patterns
```

Do not map every 5xx to “network.” A load balancer can have perfect network connectivity to the client and still return 503 because no targets are healthy.

## 16. One browser page creates many dependency flows

HTML may reference:

```text
CSS from CDN A
JavaScript from CDN B
fonts from provider C
images from object storage/CDN
API calls to api.example.com
analytics endpoint
identity provider
```

Thus:

```text
“homepage HTML returned” != “page fully works”
```

Use browser developer tools/network waterfall to identify which dependent hostname/request failed.

## 17. HTTP/2 and HTTP/3 mental model

### HTTP/2
Typically runs over one TCP/TLS connection and multiplexes streams, reducing some connection overhead compared with older HTTP/1.1 usage patterns.

### HTTP/3
Runs over QUIC on UDP. QUIC integrates cryptographic/transport behavior and supports multiplexed streams without TCP's exact head-of-line behavior across all streams.

For troubleshooting, remember that “HTTPS” traffic may not always be TCP/443; HTTP/3 commonly uses QUIC/UDP on port 443.

## 18. Proxy and load balancer connection boundaries

A reverse proxy/load balancer can terminate one client connection and create a different backend connection:

```text
Client --TLS/TCP--> ALB --HTTP/TCP or HTTPS/TCP--> app target
```

Therefore there can be two distinct failures:

- client ↔ load balancer,
- load balancer ↔ target.

Logs/metrics and health checks help identify which boundary is broken.

## 19. TLS termination patterns

### At edge/CDN

```text
Client TLS -> CloudFront
CloudFront -> origin connection
```

### At ALB

```text
Client TLS -> ALB :443
ALB -> app :8080 or :443
```

### Pass-through to server
Layer-4 designs can forward encrypted traffic without terminating it at that intermediate layer.

Each pattern changes:

- certificate ownership,
- visibility for WAF/L7 routing,
- client IP propagation method,
- backend encryption,
- troubleshooting evidence.

## 20. DNS vs load balancing

DNS can return endpoint addresses, but DNS itself does not continuously proxy the connection.

Example:

```text
Route 53 -> returns ALB name/address mapping
Client -> directly establishes connection to ALB endpoint
```

This is why DNS TTL and load-balancer health are different layers.

## 21. Packet walk with state table

| Stage | Source → destination | What was solved? | Best evidence |
|---|---|---|---|
| DNS | client → resolver | hostname → record | `dig`, resolver logs |
| route | client/router | next hop | route table, traceroute |
| TCP | client ephemeral → 443 | transport session | `nc`, SYN/SYN-ACK capture |
| TLS | client ↔ TLS endpoint | encryption + server identity | `openssl s_client`, `curl -v` |
| HTTP | browser → web endpoint | app request | access logs/status/body |
| backend | LB/proxy → target | upstream request | LB logs, target logs/metrics |

## 22. Practical debugging commands

```bash
# DNS
dig api.example.com

# Does TCP 443 open?
nc -vz api.example.com 443

# Observe TLS and HTTP
curl -v https://api.example.com/

# Inspect certificate/TLS
openssl s_client -connect api.example.com:443 -servername api.example.com

# Route/hops
traceroute api.example.com
# or Windows: tracert
```

Use these only against systems/networks where you are authorized to test.

## 23. AWS example: browser to private app tier

```text
Browser
  ↓ DNS
Route 53
  ↓
CloudFront/WAF (optional)
  ↓ TLS/HTTP
ALB :443
  ↓ target selection
App :8080 in private subnet
  ↓
RDS :5432
```

Debug each boundary independently:

```text
DNS correct?
CloudFront origin healthy?
ALB listener/certificate?
ALB SG to app SG?
target group health?
app listening 8080?
app SG to DB SG 5432?
DB connection/auth?
```

## 24. Best references

- Cloudflare DNS: https://www.cloudflare.com/learning/dns/what-is-dns/
- Cloudflare recursive DNS: https://www.cloudflare.com/learning/dns/what-is-recursive-dns/
- Cloudflare TLS handshake: https://www.cloudflare.com/learning/ssl/what-happens-in-a-tls-handshake/
- AWS VPC Resolver: https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/resolver.html
- AWS Amazon DNS concepts: https://docs.aws.amazon.com/vpc/latest/userguide/AmazonDNS-concepts.html

## 25. Memory trick

> **Resolve → Route → Connect → Encrypt → Request.**

```text
DNS -> IP route -> TCP/QUIC -> TLS -> HTTP
```

When debugging, identify the first arrow that fails.


---



[← Previous](05-routing-isp-bgp-peering.md) · [Chapter index](index.md) · [Next →](07-ingress-egress-firewalls-dmz.md)
