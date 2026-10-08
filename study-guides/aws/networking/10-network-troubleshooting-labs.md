# Network Troubleshooting Labs — Stop Guessing, Prove the Broken Layer




![Troubleshooting decision tree](assets/diagrams/networking/11-troubleshooting.svg)

## 1. The golden rule

> **Change nothing until you can state the first layer where observed behavior differs from expected behavior.**

Randomly opening firewalls creates security risk and destroys evidence.

## 2. The troubleshooting ladder

Use this order:

```text
1. Name/DNS
2. Local address/interface
3. Local route/default gateway
4. End-to-end route/reachability
5. Firewall/security policy
6. Transport/listener
7. TLS
8. HTTP/application
9. downstream dependencies
```

A later layer cannot work if an earlier required layer is broken.

## 3. Build an evidence table before testing

| Question | Expected | Observed | Evidence |
|---|---|---|---|
| DNS A/AAAA | expected endpoint | ? | `dig` |
| source IP | expected interface | ? | `ip addr` |
| route | expected gateway/target | ? | `ip route get` / VPC RT |
| TCP port | opens | ? | `nc` / `curl` |
| TLS cert | valid hostname | ? | `openssl` |
| HTTP | 200/expected code | ? | `curl -v` |
| backend | healthy | ? | LB target health/logs |

This prevents “I think the route is correct” debugging.

## 4. Local host lab

Linux examples:

```bash
ip addrip route
ip route get 1.1.1.1
arp -n
resolvectl status   # systemd systems
ss -lntup
```

macOS equivalents include:

```bash
ifconfig
netstat -rn
route -n get default
arp -a
lsof -iTCP -sTCP:LISTEN
```

Windows:

```powershell
ipconfig /all
route print
arp -a
Get-NetTCPConnection -State Listen
```

### Exercise
Find your:

- active interface,
- private IP,
- prefix/netmask,
- default gateway,
- DNS resolvers,
- listening local ports.

## 5. DNS lab

```bash
nslookup example.com
dig example.com A
dig example.com AAAA
```

Then compare a specific resolver:

```bash
dig @1.1.1.1 example.com A
dig @8.8.8.8 example.com A
```

Do not assume answers will always be identical because CDNs/geo/routing/caching can influence results.

### Failure patterns

```text
NXDOMAIN      name does not exist according to DNS response
SERVFAIL      resolver/authoritative/validation/dependency problem possible
timeout       resolver path/firewall/network issue possible
wrong IP      stale/misconfigured DNS/cache/split-horizon issue
```

## 6. Test an IP path separately from DNS

If DNS returns `198.51.100.20`, you can reason about IP separately.

`ping` may be filtered, so do not require it.

Use application-relevant tests:

```bash
nc -vz host.example 443
curl -v https://host.example/
```

For HTTP services where you need to force a particular IP while preserving hostname/SNI, `curl --resolve` can be a powerful authorized diagnostic:

```bash
curl -v --resolve api.example.com:443:203.0.113.25 https://api.example.com/
```

This isolates DNS from TLS/HTTP endpoint testing.

## 7. Route/path tools

```bash
traceroute example.com
mtr example.com
```

Windows:

```powershell
tracert example.com
pathping example.com
```

Interpret carefully. Missing intermediate responses do not necessarily mean packet forwarding failed.

## 8. Transport test

### Port open

```bash
nc -vz api.example.com 443
```

### Server listener

Linux:

```bash
ss -lntp | grep ':8080'
```

If a process listens only on:

```text
127.0.0.1:8080
```

remote load balancers cannot normally reach it via the host's private ENI address. Bind to the correct interface according to application/security requirements.

## 9. TLS test

```bash
openssl s_client \
  -connect api.example.com:443 \
  -servername api.example.com
```

Inspect:

- certificate subject/SAN,
- issuer/chain,
- expiration,
- negotiated TLS version/cipher,
- hostname/SNI behavior.

A successful TCP connection plus failed TLS narrows the problem dramatically.

## 10. HTTP test

```bash
curl -v https://api.example.com/health
```

Observe:

```text
resolved IP
connection target
TLS messages
HTTP status
redirect Location header
server/proxy headers (when present)
response timing
```

