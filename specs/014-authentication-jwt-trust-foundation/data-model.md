# Data Model: Authentication JWT Trust Foundation

This feature adds no durable business tables.

## Runtime configuration

| Name | Meaning | Source | Rule |
|---|---|---|---|
| issuer | JWT `iss` value | environment/configuration | exact URI `http://authentication-service:8080` by default |
| audience | accepted `aud` value | environment/configuration | must contain `flash-sale-api` |
| keyId | active JWK `kid` | environment/configuration | non-blank |
| publicKey | RSA verification key | deployment secret/mounted secret | parseable RSA public key; never private |

## Wire representations

- JWT claims are not persisted by this feature.
- JWKS contains public RSA parameters (`kty`, `use`, `alg`, `kid`, `n`, `e`).
