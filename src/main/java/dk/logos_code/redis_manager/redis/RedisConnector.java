package dk.logos_code.redis_manager.redis;

import dk.logos_code.redis_manager.redis.datamodels.ConnectionConfig;
import dk.logos_code.redis_manager.redis.datamodels.RedisHostAndPort;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.quarkus.logging.Log;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Low-level Redis connection helper responsible for constructing URIs and
 * performing basic client interactions (e.g., fetching INFO sections).
 *
 * This extraction helps RedisService focus on domain logic (like parsing
 * databases) and keeps connection details in one place.
 */
@ApplicationScoped
public final class RedisConnector implements AutoCloseable {

    public RedisConnector() {}
    private RedisClient clientInstance = null;
    private ClientResources clientResources = null;

    public StatefulRedisConnection<String, String> Connect(ConnectionConfig req, int timeoutMs) {
        var uri = buildUri(req, timeoutMs);
        if (clientInstance == null) {
            throw new IllegalStateException("RedisClient not initialized. Call GetClient() first.");
        }
        StatefulRedisConnection<String, String> conn = clientInstance.connect(uri);
        try {
            String pong = conn.sync().ping();
            if (!"PONG".equals(pong)) {
                Log.error("Failed to connect to Redis at " + uri + ", ping returned: " + pong);
                conn.close();
                throw new RuntimeException("Failed to connect to Redis at " + uri);
            }
            Log.info("Connected to Redis at " + uri);
        } catch (Exception e) {
            try { conn.close(); } catch (Exception ignored) {}
            throw e;
        }
        return conn;
    }

    public void Disconnect() {
        if (clientInstance != null) {
            try {
                clientInstance.shutdown();
            } finally {
                clientInstance = null;
            }
        }
        if (clientResources != null) {
            try {
                clientResources.shutdown();
            } finally {
                clientResources = null;
            }
        }
    }

    public RedisConnector GetClient(){
        if(clientInstance == null){
            CreateClient();
        }
        return this;
    }


    /**
     * Build a RedisURI for node or sentinel request types. For cluster, caller should
     * handle differently (typically not using logical DBs).
     * Throws IllegalArgumentException for invalid inputs so callers can map to HTTP errors.
     */
    private RedisURI buildUri(ConnectionConfig req, int timeoutMs) {
        if (req == null || req.type == null) throw new IllegalArgumentException("Missing connection type");
        return switch (req.type) {
            case node -> buildNodeUri(req, timeoutMs);
            case sentinel -> buildSentinelUri(req, timeoutMs);
            //TODO: Add support for cluster
            case cluster -> throw new IllegalArgumentException("Cluster uses different client/URI handling");
        };
    }

    private void CreateClient() {
        clientResources = DefaultClientResources.create();
        clientInstance = RedisClient.create(clientResources);
    }

    static RedisURI buildNodeUri(ConnectionConfig req, int timeoutMs) {
        String url = normalizeUrl(req);
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Missing url");
        RedisURI uri = RedisURI.create(url);
        applyCredentialsAndSettings(uri, req, timeoutMs);
        return uri;
    }

    static RedisURI buildSentinelUri(ConnectionConfig req, int timeoutMs) {
        List<RedisHostAndPort> sentinels = parseHosts(req);
        if (sentinels.isEmpty()) throw new IllegalArgumentException("No sentinels provided");
        if (req.sentinelMasterId == null || req.sentinelMasterId.isBlank()) throw new IllegalArgumentException("Missing sentinelMasterId");
        RedisURI.Builder builder = null;
        for (RedisHostAndPort hp : sentinels) {
            if (builder == null) builder = RedisURI.Builder.sentinel(hp.host(), hp.port(), req.sentinelMasterId);
            else builder.withSentinel(hp.host(), hp.port());
        }
        if (req.username != null && !req.username.isBlank())
            builder.withAuthentication(req.username, req.password != null ? req.password : "");
        else if (req.password != null && !req.password.isBlank())
            builder.withAuthentication("", req.password);
        builder.withTimeout(Duration.ofMillis(timeoutMs));
        return builder.build();
    }

    static void applyCredentialsAndSettings(RedisURI uri, ConnectionConfig req, int timeoutMs) {
        if (req.username != null && !req.username.isBlank()) {
            uri.setUsername(req.username);
        }
        if (req.password != null && !req.password.isBlank()) {
            uri.setPassword(req.password.toCharArray());
        }
        if (req.database != null) {
            uri.setDatabase(req.database);
        }
        uri.setTimeout(Duration.ofMillis(timeoutMs));
    }

    static String normalizeUrl(ConnectionConfig req) {
        // Prefer first entry from urls list
        if (req.urls != null && !req.urls.isEmpty()) {
            String first = req.urls.get(0);
            if (first != null) first = first.trim();
            if (first != null && !first.isBlank()) {
                if (!first.contains("://")) first = "redis://" + first;
                return first;
            }
        }
        return null;
    }

    static List<RedisHostAndPort> parseHosts(ConnectionConfig req) {
        List<RedisHostAndPort> res = new ArrayList<>();
        if (req.urls != null && !req.urls.isEmpty()) {
            for (String s : req.urls) {
                if (s == null || s.isBlank()) continue;
                String[] hp = s.trim().split(":");
                String host = hp[0];
                int port = hp.length > 1 ? Integer.parseInt(hp[1]) : 26379;
                res.add(new RedisHostAndPort(host, port));
            }
        }
        return res;
    }

    @Override
    public void close() throws Exception {
        this.Disconnect();
    }

    @PreDestroy
    public void cleanup() {
        Log.info("Shutting down RedisConnector resources");
        try {
            close();
        } catch (Exception e) {
            Log.error("Error during RedisConnector cleanup", e);
        }
    }
}
