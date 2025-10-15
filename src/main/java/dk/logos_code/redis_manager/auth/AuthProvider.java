package dk.logos_code.redis_manager.auth;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Contract for pluggable authentication providers.
 */
public interface AuthProvider {
    Response login(AuthResource.LoginRequest body, UriInfo uriInfo);
    Response me(String token);
    Response logout(String token);
}