### Status interpretation

```text
403 from WAF != TCP failure
503 from ALB != DNS failure
504 often indicates upstream timeout path
401 != firewall denial
```

## 11. Packet capture

Authorized Linux capture:

```bash
sudo tcpdump -ni any host 198.51.100.20 and port 443
```

You might see:

```text
SYN sent, no SYN/ACK        path/drop/destination issue
SYN, SYN/ACK, ACK           TCP established
TLS ClientHello, alert      TLS negotiation/certificate/protocol issue
HTTP request + response     network and transport are functioning
RST                          active reset/refusal/closed state
```

Wireshark provides deeper visual decoding.

Do not capture traffic you are not authorized to inspect; payloads can contain sensitive data.

## 12. NAT troubleshooting

Questions:

```text
Is source translation expected?
What public IP should destination see?
Does NAT have translation/session capacity?
Is return traffic addressed to translated tuple?
Is vendor allowlisting the translated public IP?
Is there double NAT/CGNAT?
```

A common vendor integration test is to call an endpoint that reports the source public IP, or inspect vendor logs, then compare with expected NAT EIP.

## 13. Stateful firewall troubleshooting

Check session logs/table:

```text
did SYN enter?
which policy matched?
was source NAT applied?
did SYN leave?
did SYN/ACK return?
did it return through same stateful device/path?
was it dropped by threat/IPS policy?
```

A stateful firewall with no return packet cannot complete the flow even if its allow rule looks correct.

## 14. AWS troubleshooting checklist — EC2 to Internet

```text
[ ] DNS resolution works
[ ] EC2 has correct private IP
[ ] subnet route table has expected default route
[ ] NAT/IGW target exists and is in correct state
[ ] NAT Gateway public subnet itself routes to IGW
[ ] SG egress allows required flow
[ ] NACL permits both directions
[ ] Network Firewall/inspection routes are symmetric/correct
[ ] destination permits the NAT/public source
[ ] IPv4/IPv6 family is what you expect
```

## 15. AWS troubleshooting checklist — Internet to ALB

```text
[ ] DNS points to correct ALB/CloudFront endpoint
[ ] ALB scheme is internet-facing
[ ] ALB subnets have appropriate Internet routing
[ ] ALB SG allows client source/443
[ ] NACLs do not block
[ ] listener exists on expected port/protocol
[ ] certificate/SNI is correct
[ ] WAF is not blocking unexpectedly
[ ] targets are healthy
```

## 16. AWS troubleshooting checklist — ALB to app

```text
[ ] target IP/instance registered
[ ] target health-check port/path correct
[ ] APP-SG permits source ALB-SG
[ ] app subnet route/local path correct
[ ] NACL permits flow/return
[ ] process listens on expected interface/port
[ ] host firewall permits
[ ] app health endpoint returns expected status fast enough
```

## 17. AWS troubleshooting checklist — app to RDS

```text
[ ] DNS endpoint resolves
[ ] route/local/VPC connectivity exists
[ ] DB-SG permits APP-SG on DB port
[ ] NACLs permit flow
[ ] DB is available/listening
[ ] credentials/auth method valid
[ ] TLS requirement/client trust correct
[ ] connection pool not exhausted
```

A DB authentication error proves much more network path success than a timeout.

## 18. VPC Flow Logs dry run

Imagine record metadata indicates:

```text
srcaddr=10.0.11.25
dstaddr=10.0.21.10
srcport=53120
dstport=5432
protocol=6
action=REJECT
```

This immediately focuses investigation on network policy/path around that ENI/subnet observation point rather than SQL credentials.

If action is `ACCEPT` but app times out, investigate:

- another hop/policy,
- host firewall,
- service listener,
- app-level timeout,
- return path,
- packet loss/MTU.

## 19. Reachability Analyzer lab

Use Reachability Analyzer to model a path such as:

```text
EC2 ENI -> another ENI
EC2 -> Internet Gateway path
source -> destination through TGW/peering supported resources
```

Important: it statically analyzes configuration. It does not prove that your application process is listening or that a runtime service is healthy.

## 20. Route-table lab

