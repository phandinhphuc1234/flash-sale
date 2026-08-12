package com.philia.flashsale.authentication.security.jwks;

import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publishes public verification material; no private signing material crosses this boundary. */
@RestController
/** Public JWKS endpoint exposing only the configured verification key. */
public class JwksController {

    private final RSAKey publicJwk;

    public JwksController(RSAKey publicJwk) {
        this.publicJwk = publicJwk;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return new JWKSet(publicJwk.toPublicJWK()).toJSONObject();
    }
}
