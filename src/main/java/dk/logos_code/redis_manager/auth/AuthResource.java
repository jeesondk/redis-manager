package dk.logos_code.redis_manager.auth;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final String COOKIE_NAME = "rm_session";
    private static final int SESSION_TTL_SECONDS = 30 * 60; // 30 minutes
    private static final String AUTH_PROVIDER_KEY = "AUTH_PROVIDER";


    public record LoginRequest(String username, String password) {}
    public record MeResponse(String username) {}
    public record ErrorResponse(String error) {}
    public record ProviderResponse(String provider) {}

    private enum Provider { BASIC, OIDC, ENTRAID }

    private static Provider providerFromEnv() {
        String raw = getEnv(AUTH_PROVIDER_KEY);
        if (raw == null || raw.isBlank()) return Provider.BASIC; // default
        String norm = raw.trim().toUpperCase();
        return switch (norm) {
            case "BASIC" -> Provider.BASIC;
            case "OIDC" -> Provider.OIDC;
            case "ENTRAID", "ENTRA_ID", "ENTRA" -> Provider.ENTRAID;
            default -> null;
        };
    }

    @POST
    @Path("/login")
    public Response login(LoginRequest body, @Context UriInfo uriInfo) {
        Provider provider = providerFromEnv();
        if (provider == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Invalid AUTH_PROVIDER value. Accepted: Basic, OIDC, EntraID"))
                    .build();
        }

        AuthProvider auth;
        switch (provider) {
            case BASIC -> auth = new BasicAuthProvider(COOKIE_NAME, SESSION_TTL_SECONDS);
            case OIDC -> auth = new OidcAuthProvider();
            case ENTRAID -> {
                return Response.status(Response.Status.NOT_IMPLEMENTED)
                        .entity(new ErrorResponse(provider + " login is not implemented yet"))
                        .build();
            }
            default -> {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new ErrorResponse("Invalid AUTH_PROVIDER value. Accepted: Basic, OIDC, EntraID"))
                        .build();
            }
        }
        return auth.login(body, uriInfo);
    }

    @GET
    @Path("/me")
    public Response me(@CookieParam(COOKIE_NAME) String token) {
        Provider provider = providerFromEnv();
        if (provider == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Invalid AUTH_PROVIDER value. Accepted: Basic, OIDC, EntraID"))
                    .build();
        }
        AuthProvider auth;
        switch (provider) {
            case BASIC -> auth = new BasicAuthProvider(COOKIE_NAME, SESSION_TTL_SECONDS);
            case OIDC -> auth = new OidcAuthProvider();
            case ENTRAID -> {
                return Response.status(Response.Status.NOT_IMPLEMENTED)
                        .entity(new ErrorResponse(provider + " provider is not implemented yet"))
                        .build();
            }
            default -> {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new ErrorResponse("Invalid AUTH_PROVIDER value. Accepted: Basic, OIDC, EntraID"))
                        .build();
            }
        }
        return auth.me(token);
    }

    @POST
    @Path("/logout")
    public Response logout(@CookieParam(COOKIE_NAME) String token) {
        Provider provider = providerFromEnv();
        if (provider == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Invalid AUTH_PROVIDER value. Accepted: Basic, OIDC, EntraID"))
                    .build();
        }
        AuthProvider auth;
        switch (provider) {
            case BASIC -> auth = new BasicAuthProvider(COOKIE_NAME, SESSION_TTL_SECONDS);
            case OIDC -> auth = new OidcAuthProvider();
            case ENTRAID -> {
                // For EntraID we still return 501 since not implemented yet
                NewCookie expired = buildSessionCookie("", 0);
                return Response.status(Response.Status.NOT_IMPLEMENTED)
                        .cookie(expired)
                        .entity(new ErrorResponse(provider + " logout is not implemented yet"))
                        .build();
            }
            default -> {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new ErrorResponse("Invalid AUTH_PROVIDER value. Accepted: Basic, OIDC, EntraID"))
                        .build();
            }
        }
        return auth.logout(token);
    }

    @GET
    @Path("/provider")
    public Response provider() {
        Provider provider = providerFromEnv();
        if (provider == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Invalid AUTH_PROVIDER value. Accepted: Basic, OIDC, EntraID"))
                    .build();
        }
        String name = switch (provider) {
            case BASIC -> "Basic";
            case OIDC -> "OIDC";
            case ENTRAID -> "EntraID";
        };
        return Response.ok(new ProviderResponse(name)).build();
    }

    private static NewCookie buildSessionCookie(String value, int maxAgeSeconds) {
        // Note: set secure=false to work in dev over http; consider enabling Secure in production behind TLS
        return new NewCookie(
                COOKIE_NAME,
                value,
                "/",
                null,
                1,
                "Session for Redis Manager",
                maxAgeSeconds,
                null,
                false, // secure
                true    // httpOnly
        );
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String getEnv(String key) {
        String v = System.getenv(key);
        if (v != null) return v;
        // Try common fallbacks: map env to config style if using SmallRye (optional)
        v = System.getProperty(key);
        return v;
    }


}
