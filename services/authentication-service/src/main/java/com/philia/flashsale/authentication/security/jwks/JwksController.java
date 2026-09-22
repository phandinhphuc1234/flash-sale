package com.philia.flashsale.authentication.security.jwks;

import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Publishes public verification material; no private signing material crosses this boundary. */
@RestController
/** Public JWKS endpoint exposing only the configured verification key. */
@Tag(name = "Identity trust")
public class JwksController {

    private final RSAKey publicJwk;

    public JwksController(RSAKey publicJwk) {
        this.publicJwk = publicJwk;
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Publish JWT verification keys", description = "Returns public JWK material only; private signing material is never exposed.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Public JWK set returned")
    })
    public Map<String, Object> jwks() {
        return new JWKSet(publicJwk.toPublicJWK()).toJSONObject();
    }
}
