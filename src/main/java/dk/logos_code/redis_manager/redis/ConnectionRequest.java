package dk.logos_code.redis_manager.redis;

import dk.logos_code.redis_manager.redis.datamodels.RedisConnectionType;

import java.util.List;

public class ConnectionRequest {
    public RedisConnectionType type; // node | sentinel | cluster

    // For node: single URL like redis://host:6379 or rediss://...
    public String url;

    // For sentinel/cluster: list of urls or host:port entries. If a single comma-separated string is passed via url, we will split it.
    public List<String> urls;

    // Optional credentials (applied to target server/cluster master)
    public String username;
    public String password;

    // Sentinel-specific: master id (name)
    public String sentinelMasterId;

    // Optional database index for standalone
    public Integer database;

    // Optional timeout millis. Defaults will be applied if null
    public Integer timeoutMs;
}
