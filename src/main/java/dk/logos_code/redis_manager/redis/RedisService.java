package dk.logos_code.redis_manager.redis;

import dk.logos_code.redis_manager.redis.commands.RedisGetValue;
import dk.logos_code.redis_manager.redis.commands.RedisListKeys;
import dk.logos_code.redis_manager.redis.datamodels.*;
import dk.logos_code.redis_manager.redis.mappers.ConnectionRequestMapper;
import dk.logos_code.redis_manager.redis.mappers.ConnectionResponseMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dk.logos_code.redis_manager.redis.commands.RedisDbCounts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service encapsulating Redis interactions, including listing logical databases.
 */
@ApplicationScoped
public class RedisService {

    @Inject
    RedisConnectionRepository repo;
    @Inject
    RedisConnector connector;
    @Inject
    ConnectionRequestMapper requestMapper;
    @Inject
    ConnectionResponseMapper responseMapper;


    /**
     * Return available logical databases for the provided connection request.
     * For clusters, only DB 0 is supported.
     */
    public Map<Integer, Long> getDatabases(long connectionId) throws Exception {
        ConnectionConfig req = repo.get(connectionId);

        if (req == null)
            throw new Exception("Not found");

        try(RedisConnector redisConnector = connector.GetClient()){
            try(StatefulRedisConnection<String, String> conn = redisConnector.Connect(req, req.timeoutMs)){
                return RedisDbCounts.getDbKeyCounts(conn);
            }

        }
    }

    public List<RedisKeyInfo> listKeys(long connectionId, int dbIndex, String matchPattern, int scanCount) throws Exception {
        ConnectionConfig req = repo.get(connectionId);
        if (req == null)
            throw new Exception("Not found");

        try(RedisConnector redisConnector = connector.GetClient()){
            try(StatefulRedisConnection<String, String> conn = redisConnector.Connect(req, req.timeoutMs)){
                List<RedisKeyInfo> keys = new ArrayList<>();
                RedisListKeys.streamKeysWithTypeInDbPipelined(conn, dbIndex, matchPattern, scanCount, keys::add);
                return keys;
            }
        }
    }

    public RedisValue getKeyValue(long connectionId, int dbIndex, String key) throws Exception {
        ConnectionConfig req = repo.get(connectionId);
        if (req == null)
            throw new Exception("Not found");

        try(RedisConnector redisConnector = connector.GetClient()){
            try(StatefulRedisConnection<String, String> conn = redisConnector.Connect(req, req.timeoutMs)){
                return RedisGetValue.get(conn, dbIndex, key);
            }
        }
    }

    public ConnectionResponse addConnection(CreateConnectionRequest req) {
        var connCfg = requestMapper.toConfig(req);
        var conn = repo.store(connCfg);

        return responseMapper.toResponse(conn);
    }

    public List<ConnectionResponse> getConnections() {
        return repo.getAll().stream()
                .map(responseMapper::toResponse)
                .toList();
    }

    public ConnectionResponse getConnection(long id) {
        ConnectionConfig cfg = repo.get(id);
        if (cfg == null) return null;
        return responseMapper.toResponse(cfg);
    }

    public ConnectionResponse updateConnection(long id, CreateConnectionRequest req) {
        var connCfg = repo.get(id);
        Objects.requireNonNull(req, "req");
        // Name (if provided)
        if (notBlank(req.name())) {
            connCfg.name = req.name().trim();
        }

        // Type from req.type() or fallback from serverInfo.mode
        connCfg.type = req.serverInfo().mode();

        // Credentials
        if (req.credentials() != null) {
            if (notBlank(req.credentials().username())) {
                connCfg.username = req.credentials().username().trim();
            } else {
                connCfg.username = null;
            }
            if (notBlank(req.credentials().password())) {
                connCfg.password = req.credentials().password();
            } else {
                connCfg.password = null;
            }
        }

        // Sentinel master id (only meaningful for sentinel mode, but we store whatever is sent)
        connCfg.sentinelMasterId = blankToNull(req.sentinelMasterId());

        // Server info: db, timeout, urls
        RedisServerInfo si = req.serverInfo();
        if (si != null) {
            // db / timeout
            connCfg.database = si.defaultDb();
            connCfg.timeoutMs = si.timeoutMs();

            // rebuild urls from hosts + port
            connCfg.urls = buildUrlsFromHosts(si);
        }

        // Optional: validate consistency
        validateConfig(connCfg);

        return responseMapper.toResponse(repo.update(connCfg));
    }

    public void deleteConnection(long id) {
        repo.delete(id);
    }

    private List<String> buildUrlsFromHosts(RedisServerInfo si) {
        String[] hosts = si.hosts();
        int port = si.port() > 0 ? si.port() : 6379;

        if (hosts == null || hosts.length == 0) {
            return null; // or keep previous cfg.urls if you prefer; change to 'return cfg.urls;'
        }

        List<String> list = new ArrayList<>(hosts.length);
        for (String h : hosts) {
            if (notBlank(h)) list.add(h.trim() + ":" + port);
        }
        return list.isEmpty() ? null : list;
    }

    private void validateConfig(ConnectionConfig cfg) {
        // Minimal checks; expand as needed
        if (cfg.type == RedisConnectionType.sentinel && !notBlank(cfg.sentinelMasterId)) {
            throw new IllegalArgumentException("sentinelMasterId is required for sentinel connections");
        }
        if ((cfg.urls == null || cfg.urls.isEmpty())) {
            throw new IllegalArgumentException("At least one endpoint (host:port) is required");
        }
        // Ensure ports are present; if your UI always sets port, this can be removed
        for (String ep : cfg.urls) {
            if (ep == null || !ep.contains(":")) {
                throw new IllegalArgumentException("Endpoint must include port: " + ep);
            }
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
    private static String blankToNull(String s) { return notBlank(s) ? s.trim() : null; }


}
