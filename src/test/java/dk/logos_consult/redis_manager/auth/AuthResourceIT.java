package dk.logos_consult.redis_manager.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class AuthResourceIT {

    private static final String USER_KEY = "REDIS_MANAGER_USER";
    private static final String PASS_KEY = "REDIS_MANAGER_PASS";

    @BeforeAll
    static void setUpCreds() {
        // Ensure deterministic credentials for the test app instance
        System.setProperty(USER_KEY, "it-user");
        System.setProperty(PASS_KEY, "it-pass");
    }

    @Test
    void meWithoutCookieIsUnauthorized() {
        given()
            .when().get("/api/auth/me")
            .then()
            .statusCode(401)
            .body("error", containsString("Not authenticated"));
    }

    @Test
    void loginMeAndLogoutHappyPath() {
        // Login
        var loginResponse =
            given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"it-user\",\"password\":\"it-pass\"}")
            .when()
                .post("/api/auth/login")
            .then()
                .statusCode(200)
                .header("Set-Cookie", containsString("rm_session"))
                .body("username", equalTo("it-user"))
                .extract();

        String cookie = loginResponse.header("Set-Cookie");

        // Call /me with session cookie
        given()
            .header("Cookie", cookie)
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(200)
            .body("username", equalTo("it-user"));

        // Logout
        given()
            .header("Cookie", cookie)
            .contentType(ContentType.JSON)
        .when()
            .post("/api/auth/logout")
        .then()
            .statusCode(anyOf(is(204), is(200))); // Quarkus may normalize no-content

        // /me should be unauthorized after logout
        given()
            .header("Cookie", cookie)
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(401);
    }
}
