package dk.logos_code.redis_manager.auth;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * OIDC-based auth provider leveraging Quarkus OIDC security.
 *
 * Notes:
 * - Login flow is handled via the OIDC browser redirect configured in Quarkus.
 * - This provider exposes /me to return the current authenticated principal name.
 * - Logout typically requires OIDC end-session configuration; here we return 204 and let the frontend/provider handle it.
 */
public class OidcAuthProvider implements AuthProvider {

    @Override
    public Response login(AuthResource.LoginRequest body, UriInfo uriInfo) {
        // In OIDC, login should be initiated via browser redirect to the provider.
        // We return 501 to guide clients to use the provider flow.
        return Response.status(Response.Status.NOT_IMPLEMENTED)
                .entity(new AuthResource.ErrorResponse("Use OIDC login flow initiated by the browser/provider"))
                .build();
    }

    @Override
    public Response me(String ignoredToken) {
        SecurityIdentity identity = getIdentity();
        if (identity == null || identity.isAnonymous()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new AuthResource.ErrorResponse("Not authenticated"))
                    .build();
        }
        String name = identity.getPrincipal().getName();
        return Response.ok(new AuthResource.MeResponse(name)).build();
    }

    @Override
    public Response logout(String ignoredToken) {
        // No server session cookie to clear. For OIDC, front-channel/back-channel logout can be configured in Quarkus.
        // Here we simply return 204 so the client can proceed with provider logout if desired.
        return Response.noContent().build();
    }

    private SecurityIdentity getIdentity() {
        try {
            return CDI.current().select(SecurityIdentity.class).get();
        } catch (Exception e) {
            return null;
        }
    }
}
