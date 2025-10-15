package dk.logos_code.redis_manager.redis.resources;

import dk.logos_code.redis_manager.redis.ConnectionRequest;
import dk.logos_code.redis_manager.redis.datamodels.ConnectionResult;
import dk.logos_code.redis_manager.redis.RedisService;
import dk.logos_code.redis_manager.redis.datamodels.ConnectionResponse;
import dk.logos_code.redis_manager.redis.datamodels.CreateConnectionRequest;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.PathParam;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Path("/api/redis/connections")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RedisConnectionResource {

    @Inject
    RedisService redisService;

    @GET
    @Path("/")
    public List<ConnectionResponse> listConnections() {
        return redisService.getConnections();
    }

    @GET
    @Path("/{id}")
    public ConnectionResponse getConnection (@PathParam("id") long id) {
        return redisService.getConnection(id);
    }

    @POST
    @Path("/")
    public ConnectionResponse createConnection(CreateConnectionRequest req) {
        if (req == null) throw new BadRequestException("Missing body");
        if (req.name() == null || req.name().isBlank()) throw new BadRequestException("Missing name");

        return redisService.addConnection(req);
    }

    @PATCH
    @Path("/{id}")
    public ConnectionResponse updateConnection(@PathParam("id") long id, CreateConnectionRequest req) {
        return redisService.updateConnection(id, req);
    }

    @DELETE
    @Path("/{id}")
    public void deleteConnection(@PathParam("id") long id) {

        redisService.deleteConnection(id);
    }

    @POST
    @Path("/test-connection")
    public ConnectionResult testConnection(ConnectionRequest req) {
        //TODO: fix this needs to handle the connection ID
        if (req == null || req.type == null) {
            return ConnectionResult.error(null, "Missing connection type");
        }
        int timeoutMs = req.timeoutMs != null ? req.timeoutMs : 3000;

        try {
            return switch (req.type) {
                case node -> testNode(req, timeoutMs);
                case sentinel -> testSentinel(req, timeoutMs);
                case cluster -> testCluster(req, timeoutMs);
            };
        } catch (Exception e) {
            return ConnectionResult.error(req.type.name(), rootMessage(e));
        }
    }

    private ConnectionResult testNode(ConnectionRequest req, int timeoutMs) {
        String url = normalizeUrl(req);
        if (url == null || url.isBlank()) {
            return ConnectionResult.error("node", "Missing url");
        }
        RedisURI uri;
        try {
            uri = RedisURI.create(url);
        } catch (IllegalArgumentException e) {
            return ConnectionResult.error("node", "Invalid URL: " + e.getMessage());
        }

        applyCredentialsAndSettings(uri, req, timeoutMs);

        RedisClient client = RedisClient.create(uri);
        try (StatefulRedisConnection<String, String> conn = client.connect()) {
            RedisCommands<String, String> cmd = conn.sync();
            String pong = cmd.ping();
            String infoServer = safeInfo(cmd);
            return ConnectionResult.ok("node", "PING=" + pong + (infoServer != null ? ("; " + infoServer) : ""));
        } finally {
            client.shutdown();
        }
    }
    private void applyCredentialsAndSettings(RedisURI uri, ConnectionRequest req, int timeoutMs) {
        parseCredentials(uri, timeoutMs, req.username, req.password, req.database, req);
    }

    public static void parseCredentials(RedisURI uri, int timeoutMs, String username, String password, Integer database, ConnectionRequest req) {
        if (username != null && !username.isBlank()) {
            uri.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            uri.setPassword(password.toCharArray());
        }
        if (database != null) {
            uri.setDatabase(database);
        }
        uri.setTimeout(Duration.ofMillis(timeoutMs));
    }

    private ConnectionResult testSentinel(ConnectionRequest req, int timeoutMs) {
        // Expect either urls list or comma-separated in url field
        List<HostAndPort> sentinels = parseHosts(req);
        if (sentinels.isEmpty()) {
            return ConnectionResult.error("sentinel", "Provide at least one sentinel host:port in urls or url");
        }
        if (req.sentinelMasterId == null || req.sentinelMasterId.isBlank()) {
            return ConnectionResult.error("sentinel", "Missing sentinelMasterId (master name)");
        }

        RedisURI.Builder builder = null;
        for (HostAndPort hp : sentinels) {
            if (builder == null) {
                builder = RedisURI.Builder.sentinel(hp.host, hp.port, req.sentinelMasterId);
            } else {
                builder.withSentinel(hp.host, hp.port);
            }
        }
        if (builder == null) {
            return ConnectionResult.error("sentinel", "Failed to build sentinel URI");
        }
        if (req.username != null && !req.username.isBlank()) {
            builder.withAuthentication(req.username, req.password != null ? req.password : "");
        } else if (req.password != null && !req.password.isBlank()) {
            builder.withPassword(req.password);
        }
        builder.withTimeout(Duration.ofMillis(timeoutMs));

        RedisURI uri = builder.build();
        RedisClient client = RedisClient.create();
        try (StatefulRedisConnection<String, String> conn = client.connect(uri)) {
            RedisCommands<String, String> cmd = conn.sync();
            String pong = cmd.ping();
            String infoServer = safeInfo(cmd);
            return ConnectionResult.ok("sentinel", "PING=" + pong + (infoServer != null ? ("; " + infoServer) : ""));
        } finally {
            client.shutdown();
        }
    }

    private ConnectionResult testCluster(ConnectionRequest req, int timeoutMs) {
        List<HostAndPort> nodes = parseHosts(req);
        if (nodes.isEmpty()) {
            // try single URL
            String url = normalizeUrl(req);
            if (url != null && !url.isBlank()) {
                try {
                    RedisURI u = RedisURI.create(url);
                    nodes = List.of(new HostAndPort(u.getHost(), u.getPort() == 0 ? 6379 : u.getPort()));
                } catch (Exception ignored) {}
            }
        }
        if (nodes.isEmpty()) {
            return ConnectionResult.error("cluster", "Provide at least one cluster node as host:port in urls or a redis:// URL");
        }

        List<RedisURI> uris = new ArrayList<>();
        for (HostAndPort hp : nodes) {
            RedisURI.Builder b = RedisURI.Builder.redis(hp.host, hp.port).withTimeout(Duration.ofMillis(timeoutMs));
            if (req.username != null && !req.username.isBlank()) {
                b.withAuthentication(req.username, req.password != null ? req.password : "");
            } else if (req.password != null && !req.password.isBlank()) {
                b.withPassword(req.password);
            }
            uris.add(b.build());
        }

        RedisClusterClient clusterClient = RedisClusterClient.create(uris);
        try (StatefulRedisClusterConnection<String, String> conn = clusterClient.connect()) {
            RedisAdvancedClusterCommands<String, String> cmd = conn.sync();
            String pong = cmd.ping();
            String infoServer = safeClusterInfo(cmd);
            return ConnectionResult.ok("cluster", "PING=" + pong + (infoServer != null ? ("; " + infoServer) : ""));
        } finally {
            clusterClient.shutdown();
        }
    }

    private String normalizeUrl(ConnectionRequest req) {
        String url = req.url;
        if (url == null && req.urls != null && req.urls.size() == 1) {
            url = req.urls.get(0);
        }
        if (url != null) url = url.trim();
        return url;
    }

    private List<HostAndPort> parseHosts(ConnectionRequest req) {
        List<String> inputs = new ArrayList<>();
        if (req.urls != null) inputs.addAll(req.urls);
        if (req.url != null && req.url.contains(",")) {
            inputs.addAll(Arrays.stream(req.url.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
        }
        List<HostAndPort> result = new ArrayList<>();
        for (String s : inputs) {
            if (s == null || s.isBlank()) continue;
            String v = s.trim();
            if (v.startsWith("redis://") || v.startsWith("rediss://")) {
                try {
                    RedisURI uri = RedisURI.create(v);
                    result.add(new HostAndPort(uri.getHost(), uri.getPort() == 0 ? 6379 : uri.getPort()));
                } catch (Exception ignored) {}
            } else if (v.contains(":")) {
                String[] hp = v.split(":", 2);
                try {
                    result.add(new HostAndPort(hp[0].trim(), Integer.parseInt(hp[1].trim())));
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    private String safeInfo(RedisCommands<String, String> cmd) {
        try {
            String info = cmd.info("server");
            if (info != null) {
                // return a short line like redis_version and mode
                for (String line : info.split("\n")) {
                    if (line.startsWith("redis_version") || line.startsWith("role:")) {
                        return line.trim();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String safeClusterInfo(RedisAdvancedClusterCommands<String, String> cmd) {
        try {
            String info = cmd.clusterInfo();
            if (info != null) {
                String[] parts = info.split("\\s+");
                return Arrays.stream(parts).filter(p -> p.startsWith("cluster_state") || p.startsWith("cluster_slots_ok")).collect(Collectors.joining(","));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        String last = null;
        while (cur != null) {
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) last = cur.getMessage();
            cur = cur.getCause();
        }
        return last != null ? last : t.toString();
    }

    record HostAndPort(String host, int port) {}
}