Given:

```text
10.0.0.0/16  local
10.50.0.0/16 tgw
0.0.0.0/0    nat
```

Answer:

```text
10.0.12.8   -> local
10.50.8.8   -> TGW
8.8.8.8     -> NAT
```

Now add:

```text
10.50.8.0/24 firewall-endpoint
```

Destination `10.50.8.8` now matches `/24` and takes the more specific firewall route.

## 21. NACL ephemeral-port lab

Client:

```text
10.0.1.25:53000 -> 10.0.2.50:443
```

Stateless subnet ACL reasoning must cover:

```text
forward packet to destination 443
reverse packet to client 53000
```

If the return-direction ACL blocks the ephemeral port, the connection fails even though inbound 443 looks correct.

## 22. SG statefulness lab

SG permits outbound HTTPS from client.

```text
client ephemeral -> server 443
```

The response is recognized as return traffic for the established allowed connection under SG statefulness. You do not add a broad “inbound 443” rule to the client just to receive that response.

## 23. IPv4 vs IPv6 split test

```bash
curl -4 -v https://example.com/
curl -6 -v https://example.com/
```

If one works and the other fails, stop treating the problem as one generic “Internet” issue. Compare DNS AAAA, IPv6 route, egress-only/IGW path, SG/NACL IPv6 rules, and upstream support.

## 24. MTU/VPN symptom lab

Scenario:

```text
SSH login works
small HTTP response works
large upload stalls over VPN
```

Investigate MTU/MSS/Path MTU Discovery and tunnel overhead after proving route/policy basics.

Useful tools vary by OS; controlled pings with DF/size options and packet captures can reveal fragmentation-needed/ICMP behavior.

## 25. “Works from bastion but not app server”

Compare exactly:

```text
source subnet
source SG
route table association
NACL
DNS resolver/search path
NAT/egress IP
proxy environment variables
IPv6 availability
host firewall
```

Do not conclude “AWS network is random.” The two hosts are different network principals/paths.

## 26. “Works by IP but not hostname”

High probability areas:

- DNS resolution,
- split-horizon zone,
- stale cache,
- search suffix,
- proxy/SNI/Host header behavior.

If HTTPS works only by hostname and fails by IP, that may be expected because TLS certificate/SNI and virtual hosting depend on hostname.

## 27. “Works in browser but curl fails”

Compare:

- proxy settings,
- client certificates,
- browser DNS/DoH,
- cookies/authentication,
- HTTP/2/3 behavior,
- user agent/WAF policy,
- system trust store vs browser trust store.

## 28. “403 means firewall” — wrong

A network firewall normally drops/rejects before an HTTP application response. An HTTP `403` means some HTTP-speaking component generated a response—perhaps WAF, proxy, API gateway, application authorization.

That is valuable evidence that DNS/routing/transport/TLS likely got much farther than a raw network drop.

## 29. Minimal incident notes template

```text
Client/source:
Timestamp + timezone:
Hostname:
Resolved A/AAAA:
Source public egress IP:
Expected destination/port:
Route path expectation:
TCP result:
TLS result:
HTTP result:
Relevant SG/NACL/firewall rule IDs:
Flow-log evidence:
LB/WAF/app log evidence:
Recent changes:
```

This is vastly more useful than “network not working.”

## 30. Best tools/reference trail

- VPC Flow Logs basics: https://docs.aws.amazon.com/vpc/latest/userguide/flow-logs-basics.html
- Flow-log records: https://docs.aws.amazon.com/vpc/latest/userguide/flow-log-records.html
- Reachability Analyzer: https://docs.aws.amazon.com/vpc/latest/reachability/what-is-reachability-analyzer.html
- Route priority: https://docs.aws.amazon.com/vpc/latest/userguide/route-tables-priority.html

## 31. Memory trick

> **D-A-R-E-P-T-A = DNS, Address, Route, Edge translation, Policy, Transport, Application.**

Write the evidence for each letter. The first failed letter is where investigation begins.


---



[← Previous](09-aws-internet-vpc-packet-flow.md) · [Chapter index](index.md) · [Next →](11-networking-cheatsheet.md)
