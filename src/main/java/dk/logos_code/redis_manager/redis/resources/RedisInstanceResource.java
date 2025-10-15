package dk.logos_code.redis_manager.redis.resources;

import dk.logos_code.redis_manager.redis.RedisService;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/api/redis/instances")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RedisInstanceResource {

    @Inject
    RedisService redisService;

    @GET
    @Path("/{id}/databases")
    public Map<Integer, Long> databases(@PathParam("id") long id) {
        try {
            return redisService.getDatabases(id);
        }
        catch (Exception e) {
            Log.error("Failed to get databases", e);
            throw new BadRequestException();
        }
    }

    @GET
    @Path("/{id}/{database}/keys")
    public List<String> keys(@PathParam("id") long id, @PathParam("database") int dbIndex, @QueryParam("pattern") String pattern, @QueryParam("count") int count) {
        try {
            return redisService.listKeys(id, dbIndex, pattern, count);
        }
        catch (Exception e) {
            Log.error("Failed to get keys", e);
            throw new BadRequestException();
        }
    }
}
