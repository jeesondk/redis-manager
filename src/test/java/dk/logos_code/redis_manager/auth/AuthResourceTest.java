package dk.logos_code.redis_manager.auth;

import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AuthResourceTest {

    private static final String USER_KEY = "REDIS_MANAGER_USER";
    private static final String PASS_KEY = "REDIS_MANAGER_PASS";

    @AfterEach
    void cleanupProps() {
        System.clearProperty(USER_KEY);
        System.clearProperty(PASS_KEY);
    }

    @Test
    void loginSucceedsWithSystemProperties() {
        System.setProperty(USER_KEY, "u1");
        System.setProperty(PASS_KEY, "p1");

        AuthResource resource = new AuthResource();
        Response resp = resource.login(new AuthResource.LoginRequest("u1", "p1"), null);
        assertEquals(200, resp.getStatus(), "login should succeed with correct credentials");
        Object entity = resp.getEntity();
        assertTrue(entity instanceof AuthResource.MeResponse, "entity should be MeResponse");
        AuthResource.MeResponse me = (AuthResource.MeResponse) entity;
        assertEquals("u1", me.username());
        NewCookie cookie = resp.getCookies().get("rm_session");
        assertNotNull(cookie, "session cookie should be set");
        assertNotNull(cookie.getValue());
        assertFalse(cookie.getValue().isEmpty());
    }

    @Test
    void loginFailsWithMisconfigurationWhenOnlyUserProvided() {
        System.setProperty(USER_KEY, "only-user");
        System.clearProperty(PASS_KEY);

        AuthResource resource = new AuthResource();
        Response resp = resource.login(new AuthResource.LoginRequest("only-user", "irrelevant"), null);
        assertEquals(500, resp.getStatus(), "should return 500 when only one of user/pass is configured");
    }

    @Test
    void meReturnsUnauthorizedWithoutCookie() {
        AuthResource resource = new AuthResource();
        Response resp = resource.me(null);
        assertEquals(401, resp.getStatus());
    }

    @Test
    void logoutIsIdempotent() {
        AuthResource resource = new AuthResource();
        Response resp = resource.logout(null);
        assertEquals(204, resp.getStatus());
    }
}
