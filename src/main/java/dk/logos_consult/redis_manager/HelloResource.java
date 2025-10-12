package dk.logos_consult.redis_manager;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/hello")
public class HelloResource {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Message hello() {
        return new Message("Hello from Quarkus REST!");
    }

    public static record Message(String message) {}
}
