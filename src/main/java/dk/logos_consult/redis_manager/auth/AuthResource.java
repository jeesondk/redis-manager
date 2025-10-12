package dk.logos_consult.redis_manager.auth;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final String COOKIE_NAME = "rm_session";
    private static final int SESSION_TTL_SECONDS = 30 * 60; // 30 minutes

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    public record LoginRequest(String username, String password) {}
    public record MeResponse(String username) {}
    public record ErrorResponse(String error) {}

    @POST
    @Path("/login")
    public Response login(LoginRequest body, @Context UriInfo uriInfo) {
        if (body == null || isBlank(body.username()) || isBlank(body.password())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("username and password are required"))
                    .build();
        }

        String expectedUser = getEnv("REDIS_MANAGER_USER");
        String expectedPass = getEnv("REDIS_MANAGER_PASS");

        // If both env vars are missing, use default built-in admin credentials
        if (expectedUser == null && expectedPass == null) {
            expectedUser = "admin";
            expectedPass = "Pa$$W0rd!";
        } else if (expectedUser == null || expectedPass == null) {
            // One is set but not the other: treat as misconfiguration
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Server auth is misconfigured: both REDIS_MANAGER_USER and REDIS_MANAGER_PASS must be set, or neither (to use defaults)"))
                    .build();
        }

        if (!expectedUser.equals(body.username()) || !expectedPass.equals(body.password())) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Invalid username or password"))
                    .build();
        }

        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(SESSION_TTL_SECONDS);
        SESSIONS.put(token, new Session(body.username(), expiresAt));

        NewCookie cookie = buildSessionCookie(token, SESSION_TTL_SECONDS);
        return Response.ok(new MeResponse(body.username()))
                .cookie(cookie)
                .build();
    }

    @GET
    @Path("/me")
    public Response me(@CookieParam(COOKIE_NAME) String token) {
        Session session = validate(token);
        if (session == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Not authenticated"))
                    .build();
        }
        return Response.ok(new MeResponse(session.username)).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@CookieParam(COOKIE_NAME) String token) {
        if (token != null) {
            SESSIONS.remove(token);
        }
        // expire the cookie on client
        NewCookie expired = buildSessionCookie("", 0);
        return Response.noContent().cookie(expired).build();
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
