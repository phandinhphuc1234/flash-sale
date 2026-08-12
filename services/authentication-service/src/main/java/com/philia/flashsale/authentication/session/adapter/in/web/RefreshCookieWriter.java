package com.philia.flashsale.authentication.session.adapter.in.web;

import com.philia.flashsale.authentication.configuration.AuthenticationProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "runtime-enabled", havingValue = "true", matchIfMissing = true)
/** Central cookie policy adapter for secure refresh-cookie creation and clearing. */
public class RefreshCookieWriter {
    private final AuthenticationProperties properties;

    public RefreshCookieWriter(AuthenticationProperties properties) { this.properties = properties; }

    public void write(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", value)
                .httpOnly(true).secure(properties.cookie().secure()).path(properties.cookie().path())
                .sameSite(properties.cookie().sameSite()).maxAge(maxAgeSeconds).build();
        response.addHeader("Set-Cookie", cookie.toString());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }

    public void clear(HttpServletResponse response) { write(response, "", 0); }
}
