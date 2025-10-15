package dk.logos_code.redis_manager.auth;

import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Basic username/password auth with server-side session cookie.
 * Extracted from AuthResource to follow pluggable provider pattern.
 */
public class BasicAuthProvider implements AuthProvider {

    private final String cookieName;
    private final int sessionTtlSeconds;

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    public BasicAuthProvider(String cookieName, int sessionTtlSeconds) {
        this.cookieName = cookieName;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    @Override
    public Response login(AuthResource.LoginRequest body, UriInfo uriInfo) {
        if (body == null || isBlank(body.username()) || isBlank(body.password())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new AuthResource.ErrorResponse("username and password are required"))
                    .build();
        }

        String expectedUser = getEnv("REDIS_MANAGER_USER");
        String expectedPass = getEnv("REDIS_MANAGER_PASS");

        // If both env vars are missing, use default built-in admin credentials
        if (expectedUser == null && expectedPass == null) {
            expectedUser = "admin";
            expectedPass = "Pa$$W0rd!";
            System.out.println("Using built-in admin credentials for server auth");
        } else if (expectedUser == null || expectedPass == null) {
            // One is set but not the other: treat as misconfiguration
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new AuthResource.ErrorResponse("Server auth is misconfigured: both REDIS_MANAGER_USER and REDIS_MANAGER_PASS must be set, or neither (to use defaults)"))
                    .build();
        }

        if (!expectedUser.equals(body.username()) || !expectedPass.equals(body.password())) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new AuthResource.ErrorResponse("Invalid username or password"))
                    .build();
        }

        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(sessionTtlSeconds);
        SESSIONS.put(token, new Session(body.username(), expiresAt));

        NewCookie cookie = buildSessionCookie(token, sessionTtlSeconds);
        return Response.ok(new AuthResource.MeResponse(body.username()))
                .cookie(cookie)
                .build();
    }

    @Override
    public Response me(String token) {
        Session session = validate(token);
        if (session == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new AuthResource.ErrorResponse("Not authenticated"))
                    .build();
        }
        return Response.ok(new AuthResource.MeResponse(session.username)).build();
    }

    @Override
    public Response logout(String token) {
        if (token != null) {
            SESSIONS.remove(token);
        }
        // expire the cookie on client
        NewCookie expired = buildSessionCookie("", 0);
        return Response.noContent().cookie(expired).build();
    }

    private NewCookie buildSessionCookie(String value, int maxAgeSeconds) {
        // Note: set secure=false to work in dev over http; consider enabling Secure in production behind TLS
        return new NewCookie(
                this.cookieName,
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

    private static class Session {
        final String username;
        final Instant expiresAt;
        Session(String username, Instant expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }

    private static Session validate(String token) {
        if (token == null || token.isEmpty()) return null;
        Session s = SESSIONS.get(token);
        if (s == null) return null;
        if (Instant.now().isAfter(s.expiresAt)) {
            SESSIONS.remove(token);
            return null;
        }
        return s;
    }
}
