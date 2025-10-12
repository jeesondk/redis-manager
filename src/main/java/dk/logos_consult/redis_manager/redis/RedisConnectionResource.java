package dk.logos_consult.redis_manager.redis;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.PathParam;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Path("/api/redis")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RedisConnectionResource {

    private static final Map<Long, ConnectionConfig> STORE = new ConcurrentHashMap<>();
    private static final AtomicLong IDGEN = new AtomicLong(1);

    @GET
    @Path("/connections")
    public List<ConnectionConfig> listConnections() {
        return STORE.values().stream()
                .sorted((a,b) -> Long.compare(a.id, b.id))
                .toList();
    }

    @POST
    @Path("/connections")
    public ConnectionConfig create(ConnectionConfig cfg) {
        if (cfg == null) throw new BadRequestException("Missing body");
        if (cfg.name == null || cfg.name.isBlank()) throw new BadRequestException("Missing name");
        long id = IDGEN.getAndIncrement();
        cfg.id = id;
        STORE.put(id, cfg);
        return cfg;
    }

    @PUT
    @Path("/connections/{id}")
    public ConnectionConfig update(@PathParam("id") long id, ConnectionConfig cfg) {
        ConnectionConfig existing = STORE.get(id);
        if (existing == null) throw new NotFoundException("Not found");
        cfg.id = id;
        STORE.put(id, cfg);
        return cfg;
    }

    @DELETE
    @Path("/connections/{id}")
    public void delete(@PathParam("id") long id) {
        STORE.remove(id);
    }

    @POST
    @Path("/connections/{id}/connect")
    public ConnectionResult connect(@PathParam("id") long id) {
        ConnectionConfig cfg = STORE.get(id);
        if (cfg == null) throw new NotFoundException("Not found");
        ConnectionRequest req = toRequest(cfg);
        return testConnection(req);
    }

    @GET
    @Path("/connections/{id}/databases")
    public List<Database> databases(@PathParam("id") long id) {
        ConnectionConfig cfg = STORE.get(id);
        if (cfg == null) throw new NotFoundException("Not found");
        return fetchDatabases(toRequest(cfg));
    }

    private ConnectionRequest toRequest(ConnectionConfig cfg) {
        ConnectionRequest r = new ConnectionRequest();
        r.type = cfg.type;
        r.url = cfg.url;
        r.urls = cfg.urls;
        r.username = cfg.username;
        r.password = cfg.password;
        r.sentinelMasterId = cfg.sentinelMasterId;
        r.database = cfg.database;
        r.timeoutMs = cfg.timeoutMs;
        return r;
    }

    public static class Database {
        public int index;
        public Integer keys; // may be null if unknown
        public Database() {}
        public Database(int index, Integer keys) { this.index = index; this.keys = keys; }
    }

    private List<Database> fetchDatabases(ConnectionRequest req) {
        int timeoutMs = req.timeoutMs != null ? req.timeoutMs : 3000;
        if (req.type == ConnectionType.cluster) {
            // Cluster supports only DB 0
            List<Database> l = new ArrayList<>();
            l.add(new Database(0, null));
            return l;
        }
        // node or sentinel: connect and parse INFO keyspace
        RedisURI uri;
        if (req.type == ConnectionType.node) {
            String url = normalizeUrl(req);
            if (url == null || url.isBlank()) throw new BadRequestException("Missing url");
            uri = RedisURI.create(url);
            if (req.username != null && !req.username.isBlank()) uri.setUsername(req.username);
            if (req.password != null && !req.password.isBlank()) uri.setPassword(req.password.toCharArray());
            if (req.database != null) uri.setDatabase(req.database);
            uri.setTimeout(java.time.Duration.ofMillis(timeoutMs));
        } else {
            // sentinel
            List<HostAndPort> sentinels = parseHosts(req);
            if (sentinels.isEmpty()) throw new BadRequestException("No sentinels provided");
            if (req.sentinelMasterId == null || req.sentinelMasterId.isBlank()) throw new BadRequestException("Missing sentinelMasterId");
            RedisURI.Builder builder = null;
            for (HostAndPort hp : sentinels) {
                if (builder == null) builder = RedisURI.Builder.sentinel(hp.host, hp.port, req.sentinelMasterId);
                else builder.withSentinel(hp.host, hp.port);
            }
            if (req.username != null && !req.username.isBlank()) builder.withAuthentication(req.username, req.password != null ? req.password : "");
            else if (req.password != null && !req.password.isBlank()) builder.withPassword(req.password);
            builder.withTimeout(java.time.Duration.ofMillis(timeoutMs));
            uri = builder.build();
        }
        RedisClient client = RedisClient.create();
        try (StatefulRedisConnection<String, String> conn = client.connect(uri)) {
            RedisCommands<String, String> cmd = conn.sync();
            String info = cmd.info("keyspace");
            return parseDbInfo(info);
        } finally {
            client.shutdown();
        }
    }

    private List<Database> parseDbInfo(String info) {
        List<Database> out = new ArrayList<>();
        if (info == null || info.isBlank()) return out;
        String[] lines = info.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.startsWith("db")) continue;
            // format: db0:keys=1,expires=0,avg_ttl=0
            try {
                int colon = line.indexOf(':');
                String dbName = line.substring(0, colon);
                int idx = Integer.parseInt(dbName.substring(2));
                Integer keys = null;
                String[] parts = line.substring(colon+1).split(",");
                for (String p : parts) {
                    p = p.trim();
                    if (p.startsWith("keys=")) {
                        keys = Integer.parseInt(p.substring("keys=".length()));
                        break;
                    }
                }
                out.add(new Database(idx, keys));
            } catch (Exception ignored) {}
        }
        if (out.isEmpty()) {
            // fallback to just db0
            out.add(new Database(0, null));
        }
        // sort by index
        out.sort((a,b) -> Integer.compare(a.index, b.index));
        return out;
    }

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
