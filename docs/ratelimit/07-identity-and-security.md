# 07 - Identity, HMAC, and Trusted Proxy

**Document status**: Approved living design for Feature 013  
**Canonical sources**: [spec](../../specs/013-gateway-redis-rate-limiter/spec.md),
[data model](../../specs/013-gateway-redis-rate-limiter/data-model.md)

## 1. Security Goal

The limiter needs a stable bucket identity, but it must not trust spoofable caller data or expose
caller identity through Redis keys, responses, logs, metrics, traces, or baggage.

## 2. Approved Identity Strategy

Feature 013 implements only:

| Strategy | Trusted source | Missing source outcome |
|----------|----------------|------------------------|
| `CLIENT_IP` | Normalized direct socket address | Skip Redis, forward downstream once, record identity-unavailable signal, emit no quota headers. |

`Forwarded` and `X-Forwarded-For` are ignored. Gateway pins
`server.forward-headers-strategy=none` so Boot does not rewrite the direct peer before resolver code
runs.

Authenticated-user, user-or-IP, and API-key strategies are deferred until authentication contracts
exist for selected routes.

## 3. Raw Identity Lifetime

Raw address bytes may exist only long enough to normalize and compute the HMAC. Do not create a
record/object that stores both raw and hashed values because generated `toString()`, debugger views,
or accidental logging can leak identity.

The identity boundary output is only the identity type plus opaque Redis key material.

## 4. HMAC Construction

The HMAC input is length-prefixed and versioned:

```text
u32be(length("gateway-rate-limit-key-k1")) + UTF-8("gateway-rate-limit-key-k1")
u32be(length(environment))                 + UTF-8(environment)
u32be(length(policyStateVersion))          + UTF-8(policyStateVersion)
u32be(length(policyId))                    + UTF-8(policyId)
u32be(length("CLIENT_IP"))                 + UTF-8("CLIENT_IP")
u32be(length(addressBytes))                + addressBytes
```

Approved output:

```text
HMAC-SHA-256(secret, input) -> 64 lowercase hexadecimal characters
```

The physical Redis key is `rl:k1:<digest>`. The digest may appear only as the Redis key identity; it
must not appear in HTTP, logs, metrics, traces, or baggage.

## 5. Secret Rules

- The limiter secret is dedicated and must not reuse JWT signing keys or application secrets.
- Configuration uses standard Java Basic/RFC 4648 Base64.
- Enabled configuration must decode to at least 32 bytes.
- Missing, malformed, URL/MIME-style, whitespace-containing, or too-short secrets fail startup.
- Validation messages/logs must identify only the property and safe reason; never echo the secret.
- Runtime cannot prove entropy; operators must generate bytes with a CSPRNG.
- Secret rotation intentionally creates a fresh bucket namespace.

The implementation creates and initializes a fresh `javax.crypto.Mac` per derivation. Do not share a
mutable `Mac` across concurrent WebFlux requests.

## 6. Address Normalization

- IPv4 resolves to four address bytes.
- IPv6 resolves to sixteen address bytes.
- IPv4-mapped IPv6 normalizes to the equivalent IPv4 bytes.
- Scope IDs and textual formatting are not part of identity.
- Missing or unsupported address data is identity-unavailable, not a shared `unknown` bucket.

## 7. Deferred Trusted Proxy Work

Do not enable this direct-IP strategy behind Kubernetes ingress yet. A later feature must approve:

- trusted proxy CIDRs or hop count;
- accepted `Forwarded` or `X-Forwarded-For` format;
- spoofing tests;
- secret distribution and coordinated rotation in the cluster.

Until then, arbitrary forwarding headers remain untrusted.

## 8. Forbidden Exposure

Never expose raw IP, JWT, user ID, API-key ID/secret, HMAC secret, Redis key, or full digest through:

- response body/header;
- metric tags;
- trace attributes or baggage;
- normal structured logs;
- exception messages returned to clients.
