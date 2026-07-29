# JWT/JWKS Trust Contract

**Status**: Verified

## JWKS endpoint

`GET /.well-known/jwks.json`

Response: HTTP 200, `Content-Type: application/json`

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "<active-key-id>",
      "n": "<base64url-modulus>",
      "e": "AQAB"
    }
  ]
}
```

Only public fields are allowed. Private RSA parameters (`d`, `p`, `q`, `dp`, `dq`, `qi`) MUST NOT
appear.

## JWT validation

- `alg`: `RS256` only.
- `kid`: required and must select a key in the JWKS response.
- `iss`: exactly `http://authentication-service:8080` unless deployment configuration overrides the
  documented local/cluster URI consistently in all consumers.
- `aud`: contains `flash-sale-api`.
- `sub`: non-blank caller identifier.
- `iat` and `exp`: required for issued tokens and validated using the standard resource-server clock.
- `authorities`: array of strings; `CATALOG_ADMIN` is the canonical admin authority.

## Failure semantics

- Invalid/missing bearer token, signature, issuer, audience, `kid`, or expiry: HTTP 401 using the
  existing service/gateway error contract.
- Valid token without the authority required by an admin route: HTTP 403.
- JWKS/key configuration failure: service startup fails closed; no request is treated as authenticated.

## Compatibility

The contract is additive to existing routes. Test-only JWT decoders may override remote JWKS access in
isolated tests. Login, refresh, logout, revocation, and key-rotation APIs are not part of this contract.
