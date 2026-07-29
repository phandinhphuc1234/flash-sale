# Quickstart: Authentication JWT Trust Foundation

## 1. Configure a public key

Provide the RSA public key through `JWT_PUBLIC_KEY_PEM`, or through the secret-mounted configuration
mechanism selected by the deployment. Never commit the private key. Set the same issuer, audience,
JWKS URI, and key id in authentication-service, api-gateway, and product-service.

Defaults:

- issuer: `http://authentication-service:8080`
- audience: `flash-sale-api`
- JWKS: `http://authentication-service:8080/.well-known/jwks.json`
- algorithm: `RS256`

## 2. Verify the metadata endpoint

```text
curl http://localhost:18081/.well-known/jwks.json
```

The response must contain an RSA public key with `alg=RS256`, `use=sig`, and a non-empty `kid`, and
must not contain private key fields.

## 3. Run verification

```text
.\mvnw.cmd -pl services/authentication-service -am verify
.\mvnw.cmd -pl services/api-gateway -am verify
.\mvnw.cmd -pl services/product-service -am verify
```

The contract tests cover valid tokens, wrong signature/issuer/audience/expiry, and the 401 versus 403
boundary. A real login/token-issuance smoke test is intentionally deferred to a later approved
feature.
