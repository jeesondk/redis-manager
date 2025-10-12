package dk.logos_consult.redis_manager.redis;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Path("/api/redis")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RedisConnectionResource {

    @POST
    @Path("/test-connection")
    public ConnectionResult testConnection(ConnectionRequest req) {
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
        if (req.username != null && !req.username.isBlank()) {
            uri.setUsername(req.username);
        }
        if (req.password != null && !req.password.isBlank()) {
            uri.setPassword(req.password.toCharArray());
        }
        if (req.database != null) uri.setDatabase(req.database);
        uri.setTimeout(Duration.ofMillis(timeoutMs));

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
